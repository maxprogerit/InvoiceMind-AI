-- Credit notes: a document type next to the invoice. Amounts of a credit note are stored negative.
ALTER TABLE invoices
  ADD COLUMN document_type VARCHAR(12) NOT NULL DEFAULT 'INVOICE'
  CHECK (document_type IN ('INVOICE', 'CREDIT_NOTE'));

CREATE INDEX ix_invoices_org_type ON invoices (organization_id, document_type);
