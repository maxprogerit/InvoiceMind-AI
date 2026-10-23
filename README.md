# InvoiceMind

Upload an invoice (PDF or photo): InvoiceMind reads it with OCR, extracts the data with an LLM, checks the arithmetic with
deterministic rules, lets a person correct and approve it, and gives you analytics and exports.

The model is never trusted: every value it returns must appear in the document, is re-validated by plain Java code, and
anything doubtful goes to a person.

> This is the **lite** branch: four containers, no tests, no extra documents. The complete version (Kafka, Redis, Nginx,
> 150 backend + 45 ai-service + 28 frontend + 11 browser tests, architecture notes, status and evaluation reports,
> screenshots) is on the `main` branch of the same repository. The numbers quoted below come from there.

## Quick start

Requirements: Docker with Compose v2 and an OpenAI API key.

```bash
git clone <this repository> invoicemind && cd invoicemind
cp .env.example .env          # then put your key in OPENAI_API_KEY=
docker compose up --build     # the first build takes a few minutes
# open http://localhost
```

http://localhost shows a short landing page to visitors who are not signed in (with links to sign in and register); signed-in people go straight to their invoices.

Four containers: `postgres` (with pgvector), `ai-service` (OCR and every OpenAI call), `backend` (Spring Boot) and
`frontend` (Next.js, the only published port; it also proxies `/api` and the WebSocket to the backend).

Sign in with a seeded demo account (password is `DEMO_PASSWORD` in `.env`, default `Demo12345!`):

| Account | Role |
|---|---|
| `admin@demo.invoicemind.local` | ADMIN: everything, plus team, settings, integrations, knowledge |
| `approver@demo.invoicemind.local` | APPROVER: sees the organisation, approves and rejects |
| `user@demo.invoicemind.local` | USER: uploads, sees only their own invoices |

Or register your own organisation (you become its admin). Try [`samples/invoices`](samples/invoices): text PDFs, scans, a
multi-page invoice, German, Russian, two credit notes and one invoice with a hidden prompt injection.
Without an `OPENAI_API_KEY` the app starts, but uploads end as *Failed* with a clear message instead of made-up data.

## What it does

| | |
|---|---|
| **Read** | PDFs with a text layer are read directly; scans and photos go through Tesseract (English, German, Russian), or PaddleOCR if built with it |
| **Extract** | OpenAI structured output: vendor, number, dates, currency, line items, subtotal, tax, total, document type, per-field confidence |
| **Verify** | Values are grounded against the OCR text; Java rules check sums, tax, dates, currency, duplicates; suspected prompt injection forces review |
| **Review** | Document next to editable fields; errors and low-confidence fields highlighted; sums re-checked as you type |
| **Approve** | Roles, comments, four-eyes rule, audit trail, notifications, live status over WebSocket |
| **Credit notes** | Their own document type, stored with negative amounts, mirrored validation, filter and export |
| **Convert** | Report in one currency at the ECB rate of each invoice's date; manual rates for currencies the ECB lacks; no rate means "not converted", never a guess |
| **Understand** | Analytics from real data, export to CSV / XLSX / JSON |
| **Explain** | Upload your policies; "Why was this flagged?" answers from them with cited sources (RAG on pgvector) |

On the `main` branch the evaluation with the real model (`gpt-5.5`, 18 synthetic documents) extracted **18 of 18** completely
correctly, flagged the injection sample, and answered the policy question with the right citation. That set is small and
synthetic: read it as a smoke test of the pipeline, not as a production accuracy figure.

### Optional features (off by default, environment variables, see `.env.example`)

- **Webhooks** for ERP / accounting systems (admin: *Integrations*): `invoice.approved`, `.rejected`, `.validated`,
  `.needs_review`, `.failed`; HMAC-SHA256 signed (`X-InvoiceMind-Signature: t=<unix>,v1=<hex of HMAC(secret, "t.body")>`),
  at-least-once with backoff, delivery log. URLs pointing to private or internal addresses are refused (SSRF guard) unless
  `WEBHOOKS_ALLOW_PRIVATE_TARGETS=true`.
- **E-mail ingestion** (`MAIL_INGEST_*`, `MAIL_IMAP_*`): mail to `<mailbox>+<token>@<domain>` (address shown under *Settings*) is
  imported when the sender is a user of that organisation; everything lands in review.
- **Single sign-on** (`OIDC_*`): any OpenID Connect provider, authorization code + PKCE. Accounts are linked by verified
  e-mail and then pinned to the provider's subject. Register `<app url>/api/v1/auth/oidc/callback` at the provider.
- **S3 storage** (`STORAGE_TYPE=s3`, `S3_*`): invoice files in a bucket instead of a volume.
- **Tracing** (`TRACING_ENABLED=true`, `OTEL_EXPORTER_OTLP_ENDPOINT`): OpenTelemetry spans from backend and ai-service.
- **PaddleOCR** (`docker compose build --build-arg WITH_PADDLE=1`, then `OCR_ENGINE=paddle`, `PADDLE_LANG=de|en|ru`): found
  every value on the five sample scans like Tesseract, but about 40 times slower on CPU; Tesseract stays the default.

Single sign-on, e-mail and S3 were verified against real protocol implementations (an OIDC server, an IMAP server, an
S3-compatible store), not against Entra ID, Gmail or AWS themselves.

## How it fits together

```
browser ──> frontend (Next.js) ──/api, /ws──> backend (Spring Boot) ──> postgres (+ pgvector)
                                                   │
                                                   └── HTTP ──> ai-service (FastAPI) ──> OpenAI
```

Upload → file stored → invoice handed to an in-process worker pool (3 threads) after the transaction commits → OCR → LLM →
deterministic validation → `VALIDATED` or `NEEDS_REVIEW` → a person approves or rejects. Lifecycle:
`UPLOADED → PROCESSING → EXTRACTED → NEEDS_REVIEW | VALIDATED → APPROVED | REJECTED`, or `FAILED` (one click to retry).

Security in short: JWT access tokens (15 min, in memory) plus a rotating refresh token in an `httpOnly` cookie with theft detection;
roles USER / APPROVER / ADMIN; tenant isolation by organisation taken from the token (foreign ids give 404); upload hardening (magic
bytes, 15 MB, PDF active-content scan); untrusted-data prompts with nonce delimiters, schema-validated and grounded model output;
CSV/XLSX formula neutralisation; audit trail of every change.

## Multi-client firms

An accounting firm works for several client companies. A **firm** sits above its **client companies** (organisations):
each client stays a separate data tenant with its own invoices, team, audit trail, knowledge base, webhooks and
reporting currency; the firm only decides who may enter which client. Nothing changes for a company that registers on its
own: it is a firm of one with a single client (itself), and the extra screens only appear when there is something to switch.

- **Firm roles.** `FIRM_ADMIN` reaches every client of the firm (as ADMIN there) and manages clients and the firm team.
  `ACCOUNTANT` reaches only the clients they were given, with the role they were given there (a person can be an approver
  for one client and a plain user for another).
- **Switching.** The client switcher (sidebar, only for people who can reach more than one client; searchable) asks the
  server for a token for the chosen client (`POST /api/v1/auth/switch-organization`). The server checks the database, not the
  old token; "no such client" and "not yours" give the identical 403. The page's cache is reset and the live connection
  reconnects with the new token. The chosen client is stored with the refresh token, so a reload or a token refresh stays in
  it; if access has been withdrawn in the meantime, the next refresh lands in the person's home client.
- **Firm clients** (`/firm/clients`): documents, in review, awaiting approval, failed, and the approved total of the current
  month per client. The total counts only invoices already in the client's reporting currency; approved invoices in other
  currencies are shown as a count, never converted behind your back. A row opens that client.
- **Firm team** (`/firm/team`): the access matrix (person × client, with the role there). New accountants start with one client
  (their home, where they land on sign-in) and get more in the table. Removing a person disables the account.
- **Monthly document limit.** `firms.monthly_document_limit` (null = unlimited, the default) caps the uploads of the whole firm
  per calendar month (UTC), summed over all of its clients, including mail ingestion. Over the limit an upload answers
  `402 firm_limit_reached`, and the refusal is audited. There is no billing and no UI to set the limit:
  `update firms set monthly_document_limit = 200 where id = '…'`.

## Configuration

Everything is an environment variable; [`.env.example`](.env.example) lists them all with comments (secrets are never committed).
The ones you will touch first: `OPENAI_API_KEY`, `OPENAI_MODEL` (default `gpt-5.5`), `OPENAI_PRICE_INPUT_PER_1M` /
`OPENAI_PRICE_OUTPUT_PER_1M` (only used to show a cost per invoice; set the real prices), `JWT_SECRET` and `AI_SERVICE_TOKEN`
(change the development values for anything real), `DEMO_PASSWORD` (empty disables the demo users), `HTTP_PORT` (default 80).
Behind TLS also set `APP_COOKIES_SECURE=true` on the backend. The backend API and Swagger UI are published on
`127.0.0.1:8080` (`BACKEND_PORT`) for local tools and available through the frontend at `/swagger-ui.html`.

## Development

```bash
docker compose up --build        # whole stack
cd frontend && npm ci && BACKEND_INTERNAL_URL=http://127.0.0.1:8080 npm run dev     # hot reload on http://localhost:3000
cd frontend && OPENAPI_URL=http://localhost:8080/v3/api-docs npm run api:types      # regenerate the typed API client
cd backend && mvn -DskipTests package                                               # build the jar
```

## Limitations

- One backend process: the worker queue, rate limits and the analytics cache are in memory. After a restart, invoices that were
  waiting are queued again and those that were mid-processing are marked failed (retry with one click); nothing else is lost.
- Access tokens live 15 minutes, so disabling a user takes effect for issued tokens at their expiry (refresh is blocked at once).
- Uploads are limited to 15 MB; the frontend proxy buffers request bodies up to 16 MB.
- Currency conversion uses one rate per invoice date; the ECB does not publish every currency (RUB needs a manual rate). The
  extraction cost shown uses whatever prices you configured.
- Webhook delivery is at-least-once (receivers deduplicate on the event id) and the signing secret is stored in the database in clear.
- E-mail ingestion polls one mailbox every minute and trusts `From` unless `MAIL_REQUIRE_AUTH_RESULTS=true`.
- Single sign-on does not map provider groups to roles and sign-out does not end the session at the provider.
- Firms: an e-mail address belongs to one account, so one person cannot be two separate users in two firms. Withdrawing access, deactivating a client or removing a team member shows in an open session when its 15-minute token expires (`/auth/me` and refresh check at once). The monthly limit is not locked, so simultaneous uploads can overshoot it slightly, and deleting a document does not give the quota back. There is no screen to edit a client's name or currency or to set the limit.
- Zero-value invoices are rejected by validation. Files are stored on a local volume unless S3 is configured.
- The compose file is for a single host; terminate TLS in front of the frontend for anything public.

## Repository layout

```text
backend/      Spring Boot API, pipeline, validation, knowledge base (+ Flyway migrations)
ai-service/   OCR and every OpenAI call
frontend/     Next.js application
samples/      synthetic sample invoices to try
```
