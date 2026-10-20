package com.invoicemind.ai.firm;

import com.invoicemind.ai.user.Role;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** "This person may work in that organisation, with this role there." */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "user_org_access") @IdClass(UserOrgAccessEntity.Key.class)
public class UserOrgAccessEntity {
  @Id private UUID userId;
  @Id private UUID organizationId;
  @Enumerated(EnumType.STRING)
  private Role role;
  private Instant createdAt = Instant.now();

  public UserOrgAccessEntity(UUID userId, UUID organizationId, Role role) {
    this.userId = userId;
    this.organizationId = organizationId;
    this.role = role;
  }

  @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
  public static class Key implements Serializable {
    private UUID userId;
    private UUID organizationId;
  }
}
