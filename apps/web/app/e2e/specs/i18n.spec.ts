/**
 * spec: openspec/specs/e2e/login-flow.md
 * section: 10. Internationalization (i18n)
 *
 * NOTE: Language switching is NOT available on the auth page. The locale
 * is managed via the settings store and the language switcher lives in
 * the authenticated dashboard sidebar and Settings page.
 *
 * The auth page renders in the default locale (English). Spanish locale
 * must be verified via the dashboard or at the unit-test level.
 */

import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { I18N_TEXT, APP_URL } from '../fixtures/test-data'

test.describe('Internationalization', () => {
  test.beforeEach(async ({ page }) => {
    await page.context().clearCookies()
    await page.evaluate(() => {
      try { localStorage.clear(); sessionStorage.clear() } catch {}
    }).catch(() => {})
  })

  test('10.1 English locale renders correctly', { tag: '@frontend' }, async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    const en = I18N_TEXT.en

    // Heading and subtitle
    await expect(page.getByRole('heading', { name: en.titleLogin, level: 2 })).toBeVisible()
    await expect(page.getByText(en.subtitleLogin)).toBeVisible()

    // Submit button
    await expect(loginPage.submitButton).toHaveText(en.submitLogin)

    // Alternate link
    await expect(page.getByText(en.alternateLabelLogin)).toBeVisible()
    const registerLink = page.locator('a').filter({ hasText: en.alternateActionLogin })
    await expect(registerLink).toBeVisible()

    // Email placeholder
    await expect(loginPage.emailInput).toHaveAttribute('placeholder', en.emailPlaceholder)
  })
})
