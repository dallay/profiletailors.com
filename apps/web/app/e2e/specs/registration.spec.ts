/**
 * spec: openspec/specs/e2e/login-flow.md
 * section: 5. Registration
 *
 * Covers registration success, duplicate email, short password,
 * invalid email, and email normalization.
 */

import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { DashboardPage } from '../pages/dashboard-page'
import {
  NEW_USER,
  SHORT_PASSWORD,
  WHITESPACE_EMAIL,
  VALID_CREDENTIALS,
  APP_URL,
} from '../fixtures/test-data'
import { logout, mockRegisterResponse, mockRefreshFailure } from '../fixtures/auth-helpers'

test.describe('Registration', { tag: '@integration' }, () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('5.1 Successful registration creates account and logs in', async ({ page }) => {
    // Mock register to succeed — HAR has no register entries
    await mockRegisterResponse(page)

    const loginPage = new LoginPage(page)
    const dashboard = new DashboardPage(page)

    await loginPage.goto(APP_URL.register)

    // Fill registration form
    await loginPage.fillEmail(NEW_USER.email)
    await loginPage.fillPassword(NEW_USER.password)

    // Intercept API to validate token response
    const responsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/auth/register') && res.status() === 201,
    )

    await loginPage.submit()

    // Verify API returns tokens
    const response = await responsePromise
    const body = await response.json()
    expect(body).toHaveProperty('principalId')
    expect(body).toHaveProperty('email')
    expect(body).toHaveProperty('emailStatus', 'PENDING')

    // Verify redirect to dashboard
    await dashboard.expectAuthenticated()
    await expect(page).toHaveURL(APP_URL.dashboard)
  })

  test('5.2 Duplicate email returns 409 error', async ({ page }) => {
    // Mock register to return 409 conflict
    await mockRegisterResponse(page, {
      status: 409,
      body: {
        title: 'Conflict',
        detail: 'An account with this email already exists.',
        status: 409,
      },
    })

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.register)

    // Register with an email that already exists
    await loginPage.fillEmail(VALID_CREDENTIALS.email)
    await loginPage.fillPassword(VALID_CREDENTIALS.password)
    await loginPage.submit()

    // Should see conflict error
    await loginPage.expectErrorVisible(/already exists/i)
    await loginPage.expectOnRegisterPage()
  })

  test('5.3 Invalid email blocked by browser validation', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.register)

    let requestMade = false
    await page.route('**/api/auth/register', () => {
      requestMade = true
    })

    await loginPage.fillEmail('invalid')
    await loginPage.fillPassword('ValidPass123!')
    await loginPage.submit()

    expect(requestMade).toBe(false)
  })

  test('5.4 Short password returns validation error', async ({ page }) => {
    // Mock register to return 422 for short password
    await mockRegisterResponse(page, {
      status: 422,
      body: {
        title: 'Validation Error',
        detail: 'Password must be at least 8 characters.',
        status: 422,
        errors: { password: ['too short'] },
      },
    })

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.register)

    await loginPage.fillEmail('newuser@example.com')
    await loginPage.fillPassword(SHORT_PASSWORD)
    await loginPage.submit()

    // Backend validation should fire
    await loginPage.expectErrorVisible(/password/i)
    await loginPage.expectOnRegisterPage()
  })

  test('5.5 Email whitespace and case normalization', async ({ page }) => {
    // Mock register to succeed
    await mockRegisterResponse(page)

    const loginPage = new LoginPage(page)
    const dashboard = new DashboardPage(page)

    // Register with whitespace and uppercase email
    await loginPage.goto(APP_URL.register)
    await loginPage.fillEmail(WHITESPACE_EMAIL)
    await loginPage.fillPassword('SecurePass123!')
    await loginPage.submit()

    // Should succeed and redirect
    await dashboard.expectAuthenticated()

    // Logout
    await logout(page)
    // Override refresh to fail — otherwise the HAR would re-authenticate
    // the user via refresh on the next page load
    await mockRefreshFailure(page)

    // Login with the same email (lowercased, trimmed)
    await loginPage.goto(APP_URL.login)
    await loginPage.login('test@example.com', 'SecurePass123!')
    await dashboard.expectAuthenticated()

    // Also try with original casing
    await logout(page)
    await mockRefreshFailure(page)
    await loginPage.goto(APP_URL.login)
    await loginPage.login('  Test@Example.com  ', 'SecurePass123!')
    await dashboard.expectAuthenticated()
  })
})
