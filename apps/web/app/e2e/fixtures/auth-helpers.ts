/**
 * Auth helper functions for E2E tests.
 *
 * IMPORTANT: All API calls use page-level fetch() to ensure they go through
 * the Playwright route interception layer (page.route() and context.routeFromHAR()).
 * Playwright's APIRequestContext (page.request.*) bypasses these interceptors.
 */

import type { Page, Route } from '@playwright/test'
import { VALID_CREDENTIALS, APP_URL } from './test-data'

interface SessionOverrides {
  accessToken?: string
  principalId?: string
  email?: string
  username?: string
  emailStatus?: string
  workspaceId?: string | null
}

interface RegisterFlowOverrides extends SessionOverrides {
  registerEmail?: string
}

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
 *
 * IMPORTANT: This only mocks POST /api/auth/login. It does NOT pre-hydrate the app.
 * Use mockAuthenticatedSession() separately only after the test no longer needs to render the login form.
 *
 * When status < 400, also sets the refresh-token cookie to match real backend behavior.
 */
export async function mockLoginResponse(
  page: Page,
  overrides: { status?: number; body?: Record<string, unknown> } = {},
): Promise<void> {
  const {
    status = 401,
    body = { title: 'Invalid credentials', detail: 'Invalid email or password.', status: 401 },
  } = overrides
  await page.route('**/api/auth/login', async (route) => {
    const isSuccess = status < 400
    const headers: Record<string, string> = {
      'content-type': isSuccess ? 'application/vnd.api.v1+json' : 'application/problem+json',
    }
    // Match the real backend Set-Cookie header from RefreshSessionCookieFactory
    if (isSuccess) {
      headers['set-cookie'] =
        'pt_refresh=mock-login.refresh-lookup; Path=/api/auth; HttpOnly; SameSite=Lax; Max-Age=604800'
    }
    await route.fulfill({
      status,
      headers,
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
  overrides: { accessToken?: string; email?: string; username?: string; emailStatus?: string } = {},
): Promise<void> {
  const {
    accessToken = 'refreshed-test-token',
    email = 'dev@profiletailors.com',
    username = 'dev',
    emailStatus = 'VERIFIED',
  } = overrides
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
        emailStatus,
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
 * Default status is 201 Created with full AuthTokens + session cookie (matching backend behavior).
 *
 * NOTE: Always active — HAR replay mode doesn't have register entries, so we must
 * intercept and return the correct contract for tests that cover registration.
 */
export async function mockRegisterResponse(
  page: Page,
  overrides: { status?: number; body?: Record<string, unknown> } = {},
): Promise<void> {
  const {
    status = 201,
    body = {
      accessToken: 'register-test-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      principalId: 'reg-test-user',
      email: 'test@example.com',
      username: 'test',
      emailStatus: 'PENDING',
      workspaceId: null,
    },
  } = overrides

  await page.route('**/api/auth/register', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/vnd.api.v1+json',
      body: JSON.stringify(body),
    })
  })
}

/**
 * Mock the resend verification email API.
 * Always returns 202 Accepted (even for non-existent emails to prevent enumeration).
 */
export async function mockResendVerificationResponse(
  page: Page,
  overrides: { status?: number } = {},
): Promise<void> {
  const { status = 202 } = overrides
  await page.route('**/api/auth/resend-verification', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify({}),
    })
  })
}

/**
 * Mock the verify email API with token validation.
 */
export async function mockVerifyEmailResponse(
  page: Page,
  overrides: { status?: number; body?: Record<string, unknown> } = {},
): Promise<void> {
  const {
    status = 200,
    body = {
      accessToken: 'verified-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      principalId: 'verified-user',
      email: 'verified@example.com',
      username: 'verified',
      emailStatus: 'VERIFIED',
      workspaceId: 'workspace-verified',
    },
  } = overrides
  await page.route('**/api/auth/verify-email', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/vnd.api.v1+json',
      body: JSON.stringify(body),
    })
  })
}

/**
 * Mocks GET /api/auth/me so the SPA can load user profile data after hydration.
 * Call this together with mockRefreshResponse for a complete session bootstrap.
 *
 * The defaults match what the SPA expects for the dashboard to render fully.
 */
export async function mockUserProfile(
  page: Page,
  overrides: {
    principalId?: string
    email?: string
    username?: string
    displayIdentity?: string
    workspaceId?: string
    workspaceName?: string
    workspaceRole?: string
  } = {},
): Promise<void> {
  const {
    principalId = 'test-user',
    email = 'dev@profiletailors.com',
    username = 'dev',
    displayIdentity = 'dev',
    workspaceId = 'workspace-001',
    workspaceName = "dev's Workspace",
    workspaceRole = 'OWNER',
  } = overrides

  await page.route('**/api/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/vnd.api.v1+json',
      body: JSON.stringify({
        principalId,
        email,
        username,
        displayIdentity,
      }),
    })
  })

  await page.route('**/api/tenancy/workspaces', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/vnd.api.v1+json',
      body: JSON.stringify([{ workspaceId, name: workspaceName, role: workspaceRole, icon: null }]),
    })
  })
}

/**
 * Mocks GET /api/tenancy/workspaces/current so the SPA resolves the active workspace.
 * Used when a session is already established and the dashboard tries to load workspace data.
 */
export async function mockCurrentWorkspace(
  page: Page,
  overrides: {
    workspaceId?: string
    workspaceName?: string
  } = {},
): Promise<void> {
  const { workspaceId = 'workspace-001', workspaceName = "dev's Workspace" } = overrides
  await page.route('**/api/tenancy/workspaces/current', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/vnd.api.v1+json',
      body: JSON.stringify({
        workspaceId,
        name: workspaceName,
        role: 'OWNER',
        icon: null,
      }),
    })
  })
}

/**
 * Full session bootstrap for E2E tests that need a logged-in SPA context.
 *
 * Sets up:
 * - refresh endpoint (always succeeds with valid tokens)
 * - /api/auth/me (returns user profile)
 * - /api/tenancy/workspaces (returns workspace list)
 *
 * Does NOT set a refresh token cookie — tests that reload should do that separately.
 *
 * Use this when a test needs to navigate as an authenticated user without going
 * through the register or login form.
 */
export async function mockAuthenticatedSession(
  page: Page,
  overrides: SessionOverrides = {},
): Promise<void> {
  const {
    accessToken = 'e2e-test-token',
    principalId = 'test-user',
    email = 'dev@profiletailors.com',
    username = 'dev',
    emailStatus = 'VERIFIED',
    workspaceId = 'workspace-001',
  } = overrides

  await mockRefreshResponse(page, { accessToken, email, username, emailStatus })
  await mockUserProfile(page, {
    principalId,
    email,
    username,
    displayIdentity: username,
    workspaceId,
  })
  await mockCurrentWorkspace(page, { workspaceId })
}

/**
 * Register-submit success mock for tests that start on /register as a guest.
 *
 * ONLY mocks the register endpoint itself. The SPA will call this after the user
 * submits the form. Does NOT mock /api/auth/refresh preemptively, because that
 * would make the SPA hydrate as authenticated before the test even submits,
 * causing the router guard to redirect away from /register.
 *
 * For tests that need to reload the page after registration, call
 * mockAuthenticatedSession() AFTER the register submit has completed.
 */
export async function mockRegisterSuccess(
  page: Page,
  overrides: RegisterFlowOverrides = {},
): Promise<void> {
  const {
    accessToken = 'register-e2e-token',
    principalId = 'reg-test-user',
    email = 'test@example.com',
    username = 'test',
    emailStatus = 'PENDING',
    workspaceId = null,
  } = overrides

  await page.route('**/api/auth/register', async (route) => {
    await route.fulfill({
      status: 201,
      contentType: 'application/vnd.api.v1+json',
      body: JSON.stringify({
        accessToken,
        tokenType: 'Bearer',
        expiresIn: 900,
        principalId,
        email,
        username,
        emailStatus,
        workspaceId,
      }),
    })
  })

  // Mock /api/auth/refresh so the FIRST app boot remains unauthenticated
  // (user is still on /register), but subsequent refreshes after registration/reload succeed.
  // HAR doesn't have a refresh entry, so without this the SPA fails to hydrate after reload.
  let seenRefresh = false
  await page.route('**/api/auth/refresh', async (route) => {
    if (!seenRefresh) {
      seenRefresh = true
      await route.fulfill({
        status: 401,
        contentType: 'application/problem+json',
        body: JSON.stringify({ title: 'Refresh session invalid', status: 401 }),
      })
      return
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/vnd.api.v1+json',
      body: JSON.stringify({
        accessToken,
        tokenType: 'Bearer',
        expiresIn: 900,
        principalId,
        email,
        username,
        emailStatus,
        workspaceId,
      }),
    })
  })

  await mockUserProfile(page, {
    principalId,
    email,
    username,
    displayIdentity: username,
    workspaceId: workspaceId ?? undefined,
  })

  await mockCurrentWorkspace(page, { workspaceId: workspaceId ?? 'workspace-001' })
}
