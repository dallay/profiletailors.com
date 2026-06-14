/**
 * spec: openspec/specs/e2e/login-flow.md
 * section: 9. Route Guards
 *
 * Covers redirecting unauthenticated users to login, authenticated
 * users away from guest routes, and redirect parameter propagation.
 */

import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { DashboardPage } from '../pages/dashboard-page'
import { APP_URL, GUEST_ROUTES, PROTECTED_ROUTES, VALID_CREDENTIALS } from '../fixtures/test-data'
import { authenticateAs, mockRefreshFailure, mockRefreshResponse } from '../fixtures/auth-helpers'
import { safeGoto } from '../fixtures/navigation'

test.describe('Route Guards', { tag: '@integration' }, () => {
  test('9.1 All protected routes redirect unauthenticated users to login', async ({ page }) => {
    const loginPage = new LoginPage(page)

    for (const route of PROTECTED_ROUTES) {
      await page.context().clearCookies()
      await page.evaluate(() => {
        try { localStorage.clear(); sessionStorage.clear() } catch {}
      }).catch(() => {})

      // HAR always returns 200 for refresh — override to return 401
      // so hydration fails and the route guard redirects to login
      await mockRefreshFailure(page)

      // Navigate to protected route
      await safeGoto(page, route.path)

      // Should redirect to login with redirect param (raw path, not URL-encoded)
      await loginPage.expectOnLoginPage()
      expect(page.url()).toContain(route.path)
    }
  })

  test('9.2 Guest routes redirect authenticated users to dashboard', async ({ page }) => {
    // Authenticate
    await authenticateAs(page)
    await mockRefreshResponse(page)

    for (const route of GUEST_ROUTES) {
      // Navigate to guest route while authenticated
      await safeGoto(page, route.path)

      // Should redirect to dashboard
      const dashboard = new DashboardPage(page)
      await dashboard.expectAuthenticated()
      await expect(page).toHaveURL(APP_URL.dashboard)
    }
  })

  test('9.3 Redirect parameter propagates correctly through login flow', async ({ page }) => {
    const loginPage = new LoginPage(page)
    const dashboard = new DashboardPage(page)

    // Navigate to settings (protected) — should be redirected
    await safeGoto(page, APP_URL.settings)
    await loginPage.expectOnLoginPage()
    expect(page.url()).toContain(APP_URL.settings)

    // Login
    await loginPage.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)

    // Should end up on settings, not /
    await page.waitForURL('**/settings')
    await expect(page).toHaveURL(APP_URL.settings)
  })
})
