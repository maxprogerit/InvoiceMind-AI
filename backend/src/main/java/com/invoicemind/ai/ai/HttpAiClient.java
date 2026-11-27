package com.invoicemind.ai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicemind.ai.ai.AiDtos.EmbedResponse;
import com.invoicemind.ai.ai.AiDtos.ExplainRequest;
import com.invoicemind.ai.ai.AiDtos.ExplainResponse;
import com.invoicemind.ai.ai.AiDtos.InvoiceResult;
import com.invoicemind.ai.ai.AiDtos.OcrResponse;
import com.invoicemind.ai.common.RequestIdFilter;
import com.invoicemind.ai.config.AppProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
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
 * HTTP client for the internal extract-service. One attempt per call: retries belong to the job queue, which
 * persists them with backoff across restarts (see {@code ProcessingWorker}). Each failure is classified here:
 * <ul>
 *   <li>timeouts, connection errors, 5xx and 429: {@link AiClientException#retryable() retryable};</li>
 *   <li>4xx (bad file, unreadable document, too many pages ...): permanent, retrying cannot help;</li>
 *   <li>5xx that signal misconfiguration ({@link #PERMANENT_CODES}): permanent as well.</li>
 * </ul>
 */
@Component
public class HttpAiClient implements AiClient {
  /** 5xx responses that retrying cannot fix: they need a person to change the configuration. */
  static final Set<String> PERMANENT_CODES = Set.of("llm_not_configured", "llm_misconfigured", "model_refused", "unauthorized");

  private final RestClient http;
  private final ObjectMapper mapper;
  private final String token;

  /** The builder must be the injected one: it carries the observation (tracing) customizer, so calls propagate the trace. */
  public HttpAiClient(AppProperties props, ObjectMapper mapper, RestClient.Builder restClientBuilder) {
    var ai = props.ai();
    this.mapper = mapper;
    this.token = ai.token();
    var client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofMillis(ai.connectTimeoutMs()))
        .build();
    var factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofMillis(ai.readTimeoutMs()));
    this.http = restClientBuilder.baseUrl(ai.baseUrl()).requestFactory(factory).build();
  }

  @Override
  public InvoiceResult processInvoice(byte[] data, String filename) {
    return call(() -> http.post().uri("/invoices")
        .headers(h -> { h.set("X-Internal-Token", token); h.set(RequestIdFilter.HEADER, requestId()); })
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(file(data, filename))
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> { throw toException(res.getStatusCode().value(), res.getBody().readAllBytes()); })
        .body(InvoiceResult.class));
  }

  @Override
  public OcrResponse ocr(byte[] data, String filename) {
    return call(() -> http.post().uri("/ocr")
        .headers(h -> { h.set("X-Internal-Token", token); h.set(RequestIdFilter.HEADER, requestId()); })
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(file(data, filename))
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> { throw toException(res.getStatusCode().value(), res.getBody().readAllBytes()); })
        .body(OcrResponse.class));
  }

  @Override
  public EmbedResponse embed(List<String> texts) {
    return call(() -> http.post().uri("/embed")
        .headers(h -> { h.set("X-Internal-Token", token); h.set(RequestIdFilter.HEADER, requestId()); })
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("texts", texts))
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> { throw toException(res.getStatusCode().value(), res.getBody().readAllBytes()); })
        .body(EmbedResponse.class));
  }

  @Override
  public ExplainResponse explain(ExplainRequest request) {
    return call(() -> http.post().uri("/explain")
        .headers(h -> { h.set("X-Internal-Token", token); h.set(RequestIdFilter.HEADER, requestId()); })
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> { throw toException(res.getStatusCode().value(), res.getBody().readAllBytes()); })
        .body(ExplainResponse.class));
  }

  private static Object file(byte[] data, String filename) {
    MultipartBodyBuilder body = new MultipartBodyBuilder();
    body.part("file", new ByteArrayResource(data) {
      @Override
      public String getFilename() { return filename; }
    }).contentType(MediaType.APPLICATION_OCTET_STREAM);
    return body.build();
  }

  private <T> T call(Supplier<T> supplier) {
    T result;
    try {
      result = supplier.get();
    } catch (ResourceAccessException e) { // connect/read timeouts, connection refused
      throw new AiClientException(0, "ai_unreachable", "The extraction service could not be reached", true, e);
    }
    if (result == null) {
      throw new AiClientException(502, "empty_response", "The extraction service returned an empty response", true, null);
    }
    return result;
  }

  AiClientException toException(int status, byte[] body) {
    String code = "ai_error";
    String message = "The extraction service returned HTTP " + status;
    try {
      JsonNode error = mapper.readTree(body).path("error");
      if (error.hasNonNull("code")) code = error.get("code").asText();
      if (error.hasNonNull("message")) message = error.get("message").asText();
    } catch (Exception ignored) {
      // body was not our JSON error envelope; keep the generic message
    }
    boolean transientStatus = status >= 500 || status == 429;
    return new AiClientException(status, code, message, transientStatus && !PERMANENT_CODES.contains(code), null);
  }

  private static String requestId() {
    String id = MDC.get(RequestIdFilter.MDC_KEY);
    return id != null && id.length() >= 8 ? id : "backend-" + System.nanoTime();
  }
}
