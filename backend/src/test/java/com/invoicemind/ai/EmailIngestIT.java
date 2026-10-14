package com.invoicemind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.ingest.EmailIngestService;
import com.invoicemind.ai.ingest.EmailIngestService.Result;
import com.invoicemind.ai.ingest.MailProperties;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.MailServerForTests;
import com.invoicemind.ai.support.TestApi;
import com.invoicemind.ai.support.TestApi.Session;
import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.invoice.InvoiceService;
import com.invoicemind.ai.user.OrganizationRepository;
import com.invoicemind.ai.user.UserRepository;
import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * E-mail ingestion against a real IMAP server (GreenMail): routing by the plus-address token, sender checks, attachment
 * handling through the normal upload path, the message log, duplicate protection and token rotation.
 */
class EmailIngestIT extends AbstractIntegrationTest {
  @Autowired EmailIngestService service;
  @Autowired MailProperties mailProps;
  @Autowired AppProperties appProps;
  @Autowired OrganizationRepository organizations;
  @Autowired UserRepository users;
  @Autowired InvoiceService invoices;
  @Autowired AuditService audit;
  @Autowired NamedParameterJdbcTemplate jdbc;

  record Att(String name, byte[] data, String type) {}

  /** A readable PDF whose scenario the fake ai-service knows, so the invoice completes the whole pipeline. */
  private Att pdf(String name) {
    String id = "mail-" + UUID.randomUUID().toString().substring(0, 8);
    FakeAiServiceForTests.scenario(id, FakeAiServiceForTests.okOcr(id), PipelineIT.extraction("Mail Vendor GmbH", "INV-" + id,
        "1000.00", "190.00", "1190.00"));
    return new Att(name, TestApi.pdf(id), "application/pdf");
  }

  private String token(Session admin) {
    String address = api.get("/api/v1/ingest/email", admin.token()).json().get("address").asText();
    return address.substring(address.indexOf('+') + 1, address.indexOf('@'));
  }

  private MimeMessage mail(String to, String from, String subject, String messageId, List<Att> attachments) throws Exception {
    MimeMessage m = new MimeMessage((jakarta.mail.Session) null) {
      @Override
      protected void updateMessageID() throws jakarta.mail.MessagingException {
        if (messageId != null) setHeader("Message-ID", messageId);
        else super.updateMessageID();
      }
    };
    m.setFrom(new InternetAddress(from, "Some Sender"));
    m.setRecipients(Message.RecipientType.TO, to);
    m.setSubject(subject);
    MimeMultipart body = new MimeMultipart();
    MimeBodyPart text = new MimeBodyPart();
    text.setText("Please book the attached documents.\nThanks", "utf-8");
    body.addBodyPart(text);
    for (Att a : attachments) {
      MimeBodyPart p = new MimeBodyPart();
      p.setDataHandler(new DataHandler(new ByteArrayDataSource(a.data(), a.type())));
      p.setFileName(a.name());
      p.setDisposition("attachment");
      body.addBodyPart(p);
    }
    m.setContent(body);
    m.saveChanges();
    return m;
  }

  private void deliver(MimeMessage m) {
    MailServerForTests.server().getUserManager().getUserByEmail(MailServerForTests.ADDRESS).deliver(m);
  }

  private String addr(String token) { return "invoices+" + token + "@example.com"; }

  private JsonNode log(Session admin) { return api.get("/api/v1/ingest/email", admin.token()).json().get("recent"); }

  private int invoiceCount(Session who) { return api.get("/api/v1/invoices?size=100", who.token()).json().get("totalItems").asInt(); }

  @Test
  void aMailFromAKnownSenderBecomesInvoicesThatFlowThroughThePipeline() throws Exception {
    Session admin = api.registerOrg("Mail Inc");
    Session user = api.createUser(admin, "USER");
    String token = token(admin);
    assertThat(api.get("/api/v1/ingest/email", admin.token()).json().get("address").asText())
        .isEqualTo("invoices+" + token + "@example.com");
    assertThat(token).matches("[0-9a-f]{24}");

    deliver(mail(addr(token), user.email(), "Invoices for March", "<one-" + token + "@test>",
        List.of(pdf("march-1.pdf"), pdf("march-2.pdf"), new Att("notes.txt", "just text".getBytes(StandardCharsets.UTF_8), "text/plain"))));
    Result result = service.pollOnce();

    assertThat(result).isEqualTo(new Result(1, 1, 0));
    assertThat(invoiceCount(user)).isEqualTo(2); // the .txt part is a body-like text part, not a document

    JsonNode list = api.get("/api/v1/invoices?size=100", user.token()).json().get("items");
    for (JsonNode inv : list) {
      assertThat(inv.get("uploadedBy").asText()).isEqualTo(user.userId().toString()); // attributed to the sender
      awaitStatus(user, UUID.fromString(inv.get("id").asText()), "VALIDATED", "NEEDS_REVIEW", "FAILED");
    }
    JsonNode first = api.get("/api/v1/invoices/" + list.get(0).get("id").asText() + "/audit", user.token()).json();
    assertThat(first.toString()).contains("\"source\":\"email\"");

    JsonNode entry = log(admin).get(0);
    assertThat(entry.get("status").asText()).isEqualTo("ACCEPTED");
    assertThat(entry.get("invoicesCreated").asInt()).isEqualTo(2);
    assertThat(entry.get("subject").asText()).isEqualTo("Invoices for March");
    assertThat(entry.get("from").asText()).isEqualTo(user.email());
  }

  @Test
  void messagesThatDoNotQualifyAreRejectedAndLoggedNotProcessed() throws Exception {
    Session admin = api.registerOrg("Mail Rules Inc");
    Session user = api.createUser(admin, "USER");
    Session disabled = api.createUser(admin, "USER");
    Session stranger = api.registerOrg("Mail Stranger Inc");
    String token = token(admin);
    assertThat(api.patch("/api/v1/users/" + disabled.userId(), admin.token(), Map.of("enabled", false)).status()).isEqualTo(200);

    String unknownToken = "ffffffffffffffffffffffff";
    deliver(mail(addr(unknownToken), user.email(), "unknown address", null, List.of(pdf("a.pdf"))));
    deliver(mail("invoices@example.com", user.email(), "no token at all", null, List.of(pdf("a.pdf"))));
    deliver(mail("invoices+" + token + "@other-domain.example", user.email(), "wrong domain", null, List.of(pdf("a.pdf"))));
    deliver(mail(addr(token), "outsider@elsewhere.example", "not a user", null, List.of(pdf("a.pdf"))));
    deliver(mail(addr(token), stranger.email(), "user of ANOTHER organisation", null, List.of(pdf("a.pdf"))));
    deliver(mail(addr(token), disabled.email(), "disabled user", null, List.of(pdf("a.pdf"))));
    deliver(mail(addr(token), user.email(), "no attachment", null, List.of()));
    deliver(mail(addr(token), user.email(), "disguised executable", null,
        List.of(new Att("invoice.pdf", "MZ\u0090\u0000 not a pdf".getBytes(StandardCharsets.ISO_8859_1), "application/pdf"))));

    Result result = service.pollOnce();
    assertThat(result).isEqualTo(new Result(8, 0, 8));
    assertThat(invoiceCount(admin)).isZero();
    assertThat(invoiceCount(stranger)).isZero();

    // the organisation's admin sees what was rejected for its address, with the reason
    Map<String, String> reasons = new java.util.HashMap<>();
    log(admin).forEach(e -> reasons.put(e.get("subject").asText(), e.get("reason").asText()));
    assertThat(reasons).containsOnlyKeys("not a user", "user of ANOTHER organisation", "disabled user", "no attachment", "disguised executable");
    assertThat(reasons.get("not a user")).contains("not a user of this organisation");
    assertThat(reasons.get("user of ANOTHER organisation")).contains("not a user of this organisation");
    assertThat(reasons.get("no attachment")).contains("no attachment");
    assertThat(reasons.get("disguised executable")).contains("Only PDF");
    assertThat(log(stranger)).isEmpty(); // another tenant sees nothing of it
  }

  @Test
  void aMessageIsNeverProcessedTwiceEvenIfItReappearsUnread() throws Exception {
    Session admin = api.registerOrg("Mail Dup Inc");
    Session user = api.createUser(admin, "USER");
    String token = token(admin);
    String id = "<same-" + UUID.randomUUID() + "@test>";
    Att attachment = pdf("once.pdf");

    deliver(mail(addr(token), user.email(), "once", id, List.of(attachment)));
    assertThat(service.pollOnce().accepted()).isEqualTo(1);
    deliver(mail(addr(token), user.email(), "once", id, List.of(attachment))); // same Message-ID, delivered again
    assertThat(service.pollOnce().accepted()).isZero();
    assertThat(invoiceCount(user)).isEqualTo(1);
    assertThat(log(admin)).hasSize(1);
  }

  @Test
  void anotherPollFindsNothingNewAndAttachmentsAreCapped() throws Exception {
    Session admin = api.registerOrg("Mail Cap Inc");
    Session user = api.createUser(admin, "USER");
    String token = token(admin);
    List<Att> seven = new ArrayList<>();
    for (int i = 0; i < 7; i++) seven.add(pdf("doc-" + i + ".pdf"));
    deliver(mail(addr(token), user.email(), "seven documents", null, seven));

    assertThat(service.pollOnce().accepted()).isEqualTo(1);
    assertThat(service.pollOnce()).isEqualTo(new Result(0, 0, 0)); // everything is flagged as read
    assertThat(invoiceCount(user)).isEqualTo(5); // app.mail.max-attachments
    assertThat(log(admin).get(0).get("reason").asText()).contains("2 more attachment(s) ignored");
  }

  @Test
  void rotatingTheTokenInvalidatesTheOldAddress() throws Exception {
    Session admin = api.registerOrg("Mail Rotate Inc");
    Session user = api.createUser(admin, "USER");
    String oldToken = token(admin);

    JsonNode rotated = api.post("/api/v1/ingest/email/token", admin.token(), null).json();
    String newToken = rotated.get("address").asText().replaceAll(".*\\+(.*)@.*", "$1");
    assertThat(newToken).isNotEqualTo(oldToken).matches("[0-9a-f]{24}");

    deliver(mail(addr(oldToken), user.email(), "to the old address", null, List.of(pdf("a.pdf"))));
    deliver(mail(addr(newToken), user.email(), "to the new address", null, List.of(pdf("b.pdf"))));
    Result result = service.pollOnce();
    assertThat(result.accepted()).isEqualTo(1);
    assertThat(result.rejected()).isEqualTo(1);
    assertThat(invoiceCount(user)).isEqualTo(1);
    assertThat(api.get("/api/v1/audit", admin.token()).json().toString()).contains("EMAIL_ADDRESS_ROTATED");
  }

  @Test
  void whenRequiredTheMailServersAuthenticationResultsMustVouchForTheSender() throws Exception {
    Session admin = api.registerOrg("Mail Auth Inc");
    Session user = api.createUser(admin, "USER");
    String token = token(admin);
    MailProperties strictProps = new MailProperties(true, mailProps.address(), mailProps.host(), mailProps.port(),
        mailProps.username(), mailProps.password(), false, "INBOX", 3600, true, 20, 5, 30_000_000);
    EmailIngestService strict = new EmailIngestService(strictProps, appProps, organizations, users, invoices, audit, jdbc);

    MimeMessage forged = mail(addr(token), user.email(), "forged sender", null, List.of(pdf("a.pdf")));
    forged.setHeader("Authentication-Results", "mx.example.com; dkim=fail; spf=fail");
    MimeMessage genuine = mail(addr(token), user.email(), "verified sender", null, List.of(pdf("b.pdf")));
    genuine.setHeader("Authentication-Results", "mx.example.com; dkim=pass header.d=example.com; spf=pass");
    MimeMessage unsigned = mail(addr(token), user.email(), "no results header", null, List.of(pdf("c.pdf")));
    deliver(forged);
    deliver(genuine);
    deliver(unsigned);

    Result result = strict.pollOnce();
    assertThat(result).isEqualTo(new Result(3, 1, 2));
    assertThat(invoiceCount(user)).isEqualTo(1);
    Map<String, String> status = new java.util.HashMap<>();
    log(admin).forEach(e -> status.put(e.get("subject").asText(), e.get("status").asText()));
    assertThat(status).containsEntry("verified sender", "ACCEPTED").containsEntry("forged sender", "REJECTED")
        .containsEntry("no results header", "REJECTED");
  }

  @Test
  void onlyAdminsSeeTheAddressAndTheLog() {
    Session admin = api.registerOrg("Mail Admin Inc");
    Session approver = api.createUser(admin, "APPROVER");
    assertThat(api.get("/api/v1/ingest/email", approver.token()).status()).isEqualTo(403);
    assertThat(api.post("/api/v1/ingest/email/token", approver.token(), null).status()).isEqualTo(403);
    JsonNode status = api.get("/api/v1/ingest/email", admin.token()).json();
    assertThat(status.get("enabled").asBoolean()).isTrue();
    assertThat(status.get("recent")).isEmpty();
  }
}
