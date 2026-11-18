import { expect, test } from '../fixtures/base-test'
import { registerAdminMocks } from '../fixtures/admin-mocks'

test.describe('admin BFF authentication (mocked lane)', () => {
  test('uses the admin authorization flow and preserves a safe return path without storing credentials', async ({
    page,
  }) => {
    const { authorizationRequests } = await registerAdminMocks(page)
    await page.goto('/login?redirect=%2Fwaitlist')

    await expect(page.getByTestId('admin-login-button')).toBeVisible()
    await expect(page.getByLabel(/email|password/i)).toHaveCount(0)
    await page.getByTestId('admin-login-button').click()

    await expect(page).toHaveURL(/\/waitlist$/)
    expect(authorizationRequests).toEqual([
      { path: '/oauth2/authorization/pt-admin', returnTo: '/waitlist' },
    ])
    expect(
      await page.evaluate(() => ({
        localStorage: Object.keys(localStorage),
        sessionStorage: Object.keys(sessionStorage),
      })),
    ).toEqual({ localStorage: [], sessionStorage: [] })
  })

  test('replaces an unsafe return path with the admin root', async ({ page }) => {
    const { authorizationRequests } = await registerAdminMocks(page)
    await page.goto('/login?redirect=%2F%2Fevil.example%2Fsteal')
    await page.getByTestId('admin-login-button').click()

    expect(authorizationRequests).toEqual([
      { path: '/oauth2/authorization/pt-admin', returnTo: '/' },
    ])
  })

  test('sends a CSRF token on logout and an unsafe admin request', async ({ page }) => {
    const state = await registerAdminMocks(page)
    await page.goto('/login')
    await page.getByTestId('admin-login-button').click()
    await expect(page).toHaveURL(/\/$/)
    await page.goto('/waitlist')
    await expect(
      page.getByRole('main').getByRole('heading', { name: /waitlist/i, level: 1 }),
    ).toBeVisible()

    await page.getByTestId('bulk-select-all').check()
    await page.getByTestId('bulk-invite').click()
    await expect(page.getByTestId('bulk-results')).toContainText('3 invited')
    expect(state.unsafeRequests).toHaveLength(1)
    expect(state.unsafeRequests[0]?.csrfToken).toBe('e2e-csrf-token')

    await page.getByRole('button', { name: /sign out/i }).click()
    await expect(page).toHaveURL(/\/login$/)
    expect(state.logoutRequests).toEqual([{ csrfToken: 'e2e-csrf-token' }])
    expect(
      await page.evaluate(() => ({
        localStorage: Object.keys(localStorage),
        sessionStorage: Object.keys(sessionStorage),
      })),
    ).toEqual({ localStorage: [], sessionStorage: [] })
  })
})
