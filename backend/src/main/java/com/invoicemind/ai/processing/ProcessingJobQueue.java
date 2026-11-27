package com.invoicemind.ai.processing;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The processing queue, as a PostgreSQL table ({@code processing_jobs}).
 *
 * <p>{@link #enqueue} joins the caller's transaction, so the job exists exactly when the invoice row does.
 * {@link #claim} uses {@code FOR UPDATE SKIP LOCKED}: concurrent workers (threads or backend instances) never
 * receive the same job. A retry is a {@code run_after} in the future; a crashed worker's job is released by
 * {@link #releaseStuck}.
 */
@Component
@RequiredArgsConstructor
public class ProcessingJobQueue {
  public record Job(long id, UUID invoiceId, int attempts, int maxAttempts, String traceparent) {
    public boolean lastAttempt() { return attempts >= maxAttempts; }
  }

  private final NamedParameterJdbcTemplate jdbc;
  private final Clock clock;

  /** Queues processing for the invoice; a no-op when the invoice already has an open job. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(UUID invoiceId, String traceparent, int maxAttempts) {
    jdbc.update("""
        insert into processing_jobs (invoice_id, max_attempts, traceparent, run_after)
        values (:invoice, :max, :trace, :now)
        on conflict (invoice_id) where status in ('QUEUED', 'RUNNING') do nothing""",
        new MapSqlParameterSource("invoice", invoiceId).addValue("max", maxAttempts)
            .addValue("trace", traceparent).addValue("now", now()));
  }

  /** Atomically takes up to {@code limit} due jobs and marks them RUNNING (attempts + 1). */
  @Transactional
  public List<Job> claim(int limit, String workerId) {
    return jdbc.query("""
        update processing_jobs j
           set status = 'RUNNING', attempts = j.attempts + 1, locked_at = :now, locked_by = :worker, updated_at = :now
         where j.id in (select id from processing_jobs
                         where status = 'QUEUED' and run_after <= :now
                         order by run_after, id
                         limit :limit
                         for update skip locked)
        returning j.id, j.invoice_id, j.attempts, j.max_attempts, j.traceparent""",
        new MapSqlParameterSource("now", now()).addValue("worker", workerId).addValue("limit", limit),
        (rs, i) -> new Job(rs.getLong("id"), rs.getObject("invoice_id", UUID.class), rs.getInt("attempts"),
            rs.getInt("max_attempts"), rs.getString("traceparent")));
  }

  @Transactional
  public void complete(long jobId) {
    finish(jobId, "DONE", null);
  }

  @Transactional
  public void fail(long jobId, String error) {
    finish(jobId, "FAILED", error);
  }

  /** Puts the job back in the queue, due after {@code delay}. */
  @Transactional
  public void retryLater(long jobId, Duration delay, String error) {
    jdbc.update("""
        update processing_jobs set status = 'QUEUED', run_after = :due, locked_at = null, locked_by = null,
               last_error = :error, updated_at = :now where id = :id""",
        new MapSqlParameterSource("id", jobId).addValue("due", Timestamp.from(Instant.now(clock).plus(delay)))
            .addValue("error", truncate(error)).addValue("now", now()));
  }

  public record Released(UUID invoiceId, boolean gaveUp) {}

  /**
   * Jobs RUNNING for longer than {@code after} belong to a worker that died: hand them out again, or give up
   * when the job has used all its attempts (a document that crashes the worker every time).
   */
  @Transactional
  public List<Released> releaseStuck(Duration after) {
    return jdbc.query("""
        update processing_jobs
           set status = case when attempts >= max_attempts then 'FAILED' else 'QUEUED' end,
               run_after = :now, locked_at = null, locked_by = null, last_error = 'worker lost', updated_at = :now
         where status = 'RUNNING' and locked_at < :cutoff
        returning invoice_id, status""",
        new MapSqlParameterSource("now", now()).addValue("cutoff", Timestamp.from(Instant.now(clock).minus(after))),
        (rs, i) -> new Released(rs.getObject("invoice_id", UUID.class), "FAILED".equals(rs.getString("status"))));
  }

  /** Read access for tests and diagnostics. */
  public List<String> statuses(UUID invoiceId) {
    return jdbc.queryForList("select status from processing_jobs where invoice_id = :id order by id",
        new MapSqlParameterSource("id", invoiceId), String.class);
  }

  private void finish(long jobId, String status, String error) {
    jdbc.update("""
        update processing_jobs set status = :status, last_error = :error, locked_at = null, updated_at = :now
         where id = :id""",
        new MapSqlParameterSource("id", jobId).addValue("status", status).addValue("error", truncate(error))
            .addValue("now", now()));
  }

  private Timestamp now() {
    return Timestamp.from(Instant.now(clock));
  }

  private static String truncate(String s) {
    return s == null ? null : (s.length() > 500 ? s.substring(0, 500) : s);
  }
}
