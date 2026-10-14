# Status

What works, what is unverified and what is not built — with evidence. Updated after the rebuild that followed
the [Phase 0 audit](AUDIT-PHASE0.md) (which found a mock-data frontend, simulated OCR, a failing clean build and
no tenant isolation).

**Legend** — `WORKS` observed working by running it (test name or command given) · `UNVERIFIED` implemented
and tested against fakes, but never run against the real external service in this environment ·
`MISSING` not built (roadmap).

## Verified in this environment

| Check | Result |
|---|---|
| Fresh `git clone` → `cp .env.example .env` → `docker compose up --build --wait` | 7 services healthy, nothing edited by hand (this check found and fixed a hidden `storage` package that `.gitignore` had swallowed) |
| Backend `mvn test` | **85 tests, 0 failures** (unit + Testcontainers integration on real PostgreSQL/pgvector, Kafka, Redis) |
| ai-service `pytest` in Docker with real Tesseract + Poppler | **32 tests, 0 failures** |
| Frontend `eslint`, `tsc`, `vitest` | clean, **23 tests** |
| Playwright against the full stack through Nginx | **8 scenarios pass** (register → upload → live status → correct → approve → analytics → XLSX → knowledge → roles → session expiry) |
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
| LLM extraction with OpenAI structured outputs | **UNVERIFIED** | written against the official `responses.parse` + Pydantic `text_format` docs; exercised with a fake client (`test_extract.py`: hallucination, injection, errors, refusal). **Never called with a real key here** |
| Prompt-injection defence | WORKS at every layer that can be tested offline | ai-service test with an obeying fake model: detector + grounding force review; backend `PipelineIT.promptInjection...`: cannot be approved until a human corrects it. Resistance of the real model is measured by sample `12-injection` in the evaluation (**pending**) |
| Deterministic validation (Java) | WORKS | `InvoiceValidatorTest` (14), pipeline ITs |
| Persistence + Flyway migrations | WORKS | V1 baseline, V2 knowledge base; applied automatically on start |
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
| Frontend on the real API (no mock data) | WORKS | all pages call the backend; e2e |
| Observability: request ids, JSON logs, tokens/cost/time stored | WORKS | visible in logs and on the review page / analytics |
| OpenAPI + typed client | WORKS | Swagger UI at `/swagger-ui.html`; TypeScript types generated from the live spec, CI fails on drift |
| CI workflow | **UNVERIFIED** | `.github/workflows/ci.yml` is written and every command in it was run locally, but it has not run on GitHub (this repository has no remote here) |
| Accuracy evaluation | harness WORKS, **numbers MISSING** | `eval/run_eval.py` ran end to end against the test double (output deliberately not written). `docs/EVALUATION.md` is produced by the first run with a real key |
| README screenshots | MISSING | `scripts/capture-screenshots.cjs` produces them; they are not committed because screenshots made with the offline extractor would misrepresent extraction quality |

## Not built (roadmap)

- **PaddleOCR**: `app/ocr/base.py` is the interface; only Tesseract is implemented.
- **Credit notes / negative totals**: an invoice with a total of zero or less is rejected by validation.
- **Currency conversion**: spend is reported per currency and never summed across currencies.
- **E-mail ingestion, ERP/accounting integrations, SSO.**
- **Object storage (S3) and horizontal scaling of file storage**: files live on a volume.
- **Distributed tracing (OpenTelemetry)**: request ids and structured logs only.
- **The React Flow workflow editor** from the original README was removed rather than wired to nothing; processing is a fixed, tested pipeline.

## Decisions taken

See [ARCHITECTURE.md](ARCHITECTURE.md): Kafka for the processing queue, STOMP for live status, Redis for rate limits and cache, one organisation per registration, pgvector instead of a separate vector database.
