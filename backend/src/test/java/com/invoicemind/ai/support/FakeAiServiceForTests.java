package com.invoicemind.ai.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TEST DOUBLE of the ai-service (never used outside tests). Behaviour is selected per request by a
 * marker embedded in the uploaded "document": {@code SCENARIO:<id>}. /ocr echoes the marker back in
 * its text and /extract finds it again, so scenarios are independent and safe to run in parallel.
 */
public final class FakeAiServiceForTests {
  public record Reply(int status, String body) {
    public static Reply json(int status, Object body) {
      try {
        return new Reply(status, new ObjectMapper().writeValueAsString(body));
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static final Pattern MARKER = Pattern.compile("SCENARIO:([a-z0-9-]+)");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Map<String, Reply> OCR = new ConcurrentHashMap<>();
  private static final Map<String, Reply> EXTRACT = new ConcurrentHashMap<>();
  private static final Map<String, AtomicInteger> OCR_CALLS = new ConcurrentHashMap<>();
  private static final Map<String, AtomicInteger> EXTRACT_CALLS = new ConcurrentHashMap<>();
  private static final Map<String, String> EXTRACT_REQUEST_TEXT = new ConcurrentHashMap<>();
  private static final java.util.concurrent.atomic.AtomicReference<String> LAST_EXPLAIN = new java.util.concurrent.atomic.AtomicReference<>();
  private static final HttpServer SERVER = start();

  private FakeAiServiceForTests() {}

  public static String baseUrl() { return "http://127.0.0.1:" + SERVER.getAddress().getPort(); }

  public static void scenario(String id, Reply ocr, Reply extract) {
    OCR.put(id, ocr);
    EXTRACT.put(id, extract);
  }

  public static int ocrCalls(String id) { return OCR_CALLS.computeIfAbsent(id, k -> new AtomicInteger()).get(); }

  public static int extractCalls(String id) { return EXTRACT_CALLS.computeIfAbsent(id, k -> new AtomicInteger()).get(); }

  public static String textSentToExtract(String id) { return EXTRACT_REQUEST_TEXT.get(id); }

  /** Raw JSON body of the most recent /explain request (to assert what the backend sends the model). */
  public static String lastExplainRequest() { return LAST_EXPLAIN.get(); }

  public static Reply okOcr(String id) {
    String text = "ACME GmbH\nInvoice INV-100\nTotal EUR 1190.00\n" + "SCENARIO:" + id;
    return Reply.json(200, Map.of("text", text,
        "pages", List.of(Map.of("page", 1, "text", text, "confidence", 0.97, "method", "text-layer")),
        "page_count", 1, "confidence", 0.97, "engine", "tesseract", "mime_type", "application/pdf"));
  }

  /** ExtractResponse in the ai-service's snake_case wire format. */
  public static Reply okExtraction(String vendor, String number, String date, String due, String currency,
                                   String subtotal, String tax, String total, List<Map<String, Object>> lines,
                                   boolean needsReview, boolean injection) {
    return okExtraction("invoice", vendor, number, date, due, currency, subtotal, tax, total, lines, needsReview, injection);
  }

  public static Reply okExtraction(String documentType, String vendor, String number, String date, String due, String currency,
                                   String subtotal, String tax, String total, List<Map<String, Object>> lines,
                                   boolean needsReview, boolean injection) {
    Map<String, Object> confidence = new LinkedHashMap<>();
    for (String f : List.of("vendor", "invoice_number", "invoice_date", "due_date", "currency", "line_items",
        "subtotal", "tax", "total")) confidence.put(f, 0.95);
    Map<String, Object> extraction = new LinkedHashMap<>();
    extraction.put("document_type", documentType);
    extraction.put("vendor", vendor);
    extraction.put("invoice_number", number);
    extraction.put("invoice_date", date);
    extraction.put("due_date", due);
    extraction.put("currency", currency);
    extraction.put("line_items", lines);
    extraction.put("subtotal", subtotal == null ? null : new java.math.BigDecimal(subtotal));
    extraction.put("tax", tax == null ? null : new java.math.BigDecimal(tax));
    extraction.put("total", total == null ? null : new java.math.BigDecimal(total));
    extraction.put("confidence", confidence);
    extraction.put("needs_review", needsReview);
    extraction.put("review_reasons", needsReview ? List.of("flagged by the model") : List.of());
    return Reply.json(200, Map.of("extraction", extraction, "injection_suspected", injection,
        "usage", Map.of("model", "test-model", "input_tokens", 1200, "output_tokens", 300, "cost_usd", 0.00027,
            "latency_ms", 5)));
  }

  public static Map<String, Object> line(String description, String qty, String unit, String total) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("description", description);
    m.put("quantity", new java.math.BigDecimal(qty));
    m.put("unit_price", new java.math.BigDecimal(unit));
    m.put("total", new java.math.BigDecimal(total));
    return m;
  }

  // ------------------------------------------------------------------ server
  private static HttpServer start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/ocr", ex -> handle(ex, true));
      server.createContext("/extract", ex -> handle(ex, false));
      server.createContext("/embed", ex -> knowledge(ex, true));
      server.createContext("/explain", ex -> knowledge(ex, false));
      server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException("Could not start the fake ai-service", e);
    }
  }

  private static void handle(HttpExchange ex, boolean ocr) throws IOException {
    try (ex) {
      byte[] raw = ex.getRequestBody().readAllBytes();
      String body = new String(raw, StandardCharsets.ISO_8859_1);
      Reply reply;
      if (!"test-token".equals(ex.getRequestHeaders().getFirst("X-Internal-Token"))) {
        reply = Reply.json(401, Map.of("error", Map.of("code", "unauthorized", "message", "Missing or invalid internal token")));
      } else {
        Matcher m = MARKER.matcher(ocr ? body : jsonText(raw));
        String id = m.find() ? m.group(1) : "none";
        if (ocr) {
          OCR_CALLS.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
          reply = OCR.getOrDefault(id, Reply.json(404, Map.of("error", Map.of("code", "no_scenario", "message", id))));
        } else {
          EXTRACT_CALLS.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
          EXTRACT_REQUEST_TEXT.put(id, jsonText(raw));
          reply = EXTRACT.getOrDefault(id, Reply.json(404, Map.of("error", Map.of("code", "no_scenario", "message", id))));
        }
      }
      byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().add("Content-Type", "application/json");
      ex.sendResponseHeaders(reply.status(), out.length);
      ex.getResponseBody().write(out);
    }
  }

  /** Deterministic bag-of-words embedding (feature hashing, 1536 dims, L2-normalised): similar wording -> similar vectors. */
  static List<Double> hashEmbedding(String text) {
    double[] v = new double[1536];
    for (String token : text.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
      if (token.length() >= 3) v[Math.floorMod(token.hashCode(), v.length)] += 1;
    }
    double norm = Math.sqrt(java.util.Arrays.stream(v).map(x -> x * x).sum());
    java.util.ArrayList<Double> out = new java.util.ArrayList<>(v.length);
    for (double x : v) out.add(norm == 0 ? 0.0 : x / norm);
    return out;
  }

  private static void knowledge(HttpExchange ex, boolean embed) throws IOException {
    try (ex) {
      byte[] raw = ex.getRequestBody().readAllBytes();
      Reply reply;
      if (!"test-token".equals(ex.getRequestHeaders().getFirst("X-Internal-Token"))) {
        reply = Reply.json(401, Map.of("error", Map.of("code", "unauthorized", "message", "Missing or invalid internal token")));
      } else if (embed) {
        var texts = MAPPER.readTree(raw).path("texts");
        List<List<Double>> vectors = new java.util.ArrayList<>();
        texts.forEach(t -> vectors.add(hashEmbedding(t.asText())));
        reply = Reply.json(200, Map.of("vectors", vectors, "model", "test-embedding", "dimensions", 1536,
            "input_tokens", 5 * vectors.size(), "cost_usd", 0.0));
      } else {
        LAST_EXPLAIN.set(new String(raw, StandardCharsets.UTF_8));
        var passages = MAPPER.readTree(raw).path("passages");
        boolean none = passages.isEmpty();
        String answer = none ? "No company rule covers this."
            : "Per rule [" + passages.get(0).path("id").asInt() + "]: " + passages.get(0).path("text").asText().substring(0, Math.min(100, passages.get(0).path("text").asText().length()));
        reply = Reply.json(200, Map.of("answer", answer, "citations", none ? List.of() : List.of(passages.get(0).path("id").asInt()),
            "insufficient_context", none, "injection_suspected", false,
            "usage", Map.of("model", "test-model", "input_tokens", 300, "output_tokens", 60, "cost_usd", 0.0, "latency_ms", 3)));
      }
      byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().add("Content-Type", "application/json");
      ex.sendResponseHeaders(reply.status(), out.length);
      ex.getResponseBody().write(out);
    }
  }

  private static String jsonText(byte[] raw) {
    try {
      return MAPPER.readTree(raw).path("text").asText("");
    } catch (IOException e) {
      return "";
    }
  }
}
