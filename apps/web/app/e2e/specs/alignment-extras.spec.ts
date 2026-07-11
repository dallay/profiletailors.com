import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { APP_URL, VALID_CREDENTIALS } from '../fixtures/test-data'
import {
  mockLoginResponse,
  mockUserProfile,
  authenticateAs,
  keepSessionAlive,
} from '../fixtures/auth-helpers'
import { DashboardPage } from '../pages/dashboard-page'

test.describe('Gherkin Alignment Extras', () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('Unverified user can log in and receives PENDING session', async ({ page }) => {
    const loginPage = new LoginPage(page)
    const dashboard = new DashboardPage(page)

    await mockLoginResponse(page, {
      body: {
        accessToken: 'pending-token',
        tokenType: 'Bearer',
        expiresIn: 3600,
        principalId: 'user-pending',
        email: VALID_CREDENTIALS.email,
        username: 'pending-user',
        emailStatus: 'PENDING',
      },
    })

    await mockUserProfile(page, {
      principalId: 'user-pending',
      email: VALID_CREDENTIALS.email,
      username: 'pending-user',
      emailStatus: 'PENDING',
    })

    await loginPage.goto(APP_URL.login)
    await loginPage.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)

    await expect(page).toHaveURL(APP_URL.dashboard)
    await dashboard.expectAuthenticated()

    await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible()
  })

  test('LinkedIn callback requires authentication', async ({ page }) => {
    await page.goto('/integrations/linkedin/callback')
    await expect(page).toHaveURL(new RegExp(APP_URL.login))
  })

  test('LinkedIn callback without code handles error gracefully', async ({ page }) => {
    await authenticateAs(page)
    await keepSessionAlive(page)

    await page.goto('/integrations/linkedin/callback')

    await expect(page.getByText('Connection failed')).toBeVisible()
    await expect(page.getByText('LinkedIn did not return the required information')).toBeVisible()

    await expect(page.getByRole('button', { name: /try again/i })).toBeVisible()
  })

  test('Dashboard is visible during slow hydration (success)', async ({ page }) => {
    // Authenticate first
    await authenticateAs(page)

    // Delay the refresh API to simulate slow hydration
    await page.route('**/api/auth/refresh', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 2000))
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          accessToken: 'slow-token',
          tokenType: 'Bearer',
          expiresIn: 3600,
          principalId: 'user-slow',
          email: VALID_CREDENTIALS.email,
          username: 'slow-user',
          emailStatus: 'VERIFIED',
        }),
      })
    })

    // Navigate to dashboard
    await page.goto(APP_URL.dashboard)

    // Dashboard should eventually render without showing login page
    const dashboard = new DashboardPage(page)
    await expect(page).not.toHaveURL(new RegExp(APP_URL.login))
    await dashboard.expectAuthenticated()
  })
})
