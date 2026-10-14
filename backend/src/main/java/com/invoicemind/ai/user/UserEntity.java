package com.invoicemind.ai.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "users")
public class UserEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  private UUID organizationId;
  private String email;
  private String passwordHash;
  private String fullName;
  @Enumerated(EnumType.STRING)
  private Role role;
  private boolean enabled = true;
  /** Set on the first SSO login: (issuer, subject) identifies the person at the identity provider. */
  private String oidcIssuer;
  private String oidcSubject;
  private Instant createdAt = Instant.now();
}
