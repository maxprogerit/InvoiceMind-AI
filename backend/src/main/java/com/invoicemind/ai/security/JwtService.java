package com.invoicemind.ai.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  @Value("${app.jwt.secret}")
  private String secret;

  @Value("${app.jwt.expiration-seconds}")
  private long expirationSeconds;

  public String generateToken(UserDetails userDetails) {
    Instant now = Instant.now();
    return Jwts.builder()
      .subject(userDetails.getUsername())
      .issuedAt(Date.from(now))
      .expiration(Date.from(now.plusSeconds(expirationSeconds)))
      .signWith(signingKey(), SignatureAlgorithm.HS256)
      .compact();
  }

  public String extractUsername(String token) {
    return extractAllClaims(token).getSubject();
  }

  public boolean isTokenValid(String token, UserDetails userDetails) {
    String username = extractUsername(token);
    return username.equals(userDetails.getUsername()) && !extractAllClaims(token).getExpiration().before(new Date());
  }

  private Claims extractAllClaims(String token) {
    return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
  }

  private SecretKey signingKey() {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }
}
