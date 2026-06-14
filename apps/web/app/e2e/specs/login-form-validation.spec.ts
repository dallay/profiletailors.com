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
  test.beforeEach(async ({ page }) => {
    await page.context().clearCookies()
    await page.evaluate(() => {
      try { localStorage.clear(); sessionStorage.clear() } catch {}
    }).catch(() => {})
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

    // Wait a beat to let any potential request fire
    await page.waitForTimeout(500)

    // HTML5 validation should prevent form submission
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

    await page.waitForTimeout(500)
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

    await page.waitForTimeout(500)
    expect(requestMade).toBe(false)
  })
})
