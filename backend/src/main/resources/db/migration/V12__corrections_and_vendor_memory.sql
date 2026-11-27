-- Every human correction, one row per changed field (who, when, before, after).
CREATE TABLE invoice_corrections (
  id               BIGSERIAL     PRIMARY KEY,
  invoice_id       UUID          NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  organization_id  UUID          NOT NULL REFERENCES organizations(id),
  vendor_id        UUID          REFERENCES vendors(id) ON DELETE SET NULL,
  field            VARCHAR(50)   NOT NULL,
  old_value        VARCHAR(1000),
  new_value        VARCHAR(1000),
  user_id          UUID          REFERENCES users(id),
  created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_corrections_invoice ON invoice_corrections (invoice_id, created_at);
CREATE INDEX ix_corrections_vendor  ON invoice_corrections (vendor_id, field, created_at);

-- Vendor memory: deterministic defaults learned from those corrections (not model training).
--   currency          when the model read `when_extracted` (or nothing, NULL) for this vendor, use `value`
--   payment_terms     due date = invoice date + `value` days when the document prints no due date
--   category          expense category to pre-fill when none is set
CREATE TABLE vendor_memory (
  id                    BIGSERIAL    PRIMARY KEY,
  vendor_id             UUID         NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
  organization_id       UUID         NOT NULL REFERENCES organizations(id),
  field                 VARCHAR(30)  NOT NULL CHECK (field IN ('CURRENCY', 'PAYMENT_TERMS', 'CATEGORY')),
  when_extracted        VARCHAR(100),
  value                 VARCHAR(100) NOT NULL,
  learned_from_invoice  UUID         REFERENCES invoices(id) ON DELETE SET NULL,
  learned_by            UUID         REFERENCES users(id),
  learned_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
  times_applied         INT          NOT NULL DEFAULT 0,
  UNIQUE NULLS NOT DISTINCT (vendor_id, field, when_extracted)
);
CREATE INDEX ix_vendor_memory_vendor ON vendor_memory (vendor_id);

ALTER TABLE vendors ADD COLUMN payment_terms_days INT CHECK (payment_terms_days BETWEEN 0 AND 365);
ALTER TABLE vendors ADD COLUMN default_category  VARCHAR(100);

ALTER TABLE invoices ADD COLUMN expense_category VARCHAR(100);
-- What vendor memory filled in on this invoice: [{field, value, extracted, memoryId}], shown in the UI.
ALTER TABLE invoices ADD COLUMN memory_applied JSONB;
CREATE INDEX ix_invoices_due ON invoices (organization_id, due_date);
