-- Outbound webhooks: signed HTTP callbacks on invoice events (the integration point for ERP / accounting systems).
-- Deliveries are written in the same transaction as the change that caused them (transactional outbox),
-- then sent by a background dispatcher with retries.

CREATE TABLE webhook_endpoints (
  id               UUID PRIMARY KEY,
  organization_id  UUID          NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  url              VARCHAR(500)  NOT NULL,
  description      VARCHAR(200),
  secret           VARCHAR(100)  NOT NULL,   -- HMAC signing key, shown to the admin once
  events           VARCHAR(300)  NOT NULL,   -- comma separated event types
  enabled          BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_webhook_endpoints_org ON webhook_endpoints (organization_id);

CREATE TABLE webhook_deliveries (
  id               UUID PRIMARY KEY,
  endpoint_id      UUID          NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
  organization_id  UUID          NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  event_id         UUID          NOT NULL,
  event_type       VARCHAR(40)   NOT NULL,
  invoice_id       UUID,
  payload          JSONB         NOT NULL,
  status           VARCHAR(10)   NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
  attempts         INT           NOT NULL DEFAULT 0,
  next_attempt_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
  last_status_code INT,
  last_error       VARCHAR(300),
  created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
  delivered_at     TIMESTAMPTZ
);
CREATE INDEX ix_webhook_deliveries_due ON webhook_deliveries (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX ix_webhook_deliveries_endpoint ON webhook_deliveries (endpoint_id, created_at DESC);
