# Status

What works, what is unverified and what is not built, with evidence. Updated on 2026-11-27 after the merge of
InvoiceExtract into InvoiceMind (stages 1-9: one product, Spring Boot owns the business, the extract-service is the
internal AI service, Next.js is the only UI). History of earlier rounds: [Phase 0 audit](AUDIT-PHASE0.md).

**Legend** — `WORKS` observed working by running it (test name or command given) · `UNVERIFIED` implemented
and tested against fakes or protocol implementations, never against the real external service · `NOT RUN` built
and tested offline, but the measurement itself has not been made · `MISSING` not built.

## Verified in this environment (2026-11-27)

| Check | Result |
|---|---|
| Backend `mvn verify` | **225 tests, 0 failures**: unit + Testcontainers integration on real PostgreSQL/pgvector, a real IMAP/SMTP server (GreenMail), an OIDC server, an S3-compatible store (SeaweedFS) |
| extract-service `pytest` (real Tesseract + Poppler) | **116 passed, 1 skipped** (the live OpenAI smoke test, opt-in with `LIVE_OPENAI_SMOKE=1`) |
| Frontend `eslint`, `tsc`, `vitest` | clean, **53 tests**; the generated OpenAPI types match the backend |
| Playwright against the full stack through Nginx (offline AI test double) | **27 scenarios pass**: upload, live status, correction, approver in a second browser, possible duplicate decision, new-vendor confirmation, queues and vendor memory, credit note, approval rules, Xero export once, zip upload, accountant view, firms, landing page |
| Demo flow e-mail → review → approve → export | `scripts/demo-flow.cjs` on the local stack with a GreenMail IMAP mailbox: the supplier's mail became an invoice (*Arrived via e-mail*), the wrong total was flagged by code, corrected, approved under the EUR rule (one approver; 9,897.80 EUR needed two; 45 EUR was automatic), exported to Xero once. The README screenshots come from this run |
| Network isolation | nginx and the frontend cannot resolve `extract-service`; a request without the service token gets 401 |
| Behaviour with no OpenAI key | the extract-service answers 503 `llm_not_configured`, the invoice ends `FAILED` with that message; nothing is invented |
| **Not done here** | no run against the real OpenAI API since the merge (no key in this environment); CI has not run on GitHub; no deployment |

## Features

| Feature | Status | Evidence |
|---|---|---|
| Auth: register, login, JWT, refresh rotation, logout | WORKS | `AuthIT`: rotation, reuse revokes every session, CSRF header, uniform login failure |
| Roles ACCOUNTANT / APPROVER / ADMIN | WORKS | `ApprovalRulesIT`, `WorkflowIT`, `ReviewQueueIT`, `AuthIT`; accountants see the whole organisation and review, cannot approve; nobody approves their own upload, admins included |
| Demo organisation with one user per role and approval rules | WORKS | `DemoSeedIT` (four users with their roles, EUR/USD/GBP rules, idempotent) |
| Tenant isolation | WORKS | `TenancyIT`, `FirmSwitchIT` |
| Upload + file storage + safe serving | WORKS | `PipelineIT`, `FileValidatorTest` (disguised exe 415, active PDF 422, >15 MB 413) |
| Batch upload (.zip) | WORKS | `BatchUploadIT`: limits counted while unpacking, path traversal refused, nested archives not opened, per-file reasons; e2e |
| E-mail intake (IMAP) | WORKS against GreenMail, UNVERIFIED against Gmail/Outlook | `EmailIngestIT`: plus-address routing, policy ANYONE / USERS, supplier mail lands under the "E-mail intake" account, claim-first by Message-ID, marked seen only after its documents are queued, the mail body never reaches the model; live in the demo flow |
| Processing queue in PostgreSQL (`FOR UPDATE SKIP LOCKED`) | WORKS | `ProcessingJobQueueIT`: one job per invoice, locked jobs skipped instead of waited for, retry backoff and attempt count, a dead worker's jobs handed out again and given up after the last attempt; error classification (5xx/429/timeouts retried, 4xx permanent) in `HttpAiClientTest`; Kafka and Redis removed |
| extract-service `POST /invoices` (file → validated JSON) | WORKS (offline) | `test_invoices.py`: money as decimal strings, deterministic validation by code, unsupported files rejected before the model; token required |
| LLM extraction with the real model | **NOT RUN since the merge** | the code path is the one measured on 2026-10-15 (18/18 with `gpt-5.5`, see [evaluation.md](evaluation.md)), plus the new validation step; `test_live_smoke.py` is ready for a key |
| Prompt-injection defence | WORKS (offline) | detector + grounding force review (`test_extract.py`), cannot be approved until a person corrects it (`PipelineIT`); on 2026-10-15 the real model resisted sample `12-injection` |
| Deterministic validation (Java and Python) | WORKS | `InvoiceValidatorTest`, `test_invoice_validation.py` |
| Vendors: tax-id / name / alias matching, unverified vendors block approval | WORKS | `VendorIT` (including concurrent first invoices of one vendor creating one vendor) |
| Duplicates: same vendor + number, same file (extraction reused, model not called), possible duplicate within 7 days | WORKS | `DuplicatesIT` ("older wins", re-check of newer invoices after a correction) |
| Review queues and field-level corrections | WORKS | `ReviewQueueIT`, e2e |
| Vendor memory (currency, payment terms, category, aliases) | WORKS | `VendorMemoryIT`: currency, payment terms and category learned from corrections and applied (marked) to the next invoice, a misread name becomes an alias, every correction stored with before/after/who/when |
| Approval rules per currency (auto / one / two approvers) | WORKS | `ApprovalRulesIT`: automatic only once every check passes and the vendor is confirmed; two distinct approvers, neither the uploader; credit notes by absolute amount; a correction restarts the approvals. Supplier e-mail always needs a person: `EmailIngestIT` |
| Due-date reminders | WORKS | `ReminderIT` (one digest per organisation and day, exported invoices left out); checked live in Mailpit |
| Export to Xero / QuickBooks Online | WORKS, **headers UNVERIFIED against live templates** | `AccountingExportIT` (columns, each invoice exported once, credit notes left out of QuickBooks); see [ACCOUNTING-EXPORT.md](ACCOUNTING-EXPORT.md) for sources |
| Webhooks (signed, retried, logged) | WORKS | `WebhookIT`, `UrlGuardTest` |
| Credit notes, currency conversion, analytics, exports XLSX/CSV/JSON | WORKS | `PipelineIT`, `FxIT`, `AnalyticsExportIT` |
| Live status over WebSocket | WORKS | `WebSocketIT` |
| Knowledge base (RAG on pgvector) | WORKS offline | `KnowledgeIT`; a real-model answer with citation was seen on 2026-10-15 |
| Single sign-on (OIDC) | WORKS against mock-oauth2-server, UNVERIFIED against Entra ID / Okta | `OidcSsoIT` |
| S3 storage | WORKS against SeaweedFS, UNVERIFIED against AWS | `S3FileStorageIT`, `S3PipelineIT` |
| OpenTelemetry tracing | WORKS | `TracingIT` (the traceparent stored on the job reaches the extract-service call), `test_tracing.py` |
| Evaluation harness | WORKS (offline), **NOT RUN with the model** | 32 synthetic documents with ground truth; `test_eval_metrics.py` (21 tests); smoke run with the test double passes end to end |
| CI workflow | UNVERIFIED | `.github/workflows/ci.yml`; every command in it was run locally, it has not run on GitHub |

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

## Defects found and fixed during the merge (stages 1-9)

- Dropping the unique vendor-name constraint let two workers create the same vendor at once, so possible duplicates
  were missed (found by the browser test); fixed with a Postgres advisory lock per organisation and name, and a
  concurrency test.
- Correcting an older invoice to match a newer one did not flag the newer one; newer invoices of the vendor are now
  re-checked after processing and after every correction.
- Two Java records named `Skipped` collided in the OpenAPI schema and broke the generated TypeScript types; renamed.
- The extraction prompt still asked for JSON numbers after money became decimal strings; fixed with the schema.

## Defects found and fixed while building the optional features (earlier round)

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
- **Distributed tracing of the browser and the database**: spans cover HTTP, the job queue and the AI calls.
- **Scanned credit-note samples** in the evaluation set (the two credit-note samples are text PDFs).
- **The React Flow workflow editor** from the original README was removed rather than wired to nothing; processing is a fixed, tested pipeline.

## Decisions taken

See [ARCHITECTURE.md](ARCHITECTURE.md): a PostgreSQL job queue instead of Kafka, rate limits in PostgreSQL instead of Redis, the extract-service on an internal network with a service token, STOMP for live status, one organisation per registration, pgvector instead of a separate vector database, credit notes stored negative, EUR-pivot rates that never guess, transactional-outbox webhooks with an SSRF guard, OIDC with subject pinning.

### Landing page and the soft accent

| Claim | Status | Evidence / gap |
|---|---|---|
| `/` shows a landing page to anonymous visitors, signed-in people still land on `/invoices`, `/login` and `/register` are unchanged | WORKS | `landing.test.tsx`, `e2e/landing.spec.ts`, and the 11 earlier browser scenarios unchanged and passing |
| The conveyor drawing is CSS only, loops (belt, beam, invoices, arm) and stops under `prefers-reduced-motion`, leaving a still frame with the three outcomes | WORKS | `e2e/landing.spec.ts` reads the computed animations in both modes; checked by eye on screenshots at 1440 px and 390 px |
| No horizontal scroll on a phone | WORKS | measured at 390 px (`scrollWidth` equals `clientWidth`) |
| Text contrast inside the drawing and the page | WORKS (computed) | every text/background pair used is at least 4.5:1 (lowest: coral on the soft accent, 5.5:1). `--dim` (3.6:1 on the background) is not used for text on the landing page; the existing app does use it for small labels, which does not meet AA, and was left alone |
| Large coral surfaces use `--color-accent-soft` | WORKS for what was changed | the large and full-width primary buttons, and the top strip of the sign-in screens. Left at the full accent on purpose: small buttons, badges, dots, focus rings, chart bars and the upload progress bar (soft on its track would be 1.04:1, invisible) |
| Real browsers other than Chromium, screen readers | NOT TESTED | |
