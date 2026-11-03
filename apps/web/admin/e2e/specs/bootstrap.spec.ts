import { expect, test } from '../fixtures/base-test'

test.describe('platform bootstrap recipient flow', () => {
  test('clears the fragment before requests and submits current Terms acceptance without age confirmation', async ({
    page,
  }) => {
    let submittedBody: unknown
    let fragmentAtSubmit: string | null = null

    await page.route('**/api/bootstrap/requirements', (route) =>
      route.fulfill({ json: { currentTermsVersion: 'terms-v1.0.0' } }),
    )
    await page.route('**/api/bootstrap/complete', async (route) => {
      submittedBody = route.request().postDataJSON()
      fragmentAtSubmit = new URL(page.url()).hash
      await route.fulfill({ status: 202 })
    })

    await page.goto('/bootstrap#one-use-token')

    await expect(page.getByTestId('bootstrap-terms-version')).toContainText('terms-v1.0.0')
    await expect(page).not.toHaveURL(/#one-use-token/)
    await expect(page.getByRole('button', { name: 'Set Password' })).toBeDisabled()
    await page.getByLabel('Create password').fill('a-strong-password')
    await page.getByLabel('I have reviewed and accept the current Terms of Service.').check()
    await page.getByRole('button', { name: 'Set Password' }).click()

    await expect(page.getByTestId('bootstrap-complete')).toContainText('Check your email')
    expect(fragmentAtSubmit).toBe('')
    expect(submittedBody).toEqual({
      invitationToken: 'one-use-token',
      password: 'a-strong-password',
      acceptedTermsVersion: 'terms-v1.0.0',
    })
    await expect(page.getByTestId('bootstrap-age-confirmation')).toHaveCount(0)
  })
})
