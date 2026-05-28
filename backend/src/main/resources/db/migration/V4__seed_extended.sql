-- ==================== Additional Vendors ====================
INSERT INTO vendors (organization_id, name, contact_email, payment_terms, risk_score, country, contact_phone, address)
VALUES
  (1, 'Nova Office Supply', 'orders@novaofficelp.com', 'Net 30', 0.06, 'USA', '+1-555-0101', '123 Commerce Ave, Chicago IL'),
  (1, 'Orion Consulting', 'ap@orionconsulting.co', 'Net 45', 0.14, 'USA', '+1-555-0202', '500 Lexington Ave, New York NY'),
  (1, 'Sigma Telecom', 'billing@sigmatelecom.net', 'Net 15', 0.09, 'Germany', '+49-30-555-0303', 'Friedrichstraße 100, Berlin'),
  (1, 'Delta Freight', 'invoices@deltafreight.eu', 'Net 30', 0.12, 'Netherlands', '+31-20-555-0404', 'Herengracht 50, Amsterdam')
ON CONFLICT DO NOTHING;

-- ==================== Invoices ====================
INSERT INTO invoices (organization_id, document_id, vendor_id, invoice_number, total_amount, tax_amount, currency, invoice_date, due_date, status, confidence_score, duplicate_flag, smart_category, payment_status)
SELECT
  1,
  NULL,
  v.id,
  inv_num,
  total,
  total * 0.2,
  'USD',
  CURRENT_DATE - (n * 3),
  CURRENT_DATE + (30 - n * 2),
  status,
  confidence,
  FALSE,
  category,
  pay_status
FROM (VALUES
  ('INV-2026-4012', 12842.51, 'approved', 0.982, 'Operations', 'paid', 2),
  ('INV-2026-4011', 6423.95, 'processing', 0.913, 'Infrastructure', 'unpaid', 3),
  ('INV-2026-4010', 2140.88, 'ready', 0.958, 'Office', 'unpaid', 4),
  ('INV-2026-4009', 22200.00, 'rejected', 0.849, 'Professional Services', 'unpaid', 5),
  ('INV-2026-4008', 4890.25, 'exported', 0.991, 'Utilities', 'paid', 6),
  ('INV-2026-4007', 9312.00, 'approved', 0.976, 'Telecom', 'paid', 7),
  ('INV-2026-4006', 1845.60, 'ready', 0.944, 'Office', 'unpaid', 8),
  ('INV-2026-4005', 33150.00, 'approved', 0.961, 'Logistics', 'paid', 9),
  ('INV-2026-4004', 7200.00, 'processing', 0.887, 'Infrastructure', 'unpaid', 10),
  ('INV-2026-4003', 15500.00, 'exported', 0.993, 'Professional Services', 'paid', 11)
) AS t(inv_num, total, status, confidence, category, pay_status, n)
JOIN vendors v ON v.organization_id = 1
WHERE v.name IN ('Apex Logistics','BytePeak Hosting','Nova Office Supply','Orion Consulting','Helios Energy','Sigma Telecom','Delta Freight')
  AND NOT EXISTS (SELECT 1 FROM invoices i WHERE i.invoice_number = t.inv_num)
LIMIT 10;

-- ==================== Receipts ====================
INSERT INTO receipts (organization_id, vendor_id, merchant_name, total_amount, tax_amount, expense_category, confidence_score, receipt_date, merchant_category, uploaded_by, status)
SELECT 1, v.id, data.merchant, data.total, data.total * 0.1, data.category, data.conf, CURRENT_DATE - data.n, 'Retail', 1, 'processed'
FROM (VALUES
  ('Starbucks Chicago', 48.20, 'Meals & Entertainment', 0.98, 1),
  ('AWS Marketplace', 1240.00, 'Infrastructure', 0.97, 2),
  ('United Airlines', 890.50, 'Travel', 0.94, 3),
  ('Office Depot #412', 320.80, 'Office Supplies', 0.96, 4),
  ('Marriott Downtown', 675.00, 'Travel', 0.92, 5)
) AS data(merchant, total, category, conf, n)
JOIN vendors v ON v.organization_id = 1 AND v.name = 'Nova Office Supply'
WHERE NOT EXISTS (SELECT 1 FROM receipts r WHERE r.merchant_name = data.merchant);

-- ==================== Workflow Executions ====================
INSERT INTO workflow_executions (organization_id, workflow_id, document_id, status, started_at, ended_at, duration_ms, kafka_trace_id, progress_percent)
SELECT 1, w.id, NULL, data.status, NOW() - (data.n * INTERVAL '5 minutes'), NOW() - (data.n * INTERVAL '5 minutes') + (data.dur * INTERVAL '1 millisecond'), data.dur, 'trace-' || gen_random_uuid(), data.prog
FROM (VALUES
  ('completed', 1820, 100, 1),
  ('completed', 931, 100, 2),
  ('failed', 2401, 100, 3),
  ('running', 3200, 67, 4),
  ('completed', 1105, 100, 5),
  ('completed', 875, 100, 6),
  ('queued', 0, 0, 7),
  ('completed', 2210, 100, 8)
) AS data(status, dur, prog, n)
CROSS JOIN (SELECT id FROM workflows WHERE organization_id = 1 LIMIT 1) AS w;

-- ==================== Approvals ====================
INSERT INTO approvals (organization_id, invoice_id, requested_by, approver_id, status, comment, decided_at, ai_recommendation)
SELECT 1, i.id, 1, 2, data.status, data.comment, CASE WHEN data.status != 'pending' THEN NOW() - INTERVAL '1 hour' ELSE NULL END, data.ai_rec
FROM (VALUES
  ('pending', NULL, 'AI suggests approve — low risk vendor, confidence 91.3%'),
  ('pending', NULL, 'High value invoice $22,200 — manual review required'),
  ('approved', 'Approved after reviewing vendor history', 'Auto-approve candidate — confidence 96.1%'),
  ('rejected', 'Duplicate detected — same amount within 7 days', 'Potential duplicate — flag for review')
) AS data(status, comment, ai_rec)
JOIN invoices i ON i.organization_id = 1
WHERE NOT EXISTS (SELECT 1 FROM approvals a WHERE a.organization_id = 1 AND a.invoice_id = i.id)
ORDER BY i.id
LIMIT 4;

-- ==================== Audit Logs ====================
INSERT INTO audit_logs (organization_id, user_id, action, entity_type, entity_id, metadata_json, created_at)
VALUES
  (1, 1, 'DOCUMENT_UPLOADED', 'document', 1, '{"fileName":"invoice_apex_q2.pdf","type":"invoice"}', NOW() - INTERVAL '2 hours'),
  (1, NULL, 'OCR_COMPLETED', 'document', 1, '{"confidence":0.982,"duration_ms":1820}', NOW() - INTERVAL '2 hours' + INTERVAL '2 minutes'),
  (1, 1, 'INVOICE_CREATED', 'invoice', 1, '{"invoiceNumber":"INV-2026-4012","amount":12842.51}', NOW() - INTERVAL '2 hours' + INTERVAL '3 minutes'),
  (1, 2, 'APPROVAL_DECISION', 'approval', 1, '{"decision":"approved","invoice":"INV-2026-4012"}', NOW() - INTERVAL '1 hour 45 minutes'),
  (1, 1, 'DOCUMENT_UPLOADED', 'document', 2, '{"fileName":"bytepeak_may.pdf","type":"invoice"}', NOW() - INTERVAL '1 hour 30 minutes'),
  (1, NULL, 'OCR_STARTED', 'document', 2, '{"traceId":"trace-001"}', NOW() - INTERVAL '1 hour 29 minutes'),
  (1, NULL, 'OCR_COMPLETED', 'document', 2, '{"confidence":0.913,"duration_ms":2401}', NOW() - INTERVAL '1 hour 27 minutes'),
  (1, 1, 'INVOICE_CREATED', 'invoice', 2, '{"invoiceNumber":"INV-2026-4011","amount":6423.95}', NOW() - INTERVAL '1 hour 26 minutes'),
  (1, 1, 'APPROVAL_REQUESTED', 'approval', 2, '{"reason":"low_confidence","threshold":0.92}', NOW() - INTERVAL '1 hour 25 minutes'),
  (1, 1, 'DOCUMENT_UPLOADED', 'document', 3, '{"fileName":"orion_consulting.pdf","type":"invoice"}', NOW() - INTERVAL '1 hour'),
  (1, NULL, 'OCR_COMPLETED', 'document', 3, '{"confidence":0.849,"duration_ms":3100}', NOW() - INTERVAL '58 minutes'),
  (1, 1, 'APPROVAL_REQUESTED', 'approval', 3, '{"reason":"high_amount","amount":22200}', NOW() - INTERVAL '57 minutes'),
  (1, 2, 'APPROVAL_DECISION', 'approval', 3, '{"decision":"rejected","reason":"duplicate"}', NOW() - INTERVAL '45 minutes'),
  (1, 1, 'REPORT_GENERATED', 'report', 1, '{"type":"monthly_invoice_summary","format":"csv"}', NOW() - INTERVAL '30 minutes'),
  (1, 1, 'VENDOR_CREATED', 'vendor', 5, '{"name":"Nova Office Supply","country":"USA"}', NOW() - INTERVAL '3 days'),
  (1, 1, 'WORKFLOW_PUBLISHED', 'workflow', 1, '{"name":"Invoice AP Auto-Approval","version":"v3.2.1"}', NOW() - INTERVAL '5 days'),
  (1, 1, 'EXPORT_COMPLETED', 'invoice', 2, '{"format":"csv","records":8}', NOW() - INTERVAL '20 minutes'),
  (1, 1, 'RECEIPT_UPLOADED', 'receipt', 1, '{"merchant":"Starbucks Chicago","amount":48.20}', NOW() - INTERVAL '4 hours'),
  (1, NULL, 'DUPLICATE_DETECTED', 'invoice', 4, '{"matchedWith":"INV-2026-3992","score":0.97}', NOW() - INTERVAL '10 minutes'),
  (1, 1, 'SETTINGS_UPDATED', 'organization', 1, '{"field":"timezone","value":"UTC"}', NOW() - INTERVAL '2 days');

-- ==================== Instruction Rules ====================
INSERT INTO instruction_rules (organization_id, rule_type, name, pattern, action_config, active)
VALUES
  (1, 'EXTRACTION', 'VAT Field Requirement for EU Vendors', 'vendor.country IN (''DE'',''NL'',''FR'',''IT'',''ES'')', '{"required_fields":["vat_number","tax_amount"],"on_missing":"flag_for_review"}', TRUE),
  (1, 'APPROVAL', 'High Amount Auto-Escalation', 'invoice.totalAmount > 10000', '{"route_to":"finance_manager","priority":"high"}', TRUE),
  (1, 'APPROVAL', 'Low Confidence Manual Review', 'ocr.confidenceScore < 0.90', '{"route_to":"operations_team","reason":"low_ocr_confidence"}', TRUE),
  (1, 'VENDOR_MATCH', 'Telecom Vendor Auto-Route', 'vendor.name CONTAINS ''telecom'' OR invoice.category = ''Telecom''', '{"workflow":"standard_approval","auto_approve_threshold":15000}', TRUE),
  (1, 'DUPLICATE', 'Duplicate Invoice Detection Window', 'invoice.totalAmount = previous.totalAmount AND invoice.vendorId = previous.vendorId WITHIN 7d', '{"action":"flag_duplicate","notify":"finance_team"}', TRUE);

-- ==================== Reports ====================
INSERT INTO reports (organization_id, report_type, format, generated_by, storage_path, created_at, status, date_from, date_to, record_count)
VALUES
  (1, 'monthly_invoice_summary', 'csv', 'admin@invoicemind.ai', '/reports/monthly_may_2026.csv', NOW() - INTERVAL '30 minutes', 'completed', '2026-05-01', '2026-05-28', 10),
  (1, 'vendor_spend_analysis', 'csv', 'admin@invoicemind.ai', '/reports/vendor_spend_q1.csv', NOW() - INTERVAL '1 day', 'completed', '2026-01-01', '2026-03-31', 7),
  (1, 'automation_performance', 'csv', 'admin@invoicemind.ai', '/reports/automation_perf_may.csv', NOW() - INTERVAL '2 days', 'completed', '2026-05-01', '2026-05-27', 150);

-- ==================== AI Insights ====================
INSERT INTO ai_insights (organization_id, insight_type, title, detail, confidence_score)
VALUES
  (1, 'duplicate', 'Potential duplicate invoices detected', '3 invoice pairs share identical amounts within 7 days — review recommended.', 0.94),
  (1, 'cashflow', 'Early payment discount opportunity', 'Apex Logistics offers 2% discount for payment within 10 days — estimated $18K savings.', 0.91)
ON CONFLICT DO NOTHING;
