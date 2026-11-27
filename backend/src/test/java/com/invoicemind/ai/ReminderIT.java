package com.invoicemind.ai;

import static com.invoicemind.ai.support.FakeAiServiceForTests.line;
import static org.assertj.core.api.Assertions.assertThat;

import com.invoicemind.ai.reminder.ReminderService;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.MailServerForTests;
import com.invoicemind.ai.support.TestApi.Session;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Daily due-date reminders over real SMTP (GreenMail), and due dates taken from the vendor's payment terms. */
class ReminderIT extends AbstractIntegrationTest {
  @Autowired ReminderService reminders;

  private UUID invoice(Session who, String number, String due) {
    String sc = "rem-" + UUID.randomUUID().toString().substring(0, 8);
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc), FakeAiServiceForTests.okExtraction(
        "Reminder Vendor " + sc, number, LocalDate.now().minusDays(30).toString(), due, "EUR", "10.00", "0.00", "10.00",
        List.of(line("Item", "1", "10.00", "10.00")), false, false));
    UUID id = upload(who, sc);
    awaitStatus(who, id, "VALIDATED", "NEEDS_REVIEW");
    return id;
  }

  private List<MimeMessage> mailTo(String address) {
    return Arrays.stream(MailServerForTests.server().getReceivedMessages()).filter(m -> {
      try {
        return Arrays.stream(m.getRecipients(Message.RecipientType.TO)).anyMatch(a -> a.toString().equals(address));
      } catch (Exception e) {
        return false;
      }
    }).toList();
  }

  @Test
  void oneDigestPerDayListsOverdueAndSoonDueInvoicesForThePeopleOfTheOrganisation() throws Exception {
    Session admin = api.registerOrg("Reminder Inc");
    Session approver = api.createUser(admin, "APPROVER");
    Session accountant = api.createUser(admin, "ACCOUNTANT");
    LocalDate today = LocalDate.now();
    invoice(accountant, "LATE-1", today.minusDays(5).toString());
    invoice(accountant, "SOON-1", today.plusDays(2).toString());
    invoice(accountant, "LATER-1", today.plusDays(10).toString());
    UUID rejected = invoice(accountant, "LATE-REJECTED", today.minusDays(3).toString());
    api.post("/api/v1/invoices/" + rejected + "/reject", admin.token(), Map.of("comment", "not ours"));

    ReminderService.Digest digest = reminders.sendDigest(admin.organizationId(), today);
    assertThat(digest.sent()).isTrue();
    assertThat(digest.overdue()).isEqualTo(1);
    assertThat(digest.dueSoon()).isEqualTo(1);
    assertThat(digest.recipients()).isEqualTo(3); // admin, approver, accountant

    List<MimeMessage> received = mailTo(approver.email());
    assertThat(received).hasSize(1);
    String body = (String) received.get(0).getContent();
    assertThat(received.get(0).getSubject()).contains("1 overdue, 1 due within 3 days").contains("Reminder Inc");
    assertThat(body).contains("LATE-1").contains("5 days overdue").contains("SOON-1").contains("due in 2 days")
        .contains("/invoices/").doesNotContain("LATER-1").doesNotContain("LATE-REJECTED");
    assertThat(mailTo(accountant.email())).hasSize(1);

    // a second run the same day (another instance, a restart) sends nothing
    assertThat(reminders.sendDigest(admin.organizationId(), today).sent()).isFalse();
    assertThat(mailTo(approver.email())).hasSize(1);
    assertThat(api.get("/api/v1/audit?action=REMINDER_SENT", admin.token()).json().get("totalItems").asInt()).isEqualTo(1);
  }

  @Test
  void nothingDueMeansNoMail() {
    Session admin = api.registerOrg("Quiet Inc");
    invoice(admin, "FAR-1", LocalDate.now().plusDays(40).toString());
    assertThat(reminders.sendDigest(admin.organizationId(), LocalDate.now()).sent()).isFalse();
    assertThat(mailTo(admin.email())).isEmpty();
  }

  @Test
  void withoutAPrintedDueDateTheVendorsPaymentTermsSetIt() {
    Session admin = api.registerOrg("Terms Inc");
    String sc = "terms-" + UUID.randomUUID().toString().substring(0, 8);
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc), FakeAiServiceForTests.okExtraction(
        "Terms Vendor GmbH", "T-1", "2026-05-01", null, "EUR", "10.00", "0.00", "10.00",
        List.of(line("Item", "1", "10.00", "10.00")), false, false));
    var first = awaitStatus(admin, upload(admin, sc), "VALIDATED", "NEEDS_REVIEW");
    assertThat(first.hasNonNull("dueDate")).isFalse();
    String vendorId = first.at("/vendor/id").asText();
    assertThat(api.put("/api/v1/vendors/" + vendorId, admin.token(), Map.of("name", "Terms Vendor GmbH", "paymentTermsDays", 14))
        .status()).isEqualTo(200);

    String sc2 = "terms-" + UUID.randomUUID().toString().substring(0, 8);
    FakeAiServiceForTests.scenario(sc2, FakeAiServiceForTests.okOcr(sc2), FakeAiServiceForTests.okExtraction(
        "Terms Vendor GmbH", "T-2", "2026-06-01", null, "EUR", "11.00", "0.00", "11.00",
        List.of(line("Item", "1", "11.00", "11.00")), false, false));
    var second = awaitStatus(admin, upload(admin, sc2), "VALIDATED", "NEEDS_REVIEW");
    assertThat(second.get("dueDate").asText()).isEqualTo("2026-06-15");
    assertThat(second.path("memoryApplied").findValuesAsText("field")).contains("dueDate");
  }
}
