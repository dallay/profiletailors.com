import { test, expect } from '../fixtures/scheduler-base-test'
import { SchedulerPage } from '../pages/scheduler-page'
import { ComposeModalPage } from '../pages/compose-modal-page'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'

const MOCK_WORKSPACE_ID = 'workspace-001'

const mockThreadsChannels = {
  channels: [
    {
      socialAccountId: 'sa-linkedin-001',
      connectionId: 'conn-001',
      provider: 'LINKEDIN',
      accountKind: 'PERSONAL_PROFILE',
      displayName: 'Dev User',
      status: 'ACTIVE',
      avatarUrl: null,
      connectedAt: '2026-01-15T10:00:00Z',
      lastSyncedAt: '2026-06-18T08:00:00Z',
    },
    {
      socialAccountId: 'sa-threads-001',
      connectionId: 'conn-threads-001',
      provider: 'THREADS',
      accountKind: 'PERSONAL_PROFILE',
      displayName: 'Threads Dev',
      status: 'ACTIVE',
      avatarUrl: null,
      connectedAt: '2026-09-01T10:00:00Z',
      lastSyncedAt: '2026-09-28T08:00:00Z',
    },
  ],
}

const mockThreadsProviders = {
  providers: [
    {
      provider: 'linkedin',
      accountKinds: ['PERSONAL_PROFILE'],
      channelLimit: null,
      connectedChannelCount: 1,
      canConnectMore: true,
      state: 'AVAILABLE',
      reason: null,
    },
    {
      provider: 'threads',
      accountKinds: ['PERSONAL_PROFILE'],
      channelLimit: null,
      connectedChannelCount: 1,
      canConnectMore: false,
      state: 'AVAILABLE',
      reason: null,
    },
  ],
}

function json(body: unknown, status = 200) {
  return {
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  }
}

async function registerThreadsMocks(
  context: import('@playwright/test').BrowserContext,
): Promise<void> {
  await context.route('**/api/publishing/channels/providers', (route) => {
    route.fulfill(json(mockThreadsProviders))
  })
  await context.route('**/api/publishing/channels', (route) => {
    if (route.request().method() === 'GET') {
      route.fulfill(json(mockThreadsChannels))
      return
    }
    route.fallback()
  })
  await context.route('**/api/publishing/threads/connections/initiate', (route) => {
    route.fulfill(
      json({
        authorizationUrl: 'https://www.threads.net/oauth/authorize?mock=true',
        state: 'mock-threads-state',
        expiresAt: new Date(Date.now() + 600_000).toISOString(),
      }),
    )
  })
  await context.route('**/api/publishing/threads/connections/complete', (route) => {
    route.fulfill(
      json({
        connectionId: 'conn-threads-001',
        workspaceId: MOCK_WORKSPACE_ID,
        provider: 'THREADS',
        status: 'ACTIVE',
        account: {
          accountId: 'sa-threads-001',
          providerAccountId: 'threads-profile-1',
          displayName: 'Threads Dev',
          kind: 'PERSONAL_PROFILE',
        },
      }),
    )
  })
}

test.describe('Scheduler — Threads provider @threads', () => {
  test.beforeEach(async ({ page, context }) => {
    await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })
    await registerThreadsMocks(context)
  })

  test('TH-01: Threads OAuth callback completes and lands on settings @integration @connect', async ({
    page,
  }) => {
    await page.goto('/integrations/threads/callback?code=mock-code&state=mock-threads-state')

    await expect(page).toHaveURL(/\/settings\?.*connected=threads/, { timeout: 15_000 })
    await expect(page).toHaveURL(/panel=channels/)
  })

  test('TH-02: denied Threads callback shows a Threads error @integration @connect', async ({
    page,
  }) => {
    await page.goto('/integrations/threads/callback?error=access_denied')

    await expect(
      page.getByText('Threads connection was cancelled before permissions were granted.'),
    ).toBeVisible({ timeout: 10_000 })
  })

  test('TH-03: Threads channel is selectable and a scheduled post is queued @integration @scheduler', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()

    await scheduler.clickNewPost()
    await composeModal.expectVisible()

    const threadsChip = page.getByTestId('channel-button').filter({ hasText: 'Threads Dev' })
    await expect(threadsChip).toBeVisible({ timeout: 10_000 })
    await threadsChip.first().click()

    let postedAccountId: unknown = null
    await page.route('**/api/publishing/publications', (route) => {
      if (route.request().method() === 'POST') {
        postedAccountId = route.request().postDataJSON()?.socialAccountId ?? null
        route.fulfill(
          json(
            {
              publicationId: `backend-threads-${Date.now()}`,
              workspaceId: MOCK_WORKSPACE_ID,
              socialAccountId: postedAccountId,
              status: 'QUEUED',
              scheduleMode: 'NOW',
              priority: false,
              title: 'Post from App',
              bodyText: route.request().postDataJSON()?.bodyText ?? null,
              assetIds: [],
              scheduledFor: null,
              nextSlotAfter: null,
            },
            201,
          ),
        )
        return
      }
      route.fallback()
    })

    const testText = `Threads E2E post ${Date.now()}`
    await composeModal.switchToNow()
    await composeModal.fillText(testText)
    await composeModal.clickScheduleNow()

    await composeModal.expectHidden().catch(async () => {
      await composeModal.clickCancel()
      await composeModal.expectHidden()
    })

    expect(postedAccountId).toBe('sa-threads-001')

    await scheduler.switchToList()
    await expect(page.getByText(testText).first()).toBeVisible({ timeout: 10_000 })
  })
})
