import { test, expect, type BrowserContext, type Page } from '@playwright/test'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'

const WORKSPACE_A_ID = 'cc27eb7a-8b06-5f47-8ba9-e269f8a58299'
const WORKSPACE_A_NAME = 'Workspace Alpha'
const WORKSPACE_B_ID = '2126c946-a089-5551-81a4-fa143513959e'
const WORKSPACE_B_NAME = 'Workspace Beta'

const SOCIAL_ACCOUNT_A = '252ef8cb-af75-564a-913d-241e12bbf030'
const SOCIAL_ACCOUNT_B = '186e39ff-57c6-58df-a2ad-c730b06f7c01'

const PUBLICATION_A_ID = '0e19dbd3-dd1f-5a3b-bb52-7ff643ce3310'
const PUBLICATION_B_ID = '4d33c3d3-9fff-5d2d-acf8-23b2d135a8e3'

interface WorkspaceFixture {
  id: string
  name: string
  publicationId: string
  socialAccountId: string
  socialAccountName: string
}

interface MockPublication {
  id: string
  workspaceId: string
  socialAccountId: string
  provider: string
  status: string
  scheduleMode: string
  priority: boolean
  title: string
  bodyText: string
  scheduledFor: string | null
  nextSlotAfter: string | null
  assetIds: string[]
  hasConflict: boolean
  conflictingPublicationIds: string[]
  updatedAt: string
}

function buildSeedPublications(workspace: WorkspaceFixture): MockPublication[] {
  return [
    {
      id: workspace.publicationId,
      workspaceId: workspace.id,
      socialAccountId: workspace.socialAccountId,
      provider: 'linkedin',
      status: 'QUEUED',
      scheduleMode: 'SCHEDULED_AT',
      priority: false,
      title: `${workspace.name} 13:00 post`,
      bodyText: `${workspace.name} post body`,
      scheduledFor: '2026-09-23T13:00:00Z',
      nextSlotAfter: null,
      assetIds: [],
      hasConflict: false,
      conflictingPublicationIds: [],
      updatedAt: '2026-09-22T12:00:00Z',
    },
  ]
}

async function registerWorkspaceMocks(
  context: BrowserContext,
  workspace: WorkspaceFixture,
): Promise<void> {
  let stored: MockPublication[] = buildSeedPublications(workspace)

  await context.route('**/api/tenancy/workspaces', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          workspaceId: workspace.id,
          name: workspace.name,
          role: 'OWNER',
          icon: null,
        },
      ]),
    })
  })

  await context.route('**/api/tenancy/workspaces/current/name', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ workspaceId: workspace.id, name: workspace.name }),
    })
  })

  await context.route('**/api/tenancy/workspaces/current/icon', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ workspaceId: workspace.id, icon: null }),
    })
  })

  await context.route('**/api/publishing/channels', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        channels: [
          {
            socialAccountId: workspace.socialAccountId,
            connectionId: `conn-${workspace.id}`,
            provider: 'linkedin',
            accountKind: 'PERSONAL',
            displayName: workspace.socialAccountName,
            status: 'ACTIVE',
            avatarUrl: null,
            connectedAt: '2026-01-15T10:00:00Z',
            lastSyncedAt: '2026-09-18T08:00:00Z',
          },
        ],
      }),
    })
  })

  await context.route('**/api/publishing/channels/providers', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        providers: [{ name: 'linkedin', configured: true }],
      }),
    })
  })

  await context.route('**/api/publishing/channels/events', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: 'event: done\ndata: {}\n\n',
    })
  })

  await context.route('**/api/publishing/publications/calendar**', (route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        publications: stored,
        conflicts: [],
        activity: [],
      }),
    })
  })

  await context.route(/\/api\/publishing\/publications\/[^/]+\/reschedule/, (route) => {
    const url = route.request().url()
    const id = url.split('/publications/')[1]?.split('/')[0]
    const body = route.request().postDataJSON() as { scheduledFor?: string } | null
    const target = stored.find((p) => p.id === id)
    if (target && body?.scheduledFor) {
      target.scheduledFor = body.scheduledFor
      target.updatedAt = new Date().toISOString()
    }
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true }),
    })
  })

  await context.route(/\/api\/publishing\/publications\/[^/]+$/, (route) => {
    const method = route.request().method()
    if (method === 'DELETE') {
      const url = route.request().url()
      const id = url.split('/publications/')[1]?.split('?')[0]
      stored = stored.filter((p) => p.id !== id)
      route.fulfill({ status: 204, body: '' })
      return
    }
    if (method === 'GET') {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ publications: stored }),
      })
      return
    }
    route.fallback()
  })

  await context.route(/\/api\/publishing\/publications$/, (route) => {
    if (route.request().method() !== 'POST') {
      route.fallback()
      return
    }
    const body = route.request().postDataJSON() as {
      bodyText?: string
      title?: string
      scheduledFor?: string
    } | null
    const newPub: MockPublication = {
      id: crypto.randomUUID(),
      workspaceId: workspace.id,
      socialAccountId: workspace.socialAccountId,
      provider: 'linkedin',
      status: 'QUEUED',
      scheduleMode: 'SCHEDULED_AT',
      priority: false,
      title: body?.title ?? 'Quick post',
      bodyText: body?.bodyText ?? '',
      scheduledFor: body?.scheduledFor ?? '2026-09-24T15:00:00Z',
      nextSlotAfter: null,
      assetIds: [],
      hasConflict: false,
      conflictingPublicationIds: [],
      updatedAt: new Date().toISOString(),
    }
    stored = [newPub, ...stored]
    route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({ ...newPub, publicationId: newPub.id }),
    })
  })
}

async function openScheduler(page: Page): Promise<void> {
  await page.goto('/scheduler/calendar/week?date=2026-09-23', {
    waitUntil: 'domcontentloaded',
  })
  await expect(page.getByRole('heading', { name: /all channels/i }).first()).toBeVisible({
    timeout: 15_000,
  })
}

test.describe('Scheduler — Cross-tab browser sync', () => {
  test('TC-BS-01: same-workspace reschedule in tab A becomes visible in tab B @browser-sync', async ({
    browser,
  }) => {
    const context = await browser.newContext()
    try {
      const workspace: WorkspaceFixture = {
        id: WORKSPACE_A_ID,
        name: WORKSPACE_A_NAME,
        publicationId: PUBLICATION_A_ID,
        socialAccountId: SOCIAL_ACCOUNT_A,
        socialAccountName: 'Alpha User',
      }
      await registerWorkspaceMocks(context, workspace)

      const tabA = await context.newPage()
      const tabB = await context.newPage()
      await mockAuthenticatedSession(tabA, { workspaceId: WORKSPACE_A_ID })
      await mockAuthenticatedSession(tabB, { workspaceId: WORKSPACE_A_ID })
      await openScheduler(tabA)
      await openScheduler(tabB)

      const seedBody = `${WORKSPACE_A_NAME} post body`
      await expect(tabA.locator('[draggable="true"]').filter({ hasText: seedBody })).toHaveCount(1)
      await expect(tabB.locator('[draggable="true"]').filter({ hasText: seedBody })).toHaveCount(1)

      await tabA.evaluate(
        async ({ id, scheduledFor }: { id: string; scheduledFor: string }) => {
          const app = (
            document.querySelector('#app') as unknown as {
              __vue_app__?: {
                config: {
                  globalProperties: {
                    $pinia?: {
                      _s: Map<
                        string,
                        {
                          reschedulePublication: (
                            id: string,
                            scheduledFor: string,
                          ) => Promise<unknown>
                        }
                      >
                    }
                  }
                }
              }
            }
          )?.__vue_app__
          const store = app?.config?.globalProperties?.$pinia?._s?.get('publishing')
          await store?.reschedulePublication(id, scheduledFor)
        },
        { id: PUBLICATION_A_ID, scheduledFor: '2026-09-23T15:00:00Z' },
      )

      await expect
        .poll(
          async () =>
            tabB.evaluate((id: string) => {
              const app = (
                document.querySelector('#app') as unknown as {
                  __vue_app__?: {
                    config: {
                      globalProperties: {
                        $pinia?: {
                          _s: Map<string, { publications: { id: string; scheduledAt: string }[] }>
                        }
                      }
                    }
                  }
                }
              )?.__vue_app__
              const store = app?.config?.globalProperties?.$pinia?._s?.get('publishing')
              return store?.publications.find((item) => item.id === id)?.scheduledAt ?? null
            }, PUBLICATION_A_ID),
          { timeout: 15_000 },
        )
        .toBe('2026-09-23T15:00:00Z')
    } finally {
      await context.close()
    }
  })

  test('TC-BS-02: foreign-workspace invalidation is ignored @browser-sync @isolation', async ({
    browser,
  }) => {
    const contextA = await browser.newContext()
    const contextB = await browser.newContext()
    try {
      const workspaceA: WorkspaceFixture = {
        id: WORKSPACE_A_ID,
        name: WORKSPACE_A_NAME,
        publicationId: PUBLICATION_A_ID,
        socialAccountId: SOCIAL_ACCOUNT_A,
        socialAccountName: 'Alpha User',
      }
      const workspaceB: WorkspaceFixture = {
        id: WORKSPACE_B_ID,
        name: WORKSPACE_B_NAME,
        publicationId: PUBLICATION_B_ID,
        socialAccountId: SOCIAL_ACCOUNT_B,
        socialAccountName: 'Beta User',
      }
      await registerWorkspaceMocks(contextA, workspaceA)
      await registerWorkspaceMocks(contextB, workspaceB)

      const tabA = await contextA.newPage()
      const tabB = await contextB.newPage()
      await mockAuthenticatedSession(tabA, { workspaceId: WORKSPACE_A_ID })
      await mockAuthenticatedSession(tabB, { workspaceId: WORKSPACE_B_ID })
      await openScheduler(tabA)
      await openScheduler(tabB)

      const alphaBody = `${WORKSPACE_A_NAME} post body`
      const betaBody = `${WORKSPACE_B_NAME} post body`

      await expect(tabA.locator('[draggable="true"]').filter({ hasText: alphaBody })).toHaveCount(1)
      await expect(tabB.locator('[draggable="true"]').filter({ hasText: betaBody })).toHaveCount(1)
      await expect(tabA.locator('[draggable="true"]').filter({ hasText: betaBody })).toHaveCount(0)
      await expect(tabB.locator('[draggable="true"]').filter({ hasText: alphaBody })).toHaveCount(0)
    } finally {
      await contextA.close()
      await contextB.close()
    }
  })

  test.skip('TC-BS-03: worker-driven SSE status change refreshes the calendar without reload @browser-sync @sse @needs-backend', async ({
    browser,
  }) => {
    const context = await browser.newContext()
    try {
      const workspace: WorkspaceFixture = {
        id: WORKSPACE_A_ID,
        name: WORKSPACE_A_NAME,
        publicationId: PUBLICATION_A_ID,
        socialAccountId: SOCIAL_ACCOUNT_A,
        socialAccountName: 'Alpha User',
      }
      await registerWorkspaceMocks(context, workspace)

      let calendarRequests = 0
      let eventsRequests = 0
      await context.route('**/api/publishing/publications/calendar**', (route) => {
        calendarRequests += 1
        const seed = buildSeedPublications(workspace)
        const first = seed[0]
        if (calendarRequests >= 2 && first) {
          seed[0] = {
            ...first,
            status: 'PUBLISHED',
            title: `${WORKSPACE_A_NAME} 13:00 post (published)`,
            bodyText: `${WORKSPACE_A_NAME} post body (published)`,
          }
        }
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ publications: seed, conflicts: [], activity: [] }),
        })
      })

      await context.route('**/api/publishing/publications/events**', (route) => {
        eventsRequests += 1
        setTimeout(() => {
          route
            .fulfill({
              status: 200,
              contentType: 'text/event-stream',
              body: `event: publication.status-changed\ndata: ${JSON.stringify({
                workspaceId: WORKSPACE_A_ID,
                publicationId: PUBLICATION_A_ID,
                socialAccountId: SOCIAL_ACCOUNT_A,
                changeType: 'publication.status-changed',
                occurredAt: '2026-09-23T14:00:00.000Z',
              })}\n\n`,
            })
            .catch(() => {})
        }, 1500)
      })

      const tab = await context.newPage()
      await mockAuthenticatedSession(tab, { workspaceId: WORKSPACE_A_ID })
      await openScheduler(tab)

      await expect(tab.locator('[draggable="true"]').first()).toBeVisible({ timeout: 15_000 })
      expect(calendarRequests).toBeGreaterThanOrEqual(1)

      await expect
        .poll(
          async () => tab.locator('[draggable="true"]').filter({ hasText: 'published' }).count(),
          { timeout: 15_000 },
        )
        .toBe(1)
      expect(calendarRequests).toBeGreaterThanOrEqual(2)
      expect(eventsRequests).toBeGreaterThanOrEqual(1)
    } finally {
      await context.close()
    }
  })
})
