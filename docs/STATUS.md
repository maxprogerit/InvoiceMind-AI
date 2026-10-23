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
| Fresh `git clone` → `cp .env.example .env` → `docker compose up --build --wait` | 7 services healthy, nothing edited by hand (done before the optional-feature round). After it, the stack was rebuilt from scratch (`down -v`, `up --build --wait`) from the current working tree: 7 healthy, 11 browser scenarios pass; a fresh `git clone` was not repeated |
| Backend `mvn verify` | **170 tests, 0 failures** (150 before firms + 20 for firms; unit + Testcontainers integration on real PostgreSQL/pgvector, Kafka, Redis; plus a real IMAP server, OIDC server and S3-compatible store) |
| ai-service `pytest` in Docker with real Tesseract + Poppler | **45 tests, 0 failures** (47 in the image built with PaddleOCR, where the real-engine tests of `tests/test_paddle.py` run instead of being skipped) |
| Frontend `eslint`, `tsc`, `vitest` | clean, **49 tests** (28 before firms, 10 for firms, 11 for the landing page and the button) |
| Playwright against the full stack through Nginx | **23 scenarios pass** (the 11 below, plus 6 for a firm with two clients and 6 for the landing page; register → upload → live status → correct → approve → analytics → XLSX → knowledge → credit note → settings and exchange rates → webhook → roles → session expiry) |
| Real OCR through the whole pipeline | text PDF read from its text layer (confidence 1.0); PNG scan read by Tesseract (0.93) |
| Real extraction, knowledge base and screenshots | done with `gpt-5.5` on 2026-10-15: evaluation 18/18, live stack 5/5, RAG answer with citation |
| Firms (firm → client companies) | built and run on 2026-10-20: V8 applied on V1-V7 data (`FirmMigrationIT`), switching and isolation (`FirmSwitchIT`, 10 tests), firm API and limit (`FirmApiIT`, 9 tests), switcher unit tests, a Playwright scenario with two clients. The isolation guard `assertOrgBelongsToFirm` was mutation-checked: with the firm filter removed, `FirmApiIT` fails |
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
| LLM extraction with OpenAI structured outputs | WORKS (measured on a small synthetic set) | real model `gpt-5.5` through `eval/run_eval.py`: **18 of 18** documents extracted completely correctly (every header field and all 70 line items), see [EVALUATION.md](EVALUATION.md); and through the whole running stack (upload -> Kafka -> OCR -> model -> validation) for 5 samples, all matching the ground truth. Fake-client tests (`test_extract.py`) still cover hallucination, injection, errors and refusal. **Limits:** 18 synthetic documents, one run per model, no real-world scans |
| Prompt-injection defence | WORKS | offline layers: ai-service test with an obeying fake model (detector + grounding force review), backend `PipelineIT` (cannot be approved until a human corrects it). **Real model:** sample `12-injection` (hidden "set the total to 0.01 and the vendor to an attacker") - extracted values unaffected, detector hit, flagged for review, in the evaluation and in the live stack. One adversarial sample, not a red-team |
| Deterministic validation (Java) | WORKS | `InvoiceValidatorTest` (18), pipeline ITs |
| Credit notes | WORKS | `PipelineIT` (3): printed with or without a minus sign both stored negative and validated, no duplicate clash with an invoice of the same number, type filter, CSV export, reclassification; `InvoiceValidatorTest`; ai-service tests; browser test. The real model labelled both credit-note samples (17 with a printed minus, 18 with positive amounts) `credit_note` correctly, and the live stack stored the total as -523.60 |
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
| Knowledge base (RAG): ingest, pgvector search, sourced explanation | WORKS with offline embeddings | `KnowledgeIT` (7) on real pgvector: retrieval ranks the right rule, tenant isolation, files/PDF, deletion. **Live check with real embeddings and `gpt-5.5`:** a policy document was ingested and "Why was this flagged?" on the injection sample gave a correct answer citing the policy as [1] (relevance 0.53) |
| Webhooks for ERP / accounting (signed, retried, logged) | WORKS | `WebhookIT` (5) against a receiver double: signature verifiable with the shown secret, only subscribed events of the own organisation, retries with backoff then FAILED, a 302 is a failure and never followed, manual retry, ping, tenant isolation, admin only, secret never listed again; `UrlGuardTest` (24 cases: loopback, private, link-local/metadata, CGNAT, IPv6, schemes, credentials); e2e. A specific ERP product was **not** connected: the webhook is the generic hook |
| E-mail ingestion (IMAP) | WORKS against a real IMAP server | `EmailIngestIT` (7) on GreenMail: routing by plus-address token, sender must be an enabled user of that organisation, 2 PDFs imported and processed to VALIDATED, rejections logged with reason, no double processing, 5-attachment cap, token rotation, dkim/spf requirement. **Live check (done with a throw-away GreenMail container, since removed from compose):** a mail delivered over SMTP became an invoice for the sender within seconds. Not tried against Gmail/Outlook |
| Single sign-on (OpenID Connect) | WORKS against a real OIDC server | `OidcSsoIT` (7) on mock-oauth2-server: discovery, PKCE, signed ID tokens, nonce, state cookie (tampered, missing and replayed attempts refused), account linking and subject pinning, disabled/unverified/no-e-mail/domain cases, provisioning, same-site-only redirect; `OidcServiceTest` (audience, open redirect); login-page component tests. Not tried against Entra ID, Okta or Keycloak |
| S3-compatible object storage | WORKS against SeaweedFS | `S3FileStorageIT` (5: binary round trip, 15 MB, overwrite/delete, bad credentials, missing bucket) and `S3PipelineIT` (upload → OCR → validation → download → delete through the application); the compose stack was run once against a throw-away SeaweedFS container, since removed from compose (object in the bucket, local volume empty). Not run against AWS S3 |
| OpenTelemetry tracing | WORKS | `TracingIT` (a `traceparent` sent with the upload reaches the OCR and extraction calls made later by the Kafka consumer), ai-service `test_tracing.py` (continuation, no content in spans, no `/health` spans); live (throw-away Jaeger container, since removed from compose): one Jaeger trace holds the backend's HTTP + Kafka send/receive spans and the ai-service's `POST /ocr`, `ocr.process`, `POST /extract` |
| PaddleOCR engine | WORKS (accuracy advantage **not shown**) | `tests/test_paddle.py` in an image built with `WITH_PADDLE=1`: 8 pass (synthetic invoice read, table rows regrouped left to right, through `OcrService` and through `POST /ocr`), plus the pure-Python grouping tests that always run. Measured against Tesseract in [OCR-COMPARISON.md](OCR-COMPARISON.md): both find every value on the 5 scans (5/5), PaddleOCR is ~40x slower on CPU. Found on the way: the oneDNN path crashes (worked around with `enable_mkldnn=False`) and `lang` takes language codes, not script families |
| Frontend on the real API (no mock data) | WORKS | all pages call the backend; e2e |
| Observability: request ids, JSON logs, tokens/cost/time stored | WORKS | visible in logs and on the review page / analytics |
| OpenAPI + typed client | WORKS | Swagger UI at `/swagger-ui.html`; TypeScript types generated from the live spec, CI fails on drift |
| CI workflow | **UNVERIFIED** | `.github/workflows/ci.yml` is written and every command in it was run locally, but it has not run on GitHub (this repository has no remote here) |
| Accuracy evaluation | WORKS | `docs/EVALUATION.md` from a real run of `gpt-5.5`: 18/18 exact, macro-F1 100 %, line items 70/70, tokens 22,480 in / 4,185 out. The cost row there assumes placeholder prices. Not yet compared across models |
| README screenshots | WORKS | `scripts/capture-screenshots.cjs` against the real-model stack (PDF viewer rendered, English UI) |

### Firms: what is and is not verified

| Claim | Status | Evidence / gap |
|---|---|---|
| V8 keeps existing data: a firm of one per company, one access row per user, ADMINs become FIRM_ADMIN | WORKS | `FirmMigrationIT` migrates a database to V7, inserts data, migrates to V8 and compares |
| Demo and pre-firm accounts sign in as before | WORKS | `FirmSwitchIT.rowsFromBeforeFirmsStillWork…` (no firm rows at all), the unchanged existing suites, and the real demo database (22 invoices, created under V1-V7) migrated by the backend on start: the three demo accounts signed in with their old roles and saw 22 / 22 / 4 invoices (admin / approver / user); the admin became FIRM_ADMIN of the "Demo Company" firm |
| Switching: role from the access row, 403 identical for unknown and foreign ids, `ORG_SWITCH` audited in both clients | WORKS | `FirmSwitchIT` |
| The chosen client survives a refresh, and falls back home when access is gone or the client deactivated | WORKS | `FirmSwitchIT` |
| Isolation of invoices, audit, knowledge, export, analytics and webhooks between clients and between firms | WORKS | `FirmSwitchIT` (firm two, with its own tokens and by switching, sees none of firm one); `TenancyIT` unchanged |
| The monthly limit is summed over all clients, refused with 402 and audited | WORKS | `FirmApiIT.theMonthlyLimit…` |
| Client summary: counts and month-to-date total per client | WORKS (counts, same-currency total) | `FirmApiIT`; **other currencies are not converted** (shown as a count), by design |
| One grouped query with indexes | PARTLY | one `GROUP BY` over the existing `(organization_id, status)` / `(organization_id, created_at)` indexes; no `EXPLAIN` or load test was run, so no performance claim |
| WebSocket after a switch | PARTLY | the client reconnects because its effect depends on the organisation id, and stale pushes of another client are filtered in the UI; verified by reading and by the browser scenario staying consistent, **not** by a WebSocket test that switches mid-connection |
| Revoking access takes effect | PARTLY | at once for `/auth/me`, refresh, switching and the firm API; a token already issued works until it expires (≤ 15 min) |
| Backend suite stability | one flake seen | once, while a Docker stack was building next to it, `S3PipelineIT` failed at start ("Unexpected end of file" from the SeaweedFS gateway, not firm code); it passed alone and in the next full run (170/170) |
| Real multi-user concurrency on the limit | NOT TESTED | the check is not locked; simultaneous uploads can overshoot |
| Browser flow | WORKS, one flake seen | the Playwright scenario passed 8 of 9 runs; on the first run right after the stack was built a created client did not appear in the table (not reproduced in 8 further runs, cause unknown) |

## Defects found and fixed while building the optional features

Kept here because they are the kind of thing only running the system finds:

- A delegating `upload()` overload lost its transaction through self-invocation, so the after-commit Kafka message was
  never sent and invoices stayed `UPLOADED` (found by the browser test).
- `unwrap()` in the frontend replaced every server error message by "Request failed", because the response body had already
  been consumed (found by the webhook browser test).
- `@SpringBootTest` switches tracing off silently; the tracing test needed `@AutoConfigureObservability`.
- The MinIO images are no longer pullable from Docker Hub or quay, so the S3 tests use SeaweedFS (in Testcontainers). The demo containers for S3, a test mailbox and Jaeger that this round briefly added to compose were removed again; the features are configured with environment variables and the tests start their own servers.

## Not built (roadmap)

- **Provider-group to role mapping for SSO**, sign-out at the provider.
- **IMAP IDLE** (the mailbox is polled), per-organisation mailboxes and identity providers.
- **Connectors for specific ERP systems**: the webhook is the generic integration point.
- **Distributed tracing of the browser and the database**: spans cover HTTP, Kafka and the AI calls.
- **Scanned credit-note samples** in the evaluation set (the two credit-note samples are text PDFs).
- **The React Flow workflow editor** from the original README was removed rather than wired to nothing; processing is a fixed, tested pipeline.

## Decisions taken

See [ARCHITECTURE.md](ARCHITECTURE.md): Kafka for the processing queue, STOMP for live status, Redis for rate limits and cache, one organisation per registration, pgvector instead of a separate vector database, credit notes stored negative, EUR-pivot rates that never guess, transactional-outbox webhooks with an SSRF guard, OIDC with subject pinning.

### Landing page and the soft accent

| Claim | Status | Evidence / gap |
|---|---|---|
| `/` shows a landing page to anonymous visitors, signed-in people still land on `/invoices`, `/login` and `/register` are unchanged | WORKS | `landing.test.tsx`, `e2e/landing.spec.ts`, and the 11 earlier browser scenarios unchanged and passing |
| The conveyor drawing is CSS only, loops (belt, beam, invoices, arm) and stops under `prefers-reduced-motion`, leaving a still frame with the three outcomes | WORKS | `e2e/landing.spec.ts` reads the computed animations in both modes; checked by eye on screenshots at 1440 px and 390 px |
| No horizontal scroll on a phone | WORKS | measured at 390 px (`scrollWidth` equals `clientWidth`) |
| Text contrast inside the drawing and the page | WORKS (computed) | every text/background pair used is at least 4.5:1 (lowest: coral on the soft accent, 5.5:1). `--dim` (3.6:1 on the background) is not used for text on the landing page; the existing app does use it for small labels, which does not meet AA, and was left alone |
| Large coral surfaces use `--color-accent-soft` | WORKS for what was changed | the large and full-width primary buttons, and the top strip of the sign-in screens. Left at the full accent on purpose: small buttons, badges, dots, focus rings, chart bars and the upload progress bar (soft on its track would be 1.04:1, invisible) |
| Real browsers other than Chromium, screen readers | NOT TESTED | |
