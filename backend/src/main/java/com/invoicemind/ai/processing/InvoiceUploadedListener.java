package com.invoicemind.ai.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicemind.ai.processing.ProcessingQueue.InvoiceQueuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceUploadedListener {
  private final ObjectMapper mapper;
  private final ProcessingService processing;

  @KafkaListener(topics = "${app.processing.topic}", groupId = "invoicemind-processing", concurrency = "3")
  public void onMessage(String payload) throws Exception {
    InvoiceQueuedEvent event = mapper.readValue(payload, InvoiceQueuedEvent.class);
    MDC.put("requestId", "inv-" + event.invoiceId().toString().substring(0, 8));
    try {
      processing.process(event.invoiceId());
    } finally {
      MDC.remove("requestId");
    }
  }

  /** Last resort for records the main listener could not handle even after retries. */
  @KafkaListener(topics = "${app.processing.topic}.DLT", groupId = "invoicemind-processing-dlt")
  public void onDeadLetter(String payload) {
    try {
      InvoiceQueuedEvent event = mapper.readValue(payload, InvoiceQueuedEvent.class);
      log.error("Dead-lettered invoice {}", event.invoiceId());
      processing.markFailedFromDeadLetter(event.invoiceId());
    } catch (Exception e) {
      log.error("Unreadable dead-letter record dropped: {}", e.getMessage());
    }
  }
}
