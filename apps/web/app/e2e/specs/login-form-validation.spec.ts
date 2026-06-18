/**
 * spec: openspec/specs/e2e/login-flow.md
 * section: 2. Form Validation (Frontend)
 *
 * Verifies that the browser's HTML5 validation blocks invalid form
 * submissions before any network request is made.
 */

import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { APP_URL } from '../fixtures/test-data'

test.describe('Login Form Validation', { tag: '@frontend' }, () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('2.1 Empty fields trigger HTML5 validation', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // Intercept network requests to verify none are made
    let requestMade = false
    await page.route('**/api/auth/login', () => {
      requestMade = true
    })

    // Click submit with empty fields
    await loginPage.submit()

    // HTML5 validation prevents form submission synchronously, no network
    // request should be made
    expect(requestMade).toBe(false)
  })

  test('2.2 Invalid email format blocked by browser', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    let requestMade = false
    await page.route('**/api/auth/login', () => {
      requestMade = true
    })

    await loginPage.fillEmail('not-an-email')
    await loginPage.fillPassword('password123')
    await loginPage.submit()

    expect(requestMade).toBe(false)
  })

  test('2.3 Empty password blocked by browser', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    let requestMade = false
    await page.route('**/api/auth/login', () => {
      requestMade = true
    })

    await loginPage.fillEmail('valid@email.com')
    // Leave password empty
    await loginPage.submit()

    expect(requestMade).toBe(false)
  })
})
