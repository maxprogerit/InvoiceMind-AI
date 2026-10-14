package com.invoicemind.ai.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {
  private AnalyticsDtos() {}

  public record MoneyByCurrency(String currency, BigDecimal amount, long invoices) {}

  public record Summary(
      long totalInvoices,
      long awaitingDecision,
      long approved,
      long rejected,
      long failed,
      Double avgProcessingSeconds,
      Double reviewRate,
      Double autoValidatedRate,
      Double failureRate,
      Double avgOcrConfidence,
      long inputTokens,
      long outputTokens,
      BigDecimal costUsd,
      BigDecimal avgCostPerInvoiceUsd,
      List<MoneyByCurrency> approvedSpend,
      List<MoneyByCurrency> pendingAmount,
      String defaultCurrency) {}

  public record VendorSpend(String vendor, BigDecimal amount, long invoices) {}

  public record SpendByVendor(String currency, List<VendorSpend> items) {}

  public record MonthSpend(String month, BigDecimal amount, long invoices) {}

  public record SpendByMonth(String currency, List<MonthSpend> items) {}

  public record StatusCount(String status, long count, BigDecimal amount) {}

  public record ByStatus(List<StatusCount> items) {}

  public record DayVolume(LocalDate day, long uploaded, long flagged, long failed) {}

  public record VolumeByDay(List<DayVolume> items) {}

  public record Totals(Map<String, Long> counts) {}
}
