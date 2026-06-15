/**
 * spec: openspec/specs/e2e/login-flow.md
 * section: 6. Authentication State / Session Hydration
 *
 * Covers session persistence across page refreshes, expired session
 * redirect, and fresh browser without session.
 */

import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { DashboardPage } from '../pages/dashboard-page'
import { APP_URL } from '../fixtures/test-data'
import { authenticateAs, mockRefreshResponse } from '../fixtures/auth-helpers'
import { safeGoto } from '../fixtures/navigation'

test.describe('Session Hydration', { tag: '@integration' }, () => {
  test('6.1 Page refresh maintains session via refresh cookie', async ({ page }) => {
    // Authenticate via form, then mock refresh so the session survives re-navigation
    await authenticateAs(page)
    await mockRefreshResponse(page)

    // Navigate to dashboard (will hydrateSession via mocked refresh → 200)
    await safeGoto(page, APP_URL.dashboard)
    const dashboard = new DashboardPage(page)
    await dashboard.expectAuthenticated()

    // Refresh the page (mocked refresh persists)
    await page.reload()
    await dashboard.expectAuthenticated()
    await expect(page).toHaveURL(APP_URL.dashboard)
  })

  test('6.2 Expired session shows login page after refresh', async ({ page }) => {
    // Authenticate via form, then mock refresh so dashboard loads
    await authenticateAs(page)
    await mockRefreshResponse(page)
    await safeGoto(page, APP_URL.dashboard)
    await expect(page).toHaveURL(APP_URL.dashboard)

    // Now mock the refresh endpoint to return 401 (expired session)
    await page.route('**/api/auth/refresh', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Refresh session invalid',
          detail: 'Unauthorized',
          status: 401,
        }),
      })
    })

    // Also clear cookies to prevent real refresh
    await page.context().clearCookies()

    // Reload — should redirect to login
    await page.reload()
    const loginPage = new LoginPage(page)
    await loginPage.expectOnLoginPage()
    await expect(page).toHaveURL(/\/login/)
  })

  test('6.3 Fresh browser without session redirects to login', async ({ page }) => {
    const loginPage = new LoginPage(page)

    // Navigate to root without any session
    await safeGoto(page, APP_URL.dashboard)

    // Should redirect to login
    await loginPage.expectOnLoginPage()
  })

  test('6.4 Page renders during auth hydration', async ({ page }) => {
    // Delay the refresh API to simulate slow hydration
    await page.route('**/api/auth/refresh', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 2_000))
      await route.fulfill({ status: 401 })
    })

    // Navigate to login — page should render immediately
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // Form should be visible while auth hydration is in flight
    await expect(loginPage.emailInput).toBeVisible({ timeout: 3_000 })
    await expect(loginPage.submitButton).toBeVisible()
  })
})
