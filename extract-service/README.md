# extract-service

The internal AI service of InvoiceMind: **file in, validated structured JSON out.** It has no database and no UI;
the Spring Boot backend owns files, results, users and history, and is the only caller.

```
POST /invoices  (multipart "file", header X-Internal-Token)
  -> text: PDF text layer (pdfplumber) or Tesseract OCR per page        "source": text_layer | ocr | mixed
  -> OpenAI Responses API with Structured Outputs (Pydantic schema, no tools)
  -> grounding (every key value must appear in the text) + prompt-injection check
  -> deterministic validation in plain Python + Decimal (line math, subtotal, total, dates, currency)
  <- { source, ocr, extraction, validation: {status, issues}, injection_suspected, usage }
POST /embed, POST /explain   knowledge base (RAG) helpers
GET  /health                 no token
```

- **Money is `Decimal`**, sent as JSON strings (`"1190.00"`), never JSON numbers; the schema handed to the model asks
  for strings too. Java reads them into `BigDecimal` exactly.
- **The model is never trusted**: the backend re-validates every number in Java and decides the invoice status;
  this service's own validation is a first pass and what the evaluation measures.
- **Internal only**: no published port in docker compose, reachable only on the `extract` Docker network, and every
  endpoint except `/health` answers 401 without `EXTRACT_SERVICE_TOKEN`.
- Errors: `413/415/422` for the file itself (the backend does not retry them), `503 llm_not_configured` without a key
  (nothing is invented), `502/504` for model failures (retried by the backend with backoff).

## Where it came from

This directory was imported from the standalone **InvoiceExtract** project with `git subtree add`, so its history is
part of this repository. It was then merged with InvoiceMind's former `ai-service`: the stateless service layout,
grounding, injection detection, credit notes and the RAG endpoints come from `ai-service`; the `Decimal` money type
and the deterministic validator (`app/validation.py`, with its tests) come from InvoiceExtract. InvoiceExtract's own
database, React UI and file storage were dropped: the backend does that job.

## Tests

```bash
docker build --target test -t im-extract-test . && docker run --rm im-extract-test     # real Tesseract + Poppler
LIVE_OPENAI_SMOKE=1 OPENAI_API_KEY=sk-... pytest -q tests/test_live_smoke.py           # one real API call
```

The model is a fake in every test except `test_live_smoke.py`.
