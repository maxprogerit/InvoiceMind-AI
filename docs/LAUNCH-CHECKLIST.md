# Launch checklist (manual steps)

Things the code cannot do for you. Tick them in order.

## 1. Measure with the real model

- [ ] Put `OPENAI_API_KEY`, `OPENAI_MODEL` and the model's real prices (`OPENAI_PRICE_INPUT_PER_1M`,
      `OPENAI_PRICE_OUTPUT_PER_1M`, USD per 1M tokens, from the provider's price page) in `.env`.
- [ ] `EVAL_MAX_COST_USD=1.00 docker compose --profile eval run --rm eval` → commit `docs/evaluation.md` and
      `eval/results/latest.json`.
- [ ] Copy the headline numbers into the *Evaluation* table at the top of the README (replace "not run yet").
- [ ] Add real invoices with personal data blacked out to `samples/real/` (see its README), re-run, commit.
- [ ] `LIVE_OPENAI_SMOKE=1 pytest tests/test_live_smoke.py` in `extract-service/` once.

## 2. Check the accounting files in real accounts

- [ ] Xero: *Business → Bills to pay → Import*, download the template, compare its header row with
      `docs/ACCOUNTING-EXPORT.md`; import one exported file into a demo company.
- [ ] QuickBooks Online: *Settings → Import data → Bills*, import one exported file, map the columns.
- [ ] Fix the export (and the doc's "not verified" note) if anything differs.

## 3. Deploy

- [ ] A VPS with Docker (2 vCPU / 4 GB is enough for a demo), a domain, TLS in front of Nginx (Caddy or a
      load balancer); set `APP_COOKIES_SECURE: "true"` in `docker-compose.yml`, and strong `JWT_SECRET`,
      `EXTRACT_SERVICE_TOKEN` and `POSTGRES_PASSWORD` in `.env`.
- [ ] Empty `DEMO_PASSWORD` for a real organisation, or keep a separate public demo instance with it set.
- [ ] A mailbox with plus-addressing for e-mail intake (`MAIL_*`), SMTP for reminders (`SMTP_*`).
- [ ] Backups of the `postgres_data` and `storage_data` volumes.
- [x] CI ran green on GitHub for PR #1 (backend, extract-service, frontend, end-to-end).
- [ ] GitGuardian: mark incident 38111022 (dev default password in the imported InvoiceExtract history) as a false positive / test credential in its dashboard.

## 4. Screenshots

- [ ] With the real model configured, re-run `node scripts/demo-flow.cjs https://<your-domain>` and commit
      `docs/screenshots/flow-*.png`; then drop the "offline test double" note under the README screenshots.
- [ ] Optionally record the same flow as a GIF (e.g. Peek / ScreenToGif) for the portfolio card.

## 5. Portfolio

- [ ] Upwork portfolio card for InvoiceMind (text below), with the four flow screenshots and the repository link.
- [ ] Archive the old InvoiceExtract card (or point it to InvoiceMind) and archive the InvoiceExtract repository
      on GitHub; its README already says it now lives in `extract-service/`.

### Upwork card text (draft)

**Title:** InvoiceMind: accounts-payable automation with AI extraction and code-checked numbers

**Description:**
Suppliers e-mail invoices to a company address or staff upload them (also as a zip). OCR and an LLM extract the
data; plain Java code then checks every sum, date and currency, detects duplicate and possible-duplicate bills, and
sends only the failed checks to a person. Corrections are remembered per vendor. Approval rules per currency
(automatic below a limit, one or two approvers above), daily due-date reminders, and an export to Xero or QuickBooks
that hands each invoice over exactly once.

Stack: Spring Boot 3 / Java 21, PostgreSQL (with a SKIP LOCKED job queue and pgvector), Python FastAPI service for
OCR and extraction, Next.js 15, Docker Compose. Tested with JUnit + Testcontainers, pytest and Playwright; accuracy
measured on a 32-document evaluation set with a spend cap.

**Role:** sole developer (architecture, backend, AI service, frontend, tests).
**Skills:** Java, Spring Boot, PostgreSQL, Python, FastAPI, LLM, OCR, Next.js, Docker.
