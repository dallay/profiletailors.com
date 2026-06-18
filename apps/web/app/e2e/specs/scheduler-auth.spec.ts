import { test, expect } from '../fixtures/scheduler-base-test'
import { LoginPage } from '../pages/login-page'
import { DashboardPage } from '../pages/dashboard-page'
import { safeGoto } from '../fixtures/navigation'
import { VALID_CREDENTIALS, APP_URL } from '../fixtures/test-data'
import { keepSessionAlive } from '../fixtures/auth-helpers'

/**
 * TC-01: Login and Session Persistence
 *
 * Verifies:
 * - Login form renders with correct headings
 * - Valid credentials grant access to dashboard
 * - Session persists across page reload
 *
 * Runs against HAR replay — no backend required.
 */
test.describe('Scheduler — Authentication', () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('TC-01: login and session persistence @auth @smoke', async ({ page }) => {
    const loginPage = new LoginPage(page)
    const dashboardPage = new DashboardPage(page)

    // Step 1: Navigate to login
    await safeGoto(page, APP_URL.login)

    // Step 2: Verify login page renders
    await expect(loginPage.heading).toBeVisible()
    await expect(loginPage.submitButton).toBeVisible()

    // Step 3-5: Fill credentials and submit
    await loginPage.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)

    // Step 6: Verify redirect to dashboard
    await dashboardPage.expectAuthenticated()

    // Override refresh so session survives page reload in HAR replay mode
    await keepSessionAlive(page)

    // Step 7: Verify session persists across reload
    await page.reload()
    await dashboardPage.expectAuthenticated()
  })
})
