/**
 * spec: openspec/specs/e2e/login-flow.md
 * section: 14. Responsive Design
 *
 * Covers login page rendering on mobile and tablet viewports.
 */

import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { APP_URL } from '../fixtures/test-data'

test.describe('Responsive Design', { tag: '@frontend' }, () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('14.1 Auth pages render on mobile viewport', async ({ page }) => {
    // iPhone 12 viewport
    await page.setViewportSize({ width: 390, height: 844 })

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // Form should be readable
    await expect(loginPage.emailInput).toBeVisible()
    await expect(loginPage.passwordInput).toBeVisible()
    await expect(loginPage.submitButton).toBeVisible()
    await expect(loginPage.submitButton).toBeEnabled()

    // Should not need horizontal scrolling
    const pageWidth = await page.evaluate(() => document.documentElement.scrollWidth)
    expect(pageWidth).toBeLessThanOrEqual(400)
  })

  test('14.2 Auth pages render on tablet viewport', async ({ page }) => {
    // iPad viewport
    await page.setViewportSize({ width: 768, height: 1024 })

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // Form should be readable
    await expect(loginPage.emailInput).toBeVisible()
    await expect(loginPage.passwordInput).toBeVisible()

    // Hero section should be visible (multi-column layout)
    await expect(loginPage.heroTitle).toBeVisible()
    await expect(loginPage.badge).toBeVisible()

    // No horizontal scroll
    const pageWidth = await page.evaluate(() => document.documentElement.scrollWidth)
    expect(pageWidth).toBeLessThanOrEqual(780)
  })

  test('14.3 Register page renders on mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.register)
    await expect(loginPage.emailInput).toBeVisible()
    const pageWidth = await page.evaluate(() => document.documentElement.scrollWidth)
    expect(pageWidth).toBeLessThanOrEqual(400)
  })
})
