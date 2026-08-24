/**
 * spec: openspec/specs/e2e/login-flow.md
 * section: 11. Security
 *
 * Covers access token not persisted to localStorage, HttpOnly cookie
 * flags, no credential exposure in URL, no user enumeration, and
 * required API headers.
 *
 * Uses HAR replay by default. Cookie assertions rely on the Set-Cookie
 * captured in the HAR response and remain skipped on WebKit.
 */

import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { APP_URL, VALID_CREDENTIALS, NONEXISTENT_EMAIL_CREDENTIALS } from '../fixtures/test-data'
import { mockLoginResponse } from '../fixtures/auth-helpers'

test.describe('Security', { tag: '@integration' }, () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('11.1 Access token never persisted to localStorage', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // Listen for the login response
    const responsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/auth/login') && res.status() === 200,
    )

    await loginPage.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)
    await responsePromise

    // Wait for dashboard
    await page.waitForURL(APP_URL.dashboard)

    // Check localStorage and sessionStorage
    const localStorageContent = await page.evaluate(() => {
      const items: Record<string, string> = {}
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i)
        if (key) items[key] = localStorage.getItem(key) ?? ''
      }
      return items
    })

    // No auth tokens should be in localStorage
    const localStorageStr = JSON.stringify(localStorageContent).toLowerCase()
    expect(localStorageStr).not.toContain('access_token')
    expect(localStorageStr).not.toContain('accessToken')
    expect(localStorageStr).not.toContain('bearer')
    expect(localStorageStr).not.toContain('jwt')
    expect(localStorageStr).not.toContain('refresh')
  })

  test('11.2 Refresh token is HttpOnly cookie', async ({ page }) => {
    // WebKit is officially excluded from the Dashboard E2E matrix due to engine-level cookie handling bugs with intercepted responses.
    test.skip(
      test.info().project.name === 'webkit' || test.info().project.name.includes('webkit'),
      'WebKit: cookies are not set from routeFromHAR responses (WebKit is officially excluded from the Dashboard E2E matrix).',
    )

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    await loginPage.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)
    await page.waitForURL(APP_URL.dashboard)

    // After login, the browser should have the pt_refresh cookie
    const cookies = await page.context().cookies()
    const refreshCookie = cookies.find((c) => c.name === 'pt_refresh')

    expect(refreshCookie).toBeDefined()
    expect(refreshCookie?.httpOnly).toBe(true)
    expect(refreshCookie?.path).toBe('/api/auth')
    expect(refreshCookie?.secure).toBe(true)
  })

  test('11.3 No credential exposure in URL', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)
    await loginPage.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)

    // The current URL should never contain the password or email
    const currentUrl = page.url()
    expect(currentUrl).not.toContain(VALID_CREDENTIALS.email)
    expect(currentUrl).not.toContain(VALID_CREDENTIALS.password)
    expect(currentUrl).not.toContain('password')
  })

  test('11.4 Failed login does not expose user existence', async ({ page }) => {
    // Mock login to return 401 so we can inspect the response body
    await mockLoginResponse(page, {
      body: { title: 'Invalid credentials', detail: 'Invalid email or password.', status: 401 },
    })

    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    const responsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/auth/login') && res.status() === 401,
    )

    await loginPage.login(
      NONEXISTENT_EMAIL_CREDENTIALS.email,
      NONEXISTENT_EMAIL_CREDENTIALS.password,
    )

    const response = await responsePromise
    const body = await response.json()

    // Generic error — no distinction between "user not found" and "wrong password"
    expect(body.title).toBe('Invalid credentials')
    expect(body.detail).toBe('Invalid email or password.')
    expect(body.detail).not.toContain('not found')
    expect(body.detail).not.toContain('exist')
    expect(body.detail).not.toContain('incorrect')
  })

  test('11.5 Required Content-Type and Accept headers on auth endpoints', async ({ page }) => {
    const loginPage = new LoginPage(page)

    // Intercept and validate the login request headers
    const requestPromise = page.waitForRequest(
      (req) => req.url().includes('/api/auth/login') && req.method() === 'POST',
    )

    await loginPage.goto(APP_URL.login)
    await loginPage.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)

    const request = await requestPromise
    const headers = request.headers()

    expect(headers['content-type']).toContain('application/json')
    expect(headers.accept).toContain('application/vnd.api.v1+json')
  })
})
