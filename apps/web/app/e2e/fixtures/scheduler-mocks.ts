/**
 * Scheduler-specific API mocks for E2E tests.
 *
 * These mocks intercept scheduler API calls (publications, channels,
 * workspaces) so tests can run without the Spring Boot backend.
 *
 * Auth endpoints are already handled by auth-flow.har via routeFromHAR.
 * These mocks handle the publishing/workspace layer on top.
 *
 * ## How it works
 *
 * - Mocks are registered via context.route() which takes priority over
 *   routeFromHAR (registered later in base-test.ts) — BUT since
 *   routeFromHAR is registered first and these are registered second,
 *   these mocks win for matching patterns.
 * - The publishing store has a local-first fallback: even if a mock
 *   returns an error, posts are created in local state. This means
 *   most UI tests work regardless of mock accuracy.
 * - For tests that verify API response shapes, the mocks return
 *   realistic data matching the backend contract.
 *
 * @see apps/web/app/src/stores/publishing.ts for the store that consumes these APIs
 * @see apps/web/app/src/lib/auth-api.ts for the request infrastructure
 */

import type { BrowserContext } from '@playwright/test'

// ---------------------------------------------------------------------------
// Vue / Pinia internal types — used only for the evaluate() injection below.
// We model exactly the property chain we access so no `any` is needed.
// ---------------------------------------------------------------------------

interface PiniaStateValue {
  publishing?: {
    publications?: Array<Record<string, unknown>>
  }
}

interface PiniaInstance {
  state: { value: PiniaStateValue }
}

interface VueAppConfig {
  globalProperties: {
    $pinia?: PiniaInstance
  }
}

interface VueApp {
  config: VueAppConfig
}

interface VueAppElement extends HTMLElement {
  __vue_app__?: VueApp
}

// ---------------------------------------------------------------------------
// State — mutable so tests can add publications dynamically
// ---------------------------------------------------------------------------

interface MockPublication {
  id: string
  workspaceId: string
  socialAccountId: string
  provider: string
  status: string
  scheduleMode: string
  priority: boolean
  title: string | null
  bodyText: string | null
  scheduledFor: string | null
  hasConflict: boolean
  conflictingPublicationIds: string[]
  externalPublicationId?: string | null
  publicUrl?: string | null
  publishedAt?: string | null
}

let publications: MockPublication[] = []

// ---------------------------------------------------------------------------
// Mock data — realistic shapes matching the backend contract
// ---------------------------------------------------------------------------

const MOCK_WORKSPACE_ID = 'workspace-001'
const MOCK_SOCIAL_ACCOUNT_ID = 'sa-linkedin-001'

const mockChannels = {
  channels: [
    {
      socialAccountId: MOCK_SOCIAL_ACCOUNT_ID,
      connectionId: 'conn-001',
      provider: 'linkedin',
      accountKind: 'PERSONAL',
      displayName: 'Dev User',
      status: 'ACTIVE',
      avatarUrl: null,
      connectedAt: '2026-01-15T10:00:00Z',
      lastSyncedAt: '2026-06-18T08:00:00Z',
    },
  ],
}

const mockProviders = {
  providers: [
    { name: 'linkedin', configured: true },
    { name: 'twitter', configured: false },
    { name: 'facebook', configured: false },
  ],
}

const mockWorkspaces = [
  {
    workspaceId: MOCK_WORKSPACE_ID,
    name: 'Dev Workspace',
    role: 'OWNER',
    icon: null,
  },
]

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function json(body: unknown, status = 200) {
  return {
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  }
}

function calendarResponse(): object {
  return {
    publications,
    conflicts: [],
    activity: [],
  }
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Register all scheduler-specific mock routes on a browser context.
 *
 * Call this AFTER routeFromHAR so these mocks take priority for
 * scheduler-specific endpoints. Auth endpoints continue to be
 * served by the HAR.
 */
export async function registerSchedulerMocks(context: BrowserContext): Promise<void> {
  // --- Publications calendar (scheduler page load) ---
  // Register the broad publications pattern FIRST, then override with specific ones.
  await context.route('**/api/publishing/publications**', (route) => {
    const method = route.request().method()

    if (method === 'POST') {
      const body = route.request().postDataJSON()
      const pub: MockPublication = {
        id: `pub-${Date.now()}`,
        workspaceId: MOCK_WORKSPACE_ID,
        socialAccountId: body?.socialAccountId ?? MOCK_SOCIAL_ACCOUNT_ID,
        provider: 'linkedin',
        status: 'QUEUED',
        scheduleMode: body?.scheduleMode ?? 'NOW',
        priority: body?.priority ?? false,
        title: body?.title ?? null,
        bodyText: body?.bodyText ?? null,
        scheduledFor: body?.scheduledFor ?? new Date().toISOString(),
        hasConflict: false,
        conflictingPublicationIds: [],
      }
      publications.unshift(pub)
      route.fulfill(json(pub, 201))
      return
    }

    // GET — list all publications
    route.fulfill(json({ publications }))
  })

  // --- Calendar endpoint — overrides the broad publications pattern ---
  await context.route('**/api/publishing/publications/calendar**', (route) => {
    route.fulfill(json(calendarResponse()))
  })

  // --- Quick create — overrides the broad publications pattern ---
  await context.route('**/api/publishing/publications/quick-create**', (route) => {
    const body = route.request().postDataJSON()
    const pub: MockPublication = {
      id: `pub-${Date.now()}`,
      workspaceId: MOCK_WORKSPACE_ID,
      socialAccountId: body?.socialAccountId ?? MOCK_SOCIAL_ACCOUNT_ID,
      provider: 'linkedin',
      status: 'QUEUED',
      scheduleMode: 'NOW',
      priority: body?.priority ?? false,
      title: body?.title ?? 'Quick post',
      bodyText: body?.bodyText ?? null,
      scheduledFor: body?.scheduledFor ?? new Date().toISOString(),
      hasConflict: false,
      conflictingPublicationIds: [],
    }
    publications.unshift(pub)
    route.fulfill(json(pub, 201))
  })

  // --- Reschedule — overrides the broad publications pattern ---
  await context.route('**/api/publishing/publications/*/reschedule**', (route) => {
    const body = route.request().postDataJSON()
    const url = route.request().url()
    const id = url.split('/publications/')[1]?.split('/')[0]
    const pub = publications.find((p) => p.id === id)
    if (pub) {
      pub.scheduledFor = body?.scheduledFor ?? pub.scheduledFor
      pub.scheduleMode = body?.scheduleMode ?? pub.scheduleMode
    }
    route.fulfill(json({ success: true }))
  })

  // --- Connected channels (settings page + post creation guard) ---
  // Register the broad pattern FIRST, then override with specific ones.
  // Playwright uses the LAST matching route, so specific patterns must come last.
  await context.route('**/api/publishing/channels**', (route) => {
    route.fulfill(json(mockChannels))
  })

  // --- Channel providers (settings page) — overrides the broad channels pattern ---
  await context.route('**/api/publishing/channels/providers**', (route) => {
    route.fulfill(json(mockProviders))
  })

  // --- SSE events endpoint — override to prevent hanging on stream reads ---
  await context.route('**/api/publishing/channels/events**', (route) => {
    // Return an empty SSE stream so consumeSseStream terminates gracefully
    route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: 'event: done\ndata: {}\n\n',
    })
  })

  // --- LinkedIn connections (settings page) ---
  await context.route('**/api/publishing/linkedin/connections/initiate**', (route) => {
    route.fulfill(
      json({
        authorizationUrl: 'https://www.linkedin.com/oauth/v2/authorization?mock=true',
        state: 'mock-state',
        expiresAt: new Date(Date.now() + 600_000).toISOString(),
      }),
    )
  })

  await context.route('**/api/publishing/linkedin/connections/complete**', (route) => {
    route.fulfill(
      json({
        connectionId: 'conn-001',
        workspaceId: MOCK_WORKSPACE_ID,
        provider: 'linkedin',
        status: 'ACTIVE',
        account: {
          accountId: MOCK_SOCIAL_ACCOUNT_ID,
          providerAccountId: 'li-12345',
          displayName: 'Dev User',
          kind: 'PERSONAL',
          profileUrn: 'urn:li:person:12345',
        },
      }),
    )
  })

  // --- Workspaces (broad pattern first, specific ones last) ---
  await context.route('**/api/tenancy/workspaces**', (route) => {
    route.fulfill(json(mockWorkspaces))
  })

  await context.route('**/api/tenancy/workspaces/current/name**', (route) => {
    route.fulfill(json({ workspaceId: MOCK_WORKSPACE_ID, name: 'Dev Workspace' }))
  })

  await context.route('**/api/tenancy/workspaces/current/icon**', (route) => {
    route.fulfill(json({ workspaceId: MOCK_WORKSPACE_ID, icon: null }))
  })
}

/**
 * Reset mock state between tests.
 */
export function resetSchedulerMocks(): void {
  publications = []
}

/**
 * Injects the mock LinkedIn channel directly into the live Pinia publishing
 * store so the compose modal's submit button becomes enabled immediately.
 *
 * Call this after `authenticateAs + scheduler.goto()` when tests need to
 * open the compose modal and create a post via the UI.
 */
export async function ensureChannelsLoaded(page: import('@playwright/test').Page): Promise<void> {
  await page.evaluate(() => {
    const channel = {
      id: 'sa-linkedin-001',
      accountId: 'sa-linkedin-001',
      name: 'Dev User',
      provider: 'linkedin',
      avatar: '',
      handle: 'Dev User',
      status: 'ACTIVE',
    }
    // biome-ignore lint/suspicious/noExplicitAny: Vue internals access
    const app = (document.querySelector('#app') as any)?.__vue_app__
    const pinia = app?.config?.globalProperties?.$pinia
    if (pinia?.state?.value?.publishing) {
      const channels = pinia.state.value.publishing.channels
      // biome-ignore lint/suspicious/noExplicitAny: dynamic channel type from Pinia
      if (!channels.some((c: any) => c.id === channel.id)) {
        channels.push(channel)
      }
    }
  })
}

/**
 * Creates a publication directly in the Pinia publishing store, bypassing the
 * UI and backend. Useful in interaction tests that just need a post to exist
 * in the list without testing the creation flow.
 *
 * Writes to localStorage (key: `pt_publications`) and then updates the live
 * Pinia state so the change is immediately visible without a page reload.
 */
export async function createPublicationInStore(
  page: import('@playwright/test').Page,
  text: string,
  options?: {
    title?: string | null
  },
): Promise<void> {
  const timestamp = Date.now()
  const title = options?.title === undefined ? 'E2E Test Post' : options.title

  const calendarPublication: MockPublication = {
    id: `pub-e2e-${timestamp}`,
    workspaceId: MOCK_WORKSPACE_ID,
    socialAccountId: MOCK_SOCIAL_ACCOUNT_ID,
    provider: 'linkedin',
    status: 'QUEUED',
    scheduleMode: 'NOW',
    priority: false,
    title,
    bodyText: text,
    scheduledFor: new Date().toISOString(),
    hasConflict: false,
    conflictingPublicationIds: [],
  }

  const frontendPublication = {
    id: calendarPublication.id,
    content: text,
    title: calendarPublication.title ?? undefined,
    channels: ['linkedin'],
    scheduledAt: calendarPublication.scheduledFor ?? new Date().toISOString(),
    status: 'QUEUED',
    priority: false,
    accountId: MOCK_SOCIAL_ACCOUNT_ID,
  }

  // Keep the route-backed mock source of truth in sync so an in-flight
  // fetchCalendar() call cannot overwrite the injected post.
  publications.unshift(calendarPublication)

  await page.evaluate((p) => {
    // 1. Persist to localStorage so the store has it on next boot
    try {
      const stored = JSON.parse(localStorage.getItem('pt_publications') || '[]')
      stored.unshift(p)
      localStorage.setItem('pt_publications', JSON.stringify(stored))
    } catch {}

    // 2. Inject into the live Pinia state so it's visible right now
    const app = (document.querySelector('#app') as VueAppElement)?.__vue_app__
    const pinia = app?.config?.globalProperties?.$pinia
    if (pinia?.state?.value?.publishing?.publications) {
      pinia.state.value.publishing.publications.unshift(p)
    }
  }, frontendPublication)
}
