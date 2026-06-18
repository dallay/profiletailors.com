/**
 * Auth helper functions for E2E tests.
 *
 * IMPORTANT: All API calls use page-level fetch() to ensure they go through
 * the Playwright route interception layer (page.route() and context.routeFromHAR()).
 * Playwright's APIRequestContext (page.request.*) bypasses these interceptors.
 */

import type { Page, Route } from '@playwright/test'
import { VALID_CREDENTIALS, APP_URL } from './test-data'

const API_HEADERS: Record<string, string> = {
  'Content-Type': 'application/json',
  Accept: 'application/vnd.api.v1+json',
}

const HAR_REPLAY = process.env.UPDATE_HAR !== 'true'

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
 * Clear all session state so each test starts from a known unauthenticated state.
 */
export async function resetSession(page: Page): Promise<void> {
  await page.context().clearCookies()
  await clearClientStorage(page)
}

/**
 * Route helper for tests that want to delay/inspect a request while still
 * letting HAR replay (or a later route handler) provide the final response.
 */
export async function fallbackAfterDelay(route: Route, delayMs: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, delayMs))
  await route.fallback()
}

/**
 * Apply the default HAR replay auth behavior explicitly.
 *
 * In replay mode, login/logout already come from the HAR and refresh returns
 * 401 by default. In record mode, these helpers are inert so the real backend
 * remains reachable.
 */
export async function useReplayAuthDefaults(page: Page): Promise<void> {
  if (!HAR_REPLAY) {
    return
  }

  await mockRefreshFailure(page)
}

/**
 * Keep the authenticated session alive across reloads/navigation by overriding
 * the HAR's default refresh failure.
 */
export async function keepSessionAlive(page: Page): Promise<void> {
  await mockRefreshResponse(page)
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
