package com.invoicemind.ai.processing;

import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.invoice.InvoiceRepository;
import com.invoicemind.ai.invoice.InvoiceStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Self-healing: fails invoices stuck in PROCESSING (crashed worker) and re-enqueues invoices that never reached a worker. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingSweeper {
  private final InvoiceRepository invoices;
  private final ProcessingService processing;
  private final ProcessingQueue queue;
  private final AppProperties props;
  private final Clock clock;

  /**
   * After a restart the in-memory queue is empty and any invoice that was being processed lost its worker: put the
   * waiting ones back on the queue and fail the interrupted ones (the user can retry them with one click).
   */
  @EventListener(ApplicationReadyEvent.class)
  public void recoverAfterRestart() {
    Instant now = Instant.now(clock);
    invoices.findTop50ByStatusAndProcessingStartedAtBefore(InvoiceStatus.PROCESSING, now).forEach(i -> {
      log.warn("Invoice {} was interrupted by a restart, failing it so it can be retried", i.getId());
      processing.fail(i.getId(), "Processing was interrupted by a restart; please retry", "restart");
    });
    invoices.findTop50ByStatusAndUpdatedAtBefore(InvoiceStatus.UPLOADED, now).forEach(i -> {
      log.info("Invoice {} was waiting when the application stopped, enqueueing", i.getId());
      queue.enqueue(i.getId(), i.getOrganizationId());
    });
  }

  @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT45S")
  public void sweep() {
    Instant now = Instant.now(clock);
    invoices.findTop50ByStatusAndProcessingStartedAtBefore(InvoiceStatus.PROCESSING,
            now.minus(Duration.ofMinutes(props.processing().stuckAfterMinutes())))
        .forEach(i -> {
          log.warn("Invoice {} stuck in PROCESSING, failing it", i.getId());
          processing.fail(i.getId(), "Processing timed out; please retry", "timeout");
        });

    invoices.findTop50ByStatusAndUpdatedAtBefore(InvoiceStatus.UPLOADED,
            now.minus(Duration.ofMinutes(props.processing().requeueUploadedAfterMinutes())))
        .forEach(i -> {
          log.warn("Invoice {} still UPLOADED, re-enqueueing", i.getId());
          queue.enqueue(i.getId(), i.getOrganizationId());
        });
  }
}
