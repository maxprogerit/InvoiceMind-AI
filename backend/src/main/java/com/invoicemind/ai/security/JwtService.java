package com.invoicemind.ai.security;

import com.invoicemind.ai.config.AppProperties;
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
    return Jwts.builder()
        .subject(p.userId().toString())
        .claim("org", p.organizationId().toString())
        .claim("role", p.role().name())
        .claim("email", p.email())
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
          Role.valueOf(c.get("role", String.class))));
    } catch (JwtException | IllegalArgumentException | NullPointerException e) {
      return Optional.empty();
    }
  }
}
