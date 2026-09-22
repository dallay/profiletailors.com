import { expect, setupSupportSession, test } from '../fixtures/base-test'
import { registerAdminMocks } from '../fixtures/admin-mocks'

test.describe('protected admin navigation (mocked lane)', () => {
  test('redirects unauthenticated visitors to login with the requested path', async ({ page }) => {
    await registerAdminMocks(page)
    await page.goto('/waitlist')

    await expect(page).toHaveURL(/\/login\?redirect=\/waitlist/)
    await expect(page.getByRole('textbox', { name: 'Email' })).toBeVisible()
  })

  test('denies a support operator access to invitation administration', async ({ page }) => {
    await setupSupportSession(page)
    await page.goto('/direct-invitations')

    await expect(page).toHaveURL(/\/access-denied$/)
    await expect(page.getByRole('heading', { name: /access denied/i })).toBeVisible()
  })
})
