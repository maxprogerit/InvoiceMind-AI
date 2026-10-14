package com.invoicemind.ai.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TEST DOUBLE of a customer's webhook receiver, never used outside tests. Every path is an independent endpoint:
 * it records what arrives and answers with a configurable behaviour.
 */
public final class FakeWebhookReceiverForTests {
  public record Received(String path, Map<String, String> headers, String body) {}

  private static final Map<String, List<Received>> RECEIVED = new ConcurrentHashMap<>();
  private static final Map<String, AtomicInteger> FAIL_FIRST = new ConcurrentHashMap<>();
  private static final Map<String, Integer> STATUS = new ConcurrentHashMap<>();
  private static final HttpServer SERVER = start();

  private FakeWebhookReceiverForTests() {}

  /** URL of an endpoint of its own; {@code name} keeps tests independent of each other. */
  public static String url(String name) { return "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/" + name; }

  public static List<Received> received(String name) { return RECEIVED.getOrDefault("/" + name, List.of()); }

  /** Answer the next {@code n} requests with HTTP 500, then behave normally. */
  public static void failFirst(String name, int n) { FAIL_FIRST.put("/" + name, new AtomicInteger(n)); }

  /** Always answer with this status (default 200). */
  public static void alwaysRespond(String name, int status) { STATUS.put("/" + name, status); }

  private static HttpServer start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", FakeWebhookReceiverForTests::handle);
      server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException("Could not start the fake webhook receiver", e);
    }
  }

  private static void handle(HttpExchange ex) throws IOException {
    try (ex) {
      String path = ex.getRequestURI().getPath();
      String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      Map<String, String> headers = new ConcurrentHashMap<>();
      ex.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
      RECEIVED.computeIfAbsent(path, k -> new CopyOnWriteArrayList<>()).add(new Received(path, headers, body));

      AtomicInteger remaining = FAIL_FIRST.get(path);
      int status = STATUS.getOrDefault(path, 200);
      if (remaining != null && remaining.getAndDecrement() > 0) status = 500;
      if (status == 302) ex.getResponseHeaders().add("Location", "http://127.0.0.1:1/elsewhere");
      ex.sendResponseHeaders(status, -1);
    }
  }
}
