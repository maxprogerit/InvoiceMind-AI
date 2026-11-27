-- Vendors become master data: a tax/VAT id, a default currency, aliases, and a verification status.
-- Duplicates are recognised by meaning (vendor + number, or vendor + total + date), not only by file hash.

-- Two companies can share a name; the tax id tells them apart, so the name is no longer unique on its own.
ALTER TABLE vendors DROP CONSTRAINT IF EXISTS vendors_organization_id_normalized_name_key;
CREATE INDEX ix_vendors_name ON vendors (organization_id, normalized_name);

ALTER TABLE vendors ADD COLUMN tax_id           VARCHAR(50);    -- normalised: upper-case letters and digits only
ALTER TABLE vendors ADD COLUMN default_currency VARCHAR(3);
-- Every vendor so far was created automatically from model output, so none of them has been confirmed by a person.
ALTER TABLE vendors ADD COLUMN status           VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED'
  CHECK (status IN ('UNVERIFIED', 'VERIFIED'));
ALTER TABLE vendors ADD COLUMN verified_by      UUID REFERENCES users(id);
ALTER TABLE vendors ADD COLUMN verified_at      TIMESTAMPTZ;
CREATE UNIQUE INDEX ux_vendors_tax_id ON vendors (organization_id, tax_id) WHERE tax_id IS NOT NULL;

CREATE TABLE vendor_aliases (
  id               BIGSERIAL    PRIMARY KEY,
  vendor_id        UUID         NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
  organization_id  UUID         NOT NULL REFERENCES organizations(id),
  alias            VARCHAR(300) NOT NULL,
  normalized_alias VARCHAR(300) NOT NULL,
  created_by       UUID         REFERENCES users(id),
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  UNIQUE (organization_id, normalized_alias)
);
CREATE INDEX ix_vendor_aliases_vendor ON vendor_aliases (vendor_id);

ALTER TABLE invoices ADD COLUMN vendor_tax_id VARCHAR(50);
-- NONE, DUPLICATE (same vendor + number, or an identical file: approval blocked) or POSSIBLE_DUPLICATE
-- (same vendor + total + date close by, different number: a person decides).
ALTER TABLE invoices ADD COLUMN duplicate_status VARCHAR(24) NOT NULL DEFAULT 'NONE'
  CHECK (duplicate_status IN ('NONE', 'DUPLICATE', 'POSSIBLE_DUPLICATE'));
ALTER TABLE invoices ADD COLUMN duplicate_of UUID REFERENCES invoices(id) ON DELETE SET NULL;
-- A person decided that a possible duplicate is a separate invoice; the rule then stays quiet for it.
ALTER TABLE invoices ADD COLUMN duplicate_cleared_by UUID REFERENCES users(id);
ALTER TABLE invoices ADD COLUMN duplicate_cleared_at TIMESTAMPTZ;
-- The extraction was copied from an earlier invoice with the same file (no second model call).
ALTER TABLE invoices ADD COLUMN reused_from UUID REFERENCES invoices(id) ON DELETE SET NULL;

CREATE INDEX ix_invoices_possible_dup ON invoices (organization_id, vendor_id, total, invoice_date);

-- Which earlier invoice a duplicate finding points to (shown as a link in the UI).
ALTER TABLE validation_issues ADD COLUMN related_invoice_id UUID REFERENCES invoices(id) ON DELETE SET NULL;
