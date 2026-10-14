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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * End-to-end through the real stack: HTTP upload -> Postgres -> Kafka -> consumer -> (fake) ai-service ->
 * deterministic validation -> status events. Only the ai-service itself is a test double.
 */
class PipelineIT extends AbstractIntegrationTest {

  static String id(String prefix) { return prefix + "-" + UUID.randomUUID().toString().substring(0, 8); }

  static Reply extraction(String vendor, String number, String subtotal, String tax, String total) {
    return FakeAiServiceForTests.okExtraction(vendor, number, "2026-03-14", "2026-04-13", "EUR", subtotal, tax, total,
        List.of(line("Consulting", "10", "100.00", "1000.00")), false, false);
  }

  /** Registers a scenario whose document extracts to the given values; returns the scenario id. */
  static String scenario(String vendor, String number, String subtotal, String tax, String total) {
    String id = id("pipe");
    FakeAiServiceForTests.scenario(id, FakeAiServiceForTests.okOcr(id), extraction(vendor, number, subtotal, tax, total));
    return id;
  }

  static List<String> codes(JsonNode invoice) {
    List<String> codes = new ArrayList<>();
    invoice.get("issues").forEach(i -> codes.add(i.get("code").asText()));
    return codes;
  }

  static Map<String, Object> correction(JsonNode inv, Map<String, Object> overrides) {
    Map<String, Object> body = new LinkedHashMap<>();
    for (String f : List.of("vendorName", "invoiceNumber", "invoiceDate", "dueDate", "currency", "subtotal", "tax", "total")) {
      body.put(f, inv.get(f) == null || inv.get(f).isNull() ? null : inv.get(f).isNumber() ? inv.get(f).decimalValue() : inv.get(f).asText());
    }
    List<Map<String, Object>> lines = new ArrayList<>();
    inv.get("lineItems").forEach(l -> lines.add(Map.of("description", l.get("description").asText(),
        "quantity", l.get("quantity").decimalValue(), "unitPrice", l.get("unitPrice").decimalValue(),
        "total", l.get("total").decimalValue())));
    body.put("lineItems", lines);
    body.put("version", inv.get("version").asLong());
    body.putAll(overrides);
    return body;
  }

  @Test
  void consistentInvoiceFlowsFromUploadToValidated() {
    Session admin = api.registerOrg("Happy Inc");
    Session approver = api.createUser(admin, "APPROVER");
    Session user = api.createUser(admin, "USER");
    String sc = scenario("ACME GmbH", "INV-" + id("n"), "1000.00", "190.00", "1190.00");

    UUID invoice = upload(user, sc);
    JsonNode inv = awaitStatus(user, invoice, "VALIDATED", "NEEDS_REVIEW", "FAILED");

    assertThat(inv.get("status").asText()).isEqualTo("VALIDATED");
    assertThat(inv.get("vendorName").asText()).isEqualTo("ACME GmbH");
    assertThat(inv.get("total").decimalValue()).isEqualByComparingTo("1190.00");
    assertThat(inv.get("subtotal").decimalValue()).isEqualByComparingTo("1000.00");
    assertThat(inv.get("tax").decimalValue()).isEqualByComparingTo("190.00");
    assertThat(inv.get("currency").asText()).isEqualTo("EUR");
    assertThat(inv.get("invoiceDate").asText()).isEqualTo("2026-03-14");
    assertThat(inv.get("issues")).isEmpty();
    assertThat(inv.get("lineItems")).hasSize(1);
    assertThat(inv.at("/ocr/engine").asText()).isEqualTo("tesseract");
    assertThat(inv.at("/ocr/pageCount").asInt()).isEqualTo(1);
    assertThat(inv.at("/usage/model").asText()).isEqualTo("test-model");
    assertThat(inv.at("/usage/inputTokens").asInt()).isEqualTo(1200);
    assertThat(inv.at("/usage/costUsd").decimalValue()).isEqualByComparingTo("0.00027");
    assertThat(inv.get("processingMs").asLong()).isGreaterThanOrEqualTo(0);
    assertThat(inv.get("fieldConfidence").get("total").asDouble()).isEqualTo(0.95);
    assertThat(inv.get("originalFilename").asText()).endsWith(".pdf");

    // the OCR text really travelled to the extraction step
    assertThat(FakeAiServiceForTests.textSentToExtract(sc)).contains("ACME GmbH").contains("SCENARIO:" + sc);
    assertThat(api.get("/api/v1/invoices/" + invoice + "/ocr-text", user.token()).json().get("text").asText())
        .contains("SCENARIO:" + sc);

    // server-computed permissions
    JsonNode perms = inv.get("permissions");
    assertThat(perms.get("canEdit").asBoolean()).isTrue();
    assertThat(perms.get("canApprove").asBoolean()).isFalse(); // uploader is a plain USER
    JsonNode approverView = api.get("/api/v1/invoices/" + invoice, approver.token()).json();
    assertThat(approverView.at("/permissions/canApprove").asBoolean()).isTrue();

    // audit trail is written by the pipeline, in order
    List<String> actions = new ArrayList<>();
    api.get("/api/v1/invoices/" + invoice + "/audit", user.token()).json().forEach(a -> actions.add(a.get("action").asText()));
    assertThat(actions).containsSubsequence("INVOICE_UPLOADED", "PROCESSING_STARTED", "EXTRACTION_COMPLETED", "VALIDATION_COMPLETED");

    // notifications: approver is told it awaits a decision, uploader that it was processed
    assertThat(api.get("/api/v1/notifications", approver.token()).json().get("items").toString()).contains("AWAITING_APPROVAL");
    assertThat(api.get("/api/v1/notifications", user.token()).json().get("items").toString()).contains("PROCESSED");
  }

  @Test
  void originalFileIsStoredAndServedBackSafely() {
    Session s = api.registerOrg("File Inc");
    String sc = scenario("ACME GmbH", "INV-" + id("n"), "1000.00", "190.00", "1190.00");
    byte[] content = TestApi.pdf(sc);
    Resp up = api.upload(s.token(), content, "../../My Invoice.PDF");
    assertThat(up.status()).isEqualTo(202);
    assertThat(up.json().get("originalFilename").asText()).isEqualTo("My Invoice.pdf"); // path stripped, ext from content
    assertThat(up.json().get("status").asText()).isEqualTo("UPLOADED");
    UUID invoice = UUID.fromString(up.json().get("id").asText());

    Resp file = api.get("/api/v1/invoices/" + invoice + "/file", s.token());
    assertThat(file.status()).isEqualTo(200);
    assertThat(file.bytes()).isEqualTo(content);
    assertThat(file.headers().getContentType().toString()).isEqualTo("application/pdf");
    assertThat(file.headers().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(file.headers().getFirst("Content-Disposition")).startsWith("inline");
    assertThat(file.headers().getFirst("Content-Security-Policy")).contains("sandbox");
  }

  @Test
  void inconsistentTotalsRequireReviewAndCanBeCorrectedByAHuman() {
    Session admin = api.registerOrg("Mismatch Inc");
    Session user = api.createUser(admin, "USER");
    String sc = scenario("ACME GmbH", "INV-" + id("n"), "1000.00", "190.00", "1500.00"); // 1000 + 190 != 1500

    UUID invoice = upload(user, sc);
    JsonNode inv = awaitStatus(user, invoice, "NEEDS_REVIEW", "VALIDATED", "FAILED");
    assertThat(inv.get("status").asText()).isEqualTo("NEEDS_REVIEW");
    assertThat(codes(inv)).contains("TOTAL_MISMATCH");
    JsonNode issue = inv.get("issues").get(0);
    assertThat(issue.get("severity").asText()).isEqualTo("ERROR");
    assertThat(issue.get("message").asText()).contains("1190.00").contains("1500.00");

    // a stale version is refused
    Map<String, Object> stale = correction(inv, Map.of("total", new BigDecimal("1190.00"), "version", 999L));
    assertThat(api.put("/api/v1/invoices/" + invoice, user.token(), stale).status()).isEqualTo(409);

    // fixing the number clears the error and moves the invoice to VALIDATED
    Resp fixed = api.put("/api/v1/invoices/" + invoice, user.token(), correction(inv, Map.of("total", new BigDecimal("1190.00"))));
    assertThat(fixed.status()).isEqualTo(200);
    assertThat(fixed.json().get("status").asText()).isEqualTo("VALIDATED");
    assertThat(fixed.json().get("humanReviewed").asBoolean()).isTrue();
    assertThat(fixed.json().get("issues")).isEmpty();

    // a still-wrong value stays in review
    Resp bad = api.put("/api/v1/invoices/" + invoice, user.token(),
        correction(fixed.json(), Map.of("total", new BigDecimal("1.00"))));
    assertThat(bad.json().get("status").asText()).isEqualTo("NEEDS_REVIEW");

    // the correction is audited with before/after values
    String trail = api.get("/api/v1/invoices/" + invoice + "/audit", user.token()).json().toString();
    assertThat(trail).contains("INVOICE_CORRECTED").contains("1500.00 -> 1190.00");
  }

  @Test
  void promptInjectionInTheDocumentCannotSlipThroughToApproval() {
    Session admin = api.registerOrg("Injection Inc");
    Session approver = api.createUser(admin, "APPROVER");
    Session user = api.createUser(admin, "USER");
    String sc = id("inject");
    // the "model" obeyed text hidden in the invoice and reported a tiny total; ai-service also flagged it
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc), FakeAiServiceForTests.okExtraction(
        "ACME GmbH", "INV-" + id("n"), "2026-03-14", "2026-04-13", "EUR", "1000.00", "190.00", "0.01",
        List.of(line("Consulting", "10", "100.00", "1000.00")), true, true));

    UUID invoice = upload(user, sc);
    JsonNode inv = awaitStatus(approver, invoice, "NEEDS_REVIEW", "VALIDATED", "FAILED");
    assertThat(inv.get("status").asText()).isEqualTo("NEEDS_REVIEW");
    assertThat(inv.get("injectionSuspected").asBoolean()).isTrue();
    assertThat(codes(inv)).contains("TOTAL_MISMATCH", "PROMPT_INJECTION_SUSPECTED", "MODEL_FLAGGED");

    // not approvable in this state, and the API says why
    Resp approve = api.post("/api/v1/invoices/" + invoice + "/approve", approver.token(), Map.of());
    assertThat(approve.status()).isEqualTo(409);
    assertThat(approve.json().get("code").asText()).isEqualTo("invoice_not_approvable");

    // only after a human corrects the figure (and thereby takes responsibility) can it be approved
    Resp fixed = api.put("/api/v1/invoices/" + invoice, approver.token(), correction(inv, Map.of("total", new BigDecimal("1190.00"))));
    assertThat(fixed.json().get("status").asText()).isEqualTo("VALIDATED");
    assertThat(codes(fixed.json())).doesNotContain("PROMPT_INJECTION_SUSPECTED");
    assertThat(api.post("/api/v1/invoices/" + invoice + "/approve", approver.token(), Map.of("comment", "checked manually"))
        .status()).isEqualTo(200);
  }

  @Test
  void unreadableDocumentFailsOnceAndCanBeRetried() {
    Session s = api.registerOrg("Fail Inc");
    String sc = id("unreadable");
    FakeAiServiceForTests.scenario(sc,
        Reply.json(422, Map.of("error", Map.of("code", "unreadable_file", "message", "File could not be decoded"))),
        extraction("ACME GmbH", "INV-" + id("n"), "1000.00", "190.00", "1190.00"));

    UUID invoice = upload(s, sc);
    JsonNode failed = awaitStatus(s, invoice, "FAILED", "VALIDATED");
    assertThat(failed.get("status").asText()).isEqualTo("FAILED");
    assertThat(failed.get("failureReason").asText()).isEqualTo("File could not be decoded");
    assertThat(failed.at("/permissions/canRetry").asBoolean()).isTrue();
    assertThat(FakeAiServiceForTests.ocrCalls(sc)).isEqualTo(1); // a 422 is not transient: no retries
    assertThat(api.get("/api/v1/notifications", s.token()).json().toString()).contains("FAILED");

    // fix the "ai-service" and retry
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc),
        extraction("ACME GmbH", "INV-" + id("n"), "1000.00", "190.00", "1190.00"));
    assertThat(api.post("/api/v1/invoices/" + invoice + "/retry", s.token(), null).status()).isEqualTo(200);
    JsonNode retried = awaitStatus(s, invoice, "VALIDATED", "FAILED");
    assertThat(retried.get("status").asText()).isEqualTo("VALIDATED");
    assertThat(retried.hasNonNull("failureReason")).isFalse();

    assertThat(api.post("/api/v1/invoices/" + invoice + "/retry", s.token(), null).status()).isEqualTo(409);
  }

  @Test
  void transientAiOutageIsRetriedThenFailsGracefully() {
    Session s = api.registerOrg("Outage Inc");
    String sc = id("outage");
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc),
        Reply.json(503, Map.of("error", Map.of("code", "llm_rate_limited", "message", "The language model is rate limited, retry later"))));

    UUID invoice = upload(s, sc);
    JsonNode failed = awaitStatus(s, invoice, "FAILED", "VALIDATED");
    assertThat(failed.get("status").asText()).isEqualTo("FAILED");
    assertThat(failed.get("failureReason").asText()).contains("rate limited");
    assertThat(FakeAiServiceForTests.extractCalls(sc)).isEqualTo(2); // app.ai.retry-attempts=2 in the test profile
  }

  @Test
  void emptyOcrTextFailsInsteadOfInventingData() {
    Session s = api.registerOrg("Blank Inc");
    String sc = id("blank");
    FakeAiServiceForTests.scenario(sc, Reply.json(200, Map.of("text", "   ", "pages", List.of(), "page_count", 1,
        "confidence", 0.0, "engine", "tesseract", "mime_type", "image/png")),
        extraction("Made Up Ltd", "INV-FAKE", "1.00", "0.00", "1.00"));
    UUID invoice = upload(s, sc);
    JsonNode failed = awaitStatus(s, invoice, "FAILED", "VALIDATED");
    assertThat(failed.get("status").asText()).isEqualTo("FAILED");
    assertThat(failed.get("failureReason").asText()).contains("No text could be read");
    assertThat(FakeAiServiceForTests.extractCalls(sc)).isZero(); // never asked the LLM to guess
  }

  @Test
  void duplicateInvoiceIsFlaggedAgainstTheOlderOne() {
    Session s = api.registerOrg("Dup Inc");
    String number = "INV-DUP-" + id("n");
    UUID first = upload(s, scenario("ACME GmbH", number, "1000.00", "190.00", "1190.00"));
    assertThat(awaitStatus(s, first, "VALIDATED", "NEEDS_REVIEW").get("status").asText()).isEqualTo("VALIDATED");

    // same vendor (different spelling) and number, different file
    UUID second = upload(s, scenario("acme gmbh.", number.toLowerCase(), "1000.00", "190.00", "1190.00"));
    JsonNode inv = awaitStatus(s, second, "VALIDATED", "NEEDS_REVIEW");
    assertThat(inv.get("status").asText()).isEqualTo("NEEDS_REVIEW");
    assertThat(codes(inv)).contains("DUPLICATE_INVOICE");
    assertThat(inv.get("issues").toString()).contains(first.toString());
    // the older invoice is not flagged
    assertThat(codes(api.get("/api/v1/invoices/" + first, s.token()).json())).doesNotContain("DUPLICATE_INVOICE");
  }

  /** Scenario whose document is a credit note; {@code sign} is what the document prints ("" or "-"). */
  static String creditScenario(String number, String sign) {
    String id = id("credit");
    FakeAiServiceForTests.scenario(id, FakeAiServiceForTests.okOcr(id), FakeAiServiceForTests.okExtraction(
        "credit_note", "ACME GmbH", number, "2026-03-20", null, "EUR", sign + "200.00", sign + "38.00", sign + "238.00",
        List.of(line("Returned hours", "2", sign + "100.00", sign + "200.00")), false, false));
    return id;
  }

  @Test
  void creditNotesAreStoredNegativeWhicheverSignWasPrinted() {
    Session admin = api.registerOrg("Credit Inc");
    Session user = api.createUser(admin, "USER");
    for (String printed : List.of("", "-")) {
      UUID invoice = upload(user, creditScenario("CN-" + id("n"), printed));
      JsonNode inv = awaitStatus(user, invoice, "VALIDATED", "NEEDS_REVIEW", "FAILED");

      assertThat(inv.get("status").asText()).as("printed sign '%s': %s", printed, codes(inv)).isEqualTo("VALIDATED");
      assertThat(inv.get("documentType").asText()).isEqualTo("CREDIT_NOTE");
      assertThat(inv.get("subtotal").decimalValue()).isEqualByComparingTo("-200.00");
      assertThat(inv.get("tax").decimalValue()).isEqualByComparingTo("-38.00");
      assertThat(inv.get("total").decimalValue()).isEqualByComparingTo("-238.00");
      JsonNode line = inv.get("lineItems").get(0);
      assertThat(line.get("quantity").decimalValue()).isEqualByComparingTo("2");
      assertThat(line.get("unitPrice").decimalValue()).isEqualByComparingTo("-100.00");
      assertThat(line.get("total").decimalValue()).isEqualByComparingTo("-200.00");
    }
    assertThat(api.get("/api/v1/notifications", user.token()).json().toString()).contains("Credit note CN-");
  }

  @Test
  void creditNoteDoesNotCollideWithAnInvoiceOfTheSameNumberAndCanBeFilteredAndExported() {
    Session s = api.registerOrg("Credit Filter Inc");
    String number = "X-" + id("n");
    UUID inv = upload(s, scenario("ACME GmbH", number, "1000.00", "190.00", "1190.00"));
    awaitStatus(s, inv, "VALIDATED", "NEEDS_REVIEW");
    UUID credit = upload(s, creditScenario(number, "-"));
    JsonNode cn = awaitStatus(s, credit, "VALIDATED", "NEEDS_REVIEW");
    assertThat(codes(cn)).doesNotContain("DUPLICATE_INVOICE");

    assertThat(api.get("/api/v1/invoices?documentType=CREDIT_NOTE", s.token()).json().get("totalItems").asInt()).isEqualTo(1);
    assertThat(api.get("/api/v1/invoices?documentType=INVOICE", s.token()).json().get("totalItems").asInt()).isEqualTo(1);
    assertThat(api.get("/api/v1/invoices", s.token()).json().get("items").toString()).contains("CREDIT_NOTE");

    Resp csv = api.get("/api/v1/export/invoices?format=csv", s.token());
    String text = new String(csv.bytes(), StandardCharsets.UTF_8);
    assertThat(text).contains("Type").contains("CREDIT_NOTE").contains("-238.00");
  }

  @Test
  void aReviewerCanReclassifyAndTheAmountsFollowTheType() {
    Session s = api.registerOrg("Reclassify Inc");
    UUID id = upload(s, scenario("ACME GmbH", "INV-" + id("n"), "1000.00", "190.00", "1190.00"));
    JsonNode inv = awaitStatus(s, id, "VALIDATED", "NEEDS_REVIEW");
    assertThat(inv.get("documentType").asText()).isEqualTo("INVOICE");

    // "this is actually a credit note": the amounts are stored negative and it still validates
    Map<String, Object> asCredit = new LinkedHashMap<>(correction(inv, Map.of()));
    asCredit.put("documentType", "CREDIT_NOTE");
    JsonNode credit = api.put("/api/v1/invoices/" + id, s.token(), asCredit).json();
    assertThat(credit.get("documentType").asText()).isEqualTo("CREDIT_NOTE");
    assertThat(credit.get("total").decimalValue()).isEqualByComparingTo("-1190.00");
    assertThat(credit.get("status").asText()).isEqualTo("VALIDATED");

    // switching back leaves the negative amounts in place, so validation refuses until a person retypes them
    Map<String, Object> back = new LinkedHashMap<>(correction(credit, Map.of()));
    back.put("documentType", "INVOICE");
    JsonNode asInvoice = api.put("/api/v1/invoices/" + id, s.token(), back).json();
    assertThat(asInvoice.get("status").asText()).isEqualTo("NEEDS_REVIEW");
    assertThat(codes(asInvoice)).contains("NON_POSITIVE_TOTAL");

    assertThat(api.get("/api/v1/invoices/" + id + "/audit", s.token()).json().toString())
        .contains("INVOICE -> CREDIT_NOTE");
  }

  @Test
  void uploadGatekeeping() {
    Session s = api.registerOrg("Gate Inc");

    Resp exe = api.upload(s.token(), "MZ\u0090\u0000 this is not a pdf".getBytes(StandardCharsets.ISO_8859_1), "invoice.pdf");
    assertThat(exe.status()).isEqualTo(415);
    assertThat(exe.json().get("code").asText()).isEqualTo("unsupported_type");

    assertThat(api.upload(s.token(), new byte[0], "empty.pdf").status()).isEqualTo(400);

    byte[] active = "%PDF-1.7\n1 0 obj<</S/JavaScript /JS (app.alert(1))>>endobj\n%%EOF".getBytes(StandardCharsets.ISO_8859_1);
    Resp js = api.upload(s.token(), active, "x.pdf");
    assertThat(js.status()).isEqualTo(422);
    assertThat(js.json().get("code").asText()).isEqualTo("active_content");

    byte[] huge = new byte[16 * 1024 * 1024];
    System.arraycopy("%PDF-1.4".getBytes(StandardCharsets.US_ASCII), 0, huge, 0, 8);
    assertThat(api.upload(s.token(), huge, "huge.pdf").status()).isEqualTo(413);

    assertThat(api.upload(null, TestApi.pdf("none"), "x.pdf").status()).isEqualTo(401);

    // nothing was created by the rejected uploads
    assertThat(api.get("/api/v1/invoices", s.token()).json().get("totalItems").asInt()).isZero();
  }

  @Test
  void listSupportsFiltersSortingAndPaging() {
    Session s = api.registerOrg("List Inc");
    UUID a = upload(s, scenario("Alpha Corp", "A-" + id("n"), "1000.00", "190.00", "1190.00"));
    UUID b = upload(s, scenario("Beta Ltd", "B-" + id("n"), "1000.00", "190.00", "1190.00"));
    UUID c = upload(s, scenario("Gamma AG", "C-" + id("n"), "1000.00", "190.00", "1500.00")); // needs review
    for (UUID x : List.of(a, b, c)) awaitStatus(s, x, "VALIDATED", "NEEDS_REVIEW", "FAILED");

    assertThat(api.get("/api/v1/invoices?status=NEEDS_REVIEW", s.token()).json().get("totalItems").asInt()).isEqualTo(1);
    assertThat(api.get("/api/v1/invoices?status=VALIDATED&status=NEEDS_REVIEW", s.token()).json().get("totalItems").asInt()).isEqualTo(3);
    assertThat(api.get("/api/v1/invoices?vendor=beta", s.token()).json().get("items").get(0).get("vendorName").asText()).isEqualTo("Beta Ltd");
    assertThat(api.get("/api/v1/invoices?q=Gamma", s.token()).json().get("totalItems").asInt()).isEqualTo(1);
    assertThat(api.get("/api/v1/invoices?minTotal=1400", s.token()).json().get("totalItems").asInt()).isEqualTo(1);
    assertThat(api.get("/api/v1/invoices?from=2026-04-01", s.token()).json().get("totalItems").asInt()).isZero();
    // LIKE wildcards typed by the user are literal
    assertThat(api.get("/api/v1/invoices?q=%25", s.token()).json().get("totalItems").asInt()).isZero();

    JsonNode page = api.get("/api/v1/invoices?size=2&page=1&sort=vendorName,asc", s.token()).json();
    assertThat(page.get("totalPages").asInt()).isEqualTo(2);
    assertThat(page.get("items")).hasSize(1);
    assertThat(page.get("items").get(0).get("vendorName").asText()).isEqualTo("Gamma AG");
    // unknown sort properties are ignored rather than reaching the query
    assertThat(api.get("/api/v1/invoices?sort=passwordHash,asc", s.token()).status()).isEqualTo(200);
  }
}
