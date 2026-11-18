import { test, expect } from '../fixtures/base-test'
import { APP_URL } from '../fixtures/test-data'

test.describe('BFF login initiation', { tag: '@integration' }, () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('starts Keycloak authorization and preserves the protected return path', async ({
    page,
  }) => {
    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({ status: 401, body: JSON.stringify({ status: 401 }) })
    })
    await page.goto(`${APP_URL.login}?redirect=%2Fanalytics`)
    await expect(page.getByRole('button', { name: /^sign in$|^iniciar sesión$/i })).toBeVisible()
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

    await page.getByRole('button', { name: /sign in|iniciar sesión/i }).click()

    await expect.poll(() => authorizationUrl?.pathname).toBe('/oauth2/authorization/pt-app')
    expect(authorizationUrl?.searchParams.get('returnTo')).toBe('/analytics')
  })
})
