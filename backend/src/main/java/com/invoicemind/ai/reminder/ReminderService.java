package com.invoicemind.ai.reminder;

import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.firm.UserOrgAccessRepository;
import com.invoicemind.ai.invoice.InvoiceEntity;
import com.invoicemind.ai.invoice.InvoiceRepository;
import com.invoicemind.ai.invoice.ReviewQueue;
import com.invoicemind.ai.user.OrganizationEntity;
import com.invoicemind.ai.user.OrganizationRepository;
import com.invoicemind.ai.user.Role;
import com.invoicemind.ai.user.UserEntity;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Daily due-date reminders: one e-mail per person (approvers, admins and accountants of the organisation) listing
 * the open invoices that are overdue or due within {@code days-ahead} days. At most one digest per organisation and
 * day, also with several backend instances ({@code reminder_runs}). Needs SMTP ({@code SMTP_HOST}); without it the
 * job does nothing.
 */
@Slf4j
@Service
public class ReminderService {
  public record Digest(int dueSoon, int overdue, int recipients, boolean sent) {
    static Digest none() { return new Digest(0, 0, 0, false); }
  }

  private static final Set<Role> RECIPIENT_ROLES = Set.of(Role.ACCOUNTANT, Role.APPROVER, Role.ADMIN);

  private final InvoiceRepository invoices;
  private final OrganizationRepository organizations;
  private final UserOrgAccessRepository access;
  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectProvider<JavaMailSender> mailSender;
  private final AuditService audit;
  private final Clock clock;
  private final boolean enabled;
  private final int daysAhead;
  private final String from;
  private final String publicUrl;
  private final String smtpHost;

  public ReminderService(InvoiceRepository invoices, OrganizationRepository organizations, UserOrgAccessRepository access,
                         NamedParameterJdbcTemplate jdbc, ObjectProvider<JavaMailSender> mailSender, AuditService audit,
                         Clock clock,
                         @Value("${app.reminders.enabled:true}") boolean enabled,
                         @Value("${app.reminders.days-ahead:3}") int daysAhead,
                         @Value("${app.reminders.from:invoicemind@localhost}") String from,
                         @Value("${app.reminders.public-url:http://localhost}") String publicUrl,
                         @Value("${spring.mail.host:}") String smtpHost) {
    this.invoices = invoices;
    this.organizations = organizations;
    this.access = access;
    this.jdbc = jdbc;
    this.mailSender = mailSender;
    this.audit = audit;
    this.clock = clock;
    this.enabled = enabled;
    this.daysAhead = daysAhead;
    this.from = from;
    this.publicUrl = publicUrl.replaceAll("/+$", "");
    this.smtpHost = smtpHost;
  }

  @Scheduled(cron = "${app.reminders.cron:0 37 5 * * *}", zone = "UTC")
  public void daily() {
    if (!enabled) return;
    if (!StringUtils.hasText(smtpHost) || mailSender.getIfAvailable() == null) {
      log.info("Due-date reminders skipped: no SMTP server configured (SMTP_HOST)");
      return;
    }
    LocalDate today = LocalDate.now(clock);
    for (OrganizationEntity org : organizations.findAll()) {
      try {
        sendDigest(org.getId(), today);
      } catch (RuntimeException e) {
        log.warn("Reminder digest for organisation {} failed: {}", org.getId(), e.getMessage());
      }
    }
  }

  /** Sends today's digest for one organisation unless it was already sent today. Public for tests. */
  public Digest sendDigest(UUID organizationId, LocalDate today) {
    List<InvoiceEntity> open = invoices.findByOrganizationIdAndStatusInAndDueDateBefore(organizationId,
        ReviewQueue.PAYABLE, today.plusDays(daysAhead + 1L)).stream()
        .filter(i -> i.getExportedAt() == null).toList(); // exported invoices are paid from the accounting system
    List<InvoiceEntity> overdue = open.stream().filter(i -> i.getDueDate().isBefore(today))
        .sorted(Comparator.comparing(InvoiceEntity::getDueDate)).toList();
    List<InvoiceEntity> dueSoon = open.stream().filter(i -> !i.getDueDate().isBefore(today))
        .sorted(Comparator.comparing(InvoiceEntity::getDueDate)).toList();
    if (overdue.isEmpty() && dueSoon.isEmpty()) return Digest.none();

    List<UserEntity> recipients = access.findEnabledUsersWithRoleIn(organizationId, RECIPIENT_ROLES);
    int claimed = jdbc.update("""
        insert into reminder_runs (organization_id, run_date, due_soon, overdue, recipients)
        values (:org, :day, :soon, :late, :people) on conflict do nothing""",
        new MapSqlParameterSource("org", organizationId).addValue("day", Date.valueOf(today))
            .addValue("soon", dueSoon.size()).addValue("late", overdue.size()).addValue("people", recipients.size()));
    if (claimed == 0) return new Digest(dueSoon.size(), overdue.size(), 0, false); // already sent today

    String orgName = organizations.findById(organizationId).map(OrganizationEntity::getName).orElse("your organisation");
    String subject = "InvoiceMind: " + overdue.size() + " overdue, " + dueSoon.size() + " due within " + daysAhead
        + " days (" + orgName + ")";
    String body = body(overdue, dueSoon, today);
    JavaMailSender sender = mailSender.getObject();
    try {
      for (UserEntity person : recipients) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(person.getEmail());
        mail.setSubject(subject);
        mail.setText("Hello " + person.getFullName() + ",\n\n" + body);
        sender.send(mail);
      }
    } catch (RuntimeException e) {
      // let tomorrow's (or a manual) run try again
      jdbc.update("delete from reminder_runs where organization_id = :org and run_date = :day",
          new MapSqlParameterSource("org", organizationId).addValue("day", Date.valueOf(today)));
      throw e;
    }
    audit.log(organizationId, null, "REMINDER_SENT", AuditService.ORGANIZATION, organizationId,
        Map.of("overdue", overdue.size(), "dueSoon", dueSoon.size(), "recipients", recipients.size()));
    return new Digest(dueSoon.size(), overdue.size(), recipients.size(), true);
  }

  private String body(List<InvoiceEntity> overdue, List<InvoiceEntity> dueSoon, LocalDate today) {
    StringBuilder b = new StringBuilder();
    if (!overdue.isEmpty()) {
      b.append("Overdue (").append(overdue.size()).append("):\n");
      overdue.forEach(i -> line(b, i, ChronoUnit.DAYS.between(i.getDueDate(), today) + " days overdue"));
      b.append('\n');
    }
    if (!dueSoon.isEmpty()) {
      b.append("Due within ").append(daysAhead).append(" days (").append(dueSoon.size()).append("):\n");
      dueSoon.forEach(i -> {
        long days = ChronoUnit.DAYS.between(today, i.getDueDate());
        line(b, i, days == 0 ? "due today" : "due in " + days + (days == 1 ? " day" : " days"));
      });
      b.append('\n');
    }
    b.append("Open the review queue: ").append(publicUrl).append("/invoices\n\n")
        .append("You receive this because you work on invoices in InvoiceMind.\n");
    return b.toString();
  }

  private void line(StringBuilder b, InvoiceEntity i, String when) {
    b.append("- ").append(i.getVendorName() == null ? i.getOriginalFilename() : i.getVendorName())
        .append(" · ").append(i.getInvoiceNumber() == null ? "(no number)" : i.getInvoiceNumber())
        .append(" · ").append(i.getTotal() == null ? "?" : i.getTotal().setScale(2, RoundingMode.HALF_UP).toPlainString())
        .append(' ').append(i.getCurrency() == null ? "" : i.getCurrency())
        .append(" · due ").append(i.getDueDate()).append(" (").append(when).append(")")
        .append(" · ").append(i.getStatus().name().toLowerCase().replace('_', ' '))
        .append("\n  ").append(publicUrl).append("/invoices/").append(i.getId()).append('\n');
  }
}
