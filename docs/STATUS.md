# Status

What works, what is unverified and what is not built, with evidence. Updated after the rebuild that followed
the [Phase 0 audit](AUDIT-PHASE0.md) (which found a mock-data frontend, simulated OCR, a failing clean build and
no tenant isolation) and after the optional-feature round (credit notes, currency conversion, webhooks, e-mail
ingestion, single sign-on, S3 storage, tracing, PaddleOCR).

**Legend** — `WORKS` observed working by running it (test name or command given) · `UNVERIFIED` implemented
and tested against fakes, but never run against the real external service in this environment ·
`MISSING` not built (roadmap).

## Verified in this environment

| Check | Result |
|---|---|
| Fresh `git clone` → `cp .env.example .env` → `docker compose up --build --wait` | 7 services healthy, nothing edited by hand (done before the optional-feature round; the stack was rebuilt and started from the current sources many times since, but the clean-clone check itself was not repeated) |
| Backend `mvn verify` | **141 tests, 0 failures** (unit + Testcontainers integration on real PostgreSQL/pgvector, Kafka, Redis; plus a real IMAP server, OIDC server and S3-compatible store) |
| ai-service `pytest` in Docker with real Tesseract + Poppler | **39 tests, 0 failures** (`tests/test_paddle.py` adds PaddleOCR cases, see below) |
| Frontend `eslint`, `tsc`, `vitest` | clean, **28 tests** |
| Playwright against the full stack through Nginx | **11 scenarios pass** (register → upload → live status → correct → approve → analytics → XLSX → knowledge → credit note → settings and exchange rates → webhook → roles → session expiry) |
| Real OCR through the whole pipeline | text PDF read from its text layer (confidence 1.0); PNG scan read by Tesseract (0.93) |
| Behaviour with no OpenAI key | the invoice ends `FAILED` with "OPENAI_API_KEY is not configured"; nothing is invented |

## Features

| Feature | Status | Evidence |
|---|---|---|
| Build from a clean checkout | WORKS | fresh clone → compose up, all healthchecks green |
| Backend / frontend / ai-service builds | WORKS | `mvn package`, `next build`, Docker images |
| Auth: register, login, JWT, refresh rotation, logout | WORKS | `AuthIT` (10): rotation, reuse revokes every session, CSRF header, uniform login failure, garbage token → 401 |
| Roles (USER / APPROVER / ADMIN) enforced | WORKS | `AuthIT.rolesAreEnforcedOnEndpoints`, `WorkflowIT` |
| Tenant isolation | WORKS | `TenancyIT`: other org gets 404 on every endpoint; USER sees only own uploads; analytics/export scoped |
| Upload + real file storage + safe serving | WORKS | `PipelineIT.originalFileIsStoredAndServedBackSafely`, `FileValidatorTest`, `uploadGatekeeping` (disguised exe 415, active PDF 422, >15 MB 413, traversal name sanitised) |
| OCR (text layer, Tesseract fallback, per page) | WORKS | `ai-service/tests/test_ocr.py` with real binaries; run through the stack on a PDF and a scan |
| LLM extraction with OpenAI structured outputs | **UNVERIFIED** | written against the official `responses.parse` + Pydantic `text_format` docs; exercised with a fake client (`test_extract.py`: hallucination, injection, errors, refusal, credit notes). **Never called with a real key here** |
| Prompt-injection defence | WORKS at every layer that can be tested offline | ai-service test with an obeying fake model: detector + grounding force review; backend `PipelineIT.promptInjection...`: cannot be approved until a human corrects it. Resistance of the real model is measured by sample `12-injection` in the evaluation (**pending**) |
| Deterministic validation (Java) | WORKS | `InvoiceValidatorTest` (18), pipeline ITs |
| Credit notes | WORKS (model's classification **UNVERIFIED**) | `PipelineIT` (3): printed with or without a minus sign both stored negative and validated, no duplicate clash with an invoice of the same number, type filter, CSV export, reclassification; `InvoiceValidatorTest`; ai-service tests; browser test. Whether the real model labels `credit_note` correctly is measured by the two new samples (17, 18) in the evaluation (**pending**) |
| Currency conversion | WORKS | `FxIT` (5): conversion at each invoice's date, weekend falls back to Friday, manual rates for a currency the feed lacks, manual beats ECB on the same date, base-currency change, unavailable source degrades to "not converted" and recovers. **Live check:** a USD 1,428.00 invoice dated 2026-03-13 converts to 1,244.34 EUR through the running stack, the same number an independent request to the real Frankfurter API gives (1.1476) |
| Persistence + Flyway migrations | WORKS | V1-V7 applied automatically on start |
| Async processing via Kafka (retry, DLT, idempotent claim, sweeper) | WORKS | `PipelineIT` on a real broker |
| Live status over WebSocket | WORKS | `WebSocketIT` (5): auth in CONNECT, private queues, reviewer topic restricted, no client publish; browser shows "live" through Nginx |
| Approval workflow, four-eyes, comments, audit | WORKS | `WorkflowIT` (7), e2e |
| Manual correction UI (document beside editable fields, highlights, live sums) | WORKS | e2e `a mismatching total is flagged, fixed by a human and then approved` |
| Analytics from the database | WORKS | `AnalyticsExportIT`: exact sums per vendor/month/status, review rate, cache invalidated on approval |
| Export CSV / XLSX / JSON with filters | WORKS | `AnalyticsExportIT` parses the files back (POI, commons-csv); e2e downloads a real XLSX; formula injection neutralised |
| Audit log | WORKS | org-wide filterable log and per-invoice history |
| Notifications (stored + pushed) | WORKS | `WorkflowIT.notificationsCanBeMarkedRead`, `WebSocketIT` |
| Redis: rate limiting and analytics cache | WORKS | `AuthIT.rateLimiterBlocksAfterTheLimit`, cache invalidation in `AnalyticsExportIT` |
| Team management (ADMIN) | WORKS | `AuthIT`, e2e |
| Knowledge base (RAG): ingest, pgvector search, sourced explanation | WORKS with offline embeddings | `KnowledgeIT` (7) on real pgvector: retrieval ranks the right rule, tenant isolation, files/PDF, deletion. **Real OpenAI embeddings and answers: UNVERIFIED** |
| Webhooks for ERP / accounting (signed, retried, logged) | WORKS | `WebhookIT` (5) against a receiver double: signature verifiable with the shown secret, only subscribed events of the own organisation, retries with backoff then FAILED, a 302 is a failure and never followed, manual retry, ping, tenant isolation, admin only, secret never listed again; `UrlGuardTest` (24 cases: loopback, private, link-local/metadata, CGNAT, IPv6, schemes, credentials); e2e. A specific ERP product was **not** connected: the webhook is the generic hook |
| E-mail ingestion (IMAP) | WORKS against a real IMAP server | `EmailIngestIT` (7) on GreenMail: routing by plus-address token, sender must be an enabled user of that organisation, 2 PDFs imported and processed to VALIDATED, rejections logged with reason, no double processing, 5-attachment cap, token rotation, dkim/spf requirement. **Live check:** a mail delivered over SMTP to a GreenMail container became an invoice for the sender within seconds. Not tried against Gmail/Outlook |
| Single sign-on (OpenID Connect) | WORKS against a real OIDC server | `OidcSsoIT` (7) on mock-oauth2-server: discovery, PKCE, signed ID tokens, nonce, state cookie (tampered, missing and replayed attempts refused), account linking and subject pinning, disabled/unverified/no-e-mail/domain cases, provisioning, same-site-only redirect; `OidcServiceTest` (audience, open redirect); login-page component tests. Not tried against Entra ID, Okta or Keycloak |
| S3-compatible object storage | WORKS against SeaweedFS | `S3FileStorageIT` (5: binary round trip, 15 MB, overwrite/delete, bad credentials, missing bucket) and `S3PipelineIT` (upload → OCR → validation → download → delete through the application); compose stack run with `--profile s3` (object in the bucket, local volume empty). Not run against AWS S3 |
| OpenTelemetry tracing | WORKS | `TracingIT` (a `traceparent` sent with the upload reaches the OCR and extraction calls made later by the Kafka consumer), ai-service `test_tracing.py` (continuation, no content in spans, no `/health` spans); live: one Jaeger trace holds the backend's HTTP + Kafka send/receive spans and the ai-service's `POST /ocr`, `ocr.process`, `POST /extract` |
| PaddleOCR engine | **UNVERIFIED** | written (`app/ocr/paddle_engine.py`, optional image build `WITH_PADDLE=1`, `OCR_ENGINE=paddle`), line-grouping logic unit-tested. **Never run to completion here:** an experiment installed it and downloaded the PP-OCRv6 models, but inference failed on CPU with a oneDNN error (worked around with `enable_mkldnn=False`, untested), and the next image build could not finish because Docker had no network. `eval/ocr_compare.py` is ready to produce `docs/OCR-COMPARISON.md` once it builds |
| Frontend on the real API (no mock data) | WORKS | all pages call the backend; e2e |
| Observability: request ids, JSON logs, tokens/cost/time stored | WORKS | visible in logs and on the review page / analytics |
| OpenAPI + typed client | WORKS | Swagger UI at `/swagger-ui.html`; TypeScript types generated from the live spec, CI fails on drift |
| CI workflow | **UNVERIFIED** | `.github/workflows/ci.yml` is written and every command in it was run locally, but it has not run on GitHub (this repository has no remote here) |
| Accuracy evaluation | harness WORKS, **numbers MISSING** | `eval/run_eval.py` ran end to end against the test double (output deliberately not written). 18 samples now (two credit notes). `docs/EVALUATION.md` is produced by the first run with a real key |
| README screenshots | MISSING | `scripts/capture-screenshots.cjs` produces them; they are not committed because screenshots made with the offline extractor would misrepresent extraction quality |

## Defects found and fixed while building the optional features

Kept here because they are the kind of thing only running the system finds:

- A delegating `upload()` overload lost its transaction through self-invocation, so the after-commit Kafka message was
  never sent and invoices stayed `UPLOADED` (found by the browser test).
- `unwrap()` in the frontend replaced every server error message by "Request failed", because the response body had already
  been consumed (found by the webhook browser test).
- `@SpringBootTest` switches tracing off silently; the tracing test needed `@AutoConfigureObservability`.
- The MinIO images are no longer pullable from Docker Hub or quay, so the S3 tests and the bundled store use SeaweedFS.

## Not built (roadmap)

- **Provider-group to role mapping for SSO**, sign-out at the provider.
- **IMAP IDLE** (the mailbox is polled), per-organisation mailboxes and identity providers.
- **Connectors for specific ERP systems**: the webhook is the generic integration point.
- **Distributed tracing of the browser and the database**: spans cover HTTP, Kafka and the AI calls.
- **Scanned credit-note samples** in the evaluation set (the two credit-note samples are text PDFs).
- **The React Flow workflow editor** from the original README was removed rather than wired to nothing; processing is a fixed, tested pipeline.

## Decisions taken

See [ARCHITECTURE.md](ARCHITECTURE.md): Kafka for the processing queue, STOMP for live status, Redis for rate limits and cache, one organisation per registration, pgvector instead of a separate vector database, credit notes stored negative, EUR-pivot rates that never guess, transactional-outbox webhooks with an SSRF guard, OIDC with subject pinning.
