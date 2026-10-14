package com.invoicemind.ai.invoice;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "validation_issues")
public class ValidationIssueEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id")
  private InvoiceEntity invoice;

  private String code;
  private String severity;
  private String field;
  private String message;
  private Instant createdAt = Instant.now();
}
