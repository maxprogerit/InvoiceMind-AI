package com.invoicemind.ai;

import static com.invoicemind.ai.support.FakeAiServiceForTests.line;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

/** Approved invoices out as Xero / QuickBooks Online bill imports, each invoice exactly once. */
class AccountingExportIT extends AbstractIntegrationTest {

  record Team(Session admin, Session accountant, Session approver) {}

  private Team team(String name) {
    Session admin = api.registerOrg(name);
    return new Team(admin, api.createUser(admin, "ACCOUNTANT"), api.createUser(admin, "APPROVER"));
  }

  /** Uploaded by the accountant, vendor confirmed, approved by the approver. */
  private UUID approved(Team t, String type, String vendor, String number, String subtotal, String tax, String total,
                        List<Map<String, Object>> lines) {
    String sc = "acct-" + UUID.randomUUID().toString().substring(0, 8);
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc), FakeAiServiceForTests.okExtraction(type, vendor, number,
        "2026-05-04", "2026-06-03", "EUR", subtotal, tax, total, lines, false, false));
    UUID id = upload(t.accountant(), sc);
    awaitStatus(t.accountant(), id, "VALIDATED");
    api.verifyVendorOf(t.accountant().token(), id);
    Resp r = api.post("/api/v1/invoices/" + id + "/approve", t.approver().token(), Map.of());
    assertThat(r.json().get("status").asText()).as(r.text()).isEqualTo("APPROVED");
    return id;
  }

  private Resp export(Session who, String target) {
    return api.post("/api/v1/export/accounting", who.token(),
        Map.of("target", target, "account", "6000", "taxCode", "Tax on Purchases", "dateStyle", "DMY"));
  }

  private List<CSVRecord> file(Session who, String batchId) throws Exception {
    String csv = api.get("/api/v1/export/accounting/batches/" + batchId + "/file", who.token()).text();
    return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(new StringReader(csv)).getRecords();
  }

  @Test
  void xeroGetsOneRowPerLineWithItsTemplateHeadersAndNothingIsExportedTwice() throws Exception {
    Team t = team("Xero Export Inc");
    approved(t, "invoice", "=HYPERLINK(\"http://evil\")", "X-1", "300.00", "57.00", "357.00",
        List.of(line("Design", "2", "100.00", "200.00"), line("Hosting", "1", "100.00", "100.00")));
    approved(t, "credit_note", "Credit Vendor Ltd", "CN-1", "50.00", "0.00", "50.00", List.of(line("Refund", "1", "50.00", "50.00")));

    Resp first = export(t.accountant(), "XERO");
    assertThat(first.status()).as(first.text()).isEqualTo(200);
    assertThat(first.json().get("invoices").asInt()).isEqualTo(2);
    String batch = first.json().get("batchId").asText();

    String csv = api.get("/api/v1/export/accounting/batches/" + batch + "/file", t.accountant().token()).text();
    assertThat(csv.lines().findFirst().orElseThrow()).startsWith("*ContactName,EmailAddress,").endsWith("TrackingOption2,Currency");
    List<CSVRecord> rows = file(t.accountant(), batch);
    assertThat(rows).hasSize(3); // two lines + one line
    CSVRecord design = rows.get(0);
    assertThat(design.get("*ContactName")).isEqualTo("'=HYPERLINK(\"http://evil\")"); // formula neutralised
    assertThat(design.get("*InvoiceNumber")).isEqualTo("X-1");
    assertThat(design.get("*InvoiceDate")).isEqualTo("04/05/2026");
    assertThat(design.get("*DueDate")).isEqualTo("03/06/2026");
    assertThat(design.get("*Quantity")).isEqualTo("2.00");
    assertThat(design.get("*UnitAmount")).isEqualTo("100.00");
    assertThat(design.get("*AccountCode")).isEqualTo("6000");
    assertThat(design.get("*TaxType")).isEqualTo("Tax on Purchases");
    assertThat(design.get("TaxAmount")).isEqualTo("57.00");
    assertThat(rows.get(1).get("TaxAmount")).isEmpty(); // the tax once per bill
    assertThat(design.get("Currency")).isEqualTo("EUR");
    CSVRecord credit = rows.get(2);
    assertThat(credit.get("*UnitAmount")).isEqualTo("-50.00"); // credit notes import as credit notes in Xero

    // already exported: not offered again; the file of the batch stays downloadable
    assertThat(export(t.accountant(), "XERO").status()).isEqualTo(409);
    assertThat(file(t.accountant(), batch)).hasSize(3);
    UUID later = approved(t, "invoice", "Later Vendor", "L-1", "10.00", "0.00", "10.00", List.of(line("x", "1", "10.00", "10.00")));
    Resp second = export(t.accountant(), "XERO");
    assertThat(second.json().get("invoices").asInt()).isEqualTo(1);
    assertThat(file(t.accountant(), second.json().get("batchId").asText()).get(0).get("*InvoiceNumber")).isEqualTo("L-1");
    assertThat(api.get("/api/v1/export/accounting/batches", t.admin().token()).json()).hasSize(2);
    assertThat(api.get("/api/v1/audit?action=INVOICES_EXPORTED", t.admin().token()).json().get("totalItems").asInt()).isEqualTo(2);
    assertThat(later).isNotNull();
  }

  @Test
  void quickbooksGetsItsColumnsTheCategoryAsAccountAndCreditNotesAreLeftOut() throws Exception {
    Team t = team("QBO Export Inc");
    approved(t, "invoice", "Pine Supplies LLC", "P-77", "80.00", "0.00", "80.00", List.of(line("Paper", "4", "20.00", "80.00")));
    // an invoice with an expense category: the category is its account in QuickBooks
    String sc = "acct-cat-" + UUID.randomUUID().toString().substring(0, 6);
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc), FakeAiServiceForTests.okExtraction("invoice", "Oak Rentals LLC",
        "R-9", "2026-05-05", "2026-06-04", "EUR", "40.00", "0.00", "40.00", List.of(line("Desk", "1", "40.00", "40.00")), false, false));
    UUID rental = upload(t.accountant(), sc);
    JsonNode inv = awaitStatus(t.accountant(), rental, "VALIDATED");
    api.put("/api/v1/invoices/" + rental, t.accountant().token(), PipelineIT.correction(inv, Map.of("expenseCategory", "Rent or Lease")));
    api.verifyVendorOf(t.accountant().token(), rental);
    assertThat(api.post("/api/v1/invoices/" + rental + "/approve", t.approver().token(), Map.of()).status()).isEqualTo(200);
    approved(t, "credit_note", "Pine Supplies LLC", "PC-1", "5.00", "0.00", "5.00", List.of(line("Return", "1", "5.00", "5.00")));

    Resp r = export(t.accountant(), "QBO");
    assertThat(r.json().get("invoices").asInt()).isEqualTo(2);
    assertThat(r.json().get("skipped").get(0).get("reason").asText()).contains("does not import credit notes");
    List<CSVRecord> rows = file(t.accountant(), r.json().get("batchId").asText());
    assertThat(rows).hasSize(2);
    assertThat(rows.get(1).get("Account")).isEqualTo("Rent or Lease");
    CSVRecord row = rows.get(0);
    assertThat(row.get("Bill No")).isEqualTo("P-77");
    assertThat(row.get("Supplier")).isEqualTo("Pine Supplies LLC");
    assertThat(row.get("Bill Date")).isEqualTo("04/05/2026");
    assertThat(row.get("Account")).isEqualTo("6000");
    assertThat(row.get("Line Amount")).isEqualTo("80.00");
    assertThat(row.get("Line Description")).isEqualTo("Paper");
  }

  @Test
  void approversDoNotExportAndExportedInvoicesAreNoLongerOverdue() {
    Team t = team("Export Rights Inc");
    UUID id = approved(t, "invoice", "Old Vendor", "O-1", "10.00", "0.00", "10.00", List.of(line("x", "1", "10.00", "10.00")));
    // due 2026-06-03, so overdue today until it is handed to accounting
    assertThat(api.get("/api/v1/invoices?queue=OVERDUE", t.admin().token()).json().path("items").findValuesAsText("id")).contains(id.toString());
    assertThat(export(t.approver(), "XERO").status()).isEqualTo(403);
    assertThat(api.get("/api/v1/export/accounting/pending", t.accountant().token()).json().get("approvedNotExported").asInt()).isEqualTo(1);
    assertThat(export(t.admin(), "XERO").status()).isEqualTo(200);
    assertThat(api.get("/api/v1/invoices?queue=OVERDUE", t.admin().token()).json().path("items").findValuesAsText("id")).doesNotContain(id.toString());
  }
}
