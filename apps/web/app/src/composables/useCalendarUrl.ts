import { computed, type Ref } from 'vue'
import type { LocationQueryRaw, RouteLocationNormalizedLoaded, Router } from 'vue-router'
import { useRoute, useRouter } from 'vue-router'

export type SchedulerSurface = 'calendar-week' | 'calendar-month' | 'list'
export type SchedulerStatus = 'all' | 'queued' | 'published' | 'cancelled'

export interface CalendarUrlState {
  surface: SchedulerSurface
  date: string
  timezone: string
  status: SchedulerStatus
  q: string
  channelIds: string[]
  postId: string | null
}

export interface CalendarUrlController {
  state: Ref<CalendarUrlState>
  needsCanonicalization: Ref<boolean>
  canonicalize: () => Promise<void>
  setSurface: (surface: SchedulerSurface) => Promise<void>
  setDate: (date: string) => Promise<void>
  stepPeriod: (direction: 'forward' | 'backward') => Promise<void>
  setTimezone: (timezone: string) => Promise<void>
  setStatus: (status: SchedulerStatus) => Promise<void>
  setSearch: (q: string) => Promise<void>
  setChannelIds: (channelIds: string[]) => Promise<void>
  openPostDetail: (postId: string) => Promise<void>
  closePostDetail: (options?: { replace?: boolean }) => Promise<void>
}

const VALID_SURFACES = new Set<SchedulerSurface>(['calendar-week', 'calendar-month', 'list'])
const VALID_STATUSES = new Set<SchedulerStatus>(['all', 'queued', 'published', 'cancelled'])

const CALENDAR_ROUTE_NAMES: Record<SchedulerSurface, string> = {
  'calendar-week': 'scheduler-calendar-week',
  'calendar-month': 'scheduler-calendar-month',
  list: 'scheduler-list',
}

function resolveToday(): string {
  return new Date().toISOString().slice(0, 10)
}

function resolveBrowserTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}

const DEFAULT_TIMEZONE = resolveBrowserTimezone()
const DEFAULT_DATE = resolveToday()

function isIsoLocalDate(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value)
}

/**
 * Extracts the first channel ID from a route query object, handling both
 * `channels[]` (array-style) and `channels` (scalar) query param shapes.
 * Returns `null` if no valid channel ID is present.
 */
export function extractFirstChannelId(query: Record<string, unknown>): string | null {
  const raw = query['channels[]'] ?? query.channels
  if (Array.isArray(raw)) return (raw[0] as string) ?? null
  if (typeof raw === 'string') return raw
  return null
}

function toArray(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.filter((entry): entry is string => typeof entry === 'string' && entry.length > 0)
  }
  if (typeof value === 'string' && value.length > 0) {
    return [value]
  }
  return []
}

function trimOrEmpty(value: unknown): string {
  const first = Array.isArray(value) ? value[0] : value
  return typeof first === 'string' ? first.trim() : ''
}

function normalizeSurface(route: { name: unknown }): SchedulerSurface {
  const name = typeof route.name === 'string' ? route.name : ''
  if (name === 'scheduler-calendar-month') return 'calendar-month'
  if (name === 'scheduler-calendar-day') return 'calendar-week'
  if (name === 'scheduler-list') return 'list'
  return 'calendar-week'
}

function normalizeStatus(rawStatus: string): SchedulerStatus {
  const lowered = rawStatus.toLowerCase() as SchedulerStatus
  return VALID_STATUSES.has(lowered) ? lowered : 'all'
}

function isInvalidStatus(rawStatus: string): boolean {
  const normalized = rawStatus.toLowerCase() as SchedulerStatus
  return rawStatus.length > 0 && !VALID_STATUSES.has(normalized)
}

function normalizeDate(rawDate: string): string {
  return isIsoLocalDate(rawDate) ? rawDate : DEFAULT_DATE
}

function normalizeTimezone(rawTimezone: string): string {
  return rawTimezone || DEFAULT_TIMEZONE
}

function normalizePostId(rawPostId: string): string | null {
  return rawPostId.length > 0 ? rawPostId : null
}

function normalizeQuery(route: {
  name: unknown
  query: Record<string, unknown>
}): CalendarUrlState {
  const rawChannels = route.query['channels[]'] ?? route.query.channels

  return {
    surface: normalizeSurface(route),
    date: normalizeDate(trimOrEmpty(route.query.date)),
    timezone: normalizeTimezone(trimOrEmpty(route.query.timezone)),
    status: normalizeStatus(trimOrEmpty(route.query.status)),
    q: trimOrEmpty(route.query.q),
    channelIds: [...new Set(toArray(rawChannels))],
    postId: normalizePostId(trimOrEmpty(route.query.postId)),
  }
}

function buildQuery(state: CalendarUrlState): LocationQueryRaw {
  const query: LocationQueryRaw = {}

  if (state.date !== DEFAULT_DATE) {
    query.date = state.date
  }

  if (state.timezone !== DEFAULT_TIMEZONE) {
    query.timezone = state.timezone
  }

  if (state.status !== 'all') {
    query.status = state.status
  }

  if (state.q) {
    query.q = state.q
  }

  if (state.channelIds.length > 0) {
    query['channels[]'] = state.channelIds
  }

  if (state.postId) {
    query.postId = state.postId
  }

  return query
}

function areQueriesEquivalent(left: Record<string, unknown>, right: LocationQueryRaw): boolean {
  // Normalize `channels` → `channels[]` so that route queries using either form
  // compare as equivalent to the canonical `channels[]` built query.
  const normalizeEntries = (query: Record<string, unknown>): Array<[string, string]> =>
    Object.entries(query)
      .flatMap(([key, value]): Array<[string, string]> => {
        const normalizedKey = key === 'channels' ? 'channels[]' : key
        if (Array.isArray(value)) {
          return [
            [
              normalizedKey,
              value
                .map(String)
                .sort((a, b) => a.localeCompare(b))
                .join(','),
            ],
          ]
        }
        if (value == null) return []
        return [[normalizedKey, typeof value === 'string' ? value : JSON.stringify(value)]]
      })
      .sort(([leftKey], [rightKey]) => leftKey.localeCompare(rightKey))

  return JSON.stringify(normalizeEntries(left)) === JSON.stringify(normalizeEntries(right))
}

async function navigate(
  router: Router,
  state: CalendarUrlState,
  method: 'push' | 'replace',
): Promise<void> {
  await router[method]({
    name: CALENDAR_ROUTE_NAMES[state.surface],
    query: buildQuery(state),
  })
}

export function createCalendarUrlController(
  route: RouteLocationNormalizedLoaded,
  router: Router,
): CalendarUrlController {
  const state = computed<CalendarUrlState>(() => normalizeQuery(route))

  const needsCanonicalization = computed(() => {
    if (!VALID_SURFACES.has(state.value.surface)) return true
    if (route.name !== CALENDAR_ROUTE_NAMES[state.value.surface]) return true
    const rawStatus = trimOrEmpty(route.query.status)
    if (isInvalidStatus(rawStatus)) return true
    return !areQueriesEquivalent(route.query, buildQuery(state.value))
  })

  return {
    state,
    needsCanonicalization,
    canonicalize: async () => {
      if (!needsCanonicalization.value) return
      await navigate(router, state.value, 'replace')
    },
    setSurface: async (surface) => {
      await navigate(router, { ...state.value, surface }, 'push')
    },
    setDate: async (date) => {
      await navigate(router, { ...state.value, date: normalizeDate(date) }, 'push')
    },
    stepPeriod: async (direction: 'forward' | 'backward') => {
      const date = new Date(`${state.value.date}T00:00:00`)
      const sign = direction === 'forward' ? 1 : -1
      if (state.value.surface === 'calendar-month') {
        // Clamp to first of month before stepping to avoid overflow
        // (e.g. Jan 31 → setMonth(1) would overflow to March 3).
        date.setDate(1)
        date.setMonth(date.getMonth() + sign)
      } else {
        date.setDate(date.getDate() + sign * 7)
      }
      const y = date.getFullYear()
      const m = String(date.getMonth() + 1).padStart(2, '0')
      const d = String(date.getDate()).padStart(2, '0')
      await navigate(router, { ...state.value, date: `${y}-${m}-${d}` }, 'push')
    },
    setTimezone: async (timezone) => {
      await navigate(router, { ...state.value, timezone }, 'replace')
    },
    setStatus: async (status) => {
      await navigate(router, { ...state.value, status: normalizeStatus(status) }, 'replace')
    },
    setSearch: async (q) => {
      await navigate(router, { ...state.value, q: q.trim() }, 'replace')
    },
    setChannelIds: async (channelIds) => {
      await navigate(router, { ...state.value, channelIds: [...new Set(channelIds)] }, 'replace')
    },
    openPostDetail: async (postId) => {
      await navigate(router, { ...state.value, postId: normalizePostId(postId.trim()) }, 'push')
    },
    closePostDetail: async (options = {}) => {
      await navigate(
        router,
        { ...state.value, postId: null },
        options.replace === false ? 'push' : 'replace',
      )
    },
  }
}

export function useCalendarUrl(): CalendarUrlController {
  return createCalendarUrlController(useRoute(), useRouter())
}
