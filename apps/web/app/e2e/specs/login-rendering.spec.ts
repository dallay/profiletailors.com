/**
 * spec: openspec/specs/e2e/login-flow.md
 * section: 1. Login Page Rendering
 *
 * Verifies that the login and registration pages render all required
 * elements with correct attributes, labels, and links.
 */

import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { APP_URL } from '../fixtures/test-data'

test.describe('Login Page Rendering', { tag: '@frontend' }, () => {
  test.beforeEach(async ({ page }) => {
    // Clear any leftover session before each render test
    await page.context().clearCookies()
    await page.evaluate(() => {
      try { localStorage.clear(); sessionStorage.clear() } catch {}
    }).catch(() => {})
  })

  test('1.1 Full login page renders correctly', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // Page title
    await expect(page).toHaveTitle(/Profile Tailors/)

    // Hero section
    await expect(loginPage.heroTitle).toBeVisible()
    await expect(loginPage.badge).toBeVisible()

    // Feature cards
    await expect(page.getByText('SECURITY')).toBeVisible()
    await expect(page.getByText('FOCUS')).toBeVisible()
    await expect(page.getByText('WORKFLOW')).toBeVisible()

    // Form fields
    await expect(loginPage.emailInput).toBeVisible()
    await expect(loginPage.passwordInput).toBeVisible()
    await expect(loginPage.submitButton).toBeVisible()

    // Alternate action
    await expect(page.getByText('Need an account?')).toBeVisible()
    await expect(loginPage.alternateLink).toBeVisible()
    await expect(loginPage.alternateLink).toHaveAttribute('href', APP_URL.register)
  })

  test('1.2 Email input has correct HTML attributes', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    const emailInput = loginPage.emailInput
    await expect(emailInput).toHaveAttribute('type', 'email')
    await expect(emailInput).toHaveAttribute('autocomplete', 'email')
    await expect(emailInput).toHaveAttribute('required', '')
    await expect(emailInput).toHaveAttribute('placeholder', /you@example\.com/)
  })

  test('1.3 Password input has correct HTML attributes', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    const passwordInput = loginPage.passwordInput
    await expect(passwordInput).toHaveAttribute('type', 'password')
    await expect(passwordInput).toHaveAttribute('autocomplete', 'current-password')
    await expect(passwordInput).toHaveAttribute('required', '')
  })

  test('1.4 Registration page renders correctly (shared component)', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.register)

    // Register-specific heading and button
    await expect(page.getByRole('heading', { name: 'Create account', level: 2 })).toBeVisible()
    await expect(loginPage.submitButton).toHaveText('Create account')

    // Alternate action points to login
    await expect(page.getByText('Already have an account?')).toBeVisible()
    const signInLink = page.locator('a').filter({ hasText: 'SIGN IN' })
    await expect(signInLink).toHaveAttribute('href', APP_URL.login)

    // Only email + password — no username field
    await expect(loginPage.emailInput).toBeVisible()
    await expect(loginPage.passwordInput).toBeVisible()
    await expect(page.getByLabel(/username/i)).toHaveCount(0)
  })

})
