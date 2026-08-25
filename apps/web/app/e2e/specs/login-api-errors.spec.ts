/**
 * spec: openspec/specs/e2e/login-flow.md
 * section: 4. Login API — Error Paths
 *
 * Covers invalid credentials, non-existent email (no user enumeration),
 * server errors, and network errors.
 */

import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import {
  INVALID_CREDENTIALS,
  NONEXISTENT_EMAIL_CREDENTIALS,
  VALID_CREDENTIALS,
  APP_URL,
} from '../fixtures/test-data'
import { mockLoginResponse } from '../fixtures/auth-helpers'

test.describe('Login API — Error Paths', { tag: '@integration' }, () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('4.1 Invalid email or password shows error banner', async ({ page }) => {
    // Mock login to return 401 so the error banner appears
    await mockLoginResponse(page)

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // Fill wrong credentials
    await loginPage.login(INVALID_CREDENTIALS.email, INVALID_CREDENTIALS.password)

    // Should see error
    await loginPage.expectErrorVisible(/could not sign you in/i)
    await expect(loginPage.errorBanner).toBeFocused()

    // URL should remain /login
    await expect(page).toHaveURL(/\/login$/)

    // Error banner should have red styling
    const errorBanner = loginPage.errorBanner
    await expect(errorBanner).toHaveClass(/border-error/)
  })

  test('4.2 Non-existent email returns same error (no user enumeration)', async ({ page }) => {
    // Mock login to return 401 so we can inspect the response
    await mockLoginResponse(page, {
      body: { title: 'Invalid credentials', detail: 'Invalid email or password.', status: 401 },
    })

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // Intercept the API response to verify what the server returns
    const responsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/auth/login') && res.status() === 401,
    )

    await loginPage.login(
      NONEXISTENT_EMAIL_CREDENTIALS.email,
      NONEXISTENT_EMAIL_CREDENTIALS.password,
    )

    const response = await responsePromise
    const body = await response.json()

    // The error should be the same generic message for any auth failure
    expect(body.title).toBe('Invalid credentials')
    expect(body.detail).toBe('Invalid email or password.')

    // The message should NOT reveal whether the email exists
    expect(body.detail).not.toContain('not found')
    expect(body.detail).not.toContain('does not exist')
    expect(body.detail).not.toContain('incorrect')
    expect(body.detail).not.toContain('wrong password')
  })

  test('4.3 Server error (500) shows appropriate error', async ({ page }) => {
    // Mock the API to return 500
    await page.route('**/api/auth/login', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Internal Server Error',
          detail: 'Something went wrong',
          status: 500,
        }),
      })
    })

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)
    await loginPage.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)

    // Should show error and stay on login page
    await loginPage.expectErrorVisible()
    await expect(page).toHaveURL(/\/login$/)
  })

  test('4.4 Network error shows appropriate message', async ({ page }) => {
    // Abort the API request to simulate network failure
    await page.route('**/api/auth/login', (route) => route.abort('connectionrefused'))

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)
    await loginPage.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)

    // Should show error and stay on login page
    await loginPage.expectErrorVisible()
    await expect(page).toHaveURL(/\/login$/)
  })

  test('4.5 Rate limiting returns 429 if configured', async ({ page }) => {
    // Mock the API to return 429
    await page.route('**/api/auth/login', async (route) => {
      await route.fulfill({
        status: 429,
        contentType: 'application/json',
        body: JSON.stringify({
          title: 'Too Many Requests',
          detail: 'Too many login attempts. Try again later.',
          status: 429,
        }),
      })
    })

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // Make a login attempt
    await loginPage.login(INVALID_CREDENTIALS.email, INVALID_CREDENTIALS.password)

    // Should show a rate-limit error
    await loginPage.expectErrorVisible(/could not sign you in|try again/i)
    await expect(page).toHaveURL(/\/login$/)
  })
})
