package com.invoicemind.ai.ratelimit;

public class RateLimitedException extends RuntimeException {
  private final int retryAfterSeconds;

  public RateLimitedException(int retryAfterSeconds) {
    super("Too many requests, retry in " + retryAfterSeconds + "s");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public int retryAfterSeconds() { return retryAfterSeconds; }
}
