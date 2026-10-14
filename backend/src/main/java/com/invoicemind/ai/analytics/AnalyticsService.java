package com.invoicemind.ai.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.invoicemind.ai.analytics.AnalyticsDtos.ByStatus;
import com.invoicemind.ai.analytics.AnalyticsDtos.ConvertedMoney;
import com.invoicemind.ai.analytics.AnalyticsDtos.DayVolume;
import com.invoicemind.ai.analytics.AnalyticsDtos.MoneyByCurrency;
import com.invoicemind.ai.analytics.AnalyticsDtos.MonthSpend;
import com.invoicemind.ai.analytics.AnalyticsDtos.SpendByMonth;
import com.invoicemind.ai.analytics.AnalyticsDtos.SpendByVendor;
import com.invoicemind.ai.analytics.AnalyticsDtos.StatusCount;
import com.invoicemind.ai.analytics.AnalyticsDtos.Summary;
import com.invoicemind.ai.analytics.AnalyticsDtos.VendorSpend;
import com.invoicemind.ai.analytics.AnalyticsDtos.VolumeByDay;
import com.invoicemind.ai.fx.ExchangeRateService;
import com.invoicemind.ai.invoice.InvoiceService;
import com.invoicemind.ai.security.AuthPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real aggregations over the invoices table. A USER only aggregates their own uploads, reviewers the
 * whole organisation. "Spend" means APPROVED invoices; amounts are never summed across currencies.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {
  private final NamedParameterJdbcTemplate jdbc;
  private final AnalyticsCache cache;
  private final Clock clock;
  private final ExchangeRateService fx;

  @Transactional(readOnly = true)
  public Summary summary(AuthPrincipal me) {
    return cached(me, "summary", new TypeReference<Summary>() {}, () -> computeSummary(me));
  }

  @Transactional(readOnly = true)
  public SpendByVendor spendByVendor(AuthPrincipal me, String currency, int limit, boolean converted) {
    int n = Math.min(Math.max(limit, 1), 50);
    if (converted) return convertedSpendByVendor(me, n);
    String cur = resolveCurrency(me, currency);
    return cached(me, "vendor:" + cur + ":" + n, new TypeReference<SpendByVendor>() {}, () -> {
      var items = jdbc.query("""
          select coalesce(vendor_name, '(unknown)') as vendor, sum(total) as amount, count(*) as n
          from invoices
          where organization_id = :org and (cast(:uploader as uuid) is null or uploaded_by = cast(:uploader as uuid))
            and status = 'APPROVED' and currency = :currency and total is not null
          group by 1 order by amount desc limit :limit""",
          params(me).addValue("currency", cur).addValue("limit", n),
          (rs, i) -> new VendorSpend(rs.getString("vendor"), rs.getBigDecimal("amount"), rs.getLong("n")));
      return new SpendByVendor(cur, items, List.of());
    });
  }

  @Transactional(readOnly = true)
  public SpendByMonth spendByMonth(AuthPrincipal me, String currency, int months, boolean converted) {
    int m = Math.min(Math.max(months, 1), 36);
    if (converted) return convertedSpendByMonth(me, m);
    String cur = resolveCurrency(me, currency);
    return cached(me, "month:" + cur + ":" + m, new TypeReference<SpendByMonth>() {}, () -> {
      LocalDate from = LocalDate.now(clock).withDayOfMonth(1).minusMonths(m - 1L);
      var rows = jdbc.query("""
          select to_char(date_trunc('month', coalesce(invoice_date, created_at::date)), 'YYYY-MM') as month,
                 sum(total) as amount, count(*) as n
          from invoices
          where organization_id = :org and (cast(:uploader as uuid) is null or uploaded_by = cast(:uploader as uuid))
            and status = 'APPROVED' and currency = :currency and total is not null
            and coalesce(invoice_date, created_at::date) >= :from
          group by 1 order by 1""",
          params(me).addValue("currency", cur).addValue("from", java.sql.Date.valueOf(from)),
          (rs, i) -> new MonthSpend(rs.getString("month"), rs.getBigDecimal("amount"), rs.getLong("n")));
      // fill months without approved spend so the chart axis is continuous
      List<MonthSpend> filled = new java.util.ArrayList<>();
      for (int k = 0; k < m; k++) {
        String key = from.plusMonths(k).toString().substring(0, 7);
        filled.add(rows.stream().filter(r -> r.month().equals(key)).findFirst()
            .orElse(new MonthSpend(key, BigDecimal.ZERO, 0)));
      }
      return new SpendByMonth(cur, filled, List.of());
    });
  }

  // ------------------------------------------------- converted to the base currency
  /**
   * Common table expressions that attach to every matching invoice its amount in the base currency. The rate of
   * each invoice is the latest one on or before its date; ECB rates older than 14 days are not trusted (the ECB
   * publishes every business day), manual rates stay valid until replaced. No rate means {@code base_amount} is NULL
   * and the invoice is reported as "not converted" - never converted with a guess.
   */
  private static String convertedCte(String statusCondition) {
    String rate = """
        (select r.per_eur from exchange_rates r
          where r.currency = %s and r.rate_date <= s.d
            and (r.organization_id is null or r.organization_id = :org)
            and (r.organization_id is not null or r.rate_date > s.d - 14)
          order by r.rate_date desc, (r.organization_id is null) limit 1)""";
    return """
        with src as (
          select vendor_name, total, currency, coalesce(invoice_date, created_at::date) as d
          from invoices
          where organization_id = :org and (cast(:uploader as uuid) is null or uploaded_by = cast(:uploader as uuid))
            and %s and total is not null and currency is not null),
        conv as (
          select s.*,
            case when s.currency = :base then s.total
                 else s.total / nullif(case when s.currency = 'EUR' then 1::numeric else %s end, 0)
                              * case when :base = 'EUR' then 1::numeric else %s end
            end as base_amount
          from src s)
        """.formatted(statusCondition, rate.formatted("s.currency"), rate.formatted(":base"));
  }

  private MapSqlParameterSource convertedParams(AuthPrincipal me, String base) {
    return params(me).addValue("base", base);
  }

  /** Makes sure the shared ECB rates reach back to the oldest invoice that needs one. */
  private void prepareRates(AuthPrincipal me, String base) {
    LocalDate earliest = jdbc.query("""
        select min(coalesce(invoice_date, created_at::date)) from invoices
        where organization_id = :org and total is not null and currency is not null and currency <> :base""",
        new MapSqlParameterSource("org", me.organizationId()).addValue("base", base),
        rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null);
    if (earliest != null) fx.ensureCovered(earliest);
  }

  private List<MoneyByCurrency> notConverted(MapSqlParameterSource p, String statusCondition) {
    return jdbc.query(convertedCte(statusCondition) + """
        select currency, sum(total) as amount, count(*) as n from conv where base_amount is null
        group by currency order by amount desc""", p,
        (rs, i) -> new MoneyByCurrency(rs.getString("currency"), rs.getBigDecimal("amount"), rs.getLong("n")));
  }

  private ConvertedMoney convertedMoney(AuthPrincipal me, String base, String statusCondition) {
    MapSqlParameterSource p = convertedParams(me, base);
    var row = jdbc.queryForMap(convertedCte(statusCondition) + """
        select coalesce(sum(base_amount), 0) as amount, count(base_amount) as n from conv""", p);
    return new ConvertedMoney(((BigDecimal) row.get("amount")).setScale(2, RoundingMode.HALF_UP), num(row.get("n")),
        notConverted(p, statusCondition));
  }

  private SpendByVendor convertedSpendByVendor(AuthPrincipal me, int limit) {
    String base = fx.baseCurrency(me.organizationId());
    return cached(me, "vendor-converted:" + base + ":" + limit, new TypeReference<SpendByVendor>() {}, () -> {
      prepareRates(me, base);
      MapSqlParameterSource p = convertedParams(me, base);
      var items = jdbc.query(convertedCte("status = 'APPROVED'") + """
          select coalesce(vendor_name, '(unknown)') as vendor, sum(base_amount) as amount, count(*) as n
          from conv where base_amount is not null
          group by 1 order by amount desc limit :limit""", p.addValue("limit", limit),
          (rs, i) -> new VendorSpend(rs.getString("vendor"), rs.getBigDecimal("amount").setScale(2, RoundingMode.HALF_UP),
              rs.getLong("n")));
      return new SpendByVendor(base, items, notConverted(p, "status = 'APPROVED'"));
    });
  }

  private SpendByMonth convertedSpendByMonth(AuthPrincipal me, int months) {
    String base = fx.baseCurrency(me.organizationId());
    return cached(me, "month-converted:" + base + ":" + months, new TypeReference<SpendByMonth>() {}, () -> {
      prepareRates(me, base);
      LocalDate from = LocalDate.now(clock).withDayOfMonth(1).minusMonths(months - 1L);
      MapSqlParameterSource p = convertedParams(me, base);
      var rows = jdbc.query(convertedCte("status = 'APPROVED'") + """
          select to_char(date_trunc('month', d), 'YYYY-MM') as month, sum(base_amount) as amount, count(*) as n
          from conv where base_amount is not null and d >= :from
          group by 1 order by 1""", p.addValue("from", java.sql.Date.valueOf(from)),
          (rs, i) -> new MonthSpend(rs.getString("month"), rs.getBigDecimal("amount").setScale(2, RoundingMode.HALF_UP),
              rs.getLong("n")));
      List<MonthSpend> filled = new java.util.ArrayList<>();
      for (int k = 0; k < months; k++) {
        String key = from.plusMonths(k).toString().substring(0, 7);
        filled.add(rows.stream().filter(r -> r.month().equals(key)).findFirst()
            .orElse(new MonthSpend(key, BigDecimal.ZERO, 0)));
      }
      return new SpendByMonth(base, filled, notConverted(p, "status = 'APPROVED'"));
    });
  }

  @Transactional(readOnly = true)
  public ByStatus byStatus(AuthPrincipal me) {
    return cached(me, "status", new TypeReference<ByStatus>() {}, () -> new ByStatus(jdbc.query("""
        select status, count(*) as n, coalesce(sum(total), 0) as amount
        from invoices
        where organization_id = :org and (cast(:uploader as uuid) is null or uploaded_by = cast(:uploader as uuid))
        group by status order by n desc""", params(me),
        (rs, i) -> new StatusCount(rs.getString("status"), rs.getLong("n"), rs.getBigDecimal("amount")))));
  }

  @Transactional(readOnly = true)
  public VolumeByDay volumeByDay(AuthPrincipal me, int days) {
    int d = Math.min(Math.max(days, 1), 180);
    return cached(me, "volume:" + d, new TypeReference<VolumeByDay>() {}, () -> {
      LocalDate from = LocalDate.now(clock).minusDays(d - 1L);
      var rows = jdbc.query("""
          select created_at::date as day, count(*) as uploaded,
                 count(*) filter (where was_flagged) as flagged,
                 count(*) filter (where status = 'FAILED') as failed
          from invoices
          where organization_id = :org and (cast(:uploader as uuid) is null or uploaded_by = cast(:uploader as uuid))
            and created_at::date >= :from
          group by 1 order by 1""",
          params(me).addValue("from", java.sql.Date.valueOf(from)),
          (rs, i) -> new DayVolume(rs.getDate("day").toLocalDate(), rs.getLong("uploaded"), rs.getLong("flagged"),
              rs.getLong("failed")));
      List<DayVolume> filled = new java.util.ArrayList<>();
      for (int k = 0; k < d; k++) {
        LocalDate day = from.plusDays(k);
        filled.add(rows.stream().filter(r -> r.day().equals(day)).findFirst().orElse(new DayVolume(day, 0, 0, 0)));
      }
      return new VolumeByDay(filled);
    });
  }

  // ------------------------------------------------------------------ internals
  private Summary computeSummary(AuthPrincipal me) {
    MapSqlParameterSource p = params(me);
    var k = jdbc.queryForMap("""
        select count(*) as total,
               count(*) filter (where status in ('NEEDS_REVIEW','VALIDATED')) as awaiting,
               count(*) filter (where status = 'APPROVED') as approved,
               count(*) filter (where status = 'REJECTED') as rejected,
               count(*) filter (where status = 'FAILED') as failed,
               count(*) filter (where processing_finished_at is not null and status <> 'FAILED') as processed,
               count(*) filter (where processing_finished_at is not null and status <> 'FAILED' and was_flagged) as flagged,
               avg(processing_ms) filter (where status <> 'FAILED') as avg_ms,
               avg(ocr_confidence) as avg_ocr,
               coalesce(sum(input_tokens), 0) as in_tok, coalesce(sum(output_tokens), 0) as out_tok,
               coalesce(sum(cost_usd), 0) as cost,
               count(*) filter (where cost_usd is not null) as costed
        from invoices
        where organization_id = :org and (cast(:uploader as uuid) is null or uploaded_by = cast(:uploader as uuid))""", p);

    long total = num(k.get("total"));
    long processed = num(k.get("processed"));
    long failed = num(k.get("failed"));
    long flagged = num(k.get("flagged"));
    BigDecimal cost = (BigDecimal) k.get("cost");
    long costed = num(k.get("costed"));

    List<MoneyByCurrency> approved = money(p, "status = 'APPROVED'");
    List<MoneyByCurrency> pending = money(p, "status in ('NEEDS_REVIEW','VALIDATED')");
    String defaultCurrency = approved.isEmpty() ? (pending.isEmpty() ? null : pending.get(0).currency())
        : approved.get(0).currency();

    String base = fx.baseCurrency(me.organizationId());
    prepareRates(me, base);
    ConvertedMoney approvedConverted = convertedMoney(me, base, "status = 'APPROVED'");
    ConvertedMoney pendingConverted = convertedMoney(me, base, "status in ('NEEDS_REVIEW','VALIDATED')");

    return new Summary(total, num(k.get("awaiting")), num(k.get("approved")), num(k.get("rejected")), failed,
        k.get("avg_ms") == null ? null : round(((Number) k.get("avg_ms")).doubleValue() / 1000.0, 2),
        processed == 0 ? null : round(flagged / (double) processed, 4),
        processed == 0 ? null : round(1 - flagged / (double) processed, 4),
        processed + failed == 0 ? null : round(failed / (double) (processed + failed), 4),
        k.get("avg_ocr") == null ? null : round(((Number) k.get("avg_ocr")).doubleValue(), 4),
        num(k.get("in_tok")), num(k.get("out_tok")), cost.setScale(4, RoundingMode.HALF_UP),
        costed == 0 ? BigDecimal.ZERO : cost.divide(BigDecimal.valueOf(costed), 6, RoundingMode.HALF_UP),
        approved, pending, defaultCurrency, base, approvedConverted, pendingConverted);
  }

  /** {@code condition} is one of two fixed literals above, never user input. */
  private List<MoneyByCurrency> money(MapSqlParameterSource p, String condition) {
    return jdbc.query("""
        select currency, sum(total) as amount, count(*) as n
        from invoices
        where organization_id = :org and (cast(:uploader as uuid) is null or uploaded_by = cast(:uploader as uuid))
          and %s and currency is not null and total is not null
        group by currency order by amount desc""".formatted(condition), p,
        (rs, i) -> new MoneyByCurrency(rs.getString("currency"), rs.getBigDecimal("amount"), rs.getLong("n")));
  }

  private String resolveCurrency(AuthPrincipal me, String requested) {
    if (requested != null && requested.matches("[A-Za-z]{3}")) return requested.toUpperCase(Locale.ROOT);
    String fallback = summary(me).defaultCurrency();
    return fallback == null ? "USD" : fallback;
  }

  private <T> T cached(AuthPrincipal me, String name, TypeReference<T> type, java.util.function.Supplier<T> compute) {
    return cache.get(me.organizationId(), scope(me), name, type, compute);
  }

  private UUID scope(AuthPrincipal me) { return InvoiceService.scopeOf(me); }

  private MapSqlParameterSource params(UUID uploader) {
    // org is added by the caller through scope(); keep both bound explicitly
    return new MapSqlParameterSource()
        .addValue("uploader", uploader == null ? null : uploader.toString(), Types.VARCHAR);
  }

  private MapSqlParameterSource params(AuthPrincipal me) {
    return params(scope(me)).addValue("org", me.organizationId());
  }

  private static long num(Object o) { return o == null ? 0 : ((Number) o).longValue(); }

  private static double round(double v, int places) {
    return BigDecimal.valueOf(v).setScale(places, RoundingMode.HALF_UP).doubleValue();
  }
}
