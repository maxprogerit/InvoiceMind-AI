package com.invoicemind.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @Valid Jwt jwt,
    Cookies cookies,
    Cors cors,
    Storage storage,
    @Valid Ai ai,
    Processing processing,
    RateLimit rateLimit,
    Seed seed) {

  public record Jwt(@NotBlank @Size(min = 32, message = "APP_JWT_SECRET must be at least 32 characters") String secret,
                    long accessTtlSeconds, long refreshTtlSeconds) {}

  public record Cookies(boolean secure) {}

  public record Cors(List<String> allowedOrigins) {}

  public record Storage(String root, long maxFileBytes) {}

  public record Ai(@NotBlank String baseUrl, @NotBlank String token, int connectTimeoutMs, int ocrTimeoutMs,
                   int extractTimeoutMs, int retryAttempts, long retryInitialBackoffMs,
                   int circuitFailureRate, int circuitOpenSeconds) {}

  public record Processing(String topic, int stuckAfterMinutes, int requeueUploadedAfterMinutes) {}

  public record RateLimit(int authPerMinute, int uploadPerMinute) {}

  public record Seed(String demoPassword) {}
}
