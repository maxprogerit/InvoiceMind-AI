package com.invoicemind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkflowIT extends AbstractIntegrationTest {

  private static String ok() {
    return PipelineIT.scenario("ACME GmbH", "INV-" + PipelineIT.id("w"), "1000.00", "190.00", "1190.00");
  }

  @Test
  void approverApprovesWithCommentAndEverythingIsAudited() {
    Session admin = api.registerOrg("Approve Inc");
    Session approver = api.createUser(admin, "APPROVER");
    Session user = api.createUser(admin, "USER");
    UUID invoice = upload(user, ok());
    awaitStatus(user, invoice, "VALIDATED");

    Resp r = api.post("/api/v1/invoices/" + invoice + "/approve", approver.token(), Map.of("comment", "Matches PO 4711"));
    assertThat(r.status()).isEqualTo(200);
    assertThat(r.json().get("status").asText()).isEqualTo("APPROVED");
    assertThat(r.json().get("decisionComment").asText()).isEqualTo("Matches PO 4711");
    assertThat(r.json().get("decidedByName").asText()).isEqualTo("APPROVER user");
    assertThat(r.json().get("decidedAt").isNull()).isFalse();

    JsonNode trail = api.get("/api/v1/invoices/" + invoice + "/audit", user.token()).json();
    JsonNode last = trail.get(trail.size() - 1);
    assertThat(last.get("action").asText()).isEqualTo("INVOICE_APPROVED");
    assertThat(last.get("userName").asText()).isEqualTo("APPROVER user");
    assertThat(last.at("/details/comment").asText()).isEqualTo("Matches PO 4711");
    assertThat(last.at("/details/from").asText()).isEqualTo("VALIDATED");

    assertThat(api.get("/api/v1/notifications", user.token()).json().get("items").toString()).contains("APPROVED");

    // the decision is final
    assertThat(api.post("/api/v1/invoices/" + invoice + "/approve", approver.token(), Map.of()).status()).isEqualTo(409);
    assertThat(api.post("/api/v1/invoices/" + invoice + "/reject", approver.token(), Map.of("comment", "too late")).status()).isEqualTo(409);
    assertThat(api.put("/api/v1/invoices/" + invoice, user.token(), Map.of("vendorName", "X", "lineItems", List.of())).status()).isEqualTo(409);
    assertThat(api.delete("/api/v1/invoices/" + invoice, admin.token()).status()).isEqualTo(409);
  }

  @Test
  void rejectionNeedsACommentAndNotifiesTheUploader() {
    Session admin = api.registerOrg("Reject Inc");
    Session approver = api.createUser(admin, "APPROVER");
    Session user = api.createUser(admin, "USER");
    UUID invoice = upload(user, ok());
    awaitStatus(user, invoice, "VALIDATED");

    assertThat(api.post("/api/v1/invoices/" + invoice + "/reject", approver.token(), Map.of()).status()).isEqualTo(400);
    assertThat(api.post("/api/v1/invoices/" + invoice + "/reject", approver.token(), Map.of("comment", "ab")).status()).isEqualTo(400);

    Resp r = api.post("/api/v1/invoices/" + invoice + "/reject", approver.token(), Map.of("comment", "Wrong cost centre"));
    assertThat(r.status()).isEqualTo(200);
    assertThat(r.json().get("status").asText()).isEqualTo("REJECTED");
    String notes = api.get("/api/v1/notifications", user.token()).json().get("items").toString();
    assertThat(notes).contains("REJECTED").contains("Wrong cost centre");
  }

  @Test
  void fourEyesApproversCannotApproveTheirOwnUploads() {
    Session admin = api.registerOrg("FourEyes Inc");
    Session approver = api.createUser(admin, "APPROVER");

    UUID own = upload(approver, ok());
    JsonNode inv = awaitStatus(approver, own, "VALIDATED");
    assertThat(inv.at("/permissions/canApprove").asBoolean()).isFalse();
    Resp r = api.post("/api/v1/invoices/" + own + "/approve", approver.token(), Map.of());
    assertThat(r.status()).isEqualTo(403);
    assertThat(api.get("/api/v1/invoices/" + own, approver.token()).json().get("status").asText()).isEqualTo("VALIDATED");

    // someone else can; so can an admin on their own upload (documented break-glass)
    assertThat(api.post("/api/v1/invoices/" + own + "/approve", admin.token(), Map.of()).status()).isEqualTo(200);
    UUID adminOwn = upload(admin, ok());
    awaitStatus(admin, adminOwn, "VALIDATED");
    assertThat(api.post("/api/v1/invoices/" + adminOwn + "/approve", admin.token(), Map.of()).status()).isEqualTo(200);
  }

  @Test
  void invoicesNeedingReviewCannotBeApprovedButCanBeRejected() {
    Session admin = api.registerOrg("Review Inc");
    Session approver = api.createUser(admin, "APPROVER");
    Session user = api.createUser(admin, "USER");
    String sc = PipelineIT.scenario("ACME GmbH", "INV-" + PipelineIT.id("w"), "1000.00", "190.00", "9999.00");
    UUID invoice = upload(user, sc);
    awaitStatus(user, invoice, "NEEDS_REVIEW");

    assertThat(api.post("/api/v1/invoices/" + invoice + "/approve", approver.token(), Map.of()).status()).isEqualTo(409);
    JsonNode detail = api.get("/api/v1/invoices/" + invoice, approver.token()).json();
    assertThat(detail.at("/permissions/canApprove").asBoolean()).isFalse();
    assertThat(detail.at("/permissions/canReject").asBoolean()).isTrue();
    assertThat(api.post("/api/v1/invoices/" + invoice + "/reject", approver.token(), Map.of("comment", "totals are wrong"))
        .status()).isEqualTo(200);
  }

  @Test
  void adminCanDeleteUnapprovedInvoicesAndTheFileGoesToo() {
    Session admin = api.registerOrg("Delete Inc");
    Session user = api.createUser(admin, "USER");
    UUID invoice = upload(user, ok());
    awaitStatus(user, invoice, "VALIDATED");

    assertThat(api.delete("/api/v1/invoices/" + invoice, user.token()).status()).isEqualTo(403);
    assertThat(api.delete("/api/v1/invoices/" + invoice, admin.token()).status()).isEqualTo(204);
    assertThat(api.get("/api/v1/invoices/" + invoice, admin.token()).status()).isEqualTo(404);
    String audit = api.get("/api/v1/audit?action=INVOICE_DELETED", admin.token()).json().get("items").toString();
    assertThat(audit).contains(invoice.toString());
  }

  @Test
  void organisationWideAuditLogIsFilterableAndPaged() {
    Session admin = api.registerOrg("Audit Inc");
    Session user = api.createUser(admin, "USER");
    UUID invoice = upload(user, ok());
    awaitStatus(user, invoice, "VALIDATED");

    JsonNode all = api.get("/api/v1/audit?size=100", admin.token()).json();
    assertThat(all.get("totalItems").asInt()).isGreaterThanOrEqualTo(5);
    JsonNode filtered = api.get("/api/v1/audit?action=INVOICE_UPLOADED&entityType=INVOICE", admin.token()).json();
    assertThat(filtered.get("totalItems").asInt()).isEqualTo(1);
    assertThat(filtered.get("items").get(0).get("userName").asText()).isEqualTo("USER user");
    JsonNode bySystem = api.get("/api/v1/audit?action=VALIDATION_COMPLETED", admin.token()).json();
    assertThat(bySystem.get("items").get(0).get("userName").asText()).isEqualTo("System");
    assertThat(api.get("/api/v1/audit?userId=" + user.userId(), admin.token()).json().get("totalItems").asInt()).isGreaterThanOrEqualTo(1);
    assertThat(api.get("/api/v1/audit?from=2999-01-01", admin.token()).json().get("totalItems").asInt()).isZero();
  }

  @Test
  void notificationsCanBeMarkedRead() {
    Session admin = api.registerOrg("Notify Inc");
    Session user = api.createUser(admin, "USER");
    UUID invoice = upload(user, ok());
    awaitStatus(user, invoice, "VALIDATED");

    JsonNode before = api.get("/api/v1/notifications", user.token()).json();
    assertThat(before.get("unreadCount").asInt()).isGreaterThanOrEqualTo(1);
    String id = before.get("items").get(0).get("id").asText();
    assertThat(api.post("/api/v1/notifications/" + id + "/read", user.token(), null).status()).isEqualTo(204);
    assertThat(api.get("/api/v1/notifications", user.token()).json().get("unreadCount").asInt())
        .isEqualTo(before.get("unreadCount").asInt() - 1);
    assertThat(api.post("/api/v1/notifications/read-all", user.token(), null).status()).isEqualTo(204);
    assertThat(api.get("/api/v1/notifications", user.token()).json().get("unreadCount").asInt()).isZero();
    // someone else's notification id is a 404
    Session other = api.createUser(admin, "USER");
    assertThat(api.post("/api/v1/notifications/" + id + "/read", other.token(), null).status()).isEqualTo(404);
  }
}
