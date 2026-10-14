package com.invoicemind.ai.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TEST DOUBLE of the Frankfurter (ECB rates) API, never used outside tests. Publishes rates on business days
 * only, like the ECB. Fixed, easy-to-calculate values: before 2026-03-01 USD 1.10 / GBP 0.80 / CHF 0.95 per EUR,
 * from 2026-03-01 USD 1.20 / GBP 0.80 / CHF 0.95. There is deliberately no RUB, as in the real feed.
 */
public final class FakeFrankfurterForTests {
  private static final Pattern RANGE = Pattern.compile("/(\\d{4}-\\d{2}-\\d{2})\\.\\.(\\d{4}-\\d{2}-\\d{2})");
  private static final LocalDate USD_STEP = LocalDate.of(2026, 3, 1);
  private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
  private static final AtomicBoolean FAILING = new AtomicBoolean();
  private static final HttpServer SERVER = start();

  private FakeFrankfurterForTests() {}

  public static String baseUrl() { return "http://127.0.0.1:" + SERVER.getAddress().getPort(); }

  /** Requested ranges so far, e.g. "2026-01-10..2026-10-14". */
  public static List<String> requests() { return List.copyOf(REQUESTS); }

  public static void failing(boolean value) { FAILING.set(value); }

  public static String usdPerEur(LocalDate date) { return date.isBefore(USD_STEP) ? "1.1" : "1.2"; }

  private static HttpServer start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", FakeFrankfurterForTests::handle);
      server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2));
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException("Could not start the fake rate source", e);
    }
  }

  private static void handle(HttpExchange ex) throws IOException {
    try (ex) {
      Matcher m = RANGE.matcher(ex.getRequestURI().getPath());
      if (FAILING.get() || !m.matches()) {
        byte[] out = "{\"message\":\"not available\"}".getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(FAILING.get() ? 503 : 404, out.length);
        ex.getResponseBody().write(out);
        return;
      }
      REQUESTS.add(m.group(1) + ".." + m.group(2));
      LocalDate from = LocalDate.parse(m.group(1)), to = LocalDate.parse(m.group(2));
      List<String> days = new ArrayList<>();
      for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
        if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
        days.add("\"" + d + "\":{\"USD\":" + usdPerEur(d) + ",\"GBP\":0.8,\"CHF\":0.95}");
      }
      byte[] out = ("{\"amount\":1.0,\"base\":\"EUR\",\"start_date\":\"" + from + "\",\"end_date\":\"" + to
          + "\",\"rates\":{" + String.join(",", days) + "}}").getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().add("Content-Type", "application/json");
      ex.sendResponseHeaders(200, out.length);
      ex.getResponseBody().write(out);
    }
  }
}
