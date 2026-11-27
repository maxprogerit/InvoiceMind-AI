-- POST /invoices on the extract-service returns how the text was read and its own first-pass validation.
ALTER TABLE invoices ADD COLUMN text_source VARCHAR(16)
  CHECK (text_source IS NULL OR text_source IN ('text_layer', 'ocr', 'mixed'));
ALTER TABLE invoices ADD COLUMN extract_validation JSONB;
