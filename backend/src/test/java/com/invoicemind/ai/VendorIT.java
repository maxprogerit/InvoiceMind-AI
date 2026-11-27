package com.invoicemind.ai;

import static com.invoicemind.ai.support.FakeAiServiceForTests.line;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.FakeAiServiceForTests.Reply;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Vendor matching (tax id first, then name or alias, else a new UNVERIFIED vendor) and vendor confirmation. */
class VendorIT extends AbstractIntegrationTest {

  private static String id(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private JsonNode process(Session who, String vendor, String taxId, String number, String currency) {
    String sc = id("vendor");
    Reply extraction = FakeAiServiceForTests.okExtraction(vendor, number, "2026-06-01", null, currency, "100.00", "0.00",
        "100.00", List.of(line("Item", "1", "100.00", "100.00")), false, false);
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc),
        taxId == null ? extraction : FakeAiServiceForTests.withTaxId(extraction, taxId));
    return awaitStatus(who, upload(who, sc), "VALIDATED", "NEEDS_REVIEW", "FAILED");
  }

  private JsonNode vendors(Session who) {
    return api.get("/api/v1/vendors", who.token()).json();
  }

  @Test
  void anUnknownVendorIsCreatedUnverifiedAndMustBeConfirmedBeforeApproval() {
    Session admin = api.registerOrg("Unknown Vendor Inc");
    Session approver = api.createUser(admin, "APPROVER");
    JsonNode inv = process(admin, "Fjord Logistics AS", "NO 987 654 321 MVA", "FL-1", "NOK");

    assertThat(inv.path("vendor").path("status").asText()).isEqualTo("UNVERIFIED");
    assertThat(inv.get("status").asText()).isEqualTo("VALIDATED"); // a warning, not an error
    assertThat(inv.path("issues").findValuesAsText("code")).contains("VENDOR_UNVERIFIED");
    assertThat(inv.path("permissions").path("canApprove").asBoolean()).isFalse();
    JsonNode asApprover = api.get("/api/v1/invoices/" + inv.get("id").asText(), approver.token()).json();
    assertThat(asApprover.path("permissions").path("approvalBlockedReason").asText()).isEqualTo("Confirm the vendor before approving");
    Resp blocked = api.post("/api/v1/invoices/" + inv.get("id").asText() + "/approve", approver.token(), Map.of());
    assertThat(blocked.status()).isEqualTo(409);

    JsonNode vendor = vendors(admin).get(0);
    assertThat(vendor.get("taxId").asText()).isEqualTo("NO987654321MVA");
    assertThat(vendor.get("defaultCurrency").asText()).isEqualTo("NOK");
    assertThat(vendor.get("invoiceCount").asInt()).isEqualTo(1);

    Resp verified = api.post("/api/v1/vendors/" + vendor.get("id").asText() + "/verify", admin.token(), null);
    assertThat(verified.json().get("status").asText()).isEqualTo("VERIFIED");
    assertThat(verified.json().get("verifiedByName").isNull()).isFalse();
    // the open invoice is re-checked at once: the warning is gone and the approver can approve
    JsonNode after = api.get("/api/v1/invoices/" + inv.get("id").asText(), approver.token()).json();
    assertThat(after.path("issues").findValuesAsText("code")).doesNotContain("VENDOR_UNVERIFIED");
    assertThat(api.post("/api/v1/invoices/" + inv.get("id").asText() + "/approve", approver.token(), Map.of()).status())
        .isEqualTo(200);
  }

  @Test
  void theTaxIdWinsOverTheNameAndTheSameNameWithAnotherTaxIdIsAnotherCompany() {
    Session s = api.registerOrg("Matching Inc");
    JsonNode a = process(s, "Meridian Consulting Ltd", "GB123456789", "M-1", "GBP");
    JsonNode renamed = process(s, "Meridian Advisory Limited", "GB 123 456 789", "M-2", "GBP");
    JsonNode namesake = process(s, "Meridian Consulting Ltd", "IE9876543W", "IE-1", "EUR");
    JsonNode noTax = process(s, "meridian consulting ltd.", null, "M-3", "GBP");

    String vendorA = a.path("vendor").path("id").asText();
    assertThat(renamed.path("vendor").path("id").asText()).isEqualTo(vendorA);
    assertThat(namesake.path("vendor").path("id").asText()).isNotEqualTo(vendorA);
    assertThat(noTax.path("vendor").path("id").asText()).isEqualTo(vendorA); // name match, oldest vendor first
    assertThat(vendors(s).size()).isEqualTo(2);
  }

  @Test
  void documentsOfANewVendorProcessedAtTheSameTimeShareOneVendor() {
    Session s = api.registerOrg("Concurrent Vendor Inc");
    List<String> scenarios = new java.util.ArrayList<>();
    for (int i = 0; i < 4; i++) {
      String sc = id("conc");
      FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc), FakeAiServiceForTests.okExtraction(
          "Simultaneous Supplies GmbH", "SS-" + i, "2026-06-0" + (i + 1), null, "EUR", "10.0" + i, "0.00", "10.0" + i,
          List.of(line("Item", "1", "10.0" + i, "10.0" + i)), false, false));
      scenarios.add(sc);
    }
    List<UUID> ids = scenarios.parallelStream().map(sc -> upload(s, sc)).toList();
    ids.forEach(i -> awaitStatus(s, i, "VALIDATED", "NEEDS_REVIEW", "FAILED"));
    assertThat(vendors(s).size()).isEqualTo(1);
    assertThat(vendors(s).get(0).get("invoiceCount").asInt()).isEqualTo(4);
  }

  @Test
  void anAliasMatchesLikeTheVendorsOwnName() {
    Session s = api.registerOrg("Alias Inc");
    JsonNode first = process(s, "Atlas Office Supplies Limited", null, "AO-1", "EUR");
    String vendorId = first.path("vendor").path("id").asText();
    Resp alias = api.post("/api/v1/vendors/" + vendorId + "/aliases", s.token(), Map.of("alias", "Atlas Office Ltd"));
    assertThat(alias.status()).isEqualTo(200);
    assertThat(alias.json().path("aliases").findValuesAsText("alias")).containsExactly("Atlas Office Ltd");

    JsonNode viaAlias = process(s, "ATLAS OFFICE LTD.", null, "AO-2", "EUR");
    assertThat(viaAlias.path("vendor").path("id").asText()).isEqualTo(vendorId);

    // an alias that is another vendor's name is refused
    JsonNode other = process(s, "Borealis Paper GmbH", null, "BP-1", "EUR");
    Resp clash = api.post("/api/v1/vendors/" + vendorId + "/aliases", s.token(), Map.of("alias", "Borealis Paper GmbH"));
    assertThat(clash.status()).isEqualTo(409);
    assertThat(other.path("vendor").path("id").asText()).isNotEqualTo(vendorId);
  }

  @Test
  void vendorDataIsCorrectedByPeopleAndIsolatedPerOrganisation() {
    Session s = api.registerOrg("Edit Vendor Inc");
    Session stranger = api.registerOrg("Stranger Inc");
    String a = process(s, "Quill & Co", "FR12345678901", "Q-1", "EUR").path("vendor").path("id").asText();
    String b = process(s, "Inkwell SARL", null, "I-1", "EUR").path("vendor").path("id").asText();

    Resp taken = api.put("/api/v1/vendors/" + b, s.token(), Map.of("name", "Inkwell SARL", "taxId", "FR 12345678901"));
    assertThat(taken.status()).isEqualTo(409);
    Resp ok = api.put("/api/v1/vendors/" + b, s.token(), Map.of("name", "Inkwell S.A.R.L.", "taxId", "FR99887766554",
        "defaultCurrency", "eur"));
    assertThat(ok.status()).isEqualTo(200);
    assertThat(ok.json().get("defaultCurrency").asText()).isEqualTo("EUR");
    assertThat(ok.json().get("taxId").asText()).isEqualTo("FR99887766554");

    assertThat(api.post("/api/v1/vendors/" + a + "/verify", stranger.token(), null).status()).isEqualTo(404);
    assertThat(vendors(stranger).size()).isZero();
    assertThat(api.get("/api/v1/audit?entityType=VENDOR", s.token()).status()).isEqualTo(200);
  }
}
