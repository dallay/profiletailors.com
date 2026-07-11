import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { APP_URL, VALID_CREDENTIALS } from '../fixtures/test-data'
import { mockLoginResponse } from '../fixtures/auth-helpers'

test.describe('Login Gherkin Alignment', { tag: '@integration' }, () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('Login is case-insensitive for email', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // Mock successful login for uppercase email
    await mockLoginResponse(page, {
      body: {
        accessToken: 'abc',
        tokenType: 'Bearer',
        expiresIn: 3600,
        principalId: 'user-1',
        email: 'TEST@EXAMPLE.COM',
        username: 'test',
        emailStatus: 'VERIFIED',
      },
    })

    await loginPage.fillEmail('TEST@EXAMPLE.COM')
    await loginPage.fillPassword(VALID_CREDENTIALS.password)
    await loginPage.submit()

    await expect(page).toHaveURL(APP_URL.dashboard)
  })

  test('Error banner clears on successful re-submission', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    // First attempt: fail
    await page.route(
      '**/api/auth/login',
      async (route) => {
        await route.fulfill({ status: 401, body: JSON.stringify({ detail: 'Invalid' }) })
      },
      { times: 1 },
    )

    await loginPage.login('wrong@example.com', 'wrong')
    await loginPage.expectErrorVisible()

    // Second attempt: success
    await mockLoginResponse(page)
    await loginPage.login(VALID_CREDENTIALS.email, VALID_CREDENTIALS.password)

    await expect(page).toHaveURL(APP_URL.dashboard)
    await expect(loginPage.errorBanner).toHaveCount(0)
  })

  test('Error banner clears on navigation away', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.login)

    await page.route('**/api/auth/login', async (route) => {
      await route.fulfill({ status: 401, body: JSON.stringify({ detail: 'Invalid' }) })
    })

    await loginPage.login('wrong@example.com', 'wrong')
    await loginPage.expectErrorVisible()

    await loginPage.alternateLink.click()
    await expect(page).toHaveURL(APP_URL.register)
    await expect(loginPage.errorBanner).toHaveCount(0)
  })
})
