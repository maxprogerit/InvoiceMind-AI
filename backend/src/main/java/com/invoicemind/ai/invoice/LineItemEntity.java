package com.invoicemind.ai.invoice;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "invoice_line_items")
public class LineItemEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id")
  private InvoiceEntity invoice;

  private int position;
  private String description;
  private BigDecimal quantity;
  private BigDecimal unitPrice;
  private BigDecimal total;
}
