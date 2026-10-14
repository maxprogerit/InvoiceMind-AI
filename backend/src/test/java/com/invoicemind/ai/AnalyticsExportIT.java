package com.invoicemind.ai;

import static com.invoicemind.ai.support.FakeAiServiceForTests.line;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class AnalyticsExportIT extends AbstractIntegrationTest {

  /** Registers a scenario with consistent arithmetic: total = net * 1.19. */
  private String scenario(String vendor, String date, String net) {
    String id = "ax-" + UUID.randomUUID().toString().substring(0, 8);
    BigDecimal n = new BigDecimal(net);
    BigDecimal tax = n.multiply(new BigDecimal("0.19")).setScale(2);
    BigDecimal total = n.add(tax);
    FakeAiServiceForTests.scenario(id, FakeAiServiceForTests.okOcr(id), FakeAiServiceForTests.okExtraction(
        vendor, "INV-" + id, date, null, "EUR", net, tax.toPlainString(), total.toPlainString(),
        List.of(line("Services", "1", net, net)), false, false));
    return id;
  }

  private UUID processed(Session who, String vendor, String date, String net) {
    UUID id = upload(who, scenario(vendor, date, net));
    awaitStatus(who, id, "VALIDATED");
    return id;
  }

  @Test
  void analyticsAreComputedFromRealInvoicesAndTrackChanges() {
    Session admin = api.registerOrg("Analytics Inc");
    Session approver = api.createUser(admin, "APPROVER");
    Session user = api.createUser(admin, "USER");

    UUID a1 = processed(user, "Alpha Corp", "2026-01-10", "1000.00");  // total 1190.00
    UUID a2 = processed(user, "Alpha Corp", "2026-02-10", "2000.00");  // total 2380.00
    UUID b1 = processed(user, "Beta Ltd", "2026-02-20", "500.00");     // total  595.00
    UUID mismatch = upload(user, PipelineIT.scenario("Gamma AG", "INV-" + PipelineIT.id("g"), "100.00", "19.00", "999.00"));
    awaitStatus(user, mismatch, "NEEDS_REVIEW");

    // nothing approved yet: no spend, pending amounts exist
    JsonNode before = api.get("/api/v1/analytics/summary", admin.token()).json();
    assertThat(before.get("totalInvoices").asInt()).isEqualTo(4);
    assertThat(before.get("approved").asInt()).isZero();
    assertThat(before.get("awaitingDecision").asInt()).isEqualTo(4);
    assertThat(before.get("approvedSpend")).isEmpty();
    assertThat(before.get("reviewRate").asDouble()).isEqualTo(0.25);        // 1 of 4 needed review
    assertThat(before.get("autoValidatedRate").asDouble()).isEqualTo(0.75);
    assertThat(before.get("failureRate").asDouble()).isZero();

    for (UUID id : List.of(a1, a2, b1)) {
      assertThat(api.post("/api/v1/invoices/" + id + "/approve", approver.token(), Map.of()).status()).isEqualTo(200);
    }

    // the cache must not serve the stale pre-approval numbers
    JsonNode sum = api.get("/api/v1/analytics/summary", admin.token()).json();
    assertThat(sum.get("approved").asInt()).isEqualTo(3);
    assertThat(sum.get("awaitingDecision").asInt()).isEqualTo(1);
    assertThat(sum.get("defaultCurrency").asText()).isEqualTo("EUR");
    assertThat(sum.at("/approvedSpend/0/currency").asText()).isEqualTo("EUR");
    assertThat(sum.at("/approvedSpend/0/amount").decimalValue()).isEqualByComparingTo("4165.00"); // 1190+2380+595
    assertThat(sum.at("/approvedSpend/0/invoices").asInt()).isEqualTo(3);
    assertThat(sum.get("avgProcessingSeconds").asDouble()).isGreaterThanOrEqualTo(0);
    assertThat(sum.get("inputTokens").asLong()).isEqualTo(4 * 1200);
    assertThat(sum.get("outputTokens").asLong()).isEqualTo(4 * 300);
    assertThat(sum.get("costUsd").decimalValue()).isEqualByComparingTo("0.0011"); // 4 * 0.00027, 4 decimals
    assertThat(sum.get("avgOcrConfidence").asDouble()).isEqualTo(0.97);

    JsonNode vendors = api.get("/api/v1/analytics/spend-by-vendor", admin.token()).json();
    assertThat(vendors.get("currency").asText()).isEqualTo("EUR");
    assertThat(vendors.at("/items/0/vendor").asText()).isEqualTo("Alpha Corp");
    assertThat(vendors.at("/items/0/amount").decimalValue()).isEqualByComparingTo("3570.00");
    assertThat(vendors.at("/items/0/invoices").asInt()).isEqualTo(2);
    assertThat(vendors.at("/items/1/vendor").asText()).isEqualTo("Beta Ltd");
    assertThat(vendors.get("items")).hasSize(2); // Gamma is not approved, so it has no spend

    JsonNode months = api.get("/api/v1/analytics/spend-by-month?months=24", admin.token()).json();
    assertThat(months.get("items")).hasSize(24); // gap-free axis
    BigDecimal jan = BigDecimal.ZERO, feb = BigDecimal.ZERO;
    for (JsonNode m : months.get("items")) {
      if (m.get("month").asText().equals("2026-01")) jan = m.get("amount").decimalValue();
      if (m.get("month").asText().equals("2026-02")) feb = m.get("amount").decimalValue();
    }
    assertThat(jan).isEqualByComparingTo("1190.00");
    assertThat(feb).isEqualByComparingTo("2975.00"); // 2380 + 595

    JsonNode byStatus = api.get("/api/v1/analytics/by-status", admin.token()).json();
    Map<String, Integer> counts = new java.util.HashMap<>();
    byStatus.get("items").forEach(i -> counts.put(i.get("status").asText(), i.get("count").asInt()));
    assertThat(counts).containsEntry("APPROVED", 3).containsEntry("NEEDS_REVIEW", 1);

    JsonNode volume = api.get("/api/v1/analytics/volume?days=7", admin.token()).json();
    assertThat(volume.get("items")).hasSize(7);
    JsonNode today = volume.get("items").get(6);
    assertThat(today.get("day").asText()).isEqualTo(LocalDate.now(java.time.ZoneOffset.UTC).toString());
    assertThat(today.get("uploaded").asInt()).isEqualTo(4);
    assertThat(today.get("flagged").asInt()).isEqualTo(1);

    // a USER only sees their own slice of the same organisation (they uploaded everything here) and a
    // fresh USER sees nothing
    Session other = api.createUser(admin, "USER");
    assertThat(api.get("/api/v1/analytics/summary", other.token()).json().get("totalInvoices").asInt()).isZero();
    assertThat(api.get("/api/v1/analytics/spend-by-vendor", other.token()).json().get("items")).isEmpty();
  }

  @Test
  void emptyOrganisationYieldsEmptyButWellFormedAnalytics() {
    Session s = api.registerOrg("Empty Inc");
    JsonNode sum = api.get("/api/v1/analytics/summary", s.token()).json();
    assertThat(sum.get("totalInvoices").asInt()).isZero();
    assertThat(sum.hasNonNull("reviewRate")).isFalse(); // undefined (omitted), not a made-up number
    assertThat(sum.hasNonNull("avgProcessingSeconds")).isFalse();
    assertThat(sum.get("approvedSpend")).isEmpty();
    assertThat(api.get("/api/v1/analytics/spend-by-month", s.token()).json().get("items")).hasSize(12);
    assertThat(api.get("/api/v1/analytics/by-status", s.token()).json().get("items")).isEmpty();
  }

  @Test
  void csvXlsxAndJsonExportsOpenAndMatchTheData() throws Exception {
    Session admin = api.registerOrg("Export Inc");
    Session approver = api.createUser(admin, "APPROVER");
    UUID normal = processed(admin, "Müller & Söhne GmbH", "2026-03-01", "1000.00");
    UUID hostile = processed(admin, "=HYPERLINK(\"http://evil.example\",\"click\")", "2026-03-02", "200.00");
    assertThat(api.post("/api/v1/invoices/" + normal + "/approve", approver.token(), Map.of("comment", "ok")).status()).isEqualTo(200);

    // ---- CSV
    Resp csv = api.get("/api/v1/export/invoices?format=csv", admin.token());
    assertThat(csv.status()).isEqualTo(200);
    assertThat(csv.headers().getContentType().toString()).startsWith("text/csv");
    assertThat(csv.headers().getFirst("Content-Disposition")).contains("attachment").contains(".csv");
    assertThat(csv.headers().getFirst("X-Row-Count")).isEqualTo("2");
    assertThat(csv.bytes()[0]).isEqualTo((byte) 0xEF); // UTF-8 BOM for Excel
    String csvText = new String(csv.bytes(), StandardCharsets.UTF_8).substring(1);
    List<CSVRecord> records = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
        .parse(new StringReader(csvText)).getRecords();
    assertThat(records).hasSize(2);
    assertThat(records.get(0).getParser().getHeaderNames()).contains("Vendor", "Invoice number", "Total", "Status");
    CSVRecord normalRow = records.stream().filter(r -> r.get("ID").equals(normal.toString())).findFirst().orElseThrow();
    assertThat(normalRow.get("Vendor")).isEqualTo("Müller & Söhne GmbH");
    assertThat(normalRow.get("Total")).isEqualTo("1190.00");
    assertThat(normalRow.get("Status")).isEqualTo("APPROVED");
    assertThat(normalRow.get("Decision comment")).isEqualTo("ok");
    // formula injection: the OCR'd vendor name must not start with '=' in a spreadsheet cell
    CSVRecord hostileRow = records.stream().filter(r -> r.get("ID").equals(hostile.toString())).findFirst().orElseThrow();
    assertThat(hostileRow.get("Vendor")).startsWith("'=");

    // ---- XLSX
    Resp xlsx = api.get("/api/v1/export/invoices?format=XLSX&status=APPROVED", admin.token());
    assertThat(xlsx.headers().getContentType().toString()).contains("spreadsheetml");
    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.bytes()))) {
      assertThat(wb.getSheetName(0)).isEqualTo("Invoices");
      assertThat(wb.getSheetName(1)).isEqualTo("Line items");
      Sheet sheet = wb.getSheetAt(0);
      assertThat(sheet.getLastRowNum()).isEqualTo(1); // header + 1 approved row
      assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Vendor");
      assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("Müller & Söhne GmbH");
      assertThat(sheet.getRow(1).getCell(9).getCellType()).isEqualTo(CellType.NUMERIC); // Total is a real number
      assertThat(sheet.getRow(1).getCell(9).getNumericCellValue()).isEqualTo(1190.00);
      assertThat(sheet.getRow(1).getCell(4).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 1));
      assertThat(wb.getSheetAt(1).getLastRowNum()).isEqualTo(1);
      assertThat(wb.getSheetAt(1).getRow(1).getCell(4).getStringCellValue()).isEqualTo("Services");
    }
    // the hostile vendor is excluded by the status filter; unfiltered XLSX keeps it as text, never a formula
    try (XSSFWorkbook all = new XSSFWorkbook(new ByteArrayInputStream(api.get("/api/v1/export/invoices?format=xlsx", admin.token()).bytes()))) {
      for (int r = 1; r <= all.getSheetAt(0).getLastRowNum(); r++) {
        assertThat(all.getSheetAt(0).getRow(r).getCell(2).getCellType()).isEqualTo(CellType.STRING);
      }
    }

    // ---- JSON
    JsonNode json = api.get("/api/v1/export/invoices?format=json&vendor=m%C3%BCller", admin.token()).json();
    assertThat(json).hasSize(1);
    assertThat(json.get(0).get("vendor").asText()).isEqualTo("Müller & Söhne GmbH");
    assertThat(json.get(0).get("total").decimalValue()).isEqualByComparingTo("1190.00");
    assertThat(json.get(0).get("lineItems")).hasSize(1);
    assertThat(json.get(0).has("storageKey")).isFalse(); // internals are not exported

    // ---- filters + validation
    assertThat(api.get("/api/v1/export/invoices?format=json&from=2026-03-02&to=2026-03-02", admin.token()).json()).hasSize(1);
    assertThat(api.get("/api/v1/export/invoices?format=json&minTotal=1000", admin.token()).json()).hasSize(1);
    Resp bad = api.get("/api/v1/export/invoices?format=pdf", admin.token());
    assertThat(bad.status()).isEqualTo(400);
    assertThat(bad.json().get("code").asText()).isEqualTo("invalid_format");

    // exports are audited
    assertThat(api.get("/api/v1/audit?action=EXPORT_CREATED", admin.token()).json().get("totalItems").asInt()).isGreaterThanOrEqualTo(4);
  }
}
