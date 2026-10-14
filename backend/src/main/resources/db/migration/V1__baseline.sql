-- InvoiceMind AI baseline schema.
-- The earlier scaffold schema (fake OCR, hardcoded org) was never deployed anywhere,
-- so the baseline was reset instead of stacking migrations on top of it.

CREATE TABLE organizations (
  id          UUID PRIMARY KEY,
  name        VARCHAR(200) NOT NULL,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
  id               UUID PRIMARY KEY,
  organization_id  UUID         NOT NULL REFERENCES organizations(id),
  email            VARCHAR(254) NOT NULL,
  password_hash    VARCHAR(100) NOT NULL,
  full_name        VARCHAR(200) NOT NULL,
  role             VARCHAR(20)  NOT NULL CHECK (role IN ('USER', 'APPROVER', 'ADMIN')),
  enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));
CREATE INDEX ix_users_org ON users (organization_id);

CREATE TABLE refresh_tokens (
  id          UUID PRIMARY KEY,
  user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  VARCHAR(64) NOT NULL UNIQUE,
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_user ON refresh_tokens (user_id);

CREATE TABLE vendors (
  id               UUID PRIMARY KEY,
  organization_id  UUID         NOT NULL REFERENCES organizations(id),
  name             VARCHAR(300) NOT NULL,
  normalized_name  VARCHAR(300) NOT NULL,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  UNIQUE (organization_id, normalized_name)
);

CREATE TABLE invoices (
  id                     UUID PRIMARY KEY,
  organization_id        UUID         NOT NULL REFERENCES organizations(id),
  uploaded_by            UUID         NOT NULL REFERENCES users(id),
  vendor_id              UUID         REFERENCES vendors(id),
  status                 VARCHAR(20)  NOT NULL
      CHECK (status IN ('UPLOADED','PROCESSING','EXTRACTED','NEEDS_REVIEW','VALIDATED','APPROVED','REJECTED','FAILED')),

  original_filename      VARCHAR(255) NOT NULL,
  storage_key            VARCHAR(300) NOT NULL,
  content_type           VARCHAR(100) NOT NULL,
  file_size              BIGINT       NOT NULL,
  file_sha256            VARCHAR(64)  NOT NULL,

  vendor_name            VARCHAR(300),
  invoice_number         VARCHAR(100),
  invoice_date           DATE,
  due_date               DATE,
  currency               VARCHAR(3),
  subtotal               NUMERIC(14,2),
  tax                    NUMERIC(14,2),
  total                  NUMERIC(14,2),

  field_confidence       JSONB,
  model_needs_review     BOOLEAN      NOT NULL DEFAULT FALSE,
  model_review_reasons   JSONB,
  injection_suspected    BOOLEAN      NOT NULL DEFAULT FALSE,
  was_flagged            BOOLEAN      NOT NULL DEFAULT FALSE,
  human_reviewed         BOOLEAN      NOT NULL DEFAULT FALSE,

  ocr_text               TEXT,
  ocr_engine             VARCHAR(50),
  ocr_confidence         NUMERIC(5,4),
  ocr_page_count         INT,
  extraction_json        JSONB,
  llm_model              VARCHAR(100),
  input_tokens           INT,
  output_tokens          INT,
  cost_usd               NUMERIC(12,6),

  failure_reason         VARCHAR(500),
  processing_started_at  TIMESTAMPTZ,
  processing_finished_at TIMESTAMPTZ,
  processing_ms          BIGINT,

  decided_by             UUID REFERENCES users(id),
  decided_at             TIMESTAMPTZ,
  decision_comment       TEXT,

  created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
  version                BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_invoices_org_created ON invoices (organization_id, created_at DESC);
CREATE INDEX ix_invoices_org_status  ON invoices (organization_id, status);
CREATE INDEX ix_invoices_uploader    ON invoices (uploaded_by);
CREATE INDEX ix_invoices_dup         ON invoices (organization_id, vendor_id, invoice_number);
CREATE INDEX ix_invoices_sha         ON invoices (organization_id, file_sha256);

CREATE TABLE invoice_line_items (
  id           UUID PRIMARY KEY,
  invoice_id   UUID          NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  position     INT           NOT NULL,
  description  VARCHAR(1000) NOT NULL,
  quantity     NUMERIC(14,4),
  unit_price   NUMERIC(14,4),
  total        NUMERIC(14,2)
);
CREATE INDEX ix_items_invoice ON invoice_line_items (invoice_id);

CREATE TABLE validation_issues (
  id          UUID PRIMARY KEY,
  invoice_id  UUID         NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  code        VARCHAR(50)  NOT NULL,
  severity    VARCHAR(10)  NOT NULL CHECK (severity IN ('ERROR','WARNING')),
  field       VARCHAR(50),
  message     VARCHAR(500) NOT NULL,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_issues_invoice ON validation_issues (invoice_id);

CREATE TABLE audit_logs (
  id               BIGSERIAL PRIMARY KEY,
  organization_id  UUID         NOT NULL REFERENCES organizations(id),
  user_id          UUID         REFERENCES users(id) ON DELETE SET NULL,
  action           VARCHAR(60)  NOT NULL,
  entity_type      VARCHAR(40)  NOT NULL,
  entity_id        UUID,
  details          JSONB,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_org_created ON audit_logs (organization_id, created_at DESC);
CREATE INDEX ix_audit_entity      ON audit_logs (entity_type, entity_id);

CREATE TABLE notifications (
  id               UUID PRIMARY KEY,
  organization_id  UUID         NOT NULL REFERENCES organizations(id),
  user_id          UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type             VARCHAR(40)  NOT NULL,
  title            VARCHAR(200) NOT NULL,
  body             VARCHAR(500) NOT NULL,
  invoice_id       UUID         REFERENCES invoices(id) ON DELETE CASCADE,
  read_at          TIMESTAMPTZ,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
