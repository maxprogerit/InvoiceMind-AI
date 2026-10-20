package com.invoicemind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.TestApi;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The firm API: clients and their summary, the team and the access matrix, authorisation, and the monthly limit. */
class FirmApiIT extends AbstractIntegrationTest {
  @Autowired JdbcTemplate jdbc;

  private Session firmAdmin() { return api.registerOrg("Firm " + UUID.randomUUID().toString().substring(0, 6)); }

  private UUID createClient(Session admin, String name, String currency) {
    Resp r = api.post("/api/v1/firm/clients", admin.token(), Map.of("name", name, "baseCurrency", currency));
    assertThat(r.status()).as(r.text()).isEqualTo(201);
    return UUID.fromString(r.json().get("id").asText());
  }

  /** The same person with a token for another client of the firm. */
  private Session inClient(Session who, UUID client) {
    Resp r = api.post("/api/v1/auth/switch-organization", who.token(), Map.of("organizationId", client));
    assertThat(r.status()).as(r.text()).isEqualTo(200);
    return new Session(r.json().get("accessToken").asText(), who.userId(), client, who.email(), who.password());
  }

  private static String scenario(String currency, String total, LocalDate date, boolean wrongSum) {
    String id = "fapi-" + UUID.randomUUID().toString().substring(0, 8);
    String sum = wrongSum ? "1.00" : total;
    FakeAiServiceForTests.scenario(id, FakeAiServiceForTests.okOcr(id), FakeAiServiceForTests.okExtraction(
        "ACME GmbH", "A-" + id, date.toString(), date.plusDays(30).toString(), currency, total, "0.00", sum,
        List.of(FakeAiServiceForTests.line("Consulting", "1", total, total)), false, false));
    return id;
  }

  private UUID memberId(Session admin, String email) {
    JsonNode team = api.get("/api/v1/firm/members", admin.token()).json();
    for (JsonNode m : team.get("members")) if (m.get("email").asText().equalsIgnoreCase(email)) return UUID.fromString(m.get("userId").asText());
    throw new AssertionError("no such member " + email);
  }

  @Test
  void aFreshRegistrationIsAFirmOfOneWithItsOwnCompanyAsTheOnlyClient() {
    Session admin = firmAdmin();
    JsonNode info = api.get("/api/v1/firm", admin.token()).json();
    assertThat(info.get("firmRole").asText()).isEqualTo("FIRM_ADMIN");
    assertThat(info.get("clientCount").asInt()).isEqualTo(1);
    assertThat(info.get("plan").asText()).isEqualTo("FREE");
    assertThat(info.path("documentLimit").isMissingNode() || info.path("documentLimit").isNull()).isTrue();   // no limit
    JsonNode clients = api.get("/api/v1/firm/clients", admin.token()).json();
    assertThat(clients).hasSize(1);
    assertThat(clients.get(0).get("id").asText()).isEqualTo(admin.organizationId().toString());
  }

  @Test
  void clientsAreCreatedWithAnIngestTokenAndAccessForTheCreatorAndAreAudited() {
    Session admin = firmAdmin();
    UUID client = createClient(admin, "Bakery Müller", "eur");

    assertThat(jdbc.queryForObject("select length(ingest_token) from organizations where id = ?", Integer.class, client)).isGreaterThan(10);
    assertThat(jdbc.queryForObject("select base_currency from organizations where id = ?", String.class, client)).isEqualTo("EUR");
    assertThat(jdbc.queryForObject("select role from user_org_access where user_id = ? and organization_id = ?", String.class,
        admin.userId(), client)).isEqualTo("ADMIN");
    assertThat(jdbc.queryForObject("select count(*) from audit_logs where organization_id = ? and action = 'CLIENT_CREATED' and user_id = ?",
        Integer.class, client, admin.userId())).isEqualTo(1);

    assertThat(api.post("/api/v1/firm/clients", admin.token(), Map.of("name", "bakery müller", "baseCurrency", "EUR")).status()).isEqualTo(409);
    assertThat(api.post("/api/v1/firm/clients", admin.token(), Map.of("name", "X", "baseCurrency", "ZZZ")).status()).isEqualTo(400);
    assertThat(api.post("/api/v1/firm/clients", admin.token(), Map.of("name", " ", "baseCurrency", "EUR")).status()).isEqualTo(400);
    assertThat(api.get("/api/v1/firm/clients", admin.token()).json()).hasSize(2);
    assertThat(api.get("/api/v1/auth/organizations", admin.token()).json()).hasSize(2);
  }

  @Test
  void theSummaryCountsDocumentsPerClientInOneListAndKeepsClientsApart() {
    Session admin = firmAdmin();
    UUID one = createClient(admin, "Client One", "EUR");
    UUID two = createClient(admin, "Client Two", "EUR");
    Session inOne = inClient(admin, one);
    Session inTwo = inClient(admin, two);
    Session approver = api.createUser(inOne, "APPROVER");
    LocalDate today = LocalDate.now();

    UUID approvedEur = upload(inOne, scenario("EUR", "1190.00", today, false));
    UUID approvedUsd = upload(inOne, scenario("USD", "50.00", today, false));
    UUID waiting = upload(inOne, scenario("EUR", "10.00", today, false));
    UUID flagged = upload(inOne, scenario("EUR", "20.00", today, true));
    UUID failed = upload(inOne, "no-such-scenario");
    for (UUID id : List.of(approvedEur, approvedUsd, waiting)) awaitStatus(inOne, id, "VALIDATED");
    awaitStatus(inOne, flagged, "NEEDS_REVIEW");
    awaitStatus(inOne, failed, "FAILED");
    for (UUID id : List.of(approvedEur, approvedUsd)) {
      assertThat(api.post("/api/v1/invoices/" + id + "/approve", approver.token(), Map.of("comment", "ok")).status()).isEqualTo(200);
    }
    UUID other = upload(inTwo, scenario("EUR", "7.00", today, false));
    awaitStatus(inTwo, other, "VALIDATED");

    JsonNode clients = api.get("/api/v1/firm/clients", admin.token()).json();
    JsonNode c1 = byId(clients, one);
    assertThat(c1.get("documents").asInt()).isEqualTo(5);
    assertThat(c1.get("inReview").asInt()).isEqualTo(1);
    assertThat(c1.get("failed").asInt()).isEqualTo(1);
    assertThat(c1.get("awaitingApproval").asInt()).isEqualTo(1);
    assertThat(c1.get("monthToDateTotal").decimalValue()).isEqualByComparingTo("1190.00");   // the USD invoice is not guessed into EUR ...
    assertThat(c1.get("monthToDateOtherCurrency").asInt()).isEqualTo(1);                     // ... it is counted as "other currency"
    JsonNode c2 = byId(clients, two);
    assertThat(c2.get("documents").asInt()).isEqualTo(1);
    assertThat(c2.get("awaitingApproval").asInt()).isEqualTo(1);
    assertThat(c2.get("monthToDateTotal").decimalValue()).isEqualByComparingTo("0");
    assertThat(byId(clients, admin.organizationId()).get("documents").asInt()).isZero();
  }

  private static JsonNode byId(JsonNode list, UUID id) {
    for (JsonNode n : list) if (n.get("id").asText().equals(id.toString())) return n;
    throw new AssertionError("client " + id + " not in " + list);
  }

  @Test
  void anAccountantSeesOnlyTheirClientsAndCannotAdministerTheFirm() {
    Session admin = firmAdmin();
    UUID one = createClient(admin, "Client One", "EUR");
    UUID two = createClient(admin, "Client Two", "EUR");
    Resp add = api.post("/api/v1/firm/members", admin.token(), Map.of("email", "acc-" + UUID.randomUUID() + "@firm.test", "fullName", "Anna Accountant",
        "password", TestApi.PASSWORD, "firmRole", "ACCOUNTANT", "access", List.of(Map.of("organizationId", one, "role", "APPROVER"))));
    assertThat(add.status()).as(add.text()).isEqualTo(201);
    Session anna = api.login(add.json().get("email").asText(), TestApi.PASSWORD);

    assertThat(anna.organizationId()).isEqualTo(one);                                         // the first client is the home company
    JsonNode visible = api.get("/api/v1/firm/clients", anna.token()).json();
    assertThat(visible).hasSize(1);
    assertThat(visible.get(0).get("id").asText()).isEqualTo(one.toString());
    assertThat(api.post("/api/v1/auth/switch-organization", anna.token(), Map.of("organizationId", two)).status()).isEqualTo(403);

    assertThat(api.post("/api/v1/firm/clients", anna.token(), Map.of("name", "Mine", "baseCurrency", "EUR")).status()).isEqualTo(403);
    assertThat(api.put("/api/v1/firm/clients/" + one, anna.token(), Map.of("name", "Renamed")).status()).isEqualTo(403);
    assertThat(api.get("/api/v1/firm/members", anna.token()).status()).isEqualTo(403);
    assertThat(api.put("/api/v1/firm/members/" + anna.userId() + "/access/" + two, anna.token(), Map.of("role", "ADMIN")).status()).isEqualTo(403);
    assertThat(api.get("/api/v1/firm", anna.token()).json().get("firmRole").asText()).isEqualTo("ACCOUNTANT");
  }

  @Test
  void someoneOutsideAnyFirmGetsNothingFromTheFirmEndpoints() {
    Session admin = firmAdmin();
    Session plainUser = api.createUser(admin, "ADMIN");       // an ADMIN of the company, but not a member of the firm
    for (String path : List.of("/api/v1/firm", "/api/v1/firm/clients", "/api/v1/firm/members")) {
      assertThat(api.get(path, plainUser.token()).status()).as(path).isEqualTo(403);
    }
    assertThat(api.post("/api/v1/firm/clients", plainUser.token(), Map.of("name", "X", "baseCurrency", "EUR")).status()).isEqualTo(403);
    assertThat(api.get("/api/v1/firm/clients", null).status()).isEqualTo(401);
  }

  @Test
  void anotherFirmCannotTouchTheClientsOrAccessOfThisOne() {
    Session firm1 = firmAdmin();
    Session firm2 = firmAdmin();
    UUID client = createClient(firm1, "Client of one", "EUR");
    UUID foreignOnes = firm1.organizationId();

    assertThat(api.put("/api/v1/firm/clients/" + client, firm2.token(), Map.of("name", "Hijacked")).status()).isEqualTo(404);
    assertThat(api.post("/api/v1/firm/clients/" + client + "/deactivate", firm2.token(), null).status()).isEqualTo(404);
    assertThat(api.put("/api/v1/firm/clients/" + UUID.randomUUID(), firm2.token(), Map.of("name", "Nothing")).status()).isEqualTo(404);

    Resp add = api.post("/api/v1/firm/members", firm2.token(), Map.of("email", "x-" + UUID.randomUUID() + "@firm.test", "fullName", "X",
        "password", TestApi.PASSWORD, "firmRole", "ACCOUNTANT", "access", List.of(Map.of("organizationId", foreignOnes, "role", "ADMIN"))));
    assertThat(add.status()).isEqualTo(404);
    assertThat(api.get("/api/v1/firm/clients", firm2.token()).json()).hasSize(1);
    assertThat(api.get("/api/v1/firm/members", firm2.token()).json().get("clients")).hasSize(1);
    assertThat(jdbc.queryForObject("select name from organizations where id = ?", String.class, client)).isEqualTo("Client of one");
  }

  @Test
  void theAccessMatrixCanBeGrantedChangedRevokedAndAMemberRemoved() {
    Session admin = firmAdmin();
    UUID one = createClient(admin, "Client One", "EUR");
    UUID two = createClient(admin, "Client Two", "EUR");
    String email = "bob-" + UUID.randomUUID() + "@firm.test";
    assertThat(api.post("/api/v1/firm/members", admin.token(), Map.of("email", email, "fullName", "Bob", "password", TestApi.PASSWORD,
        "firmRole", "ACCOUNTANT", "access", List.of(Map.of("organizationId", one, "role", "USER")))).status()).isEqualTo(201);
    UUID bob = memberId(admin, email);
    Session bobSession = api.login(email, TestApi.PASSWORD);

    // grant: Bob can now switch to Two, with the role given there
    assertThat(api.put("/api/v1/firm/members/" + bob + "/access/" + two, admin.token(), Map.of("role", "APPROVER")).status()).isEqualTo(200);
    Resp sw = api.post("/api/v1/auth/switch-organization", bobSession.token(), Map.of("organizationId", two));
    assertThat(sw.status()).isEqualTo(200);
    assertThat(sw.json().at("/user/role").asText()).isEqualTo("APPROVER");
    // change
    assertThat(api.put("/api/v1/firm/members/" + bob + "/access/" + two, admin.token(), Map.of("role", "ADMIN")).status()).isEqualTo(200);
    JsonNode matrix = api.get("/api/v1/firm/members", admin.token()).json();
    JsonNode bobRow = null;
    for (JsonNode m : matrix.get("members")) if (m.get("userId").asText().equals(bob.toString())) bobRow = m;
    assertThat(bobRow.get("access")).hasSize(2);
    assertThat(bobRow.get("homeOrganizationId").asText()).isEqualTo(one.toString());
    // revoke: gone, and the home company cannot be revoked
    assertThat(api.delete("/api/v1/firm/members/" + bob + "/access/" + two, admin.token()).status()).isEqualTo(204);
    assertThat(api.post("/api/v1/auth/switch-organization", bobSession.token(), Map.of("organizationId", two)).status()).isEqualTo(403);
    assertThat(api.delete("/api/v1/firm/members/" + bob + "/access/" + two, admin.token()).status()).isEqualTo(404);
    assertThat(api.delete("/api/v1/firm/members/" + bob + "/access/" + one, admin.token()).status()).isEqualTo(409);

    for (String action : List.of("ACCESS_GRANTED", "ACCESS_CHANGED", "ACCESS_REVOKED")) {
      assertThat(jdbc.queryForObject("select count(*) from audit_logs where organization_id = ? and action = ?", Integer.class, two, action))
          .as(action).isEqualTo(1);
    }

    // removing the member disables the account and takes every client away
    assertThat(api.delete("/api/v1/firm/members/" + bob, admin.token()).status()).isEqualTo(204);
    assertThat(api.post("/api/v1/auth/login", null, Map.of("email", email, "password", TestApi.PASSWORD)).status()).isEqualTo(401);
    assertThat(jdbc.queryForObject("select count(*) from user_org_access where user_id = ?", Integer.class, bob)).isZero();
    // the firm keeps its last admin, and nobody removes themselves
    assertThat(api.delete("/api/v1/firm/members/" + admin.userId(), admin.token()).status()).isEqualTo(400);
    // a duplicate e-mail is a conflict, a client listed twice a bad request
    assertThat(api.post("/api/v1/firm/members", admin.token(), Map.of("email", admin.email(), "fullName", "Dup", "password", TestApi.PASSWORD,
        "firmRole", "ACCOUNTANT", "access", List.of(Map.of("organizationId", one, "role", "USER")))).status()).isEqualTo(409);
  }

  @Test
  void clientsCanBeRenamedChangeCurrencyAndBeDeactivatedButNotTheOneYouAreIn() {
    Session admin = firmAdmin();
    UUID one = createClient(admin, "Client One", "EUR");
    createClient(admin, "Client Two", "EUR");

    Resp upd = api.put("/api/v1/firm/clients/" + one, admin.token(), Map.of("name", "Client Uno", "baseCurrency", "usd"));
    assertThat(upd.status()).as(upd.text()).isEqualTo(200);
    assertThat(upd.json().get("name").asText()).isEqualTo("Client Uno");
    assertThat(upd.json().get("baseCurrency").asText()).isEqualTo("USD");
    assertThat(jdbc.queryForObject("select count(*) from audit_logs where organization_id = ? and action in ('CLIENT_UPDATED','BASE_CURRENCY_CHANGED')",
        Integer.class, one)).isEqualTo(2);

    assertThat(api.post("/api/v1/firm/clients/" + admin.organizationId() + "/deactivate", admin.token(), null).status()).isEqualTo(409);
    Resp off = api.post("/api/v1/firm/clients/" + one + "/deactivate", admin.token(), null);
    assertThat(off.status()).as(off.text()).isEqualTo(200);
    assertThat(off.json().get("active").asBoolean()).isFalse();
    assertThat(api.post("/api/v1/auth/switch-organization", admin.token(), Map.of("organizationId", one)).status()).isEqualTo(403);
    assertThat(api.get("/api/v1/firm/clients", admin.token()).json()).hasSize(3);                   // admins still see it, flagged inactive
    assertThat(api.post("/api/v1/firm/clients/" + one + "/activate", admin.token(), null).json().get("active").asBoolean()).isTrue();
    assertThat(api.post("/api/v1/auth/switch-organization", admin.token(), Map.of("organizationId", one)).status()).isEqualTo(200);
  }

  @Test
  void theMonthlyLimitIsSummedOverAllClientsRefusedWithAClearMessageAndAudited() {
    Session admin = firmAdmin();
    UUID one = createClient(admin, "Limit One", "EUR");
    UUID two = createClient(admin, "Limit Two", "EUR");
    Session inOne = inClient(admin, one);
    Session inTwo = inClient(admin, two);
    UUID firm = jdbc.queryForObject("select firm_id from organizations where id = ?", UUID.class, one);
    jdbc.update("update firms set monthly_document_limit = 3 where id = ?", firm);

    LocalDate today = LocalDate.now();
    upload(inOne, scenario("EUR", "1.00", today, false));
    upload(inOne, scenario("EUR", "2.00", today, false));
    upload(inTwo, scenario("EUR", "3.00", today, false));          // 3 of 3, spread over two clients

    for (Session who : List.of(inOne, inTwo)) {
      Resp refused = api.upload(who.token(), TestApi.pdf(scenario("EUR", "4.00", today, false)), "over.pdf");
      assertThat(refused.status()).as(refused.text()).isEqualTo(402);
      assertThat(refused.json().get("code").asText()).isEqualTo("firm_limit_reached");
      assertThat(refused.json().get("message").asText()).contains("3 documents").contains("all client companies");
    }
    assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'FIRM_LIMIT_REACHED' and organization_id in (?, ?)",
        Integer.class, one, two)).isEqualTo(2);                       // recorded although the request itself failed
    assertThat(api.get("/api/v1/firm", admin.token()).json().get("documentsThisMonth").asInt()).isEqualTo(3);
    assertThat(api.get("/api/v1/firm", admin.token()).json().get("documentLimit").asInt()).isEqualTo(3);

    // the limit belongs to the firm: a different firm and a firm without a limit are not affected
    Session other = firmAdmin();
    assertThat(api.upload(other.token(), TestApi.pdf(scenario("EUR", "5.00", today, false)), "free.pdf").status()).isEqualTo(202);
    jdbc.update("update firms set monthly_document_limit = null where id = ?", firm);
    assertThat(api.upload(inOne.token(), TestApi.pdf(scenario("EUR", "6.00", today, false)), "again.pdf").status()).isEqualTo(202);
  }
}
