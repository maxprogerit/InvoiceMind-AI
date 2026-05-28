package com.invoicemind.ai.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "receipts")
@Getter
@Setter
public class ReceiptEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private Long organizationId;
  private Long documentId;
  private Long vendorId;
  private String merchantName;
  private java.math.BigDecimal totalAmount;
  private java.math.BigDecimal taxAmount;
  private String expenseCategory;
  private java.math.BigDecimal confidenceScore;
  private java.time.LocalDate receiptDate;
  private String merchantCategory;
  private Long uploadedBy;
  private java.time.Instant createdAt;
  private String status;
}
