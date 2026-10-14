package com.invoicemind.ai.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.util.List;

/** Wire format of the ai-service (snake_case JSON). Everything in here is untrusted input. */
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
  public record Extraction(String vendor, String invoiceNumber, String invoiceDate, String dueDate, String currency,
                           List<LineItem> lineItems, BigDecimal subtotal, BigDecimal tax, BigDecimal total,
                           FieldConfidence confidence, boolean needsReview, List<String> reviewReasons) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Usage(String model, int inputTokens, int outputTokens, double costUsd, long latencyMs) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ExtractResponse(Extraction extraction, boolean injectionSuspected, Usage usage) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record EmbedResponse(List<List<Float>> vectors, String model, int dimensions, int inputTokens, double costUsd) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Passage(int id, String title, String text) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record ExplainRequest(String question, String facts, List<Passage> passages) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ExplainResponse(String answer, List<Integer> citations, boolean insufficientContext,
                                boolean injectionSuspected, Usage usage) {}
}
