package com.invoicemind.ai.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "invoices")
@Getter
@Setter
public class InvoiceEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private Long organizationId;
  private Long documentId;
  private Long vendorId;
  private String invoiceNumber;
  private BigDecimal totalAmount;
  private String currency;
  private LocalDate invoiceDate;
  private LocalDate dueDate;
  private String status;
  private BigDecimal confidenceScore;
  private Boolean duplicateFlag;
  private String smartCategory;
  private java.math.BigDecimal taxAmount;
  private String paymentStatus;
}
