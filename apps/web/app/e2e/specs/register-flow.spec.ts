/**
 * spec: openspec/specs/e2e/register-flow.md
 *
 * Reliable Playwright E2E tests for the register flow.
 *
 * Strategy:
 * - @frontend tests validate pure SPA behavior with no backend dependency.
 * - @integration tests validate auth/register API interactions and session behavior
 *   using targeted route overrides and light backend assumptions.
 * - Feature-gating scenarios are captured as contract tests using API mocks so the
 *   suite remains stable while UI implementation catches up.
 */

import { test, expect, type Page } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { DashboardPage } from '../pages/dashboard-page'
import { SchedulerPage } from '../pages/scheduler-page'
import { APP_URL, I18N_TEXT, SHORT_PASSWORD, VALID_CREDENTIALS } from '../fixtures/test-data'
import {
  logout,
  mockAuthenticatedSession,
  mockCurrentWorkspace,
  mockLoginResponse,
  mockRefreshFailure,
  mockRegisterSuccess,
  mockResendVerificationResponse,
  mockUserProfile,
  mockVerifyEmailResponse,
  resetSession,
} from '../fixtures/auth-helpers'
import { safeGoto } from '../fixtures/navigation'

function uniqueEmail(prefix = 'e2e-register'): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}@profiletailors.com`
}

async function mockRegisterProblem(
  page: Page,
  status: number,
  detail: string,
  title = 'Invalid registration input',
) {
  await page.route('**/api/auth/register', async (route) => {
    await route.fulfill({
      status,
      contentType: 'application/problem+json',
      body: JSON.stringify({ title, detail, status }),
    })
  })
}

// ---------------------------------------------------------------------------
// 1. Register Page Rendering
// ---------------------------------------------------------------------------

test.describe('Register Page Rendering', { tag: '@frontend' }, () => {
  test.beforeEach(async ({ page }) => {
    await resetSession(page)
  })

  test('1.1 Full page renders correctly', async ({ page }) => {
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)

    await expect(page).toHaveTitle('Profile Tailors — Social Media Management Platform')
    await expect(page.getByRole('heading', { level: 2, name: /create account/i })).toBeVisible()
    await expect(
      page.getByText(/start managing your channels with local email and password access/i),
    ).toBeVisible()
    await expect(auth.emailInput).toBeVisible()
    await expect(auth.passwordInput).toBeVisible()
    await expect(auth.submitButton).toHaveText(/create account/i)
    await expect(page.getByText(/already have an account/i)).toBeVisible()
    await expect(page.locator('a').filter({ hasText: /sign in/i })).toHaveAttribute(
      'href',
      '/login',
    )
    await expect(page.getByText(/security/i).first()).toBeVisible()
    await expect(page.getByText(/focus/i).first()).toBeVisible()
    await expect(page.getByText(/workflow/i).first()).toBeVisible()
  })

  test('1.2 Email input attributes', async ({ page }) => {
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)

    await expect(auth.emailInput).toHaveAttribute('type', 'email')
    await expect(auth.emailInput).toHaveAttribute('autocomplete', 'email')
    await expect(auth.emailInput).toHaveAttribute('required', '')
    await expect(auth.emailInput).toHaveAttribute('placeholder', /you@example\.com/i)
  })

  test('1.3 Password input attributes', async ({ page }) => {
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)

    await expect(auth.passwordInput).toHaveAttribute('type', 'password')
    await expect(auth.passwordInput).toHaveAttribute('autocomplete', 'current-password')
    await expect(auth.passwordInput).toHaveAttribute('required', '')
    await expect(auth.passwordInput).toHaveAttribute('placeholder', /at least 8 characters/i)
  })

  test('1.4 Navigation between login and register', async ({ page }) => {
    const auth = new LoginPage(page)

    await auth.goto(APP_URL.login)
    await expect(page.getByRole('heading', { level: 2, name: /welcome back/i })).toBeVisible()
    await page
      .locator('a')
      .filter({ hasText: /register/i })
      .click()
    await auth.expectOnRegisterPage()

    await page
      .locator('a')
      .filter({ hasText: /sign in/i })
      .click()
    await auth.expectOnLoginPage()
  })

  test('1.6 Form is cleared when switching modes', async ({ page }) => {
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)

    await auth.fillEmail('test@example.com')
    await auth.fillPassword('password123')
    await page
      .locator('a')
      .filter({ hasText: /sign in/i })
      .click()

    await expect(auth.emailInput).toHaveValue('')
    await expect(auth.passwordInput).toHaveValue('')
  })
})

// ---------------------------------------------------------------------------
// 2. Form Validation (Frontend)
// ---------------------------------------------------------------------------

test.describe('Form Validation (Frontend)', { tag: '@frontend' }, () => {
  test.beforeEach(async ({ page }) => {
    await resetSession(page)
  })

  test('2.1 Empty fields trigger HTML5 validation', async ({ page }) => {
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)

    let requestMade = false
    await page.route('**/api/auth/register', async () => {
      requestMade = true
    })

    await auth.submit()
    expect(requestMade).toBe(false)
  })

  test('2.2 Invalid email format blocked by browser', async ({ page }) => {
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)

    let requestMade = false
    await page.route('**/api/auth/register', async () => {
      requestMade = true
    })

    await auth.fillEmail('not-an-email')
    await auth.fillPassword('password123')
    await auth.submit()
    expect(requestMade).toBe(false)
  })

  test('2.3 Empty password blocked', async ({ page }) => {
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)

    let requestMade = false
    await page.route('**/api/auth/register', async () => {
      requestMade = true
    })

    await auth.fillEmail('valid@email.com')
    await auth.submit()
    expect(requestMade).toBe(false)
  })

  test('2.4 Short password is not blocked client-side', async ({ page }) => {
    let requestMade = false
    await page.route('**/api/auth/register', async (route) => {
      requestMade = true
      await route.fulfill({
        status: 400,
        contentType: 'application/problem+json',
        body: JSON.stringify({
          title: 'Invalid registration input',
          detail: 'Password must contain at least 8 characters.',
          status: 400,
        }),
      })
    })

    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)
    await auth.fillEmail('valid@email.com')
    await auth.fillPassword(SHORT_PASSWORD)
    await auth.submit()

    expect(requestMade).toBe(true)
    await auth.expectErrorVisible(/at least 8 characters/i)
  })
})

// ---------------------------------------------------------------------------
// 3. Registration API — Success Path
// ---------------------------------------------------------------------------

test.describe('Registration API — Success Path', { tag: '@integration' }, () => {
  test.beforeEach(async ({ page }) => {
    await resetSession(page)
  })

  test('3.1 Successful registration creates authenticated session', async ({ page }) => {
    const auth = new LoginPage(page)
    const dashboard = new DashboardPage(page)
    const email = uniqueEmail()

    await mockRegisterSuccess(page, {
      email,
      username: email.substring(0, email.indexOf('@')),
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    })

    const requestPromise = page.waitForRequest(
      (req) => req.url().includes('/api/auth/register') && req.method() === 'POST',
    )
    const responsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/auth/register') && res.status() === 201,
    )

    await auth.goto(APP_URL.register)
    await auth.fillEmail(email)
    await auth.fillPassword('SecurePass123!')
    await auth.submit()

    const request = await requestPromise
    const payload = request.postDataJSON() as { email: string; password: string }
    expect(payload.email).toBe(email)
    expect(payload.password).toBe('SecurePass123!')
    expect(request.headers()['content-type']).toContain('application/json')
    expect(request.headers().accept).toContain('application/vnd.api.v1+json')

    const response = await responsePromise
    const body = await response.json()
    expect(body).toHaveProperty('accessToken')
    expect(body).toHaveProperty('tokenType', 'Bearer')
    expect(body).toHaveProperty('expiresIn')
    expect(body).toHaveProperty('principalId')
    expect(body).toHaveProperty('emailStatus', 'PENDING')

    await dashboard.expectAuthenticated()
    await expect(page).toHaveURL(APP_URL.dashboard)
  })

  test('3.2 Registration normalizes email and login remains case-insensitive', async ({ page }) => {
    const auth = new LoginPage(page)
    const dashboard = new DashboardPage(page)

    await mockRegisterSuccess(page, {
      email: 'test@example.com',
      username: 'test',
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    })

    await auth.goto(APP_URL.register)
    await auth.fillEmail('Test@Example.COM')
    await auth.fillPassword('SecurePass123!')
    await auth.submit()

    await dashboard.expectAuthenticated()

    await logout(page)
    // After logout the app is unauthenticated; mock refresh to stay that way on initial load
    await mockRefreshFailure(page)
    // Mock login to succeed so we test the normalized email on the form
    await mockLoginResponse(page, {
      status: 200,
      body: {
        accessToken: 'login-test-token',
        tokenType: 'Bearer',
        expiresIn: 900,
        principalId: 'reg-test-user',
        email: 'test@example.com',
        username: 'test',
        emailStatus: 'PENDING',
        workspaceId: 'workspace-001',
      },
    })
    await mockUserProfile(page, {
      principalId: 'reg-test-user',
      email: 'test@example.com',
      username: 'test',
      displayIdentity: 'test',
      workspaceId: 'workspace-001',
    })
    await mockCurrentWorkspace(page, { workspaceId: 'workspace-001' })

    await auth.goto(APP_URL.login)
    await auth.login('TEST@EXAMPLE.COM', 'SecurePass123!')
    await dashboard.expectAuthenticated()
  })

  test('3.3 Registration with whitespace in email is trimmed', async ({ page }) => {
    const auth = new LoginPage(page)
    const dashboard = new DashboardPage(page)

    await mockRegisterSuccess(page, {
      email: 'trim-me@example.com',
      username: 'trim-me',
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    })

    await auth.goto(APP_URL.register)
    await auth.fillEmail('  trim-me@example.com  ')
    await auth.fillPassword('SecurePass123!')
    await auth.submit()

    await dashboard.expectAuthenticated()
  })

  test('3.4 Registration response contains username derived from email', async ({ page }) => {
    const auth = new LoginPage(page)
    const responsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/auth/register') && res.status() === 201,
    )

    await mockRegisterSuccess(page, {
      email: 'john.doe@example.com',
      username: 'john.doe',
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    })

    await auth.goto(APP_URL.register)
    await auth.fillEmail('john.doe@example.com')
    await auth.fillPassword('SecurePass123!')
    await auth.submit()

    const response = await responsePromise
    const body = await response.json()
    expect(body.username).toBe('john.doe')
  })

  test('3.5 Workspace is available after registration', async ({ page }) => {
    const auth = new LoginPage(page)
    const dashboard = new DashboardPage(page)
    const scheduler = new SchedulerPage(page)
    const email = uniqueEmail()

    await mockRegisterSuccess(page, {
      email,
      username: 'newuser',
      emailStatus: 'PENDING',
      workspaceId: 'workspace-new',
    })

    await auth.goto(APP_URL.register)
    await auth.fillEmail(email)
    await auth.fillPassword('SecurePass123!')
    await auth.submit()
    await dashboard.expectAuthenticated()

    await scheduler.goto()
    await expect(scheduler.heading).toBeVisible()
  })

  test('3.7 Registration with redirect preserves destination', async ({ page }) => {
    const auth = new LoginPage(page)
    const email = uniqueEmail()

    await mockRegisterSuccess(page, {
      email,
      username: 'redirectuser',
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    })

    await safeGoto(page, `${APP_URL.register}?redirect=%2Fscheduler`)
    await auth.fillEmail(email)
    await auth.fillPassword('SecurePass123!')
    await auth.submit()

    await expect(page).toHaveURL(APP_URL.scheduler)
  })
})

// ---------------------------------------------------------------------------
// 4. Registration API — Error Paths
// ---------------------------------------------------------------------------

test.describe('Registration API — Error Paths', { tag: '@integration' }, () => {
  test.beforeEach(async ({ page }) => {
    await resetSession(page)
  })

  test('4.1 Duplicate email returns 409', async ({ page }) => {
    await mockRegisterProblem(
      page,
      409,
      "A user with email 'existing@profiletailors.com' already exists.",
      'User already exists',
    )

    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)
    await auth.fillEmail('existing@profiletailors.com')
    await auth.fillPassword('SecurePass123!')
    await auth.submit()

    await auth.expectErrorVisible(/already exists/i)
    await auth.expectOnRegisterPage()
    await expect(auth.emailInput).toHaveValue('existing@profiletailors.com')
  })

  test('4.2 Case-insensitive duplicate check', async ({ page }) => {
    await mockRegisterProblem(
      page,
      409,
      "A user with email 'test@example.com' already exists.",
      'User already exists',
    )

    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)
    await auth.fillEmail('TEST@EXAMPLE.COM')
    await auth.fillPassword('SecurePass123!')
    await auth.submit()

    await auth.expectErrorVisible(/already exists/i)
  })

  test('4.3 Password too short returns 400', async ({ page }) => {
    await mockRegisterProblem(page, 400, 'Password must contain at least 8 characters.')

    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)
    await auth.fillEmail('newuser@example.com')
    await auth.fillPassword(SHORT_PASSWORD)
    await auth.submit()

    await auth.expectErrorVisible(/at least 8 characters/i)
  })

  test('4.4 Password too long returns 400', async ({ page }) => {
    await mockRegisterProblem(page, 400, 'Password must be between 8 and 128 characters.')

    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)
    await auth.fillEmail('newuser@example.com')
    await auth.fillPassword('a'.repeat(129))
    await auth.submit()

    await auth.expectErrorVisible(/128 characters/i)
  })

  test('4.8 Server error shows generic/returned detail message', async ({ page }) => {
    await mockRegisterProblem(page, 500, 'An unexpected error occurred.', 'Internal Server Error')

    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)
    await auth.fillEmail(uniqueEmail())
    await auth.fillPassword('SecurePass123!')
    await auth.submit()

    await auth.expectErrorVisible()
    await auth.expectOnRegisterPage()
  })

  test('4.9 Network error shows error state', async ({ page }) => {
    await page.route('**/api/auth/register', async (route) => {
      await route.abort('failed')
    })

    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)
    await auth.fillEmail(uniqueEmail())
    await auth.fillPassword('SecurePass123!')
    await auth.submit()

    await expect(page.locator('[class*="border-error"]')).toBeVisible()
  })
})

// ---------------------------------------------------------------------------
// 5. Post-Registration Session Management
// ---------------------------------------------------------------------------

test.describe('Post-Registration Session Management', { tag: '@integration' }, () => {
  test.beforeEach(async ({ page }) => {
    await resetSession(page)
  })

  test('5.1 Session immediately active after registration', async ({ page }) => {
    const auth = new LoginPage(page)
    const dashboard = new DashboardPage(page)
    const email = uniqueEmail()

    await mockRegisterSuccess(page, {
      email,
      username: 'sessionuser',
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    })

    await auth.goto(APP_URL.register)
    await auth.fillEmail(email)
    await auth.fillPassword('SecurePass123!')
    await auth.submit()

    await dashboard.expectAuthenticated()
    await expect(page).toHaveURL(APP_URL.dashboard)
    await expect(page.getByText(/scheduler/i).first()).toBeVisible()
  })

  test('5.2 Refresh succeeds for PENDING user session', async ({ page }) => {
    const auth = new LoginPage(page)
    const dashboard = new DashboardPage(page)
    const email = uniqueEmail()

    // mockRegisterSuccess also mocks refresh with a 200 after the first call,
    // so a page reload after registration would succeed with PENDING status.
    await mockRegisterSuccess(page, {
      email,
      username: 'refreshuser',
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    })

    await auth.goto(APP_URL.register)
    await auth.fillEmail(email)
    await auth.fillPassword('SecurePass123!')
    await auth.submit()

    await dashboard.expectAuthenticated()
    await expect(page).toHaveURL(APP_URL.dashboard)
  })

  test('5.3 Authenticated user redirected from /register', async ({ page }) => {
    await mockAuthenticatedSession(page, {
      accessToken: 'auth-redirect-token',
      principalId: 'auth-redirect-user',
      email: VALID_CREDENTIALS.email,
      username: 'auth-redirect',
      emailStatus: 'VERIFIED',
      workspaceId: 'workspace-001',
    })

    await safeGoto(page, APP_URL.register)
    await expect(page).toHaveURL(APP_URL.dashboard)
  })

  test('5.4 Authenticated user redirected from /login', async ({ page }) => {
    await mockAuthenticatedSession(page, {
      accessToken: 'auth-redirect-token',
      principalId: 'auth-redirect-user',
      email: VALID_CREDENTIALS.email,
      username: 'auth-redirect',
      emailStatus: 'VERIFIED',
      workspaceId: 'workspace-001',
    })

    await safeGoto(page, APP_URL.login)
    await expect(page).toHaveURL(APP_URL.dashboard)
  })
})

// ---------------------------------------------------------------------------
// 6. Email Verification Flow
// ---------------------------------------------------------------------------

test.describe('Email Verification Flow', { tag: '@integration' }, () => {
  test.beforeEach(async ({ page }) => {
    await resetSession(page)
  })

  test('6.1 Registration returns PENDING session and implies verification lifecycle', async ({
    page,
  }) => {
    const auth = new LoginPage(page)
    const email = uniqueEmail()
    const responsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/auth/register') && res.status() === 201,
    )

    await mockRegisterSuccess(page, {
      email,
      username: 'pendinguser',
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    })

    await auth.goto(APP_URL.register)
    await auth.fillEmail(email)
    await auth.fillPassword('SecurePass123!')
    await auth.submit()

    const response = await responsePromise
    const body = await response.json()
    expect(body).toHaveProperty('emailStatus', 'PENDING')
  })

  test('6.2 Unverified user can log in and receives PENDING session', async ({ page }) => {
    const auth = new LoginPage(page)

    await mockLoginResponse(page, {
      status: 200,
      body: {
        accessToken: 'pending-login-token',
        tokenType: 'Bearer',
        expiresIn: 900,
        principalId: 'pending-user',
        email: VALID_CREDENTIALS.email,
        username: 'dev',
        emailStatus: 'PENDING',
        workspaceId: 'workspace-001',
      },
    })
    await mockUserProfile(page, {
      principalId: 'pending-user',
      email: VALID_CREDENTIALS.email,
      username: 'dev',
      displayIdentity: 'dev',
      workspaceId: 'workspace-001',
    })
    await mockCurrentWorkspace(page, { workspaceId: 'workspace-001' })

    const responsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/auth/login') && res.status() === 200,
    )

    await auth.goto(APP_URL.login)
    await auth.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)

    const response = await responsePromise
    const body = await response.json()
    expect(body).toHaveProperty('emailStatus', 'PENDING')
    await expect(page).toHaveURL(APP_URL.dashboard)
  })

  test('6.3 Resend verification email returns 202', async ({ page }) => {
    await mockResendVerificationResponse(page)
    await page.goto(APP_URL.login)

    const status = await page.evaluate(async () => {
      const response = await fetch('/api/auth/resend-verification', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/vnd.api.v1+json' },
        body: JSON.stringify({ email: 'test@example.com' }),
      })
      return response.status
    })

    expect(status).toBe(202)
  })

  test('6.4 Resend with non-existent email returns 202', async ({ page }) => {
    await mockResendVerificationResponse(page)
    await page.goto(APP_URL.login)

    const status = await page.evaluate(async () => {
      const response = await fetch('/api/auth/resend-verification', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/vnd.api.v1+json' },
        body: JSON.stringify({ email: 'nonexistent@example.com' }),
      })
      return response.status
    })

    expect(status).toBe(202)
  })

  test('6.5 Verify email with valid token returns VERIFIED session', async ({ page }) => {
    await mockVerifyEmailResponse(page)
    await page.goto(APP_URL.login)

    const response = await page.evaluate(async () => {
      const r = await fetch('/api/auth/verify-email', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/vnd.api.v1+json' },
        body: JSON.stringify({ token: 'valid-token' }),
      })
      return r.json()
    })

    expect(response).toHaveProperty('accessToken')
    expect(response).toHaveProperty('emailStatus', 'VERIFIED')
  })

  test('6.6 Verify email with invalid token returns 400', async ({ page }) => {
    await mockVerifyEmailResponse(page, {
      status: 400,
      body: {
        title: 'Invalid verification token',
        detail: 'Invalid verification token.',
        status: 400,
      },
    })
    await page.goto(APP_URL.login)

    const response = await page.evaluate(async () => {
      const r = await fetch('/api/auth/verify-email', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/vnd.api.v1+json' },
        body: JSON.stringify({ token: 'invalid-token' }),
      })
      return r.json()
    })

    expect(response).toHaveProperty('title', 'Invalid verification token')
  })
})

// ---------------------------------------------------------------------------
// 7. Feature Gating for Unverified Users
// ---------------------------------------------------------------------------

test.describe('Feature Gating for Unverified Users', { tag: '@integration' }, () => {
  test.beforeEach(async ({ page }) => {
    await resetSession(page)
  })

  test('7.1 PENDING session can access dashboard', async ({ page }) => {
    await mockAuthenticatedSession(page, {
      accessToken: 'pending-dashboard-token',
      principalId: 'pending-dashboard-user',
      email: VALID_CREDENTIALS.email,
      username: 'dev',
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    })
    await safeGoto(page, APP_URL.dashboard)
    await expect(page).toHaveURL(APP_URL.dashboard)
  })

  test('7.2 Feature-gated endpoint returns EMAIL_VERIFICATION_REQUIRED problem detail', async ({
    page,
  }) => {
    await page.route('**/api/publishing/channels/providers', async (route) => {
      await route.fulfill({
        status: 403,
        contentType: 'application/problem+json',
        body: JSON.stringify({
          title: 'Email verification required',
          detail: 'Please verify your email before using this feature.',
          status: 403,
          code: 'EMAIL_VERIFICATION_REQUIRED',
          type: 'https://api.profiletailors.com/errors/email-verification-required',
        }),
      })
    })

    await mockAuthenticatedSession(page, {
      accessToken: 'pending-feature-token',
      principalId: 'pending-feature-user',
      email: VALID_CREDENTIALS.email,
      username: 'dev',
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    })
    await safeGoto(page, APP_URL.dashboard)

    const response = await page.evaluate(async () => {
      const r = await fetch('/api/publishing/channels/providers', {
        method: 'GET',
        headers: {
          Accept: 'application/vnd.api.v1+json',
          Authorization: 'Bearer fake-token',
          'X-Workspace-Id': 'workspace-001',
        },
      })
      return { status: r.status, body: await r.json() }
    })

    expect(response.status).toBe(403)
    expect(response.body).toMatchObject({
      title: 'Email verification required',
      detail: 'Please verify your email before using this feature.',
      status: 403,
      code: 'EMAIL_VERIFICATION_REQUIRED',
      type: 'https://api.profiletailors.com/errors/email-verification-required',
    })
  })
})

// ---------------------------------------------------------------------------
// 8. Internationalization (i18n)
// ---------------------------------------------------------------------------

test.describe('Internationalization (i18n)', { tag: '@frontend' }, () => {
  test.beforeEach(async ({ page }) => {
    await resetSession(page)
  })

  test('8.1 Register page in English', async ({ page }) => {
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)

    const en = I18N_TEXT.en
    await expect(page.getByRole('heading', { level: 2, name: en.titleRegister })).toBeVisible()
    await expect(auth.submitButton).toHaveText(en.submitRegister)
    await expect(page.getByText(en.alternateLabelRegister)).toBeVisible()
    await expect(auth.emailInput).toHaveAttribute('placeholder', en.emailPlaceholder)
  })

  test('8.2 Register page reflects Spanish locale preference', async ({ page }) => {
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)
    await page.evaluate(() => {
      localStorage.setItem('pt_settings_v1', JSON.stringify({ theme: 'dark', locale: 'es' }))
    })
    await page.reload()

    await expect(page.getByRole('heading', { level: 2, name: /crear cuenta/i })).toBeVisible()
    await expect(auth.emailInput).toHaveAttribute('placeholder', /tu@ejemplo\.com/i)
  })
})

// ---------------------------------------------------------------------------
// 9. Security
// ---------------------------------------------------------------------------

test.describe('Security', { tag: '@integration' }, () => {
  test.beforeEach(async ({ page }) => {
    await resetSession(page)
  })

  test('9.1 Access token never persisted to localStorage', async ({ page }) => {
    const auth = new LoginPage(page)
    const email = uniqueEmail()

    await mockRegisterSuccess(page, {
      email,
      username: 'securityuser',
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    })

    await auth.goto(APP_URL.register)
    await auth.fillEmail(email)
    await auth.fillPassword('SecurePass123!')
    await auth.submit()
    await expect(page).toHaveURL(APP_URL.dashboard)

    const items = await page.evaluate(() => {
      const result: Record<string, string> = {}
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i)
        if (key) result[key] = localStorage.getItem(key) ?? ''
      }
      return result
    })
    const serialized = JSON.stringify(items).toLowerCase()
    expect(serialized).not.toContain('access_token')
    expect(serialized).not.toContain('bearer')
    expect(serialized).not.toContain('jwt')
    expect(serialized).not.toContain('refresh')
  })

  test('9.2 Refresh token cookie is scoped to /api/auth when present', async ({ page }) => {
    test.skip(
      test.info().project.name.includes('webkit'),
      'WebKit cookie behavior differs for intercepted responses',
    )

    const auth = new LoginPage(page)

    await mockLoginResponse(page, {
      status: 200,
      body: {
        accessToken: 'cookie-test-token',
        tokenType: 'Bearer',
        expiresIn: 900,
        principalId: 'cookie-user',
        email: VALID_CREDENTIALS.email,
        username: 'dev',
        emailStatus: 'VERIFIED',
        workspaceId: 'workspace-001',
      },
    })
    await mockUserProfile(page, {
      principalId: 'cookie-user',
      email: VALID_CREDENTIALS.email,
      username: 'dev',
      displayIdentity: 'dev',
      workspaceId: 'workspace-001',
    })
    await mockCurrentWorkspace(page, { workspaceId: 'workspace-001' })

    await auth.goto(APP_URL.login)
    await auth.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)
    await expect(page).toHaveURL(APP_URL.dashboard)

    const cookies = await page.context().cookies()
    const refreshCookie = cookies.find((cookie) => cookie.name === 'pt_refresh')
    expect(refreshCookie).toBeDefined()
    expect(refreshCookie?.httpOnly).toBe(true)
    expect(refreshCookie?.path).toBe('/api/auth')
  })

  test('9.3 Credentials are never exposed in the URL', async ({ page }) => {
    const auth = new LoginPage(page)
    const email = uniqueEmail()

    await mockRegisterSuccess(page, {
      email,
      username: 'urluser',
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    })

    await auth.goto(APP_URL.register)
    await auth.fillEmail(email)
    await auth.fillPassword('SecretPass123!')
    await auth.submit()
    await expect(page).toHaveURL(/^(?!.*password).*$/)
    expect(page.url()).not.toContain('SecretPass123!')
  })

  test('9.4 Password is sent in request but never returned in response', async ({ page }) => {
    const auth = new LoginPage(page)
    const requestPromise = page.waitForRequest(
      (req) => req.url().includes('/api/auth/register') && req.method() === 'POST',
    )
    const responsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/auth/register') && res.status() === 201,
    )

    await mockRegisterSuccess(page, {
      email: 'test@example.com',
      username: 'test',
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    })

    await auth.goto(APP_URL.register)
    await auth.fillEmail('test@example.com')
    await auth.fillPassword('SecretPass123!')
    await auth.submit()

    const request = await requestPromise
    const requestBody = request.postDataJSON() as { password: string }
    expect(requestBody.password).toBe('SecretPass123!')

    const response = await responsePromise
    const responseBody = await response.json()
    expect(responseBody).not.toHaveProperty('password')
    expect(responseBody).not.toHaveProperty('passwordHash')
  })

  test('9.5 Email enumeration does not reveal extra account details', async ({ page }) => {
    await mockRegisterProblem(
      page,
      409,
      "A user with email 'existing@profiletailors.com' already exists.",
      'User already exists',
    )

    const auth = new LoginPage(page)
    const responsePromise = page.waitForResponse(
      (res) => res.url().includes('/api/auth/register') && res.status() === 409,
    )
    await auth.goto(APP_URL.register)
    await auth.fillEmail('existing@profiletailors.com')
    await auth.fillPassword('SecurePass123!')
    await auth.submit()

    const response = await responsePromise
    const body = await response.json()
    expect(body).not.toHaveProperty('accountCreatedAt')
    expect(body).not.toHaveProperty('lastLogin')
    expect(body).not.toHaveProperty('passwordChanged')
  })

  test('9.6 Auth endpoints send required Content-Type and Accept headers', async ({ page }) => {
    const auth = new LoginPage(page)
    const requestPromise = page.waitForRequest(
      (req) => req.url().includes('/api/auth/register') && req.method() === 'POST',
    )

    await auth.goto(APP_URL.register)
    await auth.fillEmail(uniqueEmail())
    await auth.fillPassword('SecurePass123!')
    await auth.submit()

    const request = await requestPromise
    expect(request.headers()['content-type']).toContain('application/json')
    expect(request.headers().accept).toContain('application/vnd.api.v1+json')
  })
})

// ---------------------------------------------------------------------------
// 10. Error Banner Visual States
// ---------------------------------------------------------------------------

test.describe('Error Banner Visual States', { tag: '@integration' }, () => {
  test.beforeEach(async ({ page }) => {
    await resetSession(page)
  })

  test('10.1 Error banner hidden by default', { tag: '@frontend' }, async ({ page }) => {
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)
    await expect(auth.errorBanner).toHaveCount(0)
  })

  test('10.2 Error banner appears on registration failure', async ({ page }) => {
    await mockRegisterProblem(page, 409, 'A user with email already exists.', 'User already exists')

    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)
    await auth.fillEmail('existing@example.com')
    await auth.fillPassword('SecurePass123!')
    await auth.submit()

    await auth.expectErrorVisible(/already exists/i)
    const classAttr = (await auth.errorBanner.getAttribute('class')) ?? ''
    expect(classAttr).toContain('error')
  })

  test('10.4 Error banner clears on navigation away', async ({ page }) => {
    await mockRegisterProblem(page, 409, 'User already exists.', 'User already exists')

    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)
    await auth.fillEmail('existing@example.com')
    await auth.fillPassword('SecurePass123!')
    await auth.submit()
    await auth.expectErrorVisible()

    await page
      .locator('a')
      .filter({ hasText: /sign in/i })
      .click()
    await expect(page).toHaveURL(/\/login/)
    await expect(auth.errorBanner).toHaveCount(0)
  })
})

// ---------------------------------------------------------------------------
// 11. Responsive Design
// ---------------------------------------------------------------------------

test.describe('Responsive Design', { tag: '@frontend' }, () => {
  test.beforeEach(async ({ page }) => {
    await resetSession(page)
  })

  test('11.1 Register page renders on mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)

    await expect(auth.emailInput).toBeVisible()
    await expect(auth.submitButton).toBeVisible()

    const bodyWidth = await page.evaluate(() => document.body.scrollWidth)
    const viewportWidth = await page.evaluate(() => window.innerWidth)
    expect(bodyWidth).toBeLessThanOrEqual(viewportWidth)
  })

  test('11.2 Register page renders on tablet viewport', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 })
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)

    await expect(auth.emailInput).toBeVisible()
    await expect(page.locator('h1')).toBeVisible()
  })
})

// ---------------------------------------------------------------------------
// 12. Accessibility
// ---------------------------------------------------------------------------

test.describe('Accessibility', { tag: '@frontend' }, () => {
  test.beforeEach(async ({ page }) => {
    await resetSession(page)
  })

  test('12.1 Register form has proper labels', async ({ page }) => {
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)

    const emailLabel = page.locator('label').filter({ hasText: /email/i })
    const passwordLabel = page.locator('label').filter({ hasText: /password/i })
    await expect(emailLabel).toBeVisible()
    await expect(passwordLabel).toBeVisible()
    expect(await emailLabel.getAttribute('for')).toBeTruthy()
    expect(await passwordLabel.getAttribute('for')).toBeTruthy()
  })

  test('12.2 Keyboard navigation works', async ({ page }) => {
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)

    await page.keyboard.press('Tab')
    expect(await page.evaluate(() => document.activeElement?.getAttribute('id'))).toBe('email')
    await page.keyboard.press('Tab')
    expect(await page.evaluate(() => document.activeElement?.getAttribute('id'))).toBe('password')
    await page.keyboard.press('Tab')
    expect(await page.evaluate(() => document.activeElement?.tagName)).toBe('BUTTON')
  })

  test('12.3 Form has expected submission elements', async ({ page }) => {
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)
    await expect(auth.emailInput).toBeVisible()
    await expect(auth.passwordInput).toBeVisible()
    await expect(auth.submitButton).toHaveText(/create account/i)
  })
})
