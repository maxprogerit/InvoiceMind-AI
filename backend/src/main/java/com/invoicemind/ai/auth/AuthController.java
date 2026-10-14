package com.invoicemind.ai.auth;

import com.invoicemind.ai.auth.AuthDtos.AuthResponse;
import com.invoicemind.ai.auth.AuthDtos.LoginRequest;
import com.invoicemind.ai.auth.AuthDtos.RegisterRequest;
import com.invoicemind.ai.auth.AuthDtos.UserDto;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.ratelimit.RateLimiter;
import com.invoicemind.ai.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
  static final String REFRESH_COOKIE = "refresh_token";
  private static final String COOKIE_PATH = "/api/v1/auth";

  private final AuthService authService;
  private final RateLimiter rateLimiter;
  private final AppProperties props;

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
    rateLimiter.check("auth", http.getRemoteAddr(), props.rateLimit().authPerMinute());
    return withCookie(authService.register(req), 201);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
    rateLimiter.check("auth", http.getRemoteAddr(), props.rateLimit().authPerMinute());
    rateLimiter.check("login", req.email().trim().toLowerCase(), 10);
    return withCookie(authService.login(req), 200);
  }

  /** Cookie-authenticated, so it additionally requires a header that cross-site forms cannot send. */
  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
      @RequestHeader(name = "X-Requested-With", required = false) String requestedWith,
      HttpServletRequest http) {
    requireCsrfHeader(requestedWith);
    rateLimiter.check("auth", http.getRemoteAddr(), props.rateLimit().authPerMinute());
    return withCookie(authService.refresh(refreshToken), 200);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
      @RequestHeader(name = "X-Requested-With", required = false) String requestedWith) {
    requireCsrfHeader(requestedWith);
    authService.logout(refreshToken);
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
  }

  @GetMapping("/me")
  public UserDto me() {
    return authService.me(CurrentUser.get());
  }

  private ResponseEntity<AuthResponse> withCookie(AuthService.Session session, int status) {
    return ResponseEntity.status(status)
        .header(HttpHeaders.SET_COOKIE,
            cookie(session.refreshToken(), Duration.ofSeconds(props.jwt().refreshTtlSeconds())).toString())
        .body(session.response());
  }

  private ResponseCookie cookie(String value, Duration maxAge) {
    return ResponseCookie.from(REFRESH_COOKIE, value)
        .httpOnly(true)
        .secure(props.cookies().secure())
        .sameSite("Strict")
        .path(COOKIE_PATH)
        .maxAge(maxAge)
        .build();
  }

  private static void requireCsrfHeader(String header) {
    if (header == null || header.isBlank()) {
      throw ApiException.badRequest("missing_header", "X-Requested-With header is required");
    }
  }
}
