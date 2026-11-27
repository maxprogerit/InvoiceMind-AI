package com.invoicemind.ai.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code @NotNull} on response fields marks them as always present in the OpenAPI contract. */
public final class InvoiceDtos {
  private InvoiceDtos() {}

  public record LineItemDto(
      UUID id,
      @NotBlank @Size(max = 1000) String description,
      @Digits(integer = 10, fraction = 4) BigDecimal quantity,
      @Digits(integer = 10, fraction = 4) BigDecimal unitPrice,
      @Digits(integer = 12, fraction = 2) @DecimalMin("-999999999999.99") @DecimalMax("999999999999.99") BigDecimal total) {}

  /** {@code relatedInvoiceId}: the earlier invoice a duplicate finding points to. */
  public record IssueDto(@NotNull String code, @NotNull String severity, String field, @NotNull String message,
                         UUID relatedInvoiceId) {}

  /** The vendor the invoice was matched to; UNVERIFIED vendors must be confirmed before approval. */
  public record VendorRef(@NotNull UUID id, @NotNull String name, String taxId, @NotNull String status) {}

  /**
   * Result of the duplicate rules. {@code status}: NONE, DUPLICATE (approval blocked) or POSSIBLE_DUPLICATE (a person
   * decides); {@code of*}: the earlier invoice; {@code clearedBy*}: who decided a possible duplicate is a separate bill;
   * {@code reusedFrom}: the identical file whose extraction was reused (the model was not called).
   */
  public record DuplicateInfo(@NotNull DuplicateStatus status, UUID ofId, String ofLabel, String ofStatus,
                              String clearedByName, Instant clearedAt, UUID reusedFrom) {}

  public enum DuplicateDecision { NOT_DUPLICATE, DUPLICATE }

  public record DuplicateDecisionRequest(@NotNull DuplicateDecision decision, @Size(max = 1000) String comment) {}

  /** What the current user may do with this invoice, computed server-side so the UI never guesses. */
  public record Permissions(@NotNull boolean canEdit, @NotNull boolean canApprove, @NotNull boolean canReject,
                            @NotNull boolean canRetry, @NotNull boolean canDelete, @NotNull boolean canDecideDuplicate,
                            /** Why approval is not possible right now, for the UI (null when it is). */
                            String approvalBlockedReason) {}

  public record InvoiceSummary(
      @NotNull UUID id, @NotNull InvoiceStatus status, @NotNull DocumentType documentType,
      @NotNull String originalFilename, String vendorName,
      String invoiceNumber, LocalDate invoiceDate, LocalDate dueDate, String currency, BigDecimal total,
      @NotNull UUID uploadedBy, @NotNull String uploadedByName, @NotNull int errorCount, @NotNull int warningCount,
      @NotNull Instant createdAt, @NotNull DuplicateStatus duplicateStatus, @NotNull boolean vendorVerified) {}

  /** {@code source}: text_layer (PDF text read directly), ocr (Tesseract) or mixed (both, per page). */
  public record OcrInfo(String engine, BigDecimal confidence, Integer pageCount, String source) {}

  /** The extract-service's own first pass (valid / needs_review / failed); informational, Java decides the status. */
  public record ExtractCheck(@NotNull String status, @NotNull int errors, @NotNull int warnings) {}

  public record UsageInfo(String model, Integer inputTokens, Integer outputTokens, BigDecimal costUsd) {}

  public record InvoiceDetail(
      @NotNull UUID id, @NotNull InvoiceStatus status, @NotNull DocumentType documentType,
      @NotNull String originalFilename, @NotNull String contentType, @NotNull long fileSize,
      String vendorName, String invoiceNumber, LocalDate invoiceDate, LocalDate dueDate, String currency,
      BigDecimal subtotal, BigDecimal tax, BigDecimal total,
      String vendorTaxId, VendorRef vendor, @NotNull DuplicateInfo duplicate,
      Map<String, Double> fieldConfidence, List<String> modelReviewReasons, @NotNull boolean injectionSuspected,
      @NotNull boolean humanReviewed, @NotNull OcrInfo ocr, ExtractCheck extractCheck, @NotNull UsageInfo usage, Long processingMs,
      String failureReason,
      @NotNull UUID uploadedBy, @NotNull String uploadedByName, String decidedByName, Instant decidedAt,
      String decisionComment,
      @NotNull List<LineItemDto> lineItems, @NotNull List<IssueDto> issues, @NotNull Permissions permissions,
      @NotNull Instant createdAt, @NotNull Instant updatedAt, @NotNull long version) {}

  /** Full-replacement correction of the extracted fields. {@code version} guards against concurrent edits. */
  public record UpdateInvoiceRequest(
      /** Omitted keeps the current type. Switching to CREDIT_NOTE stores every amount as a negative value. */
      DocumentType documentType,
      @Size(max = 300) String vendorName,
      /** Omitted (null) keeps the current tax id; an empty string clears it. */
      @Size(max = 60) String vendorTaxId,
      @Size(max = 100) String invoiceNumber,
      LocalDate invoiceDate,
      LocalDate dueDate,
      @Size(max = 3) String currency,
      @Digits(integer = 12, fraction = 2) @DecimalMin("-999999999999.99") @DecimalMax("999999999999.99") BigDecimal subtotal,
      @Digits(integer = 12, fraction = 2) @DecimalMin("-999999999999.99") @DecimalMax("999999999999.99") BigDecimal tax,
      @Digits(integer = 12, fraction = 2) @DecimalMin("-999999999999.99") @DecimalMax("999999999999.99") BigDecimal total,
      @Valid @Size(max = 500) List<LineItemDto> lineItems,
      Long version) {}

  public record DecisionRequest(@Size(max = 2000) String comment) {}

  public record OcrTextResponse(String text, String engine, BigDecimal confidence, Integer pageCount) {}
}
