package com.invoicemind.ai.processing;

import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.invoice.InvoiceRepository;
import com.invoicemind.ai.invoice.InvoiceStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Self-healing: fails invoices stuck in PROCESSING (crashed worker) and re-enqueues lost UPLOADED messages. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingSweeper {
  private final InvoiceRepository invoices;
  private final ProcessingService processing;
  private final ProcessingQueue queue;
  private final AppProperties props;
  private final Clock clock;

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
