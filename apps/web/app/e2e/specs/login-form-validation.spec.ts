import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { APP_URL } from '../fixtures/test-data'
import { mockRefreshFailure } from '../fixtures/auth-helpers'

test.describe('Login validation and submission states', { tag: '@frontend' }, () => {
  test.beforeEach(async ({ page }) => {
    await mockRefreshFailure(page)
    await page.route('**/api/capabilities/public', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ registrationEnabled: true, passwordRecoveryEnabled: true }),
      }),
    )
  })

  test('invalid submission focuses first field, associates errors, and sends no request', async ({
    page,
  }) => {
    let requests = 0
    await page.route('**/api/auth/login', () => {
      requests += 1
    })
    const login = new LoginPage(page)
    await login.goto(APP_URL.login)
    await login.submit()

    await expect(login.emailInput).toBeFocused()
    await expect(login.emailInput).toHaveAttribute('aria-invalid', 'true')
    await expect(login.emailInput).toHaveAttribute('aria-describedby', 'login-email-error')
    await expect(page.locator('#login-email-error')).toBeVisible()
    await expect(page.locator('#login-email-error')).not.toHaveAttribute('role', 'alert')
    expect(requests).toBe(0)
  })

  test('pending login is busy, readonly, disabled, and deduplicated', async ({ page }) => {
    let requests = 0
    let release!: () => void
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    await page.route('**/api/auth/login', async (route) => {
      requests += 1
      await gate
      await route.fulfill({ status: 401, contentType: 'application/problem+json', body: '{}' })
    })
    const login = new LoginPage(page)
    await login.goto(APP_URL.login)
    await login.fillEmail('user@example.com')
    await login.fillPassword('WrongPassword!')
    await login.submitButton.dblclick({ force: true })

    await expect(page.locator('form')).toHaveAttribute('aria-busy', 'true')
    await expect(login.emailInput).toHaveAttribute('readonly', '')
    await expect(login.passwordInput).toHaveAttribute('readonly', '')
    await expect(login.submitButton).toBeDisabled()
    expect(requests).toBe(1)
    release()
  })
})
