package com.invoicemind.ai.invoice;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One field a person changed on an invoice: before, after, who and when. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "invoice_corrections")
public class InvoiceCorrectionEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private UUID invoiceId;
  private UUID organizationId;
  private UUID vendorId;
  private String field;
  private String oldValue;
  private String newValue;
  private UUID userId;
  private Instant createdAt = Instant.now();

  public InvoiceCorrectionEntity(InvoiceEntity inv, String field, String oldValue, String newValue, UUID userId) {
    this.invoiceId = inv.getId();
    this.organizationId = inv.getOrganizationId();
    this.vendorId = inv.getVendorId();
    this.field = field;
    this.oldValue = truncate(oldValue);
    this.newValue = truncate(newValue);
    this.userId = userId;
  }

  private static String truncate(String s) {
    return s == null ? null : s.length() > 1000 ? s.substring(0, 1000) : s;
  }
}
