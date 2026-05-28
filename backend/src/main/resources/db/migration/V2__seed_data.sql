INSERT INTO organizations (id, name, plan, timezone)
VALUES (1, 'InvoiceMind Global', 'enterprise', 'UTC')
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, organization_id, email, password_hash, full_name, role)
VALUES
  (1, 1, 'admin@invoicemind.ai', '$2a$10$zQ9Ky8N0wWj8xM2zjzyx9.uAgndfNf4hOXQx2v10yV4B8jlyxnFM2', 'Maxim Admin', 'ROLE_ADMIN'),
  (2, 1, 'approver@invoicemind.ai', '$2a$10$zQ9Ky8N0wWj8xM2zjzyx9.uAgndfNf4hOXQx2v10yV4B8jlyxnFM2', 'Finance Approver', 'ROLE_APPROVER')
ON CONFLICT (id) DO NOTHING;

INSERT INTO vendors (organization_id, name, contact_email, payment_terms, risk_score)
VALUES
  (1, 'Apex Logistics', 'ap@apexlogistics.io', 'Net 30', 0.08),
  (1, 'BytePeak Hosting', 'billing@bytepeak.io', 'Net 15', 0.11),
  (1, 'Helios Energy', 'finance@helios-energy.com', 'Net 30', 0.05);

INSERT INTO workflows (organization_id, name, version, definition_json, published)
VALUES
  (1, 'Invoice AP Auto-Approval', 'v3.2.1', '{"nodes":["upload","ocr","validate","approve","export"]}', true),
  (1, 'Duplicate Detection', 'v1.9.4', '{"nodes":["upload","ocr","dedupe","review"]}', true);

INSERT INTO ai_insights (organization_id, insight_type, title, detail, confidence_score)
VALUES
  (1, 'optimization', 'Vendors with delayed terms increasing', 'Renegotiate payment terms for 3 top vendors to recover ~4.2% cashflow.', 0.96),
  (1, 'anomaly', 'Potential duplicate invoices', '6 invoices have fuzzy-match score over 0.94 in the last 24h.', 0.93),
  (1, 'forecast', 'Automation savings trend positive', 'Projected monthly AP processing savings estimated at $248K.', 0.97);
