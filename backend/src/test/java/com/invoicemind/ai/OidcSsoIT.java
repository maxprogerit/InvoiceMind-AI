package com.invoicemind.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.auth.AuthService;
import com.invoicemind.ai.auth.oidc.OidcProperties;
import com.invoicemind.ai.auth.oidc.OidcService;
import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.TestApi.Session;
import com.invoicemind.ai.user.OrganizationRepository;
import com.invoicemind.ai.user.UserRepository;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.nav.security.mock.oauth2.MockOAuth2Server;
import no.nav.security.mock.oauth2.OAuth2Config;
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Single sign-on through the full OpenID Connect authorization-code + PKCE flow against a real OIDC server
 * (mock-oauth2-server: discovery, JWKS, signed ID tokens), driven over HTTP like a browser would, redirect by redirect.
 */
class OidcSsoIT extends AbstractIntegrationTest {
  static final String ISSUER_ID = "default";
  static final String CLIENT_ID = "invoicemind-client";
  static final UUID PROVISION_ORG = UUID.fromString("5d7a0000-0000-4000-8000-000000000001");
  static final MockOAuth2Server IDP = new MockOAuth2Server(new OAuth2Config(false)); // false = no interactive login page

  static {
    IDP.start();
  }

  @AfterAll
  static void stopIdp() { IDP.shutdown(); }

  @DynamicPropertySource
  static void oidc(DynamicPropertyRegistry registry) {
    registry.add("app.oidc.enabled", () -> "true");
    registry.add("app.oidc.issuer-uri", () -> IDP.issuerUrl(ISSUER_ID).toString());
    registry.add("app.oidc.client-id", () -> CLIENT_ID);
    registry.add("app.oidc.client-secret", () -> "client-secret");
    registry.add("app.oidc.button-label", () -> "Acme SSO");
    registry.add("app.oidc.auto-provision-organization-id", PROVISION_ORG::toString);
    registry.add("app.oidc.allowed-email-domains", () -> "provision.example.com");
    registry.add("app.oidc.default-role", () -> "APPROVER");
  }

  @Autowired UserRepository users;
  @Autowired OrganizationRepository organizations;
  @Autowired AuthService authService;
  @Autowired AuditService audit;
  @Autowired AppProperties appProps;
  @Autowired OidcService oidc;
  @Autowired OidcProperties oidcProps;
  @Autowired NamedParameterJdbcTemplate jdbc;

  private final HttpClient browser = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
      .followRedirects(HttpClient.Redirect.NEVER).build();

  // --------------------------------------------------------------- the browser
  private record Hop(int status, String location, Map<String, String> setCookies) {}

  private Hop get(String url, Map<String, String> cookies) throws Exception {
    var b = HttpRequest.newBuilder(URI.create(url)).GET();
    if (!cookies.isEmpty()) {
      b.header("Cookie", String.join("; ", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList()));
    }
    HttpResponse<String> r = browser.send(b.build(), HttpResponse.BodyHandlers.ofString());
    Map<String, String> set = new HashMap<>();
    r.headers().allValues("Set-Cookie").forEach(c -> {
      String[] kv = c.split(";", 2)[0].split("=", 2);
      set.put(kv[0], kv.length > 1 ? kv[1] : "");
    });
    return new Hop(r.statusCode(), r.headers().firstValue("Location").orElse(null), set);
  }

  private static Map<String, String> query(String url) {
    Map<String, String> q = new HashMap<>();
    String raw = URI.create(url).getRawQuery();
    if (raw != null) for (String p : raw.split("&")) {
      String[] kv = p.split("=", 2);
      q.put(kv[0], URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8));
    }
    return q;
  }

  /** Steps 1-2 of the browser dance: /oidc/login -> the provider's authorize endpoint -> back with a code. */
  private record Authorized(String code, String state, Map<String, String> cookies, String authorizeUrl) {}

  private Authorized authorize(String next) throws Exception {
    Hop login = get("http://localhost:" + port + "/api/v1/auth/oidc/login" + (next == null ? "" : "?next=" + next), Map.of());
    assertThat(login.status()).isEqualTo(302);
    Map<String, String> cookies = new HashMap<>(login.setCookies());
    assertThat(cookies).containsKey("oidc_flow");
    Hop idp = get(login.location(), Map.of());
    assertThat(idp.status()).as("the provider redirects straight back with a code").isEqualTo(302);
    Map<String, String> back = query(idp.location());
    assertThat(back).containsKeys("code", "state");
    return new Authorized(back.get("code"), back.get("state"), cookies, login.location());
  }

  /** Redeems the code at our callback WITHOUT queueing token claims (for attempts that must fail before any token request). */
  private Hop callback(Authorized a) throws Exception {
    return get("http://localhost:" + port + "/api/v1/auth/oidc/callback?code=" + a.code() + "&state=" + a.state(), a.cookies());
  }

  /** Queues the claims the provider will put in the tokens of the next code redemption, then redeems the code. */
  private Hop redeem(Authorized a, String subject, Map<String, Object> claims) throws Exception {
    IDP.enqueueCallback(new DefaultOAuth2TokenCallback(ISSUER_ID, subject, "JWT", List.of(CLIENT_ID), claims, 3600));
    return callback(a);
  }

  private Hop signIn(String subject, Map<String, Object> claims, String next) throws Exception {
    return redeem(authorize(next), subject, claims);
  }

  private JsonNode sessionFrom(Hop callbackHop) {
    String refresh = callbackHop.setCookies().get("refresh_token");
    assertThat(refresh).isNotBlank();
    var resp = api.call(org.springframework.http.HttpMethod.POST, "/api/v1/auth/refresh", null, null,
        Map.of("Cookie", "refresh_token=" + refresh, "X-Requested-With", "test"));
    assertThat(resp.status()).isEqualTo(200);
    return resp.json();
  }

  private Map<String, Object> claims(String email, boolean verified, String name) {
    Map<String, Object> c = new HashMap<>();
    c.put("email", email);
    c.put("email_verified", verified);
    c.put("name", name);
    return c;
  }

  private String sub() { return "idp-sub-" + UUID.randomUUID(); }

  // -------------------------------------------------------------------- tests
  @Test
  void theLoginPageLearnsWhetherSsoIsAvailable() {
    JsonNode cfg = api.get("/api/v1/auth/config", null).json();
    assertThat(cfg.at("/sso/enabled").asBoolean()).isTrue();
    assertThat(cfg.at("/sso/label").asText()).isEqualTo("Acme SSO");
  }

  @Test
  void anExistingUserSignsInAndIsBoundToTheProvidersSubject() throws Exception {
    Session admin = api.registerOrg("Sso Inc");
    Session member = api.createUser(admin, "APPROVER");
    String subject = sub();

    Authorized auth = authorize("/approvals");
    assertThat(query(auth.authorizeUrl())).containsEntry("code_challenge_method", "S256").containsKey("nonce")
        .containsEntry("response_type", "code").containsEntry("client_id", CLIENT_ID);
    Hop done = redeem(auth, subject, claims(member.email(), true, "Member Name"));

    assertThat(done.status()).isEqualTo(302);
    assertThat(done.location()).isEqualTo("/approvals"); // the requested page, same-site only
    JsonNode session = sessionFrom(done);
    assertThat(session.at("/user/email").asText()).isEqualTo(member.email());
    assertThat(session.at("/user/role").asText()).isEqualTo("APPROVER");
    assertThat(session.at("/user/organizationId").asText()).isEqualTo(admin.organizationId().toString());
    assertThat(users.findById(member.userId()).orElseThrow().getOidcSubject()).isEqualTo(subject);
    assertThat(api.get("/api/v1/audit", admin.token()).json().toString()).contains("SSO_LOGIN").contains("LOGIN_SSO");

    // later logins are recognised by the subject even if the e-mail address at the provider changed
    JsonNode second = sessionFrom(signIn(subject, claims("renamed@elsewhere.example", true, "Member Name"), null));
    assertThat(second.at("/user/id").asText()).isEqualTo(member.userId().toString());
  }

  @Test
  void aStolenOrStaleSignInAttemptIsRefused() throws Exception {
    Session admin = api.registerOrg("Sso Guard Inc");
    Session member = api.createUser(admin, "USER");
    Authorized auth = authorize(null);

    Hop wrongState = get("http://localhost:" + port + "/api/v1/auth/oidc/callback?code=" + auth.code() + "&state=tampered", auth.cookies());
    assertThat(wrongState.location()).isEqualTo("/login?sso_error=state_mismatch");
    assertThat(wrongState.setCookies()).doesNotContainKey("refresh_token");

    // the code of one browser cannot be redeemed by another: it has no flow cookie
    Authorized second = authorize(null);
    Hop noCookie = get("http://localhost:" + port + "/api/v1/auth/oidc/callback?code=" + second.code() + "&state=" + second.state(), Map.of());
    assertThat(noCookie.location()).isEqualTo("/login?sso_error=state_mismatch");

    // a tampered cookie fails its signature
    Map<String, String> forged = new HashMap<>(second.cookies());
    forged.put("oidc_flow", forged.get("oidc_flow").replaceFirst("^.", "A"));
    Hop forgedHop = get("http://localhost:" + port + "/api/v1/auth/oidc/callback?code=" + second.code() + "&state=" + second.state(), forged);
    assertThat(forgedHop.location()).isEqualTo("/login?sso_error=state_mismatch");

    // an authorization code works once
    Authorized once = authorize(null);
    assertThat(redeem(once, sub(), claims(member.email(), true, "Member")).setCookies()).containsKey("refresh_token");
    assertThat(callback(once).location()).startsWith("/login?sso_error="); // replayed code is rejected by the provider
  }

  @Test
  void accountsAreNeverGuessed() throws Exception {
    Session admin = api.registerOrg("Sso Accounts Inc");
    Session member = api.createUser(admin, "USER");
    Session disabled = api.createUser(admin, "USER");
    api.patch("/api/v1/users/" + disabled.userId(), admin.token(), Map.of("enabled", false));

    // an e-mail address the provider has not verified is worthless as proof of identity
    assertThat(signIn(sub(), claims(member.email(), false, "x"), null).location())
        .isEqualTo("/login?sso_error=email_unverified");
    // a disabled account stays disabled
    assertThat(signIn(sub(), claims(disabled.email(), true, "x"), null).location())
        .isEqualTo("/login?sso_error=disabled");
    // no e-mail claim at all
    Map<String, Object> noEmail = new HashMap<>();
    noEmail.put("name", "Nobody");
    assertThat(signIn(sub(), noEmail, null).location()).isEqualTo("/login?sso_error=no_email");

    // an account already bound to one identity is not handed to another identity with the same e-mail address
    String first = sub();
    assertThat(signIn(first, claims(member.email(), true, "x"), null).setCookies()).containsKey("refresh_token");
    assertThat(signIn(sub(), claims(member.email(), true, "x"), null).location())
        .isEqualTo("/login?sso_error=identity_mismatch");
    // ... and an unknown e-mail address at a domain that is not allowed is not created
    assertThat(signIn(sub(), claims("someone@not-allowed.example", true, "x"), null).location())
        .isEqualTo("/login?sso_error=domain_not_allowed");
  }

  @Test
  void withProvisioningAnAllowedDomainGetsAnAccountInTheConfiguredOrganisation() throws Exception {
    jdbc.update("insert into organizations (id, name, base_currency, ingest_token) values (:id, 'Provisioned Org', 'EUR', :token) on conflict do nothing",
        Map.of("id", PROVISION_ORG, "token", "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 20)));
    String email = "new.hire-" + UUID.randomUUID().toString().substring(0, 6) + "@provision.example.com";

    JsonNode session = sessionFrom(signIn(sub(), claims(email, true, "New Hire"), null));

    assertThat(session.at("/user/email").asText()).isEqualTo(email);
    assertThat(session.at("/user/fullName").asText()).isEqualTo("New Hire");
    assertThat(session.at("/user/role").asText()).isEqualTo("APPROVER"); // app.oidc.default-role
    assertThat(session.at("/user/organizationId").asText()).isEqualTo(PROVISION_ORG.toString());
    // there is no password to guess for an account created this way
    assertThat(api.post("/api/v1/auth/login", null, Map.of("email", email, "password", "Passw0rd!long")).status()).isEqualTo(401);
  }

  @Test
  void withoutProvisioningAnUnknownPersonGetsNoAccount() throws Exception {
    OidcProperties strict = new OidcProperties(true, oidcProps.issuerUri(), oidcProps.clientId(), oidcProps.clientSecret(),
        "openid email profile", "SSO", "", true, "", "USER", List.of());
    OidcService noProvision = new OidcService(strict, appProps, users, organizations, authService, audit, mapper,
        org.springframework.web.client.RestClient.builder());
    Authorized auth = authorize(null);
    IDP.enqueueCallback(new DefaultOAuth2TokenCallback(ISSUER_ID, sub(), "JWT", List.of(CLIENT_ID),
        claims("stranger-" + UUID.randomUUID() + "@provision.example.com", true, "S"), 3600));
    String redirect = "http://localhost:" + port + "/api/v1/auth/oidc/callback";
    String cookie = auth.cookies().get("oidc_flow");
    assertThatThrownBy(() -> noProvision.complete(auth.code(), auth.state(), cookie, redirect))
        .isInstanceOf(OidcService.SsoException.class).extracting("code").isEqualTo("no_account");
  }

  @Test
  void theRedirectTargetCanOnlyBeASamePath() throws Exception {
    Session admin = api.registerOrg("Sso Redirect Inc");
    Session member = api.createUser(admin, "USER");
    List<String> locations = new ArrayList<>();
    for (String next : List.of("https://evil.example/phish", "//evil.example", "/ok/path", "javascript:alert(1)")) {
      Hop done = signIn(sub(), claims(member.email(), true, "x"), java.net.URLEncoder.encode(next, StandardCharsets.UTF_8));
      locations.add(done.location());
      // a different identity each time would be refused; reuse the subject bound on the first login
      users.findById(member.userId()).ifPresent(u -> { u.setOidcSubject(null); u.setOidcIssuer(null); users.save(u); });
    }
    assertThat(locations).containsExactly("/invoices", "/invoices", "/ok/path", "/invoices");
  }
}
