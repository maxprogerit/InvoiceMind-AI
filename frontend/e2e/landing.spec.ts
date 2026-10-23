import { expect, test } from "@playwright/test";

const suffix = Math.random().toString(36).slice(2, 8);

test.describe("public landing page", () => {
  test("a visitor who is not signed in sees the landing page, with working links to sign in and register", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1, name: "Every invoice runs the same gate." })).toBeVisible();

    await page.getByRole("navigation", { name: "Account" }).getByRole("link", { name: "Sign in" }).click();
    await page.waitForURL("**/login");
    await expect(page.locator("#email")).toBeVisible();

    await page.goto("/");
    await page.getByRole("navigation", { name: "Account" }).getByRole("link", { name: "Create account" }).click();
    await page.waitForURL("**/register");
    await expect(page.locator("#org")).toBeVisible();
  });

  test("'See a sample' jumps to the worked example, 'View on GitHub' is a real external link", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("link", { name: "See a sample" }).click();
    await expect(page).toHaveURL(/#sample$/);
    await expect(page.getByRole("heading", { name: "The total does not match the lines." })).toBeInViewport();
    const repo = page.getByRole("link", { name: "View on GitHub" });
    await expect(repo).toHaveAttribute("href", /github\.com\/.+\/InvoiceMind-AI/);
    await expect(repo).toHaveAttribute("rel", /noopener/);
  });

  test("a signed-in person still goes straight to their invoices", async ({ page }) => {
    await page.goto("/register");
    await page.fill("#org", `Landing Co ${suffix}`);
    await page.fill("#name", "Lena Landing");
    await page.fill("#email", `landing-${suffix}@e2e.example.com`);
    await page.fill("#password", "Passw0rd!long");
    await page.click("button[type=submit]");
    await page.waitForURL("**/invoices");
    await page.goto("/");
    await page.waitForURL("**/invoices");
    await expect(page.getByRole("heading", { level: 1, name: "Every invoice runs the same gate." })).toHaveCount(0);
  });

  test("the drawing moves: belt, scan beam, invoices and arm all run an animation", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();   // the landing page appears once the session check says "not signed in"
    const running = await page.evaluate(() => {
      const names = new Set<string>();
      document.querySelectorAll("[class*=conveyor_]").forEach((el) => {
        const n = getComputedStyle(el).animationName;
        if (n !== "none") n.split(",").forEach((x) => names.add(x.trim().replace(/^conveyor_|__.*$/g, "")));
      });
      return [...names];
    });
    expect(running).toEqual(expect.arrayContaining(["sweep", "tread", "travel", "travelFlagged", "swing"]));
  });

  test("reduced motion: nothing in the drawing animates, and the still frame shows all three outcomes", async ({ browser }) => {
    const context = await browser.newContext({ reducedMotion: "reduce" });
    const page = await context.newPage();
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    const probe = await page.evaluate(() => {
      const all = [...document.querySelectorAll("[class*=conveyor_], [class*=conveyor_] *")];
      return { checked: all.length, running: all.map((el) => getComputedStyle(el).animationName).filter((n) => n !== "none") };
    });
    expect(probe.checked).toBeGreaterThan(20);   // the drawing was really there to be checked
    expect(probe.running).toEqual([]);
    const picture = page.getByRole("img", { name: /conveyor belt/ });
    await expect(picture).toBeVisible();
    // texts are inside an aria-hidden drawing, so read them from the DOM
    const texts = await picture.evaluate((el) => (el as HTMLElement).innerText);
    expect(texts).toContain("scanning");
    expect(texts).toContain("✓ verified");
    expect(texts).toContain("flagged");
    expect(texts.toLowerCase()).toContain("needs review");
    await context.close();
  });

  test("on a phone the page does not scroll sideways and the drawing stays in its column", async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
    const page = await context.newPage();
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    const { scroll, client } = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, client: document.documentElement.clientWidth }));
    expect(scroll).toBeLessThanOrEqual(client);
    const box = await page.getByRole("img", { name: /conveyor belt/ }).boundingBox();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(390);
    await context.close();
  });
});
