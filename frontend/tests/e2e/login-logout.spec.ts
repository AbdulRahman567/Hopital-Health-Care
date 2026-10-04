import { test, expect } from "@playwright/test";

test.describe("Login/Logout Flow", () => {
  test("should display login page", async ({ page }) => {
    await page.goto("/login");
    await expect(page.getByText("Healthcare HMS")).toBeVisible();
    await expect(page.getByText("Sign in to your account")).toBeVisible();
  });

  test("should show validation errors for empty form", async ({ page }) => {
    await page.goto("/login");
    await page.getByRole("button", { name: "Sign In" }).click();
    await expect(page.getByText("Invalid email address")).toBeVisible();
  });

  test("should redirect to login when not authenticated", async ({ page }) => {
    await page.goto("/dashboard");
    await expect(page).toHaveURL(/\/login/);
  });
});
