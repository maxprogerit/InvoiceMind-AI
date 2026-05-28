CREATE TABLE organizations (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  plan VARCHAR(50) NOT NULL,
  timezone VARCHAR(50) NOT NULL
);

CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES organizations(id),
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(255) NOT NULL,
  role VARCHAR(50) NOT NULL
);

CREATE TABLE documents (
  id BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES organizations(id),
  uploaded_by BIGINT NOT NULL REFERENCES users(id),
  file_name VARCHAR(255) NOT NULL,
  file_type VARCHAR(100) NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  processing_status VARCHAR(50) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE vendors (
  id BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES organizations(id),
  name VARCHAR(255) NOT NULL,
  contact_email VARCHAR(255),
  payment_terms VARCHAR(100),
  risk_score NUMERIC(8,4) NOT NULL DEFAULT 0.0
);

CREATE TABLE invoices (
  id BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES organizations(id),
  document_id BIGINT REFERENCES documents(id),
  vendor_id BIGINT REFERENCES vendors(id),
  invoice_number VARCHAR(100) UNIQUE NOT NULL,
  total_amount NUMERIC(14,2) NOT NULL,
  currency VARCHAR(10) NOT NULL,
  invoice_date DATE,
  due_date DATE,
  status VARCHAR(50) NOT NULL,
  confidence_score NUMERIC(6,4),
  duplicate_flag BOOLEAN NOT NULL DEFAULT FALSE,
  smart_category VARCHAR(100)
);

CREATE TABLE receipts (
  id BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES organizations(id),
  document_id BIGINT REFERENCES documents(id),
  merchant_name VARCHAR(255),
  total_amount NUMERIC(14,2),
  expense_category VARCHAR(100),
  confidence_score NUMERIC(6,4)
);

CREATE TABLE workflows (
  id BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES organizations(id),
  name VARCHAR(255) NOT NULL,
  version VARCHAR(50) NOT NULL,
  definition_json TEXT NOT NULL,
  published BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE workflow_executions (
  id BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES organizations(id),
  workflow_id BIGINT REFERENCES workflows(id),
  document_id BIGINT REFERENCES documents(id),
  status VARCHAR(50) NOT NULL,
  started_at TIMESTAMP NOT NULL DEFAULT NOW(),
  ended_at TIMESTAMP,
  duration_ms BIGINT,
  kafka_trace_id VARCHAR(100)
);

CREATE TABLE approvals (
  id BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES organizations(id),
  invoice_id BIGINT REFERENCES invoices(id),
  requested_by BIGINT REFERENCES users(id),
  approver_id BIGINT REFERENCES users(id),
  status VARCHAR(50) NOT NULL,
  comment TEXT,
  decided_at TIMESTAMP,
  ai_recommendation VARCHAR(255)
);

CREATE TABLE reports (
  id BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES organizations(id),
  report_type VARCHAR(100) NOT NULL,
  format VARCHAR(20) NOT NULL,
  generated_by VARCHAR(255) NOT NULL,
  storage_path VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE notifications (
  id BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES organizations(id),
  user_id BIGINT REFERENCES users(id),
  title VARCHAR(255) NOT NULL,
  body TEXT NOT NULL,
  read_flag BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_logs (
  id BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES organizations(id),
  user_id BIGINT REFERENCES users(id),
  action VARCHAR(100) NOT NULL,
  entity_type VARCHAR(100) NOT NULL,
  entity_id BIGINT,
  metadata_json TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE ai_insights (
  id BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES organizations(id),
  insight_type VARCHAR(100) NOT NULL,
  title VARCHAR(255) NOT NULL,
  detail TEXT NOT NULL,
  confidence_score NUMERIC(6,4),
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE processing_results (
  id BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL REFERENCES organizations(id),
  document_id BIGINT REFERENCES documents(id),
  extraction_json TEXT,
  confidence_score NUMERIC(6,4),
  anomaly_score NUMERIC(6,4),
  status VARCHAR(50) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
