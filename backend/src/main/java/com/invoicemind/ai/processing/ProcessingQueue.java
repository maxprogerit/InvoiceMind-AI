package com.invoicemind.ai.processing;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hands an uploaded invoice to the worker pool once the upload transaction has committed, so a worker can never see
 * an invoice row that does not exist yet. The queue lives in memory: work that is lost with the process (a restart)
 * is recovered from the database, where the invoice is still UPLOADED or PROCESSING, see {@link ProcessingSweeper}.
 */
@Slf4j
@Component
public class ProcessingQueue {
  public record InvoiceQueuedEvent(UUID invoiceId, UUID organizationId) {}

  private final ThreadPoolTaskExecutor executor;
  private final ProcessingService processing;

  public ProcessingQueue(ThreadPoolTaskExecutor processingExecutor, ProcessingService processing) {
    this.executor = processingExecutor;
    this.processing = processing;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onQueued(InvoiceQueuedEvent event) {
    enqueue(event.invoiceId(), event.organizationId());
  }

  public void enqueue(UUID invoiceId, UUID organizationId) {
    try {
      executor.execute(() -> run(invoiceId));
    } catch (TaskRejectedException e) {
      log.warn("Worker queue is full, invoice {} stays UPLOADED and will be retried by the sweeper", invoiceId);
    }
  }

  private void run(UUID invoiceId) {
    MDC.put("requestId", "inv-" + invoiceId.toString().substring(0, 8));
    try {
      processing.process(invoiceId); // idempotent: only an UPLOADED invoice can be claimed
    } catch (RuntimeException e) {
      log.error("Processing invoice {} crashed", invoiceId, e);
      processing.fail(invoiceId, "Unexpected processing error", "internal_error");
    } finally {
      MDC.remove("requestId");
    }
  }
}
