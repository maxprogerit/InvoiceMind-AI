-- Accounting export (Xero / QuickBooks Online bill import CSV). An approved invoice leaves the system once:
-- it is marked with the batch that exported it and is not offered for export again.
CREATE TABLE export_batches (
  id               UUID         PRIMARY KEY,
  organization_id  UUID         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  target           VARCHAR(10)  NOT NULL CHECK (target IN ('XERO', 'QBO')),
  options          JSONB        NOT NULL,   -- account, tax code, date format used, so the file can be rebuilt
  invoice_count    INT          NOT NULL,
  created_by       UUID         REFERENCES users(id),
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_export_batches_org ON export_batches (organization_id, created_at DESC);

ALTER TABLE invoices ADD COLUMN exported_at TIMESTAMPTZ;
ALTER TABLE invoices ADD COLUMN export_batch_id UUID REFERENCES export_batches(id) ON DELETE SET NULL;
CREATE INDEX ix_invoices_unexported ON invoices (organization_id, status) WHERE exported_at IS NULL;
