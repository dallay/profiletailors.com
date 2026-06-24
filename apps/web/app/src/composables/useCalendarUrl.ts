import { computed, type Ref } from 'vue'
import type { LocationQueryRaw, RouteLocationNormalizedLoaded, Router } from 'vue-router'
import { useRoute, useRouter } from 'vue-router'

export type SchedulerSurface = 'calendar-week' | 'calendar-month' | 'calendar-day' | 'list'
export type SchedulerStatus = 'all' | 'queued' | 'published' | 'cancelled'

export interface CalendarUrlState {
  surface: SchedulerSurface
  date: string
  timezone: string
  status: SchedulerStatus
  q: string
  channelIds: string[]
}

export interface CalendarUrlController {
  state: Ref<CalendarUrlState>
  needsCanonicalization: Ref<boolean>
  canonicalize: () => Promise<void>
  setSurface: (surface: SchedulerSurface) => Promise<void>
  setDate: (date: string) => Promise<void>
  setTimezone: (timezone: string) => Promise<void>
  setStatus: (status: SchedulerStatus) => Promise<void>
  setSearch: (q: string) => Promise<void>
  setChannelIds: (channelIds: string[]) => Promise<void>
}

const VALID_SURFACES = new Set<SchedulerSurface>([
  'calendar-week',
  'calendar-month',
  'calendar-day',
  'list',
])
const VALID_STATUSES = new Set<SchedulerStatus>(['all', 'queued', 'published', 'cancelled'])

const CALENDAR_ROUTE_NAMES: Record<SchedulerSurface, string> = {
  'calendar-week': 'scheduler-calendar-week',
  'calendar-month': 'scheduler-calendar-month',
  'calendar-day': 'scheduler-calendar-day',
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

function isIsoLocalDate(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value)
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
  const name = String(route.name ?? '')
  if (name === 'scheduler-calendar-month') return 'calendar-month'
  if (name === 'scheduler-list') return 'list'
  return 'calendar-week'
}

function normalizeStatus(rawStatus: string): SchedulerStatus {
  const lowered = rawStatus.toLowerCase() as SchedulerStatus
  return VALID_STATUSES.has(lowered) ? lowered : 'all'
}

function normalizeDate(rawDate: string): string {
  return isIsoLocalDate(rawDate) ? rawDate : resolveToday()
}

function normalizeTimezone(rawTimezone: string): string {
  return rawTimezone || resolveBrowserTimezone()
}

function normalizeQuery(route: {
  name: unknown
  query: Record<string, unknown>
}): CalendarUrlState {
  return {
    surface: normalizeSurface(route),
    date: normalizeDate(trimOrEmpty(route.query.date)),
    timezone: normalizeTimezone(trimOrEmpty(route.query.timezone)),
    status: normalizeStatus(trimOrEmpty(route.query.status)),
    q: trimOrEmpty(route.query.q),
    channelIds: [...new Set(toArray(route.query.channels))],
  }
}

function buildQuery(state: CalendarUrlState): LocationQueryRaw {
  const query: LocationQueryRaw = {}

  if (state.date !== resolveToday()) {
    query.date = state.date
  }

  if (state.timezone !== resolveBrowserTimezone()) {
    query.timezone = state.timezone
  }

  if (state.status !== 'all') {
    query.status = state.status
  }

  if (state.q) {
    query.q = state.q
  }

  if (state.channelIds.length > 0) {
    query.channels = state.channelIds
  }

  return query
}

function areQueriesEquivalent(left: Record<string, unknown>, right: LocationQueryRaw): boolean {
  const normalizeEntries = (query: Record<string, unknown>): Array<[string, string]> =>
    Object.entries(query)
      .flatMap(([key, value]): Array<[string, string]> => {
        if (Array.isArray(value)) {
          return [
            [
              key,
              value
                .map(String)
                .sort((a, b) => a.localeCompare(b))
                .join(','),
            ],
          ]
        }
        if (value == null) return []
        return [[key, String(value)]]
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
    setTimezone: async (timezone) => {
      await navigate(router, { ...state.value, timezone: normalizeTimezone(timezone) }, 'replace')
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
  }
}

export function useCalendarUrl(): CalendarUrlController {
  return createCalendarUrlController(useRoute(), useRouter())
}
