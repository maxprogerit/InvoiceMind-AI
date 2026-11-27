package com.invoicemind.ai;

import static com.invoicemind.ai.support.FakeAiServiceForTests.line;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.FakeAiServiceForTests.Reply;
import com.invoicemind.ai.support.TestApi;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Duplicates are recognised by meaning, so nobody pays the same bill twice: the rules work on the vendor, the
 * invoice number, the total and the date, not only on the file's bytes.
 */
class DuplicatesIT extends AbstractIntegrationTest {

  private static String id(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private static Reply invoice(String vendor, String number, String date, String total) {
    return FakeAiServiceForTests.okExtraction(vendor, number, date, null, "EUR", total, "0.00", total,
        List.of(line("Services", "1", total, total)), false, false);
  }

  private JsonNode process(Session who, String scenario, Reply ocr, Reply extraction) {
    FakeAiServiceForTests.scenario(scenario, ocr, extraction);
    return awaitStatus(who, upload(who, scenario), "VALIDATED", "NEEDS_REVIEW", "FAILED");
  }

  private static List<String> codes(JsonNode invoice) {
    return invoice.path("issues").findValuesAsText("code");
  }

  @Test
  void theSameInvoiceRescannedAsADifferentFileIsAnExactDuplicate() {
    Session s = api.registerOrg("Rescan Inc");
    String first = id("orig"), rescan = id("rescan");
    JsonNode original = process(s, first, FakeAiServiceForTests.okOcr(first),
        invoice("Nordlicht Software GmbH", "NL-2026-0412", "2026-03-14", "1820.70"));
    // the paper copy, scanned: different bytes, OCR text, the vendor name read with different case and punctuation
    JsonNode copy = process(s, rescan, FakeAiServiceForTests.scannedOcr(rescan, "NORDLICHT SOFTWARE GMBH. NL-2026-0412"),
        invoice("NORDLICHT SOFTWARE GMBH.", "NL-2026-0412", "2026-03-14", "1820.70"));

    assertThat(copy.path("ocr").path("source").asText()).isEqualTo("ocr");
    assertThat(copy.path("duplicate").path("status").asText()).isEqualTo("DUPLICATE");
    assertThat(copy.path("duplicate").path("ofId").asText()).isEqualTo(original.get("id").asText());
    assertThat(copy.get("status").asText()).isEqualTo("NEEDS_REVIEW");
    assertThat(codes(copy)).contains("DUPLICATE_INVOICE").doesNotContain("DUPLICATE_FILE");
    JsonNode issue = copy.path("issues").findParents("code").stream()
        .filter(i -> "DUPLICATE_INVOICE".equals(i.path("code").asText())).findFirst().orElseThrow();
    assertThat(issue.path("relatedInvoiceId").asText()).isEqualTo(original.get("id").asText());
    assertThat(issue.path("message").asText()).contains("same vendor and number");

    // the original is never flagged because a copy came in later
    JsonNode again = api.get("/api/v1/invoices/" + original.get("id").asText(), s.token()).json();
    assertThat(again.path("duplicate").path("status").asText()).isEqualTo("NONE");

    // approval stays blocked even with a confirmed vendor, and a person cannot wave an exact duplicate through
    api.verifyVendorOf(s.token(), copy.get("id").asText());
    Resp approve = api.post("/api/v1/invoices/" + copy.get("id").asText() + "/approve", s.token(), Map.of());
    assertThat(approve.status()).isEqualTo(409);
    assertThat(approve.json().get("message").asText()).contains("duplicate");
    Resp clear = api.post("/api/v1/invoices/" + copy.get("id").asText() + "/duplicate-decision", s.token(),
        Map.of("decision", "NOT_DUPLICATE"));
    assertThat(clear.status()).isEqualTo(409);
    assertThat(clear.json().get("code").asText()).isEqualTo("exact_duplicate");
  }

  @Test
  void aRescanWhoseVendorNameIsMisreadIsStillCaughtThroughTheTaxId() {
    Session s = api.registerOrg("Tax Id Inc");
    String first = id("tax-a"), second = id("tax-b");
    JsonNode a = process(s, first, FakeAiServiceForTests.okOcr(first),
        FakeAiServiceForTests.withTaxId(invoice("Kuhn Elektrotechnik GmbH", "KE-30871", "2026-04-02", "640.00"), "DE 811 234 567"));
    JsonNode b = process(s, second, FakeAiServiceForTests.scannedOcr(second, "Kuhn Elektrolechnik GmhH"),
        FakeAiServiceForTests.withTaxId(invoice("Kuhn Elektrolechnik GmhH", "KE-30871", "2026-04-02", "640.00"), "DE811234567"));

    assertThat(b.path("vendor").path("id").asText()).isEqualTo(a.path("vendor").path("id").asText());
    assertThat(b.path("vendorTaxId").asText()).isEqualTo("DE811234567");
    assertThat(b.path("duplicate").path("status").asText()).isEqualTo("DUPLICATE");
  }

  @Test
  void sameVendorAndTotalAFewDaysApartWithADifferentNumberIsAPossibleDuplicate() {
    Session admin = api.registerOrg("Possible Inc");
    Session accountant = api.createUser(admin, "USER");
    Session approver = api.createUser(admin, "APPROVER");
    String a = id("pos-a"), b = id("pos-b"), far = id("pos-far"), other = id("pos-other");
    JsonNode first = process(accountant, a, FakeAiServiceForTests.okOcr(a), invoice("Brightwave Analytics Inc.", "BW-1001", "2026-05-04", "1428.00"));
    JsonNode second = process(accountant, b, FakeAiServiceForTests.okOcr(b), invoice("Brightwave Analytics Inc.", "BW-1007", "2026-05-07", "1428.00"));
    JsonNode outside = process(accountant, far, FakeAiServiceForTests.okOcr(far), invoice("Brightwave Analytics Inc.", "BW-1019", "2026-05-20", "1428.00"));
    JsonNode otherTotal = process(accountant, other, FakeAiServiceForTests.okOcr(other), invoice("Brightwave Analytics Inc.", "BW-1008", "2026-05-08", "1429.00"));

    assertThat(first.path("duplicate").path("status").asText()).isEqualTo("NONE");
    assertThat(second.path("duplicate").path("status").asText()).isEqualTo("POSSIBLE_DUPLICATE");
    assertThat(second.path("duplicate").path("ofId").asText()).isEqualTo(first.get("id").asText());
    assertThat(second.get("status").asText()).isEqualTo("NEEDS_REVIEW");
    assertThat(second.path("issues").findValuesAsText("message")).anyMatch(m -> m.contains("3 days apart"));
    assertThat(second.path("permissions").path("canDecideDuplicate").asBoolean()).isTrue();
    assertThat(outside.path("duplicate").path("status").asText()).isEqualTo("NONE");     // 16 days apart: outside 7
    assertThat(otherTotal.path("duplicate").path("status").asText()).isEqualTo("NONE");  // another amount

    // the accountant checks with the vendor: it is a separate delivery
    Resp cleared = api.post("/api/v1/invoices/" + second.get("id").asText() + "/duplicate-decision", accountant.token(),
        Map.of("decision", "NOT_DUPLICATE", "comment", "Second delivery, confirmed by phone"));
    assertThat(cleared.status()).isEqualTo(200);
    assertThat(cleared.json().path("duplicate").path("status").asText()).isEqualTo("NONE");
    assertThat(cleared.json().path("duplicate").path("clearedByName").isNull()).isFalse();
    assertThat(cleared.json().get("status").asText()).isEqualTo("VALIDATED");
    assertThat(api.get("/api/v1/invoices/" + second.get("id").asText() + "/audit", accountant.token()).json()
        .findValuesAsText("action")).contains("DUPLICATE_CLEARED");

    // the decision sticks: a later correction does not bring the finding back
    Resp corrected = api.put("/api/v1/invoices/" + second.get("id").asText(), accountant.token(),
        PipelineIT.correction(cleared.json(), Map.of()));
    assertThat(corrected.json().path("duplicate").path("status").asText()).isEqualTo("NONE");

    api.verifyVendorOf(approver.token(), second.get("id").asText());
    assertThat(api.post("/api/v1/invoices/" + second.get("id").asText() + "/approve", approver.token(), Map.of()).status())
        .isEqualTo(200);
  }

  @Test
  void anOlderInvoiceCorrectedToMatchANewerOneFlagsTheNewerOne() {
    // the rules only look back in time, so a correction of the older invoice must re-check the newer ones
    Session s = api.registerOrg("Late Match Inc");
    String a = id("late-a"), b = id("late-b");
    JsonNode older = process(s, a, FakeAiServiceForTests.okOcr(a), invoice("Kestrel Print Ltd", "KP-1", "2026-03-14", "1500.00"));
    JsonNode newer = process(s, b, FakeAiServiceForTests.okOcr(b), invoice("Kestrel Print Ltd", "KP-2", "2026-03-14", "1428.00"));
    assertThat(newer.path("duplicate").path("status").asText()).isEqualTo("NONE");

    Resp fixed = api.put("/api/v1/invoices/" + older.get("id").asText(), s.token(),
        PipelineIT.correction(older, Map.of("total", new java.math.BigDecimal("1428.00"), "subtotal", new java.math.BigDecimal("1428.00"))));
    assertThat(fixed.status()).isEqualTo(200);
    assertThat(fixed.json().path("duplicate").path("status").asText()).isEqualTo("NONE"); // the older one wins

    JsonNode recheck = api.get("/api/v1/invoices/" + newer.get("id").asText(), s.token()).json();
    assertThat(recheck.path("duplicate").path("status").asText()).isEqualTo("POSSIBLE_DUPLICATE");
    assertThat(recheck.path("duplicate").path("ofId").asText()).isEqualTo(older.get("id").asText());
    assertThat(recheck.get("status").asText()).isEqualTo("NEEDS_REVIEW");
  }

  @Test
  void confirmingAPossibleDuplicateRejectsIt() {
    Session s = api.registerOrg("Confirm Dup Inc");
    String a = id("cd-a"), b = id("cd-b");
    JsonNode first = process(s, a, FakeAiServiceForTests.okOcr(a), invoice("Acme Supplies", "A-1", "2026-02-01", "99.90"));
    JsonNode second = process(s, b, FakeAiServiceForTests.okOcr(b), invoice("Acme Supplies", "A-1b", "2026-02-02", "99.90"));
    assertThat(second.path("duplicate").path("status").asText()).isEqualTo("POSSIBLE_DUPLICATE");

    Resp r = api.post("/api/v1/invoices/" + second.get("id").asText() + "/duplicate-decision", s.token(),
        Map.of("decision", "DUPLICATE", "comment", "Vendor re-sent it with a suffix"));
    assertThat(r.status()).isEqualTo(200);
    assertThat(r.json().get("status").asText()).isEqualTo("REJECTED");
    assertThat(r.json().get("decisionComment").asText()).contains("Duplicate of Invoice A-1").contains("suffix");
    assertThat(first.get("status").asText()).isEqualTo("VALIDATED");
  }

  @Test
  void anIdenticalFileReusesTheStoredResultAndNeverCallsTheModelAgain() {
    Session s = api.registerOrg("Same File Inc");
    String sc = id("same");
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc), invoice("Helvetia Druck AG", "HD-77", "2026-01-15", "312.50"));
    byte[] bytes = TestApi.pdf(sc);
    UUID first = UUID.fromString(api.upload(s.token(), bytes, "scan.pdf").json().get("id").asText());
    awaitStatus(s, first, "VALIDATED", "NEEDS_REVIEW", "FAILED");
    UUID second = UUID.fromString(api.upload(s.token(), bytes, "scan-again.pdf").json().get("id").asText());
    JsonNode copy = awaitStatus(s, second, "VALIDATED", "NEEDS_REVIEW", "FAILED");

    assertThat(FakeAiServiceForTests.extractCalls(sc)).isEqualTo(1);
    assertThat(FakeAiServiceForTests.ocrCalls(sc)).isEqualTo(1);
    assertThat(copy.path("duplicate").path("reusedFrom").asText()).isEqualTo(first.toString());
    assertThat(copy.path("duplicate").path("status").asText()).isEqualTo("DUPLICATE");
    assertThat(codes(copy)).contains("DUPLICATE_FILE", "DUPLICATE_INVOICE");
    assertThat(copy.get("vendorName").asText()).isEqualTo("Helvetia Druck AG");
    assertThat(copy.path("usage").path("inputTokens").asInt()).isZero();
    assertThat(api.get("/api/v1/invoices/" + second + "/audit", s.token()).json().findValuesAsText("action"))
        .contains("EXTRACTION_REUSED");
  }

  @Test
  void twoIdenticalFilesUploadedAtOnceCallTheModelOnce() {
    Session s = api.registerOrg("Burst Inc");
    String sc = id("burst");
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc), invoice("Burst Ltd", "B-1", "2026-01-20", "50.00"));
    byte[] bytes = TestApi.pdf(sc);
    var uploads = List.of(
        CompletableFuture.supplyAsync(() -> api.upload(s.token(), bytes, "a.pdf")),
        CompletableFuture.supplyAsync(() -> api.upload(s.token(), bytes, "b.pdf")));
    List<UUID> ids = uploads.stream().map(CompletableFuture::join).map(r -> UUID.fromString(r.json().get("id").asText())).toList();
    ids.forEach(i -> awaitStatus(s, i, "VALIDATED", "NEEDS_REVIEW", "FAILED"));
    assertThat(FakeAiServiceForTests.extractCalls(sc)).isEqualTo(1);
  }

  @Test
  void anotherOrganisationsIdenticalFileIsNotReused() {
    Session a = api.registerOrg("Org A Files"), b = api.registerOrg("Org B Files");
    String sc = id("tenant");
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc), invoice("Shared Vendor", "S-1", "2026-01-21", "10.00"));
    byte[] bytes = TestApi.pdf(sc);
    awaitStatus(a, UUID.fromString(api.upload(a.token(), bytes, "x.pdf").json().get("id").asText()), "VALIDATED", "NEEDS_REVIEW");
    JsonNode inB = awaitStatus(b, UUID.fromString(api.upload(b.token(), bytes, "x.pdf").json().get("id").asText()), "VALIDATED", "NEEDS_REVIEW");
    assertThat(FakeAiServiceForTests.extractCalls(sc)).isEqualTo(2);
    assertThat(inB.path("duplicate").path("status").asText()).isEqualTo("NONE");
  }
}
