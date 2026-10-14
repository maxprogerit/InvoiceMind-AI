package com.invoicemind.ai.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicemind.ai.config.AppProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpAiClientTest {
  static final String OCR_JSON = """
      {"text":"ACME GmbH Invoice 1","pages":[{"page":1,"text":"ACME","confidence":0.9,"method":"ocr"}],
       "page_count":1,"confidence":0.9,"engine":"tesseract","mime_type":"application/pdf","unknown_field":true}""";
  static final String EXTRACT_JSON = """
      {"extraction":{"vendor":"ACME","invoice_number":"1","invoice_date":"2026-03-14","due_date":null,"currency":"EUR",
       "line_items":[{"description":"x","quantity":1,"unit_price":2.5,"total":2.5}],"subtotal":2.5,"tax":0.0,"total":2.5,
       "confidence":{"vendor":0.9,"invoice_number":0.9,"invoice_date":0.9,"due_date":0,"currency":0.9,"line_items":0.9,
       "subtotal":0.9,"tax":0.9,"total":0.9},"needs_review":false,"review_reasons":[]},
       "injection_suspected":false,"usage":{"model":"m","input_tokens":10,"output_tokens":5,"cost_usd":0.001,"latency_ms":7}}""";

  HttpServer server;
  final AtomicInteger calls = new AtomicInteger();
  final List<String> tokens = new CopyOnWriteArrayList<>();
  final List<String> bodies = new CopyOnWriteArrayList<>();
  final List<Integer> script = new ArrayList<>(); // status per call; the last entry repeats

  HttpAiClient client(int attempts) throws IOException {
    if (server == null) {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", ex -> {
        int n = calls.getAndIncrement();
        tokens.add(String.valueOf(ex.getRequestHeaders().getFirst("X-Internal-Token")));
        bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
        int status = script.get(Math.min(n, script.size() - 1));
        String body = status == 200 ? (ex.getRequestURI().getPath().equals("/ocr") ? OCR_JSON : EXTRACT_JSON)
            : status == 422 ? "{\"error\":{\"code\":\"unreadable_file\",\"message\":\"File could not be decoded\"}}"
            : "{\"error\":{\"code\":\"llm_error\",\"message\":\"upstream\"}}";
        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, out.length);
        ex.getResponseBody().write(out);
        ex.close();
      });
      server.start();
    }
    return clientFor("http://127.0.0.1:" + server.getAddress().getPort(), attempts);
  }

  static HttpAiClient clientFor(String baseUrl, int attempts) {
    var ai = new AppProperties.Ai(baseUrl, "secret-token", 500, 5000, 5000, attempts, 10, 100, 30);
    return new HttpAiClient(new AppProperties(null, null, null, null, ai, null, null, null), new ObjectMapper());
  }

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  @Test
  void sendsInternalTokenAndMapsSnakeCaseResponses() throws Exception {
    script.add(200);
    HttpAiClient c = client(3);

    var ocr = c.ocr("%PDF-1.4 data".getBytes(StandardCharsets.ISO_8859_1), "invoice.pdf");
    assertThat(ocr.text()).isEqualTo("ACME GmbH Invoice 1");
    assertThat(ocr.pageCount()).isEqualTo(1);
    assertThat(ocr.pages().get(0).method()).isEqualTo("ocr");
    assertThat(bodies.get(0)).contains("filename=\"invoice.pdf\"").contains("%PDF-1.4 data");

    var ex = c.extract("some text");
    assertThat(ex.extraction().invoiceNumber()).isEqualTo("1");
    assertThat(ex.extraction().total()).isEqualByComparingTo("2.5");
    assertThat(ex.extraction().lineItems().get(0).unitPrice()).isEqualByComparingTo("2.5");
    assertThat(ex.usage().inputTokens()).isEqualTo(10);
    assertThat(bodies.get(1)).contains("\"text\":\"some text\"");

    assertThat(tokens).containsOnly("secret-token");
  }

  @Test
  void retriesTransientFailuresWithBackoff() throws Exception {
    script.addAll(List.of(503, 502, 200));
    var ex = client(3).extract("text");
    assertThat(ex.extraction().vendor()).isEqualTo("ACME");
    assertThat(calls.get()).isEqualTo(3);
  }

  @Test
  void doesNotRetryClientErrorsAndKeepsTheServiceMessage() throws Exception {
    script.add(422);
    assertThatThrownBy(() -> client(3).ocr(new byte[]{1}, "x.pdf"))
        .isInstanceOfSatisfying(AiClientException.class, e -> {
          assertThat(e.code()).isEqualTo("unreadable_file");
          assertThat(e.getMessage()).isEqualTo("File could not be decoded");
          assertThat(e.retryable()).isFalse();
          assertThat(e.status()).isEqualTo(422);
        });
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void givesUpAfterTheConfiguredAttempts() throws Exception {
    script.add(503);
    assertThatThrownBy(() -> client(3).extract("t")).isInstanceOfSatisfying(AiClientException.class, e -> {
      assertThat(e.retryable()).isTrue();
      assertThat(e.status()).isEqualTo(503);
    });
    assertThat(calls.get()).isEqualTo(3);
  }

  @Test
  void unreachableServiceIsReportedAsRetryableUnavailable() {
    HttpAiClient c = clientFor("http://127.0.0.1:1", 2); // nothing listens on port 1
    assertThatThrownBy(() -> c.extract("t")).isInstanceOfSatisfying(AiClientException.class, e -> {
      assertThat(e.code()).isEqualTo("ai_unreachable");
      assertThat(e.retryable()).isTrue();
    });
  }

  @Test
  void circuitBreakerOpensAndFailsFastWithoutCallingTheService() throws Exception {
    script.add(503);
    HttpAiClient c = client(1); // one attempt per call so each call is exactly one recorded failure
    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> c.extract("t")).isInstanceOf(AiClientException.class);
    }
    int before = calls.get();
    assertThat(before).isEqualTo(5);

    assertThatThrownBy(() -> c.extract("t")).isInstanceOfSatisfying(AiClientException.class, e -> {
      assertThat(e.code()).isEqualTo("ai_circuit_open");
      assertThat(e.getMessage()).contains("temporarily unavailable");
    });
    assertThat(calls.get()).isEqualTo(before); // the open circuit never reached the server
  }
}
