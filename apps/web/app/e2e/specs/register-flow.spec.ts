import { test, expect, type Page } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { APP_URL, VALID_CREDENTIALS } from '../fixtures/test-data'
import {
  mockAuthenticatedSession,
  mockCurrentWorkspace,
  mockLoginResponse,
  mockRefreshFailure,
  mockRegisterSuccess,
  mockResendVerificationResponse,
  mockUserProfile,
  mockVerifyEmailResponse,
} from '../fixtures/auth-helpers'
import { safeGoto } from '../fixtures/navigation'

function uniqueEmail(prefix = 'e2e-register'): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}@profiletailors.com`
}

async function capabilities(page: Page, enabled: boolean) {
  await page.route('**/api/capabilities/public', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ registrationEnabled: enabled, passwordRecoveryEnabled: true }),
    }),
  )
}

test.describe('Register redesign', { tag: '@frontend' }, () => {
  test.beforeEach(async ({ page }) => {
    await mockRefreshFailure(page)
  })

  test('disabled registration fails closed at the requested URL and sends no register request', async ({
    page,
  }) => {
    await capabilities(page, false)
    let requests = 0
    await page.route('**/api/auth/register', () => {
      requests += 1
    })
    await page.goto(`${APP_URL.register}?email=kept@example.com`)

    await expect(page).toHaveURL(/\/register\?email=kept/)
    await expect(
      page.getByRole('heading', { name: /registration is currently unavailable/i }),
    ).toBeVisible()
    await expect(page.locator('form')).toHaveCount(0)
    await expect(page.getByRole('link', { name: /sign in/i })).toHaveAttribute('href', '/login')
    expect(requests).toBe(0)
  })

  test('switching modes preserves email while clearing passwords and consent', async ({ page }) => {
    await capabilities(page, true)
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.login)
    await auth.fillEmail('kept@example.com')
    await page.getByRole('link', { name: /register/i }).click()
    await auth.expectOnRegisterPage()
    await expect(auth.emailInput).toHaveValue('kept@example.com')
    await auth.fillPassword('Secret123!')
    await auth.acceptRegistrationRequirements()
    await page.getByTestId('login-navigation').click()
    await auth.expectOnLoginPage()
    await expect(auth.emailInput).toHaveValue('kept@example.com')
    await expect(auth.passwordInput).toHaveValue('')
  })

  test('successful registration sends the expected contract and creates an authenticated session', async ({
    page,
  }) => {
    await capabilities(page, true)
    const email = uniqueEmail()
    await mockRegisterSuccess(page, {
      email,
      username: email.slice(0, email.indexOf('@')),
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    })
    const requestPromise = page.waitForRequest(
      (request) => request.url().includes('/api/auth/register') && request.method() === 'POST',
    )
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)
    await auth.fillEmail(email)
    await auth.fillPassword('SecurePass123!')
    await auth.acceptRegistrationRequirements()
    await auth.submit()

    const request = await requestPromise
    expect(request.postDataJSON()).toMatchObject({
      email,
      password: 'SecurePass123!',
      confirmedAgeEligibility: true,
      acceptedTermsVersion: 'terms-v1.0.0',
    })
    expect(request.headers()['content-type']).toContain('application/json')
    expect(request.headers().accept).toContain('application/vnd.api.v1+json')
    await expect(page).toHaveURL(APP_URL.dashboard)
    await expect(page.getByRole('main')).toBeVisible()
  })

  test('registration error remains generic and retryable without clearing email', async ({
    page,
  }) => {
    await capabilities(page, true)
    await page.route('**/api/auth/register', (route) =>
      route.fulfill({
        status: 409,
        contentType: 'application/problem+json',
        body: JSON.stringify({
          title: 'User already exists',
          detail: "A user with email 'existing@example.com' already exists.",
          status: 409,
        }),
      }),
    )
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.register)
    await auth.fillEmail('existing@example.com')
    await auth.fillPassword('SecurePass123!')
    await auth.acceptRegistrationRequirements()
    await auth.submit()

    await expect(page.getByRole('alert')).toHaveText(/try again|unable|error/i)
    await expect(auth.emailInput).toHaveValue('existing@example.com')
    await expect(auth.submitButton).toBeEnabled()
    await auth.expectOnRegisterPage()
  })

  test('authenticated users are redirected away from guest-only register and login', async ({
    page,
  }) => {
    await mockAuthenticatedSession(page, {
      accessToken: 'auth-redirect-token',
      principalId: 'auth-redirect-user',
      email: VALID_CREDENTIALS.email,
      username: 'auth-redirect',
      emailStatus: 'VERIFIED',
      workspaceId: 'workspace-001',
    })
    await capabilities(page, true)

    await safeGoto(page, APP_URL.register)
    await expect(page).toHaveURL(APP_URL.dashboard)
    await safeGoto(page, APP_URL.login)
    await expect(page).toHaveURL(APP_URL.dashboard)
  })

  test('email verification endpoints preserve pending and verified lifecycle contracts', async ({
    page,
  }) => {
    await mockResendVerificationResponse(page)
    await mockVerifyEmailResponse(page)
    await page.goto(APP_URL.login)

    const resendStatus = await page.evaluate(
      async () =>
        (
          await fetch('/api/auth/resend-verification', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/vnd.api.v1+json' },
            body: JSON.stringify({ email: 'missing@example.com' }),
          })
        ).status,
    )
    const verified = await page.evaluate(async () =>
      (
        await fetch('/api/auth/verify-email', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Accept: 'application/vnd.api.v1+json' },
          body: JSON.stringify({ token: 'valid-token' }),
        })
      ).json(),
    )

    expect(resendStatus).toBe(202)
    expect(verified).toMatchObject({ accessToken: expect.any(String), emailStatus: 'VERIFIED' })
  })

  test('pending sessions retain dashboard access and verification gating contract', async ({
    page,
  }) => {
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
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    })
    await mockCurrentWorkspace(page)
    await capabilities(page, true)
    const auth = new LoginPage(page)
    await auth.goto(APP_URL.login)
    await auth.login(VALID_CREDENTIALS.email, 'SecurePass123!')
    await expect(page).toHaveURL(APP_URL.dashboard)
  })

  test('register fits at 320px and exposes new-password semantics and legal links', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 320, height: 800 })
    await capabilities(page, true)
    await page.goto(APP_URL.register)
    const auth = new LoginPage(page)
    await expect(auth.emailInput).toHaveAttribute('autocomplete', 'username')
    await expect(auth.passwordInput).toHaveAttribute('autocomplete', 'new-password')
    await expect(auth.confirmPasswordInput).toHaveAttribute('autocomplete', 'new-password')
    await expect(page.getByRole('link', { name: /terms of service/i })).toHaveAttribute(
      'href',
      '/terms',
    )
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  })
})
