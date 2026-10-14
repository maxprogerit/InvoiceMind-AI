-- E-mail ingestion: invoices sent to  <mailbox>+<token>@<domain>  land in the organisation that owns the token.
ALTER TABLE organizations ADD COLUMN ingest_token VARCHAR(40);
UPDATE organizations SET ingest_token = substr(replace(gen_random_uuid()::text, '-', ''), 1, 24) WHERE ingest_token IS NULL;
ALTER TABLE organizations ALTER COLUMN ingest_token SET NOT NULL;
CREATE UNIQUE INDEX ux_organizations_ingest_token ON organizations (ingest_token);

-- Every message the mailbox poller looked at, whether it produced invoices or not (admin-visible audit of the channel).
CREATE TABLE email_ingest_log (
  id               BIGSERIAL PRIMARY KEY,
  organization_id  UUID REFERENCES organizations(id) ON DELETE CASCADE,  -- NULL when no organisation could be determined
  message_id       VARCHAR(300) NOT NULL UNIQUE,
  from_address     VARCHAR(254),
  subject          VARCHAR(200),
  status           VARCHAR(10)  NOT NULL CHECK (status IN ('ACCEPTED', 'REJECTED')),
  reason           VARCHAR(300),
  invoices_created INT          NOT NULL DEFAULT 0,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_email_ingest_org ON email_ingest_log (organization_id, created_at DESC);
