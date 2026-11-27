package com.invoicemind.ai.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicemind.ai.config.AppProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The Java client of the extract-service: wire mapping and failure classification (no retries in here). */
class HttpAiClientTest {
  static final String INVOICE_JSON = """
      {"source":"text_layer",
       "ocr":{"text":"ACME GmbH Invoice 1","pages":[{"page":1,"text":"ACME","confidence":1.0,"method":"text-layer"}],
              "page_count":1,"confidence":1.0,"engine":"tesseract","mime_type":"application/pdf"},
       "extraction":{"document_type":"invoice","vendor":"ACME","invoice_number":"1","invoice_date":"2026-03-14",
         "due_date":null,"currency":"EUR",
         "line_items":[{"description":"x","quantity":"3","unit_price":"0.10","total":"0.30"}],
         "subtotal":"0.30","tax":"0.00","total":"0.30",
         "confidence":{"vendor":0.9,"invoice_number":0.9,"invoice_date":0.9,"due_date":0,"currency":0.9,
           "line_items":0.9,"subtotal":0.9,"tax":0.9,"total":0.9},"needs_review":false,"review_reasons":[]},
       "validation":{"status":"needs_review","issues":[{"code":"missing_optional_field","severity":"warning",
         "field":"due_date","message":"Field 'due_date' was not found in the document"}]},
       "injection_suspected":false,
       "usage":{"model":"m","input_tokens":10,"output_tokens":5,"cost_usd":"0.000123","latency_ms":7},
       "unknown_field":true}""";

  HttpServer server;
  final AtomicInteger calls = new AtomicInteger();
  final List<String> tokens = new CopyOnWriteArrayList<>();
  final List<String> bodies = new CopyOnWriteArrayList<>();

  HttpAiClient serving(int status, String body) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", ex -> {
      calls.incrementAndGet();
      tokens.add(String.valueOf(ex.getRequestHeaders().getFirst("X-Internal-Token")));
      bodies.add(ex.getRequestURI().getPath() + " " + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
      byte[] out = body.getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().add("Content-Type", "application/json");
      ex.sendResponseHeaders(status, out.length);
      ex.getResponseBody().write(out);
      ex.close();
    });
    server.start();
    return clientFor("http://127.0.0.1:" + server.getAddress().getPort());
  }

  static HttpAiClient clientFor(String baseUrl) {
    var ai = new AppProperties.Ai(baseUrl, "secret-token", 500, 5000);
    return new HttpAiClient(new AppProperties(null, null, null, null, ai, null, null, null, null), new ObjectMapper(),
        org.springframework.web.client.RestClient.builder());
  }

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  @Test
  void postsTheFileWithTheTokenAndMapsTheResultExactly() throws Exception {
    var result = serving(200, INVOICE_JSON).processInvoice("%PDF-1.4 data".getBytes(StandardCharsets.ISO_8859_1), "invoice.pdf");

    assertThat(bodies.get(0)).startsWith("/invoices ").contains("filename=\"invoice.pdf\"").contains("%PDF-1.4 data");
    assertThat(tokens).containsOnly("secret-token");
    assertThat(result.source()).isEqualTo("text_layer");
    assertThat(result.ocr().pageCount()).isEqualTo(1);
    // decimal strings become exact BigDecimals: 3 x 0.10 is 0.30, not 0.30000000000000004
    var line = result.extraction().lineItems().get(0);
    assertThat(line.quantity().multiply(line.unitPrice())).isEqualByComparingTo(line.total());
    assertThat(result.extraction().total().toPlainString()).isEqualTo("0.30");
    assertThat(result.usage().costUsd().toPlainString()).isEqualTo("0.000123");
    assertThat(result.validation().status()).isEqualTo("needs_review");
    assertThat(result.validation().issues()).singleElement()
        .satisfies(i -> assertThat(i.code()).isEqualTo("missing_optional_field"));
  }

  @ParameterizedTest(name = "HTTP {0} {1} -> retryable={2}")
  @CsvSource({
      "502, llm_unreachable,    true",
      "503, llm_rate_limited,   true",
      "504, llm_timeout,        true",
      "500, internal_error,     true",
      "429, too_many_requests,  true",
      "503, llm_not_configured, false",   // no key: a person must fix the configuration
      "503, llm_misconfigured,  false",
      "422, model_refused,      false",
      "422, unreadable_file,    false",
      "415, unsupported_type,   false",
      "413, too_many_pages,     false",
      "401, unauthorized,       false",
  })
  void classifiesFailuresAndCallsExactlyOnce(int status, String code, boolean retryable) throws Exception {
    HttpAiClient c = serving(status, "{\"error\":{\"code\":\"" + code + "\",\"message\":\"msg " + code + "\"}}");
    assertThatThrownBy(() -> c.processInvoice(new byte[]{1}, "x.pdf")).isInstanceOfSatisfying(AiClientException.class, e -> {
      assertThat(e.status()).isEqualTo(status);
      assertThat(e.code()).isEqualTo(code);
      assertThat(e.getMessage()).isEqualTo("msg " + code);
      assertThat(e.retryable()).isEqualTo(retryable);
    });
    assertThat(calls.get()).isEqualTo(1); // retries are the job queue's job, never the client's
  }

  @Test
  void aNonJsonErrorBodyStillGivesAClassifiedError() throws Exception {
    HttpAiClient c = serving(502, "<html>Bad Gateway</html>");
    assertThatThrownBy(() -> c.processInvoice(new byte[]{1}, "x.pdf")).isInstanceOfSatisfying(AiClientException.class, e -> {
      assertThat(e.code()).isEqualTo("ai_error");
      assertThat(e.retryable()).isTrue();
    });
  }

  @Test
  void unreachableServiceIsRetryable() {
    HttpAiClient c = clientFor("http://127.0.0.1:1"); // nothing listens on port 1
    assertThatThrownBy(() -> c.processInvoice(new byte[]{1}, "x.pdf")).isInstanceOfSatisfying(AiClientException.class, e -> {
      assertThat(e.code()).isEqualTo("ai_unreachable");
      assertThat(e.retryable()).isTrue();
    });
  }
}
