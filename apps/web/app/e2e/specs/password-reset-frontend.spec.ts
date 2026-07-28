import { test, expect } from '../fixtures/base-test'
import {
  mockAuthenticatedSession,
  mockForgotPasswordResponse,
  mockRefreshFailure,
  mockResetPasswordResponse,
} from '../fixtures/auth-helpers'
import { APP_URL, PASSWORD_RECOVERY_TEST_DATA } from '../fixtures/test-data'
import { PasswordRecoveryPage } from '../pages/password-recovery-page'

async function setLocale(page: import('@playwright/test').Page, locale: 'en' | 'es') {
  await page.addInitScript((selectedLocale) => {
    localStorage.setItem(
      'pt_settings_v1',
      JSON.stringify({ locale: selectedLocale, theme: 'dark' }),
    )
  }, locale)
}

test.describe('Password recovery frontend', { tag: '@integration' }, () => {
  test.beforeEach(async ({ page }) => {
    await mockRefreshFailure(page)
  })

  test('shows generic forgot success', async ({ page }) => {
    await mockForgotPasswordResponse(page)
    const recovery = new PasswordRecoveryPage(page)
    await page.goto(APP_URL.forgotPassword)
    await recovery.requestReset(PASSWORD_RECOVERY_TEST_DATA.email)
    await expect(recovery.status).toContainText(/If an account exists/i)
  })

  for (const failure of [
    { status: 429, code: 'AUTH_RATE_LIMIT_EXCEEDED', message: /too many attempts/i },
    { status: 503, code: 'PASSWORD_RECOVERY_DISABLED', message: /temporarily unavailable/i },
  ]) {
    test(`maps forgot failure ${failure.status} safely`, async ({ page }) => {
      await mockForgotPasswordResponse(page, failure)
      const recovery = new PasswordRecoveryPage(page)
      await page.goto(APP_URL.forgotPassword)
      await recovery.requestReset(PASSWORD_RECOVERY_TEST_DATA.email)
      await expect(recovery.alert).toContainText(failure.message)
    })
  }

  test('shows the same invalid state for missing and rejected reset tokens', async ({ page }) => {
    const recovery = new PasswordRecoveryPage(page)
    await page.goto(APP_URL.resetPassword)
    await expect(recovery.alert).toContainText(/invalid or has expired/i)

    await mockResetPasswordResponse(page, { status: 400, code: 'USED_PASSWORD_RESET_TOKEN' })
    await page.goto(`${APP_URL.resetPassword}?token=${PASSWORD_RECOVERY_TEST_DATA.token}`)
    await recovery.resetPassword(PASSWORD_RECOVERY_TEST_DATA.password)
    await expect(recovery.alert).toContainText(/invalid or has expired/i)
  })

  test('successful reset ends at success and requires explicit login', async ({ page }) => {
    await mockResetPasswordResponse(page)
    const recovery = new PasswordRecoveryPage(page)
    await page.goto(`${APP_URL.resetPassword}?token=${PASSWORD_RECOVERY_TEST_DATA.token}`)
    await recovery.resetPassword(PASSWORD_RECOVERY_TEST_DATA.password)
    await expect(recovery.status).toContainText(/sign in again/i)
    await expect(page).toHaveURL(/reset-password/)
    await recovery.page.getByRole('link', { name: /sign in/i }).click()
    await expect(page).toHaveURL(/login/)
  })

  test('authenticated forgot redirects but authenticated reset remains accessible', async ({
    page,
  }) => {
    await mockAuthenticatedSession(page)
    await page.goto(APP_URL.forgotPassword)
    await expect(page).toHaveURL(/\/$/)

    await page.goto(`${APP_URL.resetPassword}?token=${PASSWORD_RECOVERY_TEST_DATA.token}`)
    await expect(page.locator('#new-password')).toBeVisible()
  })

  test('recovery UI is keyboard accessible, responsive, localized, and does not retain secrets', async ({
    page,
  }, testInfo) => {
    await setLocale(page, testInfo.project.name === 'Mobile Chrome' ? 'es' : 'en')
    await mockResetPasswordResponse(page)
    const recovery = new PasswordRecoveryPage(page)
    await page.goto(`${APP_URL.resetPassword}?token=${PASSWORD_RECOVERY_TEST_DATA.token}`)
    await expect(recovery.newPassword).toHaveAttribute('autocomplete', 'new-password')
    await expect(recovery.confirmation).toHaveAttribute('autocomplete', 'new-password')
    await recovery.expectNoHorizontalOverflow()
    await recovery.resetPassword(PASSWORD_RECOVERY_TEST_DATA.password)
    await expect(recovery.status).toContainText(
      testInfo.project.name === 'Mobile Chrome' ? /Inicia sesión de nuevo/i : /Sign in again/i,
    )

    const storage = await page.evaluate(() =>
      JSON.stringify({ local: localStorage, session: sessionStorage }),
    )
    expect(storage).not.toContain(PASSWORD_RECOVERY_TEST_DATA.token)
    expect(storage).not.toContain(PASSWORD_RECOVERY_TEST_DATA.password)
  })
})
