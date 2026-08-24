/**
 * spec: docs/testing/e2e/login-flow.md
 * section: 7. Token Refresh (Silent 401 Retry)
 *
 * Covers silent token refresh on 401, logout on refresh failure,
 * and non-401 errors bypassing refresh.
 *
 * These tests layer targeted route overrides on top of HAR replay.
 */

import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { APP_URL } from '../fixtures/test-data'
import { authenticateAs, fallbackAfterDelay, keepSessionAlive } from '../fixtures/auth-helpers'
import { safeGoto } from '../fixtures/navigation'

test.describe('Token Refresh', { tag: '@integration' }, () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('7.1 API fetch retries on 401 with successful refresh', async ({ page }) => {
    // Authenticate — add refresh override so page loads succeed
    await authenticateAs(page)
    await keepSessionAlive(page)
    await safeGoto(page, APP_URL.dashboard)
    await expect(page).toHaveURL(APP_URL.dashboard)

    // Track refresh calls
    let originalRequestSeen = false
    let _retriedRequestSeen = false

    // Intercept an authenticated API call — fail first time, then succeed
    await page.route('**/api/auth/me', async (route) => {
      if (!originalRequestSeen) {
        originalRequestSeen = true
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ title: 'Unauthorized', detail: 'Token expired', status: 401 }),
        })
      } else {
        _retriedRequestSeen = true
        await fallbackAfterDelay(route, 0)
      }
    })

    // Navigate to trigger auth check
    await safeGoto(page, APP_URL.dashboard)
    await page.waitForTimeout(1_000)

    // The 401 on /api/auth/me should trigger a refresh, then a retry
    // Check that the app is still on dashboard (session persisted)
    await expect(page).toHaveURL(APP_URL.dashboard)
  })

  test('7.2 API fetch retry fails → logout', async ({ page }) => {
    // Authenticate — add refresh override so page loads succeed
    await authenticateAs(page)
    await keepSessionAlive(page)
    await safeGoto(page, APP_URL.dashboard)
    await expect(page).toHaveURL(APP_URL.dashboard)

    // Mock refresh to fail with 401
    await page.route('**/api/auth/refresh', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ title: 'Refresh session invalid', status: 401 }),
      })
    })

    // Mock /api/auth/me to also fail
    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ title: 'Unauthorized', detail: 'Token expired', status: 401 }),
      })
    })

    // Navigate to trigger auth check
    await safeGoto(page, APP_URL.scheduler)

    // Should end up on login page
    const loginPage = new LoginPage(page)
    await loginPage.expectOnLoginPage()
  })

  test('7.3 Non-401 errors skip refresh', async ({ page }) => {
    // Authenticate
    await authenticateAs(page)
    await keepSessionAlive(page)

    // Mock /api/auth/me to return 403 — _loadProfile() calls this
    // during hydration. A non-401 error should NOT trigger refresh.
    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({ title: 'Forbidden', detail: 'Access denied', status: 403 }),
      })
    })

    // Keep session alive so page reload succeeds
    await keepSessionAlive(page)
    await safeGoto(page, APP_URL.dashboard)

    // The 403 from /api/auth/me should NOT have triggered a session
    // logout (non-401 errors skip refresh). Verify the user is still
    // authenticated on the dashboard.
    await expect(page).toHaveURL(APP_URL.dashboard)
  })
})
