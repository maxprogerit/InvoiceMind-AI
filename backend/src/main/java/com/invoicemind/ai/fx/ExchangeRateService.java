package com.invoicemind.ai.fx;

import com.invoicemind.ai.analytics.AnalyticsCache;
import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.fx.FxDtos.FxStatus;
import com.invoicemind.ai.fx.FxDtos.ManualRate;
import com.invoicemind.ai.fx.FxDtos.ManualRateRequest;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.user.OrganizationEntity;
import com.invoicemind.ai.user.OrganizationRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reference exchange rates. Shared ECB rates are downloaded lazily for exactly the date range the analytics
 * need and then kept current by a scheduled job; an organisation can add manual rates for currencies the ECB
 * does not publish (RUB since 2022, for example). Every rate is stored per 1 EUR; see {@code V4}.
 *
 * <p>Failures never break analytics: they are recorded, shown on the Settings page, and the affected invoices are
 * reported as "not converted" instead of being converted with an invented rate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {
  private static final int CHUNK_DAYS = 365;

  private final NamedParameterJdbcTemplate jdbc;
  private final RateSource source;
  private final AppProperties props;
  private final OrganizationRepository organizations;
  private final AnalyticsCache cache;
  private final AuditService audit;
  private final Clock clock;
  private final PlatformTransactionManager txManager;

  private record State(LocalDate from, LocalDate to, Instant lastAttempt, String lastError) {}

  /**
   * Makes sure the shared rates reach from {@code earliest} to today. Never throws.
   * Synchronized so concurrent dashboards do not download the same range twice.
   */
  public synchronized void ensureCovered(LocalDate earliest) {
    if (!props.fx().enabled() || earliest == null) return;
    LocalDate today = LocalDate.now(clock);
    LocalDate from = earliest.isAfter(today) ? today : earliest;
    State state = state();

    List<LocalDate[]> ranges = new ArrayList<>();
    if (state.from() == null) {
      ranges.add(new LocalDate[]{from, today});
    } else {
      if (from.isBefore(state.from())) ranges.add(new LocalDate[]{from, state.from().minusDays(1)});
      if (state.to().isBefore(today.minusDays(1))) ranges.add(new LocalDate[]{state.to().plusDays(1), today});
    }
    if (ranges.isEmpty() || recentlyFailed(state)) return;
    download(ranges);
  }

  /** Keeps the tail current once anything has been downloaded. */
  @Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT2M")
  public synchronized void refreshTail() {
    if (!props.fx().enabled()) return;
    State state = state();
    LocalDate today = LocalDate.now(clock);
    if (state.to() == null || !state.to().isBefore(today)) return;
    download(java.util.Collections.singletonList(new LocalDate[]{state.to().plusDays(1), today}));
  }

  private void download(List<LocalDate[]> ranges) {
    try {
      for (LocalDate[] range : ranges) {
        for (LocalDate start = range[0]; !start.isAfter(range[1]); start = start.plusDays(CHUNK_DAYS)) {
          LocalDate end = min(start.plusDays(CHUNK_DAYS - 1L), range[1]);
          Map<LocalDate, Map<String, BigDecimal>> days = source.fetch(start, end);
          LocalDate chunkStart = start;
          int stored = requiresNew().execute(s -> {
            int n = store(days);
            extendCoverage(chunkStart, end);
            return n;
          });
          log.info("Downloaded {} ECB rates for {}..{}", stored, start, end);
        }
      }
      requiresNew().executeWithoutResult(s -> markAttempt(null));
    } catch (IOException | RuntimeException e) {
      log.warn("Exchange rates could not be downloaded: {}", e.getMessage());
      requiresNew().executeWithoutResult(s -> markAttempt(truncate(e.getMessage())));
    }
  }

  /** The writes run in their own transaction: callers are usually inside a read-only analytics transaction. */
  private TransactionTemplate requiresNew() {
    TransactionTemplate t = new TransactionTemplate(txManager);
    t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return t;
  }

  private int store(Map<LocalDate, Map<String, BigDecimal>> days) {
    List<MapSqlParameterSource> batch = new ArrayList<>();
    days.forEach((date, rates) -> rates.forEach((currency, perEur) -> {
      if ("EUR".equals(currency) || !currency.matches("[A-Z]{3}")) return;
      batch.add(new MapSqlParameterSource().addValue("d", Date.valueOf(date)).addValue("c", currency)
          .addValue("r", perEur));
    }));
    if (!batch.isEmpty()) {
      jdbc.batchUpdate("""
          insert into exchange_rates (rate_date, currency, per_eur, source) values (:d, :c, :r, 'ECB')
          on conflict (rate_date, currency) where organization_id is null do nothing""",
          batch.toArray(MapSqlParameterSource[]::new));
    }
    return batch.size();
  }

  // ---------------------------------------------------------------- state
  private State state() {
    return jdbc.query("select covered_from, covered_to, last_attempt, last_error from fx_sync_state where id = 1",
        rs -> {
          rs.next();
          Date from = rs.getDate("covered_from"), to = rs.getDate("covered_to");
          var attempt = rs.getTimestamp("last_attempt");
          return new State(from == null ? null : from.toLocalDate(), to == null ? null : to.toLocalDate(),
              attempt == null ? null : attempt.toInstant(), rs.getString("last_error"));
        });
  }

  private boolean recentlyFailed(State s) {
    return s.lastError() != null && s.lastAttempt() != null
        && Instant.now(clock).isBefore(s.lastAttempt().plus(Duration.ofMinutes(props.fx().retryAfterMinutes())));
  }

  private void extendCoverage(LocalDate from, LocalDate to) {
    jdbc.update("""
        update fx_sync_state set covered_from = least(coalesce(covered_from, :from), :from),
                                 covered_to = greatest(coalesce(covered_to, :to), :to)
        where id = 1""", new MapSqlParameterSource().addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to)));
  }

  private void markAttempt(String error) {
    jdbc.update("update fx_sync_state set last_attempt = now(), last_error = :e where id = 1",
        new MapSqlParameterSource().addValue("e", error));
  }

  // ----------------------------------------------------------- admin API
  @Transactional(readOnly = true)
  public FxStatus status(UUID org) {
    State state = state();
    return new FxStatus(baseCurrency(org), props.fx().enabled(), state.from(), state.to(), state.lastError(),
        manualRates(org));
  }

  @Transactional(readOnly = true)
  public String baseCurrency(UUID org) {
    return organizations.findById(org).map(OrganizationEntity::getBaseCurrency).orElse("EUR");
  }

  @Transactional
  public String setBaseCurrency(AuthPrincipal me, String requested) {
    String code = isoCode(requested);
    OrganizationEntity o = organizations.findById(me.organizationId()).orElseThrow(() -> ApiException.notFound("Organization"));
    if (!o.getBaseCurrency().equals(code)) {
      audit.log(me.organizationId(), me.userId(), "BASE_CURRENCY_CHANGED", AuditService.ORGANIZATION, me.organizationId(),
          Map.of("from", o.getBaseCurrency(), "to", code));
      o.setBaseCurrency(code);
      organizations.save(o);
      cache.invalidate(me.organizationId());
    }
    return code;
  }

  @Transactional
  public ManualRate upsertManual(AuthPrincipal me, ManualRateRequest req) {
    String code = isoCode(req.currency());
    if ("EUR".equals(code)) {
      throw ApiException.badRequest("invalid_currency", "EUR is the reference currency and always has the rate 1");
    }
    if (req.date().isAfter(LocalDate.now(clock).plusDays(1))) {
      throw ApiException.badRequest("invalid_date", "A rate cannot be dated in the future");
    }
    Long id = jdbc.queryForObject("""
        insert into exchange_rates (organization_id, rate_date, currency, per_eur, source)
        values (:org, :d, :c, :r, 'MANUAL')
        on conflict (organization_id, rate_date, currency) where organization_id is not null
        do update set per_eur = excluded.per_eur
        returning id""", new MapSqlParameterSource().addValue("org", me.organizationId())
        .addValue("d", Date.valueOf(req.date())).addValue("c", code).addValue("r", req.perEur()), Long.class);
    audit.log(me.organizationId(), me.userId(), "FX_RATE_SET", AuditService.ORGANIZATION, me.organizationId(),
        Map.of("currency", code, "date", req.date().toString(), "perEur", req.perEur().toPlainString()));
    cache.invalidate(me.organizationId());
    return new ManualRate(id, code, req.date(), req.perEur(), Instant.now(clock));
  }

  @Transactional
  public void deleteManual(AuthPrincipal me, long id) {
    int n = jdbc.update("delete from exchange_rates where id = :id and organization_id = :org and source = 'MANUAL'",
        new MapSqlParameterSource().addValue("id", id).addValue("org", me.organizationId()));
    if (n == 0) throw ApiException.notFound("Rate");
    audit.log(me.organizationId(), me.userId(), "FX_RATE_DELETED", AuditService.ORGANIZATION, me.organizationId(),
        Map.of("id", id));
    cache.invalidate(me.organizationId());
  }

  private List<ManualRate> manualRates(UUID org) {
    return jdbc.query("""
        select id, currency, rate_date, per_eur, created_at from exchange_rates
        where organization_id = :org order by currency, rate_date desc""",
        new MapSqlParameterSource("org", org),
        (rs, i) -> new ManualRate(rs.getLong("id"), rs.getString("currency"), rs.getDate("rate_date").toLocalDate(),
            rs.getBigDecimal("per_eur"), rs.getTimestamp("created_at").toInstant()));
  }

  // ------------------------------------------------------------- helpers
  static String isoCode(String raw) {
    String code = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    try {
      if (code.length() == 3) {
        Currency.getInstance(code);
        return code;
      }
    } catch (IllegalArgumentException ignored) {
      // falls through to the error below
    }
    throw ApiException.badRequest("invalid_currency", "'" + raw + "' is not an ISO 4217 currency code");
  }

  private static LocalDate min(LocalDate a, LocalDate b) { return a.isBefore(b) ? a : b; }

  private static String truncate(String s) {
    if (s == null) return "unknown error";
    return s.length() <= 300 ? s : s.substring(0, 300);
  }
}
