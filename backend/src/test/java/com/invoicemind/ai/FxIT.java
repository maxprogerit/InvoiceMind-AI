package com.invoicemind.ai;

import static com.invoicemind.ai.support.FakeAiServiceForTests.line;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.FakeFrankfurterForTests;
import com.invoicemind.ai.support.TestApi.Session;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Currency conversion in analytics: ECB-style rates from a fake rate source (business days only), manual rates for
 * currencies the ECB does not publish, base-currency changes and graceful degradation when rates are unavailable.
 */
class FxIT extends AbstractIntegrationTest {

  @AfterEach
  void restoreRateSource() { FakeFrankfurterForTests.failing(false); }

  /** Uploads, validates and approves an invoice whose total is exactly {@code total} (no tax). */
  private UUID approved(Session uploader, Session approver, String vendor, String currency, String date, String total) {
    String id = "fx-" + UUID.randomUUID().toString().substring(0, 8);
    FakeAiServiceForTests.scenario(id, FakeAiServiceForTests.okOcr(id), FakeAiServiceForTests.okExtraction(
        vendor, "INV-" + id, date, null, currency, total, "0.00", total, List.of(line("Services", "1", total, total)),
        false, false));
    UUID invoice = upload(uploader, id);
    awaitStatus(uploader, invoice, "VALIDATED");
    assertThat(api.post("/api/v1/invoices/" + invoice + "/approve", approver.token(), Map.of()).status()).isEqualTo(200);
    return invoice;
  }

  private JsonNode summary(Session s) { return api.get("/api/v1/analytics/summary", s.token()).json(); }

  private static void assertMoney(JsonNode node, String amount) {
    assertThat(node.decimalValue()).isEqualByComparingTo(amount);
  }

  @Test
  void spendIsConvertedAtTheRateOfEachInvoiceDate() {
    Session admin = api.registerOrg("Fx Inc");
    Session approver = api.createUser(admin, "APPROVER");
    Session user = api.createUser(admin, "USER");

    approved(user, approver, "Usd Vendor", "USD", "2026-03-13", "1100.00"); // Friday, 1.2 USD per EUR -> 916.67
    approved(user, approver, "Gbp Vendor", "GBP", "2026-02-14", "800.00");  // Saturday: Friday's 0.8 applies -> 1000.00
    approved(user, approver, "Eur Vendor", "EUR", "2026-02-02", "500.00");  // already in the base currency
    approved(user, approver, "Rub Vendor", "RUB", "2026-03-02", "95000.00"); // the feed has no RUB

    JsonNode s = summary(admin);
    assertThat(s.get("baseCurrency").asText()).isEqualTo("EUR");
    assertMoney(s.at("/approvedConverted/amount"), "2416.67");
    assertThat(s.at("/approvedConverted/invoices").asInt()).isEqualTo(3);
    assertThat(s.at("/approvedConverted/notConverted")).hasSize(1);
    assertThat(s.at("/approvedConverted/notConverted/0/currency").asText()).isEqualTo("RUB");
    assertMoney(s.at("/approvedConverted/notConverted/0/amount"), "95000.00");
    assertThat(FakeFrankfurterForTests.requests()).isNotEmpty();

    JsonNode months = api.get("/api/v1/analytics/spend-by-month?converted=true&months=12", admin.token()).json();
    assertThat(months.get("currency").asText()).isEqualTo("EUR");
    for (JsonNode m : months.get("items")) {
      if (m.get("month").asText().equals("2026-02")) assertMoney(m.get("amount"), "1500.00");
      if (m.get("month").asText().equals("2026-03")) assertMoney(m.get("amount"), "916.67");
    }
    assertThat(months.get("notConverted").get(0).get("currency").asText()).isEqualTo("RUB");

    JsonNode vendors = api.get("/api/v1/analytics/spend-by-vendor?converted=true", admin.token()).json();
    assertThat(vendors.at("/items/0/vendor").asText()).isEqualTo("Gbp Vendor");
    assertMoney(vendors.at("/items/0/amount"), "1000.00");
    assertThat(vendors.at("/items/1/vendor").asText()).isEqualTo("Usd Vendor");
    assertThat(vendors.get("items")).hasSize(3); // RUB is not in the converted ranking

    // the plain per-currency view is untouched
    assertThat(api.get("/api/v1/analytics/spend-by-vendor?currency=USD", admin.token()).json().at("/items/0/amount")
        .decimalValue()).isEqualByComparingTo("1100.00");

    // a range that is already downloaded is not requested again
    int downloads = FakeFrankfurterForTests.requests().size();
    approved(user, approver, "Another Eur Vendor", "EUR", "2026-03-03", "10.00"); // invalidates the cache
    assertThat(summary(admin).at("/approvedConverted/invoices").asInt()).isEqualTo(4);
    assertThat(FakeFrankfurterForTests.requests()).hasSize(downloads);
  }

  @Test
  void manualRatesCoverCurrenciesTheFeedLacksAndBeatTheFeedOnTheSameDate() {
    Session admin = api.registerOrg("Fx Manual Inc");
    Session approver = api.createUser(admin, "APPROVER");
    approved(admin, approver, "Rub Vendor", "RUB", "2026-03-02", "95000.00");
    approved(admin, approver, "Usd Vendor", "USD", "2026-03-13", "1200.00"); // ECB 1.2 -> 1000.00

    assertThat(summary(admin).at("/approvedConverted/notConverted/0/currency").asText()).isEqualTo("RUB");

    // 1 EUR = 100 RUB from the first of January on
    var rub = api.put("/api/v1/fx/manual", admin.token(), Map.of("currency", "rub", "date", "2026-01-01", "perEur", "100"));
    assertThat(rub.status()).isEqualTo(200);
    assertThat(rub.json().get("currency").asText()).isEqualTo("RUB");
    JsonNode after = summary(admin); // the cache was invalidated by the rate change
    assertMoney(after.at("/approvedConverted/amount"), "1950.00"); // 950 + 1000
    assertThat(after.at("/approvedConverted/notConverted")).isEmpty();

    // a manual rate on the very date of the ECB rate takes precedence
    var usd = api.put("/api/v1/fx/manual", admin.token(), Map.of("currency", "USD", "date", "2026-03-13", "perEur", "1.00"));
    assertThat(summary(admin).at("/approvedConverted/amount").decimalValue()).isEqualByComparingTo("2150.00"); // 950 + 1200

    JsonNode status = api.get("/api/v1/fx", admin.token()).json();
    assertThat(status.get("manualRates")).hasSize(2);
    assertThat(status.get("ecbEnabled").asBoolean()).isTrue();
    assertThat(status.hasNonNull("ecbCoveredFrom")).isTrue();

    // deleting a rate falls back: ECB again for USD, "not converted" again for RUB
    assertThat(api.delete("/api/v1/fx/manual/" + usd.json().get("id").asLong(), admin.token()).status()).isEqualTo(204);
    assertThat(api.delete("/api/v1/fx/manual/" + rub.json().get("id").asLong(), admin.token()).status()).isEqualTo(204);
    JsonNode reverted = summary(admin);
    assertMoney(reverted.at("/approvedConverted/amount"), "1000.00");
    assertThat(reverted.at("/approvedConverted/notConverted/0/currency").asText()).isEqualTo("RUB");
    assertThat(api.delete("/api/v1/fx/manual/" + rub.json().get("id").asLong(), admin.token()).status()).isEqualTo(404);

    // another organisation never sees these rates
    Session other = api.registerOrg("Fx Other Inc");
    assertThat(api.get("/api/v1/fx", other.token()).json().get("manualRates")).isEmpty();
  }

  @Test
  void rateInputIsValidatedAndRestrictedToAdmins() {
    Session admin = api.registerOrg("Fx Rules Inc");
    Session approver = api.createUser(admin, "APPROVER");
    Map<String, Object> good = Map.of("currency", "RUB", "date", "2026-01-01", "perEur", "100");

    assertThat(api.put("/api/v1/fx/manual", approver.token(), good).status()).isEqualTo(403);
    assertThat(api.get("/api/v1/fx", approver.token()).status()).isEqualTo(403);
    assertThat(api.put("/api/v1/organization", approver.token(), Map.of("baseCurrency", "USD")).status()).isEqualTo(403);

    assertThat(api.put("/api/v1/fx/manual", admin.token(), Map.of("currency", "EUR", "date", "2026-01-01", "perEur", "1"))
        .status()).isEqualTo(400);
    assertThat(api.put("/api/v1/fx/manual", admin.token(), Map.of("currency", "ABC", "date", "2026-01-01", "perEur", "2"))
        .status()).isEqualTo(400); // not an ISO 4217 code
    assertThat(api.put("/api/v1/fx/manual", admin.token(), Map.of("currency", "RUB", "date", "2026-01-01", "perEur", "0"))
        .status()).isEqualTo(400);
    assertThat(api.put("/api/v1/fx/manual", admin.token(), Map.of("currency", "RUB", "date", "2999-01-01", "perEur", "100"))
        .status()).isEqualTo(400);
    assertThat(api.put("/api/v1/organization", admin.token(), Map.of("baseCurrency", "XYZ")).status()).isEqualTo(400);

    // everybody can read the organisation (the UI needs the base currency); only admins change it
    assertThat(api.get("/api/v1/organization", approver.token()).json().get("baseCurrency").asText()).isEqualTo("EUR");
    assertThat(api.put("/api/v1/fx/manual", admin.token(), good).status()).isEqualTo(200);
    assertThat(api.get("/api/v1/audit", admin.token()).json().toString()).contains("FX_RATE_SET");
  }

  @Test
  void changingTheBaseCurrencyReconvertsEverything() {
    Session admin = api.registerOrg("Fx Base Inc");
    Session approver = api.createUser(admin, "APPROVER");
    approved(admin, approver, "Usd Vendor", "USD", "2026-03-13", "1100.00");
    approved(admin, approver, "Gbp Vendor", "GBP", "2026-02-14", "800.00");
    approved(admin, approver, "Eur Vendor", "EUR", "2026-02-02", "500.00");
    assertMoney(summary(admin).at("/approvedConverted/amount"), "2416.67");

    var changed = api.put("/api/v1/organization", admin.token(), Map.of("baseCurrency", "usd"));
    assertThat(changed.status()).isEqualTo(200);
    assertThat(changed.json().get("baseCurrency").asText()).isEqualTo("USD");

    JsonNode s = summary(admin);
    assertThat(s.get("baseCurrency").asText()).isEqualTo("USD");
    // USD stays as is; EUR 500 on 2026-02-02 x 1.1 = 550; GBP 800 on 2026-02-14 / 0.8 x 1.1 = 1100
    assertMoney(s.at("/approvedConverted/amount"), "2750.00");
    assertThat(s.at("/approvedConverted/notConverted")).isEmpty();
    assertThat(api.get("/api/v1/analytics/spend-by-vendor?converted=true", admin.token()).json().get("currency").asText())
        .isEqualTo("USD");
  }

  @Test
  void anUnavailableRateSourceDegradesToNotConvertedAndRecovers() {
    Session admin = api.registerOrg("Fx Offline Inc");
    Session approver = api.createUser(admin, "APPROVER");
    // older than anything downloaded by the other tests, so a download is needed
    FakeFrankfurterForTests.failing(true);
    approved(admin, approver, "Usd Vendor", "USD", "2025-06-02", "1100.00");

    JsonNode offline = summary(admin);
    assertThat(api.get("/api/v1/analytics/summary", admin.token()).status()).isEqualTo(200);
    assertMoney(offline.at("/approvedConverted/amount"), "0.00");
    assertThat(offline.at("/approvedConverted/notConverted/0/currency").asText()).isEqualTo("USD");
    assertThat(api.get("/api/v1/fx", admin.token()).json().get("lastError").asText()).isNotBlank();

    FakeFrankfurterForTests.failing(false);
    approved(admin, approver, "Eur Vendor", "EUR", "2026-03-03", "10.00"); // invalidates the cached summary
    JsonNode online = summary(admin);
    assertMoney(online.at("/approvedConverted/amount"), "1010.00"); // 1100 / 1.1 + 10
    assertThat(online.at("/approvedConverted/notConverted")).isEmpty();
    assertThat(api.get("/api/v1/fx", admin.token()).json().hasNonNull("lastError")).isFalse();
  }
}
