package com.invoicemind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.TestApi;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/** Batch upload of a .zip, with its limits (test profile: 5 documents, 3 MB unpacked). */
class BatchUploadIT extends AbstractIntegrationTest {

  private static byte[] zip(Map<String, byte[]> entries) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(out)) {
      for (var e : entries.entrySet()) {
        z.putNextEntry(new ZipEntry(e.getKey()));
        if (e.getValue() != null) z.write(e.getValue());
        z.closeEntry();
      }
    }
    return out.toByteArray();
  }

  private static byte[] document(String vendor) {
    String sc = "zip-" + UUID.randomUUID().toString().substring(0, 8);
    FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc), PipelineIT.extraction(vendor + " " + sc, "Z-" + sc,
        "100.00", "19.00", "119.00"));
    return TestApi.pdf(sc);
  }

  private Resp send(Session who, byte[] archive, String name) {
    return api.upload(who.token(), archive, name, Map.of(), "/api/v1/invoices/zip");
  }

  @Test
  void documentsInAnArchiveBecomeInvoicesAndEverythingElseIsExplained() throws Exception {
    Session s = api.registerOrg("Zip Inc");
    Map<String, byte[]> entries = new LinkedHashMap<>();
    entries.put("march/", null);                                      // folder: skipped silently
    entries.put("march/a.pdf", document("Alpha"));
    entries.put("march/b.pdf", document("Beta"));
    entries.put("c.pdf", document("Gamma"));
    entries.put("notes.txt", "remember to pay".getBytes(StandardCharsets.UTF_8));
    entries.put("inner.zip", zip(Map.of("x.pdf", document("Hidden"))));
    entries.put("../../etc/evil.pdf", document("Evil"));
    entries.put("__MACOSX/march/._a.pdf", new byte[] {0, 5, 22, 7});
    entries.put(".DS_Store", new byte[] {0, 0, 0, 1});

    Resp r = send(s, zip(entries), "march.zip");
    assertThat(r.status()).as(r.text()).isEqualTo(200);
    assertThat(r.json().get("created")).hasSize(3);
    Map<String, String> skipped = new LinkedHashMap<>();
    r.json().get("skipped").forEach(x -> skipped.put(x.get("name").asText(), x.get("reason").asText()));
    assertThat(skipped).containsOnlyKeys("notes.txt", "inner.zip", "../../etc/evil.pdf");
    assertThat(skipped.get("notes.txt")).contains("Only PDF");
    assertThat(skipped.get("inner.zip")).contains("not opened");
    assertThat(skipped.get("../../etc/evil.pdf")).contains("Unsafe path");

    for (JsonNode inv : r.json().get("created")) {
      assertThat(inv.get("source").asText()).isEqualTo("BATCH");
      assertThat(inv.get("originalFilename").asText()).doesNotContain("/");
      awaitStatus(s, UUID.fromString(inv.get("id").asText()), "VALIDATED", "NEEDS_REVIEW");
    }
    assertThat(api.get("/api/v1/audit?action=BATCH_UPLOADED", s.token()).json().get("totalItems").asInt()).isEqualTo(1);
  }

  @Test
  void theLimitsHoldWhileUnpacking() throws Exception {
    Session s = api.registerOrg("Zip Limits Inc");
    Map<String, byte[]> many = new LinkedHashMap<>();
    for (int i = 0; i < 7; i++) many.put("doc-" + i + ".pdf", document("Many " + i));
    Resp tooMany = send(s, zip(many), "many.zip");
    assertThat(tooMany.json().get("created")).hasSize(5);
    assertThat(tooMany.json().get("skipped").get(0).get("reason").asText()).contains("More than 5 documents");

    // a small archive that unpacks to more than the budget (a zip bomb in miniature) is stopped while reading
    Map<String, byte[]> bomb = new LinkedHashMap<>();
    bomb.put("bomb.pdf", new byte[4_000_000]);
    byte[] archive = zip(bomb);
    assertThat(archive.length).isLessThan(50_000);
    Resp stopped = send(s, archive, "bomb.zip");
    assertThat(stopped.json().get("created")).isEmpty();
    assertThat(stopped.json().get("skipped").get(0).get("reason").asText()).contains("unpacks to more than");

    Resp notZip = send(s, TestApi.pdf("not-a-zip"), "fake.zip");
    assertThat(notZip.status()).isEqualTo(415);
  }
}
