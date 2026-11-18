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

import { test, expect } from '../fixtures/scheduler-base-test'
import type { Page, Route } from '@playwright/test'
import { APP_URL } from '../fixtures/test-data'

type InvitationFailure = {
  status: number
  code: string
  detail: string
  email: string
  token: string
  copy: RegExp
}

async function mockInvitationCapabilities(page: Page): Promise<void> {
  await page.route('**/api/capabilities/public', async (route: Route): Promise<void> => {
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
}

async function assertInvitationFailure(page: Page, failure: InvitationFailure): Promise<void> {
  let refreshCalled = false
  await page.route('**/api/auth/refresh', async (route: Route): Promise<void> => {
    refreshCalled = true
    await route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      body: JSON.stringify({ status: 401 }),
    })
  })
  await page.route('**/api/invitations/accept', async (route: Route): Promise<void> => {
    await route.fulfill({
      status: failure.status,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        title: 'Invitation unavailable',
        detail: failure.detail,
        status: failure.status,
        code: failure.code,
      }),
    })
  })

  await page.goto(`${APP_URL.dashboard}invitations/accept?token=${failure.token}`)
  const acceptInvitation = page.getByRole('button', { name: /accept invitation/i })
  await expect(acceptInvitation).toBeVisible()
  await expect(acceptInvitation).toBeEnabled()
  refreshCalled = false
  await acceptInvitation.click()

  await expect(page.getByRole('alert')).toHaveText(failure.copy)
  await expect(page).toHaveURL(new RegExp(`/invitations/accept\\?token=${failure.token}`))
  expect(refreshCalled).toBe(false)

  const html = await page.content()
  expect(html).not.toContain(failure.token)
  expect(html).not.toContain(failure.email)
}

test.describe('Invitee Private Beta Journey @integration', () => {
  test.beforeEach(async ({ page, resetSession }) => {
    await resetSession()
    await page.route('**/api/auth/csrf', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.api.v1+json',
        body: JSON.stringify({ token: 'e2e-csrf-proof', headerName: 'X-CSRF-TOKEN' }),
      })
    })
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
          workspaceId: 'e55f35b8-4442-59cf-9e58-7d06f86e2d25',
          membershipStatus: 'ACTIVE',
        }),
      })
    })

    await page.route('**/api/auth/refresh', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.api.v1+json',
        body: JSON.stringify({
          accessToken: 'invitation-access-token',
          tokenType: 'Bearer',
          expiresIn: 3600,
          principalId: '2f3c1290-243b-521c-ac23-c40164708e96',
          email: 'invitee@example.com',
          username: 'invitee',
          emailStatus: 'VERIFIED',
          workspaceId: 'e55f35b8-4442-59cf-9e58-7d06f86e2d25',
        }),
      })
    })

    await page.route('**/api/auth/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.api.v1+json',
        body: JSON.stringify({
          principalId: '2f3c1290-243b-521c-ac23-c40164708e96',
          email: 'invitee@example.com',
          username: 'invitee',
          displayIdentity: 'invitee',
          emailStatus: 'VERIFIED',
        }),
      })
    })

    await page.goto(`${APP_URL.dashboard}invitations/accept?token=raw-token-e2e`)
    await expect(page.getByRole('heading', { name: /accept your invitation/i })).toBeVisible()

    const acceptPromise = page.waitForResponse(
      (res) =>
        new URL(res.url()).pathname === '/api/invitations/accept' &&
        res.request().method() === 'POST',
    )
    await page.getByRole('button', { name: /accept invitation/i }).click()
    const acceptResponse = await acceptPromise
    expect(acceptResponse.ok()).toBe(true)
    expect(acceptResponse.request().headers()['x-csrf-token']).toBe('e2e-csrf-proof')
    expect(acceptResponse.request().headers().authorization).toBeUndefined()

    await expect(page).toHaveURL(/\/$/)
  })

  test('3.1b Pre-provisioned invitee authenticates through pt-app before accepting', async ({
    page,
  }: {
    page: Page
  }): Promise<void> => {
    await page.route('**/api/capabilities/public', async (route: Route): Promise<void> => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.api.v1+json',
        body: JSON.stringify({
          registrationEnabled: false,
          passwordRecoveryEnabled: true,
          invitationAcceptanceEnabled: true,
        }),
      })
    })

    const token = 'raw-token-schedule'
    const invitationPath = `/invitations/accept?token=${token}`
    let acceptCount = 0
    let acceptedPayload: { token?: string } | undefined
    let acceptedCsrf: string | undefined
    let callbackCompleted = false
    await page.route('**/api/invitations/accept', async (route: Route): Promise<void> => {
      acceptCount += 1
      if (acceptCount === 2) {
        acceptedPayload = route.request().postDataJSON() as { token?: string }
        acceptedCsrf = route.request().headers()['x-csrf-token']
      }
      await route.fulfill(
        acceptCount === 1
          ? {
              status: 401,
              contentType: 'application/problem+json',
              body: JSON.stringify({ code: 'INVITATION_REQUIRES_LOGIN', status: 401 }),
            }
          : {
              status: 200,
              contentType: 'application/vnd.api.v1+json',
              body: JSON.stringify({
                workspaceId: '6325c45c-5fdd-5be5-84c2-54f866dfd70e',
                membershipStatus: 'ACTIVE',
              }),
            },
      )
    })
    await page.route('**/oauth2/authorization/pt-app**', async (route: Route): Promise<void> => {
      const authorizationUrl = new URL(route.request().url())
      expect(authorizationUrl.pathname).toBe('/oauth2/authorization/pt-app')
      expect(authorizationUrl.searchParams.get('returnTo')).toBe(invitationPath)
      callbackCompleted = true
      await route.fulfill({ status: 302, headers: { location: invitationPath } })
    })
    await page.route('**/api/auth/me', async (route: Route): Promise<void> => {
      await route.fulfill(
        callbackCompleted
          ? {
              status: 200,
              contentType: 'application/vnd.api.v1+json',
              body: JSON.stringify({
                principalId: '0e3f9c21-e278-5a6a-accd-7c21f42da6db',
                email: 'invitee-schedule@example.com',
                username: 'invitee-schedule',
                emailStatus: 'VERIFIED',
                workspaceId: '6325c45c-5fdd-5be5-84c2-54f866dfd70e',
              }),
            }
          : {
              status: 401,
              contentType: 'application/problem+json',
              body: JSON.stringify({ status: 401 }),
            },
      )
    })
    await page.goto(`${APP_URL.dashboard}${invitationPath.slice(1)}`)
    await page.getByRole('button', { name: /accept invitation/i }).click()
    await expect(page).toHaveURL(
      new RegExp(`${invitationPath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`),
    )
    await expect(page.getByRole('button', { name: /accept invitation/i })).toBeVisible()
    await page.getByRole('button', { name: /accept invitation/i }).click()
    await expect.poll(() => acceptCount).toBe(2)
    expect(acceptedPayload).toEqual({ token })
    expect(acceptedCsrf).toBe('e2e-csrf-proof')
    await expect(page).toHaveURL(/\/$/)
    const html = await page.content()
    expect(html).not.toContain(token)
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

    await page.goto(`${APP_URL.dashboard}invitations/accept?token=`)
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

    await page.goto(`${APP_URL.dashboard}invitations/accept?token=raw-token-e2e`)
    await expect(page.getByRole('heading', { name: /currently unavailable/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /accept invitation/i })).toHaveCount(0)
  })

  test('3.4 Backend error surfaces the canonical invalid copy', async ({ page }) => {
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
          code: 'INVITATION_INVALID',
        }),
      })
    })

    await page.goto(`${APP_URL.dashboard}invitations/accept?token=raw-token-e2e`)
    await page.getByRole('button', { name: /accept invitation/i }).click()

    await expect(page.getByRole('alert')).toContainText(/no longer valid/i)
  })

  test('3.4b Replay error surfaces the canonical already-used copy', async ({ page }) => {
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
        status: 409,
        contentType: 'application/problem+json',
        body: JSON.stringify({
          title: 'Invitation unavailable',
          detail: 'Invitation is unavailable.',
          status: 409,
          code: 'INVITATION_ALREADY_CONSUMED',
        }),
      })
    })

    await page.goto(`${APP_URL.dashboard}invitations/accept?token=raw-token-replay`)
    await page.getByRole('button', { name: /accept invitation/i }).click()

    await expect(page.getByRole('alert')).toContainText(/already been used/i)
  })

  test('3.4c Expired invitation surfaces safe canonical copy without session activity', async ({
    page,
  }) => {
    await mockInvitationCapabilities(page)
    await assertInvitationFailure(page, {
      status: 410,
      code: 'INVITATION_EXPIRED',
      detail: 'Invitation has expired.',
      email: 'expired-invitee@example.com',
      token: 'raw-token-expired',
      copy: /This invitation link has expired\. Request a new invitation\./i,
    })
  })

  test('3.4d Revoked invitation surfaces safe canonical copy without session activity', async ({
    page,
  }) => {
    await mockInvitationCapabilities(page)
    await assertInvitationFailure(page, {
      status: 410,
      code: 'INVITATION_REVOKED',
      detail: 'Invitation has been revoked.',
      email: 'revoked-invitee@example.com',
      token: 'raw-token-revoked',
      copy: /This invitation link has been revoked\. Request a new invitation\./i,
    })
  })

  test('3.4e Email mismatch surfaces safe canonical copy without session activity', async ({
    page,
  }) => {
    await mockInvitationCapabilities(page)
    await assertInvitationFailure(page, {
      status: 403,
      code: 'INVITATION_EMAIL_MISMATCH',
      detail: 'Invitation email does not match the authenticated identity.',
      email: 'invited-email@example.com',
      token: 'raw-token-mismatch',
      copy: /Sign in with the invited email address to accept this invitation\./i,
    })
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
          workspaceId: 'e55f35b8-4442-59cf-9e58-7d06f86e2d25',
          membershipStatus: 'ACTIVE',
        }),
      })
    })

    const rawToken = 'super-secret-invitation-token-do-not-leak'
    await page.goto(`${APP_URL.dashboard}invitations/accept?token=${rawToken}`)
    await page.getByRole('button', { name: /accept invitation/i }).click()

    await expect(page).toHaveURL(/\/oauth2\/authorization\/pt-app\?returnTo=/)
    expect(new URL(page.url()).searchParams.get('returnTo')).toBe(
      `/invitations/accept?token=${rawToken}`,
    )

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

    await page.route('**/api/auth/refresh', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/problem+json',
        body: JSON.stringify({ status: 401 }),
      })
    })

    await page.goto(`${APP_URL.dashboard}invitations/accept?token=raw-token-e2e`)
    await page.getByRole('button', { name: /accept invitation/i }).click()

    await expect(page.getByRole('alert')).toContainText(/try again/i)
    const html = await page.content()
    expect(html).not.toContain('raw-token-e2e')
  })
})
