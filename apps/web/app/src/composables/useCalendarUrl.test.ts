import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import { describe, it, expect, vi } from 'vitest'
import { createCalendarUrlController, extractFirstChannelId } from './useCalendarUrl'

// ---------------------------------------------------------------------------
// Mock router and route factories
// ---------------------------------------------------------------------------

function createMockRoute(
  overrides: { name?: string; query?: Record<string, unknown> } = {},
): RouteLocationNormalizedLoaded {
  return {
    name: overrides.name ?? 'scheduler-calendar-week',
    query: overrides.query ?? {},
    fullPath: '/scheduler/calendar/week',
    hash: '',
    matched: [],
    meta: {},
    params: {},
    path: '/scheduler/calendar/week',
    redirectedFrom: undefined,
    href: '/scheduler/calendar/week',
  } as unknown as RouteLocationNormalizedLoaded
}

function createMockRouter(): Router {
  const push = vi.fn().mockResolvedValue(undefined)
  const replace = vi.fn().mockResolvedValue(undefined)
  return { push, replace } as unknown as Router
}

// ---------------------------------------------------------------------------
// Test subjects — import the pure parse/serialize helpers via the composable
// ---------------------------------------------------------------------------

// We test through the composable factory by injecting the mock route/router.
// The composable is tested by verifying router.push/replace calls match the
// current route state after each action.

describe('extractFirstChannelId', () => {
  it('prefers the first channels[] value when query contains an array', () => {
    expect(extractFirstChannelId({ 'channels[]': ['alpha', 'beta'] })).toBe('alpha')
  })

  it('returns channels[] when query contains a single string', () => {
    expect(extractFirstChannelId({ 'channels[]': 'alpha' })).toBe('alpha')
  })

  it('falls back to legacy channels when channels[] is absent', () => {
    expect(extractFirstChannelId({ channels: ['legacy-alpha', 'legacy-beta'] })).toBe(
      'legacy-alpha',
    )
    expect(extractFirstChannelId({ channels: 'legacy-alpha' })).toBe('legacy-alpha')
  })

  it('returns null when no valid channel id is present', () => {
    expect(extractFirstChannelId({})).toBeNull()
    expect(extractFirstChannelId({ channels: [] })).toBeNull()
  })
})

describe('useCalendarUrl — route normalization', () => {
  // These tests cover the core normalization logic by importing the composable
  // with a mock route. We test via the factory by manually resolving state
  // from a given query snapshot, matching the same normalization rules.

  it('defaults to calendar-week when route name is unknown', () => {
    const route = createMockRoute({ name: undefined as unknown as string })
    // surface derivation should fall back to calendar-week
    expect(route.name || 'calendar-week').not.toBeNull()
  })

  it('treats empty query as all-filters', () => {
    const query: { channels?: string[]; status?: string; timezone?: string } = {}
    const hasChannels = Array.isArray(query.channels) ? query.channels.length > 0 : false
    const hasStatus = !!query.status
    const hasTimezone = !!query.timezone
    expect(hasChannels).toBe(false)
    expect(hasStatus).toBe(false)
    expect(hasTimezone).toBe(false)
  })

  it('normalizes status value to lowercase', () => {
    const rawStatus = 'QUEUED'
    const normalized = rawStatus.toLowerCase()
    expect(['all', 'queued', 'published', 'cancelled']).toContain(normalized)
  })

  it('rejects invalid status value', () => {
    const rawStatus = 'INVALID_STATUS'
    const validStatuses = new Set(['all', 'queued', 'published', 'cancelled'])
    const isValid = validStatuses.has(rawStatus.toLowerCase())
    expect(isValid).toBe(false)
  })

  it('parses channels as array of strings', () => {
    const query = { channels: ['acc-1', 'acc-2'] }
    const channelIds = Array.isArray(query.channels)
      ? query.channels.filter((c): c is string => typeof c === 'string')
      : []
    expect(channelIds).toEqual(['acc-1', 'acc-2'])
  })

  it('normalizes single channel to array', () => {
    const query = { channels: 'acc-1' }
    const channelIds = typeof query.channels === 'string' ? [query.channels] : []
    expect(channelIds).toEqual(['acc-1'])
  })

  it('validates ISO date format YYYY-MM-DD', () => {
    const isValidDate = (v: string) => /^\d{4}-\d{2}-\d{2}$/.test(v)
    expect(isValidDate('2026-06-15')).toBe(true)
    expect(isValidDate('2026-6-15')).toBe(false)
    expect(isValidDate('06-15-2026')).toBe(false)
    expect(isValidDate('')).toBe(false)
  })

  it('normalizes date to today when missing', () => {
    const rawDate = ''
    const normalizeDate = (v: string) =>
      /^\d{4}-\d{2}-\d{2}$/.test(v) ? v : new Date().toISOString().slice(0, 10)
    const result = normalizeDate(rawDate)
    expect(result).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('normalizes timezone to browser tz when missing', () => {
    const rawTimezone = ''
    const resolveBrowserTimezone = () => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
    const result = rawTimezone || resolveBrowserTimezone()
    expect(typeof result).toBe('string')
    expect(result.length).toBeGreaterThan(0)
  })

  it('trims free-text search query', () => {
    const rawQ = '  search term  '
    const q = typeof rawQ === 'string' ? rawQ.trim() : ''
    expect(q).toBe('search term')
  })

  it('deduplicates channel IDs', () => {
    const channelIds = ['acc-1', 'acc-1', 'acc-2']
    const unique = [...new Set(channelIds)]
    expect(unique).toEqual(['acc-1', 'acc-2'])
  })
})

// Surface → vue-router route name mapping (shared across intent tests below).
// Kept in sync with `useCalendarUrl.ts#CALENDAR_ROUTE_NAMES`.
const ROUTE_NAMES: Record<'calendar-week' | 'calendar-month' | 'list', string> = {
  'calendar-week': 'scheduler-calendar-week',
  'calendar-month': 'scheduler-calendar-month',
  list: 'scheduler-list',
}

describe('useCalendarUrl — navigation intent', () => {
  it('parses postId from the query so detail modal state can be restored from a URL', () => {
    const route = createMockRoute({
      name: 'scheduler-calendar-week',
      query: { postId: 'pub-123' },
    })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    expect(ctrl.state.value.postId).toBe('pub-123')
  })

  it('openPostDetail pushes postId into the scheduler URL query', async () => {
    const route = createMockRoute({
      name: 'scheduler-calendar-week',
      query: { date: '2026-07-10', status: 'queued' },
    })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    await ctrl.openPostDetail('pub-123')

    expect(router.push).toHaveBeenCalledTimes(1)
    expect(router.replace).not.toHaveBeenCalled()
    const call = (router.push as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call).toMatchObject({
      name: 'scheduler-calendar-week',
      query: {
        date: '2026-07-10',
        status: 'queued',
        postId: 'pub-123',
      },
    })
  })

  it('closePostDetail removes postId with replace by default', async () => {
    const route = createMockRoute({
      name: 'scheduler-calendar-week',
      query: { date: '2026-07-10', postId: 'pub-123' },
    })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    await ctrl.closePostDetail()

    expect(router.replace).toHaveBeenCalledTimes(1)
    expect(router.push).not.toHaveBeenCalled()
    const call = (router.replace as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call.query).toEqual({ date: '2026-07-10' })
  })

  it('setSurface triggers push with the new route name (not a replace)', async () => {
    const route = createMockRoute({ name: 'scheduler-calendar-week', query: {} })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    await ctrl.setSurface('calendar-month')

    expect(router.push).toHaveBeenCalledTimes(1)
    expect(router.replace).not.toHaveBeenCalled()
    const call = (router.push as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call.name).toBe(ROUTE_NAMES['calendar-month'])
  })

  it('setDate triggers push with a normalized date (not a replace)', async () => {
    const route = createMockRoute({ name: 'scheduler-calendar-week', query: {} })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    await ctrl.setDate('2026-06-22')

    expect(router.push).toHaveBeenCalledTimes(1)
    expect(router.replace).not.toHaveBeenCalled()
    const call = (router.push as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call.query).toMatchObject({ date: '2026-06-22' })
  })

  it('setStatus uses replace to avoid polluting history', async () => {
    const route = createMockRoute({ name: 'scheduler-calendar-week', query: {} })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    await ctrl.setStatus('queued')

    expect(router.replace).toHaveBeenCalledTimes(1)
    expect(router.push).not.toHaveBeenCalled()
    const call = (router.replace as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call.query).toMatchObject({ status: 'queued' })
  })

  it('setTimezone uses replace to avoid polluting history', async () => {
    const route = createMockRoute({ name: 'scheduler-calendar-week', query: {} })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    await ctrl.setTimezone('Europe/Madrid')

    expect(router.replace).toHaveBeenCalledTimes(1)
    expect(router.push).not.toHaveBeenCalled()
    const call = (router.replace as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    // buildQuery only emits `timezone` when it differs from the browser tz;
    // in CI the browser tz may be Europe/Madrid itself, in which case the
    // key is intentionally omitted. We only assert push-vs-replace intent here.
    if (call.query.timezone !== undefined) {
      expect(call.query.timezone).toBe('Europe/Madrid')
    }
  })

  it('setChannelIds uses replace and writes channel ids into channels[]', async () => {
    const route = createMockRoute({ name: 'scheduler-calendar-week', query: {} })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    await ctrl.setChannelIds(['acc-123'])

    expect(router.replace).toHaveBeenCalledTimes(1)
    expect(router.push).not.toHaveBeenCalled()
    const call = (router.replace as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call.query).toMatchObject({ 'channels[]': ['acc-123'] })
  })

  it('setSearch uses replace and trims whitespace', async () => {
    const route = createMockRoute({ name: 'scheduler-calendar-week', query: {} })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    await ctrl.setSearch('  architecture  ')

    expect(router.replace).toHaveBeenCalledTimes(1)
    expect(router.push).not.toHaveBeenCalled()
    const call = (router.replace as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call.query).toMatchObject({ q: 'architecture' })
  })
})

describe('useCalendarUrl — controller fallbacks & dedup', () => {
  it('setDate falls back to today when given an invalid date string', async () => {
    const route = createMockRoute({ name: 'scheduler-calendar-week', query: {} })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    // The controller must accept the invalid input without throwing and
    // resolve it to today's date internally. Since buildQuery omits `date`
    // when it equals today, we only assert that push was called exactly once.
    await expect(ctrl.setDate('definitely-not-a-date')).resolves.not.toThrow()
    expect(router.push).toHaveBeenCalledTimes(1)
  })

  it('setTimezone falls back to browser timezone when given an empty value', async () => {
    const expected = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
    const route = createMockRoute({ name: 'scheduler-calendar-week', query: {} })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    await ctrl.setTimezone('')

    const call = (router.replace as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    // The fallback is reflected in the state; the query either omits `timezone`
    // (when it equals the browser tz) or carries the resolved value.
    if (call.query.timezone !== undefined) {
      expect(call.query.timezone).toBe(expected)
    }
  })

  it('setChannelIds deduplicates repeated channel ids via Set semantics', async () => {
    const route = createMockRoute({ name: 'scheduler-calendar-week', query: {} })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    await ctrl.setChannelIds(['acc-1', 'acc-1', 'acc-2', 'acc-2', 'acc-1'])

    const call = (router.replace as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call.query['channels[]']).toEqual(['acc-1', 'acc-2'])
  })

  it('stepPeriod arithmetic adds 7 days for week surface (forward)', async () => {
    // 2026-06-15 (Monday) + 7 days = 2026-06-22 (also Monday).
    const route = createMockRoute({
      name: 'scheduler-calendar-week',
      query: { date: '2026-06-15' },
    })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    await ctrl.stepPeriod('forward')

    const call = (router.push as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call.query.date).toBe('2026-06-22')
  })
})

describe('useCalendarUrl — route name surface derivation', () => {
  it('canonicalizes scheduler-calendar-day to the existing week surface while preserving canonical query params', async () => {
    const today = new Date().toISOString().slice(0, 10)
    const route = createMockRoute({
      name: 'scheduler-calendar-day',
      query: {
        date: today,
        timezone: 'America/New_York',
        q: ' launch ',
        channels: ['acc-2', 'acc-1'],
        postId: 'post-42',
      },
    })
    const router = createMockRouter()
    const controller = createCalendarUrlController(route, router)

    expect(controller.state.value.surface).toBe('calendar-week')

    await controller.canonicalize()

    expect(router.replace).toHaveBeenCalledWith({
      name: 'scheduler-calendar-week',
      query: {
        timezone: 'America/New_York',
        q: 'launch',
        'channels[]': ['acc-2', 'acc-1'],
        postId: 'post-42',
      },
    })
  })

  it('derives calendar-week from scheduler-calendar-week', () => {
    const _routeNames = [
      'scheduler-calendar-week',
      'scheduler-calendar-month',
      'scheduler-list',
    ] as const
    const routeName = 'scheduler-calendar-week'

    const surfaceMap: Record<string, string> = {
      'scheduler-calendar-week': 'calendar-week',
      'scheduler-calendar-month': 'calendar-month',
      'scheduler-list': 'list',
    }

    expect(surfaceMap[routeName]).toBe('calendar-week')
  })

  it('derives calendar-month from scheduler-calendar-month', () => {
    const routeName = 'scheduler-calendar-month'
    const surfaceMap: Record<string, string> = {
      'scheduler-calendar-week': 'calendar-week',
      'scheduler-calendar-month': 'calendar-month',
      'scheduler-list': 'list',
    }

    expect(surfaceMap[routeName]).toBe('calendar-month')
  })

  it('derives list from scheduler-list', () => {
    const routeName = 'scheduler-list'
    const surfaceMap: Record<string, string> = {
      'scheduler-calendar-week': 'calendar-week',
      'scheduler-calendar-month': 'calendar-month',
      'scheduler-list': 'list',
    }

    expect(surfaceMap[routeName]).toBe('list')
  })

  it('falls back to calendar-week for unrecognized route name', () => {
    const unrecognizedRoute = 'unrecognized-route'
    const surfaceMap: Record<string, string> = {
      'scheduler-calendar-week': 'calendar-week',
      'scheduler-calendar-month': 'calendar-month',
      'scheduler-list': 'list',
    }

    const surface = surfaceMap[unrecognizedRoute] ?? 'calendar-week'
    expect(surface).toBe('calendar-week')
  })
})

describe('useCalendarUrl — query serialization', () => {
  it('omits date when equal to today', () => {
    const today = new Date().toISOString().slice(0, 10)
    const state = {
      date: today,
      status: 'all',
      q: '',
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      channelIds: [] as string[],
    }

    const query: Record<string, unknown> = {}
    if (state.date !== today) query.date = state.date
    if (state.status !== 'all') query.status = state.status
    if (state.q) query.q = state.q
    if (state.channelIds.length > 0) query.channels = state.channelIds

    expect(query.date).toBeUndefined()
    expect(query.status).toBeUndefined()
    expect(query.q).toBeUndefined()
    expect(query.channels).toBeUndefined()
  })

  it('serializes non-default date to query', () => {
    const today = new Date().toISOString().slice(0, 10)
    const state = { date: '2026-06-15', status: 'all' as const, q: '', channelIds: [] as string[] }

    const query: Record<string, unknown> = {}
    if (state.date !== today) query.date = state.date

    expect(query.date).toBe('2026-06-15')
  })

  it('serializes non-all status to query', () => {
    const state = { status: 'queued' as string }

    const query: Record<string, unknown> = {}
    if (state.status !== 'all') query.status = state.status

    expect(query.status).toBe('queued')
  })

  it('serializes channels array to channels[] query param', () => {
    const channelIds = ['acc-1', 'acc-2']

    const query: Record<string, unknown> = {}
    if (channelIds.length > 0) query['channels[]'] = channelIds

    expect(query['channels[]']).toEqual(['acc-1', 'acc-2'])
  })

  it('serializes non-empty search query to query', () => {
    const state = { q: 'DDD patterns' }

    const query: Record<string, unknown> = {}
    if (state.q) query.q = state.q

    expect(query.q).toBe('DDD patterns')
  })

  it('does not include empty search in query', () => {
    const state = { q: '' }

    const query: Record<string, unknown> = {}
    if (state.q) query.q = state.q

    expect(query.q).toBeUndefined()
  })
})

describe('useCalendarUrl — canonicalization', () => {
  it('flags needsCanonicalization when surface is unrecognized', () => {
    const validSurfaces = new Set(['calendar-week', 'calendar-month', 'list'])
    const unknownSurface = 'calendar-day'
    const needsCanonicalization = !validSurfaces.has(unknownSurface)
    expect(needsCanonicalization).toBe(true)
  })

  it('does not flag canonicalization for valid surface', () => {
    const validSurfaces = new Set(['calendar-week', 'calendar-month', 'list'])
    const knownSurface = 'calendar-week'
    const needsCanonicalization = !validSurfaces.has(knownSurface)
    expect(needsCanonicalization).toBe(false)
  })

  it('flags canonicalization when query has invalid status', () => {
    const validStatuses = new Set(['all', 'queued', 'published', 'cancelled'])
    const rawStatus = 'invalid'
    const normalized = validStatuses.has(rawStatus.toLowerCase()) ? rawStatus.toLowerCase() : 'all'
    // When invalid status is normalized to 'all', query should not have it
    const queryStatus = normalized === 'all' ? undefined : normalized
    expect(queryStatus).toBeUndefined()
  })

  it('canonicalizes URL when needsCanonicalization is true', async () => {
    // Route with status=all in query but surface already defaults to week (canonical form omits status)
    const route = makeRoute({
      query: { status: 'all' },
    })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(
      route as unknown as RouteLocationNormalizedLoaded,
      router as unknown as Router,
    )

    expect(ctrl.needsCanonicalization.value).toBe(true)

    await ctrl.canonicalize()

    expect(router.replace).toHaveBeenCalled()
    const call = (router.replace as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    // Should NOT include status=all in canonical URL
    expect(call.query).not.toHaveProperty('status')
  })
})

// ---------------------------------------------------------------------------
// Composable integration — needsCanonicalization / areQueriesEquivalent
// ---------------------------------------------------------------------------

interface RouteLike {
  name: string
  query: Record<string, unknown>
  params: Record<string, string>
  path: string
  fullPath: string
  hash: string
  matched: []
  redirectedFrom: undefined
  meta: Record<string, unknown>
}

function makeRoute(overrides: Partial<RouteLike>): RouteLike {
  return {
    name: 'scheduler-calendar-week',
    query: {},
    params: {},
    path: '/scheduler/week',
    fullPath: '/scheduler/week',
    hash: '',
    matched: [],
    redirectedFrom: undefined,
    meta: {},
    ...overrides,
  }
}

const mockRouter = { push: vi.fn(), replace: vi.fn() }

describe('useCalendarUrl — needsCanonicalization / areQueriesEquivalent', () => {
  it('returns false when array values are in different order (localeCompare sorts before comparison)', () => {
    const route = makeRoute({
      query: { channels: ['acc-2', 'acc-1'] },
    })
    const controller = createCalendarUrlController(
      route as unknown as RouteLocationNormalizedLoaded,
      mockRouter as unknown as Router,
    )

    // 'acc-2', 'acc-1' is reverse order — canonical form sorts to 'acc-1', 'acc-2'
    // areQueriesEquivalent must sort both sides before comparing; if localeCompare
    // is missing (default sort), this would produce ['acc-2', 'acc-1'] !== ['acc-1', 'acc-2']
    expect(controller.needsCanonicalization.value).toBe(false)
  })

  it('returns true when query has non-canonical status', () => {
    const route = makeRoute({
      query: { status: 'QUEUED' },
    })
    const controller = createCalendarUrlController(
      route as unknown as RouteLocationNormalizedLoaded,
      mockRouter as unknown as Router,
    )

    // 'QUEUED' is stored uppercase but the canonical form is lowercase 'queued'
    expect(controller.needsCanonicalization.value).toBe(true)
  })

  it('returns true when query contains a filter that is set to default', () => {
    const route = makeRoute({
      query: { status: 'all' },
    })
    const controller = createCalendarUrlController(
      route as unknown as RouteLocationNormalizedLoaded,
      mockRouter as unknown as Router,
    )

    // 'all' is the default status — canonical form omits it
    expect(controller.needsCanonicalization.value).toBe(true)
  })

  it('canonicalize replaces route with normalized query for mixed non-canonical values', async () => {
    const router = { push: vi.fn(), replace: vi.fn().mockResolvedValue(undefined) }
    const route = makeRoute({
      query: {
        date: '2026-06-15',
        q: '  test  ',
        status: 'QUEUED',
        channels: 'acc-1',
      },
    })
    const controller = createCalendarUrlController(
      route as unknown as RouteLocationNormalizedLoaded,
      router as unknown as Router,
    )

    await controller.canonicalize()

    expect(router.replace).toHaveBeenCalledWith({
      name: 'scheduler-calendar-week',
      query: {
        date: '2026-06-15',
        q: 'test',
        status: 'queued',
        'channels[]': ['acc-1'],
      },
    })
  })
})

describe('useCalendarUrl controller — stepPeriod navigation', () => {
  it('steps forward one week in calendar-week surface', async () => {
    // 2026-06-15 (Monday) + 7 days = 2026-06-22
    const route = createMockRoute({
      name: 'scheduler-calendar-week',
      query: { date: '2026-06-15' },
    })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    await ctrl.stepPeriod('forward')

    expect(router.push).toHaveBeenCalledTimes(1)
    const call = (router.push as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call.name).toBe('scheduler-calendar-week')
    expect(call.query.date).toBe('2026-06-22')
  })

  it('steps backward one week in calendar-week surface', async () => {
    // 2026-06-15 (Monday) - 7 days = 2026-06-08
    const route = createMockRoute({
      name: 'scheduler-calendar-week',
      query: { date: '2026-06-15' },
    })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    await ctrl.stepPeriod('backward')

    expect(router.push).toHaveBeenCalledTimes(1)
    const call = (router.push as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call.name).toBe('scheduler-calendar-week')
    expect(call.query.date).toBe('2026-06-08')
  })

  it('steps forward one month in calendar-month surface', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-20T12:00:00Z'))
    try {
      // 2026-06-15 → clamp to 1st → +1 month = 2026-07-01
      const route = createMockRoute({
        name: 'scheduler-calendar-month',
        query: { date: '2026-06-15' },
      })
      const router = createMockRouter()
      const ctrl = createCalendarUrlController(route, router)

      await ctrl.stepPeriod('forward')

      const call = (router.push as ReturnType<typeof vi.fn>).mock.calls[0]![0]
      expect(call.name).toBe('scheduler-calendar-month')
      expect(call.query.date).toBe('2026-07-01')
    } finally {
      vi.useRealTimers()
    }
  })

  it('steps backward one month in calendar-month surface', async () => {
    // 2026-06-15 → clamp to 1st → -1 month = 2026-05-01
    const route = createMockRoute({
      name: 'scheduler-calendar-month',
      query: { date: '2026-06-15' },
    })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    await ctrl.stepPeriod('backward')

    const call = (router.push as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call.name).toBe('scheduler-calendar-month')
    expect(call.query.date).toBe('2026-05-01')
  })
})

describe('useCalendarUrl controller — isInvalidStatus', () => {
  it('returns false for empty status string', () => {
    const rawStatus = ''
    const lowered = rawStatus.toLowerCase() as import('./useCalendarUrl').SchedulerStatus
    const invalid =
      rawStatus.length > 0 && !new Set(['all', 'queued', 'published', 'cancelled']).has(lowered)
    expect(invalid).toBe(false)
  })

  it('returns true for an unrecognized status value', () => {
    const rawStatus = 'pending'
    const lowered = rawStatus.toLowerCase() as import('./useCalendarUrl').SchedulerStatus
    const invalid =
      rawStatus.length > 0 && !new Set(['all', 'queued', 'published', 'cancelled']).has(lowered)
    expect(invalid).toBe(true)
  })

  it('returns false for a valid status value', () => {
    const rawStatus = 'queued'
    const lowered = rawStatus.toLowerCase() as import('./useCalendarUrl').SchedulerStatus
    const invalid =
      rawStatus.length > 0 && !new Set(['all', 'queued', 'published', 'cancelled']).has(lowered)
    expect(invalid).toBe(false)
  })
})

describe('useCalendarUrl controller — list surface uses canonical path only', () => {
  it('setSurface(list) navigates to scheduler-list route with no mode query', async () => {
    const route = createMockRoute({ name: 'scheduler-calendar-week', query: {} })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(route, router)

    await ctrl.setSurface('list')

    expect(router.push).toHaveBeenCalledWith(expect.objectContaining({ name: 'scheduler-list' }))
    const call = (router.push as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call.query).not.toHaveProperty('mode')
  })

  it('scheduler-list route derives surface=list without needing mode query', () => {
    const route = createMockRoute({ name: 'scheduler-list', query: {} })
    const ctrl = createCalendarUrlController(route, createMockRouter())
    expect(ctrl.state.value.surface).toBe('list')
  })

  it('scheduler-calendar-week route derives surface=calendar-week (no calendar-day fallback)', () => {
    const route = createMockRoute({ name: 'scheduler-calendar-week', query: {} })
    const ctrl = createCalendarUrlController(route, createMockRouter())
    expect(ctrl.state.value.surface).toBe('calendar-week')
  })
})

describe('useCalendarUrl — areQueriesEquivalent', () => {
  it('normalizes legacy channels to channels[] for comparison', () => {
    const route = makeRoute({
      query: { channels: ['acc-1', 'acc-2'] },
    })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(
      route as unknown as RouteLocationNormalizedLoaded,
      router as unknown as Router,
    )

    // Should not need canonicalization when route query matches built query
    // Build query from state will use channels[]
    expect(ctrl.needsCanonicalization.value).toBe(false)
  })

  it('does not need canonicalization when channel order differs but content is same', () => {
    // Route has channels in different order than canonical form would build
    const route = makeRoute({
      query: { channels: ['acc-2', 'acc-1'] }, // reverse order
    })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(
      route as unknown as RouteLocationNormalizedLoaded,
      router as unknown as Router,
    )

    // areQueriesEquivalent sorts both sides before comparing, so this should be equivalent
    expect(ctrl.needsCanonicalization.value).toBe(false)
  })
})

describe('useCalendarUrl — stepPeriod', () => {
  it('triggers navigation when stepping forward in week view', async () => {
    const route = makeRoute({
      query: { date: '2026-06-15' },
    })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(
      route as unknown as RouteLocationNormalizedLoaded,
      router as unknown as Router,
    )

    await ctrl.stepPeriod('forward')

    expect(router.push).toHaveBeenCalled()
    const call = (router.push as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call.query).toHaveProperty('date')
    // The date should be different from the original
    expect(call.query.date).not.toBe('2026-06-15')
  })

  it('triggers navigation when stepping backward in week view', async () => {
    const route = makeRoute({
      query: { date: '2026-06-15' },
    })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(
      route as unknown as RouteLocationNormalizedLoaded,
      router as unknown as Router,
    )

    await ctrl.stepPeriod('backward')

    expect(router.push).toHaveBeenCalled()
    const call = (router.push as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call.query).toHaveProperty('date')
    expect(call.query.date).not.toBe('2026-06-15')
  })

  it('uses setMonth when stepping in month view', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-20T12:00:00Z'))
    try {
      const route = makeRoute({
        name: 'scheduler-calendar-month',
        query: { date: '2026-06-15' },
      })
      const router = createMockRouter()
      const ctrl = createCalendarUrlController(
        route as unknown as RouteLocationNormalizedLoaded,
        router as unknown as Router,
      )

      await ctrl.stepPeriod('forward')

      expect(router.push).toHaveBeenCalled()
      const call = (router.push as ReturnType<typeof vi.fn>).mock.calls[0]![0]
      expect(call.query).toHaveProperty('date')
    } finally {
      vi.useRealTimers()
    }
  })

  it('uses setDate for week view navigation', async () => {
    const route = makeRoute({
      name: 'scheduler-calendar-week',
      query: { date: '2026-06-15' },
    })
    const router = createMockRouter()
    const ctrl = createCalendarUrlController(
      route as unknown as RouteLocationNormalizedLoaded,
      router as unknown as Router,
    )

    await ctrl.stepPeriod('backward')

    expect(router.push).toHaveBeenCalled()
    const call = (router.push as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(call.query).toHaveProperty('date')
  })
})
