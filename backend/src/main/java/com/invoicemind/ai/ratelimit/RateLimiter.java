package com.invoicemind.ai.ratelimit;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fixed one-minute windows in PostgreSQL ({@code rate_limit_counters}): one atomic upsert per request, shared by
 * every backend instance. Fails open if the database cannot be written, so a counter problem cannot lock users out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiter {
  private final NamedParameterJdbcTemplate jdbc;
  private final Clock clock;

  /** Runs in its own transaction: a rejected request must still count, even if the caller's transaction rolls back. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void check(String bucket, String key, int limitPerMinute) {
    Instant now = Instant.now(clock);
    Instant window = now.truncatedTo(ChronoUnit.MINUTES);
    String bucketKey = truncate(bucket + ":" + key, 400);
    Integer hits;
    try {
      hits = jdbc.queryForObject("""
          insert into rate_limit_counters (bucket_key, window_start, hits) values (:k, :w, 1)
          on conflict (bucket_key, window_start) do update set hits = rate_limit_counters.hits + 1
          returning hits""",
          new MapSqlParameterSource("k", bucketKey).addValue("w", Timestamp.from(window)), Integer.class);
    } catch (DataAccessException e) {
      log.warn("Rate limiter unavailable, allowing request: {}", e.getClass().getSimpleName());
      return;
    }
    if (hits != null && hits > limitPerMinute) {
      int retryAfter = (int) Duration.between(now, window.plus(1, ChronoUnit.MINUTES)).toSeconds();
      throw new RateLimitedException(Math.max(retryAfter, 1));
    }
  }

  @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
  public void purgeOldWindows() {
    try {
      jdbc.update("delete from rate_limit_counters where window_start < :cutoff",
          new MapSqlParameterSource("cutoff", Timestamp.from(Instant.now(clock).minus(10, ChronoUnit.MINUTES))));
    } catch (DataAccessException e) {
      log.debug("Rate limit purge skipped: {}", e.getMessage());
    }
  }

  private static String truncate(String s, int max) {
    return s.length() > max ? s.substring(0, max) : s;
  }
}
