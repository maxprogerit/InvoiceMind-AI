package com.invoicemind.ai.processing;

import com.invoicemind.ai.analytics.AnalyticsCache;
import com.invoicemind.ai.config.WebSocketConfig;
import com.invoicemind.ai.invoice.InvoiceEntity;
import com.invoicemind.ai.invoice.InvoiceStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Pushes status changes: to the uploader's private queue, and to the organisation's reviewer topic.
 * Events carry no document content - clients refetch through the authorised REST API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatusPublisher {
  public record StatusEvent(UUID invoiceId, InvoiceStatus status, String vendorName, String invoiceNumber,
                            String failureReason, Instant at) {}

  private final SimpMessagingTemplate messaging;
  private final AnalyticsCache analyticsCache;

  public void publish(InvoiceEntity invoice) {
    StatusEvent event = new StatusEvent(invoice.getId(), invoice.getStatus(), invoice.getVendorName(),
        invoice.getInvoiceNumber(), invoice.getFailureReason(), Instant.now());
    UUID uploader = invoice.getUploadedBy();
    String topic = WebSocketConfig.REVIEWERS_TOPIC.formatted(invoice.getOrganizationId());
    afterCommit(() -> {
      analyticsCache.invalidate(invoice.getOrganizationId()); // every status change can move the numbers
      messaging.convertAndSendToUser(uploader.toString(), "/queue/invoices", event);
      messaging.convertAndSend(topic, event);
    });
  }

  private void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() { safely(action); }
      });
    } else {
      safely(action);
    }
  }

  private void safely(Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      log.warn("Status push failed: {}", e.getMessage());
    }
  }
}
