package com.invoicemind.ai.approval;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One person's approval of one invoice. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "invoice_approvals")
public class InvoiceApprovalEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private UUID invoiceId;
  private UUID userId;
  private String comment;
  private Instant createdAt = Instant.now();

  public InvoiceApprovalEntity(UUID invoiceId, UUID userId, String comment, Instant at) {
    this.invoiceId = invoiceId;
    this.userId = userId;
    this.comment = comment;
    this.createdAt = at;
  }
}
