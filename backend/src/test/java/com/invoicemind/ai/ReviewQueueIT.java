package com.invoicemind.ai;

import static com.invoicemind.ai.support.FakeAiServiceForTests.line;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.TestApi.Session;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The review queues are filters on the invoice list, with counts, under the usual visibility rules. */
class ReviewQueueIT extends AbstractIntegrationTest {

  private UUID process(Session who, String vendor, String number, String date, String due, String total, String tax) {
    String sc = "queue-" + UUID.randomUUID().toString().substring(0, 8);
    String subtotal = new java.math.BigDecimal(total).subtract(new java.math.BigDecimal(tax)).toPlainString();
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc), FakeAiServiceForTests.okExtraction(vendor,
        number, date, due, "EUR", subtotal, tax, total, List.of(line("Item", "1", subtotal, subtotal)), false, false));
    UUID id = upload(who, sc);
    awaitStatus(who, id, "VALIDATED", "NEEDS_REVIEW", "FAILED");
    return id;
  }

  private List<String> queue(Session who, String queue) {
    return api.get("/api/v1/invoices?size=100&queue=" + queue, who.token()).json().path("items").findValuesAsText("id");
  }

  @Test
  void eachQueueListsExactlyItsInvoicesAndCountsThem() {
    Session admin = api.registerOrg("Queues Inc");
    Session accountant = api.createUser(admin, "USER");
    String past = LocalDate.now().minusDays(40).toString(), pastDue = LocalDate.now().minusDays(10).toString();
    String recent = LocalDate.now().minusDays(2).toString(), soon = LocalDate.now().plusDays(28).toString();

    UUID wrongSum = process(accountant, "Queue Vendor A", "QA-1", recent, soon, "120.00", "50.00");
    UUID overdue = process(accountant, "Queue Vendor B", "QB-1", past, pastDue, "80.00", "0.00");
    UUID first = process(accountant, "Queue Vendor C", "QC-1", recent, soon, "55.00", "0.00");
    UUID possible = process(accountant, "Queue Vendor C", "QC-2", recent, soon, "55.00", "0.00");
    // a broken total for the needs-review queue
    String sc = "queue-bad-" + UUID.randomUUID().toString().substring(0, 6);
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc), FakeAiServiceForTests.okExtraction("Queue Vendor D",
        "QD-1", recent, soon, "EUR", "100.00", "19.00", "150.00", List.of(line("Item", "1", "100.00", "100.00")), false, false));
    UUID broken = upload(accountant, sc);
    awaitStatus(accountant, broken, "NEEDS_REVIEW");

    assertThat(queue(accountant, "NEEDS_REVIEW")).contains(broken.toString(), possible.toString())
        .doesNotContain(first.toString(), overdue.toString());
    assertThat(queue(accountant, "POSSIBLE_DUPLICATE")).containsExactly(possible.toString());
    assertThat(queue(accountant, "OVERDUE")).containsExactly(overdue.toString());
    assertThat(queue(accountant, "UNVERIFIED_VENDOR")).hasSize(5).contains(wrongSum.toString());

    JsonNode counts = api.get("/api/v1/invoices/queues", accountant.token()).json();
    assertThat(counts.get("possibleDuplicate").asLong()).isEqualTo(1);
    assertThat(counts.get("overdue").asLong()).isEqualTo(1);
    assertThat(counts.get("unverifiedVendor").asLong()).isEqualTo(5);
    assertThat(counts.get("needsReview").asLong()).isEqualTo(queue(accountant, "NEEDS_REVIEW").size());

    // confirming a vendor takes its invoices out of the queue; rejecting an overdue invoice closes it
    api.verifyVendorOf(admin.token(), first);
    assertThat(queue(accountant, "UNVERIFIED_VENDOR")).doesNotContain(first.toString(), possible.toString()).hasSize(3);
    api.post("/api/v1/invoices/" + overdue + "/reject", admin.token(), Map.of("comment", "Not ours"));
    assertThat(queue(accountant, "OVERDUE")).isEmpty();

    // a USER sees only their own uploads in the queues too
    Session other = api.createUser(admin, "USER");
    assertThat(api.get("/api/v1/invoices/queues", other.token()).json().get("unverifiedVendor").asLong()).isZero();
  }
}
