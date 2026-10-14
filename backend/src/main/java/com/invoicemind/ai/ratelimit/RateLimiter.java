package com.invoicemind.ai.ratelimit;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Fixed-window counter in Redis (INCR + EXPIRE). Fails open if Redis is down so an outage cannot lock users out. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiter {
  private final StringRedisTemplate redis;

  public void check(String bucket, String key, int limitPerMinute) {
    long minute = Instant.now().getEpochSecond() / 60;
    String redisKey = "rl:" + bucket + ":" + key + ":" + minute;
    try {
      Long count = redis.opsForValue().increment(redisKey);
      if (count != null && count == 1L) {
        redis.expire(redisKey, Duration.ofSeconds(90));
      }
      if (count != null && count > limitPerMinute) {
        int retryAfter = (int) (60 - (Instant.now().getEpochSecond() % 60));
        throw new RateLimitedException(Math.max(retryAfter, 1));
      }
    } catch (DataAccessException e) {
      log.warn("Rate limiter unavailable, allowing request: {}", e.getClass().getSimpleName());
    }
  }
}
