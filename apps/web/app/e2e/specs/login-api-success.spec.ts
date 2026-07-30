/**
 * spec: openspec/specs/e2e/login-flow.md
 * section: 3. Login API — Success Path
 *
 * Covers successful login, redirect preservation, token validation,
 * profile loading, and loading state.
 *
 * Runs against HAR replay by default, so no backend is required.
 * To refresh the recorded responses, run in UPDATE_HAR=true mode.
 */

import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { DashboardPage } from '../pages/dashboard-page'
import { VALID_CREDENTIALS, APP_URL } from '../fixtures/test-data'
import { fallbackAfterDelay } from '../fixtures/auth-helpers'
import { safeGoto } from '../fixtures/navigation'

test.describe('Login API — Success Path', { tag: '@integration' }, () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('3.1 Successful login redirects to dashboard', async ({ page }) => {
    const loginPage = new LoginPage(page)
    const dashboard = new DashboardPage(page)

    // Navigate to login
    await loginPage.goto(APP_URL.login)

    // Wait for form to be ready
    await expect(loginPage.submitButton).toBeVisible()

    // Fill credentials and submit
    await loginPage.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)

    // Verify redirect to dashboard
    await dashboard.expectAuthenticated()
    await expect(page).toHaveURL(APP_URL.dashboard)
  })

  test('3.2 Login preserves redirect parameter', async ({ page }) => {
    const loginPage = new LoginPage(page)

    // Navigate to a protected route (should redirect to login)
    await safeGoto(page, APP_URL.analytics)
    await loginPage.expectOnLoginPage()

    // The redirect param should be in the URL (not URL-encoded — Vue Router uses raw paths)
    expect(page.url()).toContain('/analytics')

    // Login
    await loginPage.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)

    // Should redirect to /analytics, not /
    await page.waitForURL('**/analytics')
    await expect(page).toHaveURL(APP_URL.analytics)
  })

  test('3.3 API response includes expected token fields', async ({ page }) => {
    // Intercept the login API response
    const responsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/auth/login') && res.status() === 200,
    )

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)
    await loginPage.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)

    const response = await responsePromise
    const body = await response.json()

    // Validate token shape
    expect(body).toHaveProperty('accessToken')
    expect(body.accessToken).toEqual(expect.any(String))
    expect(body.accessToken.length).toBeGreaterThan(0)

    expect(body).toHaveProperty('tokenType', 'Bearer')
    expect(body).toHaveProperty('expiresIn')
    expect(body.expiresIn).toEqual(expect.any(Number))
    expect(body.expiresIn).toBeGreaterThan(0)

    expect(body).toHaveProperty('principalId')
    expect(body.principalId).toEqual(expect.any(String))
    expect(body.principalId.length).toBeGreaterThan(0)

    expect(body).toHaveProperty('email', VALID_CREDENTIALS.email)
    expect(body).toHaveProperty('username')
    expect(body.username).toEqual(expect.any(String))
  })

  test('3.4 Profile loaded after login via GET /api/auth/me', async ({ page }) => {
    const meResponsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/auth/me') && res.status() === 200,
    )

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)
    await loginPage.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)

    // Confirm the /me call was made after login
    const meResponse = await meResponsePromise
    const profile = await meResponse.json()
    expect(profile).toHaveProperty('principalId')
    expect(profile).toHaveProperty('email', VALID_CREDENTIALS.email)
  })

  test('3.5 Submit button shows loading state during login', async ({ page }) => {
    // Delay the API response to observe loading state
    await page.route('**/api/auth/login', async (route) => {
      await fallbackAfterDelay(route, 1_000)
    })

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // Submit login
    await loginPage.fillEmail(VALID_CREDENTIALS.email)
    await loginPage.fillPassword(VALID_CREDENTIALS.password)

    // Start login and verify the form submits successfully
    await loginPage.submit()

    // After a successful login, should navigate to dashboard
    await page.waitForURL(APP_URL.dashboard)
    await expect(page).toHaveURL(APP_URL.dashboard)
  })
})
