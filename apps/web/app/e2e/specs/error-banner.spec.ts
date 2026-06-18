/**
 * spec: openspec/specs/e2e/login-flow.md
 * section: 12. Error Banner Visual States
 *
 * Covers error banner visibility states: hidden by default, appears
 * on failure, clears on new submission, and clears on navigation.
 */

import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { APP_URL, INVALID_CREDENTIALS, VALID_CREDENTIALS } from '../fixtures/test-data'
import { mockLoginResponse } from '../fixtures/auth-helpers'

test.describe('Error Banner Visual States', () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('12.1 Error banner hidden by default', { tag: '@frontend' }, async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // No error banner on initial load
    await expect(loginPage.errorBanner).toHaveCount(0)
  })

  test('12.2 Error banner appears on login failure', { tag: '@integration' }, async ({ page }) => {
    // Mock login to return 401 so the error banner appears
    await mockLoginResponse(page)

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // Trigger a failed login
    await loginPage.login(INVALID_CREDENTIALS.email, INVALID_CREDENTIALS.password)

    // Error banner should be visible with red styling
    await loginPage.expectErrorVisible('Invalid email or password.')

    // Check visual styling classes
    const errorBanner = loginPage.errorBanner
    const classAttr = await errorBanner.getAttribute('class') ?? ''
    expect(classAttr).toContain('error')
  })

  test('12.3 Error banner clears on new form submission', { tag: '@integration' }, async ({ page }) => {
    // Mock login: first call fails, second call succeeds (clears the error)
    let loginAttempt = 0
    await page.route('**/api/auth/login', async (route) => {
      loginAttempt++
      if (loginAttempt === 1) {
        await route.fulfill({
          status: 401,
          contentType: 'application/problem+json',
          body: JSON.stringify({ title: 'Invalid credentials', detail: 'Invalid email or password.', status: 401 }),
        })
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/vnd.api.v1+json',
          body: JSON.stringify({ accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 3600, principalId: 'test', email: 'new@email.com', username: 'newuser' }),
        })
      }
    })

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // Trigger an error first
    await loginPage.login(INVALID_CREDENTIALS.email, INVALID_CREDENTIALS.password)
    await loginPage.expectErrorVisible()

    // Submit again with valid credentials — error should clear on success
    await loginPage.fillEmail(VALID_CREDENTIALS.email)
    await loginPage.fillPassword(VALID_CREDENTIALS.password)
    await loginPage.submit()

    // Successful login navigates to dashboard — error banner is gone
    await expect(loginPage.errorBanner).toHaveCount(0, { timeout: 5_000 })
  })

  test('12.4 Error banner cleared on navigation away', { tag: '@integration' }, async ({ page }) => {
    await mockLoginResponse(page)

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // Trigger error on login
    await loginPage.login(INVALID_CREDENTIALS.email, INVALID_CREDENTIALS.password)
    await loginPage.expectErrorVisible()

    // Navigate to register via link
    await loginPage.alternateLink.click()

    // Wait for route navigation and confirm register page loaded
    await expect(loginPage.page).toHaveURL(/\/register/, { timeout: 10_000 })
    await expect(loginPage.heading).toBeVisible({ timeout: 5_000 })

    // Error banner from login should be gone
    await expect(loginPage.errorBanner).toHaveCount(0, { timeout: 5_000 })
  })
})
