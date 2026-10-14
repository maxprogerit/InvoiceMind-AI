package com.invoicemind.ai.support;

import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Boots the full application once (shared context) against real Postgres, Kafka and Redis. */
// @SpringBootTest switches tracing off by default; the tracing test needs it on (nothing is exported, see application-test.yml)
@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
public abstract class AbstractIntegrationTest {
  @LocalServerPort
  protected int port;
  @Autowired
  protected ObjectMapper mapper;
  protected TestApi api;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("app.ai.base-url", FakeAiServiceForTests::baseUrl);
    registry.add("app.fx.base-url", FakeFrankfurterForTests::baseUrl);
    registry.add("app.mail.host", () -> "127.0.0.1");
    registry.add("app.mail.port", MailServerForTests::imapPort);
  }

  @BeforeEach
  void initApi() {
    api = new TestApi("http://localhost:" + port, mapper);
  }

  /** Uploads a document for the given scenario and returns the invoice id (HTTP 202). */
  protected UUID upload(TestApi.Session who, String scenario) {
    TestApi.Resp r = api.upload(who.token(), TestApi.pdf(scenario), scenario + ".pdf");
    if (r.status() != 202) throw new AssertionError("upload expected 202 but was " + r.status() + ": " + r.text());
    return UUID.fromString(r.json().get("id").asText());
  }

  /** Polls the invoice until it reaches one of the given statuses (the pipeline is asynchronous via Kafka). */
  protected JsonNode awaitStatus(TestApi.Session who, UUID id, String... statuses) {
    Set<String> wanted = Set.of(statuses);
    JsonNode[] last = new JsonNode[1];
    await().atMost(Duration.ofSeconds(45)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
      TestApi.Resp r = api.get("/api/v1/invoices/" + id, who.token());
      if (r.status() != 200) throw new AssertionError("GET invoice -> " + r.status() + " " + r.text());
      last[0] = r.json();
      if (!wanted.contains(last[0].get("status").asText())) {
        throw new AssertionError("status is " + last[0].get("status").asText() + ", waiting for " + wanted);
      }
    });
    return last[0];
  }
}
