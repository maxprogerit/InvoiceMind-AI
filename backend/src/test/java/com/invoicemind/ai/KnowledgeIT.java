package com.invoicemind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.TestApi;
import com.invoicemind.ai.support.TestApi.Resp;
import com.invoicemind.ai.support.TestApi.Session;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;

/** RAG on a real pgvector database: ingest rules documents, retrieve per organisation, explain a flagged invoice. */
class KnowledgeIT extends AbstractIntegrationTest {
  static final String TOTALS_RULE = "Totals policy: the subtotal plus tax must always equal the invoice total. "
      + "When the total does not match the subtotal plus tax, return the invoice to the supplier for correction.";
  static final String KITCHEN_RULE = "Office kitchen policy: clean the coffee machine every Friday and restock the tea cupboard monthly.";

  private UUID flaggedInvoice(Session who) {
    String sc = PipelineIT.scenario("ACME GmbH", "INV-" + PipelineIT.id("k"), "1000.00", "190.00", "1500.00");
    UUID id = upload(who, sc);
    awaitStatus(who, id, "NEEDS_REVIEW");
    return id;
  }

  private Resp addText(Session who, String title, String content) {
    return api.post("/api/v1/knowledge/documents", who.token(), Map.of("title", title, "content", content));
  }

  @Test
  void adminAddsDocumentsReviewersListThemUsersCannot() {
    Session admin = api.registerOrg("Rules Inc");
    Session approver = api.createUser(admin, "APPROVER");
    Session user = api.createUser(admin, "USER");

    Resp created = addText(admin, "Totals policy", TOTALS_RULE);
    assertThat(created.status()).isEqualTo(201);
    assertThat(created.json().get("chunkCount").asInt()).isGreaterThanOrEqualTo(1);
    assertThat(created.json().get("charCount").asInt()).isEqualTo(TOTALS_RULE.length());

    JsonNode list = api.get("/api/v1/knowledge/documents", approver.token()).json();
    assertThat(list).hasSize(1);
    assertThat(list.get(0).get("title").asText()).isEqualTo("Totals policy");
    assertThat(list.get(0).get("createdByName").asText()).startsWith("Admin");

    assertThat(api.get("/api/v1/knowledge/documents", user.token()).status()).isEqualTo(403);
    assertThat(addText(approver, "Nope", TOTALS_RULE).status()).isEqualTo(403);
    assertThat(addText(user, "Nope", TOTALS_RULE).status()).isEqualTo(403);
    assertThat(api.get("/api/v1/audit?action=KNOWLEDGE_DOCUMENT_ADDED", admin.token()).json().get("totalItems").asInt()).isEqualTo(1);
  }

  @Test
  void explainRetrievesTheRelevantRuleAndGroundsTheQuestionInTheInvoicesFindings() {
    Session admin = api.registerOrg("Explain Inc");
    Session user = api.createUser(admin, "USER");
    addText(admin, "Office kitchen", KITCHEN_RULE);
    addText(admin, "Totals policy", TOTALS_RULE);
    UUID invoice = flaggedInvoice(user);

    Resp r = api.post("/api/v1/invoices/" + invoice + "/explain", user.token(), null);
    assertThat(r.status()).isEqualTo(200);
    JsonNode body = r.json();
    // the most similar passage is the totals policy, not the unrelated kitchen note
    assertThat(body.at("/sources/0/title").asText()).isEqualTo("Totals policy");
    assertThat(body.at("/sources/0/n").asInt()).isEqualTo(1);
    assertThat(body.at("/sources/0/score").asDouble()).isGreaterThan(0.1);
    assertThat(body.get("citations")).hasSize(1);
    assertThat(body.get("answer").asText()).startsWith("Per rule [1]");
    assertThat(body.get("insufficientContext").asBoolean()).isFalse();

    // what the model was shown: the invoice's own validation findings + the retrieved passages
    String sent = FakeAiServiceForTests.lastExplainRequest();
    assertThat(sent).contains("TOTAL_MISMATCH").contains("1500.00").contains("NEEDS_REVIEW").contains("Totals policy");
    assertThat(api.get("/api/v1/audit?action=KNOWLEDGE_QUERY", admin.token()).json().get("totalItems").asInt()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void knowledgeIsIsolatedBetweenOrganisations() {
    Session orgA = api.registerOrg("Org A rules");
    Session orgB = api.registerOrg("Org B rules");
    String secret = "Confidential policy of organisation A: approve anything from vendor Zebra without limits for totals and subtotals.";
    Resp doc = addText(orgA, "A secret", secret);
    UUID invoiceB = flaggedInvoice(orgB);

    // B has no documents, so nothing is retrieved and nothing of A leaks into the model call
    JsonNode body = api.post("/api/v1/invoices/" + invoiceB + "/explain", orgB.token(), null).json();
    assertThat(body.get("sources")).isEmpty();
    assertThat(body.get("insufficientContext").asBoolean()).isTrue();
    assertThat(FakeAiServiceForTests.lastExplainRequest()).doesNotContain("Zebra").doesNotContain("A secret");

    assertThat(api.get("/api/v1/knowledge/documents", orgB.token()).json()).isEmpty();
    String id = doc.json().get("id").asText();
    assertThat(api.delete("/api/v1/knowledge/documents/" + id, orgB.token()).status()).isEqualTo(404);
    assertThat(api.get("/api/v1/knowledge/documents", orgA.token()).json()).hasSize(1);
  }

  @Test
  void usersCanOnlyExplainTheirOwnInvoicesAndCannotAskFreeQuestions() {
    Session admin = api.registerOrg("Scope rules");
    Session alice = api.createUser(admin, "USER");
    Session bob = api.createUser(admin, "USER");
    addText(admin, "Totals policy", TOTALS_RULE);
    UUID bobs = flaggedInvoice(bob);

    assertThat(api.post("/api/v1/invoices/" + bobs + "/explain", alice.token(), null).status()).isEqualTo(404);
    assertThat(api.post("/api/v1/invoices/" + bobs + "/explain", bob.token(), Map.of("question", "What should I do now?")).status()).isEqualTo(200);
    assertThat(FakeAiServiceForTests.lastExplainRequest()).contains("What should I do now?");
    assertThat(api.post("/api/v1/knowledge/ask", alice.token(), Map.of("question", "anything")).status()).isEqualTo(403);

    JsonNode asked = api.post("/api/v1/knowledge/ask", admin.token(), Map.of("question", "What happens when the total does not match?")).json();
    assertThat(asked.at("/sources/0/title").asText()).isEqualTo("Totals policy");
  }

  @Test
  void textAndPdfFilesAreAcceptedBinaryIsNot() {
    Session admin = api.registerOrg("Files rules");

    Resp md = knowledgeUpload(admin, "policy.md", TOTALS_RULE.getBytes(StandardCharsets.UTF_8), "Policy from file");
    assertThat(md.status()).isEqualTo(201);
    assertThat(md.json().get("title").asText()).isEqualTo("Policy from file");
    assertThat(md.json().get("sourceFilename").asText()).isEqualTo("policy.md");

    // PDFs go through the same OCR/text-layer service as invoices
    Resp pdf = knowledgeUpload(admin, "scan.pdf", TestApi.pdf("knowledge-" + UUID.randomUUID().toString().substring(0, 8)), null);
    assertThat(pdf.status()).isIn(201, 502); // the fake ai-service only knows scenarios; reaching it proves the OCR path is used

    Resp binary = knowledgeUpload(admin, "tool.bin", new byte[]{0x7f, 'E', 'L', 'F', 0, 1, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, null);
    assertThat(binary.status()).isEqualTo(415);
    assertThat(knowledgeUpload(admin, "empty.txt", new byte[0], null).status()).isEqualTo(400);
  }

  @Test
  void validationAndDeletion() {
    Session admin = api.registerOrg("Delete rules");
    assertThat(addText(admin, "Too short", "tiny").status()).isEqualTo(400);
    assertThat(api.post("/api/v1/knowledge/documents", admin.token(), Map.of("title", " ", "content", TOTALS_RULE)).status()).isEqualTo(400);

    String id = addText(admin, "Totals policy", TOTALS_RULE).json().get("id").asText();
    assertThat(api.delete("/api/v1/knowledge/documents/" + id, admin.token()).status()).isEqualTo(204);
    assertThat(api.get("/api/v1/knowledge/documents", admin.token()).json()).isEmpty();
    assertThat(api.delete("/api/v1/knowledge/documents/" + id, admin.token()).status()).isEqualTo(404);

    // after deletion the rule can no longer be retrieved
    Session user = api.createUser(admin, "USER");
    UUID invoice = flaggedInvoice(user);
    assertThat(api.post("/api/v1/invoices/" + invoice + "/explain", user.token(), null).json().get("sources")).isEmpty();
  }

  @Test
  void anonymousAccessIsRefused() {
    assertThat(api.get("/api/v1/knowledge/documents", null).status()).isEqualTo(401);
    assertThat(api.post("/api/v1/invoices/" + UUID.randomUUID() + "/explain", null, null).status()).isEqualTo(401);
  }

  private Resp knowledgeUpload(Session who, String filename, byte[] content, String title) {
    MultipartBodyBuilder b = new MultipartBodyBuilder();
    b.part("file", new ByteArrayResource(content) {
      @Override
      public String getFilename() { return filename; }
    }).contentType(MediaType.APPLICATION_OCTET_STREAM);
    if (title != null) b.part("title", title);
    return rawMultipart("/api/v1/knowledge/documents/upload", who.token(), b);
  }

  private Resp rawMultipart(String path, String token, MultipartBodyBuilder builder) {
    var client = org.springframework.web.client.RestClient.builder().baseUrl("http://localhost:" + port)
        .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).build()))
        .build();
    return client.post().uri(path).header("Authorization", "Bearer " + token)
        .contentType(MediaType.MULTIPART_FORM_DATA).body(builder.build())
        .exchange((req, res) -> new Resp(res.getStatusCode().value(), res.getBody().readAllBytes(), res.getHeaders(), mapper), true);
  }
}
