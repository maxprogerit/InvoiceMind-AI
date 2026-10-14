package com.invoicemind.ai.analytics;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** {@code @NotNull} marks always-present fields in the OpenAPI contract; nullable values are omitted from JSON. */
public final class AnalyticsDtos {
  private AnalyticsDtos() {}

  public record MoneyByCurrency(@NotNull String currency, @NotNull BigDecimal amount, @NotNull long invoices) {}

  public record Summary(
      @NotNull long totalInvoices,
      @NotNull long awaitingDecision,
      @NotNull long approved,
      @NotNull long rejected,
      @NotNull long failed,
      Double avgProcessingSeconds,
      Double reviewRate,
      Double autoValidatedRate,
      Double failureRate,
      Double avgOcrConfidence,
      @NotNull long inputTokens,
      @NotNull long outputTokens,
      @NotNull BigDecimal costUsd,
      @NotNull BigDecimal avgCostPerInvoiceUsd,
      @NotNull List<MoneyByCurrency> approvedSpend,
      @NotNull List<MoneyByCurrency> pendingAmount,
      String defaultCurrency) {}

  public record VendorSpend(@NotNull String vendor, @NotNull BigDecimal amount, @NotNull long invoices) {}

  public record SpendByVendor(@NotNull String currency, @NotNull List<VendorSpend> items) {}

  public record MonthSpend(@NotNull String month, @NotNull BigDecimal amount, @NotNull long invoices) {}

  public record SpendByMonth(@NotNull String currency, @NotNull List<MonthSpend> items) {}

  public record StatusCount(@NotNull String status, @NotNull long count, @NotNull BigDecimal amount) {}

  public record ByStatus(@NotNull List<StatusCount> items) {}

  public record DayVolume(@NotNull LocalDate day, @NotNull long uploaded, @NotNull long flagged, @NotNull long failed) {}

  public record VolumeByDay(@NotNull List<DayVolume> items) {}
}
