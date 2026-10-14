package com.invoicemind.ai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicemind.ai.ai.AiDtos.ExtractResponse;
import com.invoicemind.ai.ai.AiDtos.OcrResponse;
import com.invoicemind.ai.common.RequestIdFilter;
import com.invoicemind.ai.config.AppProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for the ai-service with timeouts, exponential-backoff retries on transient failures
 * and a circuit breaker: when the service is down, calls fail fast and the invoice becomes FAILED
 * (retryable by the user) instead of tying up consumer threads.
 */
@Slf4j
@Component
public class HttpAiClient implements AiClient {
  private final RestClient http;
  private final ObjectMapper mapper;
  private final String token;
  private final Retry retry;
  private final CircuitBreaker breaker;

  public HttpAiClient(AppProperties props, ObjectMapper mapper) {
    var ai = props.ai();
    this.mapper = mapper;
    this.token = ai.token();
    var client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofMillis(ai.connectTimeoutMs()))
        .build();
    var factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofMillis(Math.max(ai.ocrTimeoutMs(), ai.extractTimeoutMs())));
    this.http = RestClient.builder().baseUrl(ai.baseUrl()).requestFactory(factory).build();

    this.breaker = CircuitBreaker.of("ai-service", CircuitBreakerConfig.custom()
        .failureRateThreshold(ai.circuitFailureRate())
        .slidingWindowSize(10)
        .minimumNumberOfCalls(5)
        .waitDurationInOpenState(Duration.ofSeconds(ai.circuitOpenSeconds()))
        .recordException(HttpAiClient::isTransient)
        .build());
    this.retry = Retry.of("ai-service", RetryConfig.custom()
        .maxAttempts(ai.retryAttempts())
        .intervalFunction(IntervalFunction.ofExponentialBackoff(ai.retryInitialBackoffMs(), 2.0))
        .retryOnException(HttpAiClient::isTransient)
        .build());
    this.retry.getEventPublisher().onRetry(e -> log.warn("Retrying ai-service call (attempt {}): {}",
        e.getNumberOfRetryAttempts(), e.getLastThrowable().getMessage()));
  }

  @Override
  public OcrResponse ocr(byte[] data, String filename) {
    return call(() -> {
      MultipartBodyBuilder body = new MultipartBodyBuilder();
      body.part("file", new ByteArrayResource(data) {
        @Override
        public String getFilename() { return filename; }
      }).contentType(MediaType.APPLICATION_OCTET_STREAM);
      return http.post().uri("/ocr")
          .header("X-Internal-Token", token)
          .header(RequestIdFilter.HEADER, requestId())
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(body.build())
          .retrieve()
          .onStatus(HttpStatusCode::isError, (req, res) -> { throw toException(res.getStatusCode().value(), res.getBody().readAllBytes()); })
          .body(OcrResponse.class);
    });
  }

  @Override
  public ExtractResponse extract(String text) {
    return call(() -> http.post().uri("/extract")
        .header("X-Internal-Token", token)
        .header(RequestIdFilter.HEADER, requestId())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("text", text))
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> { throw toException(res.getStatusCode().value(), res.getBody().readAllBytes()); })
        .body(ExtractResponse.class));
  }

  private <T> T call(Supplier<T> supplier) {
    Supplier<T> guarded = Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(breaker, () -> {
      try {
        return supplier.get();
      } catch (ResourceAccessException e) { // connect/read timeouts, connection refused
        throw new AiClientException(0, "ai_unreachable", "The AI service could not be reached", true, e);
      }
    }));
    try {
      T result = guarded.get();
      if (result == null) throw new AiClientException(502, "empty_response", "The AI service returned an empty response", false, null);
      return result;
    } catch (CallNotPermittedException e) {
      throw new AiClientException(503, "ai_circuit_open",
          "The AI service is temporarily unavailable, please retry in a minute", false, e);
    }
  }

  private AiClientException toException(int status, byte[] body) {
    String code = "ai_error";
    String message = "The AI service returned HTTP " + status;
    try {
      JsonNode error = mapper.readTree(body).path("error");
      if (error.hasNonNull("code")) code = error.get("code").asText();
      if (error.hasNonNull("message")) message = error.get("message").asText();
    } catch (Exception ignored) {
      // body was not our JSON error envelope; keep the generic message
    }
    return new AiClientException(status, code, message, status == 502 || status == 503 || status == 504, null);
  }

  private static boolean isTransient(Throwable t) {
    return t instanceof AiClientException a && a.retryable();
  }

  private static String requestId() {
    String id = MDC.get(RequestIdFilter.MDC_KEY);
    return id != null && id.length() >= 8 ? id : "backend-" + System.nanoTime();
  }
}
