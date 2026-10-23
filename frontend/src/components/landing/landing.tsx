import Link from "next/link";

import { Logo } from "@/components/brand/logo";

import { ConveyorScene } from "./conveyor-scene";

export const REPO_URL = "https://github.com/maxprogerit/InvoiceMind-AI";

const eyebrow = "font-mono text-xs font-medium uppercase tracking-[0.2em] text-muted";

const STEPS = [
  {
    name: "Read",
    text: "PDFs with a text layer are read directly. Scans and photos go through Tesseract, in English, German or Russian.",
  },
  {
    name: "Extract",
    text: "A language model returns structured fields. Every value has to appear in the document text, and the model is told that the text is data, not instructions.",
  },
  {
    name: "Verify",
    text: "Plain Java checks sums, tax, dates, currency and duplicates, and flags suspected prompt injection. No model is involved in this step.",
  },
  {
    name: "Decide",
    text: "A person reviews what was flagged. Approval needs a second reviewer, every change goes into the audit trail, and the status updates live over WebSocket.",
  },
] as const;

const ALSO = [
  ["Credit notes", "A document type of their own, stored with negative amounts."],
  [
    "Currencies",
    "Reports convert at the ECB rate of each invoice's date. What cannot be converted is labelled as not converted, never guessed.",
  ],
  ["Policy questions", "Upload your rules and ask why an invoice was flagged. The answer cites the passages it came from."],
  ["Analytics", "Spend by vendor and month, review rate, processing time and AI cost."],
  ["Integrations", "Optional: signed webhooks, an IMAP mailbox, OIDC single sign-on, S3 storage, OpenTelemetry tracing, PaddleOCR."],
] as const;

const SAMPLE_FIELDS = [
  ["Vendor", "E2E Fixtures GmbH"],
  ["Invoice number", "E2E-1002"],
  ["Subtotal", "1,200.00"],
  ["Tax", "228.00"],
  ["Total", "1,500.00"],
] as const;

/** The public landing page. Server-rendered markup only; the one moving thing is the conveyor drawing. */
export function Landing() {
  return (
    <div className="relative min-h-dvh overflow-x-hidden bg-bg text-fg">
      <header className="mx-auto flex max-w-[1440px] items-center justify-between px-6 py-5 lg:px-12">
        <Link href="/" aria-label="InvoiceMind home">
          <Logo />
        </Link>
        <nav aria-label="Account" className="flex items-center gap-1 whitespace-nowrap font-mono text-[12px] sm:gap-2 sm:text-[13px]">
          <Link href="/login" className="rounded-control px-2.5 py-2 text-muted transition-colors hover:text-fg sm:px-3.5">
            Sign in
          </Link>
          <Link
            href="/register"
            className="rounded-control border border-line-strong px-2.5 py-2 text-fg transition-colors hover:border-accent/60 hover:bg-surface-2 sm:px-3.5"
          >
            Create account
          </Link>
        </nav>
      </header>

      <main>
        <section className="relative">
          <div
            className="pointer-events-none absolute inset-0 bg-[radial-gradient(55%_60%_at_72%_45%,rgb(255_107_87/0.10),transparent_70%)]"
            aria-hidden
          />
          <div className="relative mx-auto grid max-w-[1440px] items-center gap-10 px-6 pb-16 pt-6 lg:min-h-[640px] lg:grid-cols-[35fr_65fr] lg:gap-6 lg:px-12 lg:pb-24">
            <div>
              <p className="font-mono text-xs font-medium uppercase tracking-[0.3em] text-accent">InvoiceMind AI</p>
              <h1 className="mt-5 text-[clamp(2.5rem,6.2vw,3.5rem)] font-semibold leading-[1.05] tracking-tight">
                Every invoice runs the same gate.
              </h1>
              <p className="mt-5 max-w-[440px] text-[17px] leading-relaxed text-muted">
                Sums, tax, dates, duplicates — checked the moment it arrives. Anything that doesn&apos;t pass gets diverted to a human.
              </p>
              <div className="mt-8 flex flex-wrap gap-3">
                <a
                  href="#sample"
                  className="inline-flex h-12 items-center rounded-control bg-accent px-6 font-mono text-sm font-medium text-accent-fg transition-colors hover:bg-accent-hover"
                >
                  See a sample
                </a>
                <a
                  href={REPO_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex h-12 items-center rounded-control border border-line-strong px-6 font-mono text-sm font-medium text-fg transition-colors hover:border-accent/60 hover:bg-surface-2"
                >
                  View on GitHub
                </a>
              </div>
            </div>
            <ConveyorScene />
          </div>
        </section>

        <section id="sample" className="border-t border-line">
          <div className="mx-auto grid max-w-[1440px] gap-10 px-6 py-16 lg:grid-cols-[35fr_65fr] lg:gap-6 lg:px-12 lg:py-24">
            <div>
              <p className={eyebrow}>A sample</p>
              <h2 className="mt-4 text-3xl font-semibold leading-tight tracking-tight">The total does not match the lines.</h2>
              <p className="mt-4 max-w-[440px] text-[17px] leading-relaxed text-muted">
                The fixture <span className="font-mono text-[15px] text-fg">e2e-mismatch.pdf</span> in the repository states a subtotal of
                1,200.00 and tax of 228.00, but a total of 1,500.00. The model&apos;s reading is not taken on trust: the sums are recomputed
                in Java, and a document that fails goes to a person instead of onward.
              </p>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <div className="rounded-card border border-line bg-surface p-6">
                <p className={eyebrow}>Read from the document</p>
                <dl className="mt-4 divide-y divide-line font-mono text-sm">
                  {SAMPLE_FIELDS.map(([k, v]) => (
                    <div key={k} className="flex justify-between gap-4 py-2.5">
                      <dt className="text-muted">{k}</dt>
                      <dd className={k === "Total" ? "font-semibold text-warn" : "text-fg"}>{v}</dd>
                    </div>
                  ))}
                </dl>
              </div>
              <div className="rounded-card border border-warn/40 bg-surface p-6">
                <p className="font-mono text-xs font-medium uppercase tracking-[0.2em] text-warn">Needs review</p>
                <p className="mt-4 font-mono text-sm leading-relaxed text-fg">
                  Subtotal 1200.00 + tax 228.00 = 1428.00, but the total is 1500.00
                </p>
                <p className="mt-5 text-[15px] leading-relaxed text-muted">
                  The invoice cannot be approved while this stands. A reviewer corrects the total, the checks run again, and a second person
                  approves. Each step is in the audit trail.
                </p>
              </div>
            </div>
          </div>
        </section>

        <section className="border-t border-line">
          <div className="mx-auto max-w-[1440px] px-6 py-16 lg:px-12 lg:py-24">
            <p className={eyebrow}>How an invoice moves</p>
            <ol className="mt-8 grid gap-8 md:grid-cols-2 lg:grid-cols-4 lg:gap-0">
              {STEPS.map((step, i) => (
                <li key={step.name} className="relative lg:pr-10">
                  <div className="flex items-center gap-3">
                    <span className="grid size-8 shrink-0 place-items-center rounded-full border border-line-strong bg-surface font-mono text-xs text-fg">
                      {i + 1}
                    </span>
                    {i < STEPS.length - 1 && <span className="hidden h-px flex-1 bg-line-strong lg:block" aria-hidden />}
                  </div>
                  <h3 className="mt-4 text-xl font-semibold tracking-tight">{step.name}</h3>
                  <p className="mt-2 text-[15px] leading-relaxed text-muted">{step.text}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="border-t border-line">
          <div className="mx-auto grid max-w-[1440px] gap-10 px-6 py-16 lg:grid-cols-[35fr_65fr] lg:gap-6 lg:px-12 lg:py-24">
            <div>
              <p className={eyebrow}>What it also does</p>
              <p className="mt-6 max-w-[440px] text-[15px] leading-relaxed text-muted">
                On 18 synthetic documents, GPT-5.5 extracted all 18 completely correctly. That is a small smoke test of the pipeline on
                invented invoices, not a benchmark, and real invoices are messier.
              </p>
            </div>
            <dl className="divide-y divide-line border-y border-line">
              {ALSO.map(([term, text]) => (
                <div key={term} className="grid gap-1 py-4 md:grid-cols-[180px_1fr] md:gap-6">
                  <dt className="font-medium">{term}</dt>
                  <dd className="text-[15px] leading-relaxed text-muted">{text}</dd>
                </div>
              ))}
            </dl>
          </div>
        </section>
      </main>

      <footer className="border-t border-line">
        <div className="mx-auto flex max-w-[1440px] flex-wrap items-center justify-between gap-4 px-6 py-8 font-mono text-[13px] text-muted lg:px-12">
          <p>Every invoice runs the same gate, with a human signing off before anything is approved.</p>
          <nav aria-label="Footer" className="flex gap-5">
            <Link href="/login" className="transition-colors hover:text-fg">
              Sign in
            </Link>
            <Link href="/register" className="transition-colors hover:text-fg">
              Create account
            </Link>
            <a href={REPO_URL} target="_blank" rel="noopener noreferrer" className="transition-colors hover:text-fg">
              GitHub
            </a>
          </nav>
        </div>
      </footer>
    </div>
  );
}
