package com.invoicemind.ai.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaEventListenerService {
  @KafkaListener(topics = "workflow-events", groupId = "invoicemind-analytics")
  public void workflowEvents(String payload) {
    log.info("workflow-events: {}", payload);
  }

  @KafkaListener(topics = "ocr-events", groupId = "invoicemind-analytics")
  public void ocrEvents(String payload) {
    log.info("ocr-events: {}", payload);
  }
}
