package com.invoicemind.ai;

import static com.invoicemind.ai.support.FakeAiServiceForTests.line;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Vendor memory: a person's correction becomes a plain rule for that vendor, applied to its next invoice and marked
 * there as "from vendor memory". Deterministic; no model involved.
 */
class VendorMemoryIT extends AbstractIntegrationTest {
  private static int counter = 100;

  /** An invoice of {@code vendor}; each call gets its own number and total, so they are never possible duplicates. */
  private JsonNode process(Session who, String vendor, String currency, String date, String due) {
    String sc = "mem-" + UUID.randomUUID().toString().substring(0, 8);
    int n = counter++;
    String total = n + ".00";
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc), FakeAiServiceForTests.okExtraction(vendor,
        "M-" + n, date, due, currency, total, "0.00", total, List.of(line("Licence", "1", total, total)), false, false));
    return awaitStatus(who, upload(who, sc), "VALIDATED", "NEEDS_REVIEW", "FAILED");
  }

  private Resp correct(Session who, JsonNode inv, Map<String, Object> changes) {
    Resp r = api.put("/api/v1/invoices/" + inv.get("id").asText(), who.token(), PipelineIT.correction(inv, changes));
    assertThat(r.status()).as(r.text()).isEqualTo(200);
    return r;
  }

  private static List<String> appliedFields(JsonNode inv) {
    return inv.path("memoryApplied").findValuesAsText("field");
  }

  @Test
  void aCorrectedCurrencyIsAppliedToTheVendorsNextInvoiceWhenTheDocumentReadsTheSame() {
    Session s = api.registerOrg("Currency Memory Inc");
    JsonNode first = process(s, "Maple Leaf Hosting", "USD", "2026-05-01", "2026-05-31");
    correct(s, first, Map.of("currency", "CAD")); // the "$" on this vendor's invoices means Canadian dollars

    JsonNode next = process(s, "Maple Leaf Hosting", "USD", "2026-06-01", "2026-07-01");
    assertThat(next.get("currency").asText()).isEqualTo("CAD");
    JsonNode applied = next.path("memoryApplied").get(0);
    assertThat(applied.get("field").asText()).isEqualTo("currency");
    assertThat(applied.get("value").asText()).isEqualTo("CAD");
    assertThat(applied.get("extracted").asText()).isEqualTo("USD");

    JsonNode euro = process(s, "Maple Leaf Hosting", "EUR", "2026-06-02", "2026-07-02");
    assertThat(euro.get("currency").asText()).isEqualTo("EUR"); // the rule is about USD only
    assertThat(appliedFields(euro)).doesNotContain("currency");

    // going back to what the document says forgets the rule
    correct(s, api.get("/api/v1/invoices/" + next.get("id").asText(), s.token()).json(), Map.of("currency", "USD"));
    JsonNode later = process(s, "Maple Leaf Hosting", "USD", "2026-06-03", "2026-07-03");
    assertThat(later.get("currency").asText()).isEqualTo("USD");
  }

  @Test
  void paymentTermsAndCategoryAreLearnedAndFillTheGapsOfTheNextInvoice() {
    Session s = api.registerOrg("Terms Memory Inc");
    JsonNode first = process(s, "Graphite Cloud GmbH", "EUR", "2026-05-01", null);
    correct(s, first, Map.of("dueDate", "2026-05-31", "expenseCategory", "Software subscriptions"));

    JsonNode next = process(s, "Graphite Cloud GmbH", "EUR", "2026-06-10", null);
    assertThat(next.get("dueDate").asText()).isEqualTo("2026-07-10"); // invoice date + the learned 30 days
    assertThat(next.get("expenseCategory").asText()).isEqualTo("Software subscriptions");
    assertThat(appliedFields(next)).containsExactlyInAnyOrder("dueDate", "expenseCategory");

    JsonNode printed = process(s, "Graphite Cloud GmbH", "EUR", "2026-06-11", "2026-06-25");
    assertThat(printed.get("dueDate").asText()).isEqualTo("2026-06-25"); // a printed due date always wins
    assertThat(appliedFields(printed)).doesNotContain("dueDate");

    JsonNode vendor = api.get("/api/v1/vendors", s.token()).json().get(0);
    assertThat(vendor.get("paymentTermsDays").asInt()).isEqualTo(30);
    assertThat(vendor.get("defaultCategory").asText()).isEqualTo("Software subscriptions");
    JsonNode terms = vendor.path("memory").findParents("field").stream()
        .filter(m -> "PAYMENT_TERMS".equals(m.get("field").asText())).findFirst().orElseThrow();
    assertThat(terms.get("timesApplied").asInt()).isEqualTo(1);

    // a person can make the system forget a rule
    Resp forgotten = api.delete("/api/v1/vendors/" + vendor.get("id").asText() + "/memory/" + terms.get("id").asText(), s.token());
    assertThat(forgotten.status()).isEqualTo(200);
    JsonNode afterForget = process(s, "Graphite Cloud GmbH", "EUR", "2026-06-12", null);
    assertThat(afterForget.hasNonNull("dueDate")).isFalse();
  }

  @Test
  void aMisreadVendorNameCorrectedToAnExistingVendorBecomesItsAlias() {
    Session s = api.registerOrg("Alias Memory Inc");
    JsonNode real = process(s, "Northwind Traders Limited", "GBP", "2026-04-01", "2026-05-01");
    api.verifyVendorOf(s.token(), real.get("id").asText());

    JsonNode misread = process(s, "Northwlnd Traders Ltd", "GBP", "2026-04-02", "2026-05-02");
    assertThat(misread.path("vendor").path("id").asText()).isNotEqualTo(real.path("vendor").path("id").asText());
    Resp fixed = correct(s, misread, Map.of("vendorName", "Northwind Traders Limited"));
    assertThat(fixed.json().path("vendor").path("id").asText()).isEqualTo(real.path("vendor").path("id").asText());

    // the vendor created from the misread name is gone; the misread name now leads to the real vendor
    assertThat(api.get("/api/v1/vendors", s.token()).json().size()).isEqualTo(1);
    JsonNode again = process(s, "Northwlnd Traders Ltd", "GBP", "2026-04-03", "2026-05-03");
    assertThat(again.path("vendor").path("id").asText()).isEqualTo(real.path("vendor").path("id").asText());
    assertThat(again.path("vendor").path("status").asText()).isEqualTo("VERIFIED");
    assertThat(appliedFields(again)).contains("vendor");
  }

  @Test
  void everyCorrectionIsStoredWithBeforeAfterWhoAndWhen() {
    Session admin = api.registerOrg("Corrections Inc");
    Session accountant = api.createUser(admin, "ACCOUNTANT");
    JsonNode inv = process(accountant, "Fennel Foods", "EUR", "2026-03-01", "2026-03-31");
    correct(accountant, inv, Map.of("invoiceNumber", "FF-2026-77", "total", new java.math.BigDecimal("101.50")));

    JsonNode rows = api.get("/api/v1/invoices/" + inv.get("id").asText() + "/corrections", accountant.token()).json();
    assertThat(rows.findValuesAsText("field")).containsExactlyInAnyOrder("invoiceNumber", "total");
    JsonNode number = rows.findParents("field").stream().filter(r -> "invoiceNumber".equals(r.get("field").asText()))
        .findFirst().orElseThrow();
    assertThat(number.get("oldValue").asText()).isEqualTo(inv.get("invoiceNumber").asText());
    assertThat(number.get("newValue").asText()).isEqualTo("FF-2026-77");
    assertThat(number.get("userName").isNull()).isFalse();
    assertThat(number.hasNonNull("at")).isTrue();
    // another organisation cannot read them
    assertThat(api.get("/api/v1/invoices/" + inv.get("id").asText() + "/corrections", api.registerOrg("Other").token())
        .status()).isEqualTo(404);
  }
}
