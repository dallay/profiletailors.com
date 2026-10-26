import { test, expect } from '../fixtures/scheduler-base-test'
import { SchedulerPage } from '../pages/scheduler-page'
import { ComposeModalPage } from '../pages/compose-modal-page'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'

const MOCK_WORKSPACE_ID = '6325c45c-5fdd-5be5-84c2-54f866dfd70e'

const mockThreadsChannels = {
  channels: [
    {
      socialAccountId: 'fa6f01a4-0c74-553d-a3b4-49ab3bed24ed',
      connectionId: 'a92b9ead-a1a3-5801-9fbc-5d5c7370edd7',
      provider: 'LINKEDIN',
      accountKind: 'PERSONAL_PROFILE',
      displayName: 'Dev User',
      status: 'ACTIVE',
      avatarUrl: null,
      connectedAt: '2026-01-15T10:00:00Z',
      lastSyncedAt: '2026-06-18T08:00:00Z',
    },
    {
      socialAccountId: 'e14fa4cc-01f5-5682-b2de-17d9e36c1250',
      connectionId: '82e18914-68aa-5935-8512-6f53dff1f938',
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
        connectionId: '82e18914-68aa-5935-8512-6f53dff1f938',
        workspaceId: MOCK_WORKSPACE_ID,
        provider: 'THREADS',
        status: 'ACTIVE',
        account: {
          accountId: 'e14fa4cc-01f5-5682-b2de-17d9e36c1250',
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

  test('TH-03: all channels are selected by default and a post is queued per channel @integration @scheduler', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()

    await scheduler.clickNewPost()
    await composeModal.expectVisible()

    const threadsChip = page.getByRole('button', { name: /Threads Dev/i, pressed: true })
    await expect(threadsChip).toBeVisible({ timeout: 10_000 })
    const linkedInChip = page.getByRole('button', { name: /Dev User/i, pressed: true })
    await expect(linkedInChip).toBeVisible({ timeout: 10_000 })
    await expect(threadsChip.first()).toHaveAttribute('aria-pressed', 'true')
    await expect(linkedInChip.first()).toHaveAttribute('aria-pressed', 'true')

    const postedAccountIds: unknown[] = []
    await page.route('**/api/publishing/publications/batch', async (route) => {
      if (route.request().method() !== 'POST') {
        await route.fallback()
        return
      }
      const body = route.request().postDataJSON()
      const targets = Array.isArray(body?.targets) ? body.targets : []
      postedAccountIds.push(
        ...targets.map((target: { socialAccountId: string }) => target.socialAccountId),
      )
      await route.fallback()
    })

    const testText = `Threads E2E post ${Date.now()}`
    await composeModal.switchToNow()
    await composeModal.fillText(testText)
    await composeModal.clickScheduleNow()

    await composeModal.expectHidden()

    expect([...postedAccountIds].sort()).toEqual([
      'e14fa4cc-01f5-5682-b2de-17d9e36c1250',
      'fa6f01a4-0c74-553d-a3b4-49ab3bed24ed',
    ])
    await expect(page.getByText(testText).first()).toBeVisible({ timeout: 10_000 })
    await page.reload()
    await scheduler.expectVisible()
    await expect(page.getByText(testText).first()).toBeVisible({ timeout: 10_000 })
  })

  test('TH-05: per-network override submits distinct bodyText per account @integration @scheduler', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()

    await scheduler.clickNewPost()
    await composeModal.expectVisible()

    const stamp = Date.now()
    const baseText = `Base E2E post ${stamp}`
    const threadsText = `Threads-only E2E post ${stamp}`

    await composeModal.fillText(baseText)
    await page.getByTestId('customize-toggle').click()
    await page.getByTestId('customize-tab').filter({ hasText: 'Threads Dev' }).click()
    await composeModal.fillText(threadsText)

    await expect(page.getByTestId('threads-preview')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByTestId('threads-preview-text')).toContainText(threadsText)

    const payloads: Record<string, unknown> = {}
    await page.route('**/api/publishing/publications**', async (route) => {
      if (route.request().method() === 'POST' && route.request().url().endsWith('/batch')) {
        const body = route.request().postDataJSON()
        const targets = Array.isArray(body?.targets) ? body.targets : []
        for (const target of targets) {
          payloads[target.socialAccountId] = target.bodyText ?? body.bodyText ?? null
        }
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            status: 'ALL_CREATED',
            results: targets.map((target: { socialAccountId: string }, index: number) => ({
              socialAccountId: target.socialAccountId,
              publicationId: `publication-${index}`,
              groupId: 'cb54b7d0-04bc-50bb-aa99-58b40d0caeeb',
              status: 'CREATED',
            })),
          }),
        })
        return
      }
      await route.fallback()
    })

    await composeModal.switchToNow()
    await composeModal.clickScheduleNow()

    await composeModal.expectHidden()

    expect(payloads['fa6f01a4-0c74-553d-a3b4-49ab3bed24ed']).toContain(baseText)
    expect(payloads['e14fa4cc-01f5-5682-b2de-17d9e36c1250']).toContain(threadsText)
  })
})
