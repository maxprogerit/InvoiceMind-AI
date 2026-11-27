package com.invoicemind.ai;

import static com.invoicemind.ai.support.FakeAiServiceForTests.line;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Approval rules per currency: automatic below X, one approver up to Y, two different approvers above Y. */
class ApprovalRulesIT extends AbstractIntegrationTest {

  record Team(Session admin, Session accountant, Session approver, Session approver2) {}

  private Team team(String name) {
    Session admin = api.registerOrg(name);
    Team t = new Team(admin, api.createUser(admin, "ACCOUNTANT"), api.createUser(admin, "APPROVER"), api.createUser(admin, "APPROVER"));
    Resp rules = api.put("/api/v1/approval-rules", admin.token(), Map.of("rules", List.of(
        Map.of("currency", "EUR", "autoApproveBelow", "100.00", "twoApproversAbove", "5000.00"))));
    assertThat(rules.status()).as(rules.text()).isEqualTo(200);
    return t;
  }

  /** Each invoice from its own vendor, so nothing here is a possible duplicate of anything else. */
  private JsonNode invoice(Session who, String currency, String total, boolean creditNote) {
    String sc = "rule-" + UUID.randomUUID().toString().substring(0, 8);
    String printed = creditNote ? "-" + total : total;
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc), FakeAiServiceForTests.okExtraction(
        creditNote ? "credit_note" : "invoice", "Vendor " + sc, "R-" + sc, "2026-06-01", "2026-07-01", currency, printed, "0.00",
        printed, List.of(line("Work", "1", printed, printed)), false, false));
    UUID id = upload(who, sc);
    awaitStatus(who, id, "VALIDATED", "NEEDS_REVIEW", "FAILED", "APPROVED");
    return api.get("/api/v1/invoices/" + id, who.token()).json();
  }

  private Resp approve(Session who, JsonNode inv) {
    return api.post("/api/v1/invoices/" + inv.get("id").asText() + "/approve", who.token(), Map.of("comment", "ok"));
  }

  @Test
  void smallInvoicesAreApprovedAutomaticallyOnceEveryCheckPasses() {
    Team t = team("Auto Approve Inc");
    JsonNode small = invoice(t.accountant(), "EUR", "49.90", false);
    // the vendor is new, so a person has to confirm it first: until then nothing is approved automatically
    assertThat(small.get("status").asText()).isEqualTo("VALIDATED");
    assertThat(small.at("/approval/required").asInt()).isZero();
    assertThat(small.at("/approval/rule").asText()).contains("below 100.00 EUR");

    api.verifyVendorOf(t.accountant().token(), small.get("id").asText());
    JsonNode after = api.get("/api/v1/invoices/" + small.get("id").asText(), t.accountant().token()).json();
    assertThat(after.get("status").asText()).isEqualTo("APPROVED");
    assertThat(after.at("/approval/autoApproved").asBoolean()).isTrue();
    assertThat(after.hasNonNull("decidedByName")).isFalse(); // the system, not a person
    assertThat(api.get("/api/v1/invoices/" + small.get("id").asText() + "/audit", t.admin().token()).json()
        .findValuesAsText("action")).contains("INVOICE_AUTO_APPROVED");

    // the next small invoice of a confirmed vendor needs nobody at all
    String sc = "rule-auto-" + UUID.randomUUID().toString().substring(0, 6);
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc), FakeAiServiceForTests.okExtraction(
        after.get("vendorName").asText(), "R2-" + sc, "2026-06-20", "2026-07-20", "EUR", "12.00", "0.00", "12.00",
        List.of(line("Work", "1", "12.00", "12.00")), false, false));
    JsonNode next = awaitStatus(t.accountant(), upload(t.accountant(), sc), "APPROVED", "VALIDATED", "NEEDS_REVIEW");
    assertThat(next.get("status").asText()).isEqualTo("APPROVED");
  }

  @Test
  void aMidSizedInvoiceNeedsOneApproverAndOtherCurrenciesFallBackToOne() {
    Team t = team("One Approver Inc");
    JsonNode mid = invoice(t.accountant(), "EUR", "1200.00", false);
    JsonNode usd = invoice(t.accountant(), "USD", "20.00", false); // no USD rule: one approver, never automatic
    for (JsonNode inv : List.of(mid, usd)) {
      api.verifyVendorOf(t.approver().token(), inv.get("id").asText());
      assertThat(api.get("/api/v1/invoices/" + inv.get("id").asText(), t.approver().token()).json().get("status").asText())
          .isEqualTo("VALIDATED");
      assertThat(approve(t.approver(), inv).json().get("status").asText()).isEqualTo("APPROVED");
    }
  }

  @Test
  void aLargeInvoiceNeedsTwoDifferentApproversNeitherOfThemTheUploader() {
    Team t = team("Two Approvers Inc");
    JsonNode big = invoice(t.approver2(), "EUR", "7500.00", false);
    api.verifyVendorOf(t.admin().token(), big.get("id").asText());
    assertThat(big.at("/approval/required").asInt()).isEqualTo(2);

    assertThat(approve(t.approver2(), big).status()).isEqualTo(403); // uploaded it
    Resp first = approve(t.approver(), big);
    assertThat(first.status()).isEqualTo(200);
    assertThat(first.json().get("status").asText()).isEqualTo("VALIDATED");
    assertThat(first.json().at("/approval/given")).hasSize(1);
    assertThat(first.json().at("/permissions/canApprove").asBoolean()).isFalse();
    Resp again = approve(t.approver(), big);
    assertThat(again.status()).isEqualTo(409);
    assertThat(again.json().get("message").asText()).contains("second approver");

    Resp second = approve(t.admin(), big);
    assertThat(second.json().get("status").asText()).isEqualTo("APPROVED");
    assertThat(second.json().at("/approval/given")).hasSize(2);
    List<String> actions = api.get("/api/v1/invoices/" + big.get("id").asText() + "/audit", t.admin().token()).json().findValuesAsText("action");
    assertThat(actions).contains("APPROVAL_RECORDED", "INVOICE_APPROVED");
  }

  @Test
  void correctingAnInvoiceAfterTheFirstApprovalStartsTheApprovalsAgain() {
    Team t = team("Reset Inc");
    JsonNode big = invoice(t.accountant(), "EUR", "9000.00", false);
    api.verifyVendorOf(t.admin().token(), big.get("id").asText());
    approve(t.approver(), big);
    JsonNode now = api.get("/api/v1/invoices/" + big.get("id").asText(), t.accountant().token()).json();
    Resp corrected = api.put("/api/v1/invoices/" + big.get("id").asText(), t.accountant().token(),
        PipelineIT.correction(now, Map.of("invoiceNumber", "R-CHANGED")));
    assertThat(corrected.json().at("/approval/given")).isEmpty();
    assertThat(api.get("/api/v1/invoices/" + big.get("id").asText() + "/audit", t.admin().token()).json()
        .findValuesAsText("action")).contains("APPROVALS_RESET");
  }

  @Test
  void creditNotesAreMeasuredByTheirAmountNotTheirSign() {
    Team t = team("Credit Rules Inc");
    JsonNode credit = invoice(t.accountant(), "EUR", "6000.00", true);
    assertThat(new BigDecimal(credit.get("total").asText())).isEqualByComparingTo("-6000.00");
    assertThat(credit.at("/approval/required").asInt()).isEqualTo(2);
  }

  @Test
  void onlyAdminsChangeTheRulesAndTheRulesMustMakeSense() {
    Team t = team("Rules API Inc");
    assertThat(api.get("/api/v1/approval-rules", t.accountant().token()).json().at("/rules/0/currency").asText()).isEqualTo("EUR");
    assertThat(api.put("/api/v1/approval-rules", t.approver().token(), Map.of("rules", List.of())).status()).isEqualTo(403);
    assertThat(api.put("/api/v1/approval-rules", t.admin().token(), Map.of("rules", List.of(
        Map.of("currency", "EUR", "autoApproveBelow", "900.00", "twoApproversAbove", "100.00")))).status()).isEqualTo(400);
    assertThat(api.put("/api/v1/approval-rules", t.admin().token(), Map.of("rules", List.of(
        Map.of("currency", "EUR"), Map.of("currency", "eur")))).status()).isEqualTo(400);
    assertThat(api.get("/api/v1/audit?action=APPROVAL_RULES_CHANGED", t.admin().token()).json().get("totalItems").asInt()).isEqualTo(1);
  }
}
