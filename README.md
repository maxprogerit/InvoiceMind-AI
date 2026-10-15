# InvoiceMind

Upload an invoice (PDF or photo), and InvoiceMind reads it with OCR, extracts the data with an LLM, checks the
arithmetic with deterministic rules, lets a human correct and approve it, and gives you analytics and exports.

It is built so that **the model is never trusted**: every value it returns must appear in the document, is
re-validated by plain Java code, and anything doubtful goes to a person.

> **Honest status.** Everything below runs and is tested, with two kinds of exception. (1) The extraction and the
> knowledge-base answers call the OpenAI API, and in the environment this was built in there was no API key, so those calls
> are verified against fakes only; the accuracy numbers in `docs/EVALUATION.md` appear after the first real run
> (`docker compose --profile eval run --rm eval`). (2) The integrations (single sign-on, e-mail ingestion, S3 storage) were
> verified against real *protocol implementations* (an OIDC server, an IMAP server, an S3-compatible store), not against
> Entra ID, Gmail or AWS themselves. The full, itemised picture is in [docs/STATUS.md](docs/STATUS.md).

## What it does

| | |
|---|---|
| **Read** | PDFs with a text layer are read directly; scans and photos go through Tesseract (English, German, Russian). Per-page confidence is kept. |
| **Extract** | OpenAI structured output turns the text into vendor, number, dates, currency, line items, subtotal, tax, total, with per-field confidence. |
| **Verify** | Values are grounded against the OCR text; Java rules check sums, tax, dates, currency, duplicates; suspected prompt injection forces review. |
| **Review** | Document on the left, editable fields on the right; errors and low-confidence fields are highlighted; sums are re-checked as you type. |
| **Approve** | Roles, comments, four-eyes rule, full audit trail, notifications, live status over WebSocket. |
| **Understand** | Analytics from real data (spend by vendor and month, review rate, processing time, AI cost). Export to CSV, XLSX, JSON. |
| **Explain** | Upload your policies; "Why was this flagged?" answers from them with cited sources (RAG on pgvector). |
| **Credit notes** | Recognised as their own document type, stored with negative amounts, validated with the mirrored rules, filterable and exportable. |
| **Convert** | Report in one currency: every invoice at the ECB rate of its own date; manual rates for currencies the ECB lacks; anything without a rate is listed as "not converted", never guessed. |
| **Integrate** | Signed webhooks for ERP / accounting systems, e-mail ingestion (IMAP), single sign-on (OpenID Connect), S3 storage, OpenTelemetry tracing, a second OCR engine (PaddleOCR). All optional, see [Optional features](#optional-features). |

## Quick start (5 commands)

Requirements: Docker with Compose v2.

```bash
git clone <this repository> invoicemind && cd invoicemind
cp .env.example .env            # then put your key in OPENAI_API_KEY=
docker compose up --build       # first build takes a few minutes
# open http://localhost
```

Sign in with a seeded demo account (password is `DEMO_PASSWORD` from `.env`, default `Demo12345!`):

| Account | Role |
|---|---|
| `admin@demo.invoicemind.local` | ADMIN: everything, plus team and knowledge management |
| `approver@demo.invoicemind.local` | APPROVER: sees the whole organisation, approves and rejects |
| `user@demo.invoicemind.local` | USER: uploads, sees only their own invoices |

Or register your own organisation: you become its admin and can add your team. Set `DEMO_PASSWORD=` (empty) to disable seeding.

Without an `OPENAI_API_KEY` the application still starts; uploads then end as **Failed** with the message
"OPENAI_API_KEY is not configured" instead of producing made-up data.

### Demo walkthrough

1. **Upload** the PDFs and scans from [`samples/invoices`](samples/invoices) (drag several at once). Watch each one move
   through *Uploaded → OCR + AI → Validated*, live.
2. Open an invoice marked **Needs review**: the validation panel says exactly why (for example "Subtotal 1000 + tax 190 =
   1190, but the total is 1500"), the offending field is highlighted, and the sums check updates as you type. Fix it and press
   *Save & revalidate*.
3. As **approver**, open *Approvals*, approve with a comment, or reject with a reason. Everything lands in the *Audit log*.
4. *Analysis* shows spend and quality metrics computed from your data. *Export* downloads the filtered set as XLSX/CSV/JSON.
5. As admin, add a policy under *Knowledge* (for example "invoices above 10,000 EUR need two approvals") and press
   *Why was this flagged?* on an invoice: the answer cites the passage it came from.

## Optional features

Off by default; each is switched on with environment variables (all listed, with comments, in [`.env.example`](.env.example)).
What "verified" means for each one is in [docs/STATUS.md](docs/STATUS.md).

| Feature | Switch on | Where it shows up |
|---|---|---|
| **Credit notes** | always on: the model reports `document_type`, a reviewer can change it | *Credit* badge and type filter in the list; amounts stored negative |
| **Currency conversion** | automatic with several currencies; `FX_RATES_ENABLED=false` for offline use | *Analysis* ("All in EUR"), *Settings* (reporting currency, manual rates) |
| **Webhooks** | admin: *Integrations* → Add webhook | `invoice.approved`, `.rejected`, `.validated`, `.needs_review`, `.failed`, HMAC-signed, retried, delivery log |
| **E-mail ingestion** | `MAIL_INGEST_ENABLED=true` + `MAIL_INGEST_ADDRESS`, `MAIL_IMAP_*` | *Settings* shows your address `invoices+<token>@your-domain`; `--profile mail` starts a local test mailbox |
| **Single sign-on** | `OIDC_ENABLED=true` + issuer, client id/secret; register `<app url>/api/v1/auth/oidc/callback` at the provider | "Continue with …" button on the login page |
| **S3 storage** | `STORAGE_TYPE=s3`; `docker compose --profile s3 up` starts SeaweedFS as the bundled store | invoice files in a bucket instead of a volume |
| **Tracing** | `TRACING_ENABLED=true`; `docker compose --profile tracing up` adds Jaeger on `127.0.0.1:16686` | one trace per upload across backend, Kafka and ai-service |
| **PaddleOCR** | build with `WITH_PADDLE=1`, run with `OCR_ENGINE=paddle` (`PADDLE_LANG=de`, `en`, `ru` ...) | second OCR engine; measured against Tesseract in [docs/OCR-COMPARISON.md](docs/OCR-COMPARISON.md) (same accuracy on the sample scans, much slower on CPU) |

### Webhooks: verifying a request

Every delivery carries `X-InvoiceMind-Signature: t=<unix seconds>,v1=<hex>` where `v1` is the HMAC-SHA256, keyed with the
secret shown once when you create the webhook, of `"<t>.<raw body>"`. Reject old timestamps (replay) and deduplicate on the
event's `id` (delivery is at-least-once; failed deliveries are retried with exponential backoff for about an hour). Webhook
URLs are requested by the server, so private, loopback and link-local addresses are refused and redirects are never followed
(`WEBHOOKS_ALLOW_PRIVATE_TARGETS=true` for a receiver inside your own network). A Node.js example is on the *Integrations* page.

### E-mail ingestion: how mail is accepted

Send or forward invoices to the address shown under *Settings*. A message is imported only if it was addressed to that exact
address (the plus-tag identifies your organisation; rotate it if it leaks) **and** the sender is an enabled user of your
organisation; the document is attributed to that user and goes to review like any upload, nothing is approved automatically.
`MAIL_REQUIRE_AUTH_RESULTS=true` additionally demands `dkim=pass` or `spf=pass` from your mail server, because the `From`
header alone can be forged. Works with any IMAP mailbox that supports plus-addressing (use an app password).

### Single sign-on: how accounts are linked

People must already have an account (the first SSO login is matched by *verified* e-mail and then pinned to the provider's
stable subject, so a reassigned address cannot take the account over), or set `OIDC_PROVISION_ORG_ID` and
`OIDC_ALLOWED_EMAIL_DOMAINS` to create accounts on first sign-in with `OIDC_DEFAULT_ROLE`. Roles are not mapped from provider
groups; an admin changes them under *Team*.

## Architecture

```mermaid
flowchart LR
  U[Browser<br/>Next.js 15] -->|HTTP, WebSocket| N[Nginx]
  N --> B[Spring Boot backend]
  B -->|invoice.uploaded| K[(Kafka)]
  K --> B
  B --> P[(PostgreSQL + pgvector)]
  B --> R[(Redis)]
  B -->|OCR, extract, embed, explain| A[ai-service<br/>FastAPI + Tesseract]
  A --> O((OpenAI API))
```

Details, the lifecycle diagram, the Kafka-vs-`@Async` decision and the trust boundaries are in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

| Part | Stack |
|---|---|
| `frontend/` | Next.js 15 (App Router), React 19, TypeScript, Tailwind v4, Radix UI, TanStack Query, Framer Motion, Recharts, STOMP.js, generated OpenAPI client |
| `backend/` | Spring Boot 3.4, Java 21, JPA + Flyway, Spring Security + JWT, Kafka, Redis, STOMP, Resilience4j, Apache POI, springdoc |
| `ai-service/` | Python 3.11, FastAPI, Pydantic v2, Tesseract + pdfplumber + Poppler, OpenAI Python SDK (Responses API) |
| infrastructure | PostgreSQL 16 with pgvector, Kafka (KRaft), Redis, Nginx, Docker Compose |

## Roles

| | USER | APPROVER | ADMIN |
|---|:-:|:-:|:-:|
| Upload, correct own invoices | ✓ | ✓ | ✓ |
| See other people's invoices, analytics for the organisation | – | ✓ | ✓ |
| Approve / reject | – | ✓ (not own uploads) | ✓ |
| Audit log, ask the knowledge base | – | ✓ | ✓ |
| Manage team, knowledge documents, delete invoices | – | – | ✓ |
| Settings (reporting currency, exchange rates, e-mail address), integrations (webhooks) | – | – | ✓ |

## Configuration

Everything is an environment variable; see [`.env.example`](.env.example) (secrets are never committed).

| Variable | Meaning |
|---|---|
| `OPENAI_API_KEY` | required for extraction and the knowledge base |
| `OPENAI_MODEL` / `OPENAI_REASONING_EFFORT` | extraction model (default `gpt-5.5`) and reasoning effort |
| `OPENAI_PRICE_INPUT_PER_1M` / `OPENAI_PRICE_OUTPUT_PER_1M` | only used to store an estimated cost per invoice |
| `OPENAI_EMBEDDING_MODEL` | knowledge-base embeddings (default `text-embedding-3-small`) |
| `JWT_SECRET`, `AI_SERVICE_TOKEN`, `POSTGRES_PASSWORD` | change the development values for anything real |
| `DEMO_PASSWORD` | password of the three seeded demo users; empty disables seeding |
| `HTTP_PORT` | published port (default 80) |
| `FX_RATES_ENABLED` | download ECB reference rates for currency conversion (default true; only dates are sent) |
| `STORAGE_TYPE`, `S3_*` | `local` volume (default) or an S3-compatible bucket |
| `MAIL_INGEST_ENABLED`, `MAIL_INGEST_ADDRESS`, `MAIL_IMAP_*`, `MAIL_REQUIRE_AUTH_RESULTS` | e-mail ingestion |
| `OIDC_ENABLED`, `OIDC_ISSUER_URI`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_PROVISION_ORG_ID`, ... | single sign-on |
| `WEBHOOKS_ALLOW_PRIVATE_TARGETS` | allow webhooks to private addresses (SSRF guard, default false) |
| `TRACING_ENABLED`, `JAEGER_UI_PORT` | OpenTelemetry tracing |
| `OCR_ENGINE`, `PADDLE_LANG`, build arg `WITH_PADDLE` | OCR engine (`tesseract` or `paddle`) |

Behind TLS, also set `APP_COOKIES_SECURE=true` for the backend so the refresh cookie gets the `Secure` flag.

## API

Interactive docs: **`/swagger-ui.html`** (OpenAPI at `/v3/api-docs`). Main groups: `auth`, `invoices` (upload, list with filters
and paging, detail, correct, approve, reject, retry, file, OCR text, audit), `analytics`, `export`, `users`, `audit`, `notifications`,
`knowledge`, `organization`/`fx` (reporting currency, rates), `webhooks`, `ingest/email`, `auth/oidc`. Live updates: STOMP over WebSocket at `/ws/live`.

## Development

```bash
# stack with ports exposed on 127.0.0.1 (backend :18080, ai-service :18000)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build

cd frontend && npm ci && npm run dev          # http://localhost:3000, proxies /api to the backend
```

### Tests

```bash
cd backend && mvn verify                                         # 150 tests; needs Docker (Testcontainers)
docker build --target test -t im-ai-test ai-service && docker run --rm im-ai-test   # 45 tests, real Tesseract/Poppler (add --build-arg WITH_PADDLE=1 for the PaddleOCR tests)
cd frontend && npm run lint && npm run typecheck && npm test     # 28 unit tests

# browser end-to-end on the full stack, with an offline AI test double (no API key, no cost)
docker compose -f docker-compose.yml -f docker-compose.e2e.yml up --build -d --wait
cd frontend && npx playwright install chromium && E2E_BASE_URL=http://localhost npx playwright test   # 11 scenarios
```

CI (`.github/workflows/ci.yml`) runs all of the above plus a check that the generated TypeScript API types match the backend.

The e2e overlay swaps the OpenAI-backed service for [`e2e/fake-ai`](e2e/fake-ai): real OCR and a rule-based extractor.
It exists only for tests and is never part of the normal stack.

### Measuring extraction quality

[`samples/`](samples) holds 16 synthetic invoices with ground truth: text PDFs and scans (rotation, noise, blur), EUR/USD/GBP/CHF/RUB,
English/German/Russian, decimal point and comma, a multi-page invoice, look-alike identifiers and a hidden prompt injection.

```bash
docker compose --profile eval run --rm eval       # real OCR + real model; writes docs/EVALUATION.md
node scripts/capture-screenshots.cjs              # seeds the demo org with the samples and captures screenshots
```

## Security notes

Upload hardening (magic bytes, size, active PDF content), untrusted-data prompts with nonce delimiters, schema-validated output,
grounding, organisation-scoped everything, rotating refresh tokens with theft detection, rate limiting, CSV/XLSX formula
neutralisation, audit trail. The full model is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#security-model).

## Known limitations

- **Real-LLM behaviour is not yet measured** (see the status note at the top); the evaluation set is synthetic and small, so its numbers will be indicative, not an SLA.
- Access tokens live 15 minutes: disabling a user or changing a role takes effect for already-issued tokens at their expiry (refresh is blocked immediately). An open WebSocket stays authorised until it reconnects.
- Zero-value invoices are rejected by validation. Currency conversion uses one rate per invoice date (no hedging, no month-end revaluation); the ECB does not publish every currency, those need manual rates.
- Webhook delivery is at-least-once, so receivers must deduplicate on the event id. The signing secret is stored in the database in clear (it must be, to sign); protect database access accordingly.
- E-mail ingestion polls one mailbox every minute (no IMAP IDLE) and trusts the `From` address unless `MAIL_REQUIRE_AUTH_RESULTS=true`.
- Single sign-on maps no roles from provider groups, and sign-out does not end the session at the provider.
- PaddleOCR uses one model per language (`PADDLE_LANG=de`, `ru` ...), unlike Tesseract's combined `eng+deu+rus`; the image is much larger with it.
- Kafka and Redis run as single nodes.
- Tesseract languages are English, German and Russian; other scripts need more language packs in the `ai-service` image.
- The PDF active-content scan is a keyword check and cannot look inside compressed streams; PDFs are only ever rendered by the user's own browser.
- The compose file is for a single host. Terminate TLS in front of Nginx for anything public.

## Roadmap

Provider-group to role mapping for SSO, IMAP IDLE, connectors for specific ERP systems (the webhooks are the generic hook), per-organisation mailboxes and identity providers, OpenTelemetry metrics, scanned credit-note samples in the evaluation set.

## Repository layout

```text
backend/      Spring Boot API, pipeline, validation, knowledge base      (+ Flyway migrations, tests)
ai-service/   OCR and every OpenAI call                                   (+ pytest suite)
frontend/     Next.js application                                         (+ vitest, Playwright)
samples/      synthetic invoices, ground truth, generator
eval/         accuracy evaluation harness
e2e/          offline AI test double for browser tests
docker/       Nginx configuration
docs/         ARCHITECTURE, STATUS, EVALUATION and OCR-COMPARISON (generated), AUDIT-PHASE0 (history)
scripts/      screenshot capture
```
