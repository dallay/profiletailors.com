/**
 * Auth helper functions for E2E tests.
 *
 * IMPORTANT: All API calls use page-level fetch() to ensure they go through
 * the Playwright route interception layer (page.route() and context.routeFromHAR()).
 * Playwright's APIRequestContext (page.request.*) bypasses these interceptors.
 *
 * Headers and credentials are passed EXPLICITLY into page.evaluate()
 * because the callback runs in the browser context, not Node.js.
 */

import type { Page } from '@playwright/test'
import { expect } from '@playwright/test'
import { VALID_CREDENTIALS, APP_URL } from './test-data'

const API_HEADERS: Record<string, string> = {
  'Content-Type': 'application/json',
  Accept: 'application/vnd.api.v1+json',
}

/**
 * Authenticate by logging in via the Vue form and waiting for dashboard.
 * This ensures the Pinia auth store is properly updated.
 *
 * NOTE: After page reload, the HAR's refresh entry returns 401,
 * so session is NOT persisted across reloads. Tests that need
 * reload support must add their own page.route override for
 * the refresh endpoint (POST /api/auth/refresh).
 */
export async function authenticateAs(
  page: Page,
  credentials: { email: string; password: string } = VALID_CREDENTIALS,
): Promise<void> {
  await page.goto(APP_URL.login, { waitUntil: 'domcontentloaded' })
  await page.getByLabel(/email/i).fill(credentials.email)
  await page.getByLabel(/password/i).fill(credentials.password)
  await page.getByRole('button', { name: /sign in|iniciar sesión/i }).click()

  // Wait for navigation to dashboard
  await page.waitForURL('**/')
}

/**
 * Register a new user via page-level fetch().
 */
export async function registerUser(
  page: Page,
  credentials: { email: string; password: string },
): Promise<{ status: number; ok: boolean; body: unknown }> {
  const result = await page.evaluate(
    (args: {
      creds: { email: string; password: string }
      headers: Record<string, string>
    }) => {
      const { creds, headers } = args
      return fetch('/api/auth/register', {
        method: 'POST',
        headers,
        body: JSON.stringify(creds),
      }).then(async (res) => {
        const body = await res.json().catch(() => ({}))
        return { status: res.status, ok: res.ok, body }
      })
    },
    { creds: credentials, headers: API_HEADERS },
  )

  return result
}

/**
 * Clear auth session. Ignores errors gracefully (idempotent).
 */
export async function logout(page: Page): Promise<void> {
  try {
    await page.evaluate((headers: Record<string, string>) => {
      return fetch('/api/auth/logout', { method: 'POST', headers }).catch(() => {})
    }, API_HEADERS)
  } catch {
    // Idempotent
  }

  await page.context().clearCookies()
  await clearClientStorage(page)
}

/**
 * Clear localStorage/sessionStorage safely.
 * No-op if the page is on about:blank or a cross-origin URL.
 */
export async function clearClientStorage(page: Page): Promise<void> {
  try {
    await page.evaluate(() => {
      localStorage.clear()
      sessionStorage.clear()
    })
  } catch {
    // Page may be on about:blank or cross-origin
  }
}

/**
 * Assert the user is on the login page (not authenticated).
 */
export async function assertOnLoginPage(page: Page): Promise<void> {
  await page.waitForURL('**/login')
  await page.getByRole('heading', { name: /welcome back|bienvenido/i }).waitFor()
}

/**
 * Assert the user is on the dashboard (authenticated).
 */
export async function assertOnDashboard(page: Page): Promise<void> {
  await page.waitForURL('**/')
  await expect(page.getByRole('heading', { name: /welcome back|bienvenido/i })).toBeVisible()
}

/**
 * Navigate to login and fill credentials, then submit via form.
 */
export async function loginViaForm(
  page: Page,
  credentials: { email: string; password: string },
): Promise<void> {
  await page.goto(APP_URL.login)
  await page.getByLabel(/email/i).fill(credentials.email)
  await page.getByLabel(/password/i).fill(credentials.password)
  await page.getByRole('button', { name: /sign in|iniciar sesión/i }).click()
}

/**
 * Wait for network to settle after a login attempt.
 */
export async function waitForAuthSettle(page: Page): Promise<void> {
  await page.waitForTimeout(500)
}

/**
 * Mock the login API to return a specific response.
 * Use this in tests that need login to fail (e.g. error banner, security checks).
 */
export async function mockLoginResponse(
  page: Page,
  overrides: { status?: number; body?: Record<string, unknown> } = {},
): Promise<void> {
  const { status = 401, body = { title: 'Invalid credentials', detail: 'Invalid email or password.', status: 401 } } = overrides
  await page.route('**/api/auth/login', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/problem+json',
      body: JSON.stringify(body),
    })
  })
}

/**
 * Mock the refresh API to return a successful 200 response.
 * Use this in tests that need the session to persist across page loads.
 */
export async function mockRefreshResponse(
  page: Page,
  overrides: { accessToken?: string; email?: string; username?: string } = {},
): Promise<void> {
  const { accessToken = 'refreshed-test-token', email = 'dev@profiletailors.com', username = 'dev' } = overrides
  await page.route('**/api/auth/refresh', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/vnd.api.v1+json',
      body: JSON.stringify({
        accessToken,
        tokenType: 'Bearer',
        expiresIn: 3600,
        principalId: 'test-user',
        email,
        username,
        workspaceId: 'workspace-001',
      }),
    })
  })
}

/**
 * Mock the refresh API to return a 401 error (no active session).
 * Use this in tests that need to navigate as unauthenticated.
 */
export async function mockRefreshFailure(page: Page): Promise<void> {
  await page.route('**/api/auth/refresh', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      body: JSON.stringify({ title: 'Refresh session invalid', status: 401 }),
    })
  })
}

/**
 * Mock the register API to return a specific response.
 */
export async function mockRegisterResponse(
  page: Page,
  overrides: { status?: number; body?: Record<string, unknown> } = {},
): Promise<void> {
  const { status = 200, body = { accessToken: 'reg-test-token', tokenType: 'Bearer', expiresIn: 3600, principalId: 'reg-test-user', email: 'test@example.com', username: 'test' } } = overrides
  await page.route('**/api/auth/register', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/vnd.api.v1+json',
      body: JSON.stringify(body),
    })
  })
}
