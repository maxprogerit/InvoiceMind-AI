package com.invoicemind.ai.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * In-memory cache for analytics aggregations. Keys embed a per-organisation version counter; bumping the counter on any
 * invoice change invalidates every cached result of that organisation at once. Entries also expire after five minutes
 * and the map is kept small.
 */
@Component
public class AnalyticsCache {
  private static final Duration TTL = Duration.ofMinutes(5);
  private static final int MAX_ENTRIES = 2_000;

  private record Entry(Object value, Instant expiresAt) {}

  private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, AtomicLong> versions = new ConcurrentHashMap<>();

  @SuppressWarnings("unchecked")
  public <T> T get(UUID org, UUID scopeUser, String name, TypeReference<T> type, Supplier<T> compute) {
    String key = org + ":" + version(org) + ":" + (scopeUser == null ? "all" : scopeUser) + ":" + name;
    Entry hit = entries.get(key);
    if (hit != null && hit.expiresAt().isAfter(Instant.now())) return (T) hit.value();

    T value = compute.get();
    if (entries.size() >= MAX_ENTRIES) {
      Instant now = Instant.now();
      entries.values().removeIf(e -> !e.expiresAt().isAfter(now));
      if (entries.size() >= MAX_ENTRIES) entries.clear();
    }
    entries.put(key, new Entry(value, Instant.now().plus(TTL)));
    return value;
  }

  public void invalidate(UUID org) {
    versions.computeIfAbsent(org, k -> new AtomicLong()).incrementAndGet();
  }

  private long version(UUID org) {
    AtomicLong v = versions.get(org);
    return v == null ? 0 : v.get();
  }
}
