import { expect, test, type Page } from "@playwright/test";
import path from "node:path";

const FIXTURES = path.resolve(__dirname, "../../samples/e2e");
const SAMPLES = path.resolve(__dirname, "../../samples/invoices");
const suffix = Math.random().toString(36).slice(2, 8);
const PASSWORD = "Passw0rd!long";

/**
 * One fresh organisation per run, so the test is repeatable and never collides with duplicate detection.
 * The ai-service in this stack is the rule-based test double (docker-compose.e2e.yml): real OCR, no LLM.
 */
test.describe.serial("invoice lifecycle in the browser", () => {
  let page: Page;
  const adminEmail = `admin-${suffix}@e2e.example.com`;
  const userEmail = `user-${suffix}@e2e.example.com`;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
  });
  test.afterAll(async () => {
    await page.close();
  });

  test("registers a new organisation and lands on an empty invoice list", async () => {
    await page.goto("/register");
    await page.fill("#org", `E2E Company ${suffix}`);
    await page.fill("#name", "Eve Tester");
    await page.fill("#email", adminEmail);
    await page.fill("#password", PASSWORD);
    await page.click("button[type=submit]");
    await page.waitForURL("**/invoices");
    await expect(page.getByText("Upload your first invoice")).toBeVisible();
    await expect(page.getByRole("link", { name: /Approvals/ })).toBeVisible(); // first user is the admin
  });

  test("uploading shows live pipeline progress and the right outcome per document", async () => {
    await page.goto("/upload");
    await page.setInputFiles("input[type=file]", [path.join(FIXTURES, "e2e-valid.pdf"), path.join(FIXTURES, "e2e-mismatch.pdf")]);
    const rows = page.locator("ul[aria-label=Uploads] > li");
    await expect(rows).toHaveCount(2);
    await expect(rows.filter({ hasText: "e2e-valid.pdf" }).getByText("Validated", { exact: true }).first()).toBeVisible();
    await expect(rows.filter({ hasText: "e2e-mismatch.pdf" }).getByText("Needs review", { exact: true }).first()).toBeVisible();
    await expect(rows.filter({ hasText: "e2e-valid.pdf" }).getByText("ACME", { exact: false }).or(rows.filter({ hasText: "E2E Fixtures GmbH" })).first()).toBeVisible();
  });

  test("the invoice list shows extracted data and a document preview", async () => {
    await page.goto("/invoices");
    const row = page.locator("tbody tr", { hasText: "E2E-1001" });
    await expect(row).toBeVisible();
    await expect(row).toContainText("1,428.00");
    await row.click();
    await expect(page.getByText("INVOICE DETAILS", { exact: false })).toBeVisible();
    await expect(page.locator("iframe[title^='Document']")).toBeVisible();
  });

  test("a mismatching total is flagged, fixed by a human and then approved", async () => {
    await page.goto("/invoices");
    await page.locator("tbody tr", { hasText: "E2E-1002" }).click();
    await page.getByRole("link", { name: /Review & edit/ }).click();
    await page.waitForURL(/\/invoices\/[0-9a-f-]{36}$/);

    await expect(page.getByText("Subtotal 1200.00 + tax 228.00 = 1428.00, but the total is 1500.00")).toBeVisible();
    await expect(page.getByRole("button", { name: "Approve" })).toHaveCount(0); // not approvable while invalid
    await page.fill("#f-total", "1428.00");
    await page.getByRole("button", { name: /Save & revalidate/ }).click();
    await expect(page.getByText("Saved. All checks passed")).toBeVisible();

    await page.getByRole("button", { name: "Approve" }).click();
    await page.getByRole("dialog").locator("textarea").fill("Checked against the contract");
    await page.getByRole("dialog").getByRole("button", { name: "Approve" }).click();
    await expect(page.getByText("Invoice approved")).toBeVisible();
    await expect(page.getByText("Checked against the contract").first()).toBeVisible(); // shown in the processing card and the history
    await expect(page.getByText("Fields corrected")).toBeVisible(); // audit history
  });

  test("approvals queue, analytics and exports reflect what happened", async () => {
    await page.goto("/approvals");
    await expect(page.getByText(/Ready to approve \(1\)/)).toBeVisible(); // the other, valid invoice

    await page.goto("/analytics");
    await expect(page.getByText("€1,428.00").first()).toBeVisible(); // approved spend KPI

    await page.goto("/export");
    await expect(page.getByText(/2 invoices will be exported/)).toBeVisible();
    const [download] = await Promise.all([page.waitForEvent("download"), page.getByRole("button", { name: "Download" }).first().click()]);
    expect(download.suggestedFilename()).toMatch(/^invoices-\d{4}-\d{2}-\d{2}\.xlsx$/);
    const stream = await download.createReadStream();
    const chunks: Buffer[] = [];
    for await (const chunk of stream) chunks.push(chunk as Buffer);
    expect(Buffer.concat(chunks).subarray(0, 2).toString()).toBe("PK"); // a real zip/xlsx container
  });

  test("a rules document explains why an invoice was flagged, with its source", async () => {
    await page.goto("/knowledge");
    await expect(page.getByText(/No documents yet/)).toBeVisible();
    await page.getByRole("button", { name: /Add document/ }).first().click();
    await page.fill("#k-title", "Totals policy");
    await page.fill("#k-content", "Totals policy: the subtotal plus tax must always equal the invoice total. If the total does not match, return the invoice to the supplier for correction.");
    await page.getByRole("dialog").getByRole("button", { name: "Add document" }).click();
    await expect(page.getByText("1 passages")).toBeVisible();

    // the same mismatching document again: a flagged duplicate that needs review
    await page.goto("/upload");
    await page.setInputFiles("input[type=file]", path.join(FIXTURES, "e2e-mismatch.pdf"));
    await page.getByRole("link", { name: /Review now/ }).click();
    await page.waitForURL(/\/invoices\/[0-9a-f-]{36}$/);
    await page.getByRole("button", { name: "Why was this flagged?" }).click();
    await expect(page.getByText(/Per rule/)).toBeVisible();
    await expect(page.getByRole("link", { name: "Source 1" })).toBeVisible(); // [1] is rendered as a citation chip
    await expect(page.locator("#source-1")).toContainText("Totals policy");
  });

  test("a credit note is recognised, stored negative and can be approved", async () => {
    await page.goto("/upload");
    await page.setInputFiles("input[type=file]", path.join(SAMPLES, "17-credit-note-minus.pdf"));
    await page.getByRole("link", { name: "Open", exact: true }).click();
    await page.waitForURL(/\/invoices\/[0-9a-f-]{36}$/);

    await expect(page.getByRole("radio", { name: "Credit note" })).toHaveAttribute("aria-checked", "true");
    await expect(page.locator("#f-total")).toHaveValue("-523.60");
    await expect(page.locator("#f-invoiceNumber")).toHaveValue("NL-CN-0031");
    await expect(page.getByText("Credit note number")).toBeVisible();

    await page.getByRole("button", { name: "Approve" }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Approve" }).click();
    await expect(page.getByText("Invoice approved")).toBeVisible();

    await page.goto("/invoices");
    const row = page.locator("tbody tr", { hasText: "NL-CN-0031" });
    await expect(row.getByText("Credit", { exact: true })).toBeVisible();
    await expect(row).toContainText("-523.60");
    await page.getByRole("button", { name: "Credit notes" }).click();
    await expect(page.locator("tbody tr")).toHaveCount(1); // the type filter hides the invoices
  });

  test("an admin sets the reporting currency and manages a manual exchange rate", async () => {
    await page.goto("/settings");
    await expect(page.getByText("Reporting currency")).toBeVisible();
    await page.fill("#base-currency", "USD");
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(page.getByText("Reporting currency is now USD")).toBeVisible();

    await page.fill("#rate-currency", "RUB");
    await page.fill("#rate-value", "100");
    await page.getByRole("button", { name: /Add rate/ }).click();
    await expect(page.getByText("Rate saved")).toBeVisible();
    await expect(page.locator("tbody tr", { hasText: "RUB" })).toContainText("100");
    await page.getByRole("button", { name: /Delete the RUB rate/ }).click();
    await expect(page.getByText("No manual rates yet.")).toBeVisible();

    await page.fill("#base-currency", "EUR");
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await expect(page.getByText("Reporting currency is now EUR")).toBeVisible();
  });

  test("an admin adds a plain user, who sees a restricted application", async () => {
    await page.goto("/team");
    await page.getByRole("button", { name: /Add member/ }).first().click();
    await page.fill("#m-name", "Una User");
    await page.fill("#m-email", userEmail);
    await page.fill("#m-pass", PASSWORD);
    await page.getByRole("dialog").getByRole("button", { name: "Add member" }).click();
    await expect(page.getByText(userEmail)).toBeVisible();

    await page.getByRole("button", { name: "Sign out" }).click();
    await page.waitForURL("**/login");
    await page.fill("#email", userEmail);
    await page.fill("#password", PASSWORD);
    await page.click("button[type=submit]");
    await page.waitForURL("**/invoices");

    await expect(page.getByRole("link", { name: "Team" })).toHaveCount(0);
    await expect(page.getByRole("link", { name: /Approvals/ })).toHaveCount(0);
    await expect(page.getByText("Nothing here yet")).toBeVisible(); // sees none of the admin's invoices
    await page.goto("/team");
    await page.waitForURL("**/invoices"); // route guard
  });

  test("an expired session returns the visitor to sign-in", async () => {
    await page.context().clearCookies();
    await page.goto("/analytics");
    await page.waitForURL(/\/login\?next=%2Fanalytics/);
  });
});
