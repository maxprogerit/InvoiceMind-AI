-- Add tax_amount and payment_status to invoices
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(14,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS payment_status VARCHAR(50) DEFAULT 'unpaid';
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS invoice_date DATE;

-- Add receipt_date and merchant_category to receipts
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS receipt_date DATE;
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS merchant_category VARCHAR(100);
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(14,2) DEFAULT 0;
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS uploaded_by BIGINT REFERENCES users(id);
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'processed';

-- Add progress_percent to workflow_executions
ALTER TABLE workflow_executions ADD COLUMN IF NOT EXISTS progress_percent INT NOT NULL DEFAULT 0;
ALTER TABLE workflow_executions ADD COLUMN IF NOT EXISTS logs_json TEXT;
ALTER TABLE workflow_executions ADD COLUMN IF NOT EXISTS error_message VARCHAR(500);

-- Instruction rules table
CREATE TABLE IF NOT EXISTS instruction_rules (
  id BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES organizations(id),
  rule_type VARCHAR(100) NOT NULL,
  name VARCHAR(255) NOT NULL,
  pattern TEXT NOT NULL,
  action_config TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Add full_name and contact_phone to vendors
ALTER TABLE vendors ADD COLUMN IF NOT EXISTS country VARCHAR(100);
ALTER TABLE vendors ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(50);
ALTER TABLE vendors ADD COLUMN IF NOT EXISTS address TEXT;

-- Report storage_path and status
ALTER TABLE reports ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'completed';
ALTER TABLE reports ADD COLUMN IF NOT EXISTS date_from DATE;
ALTER TABLE reports ADD COLUMN IF NOT EXISTS date_to DATE;
ALTER TABLE reports ADD COLUMN IF NOT EXISTS record_count INT DEFAULT 0;

-- Workflow status and created_at
ALTER TABLE workflows ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'active';
ALTER TABLE workflows ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();

-- Documents confidence_score and document_type
ALTER TABLE documents ADD COLUMN IF NOT EXISTS confidence_score NUMERIC(6,4);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS document_type VARCHAR(50);

-- Receipts vendor_id
ALTER TABLE receipts ADD COLUMN IF NOT EXISTS vendor_id BIGINT REFERENCES vendors(id);
