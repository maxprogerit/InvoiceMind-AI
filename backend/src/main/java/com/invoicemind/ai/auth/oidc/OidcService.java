package com.invoicemind.ai.auth.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.auth.AuthService;
import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.user.OrganizationRepository;
import com.invoicemind.ai.user.Role;
import com.invoicemind.ai.user.UserEntity;
import com.invoicemind.ai.user.UserRepository;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The relying-party side of OpenID Connect (authorization code + PKCE). The flow state (state, nonce, PKCE verifier)
 * lives in a short-lived signed cookie, so no server-side session is needed. The ID token is verified against the
 * provider's JWKS (signature, issuer, expiry, audience, nonce) before any account is touched.
 */
@Slf4j
@Service
public class OidcService {
  /** Why a sign-in was refused; the code travels to the login page as {@code ?sso_error=}. */
  public static final class SsoException extends RuntimeException {
    private final String code;

    public SsoException(String code, String message) {
      super(message);
      this.code = code;
    }

    public String code() { return code; }
  }

  public record Flow(String url, String cookieValue) {}

  public record Completed(UserEntity user, String next) {}

  private record Endpoints(String authorization, String token, String jwks) {}

  private static final Duration FLOW_TTL = Duration.ofMinutes(10);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final OidcProperties props;
  private final UserRepository users;
  private final OrganizationRepository organizations;
  private final AuthService authService;
  private final AuditService audit;
  private final ObjectMapper mapper;
  private final RestClient http;
  private final byte[] cookieKey;

  private volatile Endpoints endpoints;
  private volatile Instant endpointsFetchedAt = Instant.EPOCH;
  private volatile JwtDecoder decoder;

  public OidcService(OidcProperties props, AppProperties app, UserRepository users, OrganizationRepository organizations,
                     AuthService authService, AuditService audit, ObjectMapper mapper, RestClient.Builder restClientBuilder) {
    this.props = props;
    this.users = users;
    this.organizations = organizations;
    this.authService = authService;
    this.audit = audit;
    this.mapper = mapper;
    var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    factory.setReadTimeout(Duration.ofSeconds(10));
    this.http = restClientBuilder.requestFactory(factory).build();
    this.cookieKey = ("oidc-flow|" + app.jwt().secret()).getBytes(StandardCharsets.UTF_8);
  }

  public boolean enabled() { return props.configured(); }

  public String buttonLabel() { return props.buttonLabel(); }

  // ------------------------------------------------------------------ step 1
  /** Builds the redirect to the provider and the cookie that remembers state, nonce and PKCE verifier. */
  public Flow begin(String redirectUri, String next) {
    requireConfigured();
    Endpoints e = endpoints();
    String state = random(), nonce = random(), verifier = random() + random();
    String challenge = b64(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
    String url = e.authorization() + (e.authorization().contains("?") ? "&" : "?")
        + "response_type=code"
        + "&client_id=" + enc(props.clientId())
        + "&redirect_uri=" + enc(redirectUri)
        + "&scope=" + enc(props.scopes())
        + "&state=" + enc(state)
        + "&nonce=" + enc(nonce)
        + "&code_challenge=" + enc(challenge)
        + "&code_challenge_method=S256";
    long expires = Instant.now().plus(FLOW_TTL).getEpochSecond();
    String payload = String.join("|", state, nonce, verifier, Long.toString(expires), b64(safeNext(next).getBytes(StandardCharsets.UTF_8)));
    return new Flow(url, b64(payload.getBytes(StandardCharsets.UTF_8)) + "." + sign(payload));
  }

  // ------------------------------------------------------------------ step 2
  /** Completes the login: verifies state, exchanges the code, validates the ID token, links or provisions the user. */
  @Transactional
  public Completed complete(String code, String state, String cookieValue, String redirectUri) {
    requireConfigured();
    String[] flow = readFlow(cookieValue);
    if (code == null || code.isBlank() || state == null || !constantTimeEquals(flow[0], state)) {
      throw new SsoException("state_mismatch", "The sign-in attempt could not be verified; please start again");
    }
    Endpoints e = endpoints();
    JsonNode token = exchange(e, code, flow[2], redirectUri);
    String idToken = token.path("id_token").asText("");
    if (idToken.isBlank()) throw new SsoException("invalid_token", "The identity provider returned no ID token");

    Jwt jwt;
    try {
      jwt = decoder(e).decode(idToken);
    } catch (JwtException ex) {
      log.warn("ID token rejected: {}", ex.getMessage());
      throw new SsoException("invalid_token", "The ID token could not be verified");
    }
    if (!constantTimeEquals(flow[1], jwt.getClaimAsString("nonce") == null ? "" : jwt.getClaimAsString("nonce"))) {
      throw new SsoException("invalid_token", "The ID token does not belong to this sign-in attempt");
    }
    UserEntity user = resolveUser(jwt);
    audit.log(user.getOrganizationId(), user.getId(), "SSO_LOGIN", AuditService.USER, user.getId(),
        Map.of("issuer", props.issuerUri()));
    return new Completed(user, new String(Base64.getUrlDecoder().decode(flow[4]), StandardCharsets.UTF_8));
  }

  // --------------------------------------------------------------- accounts
  private UserEntity resolveUser(Jwt jwt) {
    String issuer = jwt.getIssuer() == null ? props.issuerUri() : jwt.getIssuer().toString();
    String subject = jwt.getSubject();
    if (subject == null || subject.isBlank()) throw new SsoException("invalid_token", "The ID token has no subject");

    // 1. a person who has signed in before is recognised by the provider's stable subject
    Optional<UserEntity> linked = users.findByOidcIssuerAndOidcSubject(issuer, subject);
    if (linked.isPresent()) return enabled(linked.get());

    // 2. first sign-in: link by verified e-mail
    String email = jwt.getClaimAsString("email");
    if (email == null || email.isBlank()) throw new SsoException("no_email", "The identity provider did not share an e-mail address");
    email = email.trim().toLowerCase(Locale.ROOT);
    if (props.requireEmailVerified() && !Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
      throw new SsoException("email_unverified", "The e-mail address is not verified at the identity provider");
    }
    Optional<UserEntity> byEmail = users.findByEmailIgnoreCase(email);
    if (byEmail.isPresent()) {
      UserEntity user = enabled(byEmail.get());
      if (user.getOidcSubject() != null) {
        // this account is already bound to a different identity: the e-mail was reassigned at the provider
        throw new SsoException("identity_mismatch", "This account is linked to a different identity at the provider");
      }
      user.setOidcIssuer(issuer);
      user.setOidcSubject(subject);
      return users.save(user);
    }
    return provision(issuer, subject, email, jwt);
  }

  private UserEntity provision(String issuer, String subject, String email, Jwt jwt) {
    if (props.autoProvisionOrganizationId().isBlank()) {
      throw new SsoException("no_account", "There is no account for this e-mail address; ask an administrator to add you");
    }
    String domain = email.substring(email.indexOf('@') + 1);
    boolean allowed = props.allowedEmailDomains().stream().anyMatch(d -> d.trim().equalsIgnoreCase(domain));
    if (!allowed) throw new SsoException("domain_not_allowed", "Accounts for this e-mail domain are not created automatically");
    UUID orgId;
    try {
      orgId = UUID.fromString(props.autoProvisionOrganizationId().trim());
    } catch (IllegalArgumentException ex) {
      throw new SsoException("not_configured", "Single sign-on is misconfigured");
    }
    if (!organizations.existsById(orgId)) throw new SsoException("not_configured", "Single sign-on is misconfigured");
    Role role = Role.valueOf(props.defaultRole().trim().toUpperCase(Locale.ROOT));
    String name = jwt.getClaimAsString("name");
    UserEntity user = authService.newUser(orgId, email, name == null || name.isBlank() ? email.substring(0, email.indexOf('@')) : name,
        random() + random() + "aA1", role); // a random password nobody knows: this account signs in through the provider
    user.setOidcIssuer(issuer);
    user.setOidcSubject(subject);
    audit.log(orgId, user.getId(), "USER_PROVISIONED_SSO", AuditService.USER, user.getId(), Map.of("email", email, "role", role.name()));
    return users.save(user);
  }

  private static UserEntity enabled(UserEntity user) {
    if (!user.isEnabled()) throw new SsoException("disabled", "This account is disabled");
    return user;
  }

  /** An ID token issued for another application must never log anybody in here. */
  static OAuth2TokenValidator<Jwt> audienceValidator(String clientId) {
    return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "The ID token is not meant for this application", null));
  }

  // ------------------------------------------------------- provider access
  private JsonNode exchange(Endpoints e, String code, String verifier, String redirectUri) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "authorization_code");
    form.add("code", code);
    form.add("redirect_uri", redirectUri);
    form.add("code_verifier", verifier);
    var request = http.post().uri(URI.create(e.token())).contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .accept(MediaType.APPLICATION_JSON);
    if (props.clientSecret().isBlank()) {
      form.add("client_id", props.clientId()); // public client: PKCE is the proof
    } else {
      request = request.headers(h -> h.setBasicAuth(URLEncoder.encode(props.clientId(), StandardCharsets.UTF_8),
          URLEncoder.encode(props.clientSecret(), StandardCharsets.UTF_8)));
    }
    try {
      byte[] body = request.body(form).retrieve().body(byte[].class);
      return mapper.readTree(body);
    } catch (RestClientException | IOException ex) {
      log.warn("Token exchange failed: {}", ex.getMessage());
      throw new SsoException("token_exchange_failed", "The identity provider rejected the sign-in");
    }
  }

  private JwtDecoder decoder(Endpoints e) {
    JwtDecoder d = decoder;
    if (d == null) {
      NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(e.jwks())
          .jwsAlgorithms(set -> { set.add(SignatureAlgorithm.RS256); set.add(SignatureAlgorithm.ES256); set.add(SignatureAlgorithm.PS256); })
          .build();
      nimbus.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
          JwtValidators.createDefaultWithIssuer(props.issuerUri().replaceAll("/+$", "")), audienceValidator(props.clientId())));
      decoder = d = nimbus;
    }
    return d;
  }

  /** OpenID discovery, cached for an hour; read lazily so the application starts even if the provider is down. */
  private Endpoints endpoints() {
    Endpoints cached = endpoints;
    if (cached != null && Instant.now().isBefore(endpointsFetchedAt.plus(Duration.ofHours(1)))) return cached;
    String issuer = props.issuerUri().replaceAll("/+$", "");
    try {
      JsonNode doc = mapper.readTree(http.get().uri(URI.create(issuer + "/.well-known/openid-configuration"))
          .accept(MediaType.APPLICATION_JSON).retrieve().body(byte[].class));
      if (!issuer.equals(doc.path("issuer").asText("").replaceAll("/+$", ""))) {
        throw new SsoException("not_configured", "The provider's issuer does not match the configured one");
      }
      Endpoints fresh = new Endpoints(doc.path("authorization_endpoint").asText(), doc.path("token_endpoint").asText(),
          doc.path("jwks_uri").asText());
      if (fresh.authorization().isBlank() || fresh.token().isBlank() || fresh.jwks().isBlank()) {
        throw new SsoException("not_configured", "The provider's discovery document is incomplete");
      }
      if (!fresh.equals(cached)) decoder = null; // keys may have moved
      endpoints = fresh;
      endpointsFetchedAt = Instant.now();
      return fresh;
    } catch (RestClientException | IOException ex) {
      log.warn("OpenID discovery failed: {}", ex.getMessage());
      if (cached != null) return cached; // keep working on the last known endpoints
      throw new SsoException("idp_unreachable", "The identity provider could not be reached");
    }
  }

  // ---------------------------------------------------------------- cookie
  /** Returns {state, nonce, verifier, expiry, next} after checking signature and age. */
  private String[] readFlow(String cookieValue) {
    try {
      if (cookieValue == null) throw new IllegalArgumentException("missing");
      int dot = cookieValue.lastIndexOf('.');
      String payload = new String(Base64.getUrlDecoder().decode(cookieValue.substring(0, dot)), StandardCharsets.UTF_8);
      if (!constantTimeEquals(sign(payload), cookieValue.substring(dot + 1))) throw new IllegalArgumentException("signature");
      String[] parts = payload.split("\\|", -1);
      if (parts.length != 5 || Instant.now().getEpochSecond() > Long.parseLong(parts[3])) throw new IllegalArgumentException("expired");
      return parts;
    } catch (RuntimeException e) {
      throw new SsoException("state_mismatch", "The sign-in attempt expired; please start again");
    }
  }

  private String sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(cookieKey, "HmacSHA256"));
      return b64(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  // ---------------------------------------------------------------- helpers
  /** Only same-site paths: an attacker-supplied {@code next} must never become an open redirect. */
  static String safeNext(String next) {
    if (next == null || !next.startsWith("/") || next.startsWith("//") || next.contains("\\") || next.contains("\n")
        || next.contains("\r") || next.length() > 300) return "/invoices";
    return next;
  }

  private void requireConfigured() {
    if (!props.configured()) throw new SsoException("not_configured", "Single sign-on is not enabled");
  }

  private static String random() {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return b64(bytes);
  }

  private static byte[] sha256(byte[] data) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(data);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String b64(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }

  private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

  private static boolean constantTimeEquals(String a, String b) {
    return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
