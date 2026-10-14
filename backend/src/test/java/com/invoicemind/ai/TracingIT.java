package com.invoicemind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.TestApi;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Distributed tracing: one trace follows an upload from the HTTP request through the Kafka hop into the
 * ai-service calls, so a slow or failed invoice can be followed across services.
 */
class TracingIT extends AbstractIntegrationTest {
  private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";

  @Test
  void theCallersTraceContinuesThroughKafkaIntoTheAiService() {
    Session admin = api.registerOrg("Trace Inc");
    String id = "trace-" + UUID.randomUUID().toString().substring(0, 8);
    FakeAiServiceForTests.scenario(id, FakeAiServiceForTests.okOcr(id), PipelineIT.extraction("ACME GmbH", "INV-" + id,
        "1000.00", "190.00", "1190.00"));

    Resp up = api.upload(admin.token(), TestApi.pdf(id), id + ".pdf",
        Map.of("traceparent", "00-" + TRACE_ID + "-b7ad6b7169203331-01"));
    assertThat(up.status()).isEqualTo(202);
    awaitStatus(admin, UUID.fromString(up.json().get("id").asText()), "VALIDATED", "NEEDS_REVIEW", "FAILED");

    // the OCR and extraction calls happen later, on a Kafka consumer thread, yet belong to the caller's trace
    assertThat(FakeAiServiceForTests.traceparent(id, "ocr")).startsWith("00-" + TRACE_ID + "-").endsWith("-01");
    assertThat(FakeAiServiceForTests.traceparent(id, "extract")).startsWith("00-" + TRACE_ID + "-");
    // each hop is its own span: the parent span id sent onwards is not the caller's
    assertThat(FakeAiServiceForTests.traceparent(id, "ocr")).doesNotContain("b7ad6b7169203331");
  }

  @Test
  void requestsWithoutATraceHeaderStartTheirOwnTrace() {
    Session admin = api.registerOrg("Trace Own Inc");
    String id = "trace-" + UUID.randomUUID().toString().substring(0, 8);
    FakeAiServiceForTests.scenario(id, FakeAiServiceForTests.okOcr(id), PipelineIT.extraction("ACME GmbH", "INV-" + id,
        "1000.00", "190.00", "1190.00"));
    UUID invoice = upload(admin, id);
    awaitStatus(admin, invoice, "VALIDATED", "NEEDS_REVIEW", "FAILED");
    String ocr = FakeAiServiceForTests.traceparent(id, "ocr");
    String extract = FakeAiServiceForTests.traceparent(id, "extract");
    assertThat(ocr).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    assertThat(ocr.substring(3, 35)).isEqualTo(extract.substring(3, 35)); // both calls of one invoice share a trace
    assertThat(ocr.substring(3, 35)).isNotEqualTo(TRACE_ID);
  }
}
