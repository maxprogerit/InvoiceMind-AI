package com.invoicemind.ai.invoice;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "invoices")
public class InvoiceEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  private UUID organizationId;
  private UUID uploadedBy;
  private UUID vendorId;

  @Enumerated(EnumType.STRING)
  private InvoiceStatus status = InvoiceStatus.UPLOADED;

  // original file
  private String originalFilename;
  private String storageKey;
  private String contentType;
  private long fileSize;
  private String fileSha256;

  // extracted / corrected fields
  @Enumerated(EnumType.STRING)
  private DocumentType documentType = DocumentType.INVOICE;
  private String vendorName;
  /** The seller's tax / VAT id as extracted (normalised); used to match the vendor. */
  private String vendorTaxId;
  private String invoiceNumber;
  private LocalDate invoiceDate;
  private LocalDate dueDate;
  private String currency;
  private BigDecimal subtotal;
  private BigDecimal tax;
  private BigDecimal total;

  // AI metadata
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Double> fieldConfidence;
  private boolean modelNeedsReview;
  @JdbcTypeCode(SqlTypes.JSON)
  private List<String> modelReviewReasons;
  private boolean injectionSuspected;
  private boolean wasFlagged;
  private boolean humanReviewed;

  @Column(columnDefinition = "text")
  private String ocrText;
  private String ocrEngine;
  private BigDecimal ocrConfidence;
  private Integer ocrPageCount;
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> extractionJson;
  // duplicate detection (see DuplicateStatus)
  @Enumerated(EnumType.STRING)
  private DuplicateStatus duplicateStatus = DuplicateStatus.NONE;
  private UUID duplicateOf;
  private UUID duplicateClearedBy;
  private Instant duplicateClearedAt;
  /** Extraction copied from this earlier invoice with the identical file: the model was not called again. */
  private UUID reusedFrom;

  /** Approved by the system under the approval rules (small amount, every check passed). */
  private boolean autoApproved;
  /** Expense category for the accounting export; may be pre-filled from vendor memory. */
  private String expenseCategory;
  /** What vendor memory filled in: [{field, value, extracted, memoryId}]. */
  @JdbcTypeCode(SqlTypes.JSON)
  private List<Map<String, Object>> memoryApplied;

  /** How the text was obtained: text_layer, ocr or mixed (per page). */
  private String textSource;
  /** The extract-service's own first-pass validation ({status, issues}); the Java validator decides the status. */
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> extractValidation;
  private String llmModel;
  private Integer inputTokens;
  private Integer outputTokens;
  private BigDecimal costUsd;

  private String failureReason;
  private Instant processingStartedAt;
  private Instant processingFinishedAt;
  private Long processingMs;

  private UUID decidedBy;
  private Instant decidedAt;
  private String decisionComment;

  private Instant createdAt = Instant.now();
  private Instant updatedAt = Instant.now();
  @Version
  private long version;

  @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position ASC")
  @BatchSize(size = 50)
  private List<LineItemEntity> lineItems = new ArrayList<>();

  @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
  @BatchSize(size = 50)
  private List<ValidationIssueEntity> issues = new ArrayList<>();

  @PreUpdate
  void touch() { this.updatedAt = Instant.now(); }

  /** "Invoice INV-1" / "Credit note CN-7", or the file name while nothing has been extracted. */
  public String displayLabel() {
    return invoiceNumber != null ? documentType.label() + " " + invoiceNumber : originalFilename;
  }

  public void replaceLineItems(List<LineItemEntity> items) {
    lineItems.clear();
    int pos = 0;
    for (LineItemEntity item : items) {
      item.setInvoice(this);
      item.setPosition(pos++);
      lineItems.add(item);
    }
  }

  public void replaceIssues(List<ValidationIssueEntity> newIssues) {
    issues.clear();
    for (ValidationIssueEntity issue : newIssues) {
      issue.setInvoice(this);
      issues.add(issue);
    }
  }
}
