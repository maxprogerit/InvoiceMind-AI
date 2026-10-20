package com.invoicemind.ai.security;

import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.firm.FirmRole;
import com.invoicemind.ai.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey key;
  private final long accessTtlSeconds;

  public JwtService(AppProperties props) {
    this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
    this.accessTtlSeconds = props.jwt().accessTtlSeconds();
  }

  public long accessTtlSeconds() { return accessTtlSeconds; }

  public String generateAccessToken(AuthPrincipal p) {
    Instant now = Instant.now();
    var builder = Jwts.builder()
        .subject(p.userId().toString())
        .claim("org", p.organizationId().toString())
        .claim("role", p.role().name())
        .claim("email", p.email());
    // only for people who belong to a firm, so everybody else's tokens keep exactly their old shape
    if (p.firmId() != null) builder.claim("firm", p.firmId().toString());
    if (p.firmRole() != null) builder.claim("firmRole", p.firmRole().name());
    return builder
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  /** Returns empty for anything that is not a valid, unexpired token signed by us. Never throws. */
  public Optional<AuthPrincipal> parse(String token) {
    try {
      Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      return Optional.of(new AuthPrincipal(
          UUID.fromString(c.getSubject()),
          UUID.fromString(c.get("org", String.class)),
          c.get("email", String.class),
          Role.valueOf(c.get("role", String.class)),
          c.get("firm", String.class) == null ? null : UUID.fromString(c.get("firm", String.class)),
          c.get("firmRole", String.class) == null ? null : FirmRole.valueOf(c.get("firmRole", String.class))));
    } catch (JwtException | IllegalArgumentException | NullPointerException e) {
      return Optional.empty();
    }
  }
}
