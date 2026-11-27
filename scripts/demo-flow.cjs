/**
 * Walks the demo organisation through the whole accounts-payable flow on a running stack, through the real API and
 * a real browser, and captures the README screenshots:
 *
 *   1. an invoice arrives by e-mail (sent by the supplier, not by a user) and fails a check (printed total is wrong)
 *   2. the accountant corrects it and confirms the new vendor
 *   3. an approver approves it (the amount needs one approval; a larger invoice needs two different approvers)
 *   4. the accountant exports the approved invoices as a Xero bill import, each invoice exactly once
 *
 * Usage (stack running with DEMO_PASSWORD set, so the demo users exist; e-mail ingestion configured):
 *
 *   node scripts/demo-flow.cjs [baseUrl] [screenshotDir]
 *
 * The e-mail step: with MAIL_DROP=<dir> the supplier's message is written there as an .eml file (for a local test
 * mailbox that delivers dropped files); otherwise send samples/demo/demo-email-invoice.pdf to the address the script
 * prints, from any address, and it waits for the mail to be imported (up to 10 minutes).
 * Needs the frontend dependencies installed (uses Playwright from frontend/node_modules).
 */
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require(path.resolve(__dirname, "../frontend/node_modules/@playwright/test"));

const BASE = process.argv[2] ?? "http://localhost";
const OUT = path.resolve(process.argv[3] ?? path.join(__dirname, "../docs/screenshots"));
const DEMO = path.resolve(__dirname, "../samples/demo");
const DOMAIN = "demo.invoicemind.local";
const PASSWORD = process.env.DEMO_PASSWORD ?? "Demo12345!";
const SUPPLIER = "billing@brightline-office.example";

async function api(token, method, url, body) {
  const res = await fetch(BASE + url, {
    method,
    headers: { ...(token && { Authorization: `Bearer ${token}` }), ...(body && !(body instanceof FormData) && { "Content-Type": "application/json" }) },
    body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`${method} ${url} -> ${res.status} ${await res.text()}`);
  return res.status === 204 ? null : res.json().catch(() => null);
}

const login = async (who) => (await api(null, "POST", "/api/v1/auth/login", { email: `${who}@${DOMAIN}`, password: PASSWORD })).accessToken;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function findInvoice(token, number) {
  const { items } = await api(token, "GET", "/api/v1/invoices?size=100");
  return items.find((i) => i.invoiceNumber === number) ?? null;
}

async function waitFor(what, fn, seconds) {
  for (let i = 0; i < seconds; i++) {
    const v = await fn();
    if (v) return v;
    await sleep(1000);
  }
  throw new Error(`timed out waiting for ${what}`);
}

function eml(to, file) {
  const pdf = fs.readFileSync(path.join(DEMO, file)).toString("base64").replace(/.{76}/g, "$&\r\n");
  return [
    `From: Brightline Billing <${SUPPLIER}>`, `To: ${to}`, "Subject: Invoice BOS-2291", `Date: ${new Date().toUTCString()}`,
    `Message-ID: <bos-2291-${Date.now()}@brightline-office.example>`, "MIME-Version: 1.0",
    'Content-Type: multipart/mixed; boundary="b1"', "", "--b1", "Content-Type: text/plain; charset=utf-8", "",
    "Hello, please find our invoice attached. Kind regards, Brightline", "", "--b1",
    `Content-Type: application/pdf; name="${file}"`, "Content-Transfer-Encoding: base64", `Content-Disposition: attachment; filename="${file}"`,
    "", pdf, "--b1--", "",
  ].join("\r\n");
}

async function approveInBrowser(page, invoiceId) {
  await page.goto(`${BASE}/invoices/${invoiceId}`);
  await page.getByRole("button", { name: "Approve" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Approve" }).click();
  await page.getByText("Invoice approved").waitFor();
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const [admin, accountant, approver2] = [await login("admin"), await login("accountant"), await login("approver2")];

  // 1. the supplier e-mails the invoice to the organisation's intake address; two more are uploaded
  let mailed = await findInvoice(accountant, "BOS-2291");
  if (!mailed) {
    const intake = await api(admin, "GET", "/api/v1/ingest/email");
    if (!intake.enabled || !intake.address) throw new Error("E-mail ingestion is not configured (MAIL_INGEST_* in .env)");
    if (process.env.MAIL_DROP) {
      fs.writeFileSync(path.join(process.env.MAIL_DROP, `bos-2291-${Date.now()}.eml`), eml(intake.address, "demo-email-invoice.pdf"));
    } else {
      console.log(`Send samples/demo/demo-email-invoice.pdf to ${intake.address} now; waiting for it to be imported ...`);
    }
    for (const file of ["demo-hosting.pdf", "demo-courier.pdf"]) {
      const form = new FormData();
      form.append("file", new Blob([fs.readFileSync(path.join(DEMO, file))], { type: "application/pdf" }), file);
      await api(accountant, "POST", "/api/v1/invoices", form);
    }
    mailed = await waitFor("the e-mailed invoice", () => findInvoice(accountant, "BOS-2291"), 600);
  }
  await waitFor("processing to finish", async () =>
    (await api(accountant, "GET", "/api/v1/invoices?status=UPLOADED&status=PROCESSING&status=EXTRACTED&size=1")).totalItems === 0, 120);
  mailed = await findInvoice(accountant, "BOS-2291");

  // CHROMIUM_PATH: use an installed Chromium when the Playwright version has not downloaded its own
  const browser = await chromium.launch({ args: ["--lang=en-US"], ...(process.env.CHROMIUM_PATH && { executablePath: process.env.CHROMIUM_PATH }) });
  const signIn = async (who) => {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2, locale: "en-US" });
    const page = await ctx.newPage();
    await page.goto(`${BASE}/login`);
    await page.fill("#email", `${who}@${DOMAIN}`);
    await page.fill("#password", PASSWORD);
    await page.click("button[type=submit]");
    await page.waitForURL("**/invoices");
    return page;
  };
  const shot = (page, name) => page.screenshot({ path: path.join(OUT, `${name}.png`) });

  // 2. the accountant sees what needs attention and fixes the one failed check
  const acc = await signIn("accountant");
  await acc.waitForSelector("tbody tr");
  await acc.waitForTimeout(1200);
  await shot(acc, "flow-1-inbox");
  await acc.goto(`${BASE}/invoices/${mailed.id}`);
  await acc.waitForSelector("#f-total");
  await acc.waitForTimeout(1200);
  await shot(acc, "flow-2-review");
  if (mailed.status === "NEEDS_REVIEW") {
    await acc.fill("#f-total", "1475.60");
    await acc.getByRole("button", { name: /Save & revalidate/ }).click();
    await acc.getByText(/^Saved/).first().waitFor();
  }
  for (const number of ["BOS-2291", "HCH-7781", "MCS-10442"]) {
    const inv = await findInvoice(accountant, number);
    const detail = inv && (await api(accountant, "GET", `/api/v1/invoices/${inv.id}`));
    if (detail?.vendor?.id && detail.vendor.status !== "VERIFIED") await api(accountant, "POST", `/api/v1/vendors/${detail.vendor.id}/verify`);
  }

  // 3. an approver approves; the large hosting invoice also needs a second, different approver
  const app = await signIn("approver");
  await app.goto(`${BASE}/invoices/${mailed.id}`);
  await app.getByRole("button", { name: "Approve" }).waitFor();
  await app.waitForTimeout(1200);
  await shot(app, "flow-3-approve");
  await approveInBrowser(app, mailed.id);
  const hosting = await findInvoice(accountant, "HCH-7781");
  if (hosting?.status === "VALIDATED") {
    await approveInBrowser(app, hosting.id);
    await api(approver2, "POST", `/api/v1/invoices/${hosting.id}/approve`, { comment: "Second approval: within budget" });
  }

  // 4. the accountant hands the approved invoices to accounting, each exactly once
  await acc.goto(`${BASE}/export`);
  await acc.getByText(/approved invoices? waiting/).waitFor();
  await acc.fill("#acct-account", "429");
  await acc.waitForTimeout(800);
  await shot(acc, "flow-4-export");
  await Promise.all([acc.waitForEvent("download"), acc.getByRole("button", { name: "Export to Xero" }).click()]);
  await acc.getByText("No approved invoices are waiting.").waitFor();

  await browser.close();
  console.log(`Screenshots in ${OUT}`);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
