package com.invoicemind.ai.ingest;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the mailbox poll on a schedule; only exists when e-mail ingestion is switched on. */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = {"enabled", "scheduled"}, havingValue = "true")
@RequiredArgsConstructor
public class EmailPoller {
  private final EmailIngestService service;

  @Scheduled(fixedDelayString = "PT${app.mail.poll-seconds:60}S", initialDelayString = "PT20S")
  public void poll() {
    service.pollOnce();
  }
}
