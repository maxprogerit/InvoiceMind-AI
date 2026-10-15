/**
 * Seeds the demo organisation with the sample invoices through the real API and captures README
 * screenshots with Chromium. Usage (stack running, demo user seeded):
 *
 *   node scripts/capture-screenshots.cjs [baseUrl] [outDir]
 *
 * Needs the frontend dependencies installed (uses Playwright from frontend/node_modules).
 */
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require(path.resolve(__dirname, "../frontend/node_modules/@playwright/test"));

const BASE = process.argv[2] ?? "http://localhost";
const OUT = path.resolve(process.argv[3] ?? path.join(__dirname, "../docs/screenshots"));
const SAMPLES = path.resolve(__dirname, "../samples/invoices");
const EMAIL = process.env.DEMO_EMAIL ?? "admin@demo.invoicemind.local";
const PASSWORD = process.env.DEMO_PASSWORD ?? "Demo12345!";
const RULES =
  "Approval policy: invoices above 10,000 EUR need two approvals. Totals policy: the subtotal plus tax must always equal the invoice total; " +
  "when the total does not match, return the invoice to the supplier for correction. Duplicate policy: an invoice number can be paid only once per supplier, " +
  "reject repeated submissions. Every supplier invoice must reference a purchase order number.";

async function api(token, method, url, body) {
  const res = await fetch(BASE + url, {
    method,
    headers: { ...(token && { Authorization: `Bearer ${token}` }), ...(body && !(body instanceof FormData) && { "Content-Type": "application/json" }) },
    body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok && res.status !== 404) throw new Error(`${method} ${url} -> ${res.status} ${await res.text()}`);
  return res.status === 204 ? null : res.json().catch(() => null);
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const { accessToken: token } = await api(null, "POST", "/api/v1/auth/login", { email: EMAIL, password: PASSWORD });

  const existing = (await api(token, "GET", "/api/v1/invoices?size=1")).totalItems;
  if (existing === 0) {
    for (const file of fs.readdirSync(SAMPLES).sort()) {
      const form = new FormData();
      form.append("file", new Blob([fs.readFileSync(path.join(SAMPLES, file))]), file);
      await api(token, "POST", "/api/v1/invoices", form);
    }
    for (let i = 0; i < 90; i++) {
      const inFlight = (await api(token, "GET", "/api/v1/invoices?status=UPLOADED&status=PROCESSING&status=EXTRACTED&size=1")).totalItems;
      if (inFlight === 0) break;
      await new Promise((r) => setTimeout(r, 1000));
    }
    // approve a few validated invoices (the demo admin approves; the uploader is the same admin, which is allowed for ADMIN)
    const validated = (await api(token, "GET", "/api/v1/invoices?status=VALIDATED&size=3")).items;
    for (const inv of validated) await api(token, "POST", `/api/v1/invoices/${inv.id}/approve`, { comment: "Checked against the purchase order" });
  }
  if ((await api(token, "GET", "/api/v1/knowledge/documents")).length === 0) {
    await api(token, "POST", "/api/v1/knowledge/documents", { title: "Accounts payable policy", content: RULES });
  }

  // the new headless mode ships the PDF viewer (the headless shell shows PDFs as blank); --lang keeps the browser UI in English
  const browser = await chromium.launch({ channel: "chromium", args: ["--lang=en-US"] });
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: "en-US" })).newPage();
  const shot = (name, opts = {}) => page.screenshot({ path: path.join(OUT, `${name}.png`), ...opts });

  await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded" });
  await page.waitForSelector("#email");
  await page.waitForTimeout(1600);
  await shot("login");

  await page.fill("#email", EMAIL);
  await page.fill("#password", PASSWORD);
  await page.click("button[type=submit]");
  await page.waitForURL("**/invoices");
  await page.waitForSelector("tbody tr");
  await page.waitForTimeout(2200);
  await shot("invoices");

  const review = page.locator("tbody tr", { hasText: "Needs review" }).first();
  if (await review.count()) {
    await review.click();
    await page.getByRole("link", { name: /Review & edit/ }).click();
    await page.waitForURL(/\/invoices\/[0-9a-f-]{36}$/);
    await page.waitForSelector("text=Extracted data");
    await page.waitForTimeout(2500);
    const explain = page.getByRole("button", { name: "Why was this flagged?" });
    if (await explain.count()) {
      await explain.click().catch(() => {});
      await page.waitForTimeout(2500);
    }
    await shot("review", { fullPage: true });
  }

  await page.goto(`${BASE}/analytics`, { waitUntil: "domcontentloaded" });
  await page.waitForSelector("text=Approved spend");
  await page.waitForTimeout(2000);
  await shot("analytics", { fullPage: true });

  await page.goto(`${BASE}/approvals`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(2000);
  await shot("approvals");

  await page.goto(`${BASE}/knowledge`, { waitUntil: "domcontentloaded" });
  await page.fill("input[aria-label=Question]", "What happens when the total does not match the subtotal plus tax?");
  await page.getByRole("button", { name: "Ask" }).click();
  await page.waitForTimeout(3500);
  await shot("knowledge");

  await page.goto(`${BASE}/upload`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(1200);
  await shot("upload");

  await browser.close();
  console.log(`Screenshots written to ${OUT}`);
})().catch((e) => {
  console.error("FAILED:", e.message);
  process.exit(1);
});
