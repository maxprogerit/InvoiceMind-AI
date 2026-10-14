package com.invoicemind.ai.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.user.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
  static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

  static JwtService service(String secret, long ttl) {
    return new JwtService(new AppProperties(new AppProperties.Jwt(secret, ttl, 100), null, null, null, null, null, null, null));
  }

  final AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a@b.io", Role.APPROVER);

  @Test
  void roundTripKeepsIdentityOrgAndRole() {
    JwtService jwt = service(SECRET, 60);
    assertThat(jwt.parse(jwt.generateAccessToken(principal))).contains(principal);
  }

  @Test
  void rejectsGarbageTamperedExpiredAndForeignTokens() {
    JwtService jwt = service(SECRET, 60);
    String token = jwt.generateAccessToken(principal);

    assertThat(jwt.parse("garbage.token.here")).isEmpty();
    assertThat(jwt.parse("")).isEmpty();
    assertThat(jwt.parse(token.substring(0, token.length() - 3) + "abc")).isEmpty(); // broken signature
    assertThat(service("another-secret-another-secret-0123456789", 60).parse(token)).isEmpty();
    assertThat(service(SECRET, -10).parse(service(SECRET, -10).generateAccessToken(principal))).isEmpty(); // expired
  }

  @Test
  void rejectsUnsignedTokens() {
    String unsigned = Jwts.builder().subject(principal.userId().toString())
        .claim("org", principal.organizationId().toString()).claim("role", "ADMIN").claim("email", "x@y.z")
        .expiration(new Date(System.currentTimeMillis() + 60_000)).compact();
    assertThat(service(SECRET, 60).parse(unsigned)).isEmpty();
  }

  @Test
  void rejectsTokenWithUnknownRoleClaim() {
    String forged = Jwts.builder().subject(principal.userId().toString())
        .claim("org", principal.organizationId().toString()).claim("role", "SUPERUSER").claim("email", "x@y.z")
        .expiration(new Date(System.currentTimeMillis() + 60_000))
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    assertThat(service(SECRET, 60).parse(forged)).isEmpty();
  }
}
