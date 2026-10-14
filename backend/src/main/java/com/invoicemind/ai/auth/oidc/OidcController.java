package com.invoicemind.ai.auth.oidc;

import com.invoicemind.ai.auth.AuthService;
import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.ratelimit.RateLimiter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Browser-facing single sign-on endpoints (plain redirects, not JSON). A successful callback sets the same refresh
 * cookie as a password login and sends the browser to the application, which then obtains its access token the usual way.
 */
@Slf4j
@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class OidcController {
  static final String FLOW_COOKIE = "oidc_flow";
  private static final String FLOW_PATH = "/api/v1/auth/oidc";
  private static final String REFRESH_COOKIE = "refresh_token";

  public record AuthConfig(@NotNull Sso sso) {}

  public record Sso(@NotNull boolean enabled, String label) {}

  private final OidcService oidc;
  private final OidcProperties props;
  private final AuthService authService;
  private final RateLimiter rateLimiter;
  private final AppProperties app;

  /** Public: tells the login page whether to show the single sign-on button. */
  @GetMapping("/config")
  public AuthConfig config() {
    return new AuthConfig(new Sso(oidc.enabled(), oidc.enabled() ? oidc.buttonLabel() : null));
  }

  @GetMapping("/oidc/login")
  public ResponseEntity<Void> login(@RequestParam(required = false) String next, HttpServletRequest http) {
    rateLimiter.check("auth", http.getRemoteAddr(), app.rateLimit().authPerMinute());
    try {
      OidcService.Flow flow = oidc.begin(redirectUri(http), next);
      return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(flow.url()))
          .header(HttpHeaders.SET_COOKIE, flowCookie(flow.cookieValue(), Duration.ofMinutes(10)).toString())
          .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    } catch (OidcService.SsoException e) {
      return failure(e);
    }
  }

  @GetMapping("/oidc/callback")
  public ResponseEntity<Void> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
                                       @RequestParam(required = false) String error,
                                       @CookieValue(name = FLOW_COOKIE, required = false) String flowCookie,
                                       HttpServletRequest http) {
    rateLimiter.check("auth", http.getRemoteAddr(), app.rateLimit().authPerMinute());
    try {
      if (error != null) throw new OidcService.SsoException("idp_error", "The identity provider reported: " + error);
      OidcService.Completed done = oidc.complete(code, state, flowCookie, redirectUri(http));
      AuthService.Session session = authService.startSessionFor(done.user());
      return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(done.next()))
          .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
          .header(HttpHeaders.SET_COOKIE, flowCookie("", Duration.ZERO).toString())
          .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    } catch (OidcService.SsoException e) {
      return failure(e);
    }
  }

  private ResponseEntity<Void> failure(OidcService.SsoException e) {
    log.info("SSO sign-in refused: {} ({})", e.code(), e.getMessage());
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/login?sso_error=" + e.code()))
        .header(HttpHeaders.SET_COOKIE, flowCookie("", Duration.ZERO).toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
  }

  /** Where the provider sends the browser back to; must match what is registered at the provider. */
  private String redirectUri(HttpServletRequest http) {
    String base = props.publicUrl().isBlank()
        ? ServletUriComponentsBuilder.fromContextPath(http).replacePath(null).build().toUriString()
        : props.publicUrl().replaceAll("/+$", "");
    return base + "/api/v1/auth/oidc/callback";
  }

  /** SameSite=Lax (not Strict): the provider's redirect back to us is a cross-site top-level navigation. */
  private ResponseCookie flowCookie(String value, Duration maxAge) {
    return ResponseCookie.from(FLOW_COOKIE, value).httpOnly(true).secure(app.cookies().secure()).sameSite("Lax")
        .path(FLOW_PATH).maxAge(maxAge).build();
  }

  private ResponseCookie refreshCookie(String token) {
    return ResponseCookie.from(REFRESH_COOKIE, token).httpOnly(true).secure(app.cookies().secure()).sameSite("Strict")
        .path("/api/v1/auth").maxAge(Duration.ofSeconds(app.jwt().refreshTtlSeconds())).build();
  }
}
