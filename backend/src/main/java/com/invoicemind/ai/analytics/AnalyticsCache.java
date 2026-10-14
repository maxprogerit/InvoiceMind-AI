package com.invoicemind.ai.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis cache for analytics aggregations. Keys embed a per-organisation version counter; bumping the
 * counter on any invoice change invalidates every cached result of that organisation at once.
 * Redis failures degrade to direct computation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsCache {
  private static final Duration TTL = Duration.ofMinutes(5);

  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;

  public <T> T get(UUID org, UUID scopeUser, String name, TypeReference<T> type, Supplier<T> compute) {
    String key = null;
    try {
      key = "analytics:" + org + ":" + version(org) + ":" + (scopeUser == null ? "all" : scopeUser) + ":" + name;
      String hit = redis.opsForValue().get(key);
      if (hit != null) return mapper.readValue(hit, type);
    } catch (DataAccessException | java.io.IOException e) {
      log.debug("Analytics cache read skipped: {}", e.getMessage());
    }
    T value = compute.get();
    if (key != null) {
      try {
        redis.opsForValue().set(key, mapper.writeValueAsString(value), TTL);
      } catch (DataAccessException | java.io.IOException e) {
        log.debug("Analytics cache write skipped: {}", e.getMessage());
      }
    }
    return value;
  }

  public void invalidate(UUID org) {
    try {
      redis.opsForValue().increment("analytics:ver:" + org);
    } catch (DataAccessException e) {
      log.debug("Analytics cache invalidation skipped: {}", e.getMessage());
    }
  }

  private String version(UUID org) {
    String v = redis.opsForValue().get("analytics:ver:" + org);
    return v == null ? "0" : v;
  }
}
