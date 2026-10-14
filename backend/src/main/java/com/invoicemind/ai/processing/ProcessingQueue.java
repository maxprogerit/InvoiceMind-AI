package com.invoicemind.ai.processing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicemind.ai.config.AppProperties;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes "invoice uploaded" messages to Kafka once the upload transaction has committed, so the
 * consumer can never see an invoice row that does not exist yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingQueue {
  public record InvoiceQueuedEvent(UUID invoiceId, UUID organizationId) {}

  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper mapper;
  private final AppProperties props;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onQueued(InvoiceQueuedEvent event) {
    enqueue(event.invoiceId(), event.organizationId());
  }

  /**
   * Fire-and-forget with a failure log: an invoice whose message was lost stays UPLOADED and the
   * {@link ProcessingSweeper} re-enqueues it, so the pipeline is self-healing.
   */
  public void enqueue(UUID invoiceId, UUID organizationId) {
    try {
      String payload = mapper.writeValueAsString(new InvoiceQueuedEvent(invoiceId, organizationId));
      kafka.send(props.processing().topic(), invoiceId.toString(), payload).whenComplete((result, error) -> {
        if (error != null) {
          log.error("Could not enqueue invoice {}: {}", invoiceId, error.getMessage());
        } else {
          log.debug("Enqueued invoice {} at {}", invoiceId, result.getRecordMetadata());
        }
      });
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
