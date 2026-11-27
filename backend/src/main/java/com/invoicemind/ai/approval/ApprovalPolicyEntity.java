package com.invoicemind.ai.approval;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Approval rule of one currency in one organisation (see V13). */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "approval_policies") @IdClass(ApprovalPolicyEntity.Key.class)
public class ApprovalPolicyEntity {
  @Id private UUID organizationId;
  @Id private String currency;
  /** Below this absolute total the invoice is approved automatically once it passes every check (null: never). */
  private BigDecimal autoApproveBelow;
  /** Above this absolute total two different approvers are needed (null: never). */
  private BigDecimal twoApproversAbove;
  private UUID updatedBy;
  private Instant updatedAt = Instant.now();

  @Getter @Setter @NoArgsConstructor @EqualsAndHashCode
  public static class Key implements Serializable {
    private UUID organizationId;
    private String currency;
  }
}
