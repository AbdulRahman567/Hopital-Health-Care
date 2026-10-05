import { test } from "@playwright/test";
import path from "path";
import fs from "fs";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const screenshotsDir = path.join(__dirname, "../../screenshots");

if (!fs.existsSync(screenshotsDir)) {
  fs.mkdirSync(screenshotsDir, { recursive: true });
}

test.describe("Screenshots", () => {
  test("login page", async ({ page }) => {
    await page.goto("/login");
    await page.waitForLoadState("networkidle");
    await page.screenshot({ path: path.join(screenshotsDir, "01-login-page.png"), fullPage: true });
  });

  test("login page with validation errors", async ({ page }) => {
    await page.goto("/login");
    await page.getByRole("button", { name: "Sign In" }).click();
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(screenshotsDir, "02-login-validation-errors.png"), fullPage: true });
  });

  test("dashboard redirect (unauthenticated)", async ({ page }) => {
    await page.goto("/dashboard");
    await page.waitForURL("**/login**");
    await page.screenshot({ path: path.join(screenshotsDir, "03-dashboard-redirect.png"), fullPage: true });
  });

  test("root page redirect", async ({ page }) => {
    await page.goto("/");
    await page.waitForTimeout(2000);
    await page.screenshot({ path: path.join(screenshotsDir, "04-root-redirect.png"), fullPage: true });
  });

  test("404 page", async ({ page }) => {
    await page.goto("/non-existent-page");
    await page.waitForLoadState("networkidle");
    await page.screenshot({ path: path.join(screenshotsDir, "05-404-page.png"), fullPage: true });
  });
});
