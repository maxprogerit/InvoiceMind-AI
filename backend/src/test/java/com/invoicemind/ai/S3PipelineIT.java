package com.invoicemind.ai;

import static com.invoicemind.ai.support.FakeAiServiceForTests.line;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.S3ServerForTests;
import com.invoicemind.ai.support.TestApi;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import com.invoicemind.ai.storage.FileStorage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The whole upload -> OCR -> validation -> download flow with files kept in S3 (SeaweedFS) instead of a volume. */
class S3PipelineIT extends AbstractIntegrationTest {

  @DynamicPropertySource
  static void s3(DynamicPropertyRegistry registry) {
    registry.add("app.storage.type", () -> "s3");
    registry.add("app.storage.s3.endpoint", S3ServerForTests::endpoint);
    registry.add("app.storage.s3.access-key", () -> S3ServerForTests.ACCESS_KEY);
    registry.add("app.storage.s3.secret-key", () -> S3ServerForTests.SECRET_KEY);
    registry.add("app.storage.s3.path-style", () -> "true");
    registry.add("app.storage.s3.create-bucket", () -> "true");
    registry.add("app.storage.s3.bucket", () -> "invoicemind-pipeline");
  }

  @Autowired
  FileStorage storage;

  @Test
  void invoiceFilesLiveInObjectStorage() {
    assertThat(storage.getClass().getSimpleName()).isEqualTo("S3FileStorage");

    Session admin = api.registerOrg("S3 Inc");
    String id = "s3-" + UUID.randomUUID().toString().substring(0, 8);
    FakeAiServiceForTests.scenario(id, FakeAiServiceForTests.okOcr(id), FakeAiServiceForTests.okExtraction(
        "ACME GmbH", "INV-" + id, "2026-03-14", null, "EUR", "100.00", "0.00", "100.00",
        List.of(line("Services", "1", "100.00", "100.00")), false, false));
    byte[] content = TestApi.pdf(id);
    Resp up = api.upload(admin.token(), content, id + ".pdf");
    assertThat(up.status()).isEqualTo(202);
    UUID invoice = UUID.fromString(up.json().get("id").asText());

    JsonNode inv = awaitStatus(admin, invoice, "VALIDATED", "NEEDS_REVIEW", "FAILED");
    assertThat(inv.get("status").asText()).isEqualTo("VALIDATED"); // the pipeline read the file back from S3

    Resp file = api.get("/api/v1/invoices/" + invoice + "/file", admin.token());
    assertThat(file.status()).isEqualTo(200);
    assertThat(file.bytes()).isEqualTo(content);

    // deleting the invoice removes the object as well
    assertThat(api.delete("/api/v1/invoices/" + invoice, admin.token()).status()).isEqualTo(204);
    assertThat(api.get("/api/v1/invoices/" + invoice + "/file", admin.token()).status()).isEqualTo(404);
  }
}
