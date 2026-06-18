/**
 * spec: openspec/specs/e2e/login-flow.md
 * section: 8. Logout
 *
 * Covers logout clearing session, redirect to login, protected route
 * redirect after logout, and idempotent logout.
 */

import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { DashboardPage } from '../pages/dashboard-page'
import { APP_URL } from '../fixtures/test-data'
import {
  authenticateAs,
  keepSessionAlive,
  logout,
  mockRefreshFailure,
} from '../fixtures/auth-helpers'
import { safeGoto } from '../fixtures/navigation'

test.describe('Logout', { tag: '@integration' }, () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('8.1 Logout clears session and redirects to login', async ({ page }) => {
    // Authenticate
    await authenticateAs(page)
    // Keep the session alive across protected-route reloads while still using HAR
    await keepSessionAlive(page)

    // Navigate to dashboard
    const dashboard = new DashboardPage(page)
    await safeGoto(page, APP_URL.dashboard)
    await dashboard.expectAuthenticated()

    // From now on, refresh must fail so the route guard doesn't re-authenticate
    // the user after logout (the 200 mock from mockRefreshResponse above would
    // otherwise refresh the session on next page load)
    await mockRefreshFailure(page)

    // Find and click logout in the navigation
    // The logout may be in a dropdown menu or shown directly
    const logoutButton = page.getByRole('button', { name: /logout|log out|cerrar sesión/i })

    // It might be inside a user menu dropdown — click the user button first if needed
    const userButton = page.getByRole('button', { name: /profile tailors/i }).first()
    if (await userButton.isVisible()) {
      await userButton.click()
      await page.waitForTimeout(300)
    }

    // Click logout
    if (await logoutButton.isVisible()) {
      await logoutButton.click()
      // The app redirects to /login automatically
    } else {
      // Fallback: trigger logout through the browser context so HAR/page routes still apply
      await logout(page)
      await safeGoto(page, APP_URL.login)
    }

    // Should be on login page after logout
    const loginPage = new LoginPage(page)
    await loginPage.expectOnLoginPage()
  })

  test('8.2 Protected routes redirect to login after logout', async ({ page }) => {
    // Authenticate — add refresh override so page.goto succeeds
    await authenticateAs(page)
    await keepSessionAlive(page)
    await safeGoto(page, APP_URL.dashboard)

    // Logout via browser fetch helper so HAR interception remains in play
    await logout(page)

    // Override refresh to fail — the 200 mock from mockRefreshResponse would
    // otherwise re-authenticate the user on the next page load
    await mockRefreshFailure(page)

    // Try to access a protected route — route guard should redirect to login
    await safeGoto(page, APP_URL.scheduler)

    // Should redirect to login with a redirect parameter
    const loginPage = new LoginPage(page)
    await loginPage.expectOnLoginPage()
    expect(page.url()).toContain('/login?redirect=')
  })

  test('8.3 Logout is idempotent from login page', async ({ page }) => {
    // Navigate to login (no session)
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // Attempt logout via API — should not throw
    await logout(page)

    // Should still be on login page
    await loginPage.expectOnLoginPage()

    // Still unauthenticated after an idempotent logout
    expect(page.url()).toContain(APP_URL.login)
  })
})
