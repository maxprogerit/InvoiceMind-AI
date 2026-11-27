package com.invoicemind.ai.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.util.List;

/** Wire format of the extract-service (snake_case JSON; money as decimal strings). Everything in here is untrusted input. */
public final class AiDtos {
  private AiDtos() {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OcrPage(int page, String text, double confidence, String method) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OcrResponse(String text, List<OcrPage> pages, int pageCount, double confidence, String engine,
                            String mimeType) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record LineItem(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FieldConfidence(double vendor, double invoiceNumber, double invoiceDate, double dueDate,
                                double currency, double lineItems, double subtotal, double tax, double total) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Extraction(String documentType, String vendor, String vendorTaxId, String invoiceNumber, String invoiceDate, String dueDate, String currency,
                           List<LineItem> lineItems, BigDecimal subtotal, BigDecimal tax, BigDecimal total,
                           FieldConfidence confidence, boolean needsReview, List<String> reviewReasons) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Usage(String model, int inputTokens, int outputTokens, BigDecimal costUsd, long latencyMs) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ExtractIssue(String code, String severity, String field, String message) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ExtractValidation(String status, List<ExtractIssue> issues) {}

  /**
   * Response of POST /invoices. {@code source} is how the text was obtained ({@code text_layer}, {@code ocr} or
   * {@code mixed}); {@code validation} is the extract-service's own first pass (the backend re-validates in Java).
   */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record InvoiceResult(String source, OcrResponse ocr, Extraction extraction, ExtractValidation validation,
                              boolean injectionSuspected, Usage usage) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record EmbedResponse(List<List<Float>> vectors, String model, int dimensions, int inputTokens, BigDecimal costUsd) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Passage(int id, String title, String text) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record ExplainRequest(String question, String facts, List<Passage> passages) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ExplainResponse(String answer, List<Integer> citations, boolean insufficientContext,
                                boolean injectionSuspected, Usage usage) {}
}
