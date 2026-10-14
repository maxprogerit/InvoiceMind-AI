package com.invoicemind.ai.ai;

/** Failure talking to the ai-service. The message is safe to show to end users. */
public class AiClientException extends RuntimeException {
  private final int status;
  private final String code;
  private final boolean retryable;

  public AiClientException(int status, String code, String message, boolean retryable, Throwable cause) {
    super(message, cause);
    this.status = status;
    this.code = code;
    this.retryable = retryable;
  }

  public int status() { return status; }
  public String code() { return code; }
  public boolean retryable() { return retryable; }
}
