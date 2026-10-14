# InvoiceMind AI — Honest Status Audit

Audit date: 2026-10-14. Commit audited: `2326f20` (`main`). Phase 0: nothing in the code was changed to produce this report.

**Legend** — `WORKS` observed working · `PARTIAL` works but incomplete/unsafe · `FAKE` looks real, produces simulated/hardcoded output · `BROKEN` fails when run · `MISSING` not present.
Every row is **FACT** (observed by running it) unless marked **INFERENCE** (deduced from code, not run) or **HYPOTHESIS**.

## How this was tested

- Fresh `git clone` of the committed state into a scratch dir, `cp .env.example .env`, `docker compose up --build`.
- The unmodified clone failed to build (see row 1). To audit the rest, I patched **only the scratch clone's** `package.json` (added `tailwindcss`, `postcss`, `autoprefixer`) and set `POSTGRES_PORT=5433` because this machine already has a Postgres on 5432. The repo itself is untouched.
- API probed with `curl` against `http://localhost:8080/api/v1`, DB inspected with `psql`, Kafka/Redis inspected from inside their containers.
- A hand-made one-page PDF whose text layer says `ACME GmbH / INV-7777 / Total EUR 1240.00` was used as the sample invoice.
- Not tested: a real browser session (the frontend makes zero network calls, see rows 24–26), load/performance.

## Status table

| # | Feature | Status | Evidence |
|---|---------|--------|----------|
| 1 | Clean-checkout `docker compose up --build` | **BROKEN** | Frontend image fails: `Cannot find module 'tailwindcss'`. `postcss.config.js` and `tailwind.config.ts` use `tailwindcss`/`autoprefixer`, but neither is in `frontend/package.json`; they only exist in the author's local `node_modules`. No `package-lock.json` is committed. After patching the scratch clone, all 6 containers started. |
| 2 | Backend build (`mvn package -DskipTests`) | **WORKS** | Exit 0, `target/invoicemind-backend-1.0.0.jar`; backend Docker image also builds. |
| 3 | Frontend build (`npm run build`) | **PARTIAL** | Passes in the working tree only because of undeclared local deps (row 1). Single 730 kB JS chunk. `npm run lint` is just `tsc --noEmit`; ESLint is installed but unused and there is no ESLint 9 flat config (an old `.eslintrc.json` exists). |
| 4 | Run backend locally (README: `./mvnw spring-boot:run`) | **BROKEN** | There is no `mvnw` in the repo. With plain `mvn`, startup needs Postgres/Redis/Kafka. `application.yml` defaults to user `postgres` with a **real-looking password committed in plain text**; against the Postgres on this machine it fails with password authentication failed. |
| 5 | Flyway migrations | **WORKS** | Under compose: V1 init, V2 seed, V3 reset sequences applied automatically, `ddl-auto: validate` passes. Schema has no unique constraint on `(vendor, invoice_number)` per org (`invoice_number` is globally `UNIQUE`), no line-item table, no validation-result table, no cost/token columns. |
| 6 | Seeded demo user | **BROKEN** | `admin@invoicemind.ai` / `approver@invoicemind.ai` exist, but the password is documented nowhere. 6 guesses (`admin123`, `password`, …) all failed. Unusable as a demo login. |
| 7 | Auth: register | **PARTIAL** | Works, but every registrant becomes `ROLE_ADMIN` in organization 1 (`AuthService.register`, observed in response). Duplicate email returns **raw SQL error text** to the client. No password policy (only non-blank/email checks). |
| 8 | Auth: login + JWT | **PARTIAL** | Login returns an HS256 JWT (1 h). Wrong credentials → HTTP 400 (should be 401). Unauthenticated request → 403 (should be 401). A malformed JWT → **HTTP 500** (unhandled `MalformedJwtException` in `JwtAuthFilter`). JWT secret has a committed default. |
| 9 | Auth: refresh token | **MISSING** | `POST /auth/refresh` → "No static resource". |
| 10 | Role checks / RBAC | **MISSING** | 0 uses of `@PreAuthorize` or `hasRole` in the backend. Roles are stored but never enforced. Approval endpoint hardcodes `approverId = 1`. |
| 11 | Multi-tenant isolation | **BROKEN** | `organizationId = 1L` is hardcoded in every service. User B listed user A's invoices (2 visible) and documents (2 visible, `uploadedBy: 1`). `GET /invoices/{id}` and `/documents/{id}` use `findById` with no ownership check (**INFERENCE** from code; list-level leak observed). |
| 12 | File upload | **PARTIAL** | `POST /documents/upload` accepts a file and creates a DB row. Default Spring limit is 1 MB: a 2 MB file → 400 "Maximum upload size exceeded"; 60 MB → connection reset. No type whitelist, no content sniffing: `MZ…` bytes named `../../etc/evil.exe` with `application/x-msdownload` were accepted and the unsanitized name was stored in `storagePath`. |
| 13 | File storage | **FAKE** | `LocalFileStorageService.store()` returns the string `"/storage/" + originalFilename` and **writes nothing**. `/storage` does not exist in the container. Uploaded bytes are discarded, so the original can never be previewed or re-processed. |
| 14 | OCR | **FAKE** | `OcrSimulationService.parseDocument()` returns random numbers/hardcoded vendor (`Apex Logistics`) from the *filename only*. My PDF (`ACME GmbH`, `INV-7777`, `1240.00`) became `INV-2026679 / Apex Logistics / 812`. `DocumentService.processDocument` also uses `Thread.sleep(600/500/400)` to fake progress. Settings endpoint even reports `"ocrProvider": "SmartOCR Simulation"`. |
| 15 | LLM extraction | **MISSING** | No `ai-service`, no OpenAI/LLM client, no API key in `.env.example`. "AI confidence" shown anywhere is `0.88 + random()*0.11`. |
| 16 | Validation layer | **MISSING** | No sum/tax/date/currency/duplicate checks. `duplicate_flag` is always `false`; `tax_amount` never set; invoice date/due date are fabricated as `now-2d` / `now+28d`; currency hardcoded `USD`. |
| 17 | Invoice persistence | **PARTIAL** | Rows are saved in `invoices`, but with fake content (rows 14, 16). Vendor chosen as "first vendor alphabetically" in `/invoices/upload`. No line items. Two competing upload endpoints (`/documents/upload` async, `/invoices/upload` sync) behave differently; the second never triggers processing status updates. |
| 18 | Invoice lifecycle / status | **BROKEN** | Status is a free string: `PUT /invoices/1/status {"status":"banana"}` → 200, and `banana` appears in the CSV export. Nothing ever moves an invoice out of `processing`. |
| 19 | Async processing | **FAKE** | `@Async` + `Thread.sleep` simulation (`@EnableAsync` is on). Not a queue; no retry, no failure state, lost on restart. |
| 20 | Approval workflow | **PARTIAL** | Approvals are created by a hardcoded rule (`confidence < 0.92` OR `amount > 10000` on fake data); `POST /approvals/{id}/decision` works mechanically but accepts any string, any user, and doesn't update the invoice status. No UI wired to it (row 25). |
| 21 | Analytics | **FAKE** | `/analytics/summary`, `spend-trend`, `document-volume`, `confidence-trend` return **hardcoded constants** (`ocrAccuracy: 97.2`, Jan–Jun spend arrays, savings `248000`). Dashboard KPIs fall back to invented numbers (`93.7%`, `97.1%`, `128492`, `$12.8M`). Only `vendor-spend` and a few counters hit the DB. |
| 22 | Export | **PARTIAL** | `GET /invoices/export/csv` returns a CSV from DB rows (no escaping of values other than vendor, no filters, no date range). `xlsx`/`json` endpoints don't exist (400). `/reports/generate` writes a DB row with `status: completed` and a fake `storagePath` (no file is created); "download" streams CSV from the live table regardless of requested format/range; `generatedBy` is hardcoded `admin@invoicemind.ai`. `ExportService` returns a fake `"queued"` object and is unused. |
| 23 | Audit log | **PARTIAL** | Rows are written (9 action types seen in DB), but `user_id` is hardcoded `1` or `null`, org hardcoded, metadata JSON built by string concatenation (**INFERENCE**: injectable/invalid JSON for names with quotes). No endpoint-level authorization on reading. |
| 24 | Notifications | **FAKE** | `NotificationService.list()` returns two hardcoded messages (`INV-2026-4011 awaits your decision`). `notifications` table is never written. |
| 25 | WebSocket | **PARTIAL** | STOMP endpoint `/ws/live` accepts connections (HTTP 101, also via Nginx), `/topic/executions` messages are emitted — but the handshake is unauthenticated, `setAllowedOriginPatterns("*")` (accepted an `Origin: evil.example` handshake), and the **frontend has 0 WebSocket references**, so nothing consumes it. |
| 26 | Kafka | **BROKEN** | Producer config uses default `StringSerializer` but the code sends `Map` → `SerializationException: Can't convert value of class java.util.ImmutableCollections$MapN to class StringSerializer` (seen in backend log; swallowed by the async handler). `ocr-events` has 0 messages on all partitions. The only consumers just `log.info`. Kafka is a dependency, not a feature. |
| 27 | Redis | **MISSING** (dependency only) | `spring-boot-starter-data-redis` + a Redis container; **0** uses of `RedisTemplate`/`@Cacheable`/`@EnableCaching`; `DBSIZE` = 0. |
| 28 | CORS / security headers | **PARTIAL** | CORS `allowedOriginPatterns("*")` with all methods/headers on `/api/**`; CSRF disabled (acceptable for stateless JWT). Swagger UI is public. |
| 29 | Frontend: backend integration | **FAKE** | **0** `fetch`/`axios`/`WebSocket`/`import.meta.env` usages in `frontend/src`. `services/api.ts` is a `fakeDelay` + canned "AI insight" string. All 17 data-driven pages read a Zustand store (`crmStore.ts`) seeded from `data/mockData.ts`. |
| 30 | Frontend: login/register | **FAKE** | Compares email/password **in plaintext** against mock users in the store (`admin@invoicemind.ai` / `admin123`); never calls `/auth/*`. |
| 31 | Frontend: routing / dead code | **PARTIAL** | Only 10 of 19 pages are routed (Dashboard, Documents, Executions, Workflows, Analytics, Vendors, Approvals, Reports, Login, Register). Calendar, Clients, Companies, Contacts, Deals, Invoices, Receipts, Settings, Team are unrouted CRM leftovers. App title is "InvoiceMind CRM". |
| 32 | Frontend: upload UI | **FAKE** | `FileUpload.tsx` writes to the mock store; no multipart request. |
| 33 | Frontend: manual correction / doc preview | **MISSING** | No review screen, no document viewer. |
| 34 | Frontend: workflow editor (React Flow) | **FAKE** | `Workflows.tsx` renders a hardcoded array of 6 labeled boxes with CSS animation; `reactflow` isn't even installed. Backend `workflows` CRUD exists but is not connected to processing. |
| 35 | Frontend stack vs README | **FAKE (README)** | README claims Next.js 15, React Query, Tailwind, Shadcn, React Flow, a `src/app`, `features`, `lib`, `hooks` directories. Actual: **Vite + React Router**, no Next, no React Query, no Tailwind dependency (config files only), no React Flow, none of those directories. `next.config.js`, `next-env.d.ts`, `NEXT_PUBLIC_API_BASE_URL` are vestigial (Vite ignores `NEXT_PUBLIC_*`). |
| 36 | Backend tests | **BROKEN** | One test: `contextLoads()` as `@SpringBootTest` with no Testcontainers/H2 → needs a live Postgres. `mvn test` fails (1 error: password authentication failed for `postgres`). Not hermetic. 0 unit/integration/auth tests. |
| 37 | Frontend tests | **MISSING** | None. |
| 38 | CI | **MISSING** | `.github/workflows/` is empty. |
| 39 | docker-compose quality | **PARTIAL** | Services start, Postgres healthcheck exists; **no healthchecks** on backend/frontend/redis/kafka/nginx; `depends_on` uses `service_started` for Redis/Kafka; Kafka/Redis/Postgres ports published to host; obsolete `version:` key; no `ai-service`; no volume for uploaded files. |
| 40 | Config / secrets | **BROKEN** | `application.yml` commits a DB password default and a JWT secret default; `.env.example` has a JWT secret placeholder that is also used as the working default; no `OPENAI_API_KEY`. |
| 41 | OpenAPI/Swagger | **WORKS** | `/v3/api-docs` → 200 (springdoc present). Contracts are untyped (`Map<String,Object>`, entities returned directly). |
| 42 | Observability | **MISSING** | No request IDs, no structured logs, no processing time/token/cost persisted. `trace_id` values are random UUID substrings. |
| 43 | RAG | **MISSING** | Not present; `instruction_rules` / `ai_insights` tables hold seeded/static text only. |
| 44 | README / docs | **BROKEN** | README lists a `docs/` folder that did not exist, `./mvnw` that doesn't exist, "OCR simulation" is the only honest line. |

**Count (44 rows):** WORKS 3 · PARTIAL 12 · FAKE 10 · BROKEN 9 · MISSING 10 (rows with mixed labels counted under their primary status).

## What is genuinely real today

- Spring Boot app boots under Docker; Flyway migrates; JPA validates.
- Register/login issue and verify HS256 JWTs; passwords are BCrypt-hashed.
- Basic CRUD persistence through JPA; Swagger UI available; Nginx proxies `/api`, `/ws`, `/`; WebSocket handshake works.
- Frontend UI components render and type-check (visual shell only).

## Prioritized fix plan

**P0 — blockers (do first, small commits)**
1. Make the repo build from a clean clone: declare frontend deps + lockfile, remove committed secrets from `application.yml`, complete `.env.example`, add `.dockerignore`s, fix compose (healthchecks, `depends_on: service_healthy`, remove obsolete `version`).
2. Make tests hermetic (Testcontainers) so CI can pass; add the first real tests.
3. Fix identity model: per-user organization from the JWT (no hardcoded `1L`), roles enforced with `@PreAuthorize`, 401 vs 403 handling, JWT error handling, no SQL leaks in errors, refresh token, seeded demo user with a documented password in `.env`.

**P1 — real pipeline (Phase 1)**
4. `ai-service` (FastAPI): `/ocr` (text-layer first, Tesseract fallback), `/extract` (OpenAI structured outputs, injection-hardened prompt).
5. Real file storage with sanitization, size/type whitelist and content sniffing.
6. Real invoice lifecycle + async queue + Java deterministic validation + line items/vendors/validation tables (Flyway V4+), cost/token/timing columns.
7. Delete `OcrSimulationService` and the fake `Thread.sleep` path.

**P2 — claimed product features (Phase 2)**
8. Approval workflow with roles + audit trail, correction UI, DB-backed analytics, CSV/XLSX/JSON export.

**P3 — frontend (Phase 3)**
9. Rebuild the UI against the real API with the new design (see decisions below); delete mock store and the 9 dead CRM pages.

**P4 — delivery (Phase 4)**
10. Test suites, 10–20 sample invoices with ground truth + evaluation report, CI, README rewrite with measured numbers.

## Decisions needed before Phase 1

1. **Frontend framework.** README promises Next.js 15; the code is Vite. Recommendation: **migrate to Next.js 15 (App Router) + React 19 + Tailwind v4 + shadcn/ui + TanStack Query + Framer Motion**, matching the README and the mockups.
2. **Async processing.** Recommendation: **Kafka** as the processing queue (`invoice.uploaded` → backend consumer → ai-service, with retry + dead-letter topic) because it is already in compose, and **STOMP/WebSocket** for live status. **Redis** only for rate limiting (auth/upload) and caching analytics; otherwise remove it.
3. **Tenancy.** Recommendation: registering creates a **new organization** and its first `ADMIN`; admins assign `USER`/`APPROVER` roles inside the organization.
4. **Scope cleanup.** Recommendation: remove unrelated CRM scaffolding (Calendar/Clients/Companies/Contacts/Deals/Team, receipts, workflow definitions, `ai_insights`, `instruction_rules`) unless you want them kept for a reason.
5. **OpenAI usage / cost.** Needs an `OPENAI_API_KEY` and a model choice; extraction runs and the evaluation script will cost money (small for ~20 invoices). I will confirm current model and API method names from the official docs before coding.
