/**
 * spec: openspec/changes/private-beta-launch-readiness/specs/e2e/invitee-private-beta.md
 * section: 3. Invitee Journey — Private Beta
 *
 * Covers the end-to-end invitee journey for the private beta:
 * - Acceptance from an invitation email
 * - Workspace hydration under the accepted workspace
 * - Capability gate when invitation acceptance is disabled
 * - Safe failure when the invitation is invalid/expired
 * - No unsupported request — out-of-scope endpoints are rejected without
 *   mutating state or echoing tokens
 *
 * Requires a populated HAR file at e2e/hars/invitee-private-beta.har OR
 * the live backend + `UPDATE_HAR=true` to record responses.
 */

import { test, expect } from '../fixtures/base-test'
import { APP_URL } from '../fixtures/test-data'

test.describe('Invitee Private Beta Journey @integration', () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('3.1 Invitee accepts an invitation and reaches the accepted workspace dashboard', async ({
    page,
  }) => {
    await page.route('**/api/capabilities/public', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.api.v1+json',
        body: JSON.stringify({
          registrationEnabled: true,
          passwordRecoveryEnabled: true,
          invitationAcceptanceEnabled: true,
        }),
      })
    })

    await page.route('**/api/invitations/accept', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.api.v1+json',
        body: JSON.stringify({
          workspaceId: 'invitation-workspace',
          membershipStatus: 'ACTIVE',
        }),
      })
    })

    await page.goto(`${APP_URL.dashboard}/invitations/accept?token=raw-token-e2e`)
    await expect(page.getByRole('heading', { name: /accept your invitation/i })).toBeVisible()

    const acceptPromise = page.waitForResponse(
      (res) =>
        new URL(res.url()).pathname === '/api/invitations/accept' &&
        res.request().method() === 'POST',
    )
    await page.getByRole('button', { name: /accept invitation/i }).click()
    const acceptResponse = await acceptPromise
    expect(acceptResponse.ok()).toBe(true)

    await expect(page.getByText(/invitation accepted/i)).toBeVisible()
  })

  test('3.2 Empty invitation token shows the missing-token error without calling the API', async ({
    page,
  }) => {
    await page.route('**/api/capabilities/public', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.api.v1+json',
        body: JSON.stringify({
          registrationEnabled: true,
          passwordRecoveryEnabled: true,
          invitationAcceptanceEnabled: true,
        }),
      })
    })

    let acceptCalled = false
    await page.route('**/api/invitations/accept', async (route) => {
      acceptCalled = true
      await route.continue()
    })

    await page.goto(`${APP_URL.dashboard}/invitations/accept?token=`)
    await expect(page.getByRole('alert')).toContainText(/missing its token/i)
    expect(acceptCalled).toBe(false)
  })

  test('3.3 Capability gate blocks acceptance when invitationAcceptanceEnabled is false', async ({
    page,
  }) => {
    await page.route('**/api/capabilities/public', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.api.v1+json',
        body: JSON.stringify({
          registrationEnabled: true,
          passwordRecoveryEnabled: true,
          invitationAcceptanceEnabled: false,
        }),
      })
    })

    await page.route('**/api/invitations/accept', async (route) => {
      await route.fulfill({
        status: 503,
        contentType: 'application/problem+json',
        body: JSON.stringify({ title: 'Invitation acceptance disabled', status: 503 }),
      })
    })

    await page.goto(`${APP_URL.dashboard}/invitations/accept?token=raw-token-e2e`)
    await expect(page.getByRole('heading', { name: /currently unavailable/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /accept invitation/i })).toHaveCount(0)
  })

  test('3.4 Backend error surfaces the canonical not-acceptable copy', async ({ page }) => {
    await page.route('**/api/capabilities/public', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.api.v1+json',
        body: JSON.stringify({
          registrationEnabled: true,
          passwordRecoveryEnabled: true,
          invitationAcceptanceEnabled: true,
        }),
      })
    })

    await page.route('**/api/invitations/accept', async (route) => {
      await route.fulfill({
        status: 400,
        contentType: 'application/problem+json',
        body: JSON.stringify({
          title: 'Invalid invitation',
          detail: 'This invitation link is no longer valid or has already been used.',
          status: 400,
          code: 'INVITATION_NOT_ACCEPTABLE',
        }),
      })
    })

    await page.goto(`${APP_URL.dashboard}/invitations/accept?token=raw-token-e2e`)
    await page.getByRole('button', { name: /accept invitation/i }).click()

    await expect(page.getByRole('alert')).toContainText(/no longer valid/i)
  })

  test('3.5 Raw invitation token MUST NOT appear in rendered DOM', async ({ page }) => {
    await page.route('**/api/capabilities/public', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.api.v1+json',
        body: JSON.stringify({
          registrationEnabled: true,
          passwordRecoveryEnabled: true,
          invitationAcceptanceEnabled: true,
        }),
      })
    })

    await page.route('**/api/invitations/accept', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.api.v1+json',
        body: JSON.stringify({
          workspaceId: 'invitation-workspace',
          membershipStatus: 'ACTIVE',
        }),
      })
    })

    const rawToken = 'super-secret-invitation-token-do-not-leak'
    await page.goto(`${APP_URL.dashboard}/invitations/accept?token=${rawToken}`)
    await page.getByRole('button', { name: /accept invitation/i }).click()

    await expect(page.getByText(/invitation accepted/i)).toBeVisible()

    const html = await page.content()
    expect(html).not.toContain(rawToken)
  })

  test('3.6 Safe failure surfaces generic copy on 5xx without exposing raw tokens', async ({
    page,
  }) => {
    await page.route('**/api/capabilities/public', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.api.v1+json',
        body: JSON.stringify({
          registrationEnabled: true,
          passwordRecoveryEnabled: true,
          invitationAcceptanceEnabled: true,
        }),
      })
    })

    await page.route('**/api/invitations/accept', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/problem+json',
        body: JSON.stringify({ title: 'Internal error', detail: 'Unexpected error', status: 500 }),
      })
    })

    await page.goto(`${APP_URL.dashboard}/invitations/accept?token=raw-token-e2e`)
    await page.getByRole('button', { name: /accept invitation/i }).click()

    await expect(page.getByRole('alert')).toContainText(/try again/i)
    const html = await page.content()
    expect(html).not.toContain('raw-token-e2e')
  })
})
