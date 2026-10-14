package com.invoicemind.ai.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "refresh_tokens")
public class RefreshTokenEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  private UUID userId;
  private String tokenHash;
  private Instant expiresAt;
  private Instant revokedAt;
  private Instant createdAt = Instant.now();
}
