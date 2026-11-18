import { test, expect } from '../fixtures/scheduler-base-test'
import { LoginPage } from '../pages/login-page'
import { safeGoto } from '../fixtures/navigation'
import { APP_URL } from '../fixtures/test-data'

test.describe('Scheduler — Authentication', () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('TC-01: BFF login initiation @auth @smoke', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({ status: 401, body: JSON.stringify({ status: 401 }) })
    })
    await safeGoto(page, APP_URL.login)

    await expect(loginPage.heading).toBeVisible()
    await expect(loginPage.signInButton).toBeVisible()
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
  })
})
