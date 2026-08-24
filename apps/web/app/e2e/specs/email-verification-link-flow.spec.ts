/**
 * spec: openspec/specs/email-verification-ui/spec.md
 * section: Verification Landing Route + Session Bootstrap After Verification
 *
 * Covers opening the emailed verify-email link, submitting the token to the
 * mocked verification API, showing a verified outcome, and bootstrapping the
 * authenticated session/profile state from the backend follow-up calls.
 */

import { test, expect } from '../fixtures/base-test'
import { APP_URL } from '../fixtures/test-data'
import {
  mockCurrentWorkspace,
  mockRefreshResponse,
  mockUserProfile,
  mockVerifyEmailResponse,
  resetSession,
} from '../fixtures/auth-helpers'

const VERIFIED_EMAIL = 'verified@example.com'
const VERIFIED_USERNAME = 'verified-user'
const VERIFIED_WORKSPACE_ID = 'workspace-verified'

function verificationUrl(token: string) {
  return `${APP_URL.verifyEmail}?token=${token}`
}

test.describe('Email Verification Link Flow', { tag: '@integration' }, () => {
  test.beforeEach(async ({ page }) => {
    await resetSession(page)
  })

  test('4.2.1 Emailed verification link submits token and shows a verified outcome', async ({
    page,
  }) => {
    await mockVerifyEmailResponse(page)
    await mockUserProfile(page, {
      principalId: 'verified-user',
      email: VERIFIED_EMAIL,
      username: VERIFIED_USERNAME,
      displayIdentity: 'Verified User',
      emailStatus: 'VERIFIED',
      workspaceId: VERIFIED_WORKSPACE_ID,
      workspaceName: 'Verified Workspace',
    })
    await mockCurrentWorkspace(page, {
      workspaceId: VERIFIED_WORKSPACE_ID,
      workspaceName: 'Verified Workspace',
    })

    const verifyRequestPromise = page.waitForRequest(
      (request) => request.url().includes('/api/auth/verify-email') && request.method() === 'POST',
    )
    const meResponsePromise = page.waitForResponse(
      (response) => response.url().includes('/api/auth/me') && response.status() === 200,
    )

    await page.goto(verificationUrl('token-123'), { waitUntil: 'domcontentloaded' })

    const verifyRequest = await verifyRequestPromise
    expect(verifyRequest.postDataJSON()).toEqual({ token: 'token-123' })
    expect(verifyRequest.headers()['content-type']).toContain('application/json')
    expect(verifyRequest.headers().accept).toContain('application/vnd.api.v1+json')

    await meResponsePromise

    await expect(page.getByText('Email verified')).toBeVisible()
    await expect(page.getByText(/your email has been verified/i)).toBeVisible()
    await expect(page.getByRole('link', { name: /go to dashboard/i })).toHaveAttribute('href', '/')
  })

  test('4.2.2 Verification success bootstraps the session and unlocks protected navigation', async ({
    page,
  }) => {
    await mockVerifyEmailResponse(page, {
      body: {
        accessToken: 'verified-token',
        tokenType: 'Bearer',
        expiresIn: 3600,
        principalId: 'verified-user',
        email: VERIFIED_EMAIL,
        username: VERIFIED_USERNAME,
        emailStatus: 'VERIFIED',
        workspaceId: VERIFIED_WORKSPACE_ID,
      },
    })
    await mockUserProfile(page, {
      principalId: 'verified-user',
      email: VERIFIED_EMAIL,
      username: VERIFIED_USERNAME,
      displayIdentity: 'Verified User',
      emailStatus: 'VERIFIED',
      workspaceId: VERIFIED_WORKSPACE_ID,
      workspaceName: 'Verified Workspace',
    })
    await mockCurrentWorkspace(page, {
      workspaceId: VERIFIED_WORKSPACE_ID,
      workspaceName: 'Verified Workspace',
    })

    await mockRefreshResponse(page, {
      accessToken: 'verified-token',
      email: VERIFIED_EMAIL,
      username: VERIFIED_USERNAME,
      emailStatus: 'VERIFIED',
    })

    await page.goto(verificationUrl('bootstrap-token'), { waitUntil: 'domcontentloaded' })

    await expect(page.getByRole('link', { name: /go to dashboard/i })).toBeVisible()
    await page.getByRole('link', { name: /go to dashboard/i }).click()

    await expect(page).toHaveURL(APP_URL.dashboard)
    await expect(page.getByRole('heading', { name: /welcome back, verified user/i })).toBeVisible()

    await page.getByRole('button', { name: /toggle sidebar/i }).click()
    await expect(
      page.getByRole('button', { name: /verified user verified@example.com/i }),
    ).toBeVisible()
  })
})
