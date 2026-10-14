package com.invoicemind.ai.webhook;

import com.invoicemind.ai.common.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends pending deliveries. Rows are claimed with {@code FOR UPDATE SKIP LOCKED} and a lease, so several backend
 * instances can run it at once and a crashed one only delays its deliveries by the lease. Delivery is at-least-once:
 * receivers should treat the event id as an idempotency key. A 2xx answer is success; anything else (including
 * redirects, which are never followed) is retried with exponential backoff until {@code maxAttempts}.
 *
 * <p>Every request carries {@code X-InvoiceMind-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256(secret, t + "." + body)>}.
 */
@Slf4j
@Component
public class WebhookDispatcher {
  static final String SIGNATURE_HEADER = "X-InvoiceMind-Signature";
  private static final Duration LEASE = Duration.ofMinutes(2);
  private static final int BATCH = 20;

  private record Due(UUID id, UUID endpointId, String eventType, String payload, int attempts) {}

  private record Target(String url, String secret, boolean enabled) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final WebhookProperties props;
  private final HttpClient http;

  public WebhookDispatcher(NamedParameterJdbcTemplate jdbc, WebhookProperties props) {
    this.jdbc = jdbc;
    this.props = props;
    this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofMillis(props.timeoutMs())).build();
  }

  @Scheduled(fixedDelayString = "${app.webhooks.poll-ms:2000}", initialDelayString = "PT5S")
  public void deliverDue() {
    for (Due due : claim()) {
      try {
        deliver(due);
      } catch (RuntimeException e) {
        log.error("Webhook delivery {} crashed", due.id(), e);
        record(due, null, "Internal error while delivering");
      }
    }
  }

  @Scheduled(cron = "0 17 3 * * *")
  public void purgeOld() {
    int n = jdbc.update("delete from webhook_deliveries where created_at < now() - interval '30 days'",
        new MapSqlParameterSource());
    if (n > 0) log.info("Removed {} webhook deliveries older than 30 days", n);
  }

  /** One statement, therefore atomic without an explicit transaction. */
  List<Due> claim() {
    return jdbc.query("""
            with due as (
              select id from webhook_deliveries
              where status = 'PENDING' and next_attempt_at <= now()
              order by next_attempt_at limit :n for update skip locked)
            update webhook_deliveries d set next_attempt_at = now() + make_interval(secs => :lease)
            from due where d.id = due.id
            returning d.id, d.endpoint_id, d.event_type, d.payload::text as payload, d.attempts""",
        new MapSqlParameterSource().addValue("n", BATCH).addValue("lease", (double) LEASE.toSeconds()),
        (rs, i) -> new Due(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("endpoint_id")),
            rs.getString("event_type"), rs.getString("payload"), rs.getInt("attempts")));
  }

  private void deliver(Due due) {
    Target target = jdbc.query("select url, secret, enabled from webhook_endpoints where id = :id",
        new MapSqlParameterSource("id", due.endpointId()),
        rs -> rs.next() ? new Target(rs.getString("url"), rs.getString("secret"), rs.getBoolean("enabled")) : null);
    if (target == null || !target.enabled()) {
      finish(due, "FAILED", null, "The endpoint is disabled");
      return;
    }
    URI uri;
    try {
      uri = UrlGuard.check(target.url(), props.allowPrivateTargets()); // re-checked: DNS may have changed since creation
    } catch (ApiException e) {
      finish(due, "FAILED", null, e.getMessage());
      return;
    }

    byte[] body = due.payload().getBytes(StandardCharsets.UTF_8);
    long timestamp = Instant.now().getEpochSecond();
    HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(props.timeoutMs()))
        .header("Content-Type", "application/json")
        .header("User-Agent", "InvoiceMind-Webhooks/1")
        .header("X-InvoiceMind-Event", due.eventType())
        .header("X-InvoiceMind-Delivery", due.id().toString())
        .header(SIGNATURE_HEADER, "t=" + timestamp + ",v1=" + sign(target.secret(), timestamp, body))
        .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
    try {
      int code = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
      if (code >= 200 && code < 300) finish(due, "DELIVERED", code, null);
      else record(due, code, "The endpoint answered with HTTP " + code);
    } catch (IOException e) {
      record(due, null, "Request failed: " + e.getClass().getSimpleName());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      record(due, null, "Interrupted");
    }
  }

  /** A failed attempt: retry later, or give up after the last attempt. */
  private void record(Due due, Integer code, String error) {
    int attempts = due.attempts() + 1;
    if (attempts >= props.maxAttempts()) {
      jdbc.update("""
          update webhook_deliveries set status = 'FAILED', attempts = :a, last_status_code = :code, last_error = :err
          where id = :id""", params(due, attempts, code, error));
      log.warn("Webhook delivery {} failed for good after {} attempts: {}", due.id(), attempts, error);
      return;
    }
    long delay = Math.min(3600L, props.retryBaseSeconds() * (1L << Math.min(attempts - 1, 20)));
    jdbc.update("""
        update webhook_deliveries set attempts = :a, last_status_code = :code, last_error = :err,
          next_attempt_at = now() + make_interval(secs => :delay)
        where id = :id""", params(due, attempts, code, error).addValue("delay", (double) delay));
  }

  private void finish(Due due, String status, Integer code, String error) {
    jdbc.update("""
        update webhook_deliveries set status = :status, attempts = :a, last_status_code = :code, last_error = :err,
          delivered_at = case when :status = 'DELIVERED' then now() else delivered_at end
        where id = :id""", params(due, due.attempts() + 1, code, error).addValue("status", status));
  }

  private static MapSqlParameterSource params(Due due, int attempts, Integer code, String error) {
    return new MapSqlParameterSource().addValue("id", due.id()).addValue("a", attempts).addValue("code", code)
        .addValue("err", error == null ? null : error.length() > 300 ? error.substring(0, 300) : error);
  }

  /** Hex HMAC-SHA256 over {@code "<timestamp>.<body>"}; the timestamp lets receivers reject replays. */
  static String sign(String secret, long timestamp, byte[] body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(mac.doFinal(body));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
    }
  }
}
