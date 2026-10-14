package com.invoicemind.ai.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InvoiceDtos {
  private InvoiceDtos() {}

  public record LineItemDto(
      UUID id,
      @NotBlank @Size(max = 1000) String description,
      @Digits(integer = 10, fraction = 4) BigDecimal quantity,
      @Digits(integer = 10, fraction = 4) BigDecimal unitPrice,
      @Digits(integer = 12, fraction = 2) @DecimalMin("-999999999999.99") @DecimalMax("999999999999.99") BigDecimal total) {}

  public record IssueDto(String code, String severity, String field, String message) {}

  /** What the current user may do with this invoice, computed server-side so the UI never guesses. */
  public record Permissions(boolean canEdit, boolean canApprove, boolean canReject, boolean canRetry, boolean canDelete) {}

  public record InvoiceSummary(
      UUID id, InvoiceStatus status, String originalFilename, String vendorName, String invoiceNumber,
      LocalDate invoiceDate, LocalDate dueDate, String currency, BigDecimal total,
      UUID uploadedBy, String uploadedByName, int errorCount, int warningCount, Instant createdAt) {}

  public record OcrInfo(String engine, BigDecimal confidence, Integer pageCount) {}

  public record UsageInfo(String model, Integer inputTokens, Integer outputTokens, BigDecimal costUsd) {}

  public record InvoiceDetail(
      UUID id, InvoiceStatus status, String originalFilename, String contentType, long fileSize,
      String vendorName, String invoiceNumber, LocalDate invoiceDate, LocalDate dueDate, String currency,
      BigDecimal subtotal, BigDecimal tax, BigDecimal total,
      Map<String, Double> fieldConfidence, List<String> modelReviewReasons, boolean injectionSuspected,
      boolean humanReviewed, OcrInfo ocr, UsageInfo usage, Long processingMs, String failureReason,
      UUID uploadedBy, String uploadedByName, String decidedByName, Instant decidedAt, String decisionComment,
      List<LineItemDto> lineItems, List<IssueDto> issues, Permissions permissions,
      Instant createdAt, Instant updatedAt, long version) {}

  /** Full-replacement correction of the extracted fields. {@code version} guards against concurrent edits. */
  public record UpdateInvoiceRequest(
      @Size(max = 300) String vendorName,
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
