package com.invoicemind.ai.ratelimit;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Fixed-window counter per bucket and key, kept in memory (one backend process). Counters of past minutes are dropped
 * as time moves on, so memory stays bounded by the traffic of one minute.
 */
@Service
public class RateLimiter {
  private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
  private volatile long currentMinute;

  public void check(String bucket, String key, int limitPerMinute) {
    long now = Instant.now().getEpochSecond();
    long minute = now / 60;
    if (minute != currentMinute) {
      currentMinute = minute;
      String suffix = ":" + minute;
      counters.keySet().removeIf(k -> !k.endsWith(suffix));
    }
    int count = counters.computeIfAbsent(bucket + ":" + key + ":" + minute, k -> new AtomicInteger()).incrementAndGet();
    if (count > limitPerMinute) {
      throw new RateLimitedException((int) Math.max(60 - (now % 60), 1));
    }
  }
}
