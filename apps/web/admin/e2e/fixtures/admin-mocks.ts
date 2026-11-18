import type { Page } from '@playwright/test'

import {
  OWNER_PRINCIPAL,
  SUPPORT_PRINCIPAL,
  makeEntry,
  toListItem,
  toSummary,
  type BulkResultEntry,
  type MockWaitlistEntry,
  type WaitlistEntryStatus,
} from './test-data'

export interface AdminMockOptions {
  roles?: string[]
  entries?: MockWaitlistEntry[]
}

export interface AdminMockState {
  entries: MockWaitlistEntry[]
  bulkCalls: string[][]
  authorizationRequests: Array<{ path: string; returnTo: string }>
  logoutRequests: Array<{ csrfToken: string | null }>
  unsafeRequests: Array<{ path: string; csrfToken: string | null }>
}

function jsonResponse(status: number, body: unknown) {
  return {
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  }
}

export function defaultEntries(): MockWaitlistEntry[] {
  const pending = [makeEntry('ava'), makeEntry('ben'), makeEntry('cid')]
  const invited = makeEntry('dan', 'INVITED')
  const converted = makeEntry('eli', 'CONVERTED')
  return [...pending, invited, converted]
}

export async function registerAdminMocks(
  page: Page,
  options: AdminMockOptions = {},
): Promise<AdminMockState> {
  const roles = options.roles ?? [...OWNER_PRINCIPAL.platformRoles]
  const state: AdminMockState = {
    entries: options.entries ?? defaultEntries(),
    bulkCalls: [],
    authorizationRequests: [],
    logoutRequests: [],
    unsafeRequests: [],
  }

  let sessionStarted = false
  const principalEmail = roles.includes('PLATFORM_OWNER')
    ? OWNER_PRINCIPAL.email
    : SUPPORT_PRINCIPAL.email

  await page.route('**/oauth2/authorization/pt-admin**', async (route) => {
    const url = new URL(route.request().url())
    const returnTo = url.searchParams.get('returnTo') ?? '/'
    state.authorizationRequests.push({ path: url.pathname, returnTo })
    sessionStarted = true
    await route.fulfill({
      status: 302,
      headers: { location: new URL(returnTo, page.url()).toString() },
    })
  })

  await page.route('**/api/admin/session', (route) => {
    if (!sessionStarted) {
      return route.fulfill(jsonResponse(401, { title: 'Unauthorized', status: 401 }))
    }
    return route.fulfill(
      jsonResponse(200, {
        principalId: 'e398cc5c-a565-5b7e-bea6-e52050ba0094',
        email: principalEmail,
        displayName: 'E2E Operator',
        platformRoles: roles,
      }),
    )
  })

  await page.route('**/api/auth/csrf', (route) =>
    route.fulfill(jsonResponse(200, { token: 'e2e-csrf-token' })),
  )

  await page.route('**/api/auth/logout', async (route) => {
    const csrfToken = route.request().headers()['x-csrf-token'] ?? null
    state.logoutRequests.push({ csrfToken })
    sessionStarted = false
    await route.fulfill({ status: 204 })
  })

  await page.route('**/api/admin/waitlist-entries/summary', (route) =>
    route.fulfill(jsonResponse(200, toSummary(state.entries))),
  )

  await page.route('**/api/admin/waitlist-entries/invitations:bulk', (route) => {
    if (route.request().method() !== 'POST') return route.fallback()
    state.unsafeRequests.push({
      path: new URL(route.request().url()).pathname,
      csrfToken: route.request().headers()['x-csrf-token'] ?? null,
    })
    const body = (route.request().postDataJSON() ?? {}) as { entryIds?: string[] }
    const entryIds = body.entryIds ?? []
    state.bulkCalls.push(entryIds)
    if (entryIds.length === 0) {
      return route.fulfill(jsonResponse(400, { code: 'EMPTY_SELECTION' }))
    }
    if (entryIds.length > 50) {
      return route.fulfill(jsonResponse(400, { code: 'OVER_LIMIT' }))
    }
    const results: BulkResultEntry[] = entryIds.map((entryId) => inviteOne(state, entryId))
    const summary = {
      requested: results.length,
      invited: results.filter((r) => r.outcome === 'invited').length,
      skipped: results.filter((r) => r.outcome === 'skipped').length,
      failed: results.filter((r) => r.outcome === 'failed').length,
    }
    return route.fulfill(jsonResponse(200, { results, summary }))
  })

  await page.route('**/api/admin/waitlist-entries**', (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/summary') || url.pathname.endsWith(':bulk')) {
      return route.fallback()
    }
    const statusFilter = url.searchParams.get('status')
    const emailFilter = (url.searchParams.get('email') ?? '').toLowerCase()
    let items = state.entries
    if (statusFilter) {
      items = items.filter((entry) => entry.status === (statusFilter as WaitlistEntryStatus))
    }
    if (emailFilter) {
      items = items.filter((entry) => entry.email.toLowerCase().includes(emailFilter))
    }
    return route.fulfill(
      jsonResponse(200, {
        items: items.map(toListItem),
        page: 0,
        size: 25,
        totalElements: items.length,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      }),
    )
  })

  return state
}

function inviteOne(state: AdminMockState, entryId: string): BulkResultEntry {
  const entry = state.entries.find((candidate) => candidate.id === entryId)
  if (!entry) {
    return { entryId, outcome: 'failed', code: 'ENTRY_NOT_FOUND' }
  }
  if (entry.status === 'INVITED') {
    return { entryId, outcome: 'skipped', code: 'ALREADY_INVITED' }
  }
  if (entry.status === 'CONVERTED') {
    return { entryId, outcome: 'failed', code: 'ENTRY_ALREADY_CONVERTED' }
  }
  if (entry.status !== 'PENDING') {
    return { entryId, outcome: 'failed', code: 'ENTRY_NOT_INVITABLE' }
  }
  entry.status = 'INVITED'
  entry.invitedAt = new Date().toISOString()
  return { entryId, outcome: 'invited', invitationId: crypto.randomUUID() }
}
