package com.invoicemind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Authorisation tests: organisations are isolated, and USERs only see their own uploads. */
class TenancyIT extends AbstractIntegrationTest {

  private String scenario() {
    String id = "tenancy-" + UUID.randomUUID().toString().substring(0, 8);
    FakeAiServiceForTests.scenario(id, FakeAiServiceForTests.okOcr(id), FakeAiServiceForTests.okExtraction(
        "ACME GmbH", "T-" + id, "2026-03-14", "2026-04-13", "EUR", "1000.00", "190.00", "1190.00",
        List.of(FakeAiServiceForTests.line("Consulting", "10", "100.00", "1000.00")), false, false));
    return id;
  }

  @Test
  void anotherOrganisationCannotSeeTouchOrExportAnInvoice() {
    Session orgA = api.registerOrg("Org A");
    Session orgB = api.registerOrg("Org B");
    UUID invoice = upload(orgA, scenario());
    awaitStatus(orgA, invoice, "VALIDATED");
    String path = "/api/v1/invoices/" + invoice;

    // foreign ids are indistinguishable from missing ones: always 404, never 403
    assertThat(api.get(path, orgB.token()).status()).isEqualTo(404);
    assertThat(api.get(path + "/file", orgB.token()).status()).isEqualTo(404);
    assertThat(api.get(path + "/ocr-text", orgB.token()).status()).isEqualTo(404);
    assertThat(api.get(path + "/audit", orgB.token()).status()).isEqualTo(404);
    assertThat(api.post(path + "/approve", orgB.token(), Map.of()).status()).isEqualTo(404);
    assertThat(api.post(path + "/reject", orgB.token(), Map.of("comment", "nope")).status()).isEqualTo(404);
    assertThat(api.post(path + "/retry", orgB.token(), null).status()).isEqualTo(404);
    assertThat(api.delete(path, orgB.token()).status()).isEqualTo(404);
    assertThat(api.put(path, orgB.token(), Map.of("vendorName", "Hacked", "lineItems", List.of())).status()).isEqualTo(404);

    Resp list = api.get("/api/v1/invoices", orgB.token());
    assertThat(list.json().get("totalItems").asInt()).isZero();
    assertThat(api.get("/api/v1/analytics/summary", orgB.token()).json().get("totalInvoices").asInt()).isZero();
    Resp export = api.get("/api/v1/export/invoices?format=json", orgB.token());
    assertThat(export.status()).isEqualTo(200);
    assertThat(export.json()).isEmpty();

    // the owner still sees it untouched
    Resp own = api.get(path, orgA.token());
    assertThat(own.json().get("status").asText()).isEqualTo("VALIDATED");
    assertThat(own.json().get("vendorName").asText()).isEqualTo("ACME GmbH");
  }

  @Test
  void usersOnlySeeTheirOwnUploadsWhileReviewersSeeTheOrganisation() {
    Session admin = api.registerOrg("Scope Inc");
    Session alice = api.createUser(admin, "USER");
    Session bob = api.createUser(admin, "USER");
    Session approver = api.createUser(admin, "APPROVER");

    UUID aliceInvoice = upload(alice, scenario());
    UUID bobInvoice = upload(bob, scenario());
    awaitStatus(alice, aliceInvoice, "VALIDATED");
    awaitStatus(bob, bobInvoice, "VALIDATED");

    assertThat(api.get("/api/v1/invoices/" + bobInvoice, alice.token()).status()).isEqualTo(404);
    assertThat(api.get("/api/v1/invoices/" + bobInvoice + "/file", alice.token()).status()).isEqualTo(404);
    assertThat(api.get("/api/v1/invoices/" + aliceInvoice, alice.token()).status()).isEqualTo(200);

    assertThat(api.get("/api/v1/invoices", alice.token()).json().get("totalItems").asInt()).isEqualTo(1);
    assertThat(api.get("/api/v1/invoices", bob.token()).json().get("totalItems").asInt()).isEqualTo(1);
    assertThat(api.get("/api/v1/invoices", approver.token()).json().get("totalItems").asInt()).isEqualTo(2);
    assertThat(api.get("/api/v1/invoices", admin.token()).json().get("totalItems").asInt()).isEqualTo(2);

    // scoped analytics and export follow the same rule
    assertThat(api.get("/api/v1/analytics/summary", alice.token()).json().get("totalInvoices").asInt()).isEqualTo(1);
    assertThat(api.get("/api/v1/analytics/summary", approver.token()).json().get("totalInvoices").asInt()).isEqualTo(2);
    assertThat(api.get("/api/v1/export/invoices?format=json", alice.token()).json()).hasSize(1);
    assertThat(api.get("/api/v1/export/invoices?format=json", approver.token()).json()).hasSize(2);

    // a USER cannot decide on their own or anyone else's invoice
    assertThat(api.post("/api/v1/invoices/" + aliceInvoice + "/approve", alice.token(), Map.of()).status()).isEqualTo(403);
  }

  @Test
  void organisationIdInRequestsIsIgnored() {
    Session orgA = api.registerOrg("Spoof A");
    Session orgB = api.registerOrg("Spoof B");
    UUID invoice = upload(orgA, scenario());
    awaitStatus(orgA, invoice, "VALIDATED");
    // there is no way to name another org in the API; verify listing with a hostile query param changes nothing
    Resp r = api.get("/api/v1/invoices?organizationId=" + orgA.organizationId(), orgB.token());
    assertThat(r.json().get("totalItems").asInt()).isZero();
  }
}
