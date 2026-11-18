import { test, expect } from '../fixtures/base-test'
import { APP_URL } from '../fixtures/test-data'

test.describe('BFF login entry states', () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('login entry has no credential form or error banner', { tag: '@frontend' }, async ({
    page,
  }) => {
    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({ status: 401, body: JSON.stringify({ status: 401 }) })
    })
    await page.goto(APP_URL.login)

    await expect(page.getByRole('button', { name: /^sign in$|^iniciar sesión$/i })).toBeVisible()
    await expect(page.locator('input[type="email"]')).toHaveCount(0)
    await expect(page.locator('input[type="password"]')).toHaveCount(0)
    await expect(page.getByRole('alert')).toHaveCount(0)
  })

  test('login starts BFF authorization', { tag: '@integration' }, async ({ page }) => {
    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({ status: 401, body: JSON.stringify({ status: 401 }) })
    })
    await page.goto(APP_URL.login)

    let authorizationUrl: URL | undefined
    await page.route('**/oauth2/authorization/pt-app**', async (route) => {
      authorizationUrl = new URL(route.request().url())
      await route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: '<title>Authorization</title>',
      })
    })

    await page.getByRole('button', { name: /^sign in$|^iniciar sesión$/i }).click()

    await expect.poll(() => authorizationUrl?.pathname).toBe('/oauth2/authorization/pt-app')
    await expect(page.getByRole('alert')).toHaveCount(0)
  })
})
