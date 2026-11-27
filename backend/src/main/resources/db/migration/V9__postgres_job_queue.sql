-- Kafka and Redis are replaced by PostgreSQL at this scale.
--
-- processing_jobs: the work queue between "file stored" and "extracted". Workers claim rows with
-- SELECT ... FOR UPDATE SKIP LOCKED, so several backend instances can poll the same table without
-- double-processing. The row is inserted in the same transaction as the invoice (no lost messages,
-- no publish-after-commit window), and retries are a run_after in the future.
CREATE TABLE processing_jobs (
  id            BIGSERIAL    PRIMARY KEY,
  invoice_id    UUID         NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  status        VARCHAR(16)  NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
  attempts      INT          NOT NULL DEFAULT 0,
  max_attempts  INT          NOT NULL DEFAULT 5,
  run_after     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  locked_at     TIMESTAMPTZ,
  locked_by     VARCHAR(100),
  last_error    VARCHAR(500),
  traceparent   VARCHAR(100), -- W3C trace context of the request that queued the job
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- at most one open job per invoice
CREATE UNIQUE INDEX ux_processing_jobs_open ON processing_jobs (invoice_id) WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX ix_processing_jobs_due ON processing_jobs (run_after) WHERE status = 'QUEUED';
CREATE INDEX ix_processing_jobs_running ON processing_jobs (locked_at) WHERE status = 'RUNNING';

-- Fixed-window rate-limit counters (were Redis INCR + EXPIRE). Old windows are deleted by a scheduled job.
CREATE TABLE rate_limit_counters (
  bucket_key    VARCHAR(400) NOT NULL,
  window_start  TIMESTAMPTZ  NOT NULL,
  hits          INT          NOT NULL,
  PRIMARY KEY (bucket_key, window_start)
);
CREATE INDEX ix_rate_limit_window ON rate_limit_counters (window_start);

-- Invoices that were waiting in Kafka when this migration runs get a job, so nothing is stranded.
INSERT INTO processing_jobs (invoice_id) SELECT id FROM invoices WHERE status = 'UPLOADED';
