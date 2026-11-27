package com.invoicemind.ai.processing;

import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.processing.ProcessingJobQueue.Job;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls {@code processing_jobs} and runs up to {@code app.processing.concurrency} jobs at a time.
 * Whether a failed job is retried, and when, is decided by {@link ProcessingService}; an exception that
 * escapes it (database down, a bug) is retried with backoff and fails the invoice on the last attempt.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.processing", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class ProcessingWorker {
  private final ProcessingJobQueue queue;
  private final ProcessingService processing;
  private final TraceContextCodec traces;
  private final AppProperties.Processing props;
  private final ExecutorService pool;
  private final Semaphore slots;
  private final String workerId;

  public ProcessingWorker(ProcessingJobQueue queue, ProcessingService processing, TraceContextCodec traces,
                          AppProperties app) {
    this.queue = queue;
    this.processing = processing;
    this.traces = traces;
    this.props = app.processing();
    this.pool = Executors.newFixedThreadPool(props.concurrency(), Thread.ofPlatform().name("invoice-worker-", 0).factory());
    this.slots = new Semaphore(props.concurrency());
    this.workerId = hostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  @Scheduled(fixedDelayString = "${app.processing.poll-interval:PT1S}", initialDelayString = "PT2S")
  public void poll() {
    int free = slots.availablePermits();
    if (free == 0) return;
    List<Job> jobs;
    try {
      jobs = queue.claim(free, workerId);
    } catch (RuntimeException e) {
      log.warn("Could not claim processing jobs: {}", e.getMessage());
      return;
    }
    for (Job job : jobs) {
      slots.acquireUninterruptibly();
      pool.execute(() -> {
        try {
          run(job);
        } finally {
          slots.release();
        }
      });
    }
  }

  void run(Job job) {
    MDC.put("requestId", "inv-" + job.invoiceId().toString().substring(0, 8));
    try {
      traces.runInSpan(job.traceparent(), "invoice.process", () -> {
        ProcessingService.Outcome outcome = processing.process(job.invoiceId(), job.lastAttempt());
        switch (outcome.kind()) {
          case DONE -> queue.complete(job.id());
          case FAILED -> queue.fail(job.id(), outcome.detail());
          case RETRY -> queue.retryLater(job.id(), backoff(job.attempts()), outcome.detail());
        }
      });
    } catch (RuntimeException e) {
      log.error("Job {} for invoice {} crashed (attempt {}/{})", job.id(), job.invoiceId(), job.attempts(),
          job.maxAttempts(), e);
      try {
        if (job.lastAttempt()) {
          processing.fail(job.invoiceId(), "Processing was aborted after repeated errors; please retry", "internal_error");
          queue.fail(job.id(), e.getClass().getSimpleName());
        } else {
          processing.resetForRetry(job.invoiceId());
          queue.retryLater(job.id(), backoff(job.attempts()), e.getClass().getSimpleName());
        }
      } catch (RuntimeException again) {
        log.error("Could not record the failure of job {}; it will be released as stuck", job.id(), again);
      }
    } finally {
      MDC.remove("requestId");
    }
  }

  /** Exponential backoff: base, 2x base, 4x base ... capped at 10 minutes. */
  Duration backoff(int attempts) {
    long base = props.retryBase().toMillis();
    long millis = base * (1L << Math.min(Math.max(attempts - 1, 0), 10));
    return Duration.ofMillis(Math.min(millis, Duration.ofMinutes(10).toMillis()));
  }

  /** A worker that died mid-job leaves it RUNNING; hand it out again (or give up after the last attempt). */
  @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT45S")
  public void releaseStuck() {
    try {
      for (ProcessingJobQueue.Released r : queue.releaseStuck(Duration.ofMinutes(props.stuckAfterMinutes()))) {
        if (r.gaveUp()) {
          log.warn("Processing of invoice {} was stuck on its last attempt; failing it", r.invoiceId());
          processing.fail(r.invoiceId(), "Processing timed out; please retry", "timeout");
        } else {
          log.warn("Processing of invoice {} was stuck; queued again", r.invoiceId());
          processing.resetForRetry(r.invoiceId());
        }
      }
    } catch (RuntimeException e) {
      log.warn("Stuck-job sweep failed: {}", e.getMessage());
    }
  }

  @PreDestroy
  void shutdown() throws InterruptedException {
    pool.shutdown();
    if (!pool.awaitTermination(20, TimeUnit.SECONDS)) pool.shutdownNow(); // unfinished jobs are released as stuck
  }

  private static String hostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      return "backend";
    }
  }
}
