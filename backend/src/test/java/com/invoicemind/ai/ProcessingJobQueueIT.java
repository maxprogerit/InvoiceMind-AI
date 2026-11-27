package com.invoicemind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicemind.ai.processing.ProcessingJobQueue;
import com.invoicemind.ai.processing.ProcessingJobQueue.Job;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.FakeAiServiceForTests;
import com.invoicemind.ai.support.TestApi.Session;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The Postgres job queue that replaced Kafka. The application's own worker polls the same table, so these
 * tests drive a second queue instance whose clock is in the year 2100: the jobs it creates are due only for
 * it, never for the live worker (which runs on the real clock).
 */
class ProcessingJobQueueIT extends AbstractIntegrationTest {
  private static final Instant FUTURE = Instant.parse("2100-01-01T00:00:00Z");

  @Autowired NamedParameterJdbcTemplate jdbc;
  @Autowired PlatformTransactionManager txManager;

  private ProcessingJobQueue queue;
  private TransactionTemplate tx;

  @BeforeEach
  void futureQueue() {
    queue = new ProcessingJobQueue(jdbc, Clock.fixed(FUTURE, ZoneOffset.UTC));
    tx = new TransactionTemplate(txManager);
  }

  /** Real invoices (the FK needs them), already processed by the live worker; then one future job each. */
  private List<UUID> invoicesWithFutureJobs(int n) {
    Session s = api.registerOrg("Queue " + UUID.randomUUID().toString().substring(0, 6));
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      String sc = "queue-" + UUID.randomUUID().toString().substring(0, 8);
      FakeAiServiceForTests.scenario(sc, FakeAiServiceForTests.okOcr(sc),
          PipelineIT.extraction("ACME GmbH", "INV-" + sc, "1000.00", "190.00", "1190.00"));
      UUID id = upload(s, sc);
      awaitStatus(s, id, "VALIDATED", "NEEDS_REVIEW", "FAILED");
      ids.add(id);
    }
    tx.executeWithoutResult(t -> ids.forEach(id -> queue.enqueue(id, null, 3)));
    return ids;
  }

  @Test
  void aJobIsQueuedOnlyOncePerInvoice() {
    UUID invoice = invoicesWithFutureJobs(1).get(0);
    tx.executeWithoutResult(t -> queue.enqueue(invoice, null, 3)); // second enqueue is a no-op
    assertThat(queue.statuses(invoice)).containsExactly("DONE", "QUEUED");
  }

  @Test
  void lockedJobsAreSkippedNotWaitedFor() throws Exception {
    List<UUID> invoices = invoicesWithFutureJobs(4);
    CountDownLatch claimed = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    List<Job> first = new ArrayList<>();

    // worker A claims two jobs inside a transaction it keeps open: those rows stay locked
    CompletableFuture<Void> workerA = CompletableFuture.runAsync(() -> tx.executeWithoutResult(t -> {
      first.addAll(queue.claim(2, "worker-a"));
      claimed.countDown();
      try {
        release.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }));
    assertThat(claimed.await(10, TimeUnit.SECONDS)).isTrue();

    // worker B does not block on A's locks: it gets the remaining jobs at once
    long started = System.nanoTime();
    List<Job> second = queue.claim(10, "worker-b");
    long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
    release.countDown();
    workerA.get(10, TimeUnit.SECONDS);

    Set<UUID> a = ids(first), b = ids(second);
    assertThat(a).hasSize(2);
    assertThat(b).containsAll(invoices.stream().filter(i -> !a.contains(i)).toList());
    assertThat(b).doesNotContainAnyElementsOf(a);
    assertThat(ms).isLessThan(2000);
  }

  @Test
  void aRetryWaitsForItsBackoffAndCountsAttempts() {
    UUID invoice = invoicesWithFutureJobs(1).get(0);
    Job job = only(queue.claim(50, "w"), invoice);
    assertThat(job.attempts()).isEqualTo(1);
    queue.retryLater(job.id(), Duration.ofMinutes(5), "llm_timeout");

    assertThat(ids(queue.claim(50, "w"))).doesNotContain(invoice); // not due yet
    ProcessingJobQueue later = new ProcessingJobQueue(jdbc, Clock.fixed(FUTURE.plus(Duration.ofMinutes(6)), ZoneOffset.UTC));
    Job again = only(later.claim(50, "w"), invoice);
    assertThat(again.attempts()).isEqualTo(2);
    assertThat(lastError(invoice)).isEqualTo("llm_timeout");
  }

  @Test
  void jobsOfADeadWorkerAreHandedOutAgainAndGivenUpAfterTheLastAttempt() {
    UUID invoice = invoicesWithFutureJobs(1).get(0);
    only(queue.claim(50, "dead-worker"), invoice); // attempt 1 of 3, then the worker "dies"

    ProcessingJobQueue later = new ProcessingJobQueue(jdbc, Clock.fixed(FUTURE.plus(Duration.ofMinutes(11)), ZoneOffset.UTC));
    assertThat(later.releaseStuck(Duration.ofMinutes(10))).anyMatch(r -> r.invoiceId().equals(invoice) && !r.gaveUp());
    only(later.claim(50, "w2"), invoice);
    later.releaseStuck(Duration.ofMinutes(-1));
    only(later.claim(50, "w3"), invoice); // attempt 3 of 3
    assertThat(later.releaseStuck(Duration.ofMinutes(-1))).anyMatch(r -> r.invoiceId().equals(invoice) && r.gaveUp());
    assertThat(queue.statuses(invoice)).last().isEqualTo("FAILED");
  }

  private static Set<UUID> ids(List<Job> jobs) {
    Set<UUID> s = new HashSet<>();
    jobs.forEach(j -> s.add(j.invoiceId()));
    return s;
  }

  private static Job only(List<Job> jobs, UUID invoice) {
    return jobs.stream().filter(j -> j.invoiceId().equals(invoice)).findFirst()
        .orElseThrow(() -> new AssertionError("no job claimed for " + invoice));
  }

  private String lastError(UUID invoice) {
    return jdbc.queryForObject("select last_error from processing_jobs where invoice_id = :i order by id desc limit 1",
        new MapSqlParameterSource("i", invoice), String.class);
  }
}
