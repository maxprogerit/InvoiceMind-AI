package com.invoicemind.ai.vendor.memory;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A default learned from a person's correction, for one vendor. Deterministic and inspectable: "when the document of
 * this vendor says {@code whenExtracted} (null: says nothing), use {@code value}". Not model training.
 */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "vendor_memory")
public class VendorMemoryEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private UUID vendorId;
  private UUID organizationId;
  @Enumerated(EnumType.STRING)
  private MemoryField field;
  private String whenExtracted;
  private String value;
  private UUID learnedFromInvoice;
  private UUID learnedBy;
  private Instant learnedAt = Instant.now();
  private int timesApplied;
}
