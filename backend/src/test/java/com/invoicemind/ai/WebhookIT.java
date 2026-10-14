package com.invoicemind.ai;

import static com.invoicemind.ai.support.FakeAiServiceForTests.line;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.FakeWebhookReceiverForTests;
import com.invoicemind.ai.support.FakeWebhookReceiverForTests.Received;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Outbound webhooks end to end: a real approval in the pipeline writes outbox rows, the dispatcher sends signed
 * requests to a receiver double, retries failures and keeps a delivery log.
 */
class WebhookIT extends AbstractIntegrationTest {

  private static String name() { return "hook-" + UUID.randomUUID().toString().substring(0, 8); }

  private JsonNode register(Session admin, String receiver, List<String> events) {
    Resp r = api.post("/api/v1/webhooks", admin.token(),
        Map.of("url", FakeWebhookReceiverForTests.url(receiver), "events", events, "description", "ERP " + receiver));
    assertThat(r.status()).as(r.text()).isEqualTo(201);
    return r.json();
  }

  private UUID validatedInvoice(Session who, String total) {
    String id = "wh-" + UUID.randomUUID().toString().substring(0, 8);
    FakeAiServiceForTests.scenario(id, FakeAiServiceForTests.okOcr(id), FakeAiServiceForTests.okExtraction(
        "Hook Vendor GmbH", "INV-" + id, "2026-03-14", "2026-04-13", "EUR", total, "0.00", total,
        List.of(line("Services", "1", total, total)), false, false));
    UUID invoice = upload(who, id);
    awaitStatus(who, invoice, "VALIDATED");
    return invoice;
  }

  private List<Received> awaitReceived(String receiver, int count) {
    await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
        .until(() -> FakeWebhookReceiverForTests.received(receiver).size() >= count);
    return FakeWebhookReceiverForTests.received(receiver);
  }

  private JsonNode deliveries(Session admin, String endpointId) {
    return api.get("/api/v1/webhooks/" + endpointId + "/deliveries", admin.token()).json();
  }

  private JsonNode awaitDelivery(Session admin, String endpointId, String status) {
    JsonNode[] last = new JsonNode[1];
    await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(150)).until(() -> {
      for (JsonNode d : deliveries(admin, endpointId)) {
        if (d.get("status").asText().equals(status)) {
          last[0] = d;
          return true;
        }
      }
      return false;
    });
    return last[0];
  }

  private static String hmac(String secret, String timestamp, String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void approvalDeliversASignedEventWithTheInvoice() throws Exception {
    Session admin = api.registerOrg("Hook Inc");
    Session approver = api.createUser(admin, "APPROVER");
    Session user = api.createUser(admin, "USER");
    String receiver = name();
    JsonNode created = register(admin, receiver, List.of("invoice.approved", "invoice.rejected"));
    String secret = created.get("secret").asText();
    assertThat(secret).startsWith("whsec_").hasSizeGreaterThan(30);

    UUID invoice = validatedInvoice(user, "1190.00");
    assertThat(FakeWebhookReceiverForTests.received(receiver)).isEmpty(); // validated is not subscribed
    assertThat(api.post("/api/v1/invoices/" + invoice + "/approve", approver.token(), Map.of("comment", "ok")).status()).isEqualTo(200);

    Received hit = awaitReceived(receiver, 1).get(0);
    assertThat(hit.headers()).containsEntry("x-invoicemind-event", "invoice.approved")
        .containsEntry("content-type", "application/json").containsKey("x-invoicemind-delivery");
    assertThat(hit.headers().get("user-agent")).startsWith("InvoiceMind-Webhooks");

    // the signature is verifiable with the secret shown at creation, and covers timestamp and body
    String header = hit.headers().get("x-invoicemind-signature");
    String timestamp = header.split(",")[0].substring(2);
    String signature = header.split(",")[1].substring(3);
    assertThat(signature).isEqualTo(hmac(secret, timestamp, hit.body()));
    assertThat(signature).isNotEqualTo(hmac("whsec_wrong", timestamp, hit.body()));
    assertThat(Long.parseLong(timestamp)).isBetween(System.currentTimeMillis() / 1000 - 60, System.currentTimeMillis() / 1000 + 5);

    JsonNode event = mapper.readTree(hit.body());
    assertThat(event.get("type").asText()).isEqualTo("invoice.approved");
    assertThat(event.get("organizationId").asText()).isEqualTo(admin.organizationId().toString());
    assertThat(event.get("id").asText()).isNotBlank();
    JsonNode inv = event.at("/data/invoice");
    assertThat(inv.get("id").asText()).isEqualTo(invoice.toString());
    assertThat(inv.get("status").asText()).isEqualTo("APPROVED");
    assertThat(inv.get("documentType").asText()).isEqualTo("INVOICE");
    assertThat(inv.get("vendor").asText()).isEqualTo("Hook Vendor GmbH");
    assertThat(inv.get("currency").asText()).isEqualTo("EUR");
    assertThat(inv.get("total").decimalValue()).isEqualByComparingTo("1190.00");
    assertThat(inv.get("decisionComment").asText()).isEqualTo("ok");
    assertThat(inv.get("lineItems")).hasSize(1);
    assertThat(inv.has("storageKey") || inv.has("ocrText") || inv.has("uploadedBy")).isFalse(); // nothing internal leaks

    // the secret is never listed again, and the delivery log records the success
    assertThat(api.get("/api/v1/webhooks", admin.token()).text()).doesNotContain(secret).doesNotContain("secret");
    JsonNode delivered = awaitDelivery(admin, created.at("/endpoint/id").asText(), "DELIVERED");
    assertThat(delivered.get("attempts").asInt()).isEqualTo(1);
    assertThat(delivered.get("lastStatusCode").asInt()).isEqualTo(200);
    assertThat(delivered.get("eventType").asText()).isEqualTo("invoice.approved");
  }

  @Test
  void subscriptionsDisabledEndpointsAndOtherTenantsAreRespected() {
    Session admin = api.registerOrg("Hook Filter Inc");
    Session approver = api.createUser(admin, "APPROVER");
    Session stranger = api.registerOrg("Hook Stranger Inc");

    String validatedOnly = name(), disabled = name(), others = name();
    register(admin, validatedOnly, List.of("invoice.validated"));
    JsonNode off = register(admin, disabled, List.of("invoice.validated", "invoice.approved"));
    register(stranger, others, List.of("invoice.validated", "invoice.approved"));
    assertThat(api.patch("/api/v1/webhooks/" + off.at("/endpoint/id").asText(), admin.token(), Map.of("enabled", false)).status())
        .isEqualTo(200);

    UUID invoice = validatedInvoice(admin, "10.00");
    awaitReceived(validatedOnly, 1);
    assertThat(FakeWebhookReceiverForTests.received(validatedOnly).get(0).headers())
        .containsEntry("x-invoicemind-event", "invoice.validated");
    api.post("/api/v1/invoices/" + invoice + "/approve", approver.token(), Map.of());

    // give the dispatcher time to (wrongly) send anything else
    await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until(() -> true);
    assertThat(FakeWebhookReceiverForTests.received(validatedOnly)).hasSize(1); // approved is not subscribed
    assertThat(FakeWebhookReceiverForTests.received(disabled)).isEmpty();
    assertThat(FakeWebhookReceiverForTests.received(others)).isEmpty();         // another organisation's events
  }

  @Test
  void failuresAreRetriedWithBackoffAndEventuallyGiveUp() {
    Session admin = api.registerOrg("Hook Retry Inc");
    Session approver = api.createUser(admin, "APPROVER");

    // flaky: two failures, then success
    String flaky = name();
    FakeWebhookReceiverForTests.failFirst(flaky, 2);
    JsonNode flakyEndpoint = register(admin, flaky, List.of("invoice.approved"));
    // broken: always 500
    String broken = name();
    FakeWebhookReceiverForTests.alwaysRespond(broken, 500);
    JsonNode brokenEndpoint = register(admin, broken, List.of("invoice.approved"));
    // redirecting: a 302 must be treated as a failure and never followed
    String redirecting = name();
    FakeWebhookReceiverForTests.alwaysRespond(redirecting, 302);
    JsonNode redirectEndpoint = register(admin, redirecting, List.of("invoice.approved"));

    UUID invoice = validatedInvoice(admin, "20.00");
    api.post("/api/v1/invoices/" + invoice + "/approve", approver.token(), Map.of());

    JsonNode ok = awaitDelivery(admin, flakyEndpoint.at("/endpoint/id").asText(), "DELIVERED");
    assertThat(ok.get("attempts").asInt()).isEqualTo(3);
    assertThat(FakeWebhookReceiverForTests.received(flaky)).hasSize(3);
    // the event id is the idempotency key: identical on every attempt
    assertThat(FakeWebhookReceiverForTests.received(flaky).stream().map(r -> r.headers().get("x-invoicemind-delivery")).distinct()).hasSize(1);

    JsonNode failed = awaitDelivery(admin, brokenEndpoint.at("/endpoint/id").asText(), "FAILED");
    assertThat(failed.get("attempts").asInt()).isEqualTo(4); // app.webhooks.max-attempts in the test profile
    assertThat(failed.get("lastStatusCode").asInt()).isEqualTo(500);
    assertThat(failed.get("lastError").asText()).contains("HTTP 500");

    JsonNode redirected = awaitDelivery(admin, redirectEndpoint.at("/endpoint/id").asText(), "FAILED");
    assertThat(redirected.get("lastStatusCode").asInt()).isEqualTo(302);
    assertThat(FakeWebhookReceiverForTests.received(redirecting)).hasSize(4); // retried, but the Location was not followed

    // a failed delivery can be retried by hand once the receiver is fixed
    FakeWebhookReceiverForTests.alwaysRespond(broken, 200);
    String endpointId = brokenEndpoint.at("/endpoint/id").asText();
    Resp retry = api.post("/api/v1/webhooks/" + endpointId + "/deliveries/" + failed.get("id").asText() + "/retry", admin.token(), null);
    assertThat(retry.status()).isEqualTo(200);
    JsonNode healed = awaitDelivery(admin, endpointId, "DELIVERED");
    assertThat(healed.get("id").asText()).isEqualTo(failed.get("id").asText());
  }

  @Test
  void aTestPingReachesTheEndpoint() throws Exception {
    Session admin = api.registerOrg("Hook Ping Inc");
    String receiver = name();
    JsonNode created = register(admin, receiver, List.of("invoice.approved"));
    String id = created.at("/endpoint/id").asText();

    Resp ping = api.post("/api/v1/webhooks/" + id + "/test", admin.token(), null);
    assertThat(ping.status()).isEqualTo(202);
    Received hit = awaitReceived(receiver, 1).get(0);
    assertThat(hit.headers()).containsEntry("x-invoicemind-event", "ping");
    assertThat(mapper.readTree(hit.body()).get("type").asText()).isEqualTo("ping");
    awaitDelivery(admin, id, "DELIVERED");
  }

  @Test
  void managementIsValidatedAdminOnlyAndTenantScoped() {
    Session admin = api.registerOrg("Hook Rules Inc");
    Session approver = api.createUser(admin, "APPROVER");
    Session stranger = api.registerOrg("Hook Rules Stranger Inc");
    String url = FakeWebhookReceiverForTests.url(name());

    assertThat(api.get("/api/v1/webhooks", approver.token()).status()).isEqualTo(403);
    assertThat(api.post("/api/v1/webhooks", approver.token(), Map.of("url", url, "events", List.of("invoice.approved"))).status())
        .isEqualTo(403);

    assertThat(api.post("/api/v1/webhooks", admin.token(), Map.of("url", url, "events", List.of("invoice.exploded"))).status())
        .isEqualTo(400);
    assertThat(api.post("/api/v1/webhooks", admin.token(), Map.of("url", url, "events", List.of())).status()).isEqualTo(400);
    assertThat(api.post("/api/v1/webhooks", admin.token(), Map.of("url", "file:///etc/passwd", "events", List.of("invoice.approved"))).status())
        .isEqualTo(400);
    assertThat(api.post("/api/v1/webhooks", admin.token(), Map.of("url", "https://user:pw@example.com/x", "events", List.of("invoice.approved"))).status())
        .isEqualTo(400);

    JsonNode created = api.post("/api/v1/webhooks", admin.token(), Map.of("url", url, "events", List.of("invoice.approved"))).json();
    String id = created.at("/endpoint/id").asText();
    assertThat(api.get("/api/v1/webhooks/" + id + "/deliveries", stranger.token()).status()).isEqualTo(404);
    assertThat(api.patch("/api/v1/webhooks/" + id, stranger.token(), Map.of("enabled", false)).status()).isEqualTo(404);
    assertThat(api.delete("/api/v1/webhooks/" + id, stranger.token()).status()).isEqualTo(404);
    assertThat(api.get("/api/v1/webhooks", stranger.token()).json()).isEmpty();

    assertThat(api.patch("/api/v1/webhooks/" + id, admin.token(), Map.of("events", List.of("invoice.approved", "invoice.failed"))).json()
        .get("events")).hasSize(2);
    assertThat(api.delete("/api/v1/webhooks/" + id, admin.token()).status()).isEqualTo(204);
    assertThat(api.get("/api/v1/webhooks", admin.token()).json()).isEmpty();

    // at most ten endpoints per organisation
    for (int i = 0; i < 10; i++) {
      assertThat(api.post("/api/v1/webhooks", admin.token(), Map.of("url", FakeWebhookReceiverForTests.url(name()), "events", List.of("invoice.failed"))).status())
          .isEqualTo(201);
    }
    assertThat(api.post("/api/v1/webhooks", admin.token(), Map.of("url", url, "events", List.of("invoice.failed"))).status()).isEqualTo(409);
    assertThat(api.get("/api/v1/audit", admin.token()).json().toString()).contains("WEBHOOK_CREATED").contains("WEBHOOK_DELETED");
  }
}
