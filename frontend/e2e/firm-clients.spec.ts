import { expect, test, type Page } from "@playwright/test";
import path from "node:path";

const FIXTURES = path.resolve(__dirname, "../../samples/e2e");
const suffix = Math.random().toString(36).slice(2, 8);
const PASSWORD = "Passw0rd!long";

/**
 * An accounting firm with two client companies: the accountant creates both, uploads an invoice into each, switches
 * between them and must only ever see the data of the company they are working in.
 * The ai-service in this stack is the rule-based test double (docker-compose.e2e.yml).
 */
test.describe.serial("firm with two client companies", () => {
  let page: Page;
  const alpha = `Alpha Bakery ${suffix}`;
  const beta = `Beta Logistics ${suffix}`;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
  });
  test.afterAll(async () => {
    await page.close();
  });

  async function addClient(name: string) {
    await page.getByRole("button", { name: "Add client" }).click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("Company name").fill(name);
    await dialog.getByRole("button", { name: "Add client" }).click();
    await expect(page.getByTestId("client-row").filter({ hasText: name })).toBeVisible();
  }

  async function switchTo(name: string) {
    await page.getByRole("button", { name: /Client company:/ }).click();
    await page.getByRole("combobox", { name: /Search client/ }).fill(name.split(" ")[0]!);
    await page.getByRole("option", { name: new RegExp(name) }).click();
    await expect(page.getByRole("button", { name: new RegExp(`Client company: ${name}`) })).toBeVisible();
  }

  async function upload(file: string, outcome: string) {
    await page.goto("/upload");
    await page.setInputFiles("input[type=file]", path.join(FIXTURES, file));
    await expect(page.locator("ul[aria-label=Uploads] > li").filter({ hasText: file }).getByText(outcome, { exact: true }).first()).toBeVisible();
  }

  test("registers a firm: with one client there is no switcher yet", async () => {
    await page.goto("/register");
    await page.fill("#org", `Firm ${suffix}`);
    await page.fill("#name", "Eve Accountant");
    await page.fill("#email", `eve-${suffix}@e2e.example.com`);
    await page.fill("#password", PASSWORD);
    await page.click("button[type=submit]");
    await page.waitForURL("**/invoices");
    await expect(page.getByRole("link", { name: "Firm clients" })).toBeVisible();
    await expect(page.getByRole("button", { name: /Client company:/ })).toHaveCount(0);
  });

  test("creates two client companies; the switcher appears", async () => {
    await page.goto("/firm/clients");
    await addClient(alpha);
    await addClient(beta);
    await expect(page.getByTestId("client-row")).toHaveCount(3); // the firm's own company and the two clients
    await expect(page.getByRole("button", { name: /Client company:/ })).toBeVisible();
  });

  test("opens the first client from the table and uploads an invoice into it", async () => {
    await page.getByTestId("client-row").filter({ hasText: alpha }).getByRole("button", { name: new RegExp(alpha) }).click();
    await page.waitForURL("**/invoices");
    await expect(page.getByRole("button", { name: new RegExp(`Client company: ${alpha}`) })).toBeVisible();
    await expect(page.getByText("Upload your first invoice")).toBeVisible();
    await upload("e2e-valid.pdf", "Validated");
  });

  test("switches to the second client with the search box: its list is empty, then holds only its own invoice", async () => {
    await page.goto("/invoices");
    await switchTo(beta);
    await expect(page.getByText("Upload your first invoice")).toBeVisible();   // nothing of Alpha shows through
    await upload("e2e-mismatch.pdf", "Needs review");
    await page.goto("/invoices");
    await expect(page.locator("tbody tr", { hasText: "E2E-1002" })).toBeVisible();
    await expect(page.locator("tbody tr", { hasText: "E2E-1001" })).toHaveCount(0);
  });

  test("back in the first client only its invoice is visible", async () => {
    await switchTo(alpha);
    await expect(page.locator("tbody tr", { hasText: "E2E-1001" })).toBeVisible();
    await expect(page.locator("tbody tr", { hasText: "E2E-1002" })).toHaveCount(0);
    // a reload keeps the chosen company (the refresh token remembers it)
    await page.reload();
    await expect(page.getByRole("button", { name: new RegExp(`Client company: ${alpha}`) })).toBeVisible();
    await expect(page.locator("tbody tr", { hasText: "E2E-1002" })).toHaveCount(0);
  });

  test("the firm overview summarises each client separately", async () => {
    await page.goto("/firm/clients");
    const a = page.getByTestId("client-row").filter({ hasText: alpha });
    const b = page.getByTestId("client-row").filter({ hasText: beta });
    await expect(a).toContainText("current");
    await expect(a.getByRole("cell").nth(1)).toHaveText("1");   // documents
    await expect(b.getByRole("cell").nth(1)).toHaveText("1");
    await expect(b.getByRole("cell").nth(2)).toContainText("1"); // in review: the mismatching invoice is Beta's
    await expect(a.getByRole("cell").nth(2)).toHaveText("0");
  });
});
