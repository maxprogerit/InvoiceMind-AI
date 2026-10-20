package com.invoicemind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.FakeWebhookReceiverForTests;
import com.invoicemind.ai.support.TestApi;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Working in several client companies of one firm: switching, the role taken from the access row, refresh keeping the
 * chosen company, and the isolation between clients and between firms. Setup goes through SQL because the firm API is
 * tested separately (FirmApiIT).
 */
class FirmSwitchIT extends AbstractIntegrationTest {
  @Autowired JdbcTemplate jdbc;

  private UUID firmOf(UUID org) {
    return jdbc.queryForObject("select firm_id from organizations where id = ?", UUID.class, org);
  }

  /** A second client company in the same firm as {@code org}. */
  private UUID addClient(UUID org, String name) {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into organizations (id, name, ingest_token, firm_id) values (?, ?, ?, ?)",
        id, name, id.toString().replace("-", "").substring(0, 24), firmOf(org));
    return id;
  }

  private void grant(UUID user, UUID org, String role) {
    jdbc.update("insert into user_org_access (user_id, organization_id, role) values (?, ?, ?)", user, org, role);
  }

  private void makeAccountant(UUID user, UUID org) {
    jdbc.update("insert into firm_members (firm_id, user_id, firm_role) values (?, ?, 'ACCOUNTANT')", firmOf(org), user);
  }

  private Resp switchTo(Session who, UUID org) {
    return api.post("/api/v1/auth/switch-organization", who.token(), Map.of("organizationId", org));
  }

  private static JsonNode claims(String jwt, com.fasterxml.jackson.databind.ObjectMapper mapper) throws Exception {
    return mapper.readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
  }

  private static String scenario() {
    String id = "firm-" + UUID.randomUUID().toString().substring(0, 8);
    FakeAiServiceForTests.scenario(id, FakeAiServiceForTests.okOcr(id), FakeAiServiceForTests.okExtraction(
        "ACME GmbH", "F-" + id, "2026-03-14", "2026-04-13", "EUR", "1000.00", "190.00", "1190.00",
        List.of(FakeAiServiceForTests.line("Consulting", "10", "100.00", "1000.00")), false, false));
    return id;
  }

  /** Registers a firm (its admin) and an accountant who works in the home client with the given role and in a second client. */
  private record Setup(Session admin, Session accountant, UUID clientA, UUID clientB) {}

  private Setup firmWithAccountant(String accountantRoleInB) {
    Session admin = api.registerOrg("Firm " + UUID.randomUUID().toString().substring(0, 6));
    Session accountant = api.createUser(admin, "APPROVER");
    UUID b = addClient(admin.organizationId(), "Client B");
    makeAccountant(accountant.userId(), admin.organizationId());
    grant(accountant.userId(), b, accountantRoleInB);
    return new Setup(admin, accountant, admin.organizationId(), b);
  }

  @Test
  void switchGivesATokenForTheOtherClientWithTheRoleOfThatAccessRow() throws Exception {
    Setup s = firmWithAccountant("USER");

    JsonNode options = api.get("/api/v1/auth/organizations", s.accountant().token()).json();
    assertThat(options).hasSize(2);
    assertThat(options.findValuesAsText("name")).contains("Client B");

    Resp r = switchTo(s.accountant(), s.clientB());
    assertThat(r.status()).as(r.text()).isEqualTo(200);
    String token = r.json().get("accessToken").asText();
    JsonNode c = claims(token, mapper);
    assertThat(c.get("org").asText()).isEqualTo(s.clientB().toString());
    assertThat(c.get("role").asText()).isEqualTo("USER");               // the role in B, not the APPROVER of the home company
    assertThat(c.get("firm").asText()).isEqualTo(firmOf(s.clientA()).toString());
    assertThat(c.get("firmRole").asText()).isEqualTo("ACCOUNTANT");
    assertThat(r.json().at("/user/organizationName").asText()).isEqualTo("Client B");
    assertThat(r.json().at("/user/role").asText()).isEqualTo("USER");

    JsonNode me = api.get("/api/v1/auth/me", token).json();
    assertThat(me.get("organizationId").asText()).isEqualTo(s.clientB().toString());
    assertThat(me.get("role").asText()).isEqualTo("USER");
    // a USER in B cannot do what an APPROVER/ADMIN could; the privilege of the home company did not travel along
    assertThat(api.get("/api/v1/users", token).status()).isEqualTo(403);
    assertThat(api.get("/api/v1/knowledge/documents", token).status()).isEqualTo(403);

    // ORG_SWITCH is audited in both companies, by the person who switched
    for (UUID org : List.of(s.clientA(), s.clientB())) {
      assertThat(jdbc.queryForObject("select count(*) from audit_logs where organization_id = ? and action = 'ORG_SWITCH' and user_id = ?",
          Integer.class, org, s.accountant().userId())).as("audit in " + org).isEqualTo(1);
    }
  }

  @Test
  void aCompanyThatDoesNotExistAndOneThatIsNotYoursAreAnsweredIdentically() {
    Setup s = firmWithAccountant("USER");
    Session stranger = api.registerOrg("Somebody Else");

    Resp missing = switchTo(s.accountant(), UUID.randomUUID());
    Resp foreign = switchTo(s.accountant(), stranger.organizationId());
    assertThat(missing.status()).isEqualTo(403);
    assertThat(foreign.status()).isEqualTo(403);
    assertThat(foreign.json().get("code").asText()).isEqualTo(missing.json().get("code").asText());
    assertThat(foreign.json().get("message").asText()).isEqualTo(missing.json().get("message").asText());
    assertThat(foreign.text()).doesNotContain(stranger.organizationId().toString());
    assertThat(api.post("/api/v1/auth/switch-organization", null, Map.of("organizationId", s.clientB())).status()).isEqualTo(401);
  }

  @Test
  void accessToOneClientOfTheFirmDoesNotOpenAnother() {
    Setup s = firmWithAccountant("APPROVER");
    UUID clientC = addClient(s.clientA(), "Client C");
    // an invoice of C, uploaded by the firm admin who reaches every client
    String adminTokenInC = switchTo(s.admin(), clientC).json().get("accessToken").asText();
    Session adminInC = new Session(adminTokenInC, s.admin().userId(), clientC, s.admin().email(), s.admin().password());
    UUID invoiceC = upload(adminInC, scenario());
    awaitStatus(adminInC, invoiceC, "VALIDATED");

    assertThat(switchTo(s.accountant(), clientC).status()).isEqualTo(403);
    // the token for B (which the accountant may have) sees nothing of C, and foreign ids are plain 404s
    String tokenB = switchTo(s.accountant(), s.clientB()).json().get("accessToken").asText();
    assertThat(api.get("/api/v1/invoices/" + invoiceC, tokenB).status()).isEqualTo(404);
    assertThat(api.get("/api/v1/invoices", tokenB).json().get("totalItems").asInt()).isZero();
    assertThat(api.get("/api/v1/export/invoices?format=json", tokenB).json()).isEmpty();
    assertThat(api.get("/api/v1/analytics/summary", tokenB).json().get("totalInvoices").asInt()).isZero();
  }

  @Test
  void aFirmAdminReachesEveryClientOfTheFirmAsAdminWithoutAnAccessRow() {
    Session admin = api.registerOrg("Admin Firm " + UUID.randomUUID().toString().substring(0, 6));
    UUID b = addClient(admin.organizationId(), "Client B");
    assertThat(jdbc.queryForObject("select count(*) from user_org_access where organization_id = ?", Integer.class, b)).isZero();

    assertThat(api.get("/api/v1/auth/organizations", admin.token()).json()).hasSize(2);
    Resp r = switchTo(admin, b);
    assertThat(r.status()).isEqualTo(200);
    assertThat(r.json().at("/user/role").asText()).isEqualTo("ADMIN");
    assertThat(api.get("/api/v1/users", r.json().get("accessToken").asText()).status()).isEqualTo(200);
  }

  @Test
  void aSwitchSurvivesARefreshAndFallsBackHomeWhenTheAccessIsWithdrawn() {
    Setup s = firmWithAccountant("USER");
    Resp login = api.post("/api/v1/auth/login", null, Map.of("email", s.accountant().email(), "password", TestApi.PASSWORD));
    String refresh = login.cookie("refresh_token");
    assertThat(login.json().at("/user/organizationId").asText()).isEqualTo(s.clientA().toString());   // login lands in the home company

    Resp sw = api.call(HttpMethod.POST, "/api/v1/auth/switch-organization", login.json().get("accessToken").asText(),
        Map.of("organizationId", s.clientB()), Map.of("Cookie", "refresh_token=" + refresh));
    assertThat(sw.status()).isEqualTo(200);

    Resp refreshed = api.call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, AuthIT.cookieHeader(refresh));
    assertThat(refreshed.status()).isEqualTo(200);
    assertThat(refreshed.json().at("/user/organizationId").asText()).isEqualTo(s.clientB().toString());
    assertThat(refreshed.json().at("/user/role").asText()).isEqualTo("USER");
    String next = refreshed.cookie("refresh_token");

    // the choice is carried over by the rotation, so it survives the next refresh too
    Resp again = api.call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, AuthIT.cookieHeader(next));
    assertThat(again.json().at("/user/organizationId").asText()).isEqualTo(s.clientB().toString());

    // access withdrawn: the old token keeps its 15 minutes, /me notices at once, and a refresh lands back home
    jdbc.update("delete from user_org_access where user_id = ? and organization_id = ?", s.accountant().userId(), s.clientB());
    String staleToken = again.json().get("accessToken").asText();
    assertThat(api.get("/api/v1/auth/me", staleToken).status()).isEqualTo(401);
    Resp home = api.call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, AuthIT.cookieHeader(again.cookie("refresh_token")));
    assertThat(home.status()).isEqualTo(200);
    assertThat(home.json().at("/user/organizationId").asText()).isEqualTo(s.clientA().toString());
    assertThat(home.json().at("/user/role").asText()).isEqualTo("APPROVER");
  }

  @Test
  void aDeactivatedClientCannotBeEnteredAndDoesNotRestoreOnRefresh() {
    Setup s = firmWithAccountant("USER");
    Resp login = api.post("/api/v1/auth/login", null, Map.of("email", s.accountant().email(), "password", TestApi.PASSWORD));
    String refresh = login.cookie("refresh_token");
    api.call(HttpMethod.POST, "/api/v1/auth/switch-organization", login.json().get("accessToken").asText(),
        Map.of("organizationId", s.clientB()), Map.of("Cookie", "refresh_token=" + refresh));

    jdbc.update("update organizations set active = false where id = ?", s.clientB());

    assertThat(switchTo(s.accountant(), s.clientB()).status()).isEqualTo(403);
    assertThat(api.get("/api/v1/auth/organizations", s.accountant().token()).json()).hasSize(1);
    Resp refreshed = api.call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, AuthIT.cookieHeader(refresh));
    assertThat(refreshed.json().at("/user/organizationId").asText()).isEqualTo(s.clientA().toString());
  }

  @Test
  void nobodyInAnotherFirmSeesOrReachesAClientsInvoicesAuditKnowledgeOrWebhooks() {
    Session firm1 = api.registerOrg("Firm One");
    Session firm2 = api.registerOrg("Firm Two");
    UUID firm2Client = addClient(firm2.organizationId(), "Firm Two client");
    Session firm2InClient = new Session(switchTo(firm2, firm2Client).json().get("accessToken").asText(),
        firm2.userId(), firm2Client, firm2.email(), firm2.password());

    UUID invoice = upload(firm1, scenario());
    awaitStatus(firm1, invoice, "VALIDATED");
    api.post("/api/v1/knowledge/documents", firm1.token(), Map.of("title", "Policy", "content", "Invoices above 500 EUR need two approvals."));
    api.post("/api/v1/webhooks", firm1.token(), Map.of("url", FakeWebhookReceiverForTests.url("firm-one"),
        "events", List.of("invoice.approved"), "description", "ERP"));

    // not by switching ...
    assertThat(switchTo(firm2, firm1.organizationId()).status()).isEqualTo(403);
    assertThat(switchTo(firm2InClient, firm1.organizationId()).status()).isEqualTo(403);
    // ... and not with either of firm two's tokens
    for (String token : List.of(firm2.token(), firm2InClient.token())) {
      assertThat(api.get("/api/v1/invoices/" + invoice, token).status()).isEqualTo(404);
      assertThat(api.get("/api/v1/invoices/" + invoice + "/audit", token).status()).isEqualTo(404);
      assertThat(api.get("/api/v1/invoices", token).json().get("totalItems").asInt()).isZero();
      assertThat(api.get("/api/v1/audit?size=50", token).text()).doesNotContain(invoice.toString());
      assertThat(api.get("/api/v1/knowledge/documents", token).json()).isEmpty();
      assertThat(api.get("/api/v1/webhooks", token).json()).isEmpty();
      assertThat(api.get("/api/v1/export/invoices?format=json", token).json()).isEmpty();
    }
  }

  @Test
  void anAccountantsActionsAreNamedInTheClientsAuditTrail() {
    Setup s = firmWithAccountant("ADMIN");
    String tokenB = switchTo(s.accountant(), s.clientB()).json().get("accessToken").asText();
    assertThat(api.post("/api/v1/knowledge/documents", tokenB, Map.of("title", "T", "content", "Some policy text for the client.")).status())
        .isEqualTo(201);

    // the client's admin (the firm admin, reaching B without a row) reads the trail and sees the accountant by name
    String adminTokenB = switchTo(s.admin(), s.clientB()).json().get("accessToken").asText();
    JsonNode trail = api.get("/api/v1/audit?action=KNOWLEDGE_DOCUMENT_ADDED", adminTokenB).json();
    assertThat(trail.get("totalItems").asInt()).isEqualTo(1);
    assertThat(trail.at("/items/0/userName").asText()).isEqualTo("APPROVER user").isNotEqualTo("Unknown");
  }

  @Test
  void notificationsBelongToTheCompanyTheSessionIsWorkingIn() {
    Setup s = firmWithAccountant("USER");
    UUID invoice = upload(s.accountant(), scenario());                    // uploaded in the home company A
    awaitStatus(s.accountant(), invoice, "VALIDATED");
    assertThat(api.get("/api/v1/notifications", s.accountant().token()).json().get("unreadCount").asInt()).isEqualTo(1);

    String tokenB = switchTo(s.accountant(), s.clientB()).json().get("accessToken").asText();
    JsonNode inB = api.get("/api/v1/notifications", tokenB).json();
    assertThat(inB.get("items")).isEmpty();
    assertThat(inB.get("unreadCount").asInt()).isZero();
    assertThat(api.post("/api/v1/notifications/read-all", tokenB, null).status()).isEqualTo(204);
    // reading all in B did not touch A's notification
    assertThat(api.get("/api/v1/notifications", s.accountant().token()).json().get("unreadCount").asInt()).isEqualTo(1);
  }

  @Test
  void rowsFromBeforeFirmsStillWorkWithoutAnyFirmData() {
    Session admin = api.registerOrg("Legacy " + UUID.randomUUID().toString().substring(0, 6));
    Session user = api.createUser(admin, "USER");
    // make the data look like it did before V8 created anything for it
    jdbc.update("delete from firm_members where firm_id = ?", firmOf(admin.organizationId()));
    jdbc.update("delete from user_org_access where organization_id = ?", admin.organizationId());
    jdbc.update("update organizations set firm_id = null where id = ?", admin.organizationId());

    Resp login = api.post("/api/v1/auth/login", null, Map.of("email", user.email(), "password", TestApi.PASSWORD));
    assertThat(login.status()).isEqualTo(200);
    assertThat(login.json().at("/user/role").asText()).isEqualTo("USER");
    assertThat(login.json().at("/user/firmId").isNull() || login.json().at("/user/firmId").isMissingNode()).isTrue();
    String token = login.json().get("accessToken").asText();
    assertThat(api.get("/api/v1/auth/me", token).status()).isEqualTo(200);
    UUID invoice = upload(new Session(token, user.userId(), user.organizationId(), user.email(), user.password()), scenario());
    awaitStatus(user, invoice, "VALIDATED");
    // the admin still administers the team
    assertThat(api.get("/api/v1/users", api.login(admin.email(), TestApi.PASSWORD).token()).status()).isEqualTo(200);
  }
}
