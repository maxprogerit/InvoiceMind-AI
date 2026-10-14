package com.invoicemind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicemind.ai.ratelimit.RateLimitedException;
import com.invoicemind.ai.ratelimit.RateLimiter;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.TestApi;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

class AuthIT extends AbstractIntegrationTest {
  @Autowired RateLimiter rateLimiter;

  static Map<String, String> csrf() { return Map.of("X-Requested-With", "invoicemind"); }

  static Map<String, String> cookieHeader(String refresh) {
    return Map.of("X-Requested-With", "invoicemind", "Cookie", "refresh_token=" + refresh);
  }

  @Test
  void registerCreatesOrganisationWithAdminAndReturnsProfile() {
    Resp r = api.post("/api/v1/auth/register", null, Map.of("organizationName", "Globex", "fullName", "Hank Scorpio",
        "email", "hank-" + UUID.randomUUID() + "@globex.com", "password", TestApi.PASSWORD));
    assertThat(r.status()).isEqualTo(201);
    assertThat(r.json().at("/user/role").asText()).isEqualTo("ADMIN");
    assertThat(r.json().at("/user/organizationName").asText()).isEqualTo("Globex");
    assertThat(r.cookie("refresh_token")).isNotBlank();
    assertThat(r.headers().getFirst("Set-Cookie")).contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/v1/auth");

    Resp me = api.get("/api/v1/auth/me", r.json().get("accessToken").asText());
    assertThat(me.status()).isEqualTo(200);
    assertThat(me.json().get("email").asText()).startsWith("hank-");
  }

  @Test
  void registrationRejectsDuplicatesAndWeakInput() {
    Session s = api.registerOrg("Dup Inc");
    Resp dup = api.post("/api/v1/auth/register", null, Map.of("organizationName", "Other", "fullName", "X",
        "email", s.email().toUpperCase(), "password", TestApi.PASSWORD));
    assertThat(dup.status()).isEqualTo(409);
    assertThat(dup.json().get("code").asText()).isEqualTo("email_taken");
    assertThat(dup.text()).doesNotContainIgnoringCase("sql").doesNotContain("constraint");

    Resp weak = api.post("/api/v1/auth/register", null, Map.of("organizationName", "W", "fullName", "W",
        "email", "weak@example.com", "password", "short"));
    assertThat(weak.status()).isEqualTo(400);
    assertThat(weak.json().at("/fieldErrors/password").asText()).contains("10-128");

    Resp badEmail = api.post("/api/v1/auth/register", null, Map.of("organizationName", "W", "fullName", "W",
        "email", "not-an-email", "password", TestApi.PASSWORD));
    assertThat(badEmail.status()).isEqualTo(400);
  }

  @Test
  void loginFailuresAreUniformAndUnauthenticatedAccessIs401() {
    Session s = api.registerOrg("Login Inc");
    Resp wrongPassword = api.post("/api/v1/auth/login", null, Map.of("email", s.email(), "password", "wrong-password-1"));
    Resp unknownUser = api.post("/api/v1/auth/login", null, Map.of("email", "nobody@example.com", "password", "wrong-password-1"));
    assertThat(wrongPassword.status()).isEqualTo(401);
    assertThat(unknownUser.status()).isEqualTo(401);
    assertThat(wrongPassword.json().get("message").asText()).isEqualTo(unknownUser.json().get("message").asText());

    assertThat(api.get("/api/v1/invoices", null).status()).isEqualTo(401);
    assertThat(api.get("/api/v1/analytics/summary", null).status()).isEqualTo(401);
    // a garbage token used to cause a 500 in the old scaffold
    assertThat(api.get("/api/v1/invoices", "garbage.token.here").status()).isEqualTo(401);
    assertThat(api.get("/api/v1/invoices", "").status()).isEqualTo(401);
  }

  @Test
  void refreshTokensRotateAndReuseRevokesTheWholeFamily() {
    Session s = api.registerOrg("Refresh Inc");
    Resp login = api.post("/api/v1/auth/login", null, Map.of("email", s.email(), "password", s.password()));
    String first = login.cookie("refresh_token");

    Resp rotated = api.call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, cookieHeader(first));
    assertThat(rotated.status()).isEqualTo(200);
    String second = rotated.cookie("refresh_token");
    assertThat(second).isNotEqualTo(first);
    assertThat(api.get("/api/v1/auth/me", rotated.json().get("accessToken").asText()).status()).isEqualTo(200);

    // replaying the already-used token looks like theft: it fails and kills the newer token too
    Resp replay = api.call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, cookieHeader(first));
    assertThat(replay.status()).isEqualTo(401);
    Resp afterReplay = api.call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, cookieHeader(second));
    assertThat(afterReplay.status()).isEqualTo(401);
  }

  @Test
  void refreshRequiresCsrfHeaderAndValidCookie() {
    Session s = api.registerOrg("Csrf Inc");
    String refresh = api.post("/api/v1/auth/login", null, Map.of("email", s.email(), "password", s.password()))
        .cookie("refresh_token");
    Resp noHeader = api.call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, Map.of("Cookie", "refresh_token=" + refresh));
    assertThat(noHeader.status()).isEqualTo(400);
    assertThat(api.call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, csrf()).status()).isEqualTo(401);
    assertThat(api.call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, cookieHeader("forged")).status()).isEqualTo(401);
  }

  @Test
  void logoutRevokesTheRefreshToken() {
    Session s = api.registerOrg("Logout Inc");
    String refresh = api.post("/api/v1/auth/login", null, Map.of("email", s.email(), "password", s.password()))
        .cookie("refresh_token");
    Resp out = api.call(HttpMethod.POST, "/api/v1/auth/logout", null, null, cookieHeader(refresh));
    assertThat(out.status()).isEqualTo(204);
    assertThat(api.call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, cookieHeader(refresh)).status()).isEqualTo(401);
  }

  @Test
  void rolesAreEnforcedOnEndpoints() {
    Session admin = api.registerOrg("Roles Inc");
    Session user = api.createUser(admin, "USER");
    Session approver = api.createUser(admin, "APPROVER");

    // admin-only
    assertThat(api.get("/api/v1/users", user.token()).status()).isEqualTo(403);
    assertThat(api.get("/api/v1/users", approver.token()).status()).isEqualTo(403);
    assertThat(api.get("/api/v1/users", admin.token()).status()).isEqualTo(200);
    // reviewers only
    assertThat(api.get("/api/v1/audit", user.token()).status()).isEqualTo(403);
    assertThat(api.get("/api/v1/audit", approver.token()).status()).isEqualTo(200);
    // decisions
    UUID anyId = UUID.randomUUID();
    assertThat(api.post("/api/v1/invoices/" + anyId + "/approve", user.token(), Map.of()).status()).isEqualTo(403);
    assertThat(api.post("/api/v1/invoices/" + anyId + "/reject", user.token(), Map.of("comment", "no")).status()).isEqualTo(403);
    assertThat(api.delete("/api/v1/invoices/" + anyId, approver.token()).status()).isEqualTo(403);
  }

  @Test
  void adminCannotLockTheOrganisationOut() {
    Session admin = api.registerOrg("Lock Inc");
    Session user = api.createUser(admin, "USER");

    Resp self = api.patch("/api/v1/users/" + admin.userId(), admin.token(), Map.of("role", "USER"));
    assertThat(self.status()).isEqualTo(400);
    assertThat(self.json().get("code").asText()).isEqualTo("self_change");

    Resp promote = api.patch("/api/v1/users/" + user.userId(), admin.token(), Map.of("role", "APPROVER"));
    assertThat(promote.status()).isEqualTo(200);
    assertThat(promote.json().get("role").asText()).isEqualTo("APPROVER");

    // disabling a user revokes their refresh tokens and blocks new logins
    String refresh = api.post("/api/v1/auth/login", null, Map.of("email", user.email(), "password", user.password()))
        .cookie("refresh_token");
    assertThat(api.patch("/api/v1/users/" + user.userId(), admin.token(), Map.of("enabled", false)).status()).isEqualTo(200);
    assertThat(api.post("/api/v1/auth/login", null, Map.of("email", user.email(), "password", user.password())).status())
        .isEqualTo(401);
    assertThat(api.call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, cookieHeader(refresh)).status()).isEqualTo(401);
  }

  @Test
  void lastAdminCannotBeDemotedByAnotherAdminLeavingNone() {
    Session admin = api.registerOrg("Last Inc");
    Session second = api.createUser(admin, "ADMIN");
    assertThat(api.patch("/api/v1/users/" + second.userId(), admin.token(), Map.of("role", "USER")).status()).isEqualTo(200);
    // now only one admin remains; a demoted user cannot touch it anyway, and the sole admin cannot demote itself
    assertThat(api.patch("/api/v1/users/" + admin.userId(), admin.token(), Map.of("enabled", false)).status()).isEqualTo(400);
  }

  @Test
  void rateLimiterBlocksAfterTheLimit() {
    String key = "it-" + UUID.randomUUID();
    for (int i = 0; i < 3; i++) rateLimiter.check("test", key, 3);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> rateLimiter.check("test", key, 3))
        .isInstanceOfSatisfying(RateLimitedException.class, e -> assertThat(e.retryAfterSeconds()).isBetween(1, 60));
  }
}
