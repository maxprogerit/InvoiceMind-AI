package com.invoicemind.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.TestApi.Session;
import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** Live status over the real STOMP endpoint. */
class WebSocketIT extends AbstractIntegrationTest {

  private WebSocketStompClient client() {
    WebSocketStompClient c = new WebSocketStompClient(new StandardWebSocketClient());
    c.setMessageConverter(new MappingJackson2MessageConverter());
    return c;
  }

  private StompSession connect(String token) throws Exception {
    StompHeaders headers = new StompHeaders();
    if (token != null) headers.add("Authorization", "Bearer " + token);
    return client().connectAsync("ws://localhost:" + port + "/ws/live", new WebSocketHttpHeaders(), headers,
        new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
  }

  private BlockingQueue<JsonNode> subscribe(StompSession session, String destination) {
    BlockingQueue<JsonNode> queue = new LinkedBlockingQueue<>();
    session.subscribe(destination, new StompFrameHandler() {
      @Override public Type getPayloadType(StompHeaders headers) { return JsonNode.class; }
      @Override public void handleFrame(StompHeaders headers, Object payload) { queue.add((JsonNode) payload); }
    });
    return queue;
  }

  private static String scenario() {
    return PipelineIT.scenario("ACME GmbH", "INV-" + PipelineIT.id("ws"), "1000.00", "190.00", "1190.00");
  }

  @Test
  void connectionWithoutAValidTokenIsRejected() {
    assertThatThrownBy(() -> connect(null)).isInstanceOfAny(Exception.class);
    assertThatThrownBy(() -> connect("garbage.token.value")).isInstanceOfAny(Exception.class);
  }

  @Test
  void uploaderReceivesLiveStatusEventsForTheirInvoice() throws Exception {
    Session admin = api.registerOrg("Live Inc");
    Session user = api.createUser(admin, "USER");
    StompSession ws = connect(user.token());
    BlockingQueue<JsonNode> events = subscribe(ws, "/user/queue/invoices");
    Thread.sleep(300); // let the subscription register on the broker

    UUID invoice = upload(user, scenario());

    List<String> seen = new java.util.ArrayList<>();
    long deadline = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < deadline && !seen.contains("VALIDATED")) {
      JsonNode e = events.poll(1, TimeUnit.SECONDS);
      if (e != null && e.get("invoiceId").asText().equals(invoice.toString())) seen.add(e.get("status").asText());
    }
    assertThat(seen).containsSubsequence("UPLOADED", "PROCESSING", "EXTRACTED", "VALIDATED");
    ws.disconnect();
  }

  @Test
  void otherUsersDoNotReceiveSomeoneElsesEvents() throws Exception {
    Session admin = api.registerOrg("Private Inc");
    Session alice = api.createUser(admin, "USER");
    Session bob = api.createUser(admin, "USER");
    StompSession bobWs = connect(bob.token());
    BlockingQueue<JsonNode> bobEvents = subscribe(bobWs, "/user/queue/invoices");
    Thread.sleep(300);

    UUID invoice = upload(alice, scenario());
    awaitStatus(alice, invoice, "VALIDATED");
    assertThat(bobEvents.poll(2, TimeUnit.SECONDS)).isNull();
    bobWs.disconnect();
  }

  @Test
  void reviewersTopicIsRestrictedToReviewersOfTheSameOrganisation() throws Exception {
    Session admin = api.registerOrg("Topic Inc");
    Session approver = api.createUser(admin, "APPROVER");
    Session user = api.createUser(admin, "USER");
    Session foreignApprover = api.createUser(api.registerOrg("Foreign Inc"), "APPROVER");
    String topic = "/topic/org/" + admin.organizationId() + "/reviewers";

    // reviewers of the org receive uploads by anyone in the org
    StompSession approverWs = connect(approver.token());
    BlockingQueue<JsonNode> approverEvents = subscribe(approverWs, topic);
    Thread.sleep(300);
    UUID invoice = upload(user, scenario());
    JsonNode first = approverEvents.poll(30, TimeUnit.SECONDS);
    assertThat(first).isNotNull();
    assertThat(first.get("invoiceId").asText()).isEqualTo(invoice.toString());
    awaitStatus(user, invoice, "VALIDATED");

    // a plain USER and a reviewer of another org are refused (the broker closes the session)
    for (Session intruder : List.of(user, foreignApprover)) {
      StompSession ws = connect(intruder.token());
      BlockingQueue<JsonNode> q = subscribe(ws, topic);
      Thread.sleep(500);
      upload(admin, scenario());
      assertThat(q.poll(3, TimeUnit.SECONDS)).as("events leaked to " + intruder.email()).isNull();
      try { ws.disconnect(); } catch (Exception ignored) { /* session already closed by the broker */ }
    }
    approverWs.disconnect();
  }

  @Test
  void clientsCannotPublishMessages() throws Exception {
    Session s = api.registerOrg("Send Inc");
    StompSession ws = connect(s.token());
    try {
      ws.send("/app/anything", "hello");
      Thread.sleep(500);
    } catch (Exception ignored) {
      // expected: the server rejects SEND frames
    }
    assertThat(ws.isConnected()).isFalse();
  }
}
