-- Intake: where an invoice came from, and who may send invoices by e-mail.
ALTER TABLE invoices ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'WEB' CHECK (source IN ('WEB', 'EMAIL', 'BATCH'));
ALTER TABLE invoices ADD COLUMN source_email VARCHAR(254); -- the sender, for invoices that arrived by e-mail

-- Invoices sent by people who are not users (vendors mailing invoices@) are attributed to a per-organisation
-- service account that cannot sign in and is not part of the team.
ALTER TABLE users ADD COLUMN system_account BOOLEAN NOT NULL DEFAULT false;

-- ANYONE: any sender (typically vendors); USERS: only users of the organisation. Organisations that existed before
-- keep the stricter behaviour they had; new ones accept vendor mail.
ALTER TABLE organizations ADD COLUMN ingest_accept_from VARCHAR(10) NOT NULL DEFAULT 'ANYONE'
  CHECK (ingest_accept_from IN ('ANYONE', 'USERS'));
UPDATE organizations SET ingest_accept_from = 'USERS';

-- A message is claimed (PROCESSING) before its attachments are stored, so a crash can never import it twice.
ALTER TABLE email_ingest_log DROP CONSTRAINT IF EXISTS email_ingest_log_status_check;
ALTER TABLE email_ingest_log ADD CONSTRAINT email_ingest_log_status_check
  CHECK (status IN ('PROCESSING', 'ACCEPTED', 'REJECTED'));
