import { test, expect } from '../fixtures/base-test'

test.describe('Dashboard BFF authentication', { tag: '@integration' }, () => {
  test('starts pt-app login with a safe return path and exposes no local credential or token storage', async ({
    page,
    resetSession,
  }) => {
    await resetSession()
    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ status: 401 }),
      })
    })
    await page.goto('/login?redirect=%2Fsettings')

    await expect(page.getByRole('button', { name: /sign in|iniciar sesión/i })).toBeVisible()
    await expect(page.locator('input[type="password"]')).toHaveCount(0)

    let authorizationUrl: URL | undefined
    await page.route('**/oauth2/authorization/pt-app**', async (route) => {
      authorizationUrl = new URL(route.request().url())
      await route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: '<!doctype html><title>Mocked BFF</title>',
      })
    })

    const browserState = await page.evaluate(() =>
      JSON.stringify({
        localStorage: Object.entries(localStorage),
        sessionStorage: Object.entries(sessionStorage),
      }),
    )
    expect(browserState).not.toMatch(/(?:access|refresh)[_-]?token|bearer\s/i)

    await page.getByRole('button', { name: /sign in|iniciar sesión/i }).click()
    await expect.poll(() => authorizationUrl?.pathname).toBe('/oauth2/authorization/pt-app')
    expect(authorizationUrl?.searchParams.get('returnTo')).toBe('/settings')
    expect(authorizationUrl?.searchParams.get('client_id')).toBeNull()
  })

  test('falls back to the dashboard for an external return destination', async ({
    page,
    resetSession,
  }) => {
    await resetSession()
    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ status: 401 }),
      })
    })
    await page.goto('/login?redirect=%2F%2Fevil.example%2Faccount')

    let authorizationUrl: URL | undefined
    await page.route('**/oauth2/authorization/pt-app**', async (route) => {
      authorizationUrl = new URL(route.request().url())
      await route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: '<!doctype html><title>Mocked BFF</title>',
      })
    })

    await page.getByRole('button', { name: /sign in|iniciar sesión/i }).click()
    await expect.poll(() => authorizationUrl?.pathname).toBe('/oauth2/authorization/pt-app')
    expect(authorizationUrl?.searchParams.get('returnTo')).toBe('/')
  })

  test('sends the CSRF token on logout and clears the relying-party session', async ({
    page,
    resetSession,
  }) => {
    await resetSession()
    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          principalId: 'e2e-principal',
          email: 'e2e@example.test',
          username: 'e2e',
          displayIdentity: 'E2E User',
          emailStatus: 'VERIFIED',
        }),
      })
    })
    await page.route('**/api/auth/csrf', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ token: 'csrf-e2e-token' }),
      })
    })

    let logoutRequest: { method: string; csrfToken: string | undefined } | undefined
    await page.route('**/api/auth/logout', async (route) => {
      logoutRequest = {
        method: route.request().method(),
        csrfToken: route.request().headers()['x-csrf-token'],
      }
      await route.fulfill({ status: 204 })
    })

    await page.goto('/')
    await expect(page.getByRole('heading', { name: /welcome back|bienvenido/i })).toBeVisible()
    const openNavigation = page.getByRole('button', { name: /open navigation/i })
    if (await openNavigation.isVisible()) await openNavigation.click()
    const accountButton = page.getByRole('button', { name: /e2e user/i }).first()
    await accountButton.click()
    await page.getByRole('menuitem', { name: /log out|logout|cerrar sesión/i }).click()

    await expect.poll(() => logoutRequest?.method).toBe('POST')
    expect(logoutRequest?.csrfToken).toBe('csrf-e2e-token')
    await expect(page).toHaveURL(/\/login/)
  })
})
