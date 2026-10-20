package com.invoicemind.ai.firm;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "firm_members") @IdClass(FirmMemberEntity.Key.class)
public class FirmMemberEntity {
  @Id private UUID firmId;
  @Id private UUID userId;
  @Enumerated(EnumType.STRING)
  private FirmRole firmRole;
  private Instant createdAt = Instant.now();

  public FirmMemberEntity(UUID firmId, UUID userId, FirmRole firmRole) {
    this.firmId = firmId;
    this.userId = userId;
    this.firmRole = firmRole;
  }

  @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
  public static class Key implements Serializable {
    private UUID firmId;
    private UUID userId;
  }
}
