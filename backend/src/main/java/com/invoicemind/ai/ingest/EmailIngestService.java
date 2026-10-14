package com.invoicemind.ai.ingest;

import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.invoice.InvoiceDtos.InvoiceSummary;
import com.invoicemind.ai.invoice.InvoiceService;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.user.OrganizationEntity;
import com.invoicemind.ai.user.OrganizationRepository;
import com.invoicemind.ai.user.UserEntity;
import com.invoicemind.ai.user.UserRepository;
import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Polls the IMAP mailbox and turns attachments into invoices through the same upload path as the web UI, so file
 * sniffing, size limits, the active-content scan, hashing, Kafka processing and the approval workflow all apply.
 *
 * <p>A message is accepted only if (1) it was addressed to {@code <mailbox>+<token>@<domain>} of a known organisation and
 * (2) its sender is an enabled user of that organisation (optionally also proven by the mail server's
 * Authentication-Results). The document is attributed to that user and goes to review like any other upload; nothing
 * is ever approved automatically. Every message, accepted or not, is written to {@code email_ingest_log}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailIngestService {
  public record Result(int messages, int accepted, int rejected) {}

  private record Attachment(String filename, byte[] data) {}

  private record Verdict(UUID organizationId, String status, String reason, int created) {
    static Verdict rejected(UUID org, String reason) { return new Verdict(org, "REJECTED", reason, 0); }
  }

  private final MailProperties props;
  private final AppProperties app;
  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final InvoiceService invoices;
  private final AuditService audit;
  private final NamedParameterJdbcTemplate jdbc;

  /** One poll. Synchronised: the scheduler and a manual trigger must never read the same messages twice. */
  public synchronized Result pollOnce() {
    if (!props.configured()) return new Result(0, 0, 0);
    int seen = 0, accepted = 0, rejected = 0;
    Session session = Session.getInstance(sessionProperties());
    try (Store store = session.getStore(props.ssl() ? "imaps" : "imap")) {
      store.connect(props.host(), props.port(), props.username(), props.password());
      Folder folder = store.getFolder(props.folder());
      folder.open(Folder.READ_WRITE);
      try {
        Message[] unseen = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
        for (int i = 0; i < Math.min(unseen.length, props.maxMessagesPerRun()); i++) {
          MimeMessage message = (MimeMessage) unseen[i];
          seen++;
          Verdict verdict;
          try {
            verdict = handle(message);
          } catch (RuntimeException | MessagingException | IOException e) {
            log.warn("Could not process a mail message: {}", e.toString());
            verdict = Verdict.rejected(null, "The message could not be processed");
          }
          if ("ACCEPTED".equals(verdict.status())) accepted++;
          else rejected++;
          message.setFlag(Flags.Flag.SEEN, true); // handled either way; the log table makes the outcome visible
        }
      } finally {
        folder.close(false);
      }
    } catch (MessagingException e) {
      log.warn("Mailbox poll failed: {}", e.getMessage());
    }
    if (seen > 0) log.info("Mailbox poll: {} message(s), {} accepted, {} rejected", seen, accepted, rejected);
    return new Result(seen, accepted, rejected);
  }

  // ----------------------------------------------------------- one message
  private Verdict handle(MimeMessage message) throws MessagingException, IOException {
    String messageId = messageId(message);
    String from = sender(message);
    String subject = clean(message.getSubject(), 200);
    if (alreadyLogged(messageId)) return new Verdict(null, "REJECTED", "duplicate", 0); // not logged twice

    Verdict verdict = evaluate(message, from);
    jdbc.update("""
        insert into email_ingest_log (organization_id, message_id, from_address, subject, status, reason, invoices_created)
        values (:org, :mid, :from, :subject, :status, :reason, :created) on conflict (message_id) do nothing""",
        new MapSqlParameterSource().addValue("org", verdict.organizationId()).addValue("mid", messageId)
            .addValue("from", from).addValue("subject", subject).addValue("status", verdict.status())
            .addValue("reason", verdict.reason()).addValue("created", verdict.created()));
    return verdict;
  }

  private Verdict evaluate(MimeMessage message, String from) throws MessagingException, IOException {
    if (message.getSize() > props.maxMessageBytes()) return Verdict.rejected(null, "The message is too large");

    Optional<OrganizationEntity> org = routeTo(message);
    if (org.isEmpty()) return Verdict.rejected(null, "Not addressed to a known invoice address");
    UUID orgId = org.get().getId();

    if (from == null) return Verdict.rejected(orgId, "The message has no sender");
    Optional<UserEntity> user = users.findByEmailIgnoreCase(from)
        .filter(u -> u.getOrganizationId().equals(orgId) && u.isEnabled());
    if (user.isEmpty()) return Verdict.rejected(orgId, "The sender is not a user of this organisation");
    if (props.requireAuthResults() && !senderAuthenticated(message)) {
      return Verdict.rejected(orgId, "The sender could not be verified (no dkim=pass or spf=pass from the mail server)");
    }

    List<Attachment> attachments = new ArrayList<>();
    collect(message, attachments);
    if (attachments.isEmpty()) return Verdict.rejected(orgId, "The message has no attachment");

    UserEntity u = user.get();
    AuthPrincipal principal = new AuthPrincipal(u.getId(), orgId, u.getEmail(), u.getRole());
    int created = 0;
    List<String> problems = new ArrayList<>();
    List<UUID> ids = new ArrayList<>();
    for (Attachment a : attachments.subList(0, Math.min(attachments.size(), props.maxAttachments()))) {
      try {
        InvoiceSummary summary = invoices.upload(new BytesMultipartFile(a.filename(), a.data()), principal, "email");
        ids.add(summary.id());
        created++;
      } catch (ApiException e) {
        problems.add(truncate(a.filename(), 60) + ": " + e.getMessage());
      } catch (RuntimeException e) {
        problems.add(truncate(a.filename(), 60) + ": could not be stored");
      }
    }
    if (attachments.size() > props.maxAttachments()) {
      problems.add((attachments.size() - props.maxAttachments()) + " more attachment(s) ignored (limit " + props.maxAttachments() + ")");
    }
    if (created > 0) {
      audit.log(orgId, u.getId(), "EMAIL_INGESTED", AuditService.ORGANIZATION, orgId,
          Map.of("from", from, "invoices", ids.stream().map(UUID::toString).toList()));
    }
    String reason = problems.isEmpty() ? null : truncate(String.join("; ", problems), 300);
    return created > 0 ? new Verdict(orgId, "ACCEPTED", reason, created) : Verdict.rejected(orgId, reason);
  }

  // ---------------------------------------------------------------- routing
  /** Finds the organisation from the plus-tag of any recipient address (To, Cc, Delivered-To, X-Original-To...). */
  private Optional<OrganizationEntity> routeTo(MimeMessage message) throws MessagingException {
    String base = props.address().trim().toLowerCase(Locale.ROOT);
    int at = base.indexOf('@');
    String local = base.substring(0, at), domain = base.substring(at + 1);

    Set<String> candidates = new LinkedHashSet<>();
    Address[] all = message.getAllRecipients();
    if (all != null) for (Address a : all) candidates.add(a.toString());
    for (String header : List.of("Delivered-To", "X-Original-To", "Envelope-To", "X-Envelope-To")) {
      String[] values = message.getHeader(header);
      if (values != null) candidates.addAll(List.of(values));
    }
    for (String candidate : candidates) {
      InternetAddress[] parsed;
      try {
        parsed = InternetAddress.parse(candidate, false);
      } catch (MessagingException e) {
        continue;
      }
      for (InternetAddress address : parsed) {
        String mail = address.getAddress() == null ? "" : address.getAddress().trim().toLowerCase(Locale.ROOT);
        int a = mail.indexOf('@');
        if (a < 0 || !mail.substring(a + 1).equals(domain)) continue;
        String name = mail.substring(0, a);
        int plus = name.indexOf('+');
        if (plus < 0 || !name.substring(0, plus).equals(local)) continue;
        Optional<OrganizationEntity> org = organizations.findByIngestToken(name.substring(plus + 1));
        if (org.isPresent()) return org;
      }
    }
    return Optional.empty();
  }

  private static String sender(MimeMessage message) throws MessagingException {
    Address[] from = message.getFrom();
    if (from == null || from.length == 0 || !(from[0] instanceof InternetAddress ia) || ia.getAddress() == null) return null;
    String mail = ia.getAddress().trim().toLowerCase(Locale.ROOT);
    return mail.length() > 254 ? null : mail;
  }

  private static boolean senderAuthenticated(MimeMessage message) throws MessagingException {
    String[] results = message.getHeader("Authentication-Results");
    if (results == null) return false;
    for (String r : results) {
      String lower = r.toLowerCase(Locale.ROOT);
      if (lower.contains("dkim=pass") || lower.contains("spf=pass")) return true;
    }
    return false;
  }

  // ------------------------------------------------------------ attachments
  private void collect(Part part, List<Attachment> out) throws MessagingException, IOException {
    if (part.isMimeType("multipart/*")) {
      Multipart multipart = (Multipart) part.getContent();
      for (int i = 0; i < multipart.getCount(); i++) collect(multipart.getBodyPart(i), out);
      return;
    }
    if (part.isMimeType("message/*") || part.isMimeType("text/*")) return; // bodies and forwarded mails are not documents
    String name = part.getFileName();
    boolean candidate = Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || name != null
        || part.isMimeType("application/pdf") || part.isMimeType("image/*");
    if (!candidate) return;
    long max = app.storage().maxFileBytes();
    try (var in = part.getInputStream()) {
      byte[] data = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, max + 1)); // one byte over the limit is enough to reject
      out.add(new Attachment(name == null ? "attachment" : name, data));
    }
  }

  // ---------------------------------------------------------------- helpers
  private boolean alreadyLogged(String messageId) {
    Integer n = jdbc.queryForObject("select count(*) from email_ingest_log where message_id = :id",
        new MapSqlParameterSource("id", messageId), Integer.class);
    return n != null && n > 0;
  }

  /** The Message-ID header, or a hash of the headers that identify the message when a sender omitted it. */
  private static String messageId(MimeMessage message) throws MessagingException {
    String id = message.getMessageID();
    if (id != null && !id.isBlank()) return truncate(id.trim(), 300);
    String basis = message.getFrom() + "|" + message.getSubject() + "|" + message.getSentDate() + "|" + message.getSize();
    try {
      return "synthetic-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(basis.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private Properties sessionProperties() {
    Properties p = new Properties();
    String proto = props.ssl() ? "imaps" : "imap";
    p.put("mail.mime.decodefilename", "true");
    p.put("mail." + proto + ".connectiontimeout", "10000");
    p.put("mail." + proto + ".timeout", "30000");
    p.put("mail." + proto + ".writetimeout", "30000");
    if (props.ssl()) p.put("mail.imaps.ssl.checkserveridentity", "true");
    return p;
  }

  /** Subjects and addresses come from outside: strip control characters before they are stored and shown. */
  private static String clean(String s, int max) {
    if (s == null) return null;
    return truncate(s.replaceAll("\\p{Cntrl}", " ").strip(), max);
  }

  private static String truncate(String s, int max) { return s.length() <= max ? s : s.substring(0, max); }
}
