package com.invoicemind.ai.ocr;

import com.invoicemind.ai.dto.InvoiceDtos.OcrResultResponse;
import com.invoicemind.ai.service.AiParsingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OcrSimulationService implements AiParsingService {
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final SimpMessagingTemplate messagingTemplate;
  private final Random random = new Random();

  @Async
  public void emitLiveProcessing(Long documentId, String traceId) {
    messagingTemplate.convertAndSend("/topic/executions", Map.of("documentId", documentId, "status", "OCR_STARTED", "traceId", traceId));
    kafkaTemplate.send("ocr-events", Map.of("event", "OCR_STARTED", "documentId", documentId, "traceId", traceId));
    try { Thread.sleep(900); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    messagingTemplate.convertAndSend("/topic/executions", Map.of("documentId", documentId, "status", "OCR_FINISHED", "traceId", traceId));
    kafkaTemplate.send("ocr-events", Map.of("event", "OCR_FINISHED", "documentId", documentId, "traceId", traceId));
  }

  @Override
  public OcrResultResponse parseDocument(String fileName) {
    BigDecimal confidence = BigDecimal.valueOf(0.88 + random.nextDouble() * 0.11).setScale(2, RoundingMode.HALF_UP);
    return new OcrResultResponse(
      "INV-" + (2026000 + random.nextInt(999)),
      fileName.contains("receipt") ? "Retail Merchant" : "Apex Logistics",
      BigDecimal.valueOf(500 + random.nextInt(15000)),
      confidence,
      fileName.contains("cloud") ? "Infrastructure" : "Operations",
      confidence.doubleValue() > 0.95 ? "Auto-approve candidate" : "Needs validation"
    );
  }
}
