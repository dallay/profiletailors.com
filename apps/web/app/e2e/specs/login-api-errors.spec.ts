import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { APP_URL } from '../fixtures/test-data'

test.describe('BFF login entry', { tag: '@integration' }, () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('does not expose credential fields or submit credentials to the API', async ({ page }) => {
    let legacyLoginRequest = false
    await page.route('**/api/auth/login', async (route) => {
      legacyLoginRequest = true
      await route.abort()
    })
    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({ status: 401, body: JSON.stringify({ status: 401 }) })
    })
    await page.goto(APP_URL.login)

    const loginPage = new LoginPage(page)
    await expect(page.getByRole('button', { name: /^sign in$|^iniciar sesión$/i })).toBeVisible()
    await expect(page.locator('input[type="email"]')).toHaveCount(0)
    await expect(page.locator('input[type="password"]')).toHaveCount(0)

    let authorizationUrl: URL | undefined
    await page.route('**/oauth2/authorization/pt-app**', async (route) => {
      authorizationUrl = new URL(route.request().url())
      await route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: '<title>Authorization</title>',
      })
    })

    await loginPage.startBffLogin()
    await expect.poll(() => authorizationUrl?.pathname).toBe('/oauth2/authorization/pt-app')
    expect(legacyLoginRequest).toBe(false)
  })
})
