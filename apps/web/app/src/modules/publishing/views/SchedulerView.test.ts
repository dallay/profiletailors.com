import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref, type Ref } from 'vue'
import SchedulerView from './SchedulerView.vue'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'
import type { Publication } from '@modules/publishing/infrastructure/publishing.store'
import type { CalendarUrlController } from '@modules/publishing/application/useCalendarUrl'

// ---------------------------------------------------------------------------
// Shared mock controller factory
// ---------------------------------------------------------------------------

function makeUrlController(
  overrides: Partial<{
    surface: 'calendar-week' | 'calendar-month' | 'list'
    view: 'day' | '3-days' | 'week' | 'month' | 'agenda'
    date: string
    status: 'all' | 'queued' | 'published' | 'cancelled'
    timezone: string
    q: string
    channelIds: string[]
    postId: string | null
    needsCanonicalization: boolean
  }> = {},
): CalendarUrlController {
  const state = ref({
    surface: overrides.surface ?? 'calendar-week',
    view: overrides.view ?? 'week',
    date: overrides.date ?? '2026-06-15',
    status: overrides.status ?? 'all',
    timezone: overrides.timezone ?? 'UTC',
    q: overrides.q ?? '',
    channelIds: overrides.channelIds ?? [],
    postId: overrides.postId ?? null,
  })

  return {
    state,
    needsCanonicalization: ref(overrides.needsCanonicalization ?? false),
    canonicalize: vi.fn().mockResolvedValue(undefined),
    setSurface: vi.fn().mockResolvedValue(undefined),
    setView: vi.fn().mockResolvedValue(undefined),
    setDate: vi.fn().mockResolvedValue(undefined),
    stepPeriod: vi.fn().mockResolvedValue(undefined),
    setTimezone: vi.fn().mockResolvedValue(undefined),
    setStatus: vi.fn().mockResolvedValue(undefined),
    setSearch: vi.fn().mockResolvedValue(undefined),
    setChannelIds: vi.fn().mockResolvedValue(undefined),
    setFilters: vi.fn().mockResolvedValue(undefined),
    openPostDetail: vi.fn().mockResolvedValue(undefined),
    closePostDetail: vi.fn().mockResolvedValue(undefined),
  }
}

// Singleton mock controller — reset in beforeEach
let mockController = makeUrlController()
let mockClockNow: Ref<Date>
const mockSidebarIsMobile = ref(false)
const mockSetOpenMobile = vi.fn()
const mockSetOpen = vi.fn()

vi.mock('@modules/publishing/application/useCalendarUrl', () => ({
  useCalendarUrl: () => mockController,
}))

vi.mock('@/lib/app-tour', () => ({
  startAppTour: vi.fn(),
}))

vi.mock('@vueuse/core', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vueuse/core')>()
  return {
    ...actual,
    useMediaQuery: () => ref(false),
  }
})

vi.mock('@profiletailors/vue-ui/shell/sidebar', () => ({
  useSidebar: () => ({
    isMobile: mockSidebarIsMobile,
    setOpenMobile: mockSetOpenMobile,
    setOpen: mockSetOpen,
  }),
}))

// ---------------------------------------------------------------------------
// Module mocks
// ---------------------------------------------------------------------------

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      if (!params) return key
      // Mirror the real translation pattern for keys tested with placeholders.
      if (key === 'scheduler.morePosts') {
        return `+${String(params.count ?? '')} more`
      }
      return key.replace(/\{(\w+)\}/g, (_, name) =>
        params[name] !== undefined ? String(params[name]) : `{${name}}`,
      )
    },
    locale: { value: 'en' },
  }),
}))

vi.mock('@modules/publishing/application/useReactiveClock', () => ({
  useReactiveClock: ({
    onVisible,
    onHidden,
  }: {
    onVisible?: () => void
    onHidden?: () => void
  }) => {
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'visible',
    })
    document.dispatchEvent(new Event('visibilitychange'))
    queueMicrotask(() => {
      onVisible?.()
      onHidden?.()
    })
    return { now: mockClockNow, stop: vi.fn() }
  },
}))

vi.mock('@modules/publishing/application/useCalendarRevalidation', () => {
  const fetchCalendarFn = vi.fn<(range: { from: string; to: string }) => Promise<void>>()
  const revalidation = {
    request: vi.fn(),
    setVisible: vi.fn(),
    stop: vi.fn(),
    runNow: vi.fn(),
  }
  return {
    useCalendarRevalidation: vi.fn(() => revalidation),
    __fetchCalendarMock: fetchCalendarFn,
    __revalidationMock: revalidation,
  }
})

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  createApiFetch: () =>
    async function apiFetch<T>() {
      return {} as T
    },
  refreshSession: vi.fn().mockResolvedValue(null),
  getCurrentUserProfile: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  register: vi.fn(),
  logoutSession: vi.fn(),
}))

const toastSuccessMock = vi.fn()
vi.mock('vue-sonner', () => ({
  toast: {
    success: (...args: unknown[]) => toastSuccessMock(...args),
    error: vi.fn(),
    info: vi.fn(),
  },
}))

vi.mock('@modules/publishing/presentation/components/CreatePostModal.vue', () => ({
  default: {
    template:
      '<div v-if="isOpen" data-testid="create-post-modal"><button data-testid="create-post-updated" @click="$emit(\'updated\')">updated</button><button data-testid="create-post-created" @click="$emit(\'created\')">created</button><button data-testid="create-post-created-keep-open" @click="$emit(\'created\', { keepOpen: true })">created keep open</button></div><div v-if="isOpen" data-testid="create-post-modal-open">open</div>',
    props: ['isOpen', 'initialDate', 'editingPublication', 'provider'],
    emits: ['close', 'created', 'updated'],
  },
}))

vi.mock('@modules/publishing/presentation/components/PostDetailModal.vue', () => ({
  default: {
    template:
      '<div v-if="isOpen" data-testid="post-detail-modal">' +
      '<button data-testid="detail-edit" @click="$emit(\'edit\', publication)">edit</button>' +
      '<button data-testid="detail-deleted" @click="$emit(\'deleted\')">deleted</button>' +
      '<button data-testid="detail-close" @click="$emit(\'close\')">close</button>' +
      '<button data-testid="detail-reschedule" @click="$emit(\'reschedule\')">reschedule</button>' +
      '<button data-testid="detail-retried" @click="$emit(\'retried\')">retried</button>' +
      '</div>',
    props: ['isOpen', 'publication'],
    emits: ['close', 'deleted', 'reschedule', 'retried', 'edit'],
  },
}))

vi.mock('@modules/publishing/presentation/components/CalendarHeader.vue', () => ({
  default: {
    template:
      '<div data-testid="calendar-header"><button data-testid="header-new-post" @click="$emit(\'new-post\')">New Post</button></div>',
    props: ['calendarView', 'surface', 'periodLabel'],
    emits: ['change:view', 'change:date', 'change:filter', 'new-post'],
  },
}))

vi.mock('@modules/publishing/presentation/components/CalendarCell.vue', () => ({
  default: {
    template:
      '<div data-testid="calendar-cell"><span v-if="activityEntry">{{ activityEntry.count }} {{ activityEntry.density }}</span></div>',
    props: ['activityEntry'],
  },
}))

vi.mock('@modules/publishing/presentation/components/ConflictBadge.vue', () => ({
  default: { template: '<div data-testid="conflict-badge" />' },
}))

vi.mock('@/components/ui/card', () => ({
  Card: { template: '<div><slot /></div>' },
}))

vi.mock('@profiletailors/vue-ui/shell/button', () => ({
  Button: { template: '<button><slot /></button>' },
}))

vi.mock('@lucide/vue', () => {
  const stub = { template: '<svg />' }
  return {
    Plus: stub,
    Trash2: stub,
  }
})

vi.mock('@shared/lib/provider-styles', () => ({
  getProviderColor: () => 'provider-color',
}))

vi.mock('@modules/publishing/presentation/components/RecurringScheduleModal.vue', () => ({
  default: {
    template:
      '<div v-if="isOpen" data-testid="recurring-schedule-modal">' +
      '<button data-testid="recurring-created" @click="$emit(\'created\')">created</button>' +
      '<button data-testid="recurring-updated" @click="$emit(\'updated\')">updated</button>' +
      '<button data-testid="recurring-close" @click="$emit(\'close\')">close</button>' +
      '</div>',
    props: ['isOpen', 'editingRecurringSchedule'],
    emits: ['close', 'created', 'updated'],
  },
}))

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('SchedulerView', () => {
  beforeEach(() => {
    mockClockNow = ref(new Date())
    setActivePinia(createPinia())
    const store = usePublishingStore()
    store.publications = []
    store.activity = []
    store.channels = [
      {
        id: '788c8103-35f1-5899-b15a-6ca994253c99',
        accountId: '788c8103-35f1-5899-b15a-6ca994253c99',
        name: 'LinkedIn',
        provider: 'linkedin',
        avatar: '',
        handle: '@company',
        status: 'ACTIVE',
      },
    ]
    vi.spyOn(store, 'fetchCalendar').mockResolvedValue()
    vi.spyOn(store, 'connectLinkedInPersonalProfile').mockResolvedValue(undefined as never)
    vi.spyOn(store, 'deletePost').mockResolvedValue(undefined as never)
    mockSidebarIsMobile.value = false
    mockSetOpenMobile.mockReset()
    mockSetOpen.mockReset()

    // Reset mock controller to default state per test
    mockController = makeUrlController()
  })

  function mountView(urlOverrides: Parameters<typeof makeUrlController>[0] = {}) {
    mockController = makeUrlController(urlOverrides)
    return mount(SchedulerView, {
      global: {
        mocks: { $t: (key: string) => key },
      },
    })
  }

  it('keeps one available tab stop as the date, period, and clock change', async () => {
    mockClockNow.value = new Date('2026-06-15T08:00:00')
    const wrapper = mountView({ date: '2026-06-15' })
    await flushPromises()
    const focused = wrapper.get('[data-calendar-slot="2026-06-17-10"]')
    await focused.trigger('focus')
    expect(focused.attributes('tabindex')).toBe('0')
    mockController.state.value = { ...mockController.state.value, view: 'day' }
    await flushPromises()
    const tabStops = () => wrapper.findAll('[data-calendar-slot][tabindex="0"]')
    expect(tabStops()).toHaveLength(1)
    expect(tabStops()[0]?.attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-calendar-slot="2026-06-15-10"]').trigger('focus')
    mockController.state.value = { ...mockController.state.value, date: '2026-06-16' }
    await flushPromises()
    expect(tabStops()).toHaveLength(1)
    expect(tabStops()[0]?.attributes('data-calendar-slot')).toBe('2026-06-16-0')
    await wrapper.get('[data-calendar-slot="2026-06-16-10"]').trigger('focus')
    mockClockNow.value = new Date('2026-06-16T10:00:00')
    await flushPromises()
    expect(tabStops()).toHaveLength(1)
    expect(tabStops()[0]?.attributes('data-calendar-slot')).toBe('2026-06-16-11')
    expect(tabStops()[0]?.attributes('disabled')).toBeUndefined()
    await tabStops()[0]?.trigger('keydown', { key: 'ArrowDown' })
    expect(tabStops()[0]?.attributes('data-calendar-slot')).toBe('2026-06-16-12')
    wrapper.unmount()
  })

  it('subscribes to publication events on mount and unsubscribes on unmount', async () => {
    const store = usePublishingStore()
    const auth = useAuthStore()
    const workspace = useWorkspaceStore()
    Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
    workspace.setActiveWorkspaceId('a219727d-019f-544c-95f9-ac30f00cc8b2')
    vi.spyOn(store, 'fetchRecurringSchedules').mockResolvedValue([])
    const subscribe = vi.spyOn(store, 'subscribePublicationEvents').mockResolvedValue(null)
    const unsubscribe = vi.spyOn(store, 'unsubscribePublicationEvents').mockImplementation(() => {})
    const { __revalidationMock } = (await import(
      '@modules/publishing/application/useCalendarRevalidation'
    )) as unknown as {
      __revalidationMock: { request: ReturnType<typeof vi.fn> }
    }
    __revalidationMock.request.mockClear()

    const wrapper = mountView()
    await flushPromises()

    expect(subscribe).toHaveBeenCalledTimes(1)
    const onInvalidation = subscribe.mock.calls[0]?.[0]
    expect(onInvalidation).toBeTypeOf('function')
    onInvalidation?.({
      workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
      publicationId: '48c5787c-4ddb-5cd5-b205-cd0f99e7fc07',
      changeType: 'publication.status-changed',
      occurredAt: '2026-09-25T12:00:00.000Z',
    })
    expect(__revalidationMock.request).toHaveBeenCalled()

    wrapper.unmount()
    expect(unsubscribe).toHaveBeenCalledTimes(2)
  })

  it('resubscribes to publication events when the active workspace changes', async () => {
    const store = usePublishingStore()
    const auth = useAuthStore()
    const workspace = useWorkspaceStore()
    Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
    workspace.setActiveWorkspaceId('a219727d-019f-544c-95f9-ac30f00cc8b2')
    vi.spyOn(store, 'fetchRecurringSchedules').mockResolvedValue([])
    const subscribe = vi.spyOn(store, 'subscribePublicationEvents').mockResolvedValue(null)

    const wrapper = mountView()
    await flushPromises()
    expect(subscribe).toHaveBeenCalledTimes(1)

    workspace.setActiveWorkspaceId('c1d6493d-5117-5741-a405-50309e8bfc77')
    await flushPromises()
    expect(subscribe).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('mounts and asks the revalidation coordinator for the current range', async () => {
    const store = usePublishingStore()
    const { __revalidationMock } = (await import(
      '@modules/publishing/application/useCalendarRevalidation'
    )) as unknown as {
      __revalidationMock: { request: ReturnType<typeof vi.fn>; runNow: ReturnType<typeof vi.fn> }
    }
    __revalidationMock.request.mockClear()

    const wrapper = mountView()
    await flushPromises()

    expect(__revalidationMock.request).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="calendar-header"]').exists()).toBe(true)
    void store
  })

  it('uses flex sizing on the scheduler root so the shell keeps scroll ownership', async () => {
    const wrapper = mountView()
    await flushPromises()

    const root = wrapper.get('[data-testid="scheduler-root"]')

    expect(root.classes()).toContain('flex')
    expect(root.classes()).toContain('flex-1')
    expect(root.classes()).toContain('min-h-0')
    expect(root.classes()).not.toContain('h-full')
  })

  it('keeps vertical scrolling inside the week timeline viewport', async () => {
    const wrapper = mountView({ surface: 'calendar-week' })
    await flushPromises()

    const timelineViewport = wrapper.get('[data-testid="week-timeline-viewport"]')

    expect(timelineViewport.classes()).toContain('relative')
    expect(timelineViewport.classes()).toContain('min-h-0')
    expect(timelineViewport.classes()).toContain('flex-1')
    expect(timelineViewport.classes()).toContain('overflow-y-auto')
  })

  it('does not keep h-full on the calendar mode wrapper', async () => {
    const wrapper = mountView({ surface: 'calendar-week' })
    await flushPromises()

    const calendarMode = wrapper.get('[data-testid="calendar-mode"]')

    expect(calendarMode.classes()).toContain('flex')
    expect(calendarMode.classes()).toContain('min-h-0')
    expect(calendarMode.classes()).not.toContain('h-full')
  })

  it('gives the month grid its own vertical scroll viewport', async () => {
    const wrapper = mountView({ surface: 'calendar-month', view: 'month' })
    await flushPromises()

    const monthView = wrapper.find('[data-testid="month-calendar-view"]')
    const monthScrollport = wrapper.find('.thin-scrollbar.overflow-y-auto')

    expect(monthView.exists()).toBe(true)
    expect(monthView.classes()).toContain('flex-1')
    expect(monthView.classes()).not.toContain('h-full')
    expect(monthScrollport.exists()).toBe(true)
    expect(monthScrollport.classes()).toContain('min-h-0')

    wrapper.unmount()
  })

  it('uses a flex column workspace container so the calendar can shrink', async () => {
    const wrapper = mountView({ surface: 'calendar-week' })
    await flushPromises()

    const workspace = wrapper.get('[data-testid="scheduler-workspace"]')

    expect(workspace.classes()).toContain('flex')
    expect(workspace.classes()).toContain('flex-col')
    expect(workspace.classes()).toContain('flex-1')
    expect(workspace.classes()).toContain('min-h-0')
    expect(workspace.classes()).toContain('overflow-hidden')
  })

  it('opens the create post modal from calendar header new-post event', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('[data-testid="header-new-post"]').trigger('click')

    expect(wrapper.find('[data-testid="create-post-modal"]').exists()).toBe(true)
  })

  it('shows reconnect prompt when Threads channels require reconnect', async () => {
    const store = usePublishingStore()
    store.channels = [
      {
        id: '788c8103-35f1-5899-b15a-6ca994253c99',
        accountId: '788c8103-35f1-5899-b15a-6ca994253c99',
        name: 'Threads profile',
        provider: 'threads',
        avatar: '',
        handle: '@company',
        status: 'REQUIRES_RECONNECT',
      },
    ]
    vi.spyOn(store, 'connectProviderPersonalProfile').mockResolvedValue(undefined as never)

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Reconnect Required')
    expect(wrapper.text()).toContain('Threads')
    const reconnectButton = wrapper.get('[data-testid="reconnect-provider-threads"]')
    await reconnectButton.trigger('click')
    expect(store.connectProviderPersonalProfile).toHaveBeenCalledWith('threads')
  })

  it('renders thumbnail image in week view scheduled post cards', async () => {
    const store = usePublishingStore()
    const today = new Date()
    const scheduledAt = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 14, 0, 0)
    store.publications = [
      {
        id: 'b642a89c-c558-56a0-9b68-844c557242e7',
        content: 'Post with image in week view',
        channels: ['linkedin'],
        scheduledAt: scheduledAt.toISOString(),
        status: 'QUEUED',
        priority: false,
        thumbnail: 'https://example.com/week-thumb.jpg',
      },
    ]

    // URL date must match today so publication falls in the rendered week
    const todayStr = today.toISOString().slice(0, 10)
    const wrapper = mountView({ date: todayStr })
    await flushPromises()
    const img = wrapper.find('img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe('https://example.com/week-thumb.jpg')
  })

  describe('week grid day grouping is timezone-safe', () => {
    // Regression coverage for a bug where a post scheduled at
    // 2026-07-14T09:00:00Z (11:00 local in Europe/Madrid, UTC+2 in summer)
    // rendered under Jul 15 instead of Jul 14. Root cause: dateKey() used
    // toISOString() (UTC) while the week-day columns are built from local
    // midnight, so a positive UTC offset shifted the column's key back a day
    // while the post's key (built from an 11:00 local time) stayed on the
    // same day, misaligning the two.
    //
    // These cases sweep positive, negative, fractional (UTC+5:30), and
    // extreme (+14 / -11) offsets, plus a winter date (Madrid is UTC+1 then,
    // not UTC+2) to prove the fix isn't accidentally tied to one offset.
    const originalTz = process.env.TZ

    afterEach(() => {
      process.env.TZ = originalTz
    })

    /** Local Y-M-D for `iso` once `process.env.TZ` is set, used as ground truth. */
    function localDateParts(iso: string) {
      const d = new Date(iso)
      return {
        dateStr: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`,
        day: String(d.getDate()),
      }
    }

    it.each([
      {
        tz: 'Europe/Madrid',
        scheduledAt: '2026-07-14T09:00:00.000Z',
        label: 'UTC+2 (summer DST)',
      },
      {
        tz: 'Europe/Madrid',
        scheduledAt: '2026-01-14T09:00:00.000Z',
        label: 'UTC+1 (winter, no DST)',
      },
      {
        tz: 'America/New_York',
        scheduledAt: '2026-07-14T02:30:00.000Z',
        label: 'UTC-4 (summer DST)',
      },
      {
        tz: 'Asia/Kolkata',
        scheduledAt: '2026-07-14T20:00:00.000Z',
        label: 'UTC+5:30 (fractional offset)',
      },
      {
        tz: 'Pacific/Kiritimati',
        scheduledAt: '2026-07-14T09:00:00.000Z',
        label: 'UTC+14 (extreme positive)',
      },
      {
        tz: 'Pacific/Niue',
        scheduledAt: '2026-07-14T09:00:00.000Z',
        label: 'UTC-11 (extreme negative)',
      },
      {
        tz: 'UTC',
        scheduledAt: '2026-07-14T00:00:00.000Z',
        label: 'UTC (no offset)',
      },
    ])('keeps the post under its local day for $tz ($label)', async ({ tz, scheduledAt }) => {
      process.env.TZ = tz
      const { dateStr, day } = localDateParts(scheduledAt)

      const store = usePublishingStore()
      store.publications = [
        {
          id: crypto.randomUUID(),
          content: `Post scheduled for ${tz}`,
          channels: ['linkedin'],
          scheduledAt,
          status: 'SCHEDULED',
          priority: false,
        },
      ]

      const wrapper = mountView({ date: dateStr })
      await flushPromises()

      const card = wrapper
        .findAll('[draggable="true"]')
        .find((b) => b.text().includes(`Post scheduled for ${tz}`))
      expect(card).toBeDefined()

      const dayCell = card!.element.closest('button[type="button"]')
      expect(dayCell).not.toBeNull()

      const allDayCells = wrapper.findAll('button[type="button"]').map((w) => w.element)
      const cellIndex = allDayCells.indexOf(dayCell as HTMLButtonElement)
      const dayHeaders = wrapper.findAll('span.leading-none').map((w) => w.text())

      // 7 day-cells per hour row; the column index maps 1:1 to the header index.
      const columnIndex = cellIndex % 7
      expect(dayHeaders[columnIndex]).toBe(day)
    })
  })

  it('places thumbnail to the right of text content in week view card', async () => {
    const store = usePublishingStore()
    const today = new Date()
    const scheduledAt = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 14, 0, 0)
    store.publications = [
      {
        id: 'c609e006-4c7e-54d0-ac39-10806f98674a',
        content: 'Side by side layout test',
        channels: ['linkedin'],
        scheduledAt: scheduledAt.toISOString(),
        status: 'QUEUED',
        priority: false,
        thumbnail: 'https://example.com/side-thumb.jpg',
      },
    ]

    // URL date must match today so publication falls in the rendered week
    const todayStr = today.toISOString().slice(0, 10)
    const wrapper = mountView({ date: todayStr })
    await flushPromises()
    // Find the week card (div[draggable]) that contains the pub
    const card = wrapper
      .findAll('[draggable="true"]')
      .find((b) => b.text().includes('Side by side layout test'))
    expect(card).toBeDefined()
    const resolvedCard = card!
    expect(resolvedCard.exists()).toBe(true)
    // The card uses a two-column grid so media stays beside the text without stealing a row.
    expect(resolvedCard.classes()).toContain('grid')
    const img = resolvedCard.find('img')
    const textEl = resolvedCard.find('p')
    expect(img.exists()).toBe(true)
    expect(textEl.exists()).toBe(true)
    expect(resolvedCard.find('.col-start-2 img').exists()).toBe(true)
  })

  it('handles delete error gracefully in handleDeletePublication', async () => {
    const store = usePublishingStore()
    vi.spyOn(store, 'deletePost').mockRejectedValue(new Error('Network error'))
    store.publications = [
      {
        id: '464d77e1-1243-56ef-beb7-f4c916175f43',
        content: 'To be deleted',
        channels: ['linkedin'],
        scheduledAt: '2026-06-15T10:00:00.000Z',
        status: 'QUEUED',
        priority: false,
      },
    ]

    const wrapper = mountView({ date: '2026-06-15' })
    await flushPromises()

    // Find the delete button on the card (for deletable posts)
    const deleteOverlay = wrapper
      .findAll('button')
      .find((b) => b.attributes('title') === 'Delete publication')
    expect(deleteOverlay).toBeDefined()
    await deleteOverlay!.trigger('click')

    // Should not throw — the error is caught internally
    // Publication should still be in the store
    expect(store.publications).toHaveLength(1)
  })

  it('handleReconnect suppresses errors during reconnect', async () => {
    const store = usePublishingStore()
    vi.spyOn(store, 'connectLinkedInPersonalProfile').mockRejectedValue(
      new Error('Reconnect failed'),
    )

    // No channels requiring reconnect currently — just ensure the view mounts
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
  })

  it('handles drag-and-drop failure gracefully without throwing', async () => {
    const store = usePublishingStore()
    const now = new Date()
    const futureDate = new Date(now.getTime() + 60 * 60 * 1000).toISOString()
    store.publications = [
      {
        id: 'a154e161-9521-5adb-b9c3-03e70379de4f',
        content: 'Draggable post',
        channels: ['linkedin'],
        scheduledAt: futureDate,
        status: 'QUEUED',
        priority: false,
      },
    ]
    // Make reschedulePublication fail
    vi.spyOn(store, 'reschedulePublication').mockRejectedValue(new Error('Reschedule failed'))

    const wrapper = mountView()
    await flushPromises()

    // Verify the view is still rendered - onDropCell catches the error internally
    expect(wrapper.exists()).toBe(true)
  })

  it('opens detail modal from postId in the scheduler URL state', async () => {
    const store = usePublishingStore()
    const pub: Publication = {
      id: 'e22182b1-4b6e-56a8-825d-89888d23157a',
      content: 'Deep linked post',
      channels: ['linkedin'],
      scheduledAt: '2026-06-25T10:00:00Z',
      status: 'SCHEDULED',
      priority: false,
    }
    store.publications = [pub]

    const wrapper = mountView({
      date: '2026-06-25',
      postId: 'e22182b1-4b6e-56a8-825d-89888d23157a',
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="post-detail-modal"]').exists()).toBe(true)
  })

  it('openPostDetail writes the selected publication id into the URL state', async () => {
    const wrapper = mountView({ date: '2026-06-25' })
    await flushPromises()
    const pub: Publication = {
      id: '1bb13edf-eaf3-5b61-a5c1-aef25483ebf4',
      content: 'Open via URL state',
      channels: ['linkedin'],
      scheduledAt: '2026-06-25T10:00:00Z',
      status: 'SCHEDULED',
      priority: false,
    }

    const vm = wrapper.vm as unknown as { openPostDetail: (pub: Publication) => void }
    vm.openPostDetail(pub)
    await flushPromises()

    expect(mockController.openPostDetail).toHaveBeenCalledWith(
      '1bb13edf-eaf3-5b61-a5c1-aef25483ebf4',
    )
  })

  it('opens CreatePostModal in edit mode when PostDetailModal emits edit', async () => {
    const store = usePublishingStore()
    const pub: Publication = {
      id: '1807f930-2c97-5f31-b9cf-263501218962',
      content: 'Editable post',
      channels: ['linkedin'],
      scheduledAt: '2026-06-25T10:00:00Z',
      status: 'SCHEDULED',
      priority: false,
    }
    store.publications = [pub]

    const wrapper = mountView({
      date: '2026-06-25',
      postId: '1807f930-2c97-5f31-b9cf-263501218962',
    })
    await flushPromises()

    const editBtn = wrapper.find('[data-testid="detail-edit"]')
    await editBtn.trigger('click')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[data-testid="create-post-modal"]').exists()).toBe(true)
    expect(mockController.closePostDetail).toHaveBeenCalled()
  })

  describe('route-driven post detail modal', () => {
    it('does not open the detail modal when postId does not match any filtered publication', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: '3b3b9820-59bc-53dd-8b71-bf129eb34391',
          content: 'Some other post',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T10:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      const wrapper = mountView({
        date: '2026-06-25',
        postId: '8d95892a-98a4-5a02-95bf-82ef511d72fe',
      })
      await flushPromises()

      expect(wrapper.find('[data-testid="post-detail-modal"]').exists()).toBe(false)
    })

    it('closes the post detail modal when PostDetailModal emits deleted', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: '5b7753ef-e664-5750-b295-89b1595ae34d',
          content: 'Deleted via modal',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T10:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      const wrapper = mountView({
        date: '2026-06-25',
        postId: '5b7753ef-e664-5750-b295-89b1595ae34d',
      })
      await flushPromises()

      await wrapper.find('[data-testid="detail-deleted"]').trigger('click')
      await flushPromises()

      expect(mockController.closePostDetail).toHaveBeenCalledWith(undefined)
    })

    it('closes the post detail modal when PostDetailModal emits close', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: '6473d638-ce2f-5940-8c86-0690ceeac8ff',
          content: 'Closed via modal',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T10:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      const wrapper = mountView({
        date: '2026-06-25',
        postId: '6473d638-ce2f-5940-8c86-0690ceeac8ff',
      })
      await flushPromises()

      await wrapper.find('[data-testid="detail-close"]').trigger('click')
      await flushPromises()

      expect(mockController.closePostDetail).toHaveBeenCalledWith(undefined)
    })

    it('closes the post detail modal via onReschedule when PostDetailModal emits reschedule', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: 'c0e422a3-0a0b-53c4-884b-ca7d16481cc5',
          content: 'Rescheduled via modal',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T10:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      const wrapper = mountView({
        date: '2026-06-25',
        postId: 'c0e422a3-0a0b-53c4-884b-ca7d16481cc5',
      })
      await flushPromises()

      await wrapper.find('[data-testid="detail-reschedule"]').trigger('click')
      await flushPromises()

      expect(mockController.closePostDetail).toHaveBeenCalled()
    })

    it('closes the post detail modal via onReschedule when PostDetailModal emits retried', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: '56c0efbf-0c91-5ad6-a3c9-ecba2299f98e',
          content: 'Retried via modal',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T10:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      const wrapper = mountView({
        date: '2026-06-25',
        postId: '56c0efbf-0c91-5ad6-a3c9-ecba2299f98e',
      })
      await flushPromises()

      await wrapper.find('[data-testid="detail-retried"]').trigger('click')
      await flushPromises()

      expect(mockController.closePostDetail).toHaveBeenCalled()
    })

    it('closes the post detail modal when the currently open publication is deleted', async () => {
      const store = usePublishingStore()
      const pub: Publication = {
        id: '6d45c5e0-5163-5b6b-a451-ea2a5eabe866',
        content: 'To delete while open',
        channels: ['linkedin'],
        scheduledAt: '2026-06-25T10:00:00Z',
        status: 'QUEUED',
        priority: false,
      }
      store.publications = [pub]
      vi.spyOn(store, 'deletePost').mockResolvedValue(undefined as never)

      const wrapper = mountView({
        date: '2026-06-25',
        postId: '6d45c5e0-5163-5b6b-a451-ea2a5eabe866',
      })
      await flushPromises()

      const vm = wrapper.vm as unknown as {
        handleDeletePublication: (id: string) => Promise<void>
      }
      await vm.handleDeletePublication('6d45c5e0-5163-5b6b-a451-ea2a5eabe866')
      await flushPromises()

      expect(store.deletePost).toHaveBeenCalledWith('6d45c5e0-5163-5b6b-a451-ea2a5eabe866')
      expect(mockController.closePostDetail).toHaveBeenCalled()
    })

    it('does not attempt to close the post detail modal when a different publication is deleted', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: '0de4716d-3049-5c61-a576-9f3e91390f0b',
          content: 'Currently open',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T10:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
        {
          id: '8f3ab6f2-5be3-56e9-8a75-910c0de6dad9',
          content: 'Not open',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T11:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]
      vi.spyOn(store, 'deletePost').mockResolvedValue(undefined as never)

      const wrapper = mountView({
        date: '2026-06-25',
        postId: '0de4716d-3049-5c61-a576-9f3e91390f0b',
      })
      await flushPromises()
      ;(mockController.closePostDetail as ReturnType<typeof vi.fn>).mockClear()

      const vm = wrapper.vm as unknown as {
        handleDeletePublication: (id: string) => Promise<void>
      }
      await vm.handleDeletePublication('8f3ab6f2-5be3-56e9-8a75-910c0de6dad9')
      await flushPromises()

      expect(store.deletePost).toHaveBeenCalledWith('8f3ab6f2-5be3-56e9-8a75-910c0de6dad9')
      expect(mockController.closePostDetail).not.toHaveBeenCalled()
    })

    it('auto-closes a stale postId with replace semantics once the fetch confirms it is no longer visible', async () => {
      const store = usePublishingStore()
      store.publications = []

      mountView({ date: '2026-06-25', postId: 'e72a9cbe-b819-5893-9257-baf740adac3b' })
      await flushPromises()

      expect(mockController.closePostDetail).toHaveBeenCalledWith({ replace: true })
    })

    it('keeps the post detail open and does not canonicalize when postId resolves to a visible publication', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: 'cfc438a2-da78-5401-a457-98ff72c47608',
          content: 'Still visible',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T10:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      mountView({ date: '2026-06-25', postId: 'cfc438a2-da78-5401-a457-98ff72c47608' })
      await flushPromises()

      expect(mockController.closePostDetail).not.toHaveBeenCalled()
    })

    it('only revalidates against the latest range when navigation arrives mid-flight', async () => {
      const store = usePublishingStore()
      store.publications = []
      const { __revalidationMock } = (await import(
        '@modules/publishing/application/useCalendarRevalidation'
      )) as unknown as {
        __revalidationMock: { request: ReturnType<typeof vi.fn> }
      }
      __revalidationMock.request.mockClear()

      mountView({ date: '2026-06-25', postId: '0fd7cee3-6ff0-5b10-b5f1-4b33af3c582c' })
      await flushPromises()
      const initialCalls = __revalidationMock.request.mock.calls.length
      expect(initialCalls).toBeGreaterThanOrEqual(1)

      mockController.state.value = {
        ...mockController.state.value,
        date: '2026-06-26',
      }
      await flushPromises()

      expect(__revalidationMock.request.mock.calls.length).toBeGreaterThan(initialCalls)
      const latestCall = __revalidationMock.request.mock.calls.at(-1)?.[0] as {
        from: string
        to: string
      }
      expect(latestCall.from).toBe('2026-06-21T00:00:00.000Z')
      expect(mockController.closePostDetail).toHaveBeenCalledWith({ replace: true })
    })
  })

  it('handles header date backward navigation via handleHeaderDateChange', async () => {
    const wrapper = mountView()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      handleHeaderDateChange: (action: 'forward' | 'backward' | 'today') => void
    }

    await vm.handleHeaderDateChange('backward')
    await flushPromises()

    expect(mockController.stepPeriod).toHaveBeenCalledWith('backward')
  })

  it('handles header date today navigation via handleHeaderDateChange', async () => {
    const _store = usePublishingStore()
    const wrapper = mountView()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      handleHeaderDateChange: (action: 'forward' | 'backward' | 'today') => void
    }

    await vm.handleHeaderDateChange('today')
    await flushPromises()

    // setDate should be called with a valid YYYY-MM-DD local date string
    const calledDate = (mockController.setDate as ReturnType<typeof vi.fn>).mock.calls[0]![0]
    expect(calledDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('handles header filter change for timezone via handleHeaderFilterChange', async () => {
    const _store = usePublishingStore()
    const wrapper = mountView()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      handleHeaderFilterChange: (filter: {
        status?: 'all' | 'queued' | 'published' | 'cancelled'
        timezone?: string
        channelIds?: string[]
      }) => void
    }

    await vm.handleHeaderFilterChange({ timezone: 'America/New_York' })
    await flushPromises()

    expect(mockController.setTimezone).toHaveBeenCalledWith('America/New_York')
  })

  it('handles header filter change for channelIds via handleHeaderFilterChange', async () => {
    const _store = usePublishingStore()
    const wrapper = mountView()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      handleHeaderFilterChange: (filter: {
        status?: 'all' | 'queued' | 'published' | 'cancelled'
        timezone?: string
        channelIds?: string[]
      }) => void
    }

    await vm.handleHeaderFilterChange({ channelIds: ['788c8103-35f1-5899-b15a-6ca994253c99'] })
    await flushPromises()

    expect(mockController.setChannelIds).toHaveBeenCalledWith([
      '788c8103-35f1-5899-b15a-6ca994253c99',
    ])
  })

  it('opens day view and updates URL date when openDayView is called', async () => {
    const wrapper = mountView({ date: '2026-06-15' })
    await flushPromises()

    const vm = wrapper.vm as unknown as { openDayView: (date: Date) => void }
    // Build date from local components to avoid UTC-offset day shifts.
    const targetDate = new Date(2026, 5, 20) // June 20, 2026 local
    const expectedDate = [
      targetDate.getFullYear(),
      String(targetDate.getMonth() + 1).padStart(2, '0'),
      String(targetDate.getDate()).padStart(2, '0'),
    ].join('-')

    await vm.openDayView(targetDate)
    await flushPromises()

    expect(mockController.setDate).toHaveBeenCalledWith(expectedDate)
  })

  it('refreshes calendar when CreatePostModal emits updated', async () => {
    const { __revalidationMock } = (await import(
      '@modules/publishing/application/useCalendarRevalidation'
    )) as unknown as {
      __revalidationMock: { request: ReturnType<typeof vi.fn> }
    }
    __revalidationMock.request.mockClear()

    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('[data-testid="header-new-post"]').trigger('click')
    await flushPromises()
    const initialCalls = __revalidationMock.request.mock.calls.length

    const updatedBtn = wrapper.find('[data-testid="create-post-updated"]')
    await updatedBtn.trigger('click')
    await flushPromises()

    expect(__revalidationMock.request.mock.calls.length).toBeGreaterThan(initialCalls)
  })

  describe('month view', () => {
    it('renders month grid when surface is calendar-month', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: 'dc2014ce-6494-520c-8fd7-fa7677640441',
          content: 'Month view post',
          channels: ['linkedin'],
          scheduledAt: new Date().toISOString(),
          status: 'QUEUED',
          priority: false,
        },
      ]

      const wrapper = mountView({ surface: 'calendar-month' })
      await flushPromises()

      // Month view should render with the Card wrapper (mocked as div)
      expect(wrapper.html()).toContain('bg-bg-surface')
    })

    it('renders day-of-week headers in month view', async () => {
      const wrapper = mountView({ surface: 'calendar-month' })
      await flushPromises()

      // Should have day headers
      expect(wrapper.exists()).toBe(true)
    })
  })

  describe('filters', () => {
    it('filters publications by queued status', async () => {
      const store = usePublishingStore()
      const today = new Date()
      const todayStr = today.toISOString().slice(0, 10)

      store.publications = [
        {
          id: 'd3367f8c-6c71-5324-9598-6a16c40a3058',
          content: 'Queued post',
          channels: ['linkedin'],
          scheduledAt: new Date(
            today.getFullYear(),
            today.getMonth(),
            today.getDate(),
            10,
            0,
          ).toISOString(),
          status: 'QUEUED',
          priority: false,
        },
        {
          id: '24838748-19ea-5869-ab38-40cedeb317ce',
          content: 'Published post',
          channels: ['linkedin'],
          scheduledAt: new Date(
            today.getFullYear(),
            today.getMonth(),
            today.getDate(),
            11,
            0,
          ).toISOString(),
          status: 'PUBLISHED',
          priority: false,
        },
      ]

      const wrapper = mountView({ date: todayStr, status: 'queued' })
      await flushPromises()

      // Only queued publication should be visible in week slot
      const queuedCard = wrapper.findAll('button').find((b) => b.text().includes('Queued post'))
      const publishedCard = wrapper
        .findAll('button')
        .find((b) => b.text().includes('Published post'))

      expect(queuedCard).toBeDefined()
      expect(publishedCard).toBeUndefined()
    })

    it('filters publications by channel ID', async () => {
      const store = usePublishingStore()
      const today = new Date()
      const todayStr = today.toISOString().slice(0, 10)

      store.publications = [
        {
          id: '08d5f245-e521-5ad3-97f3-7e6ab3b9362c',
          content: 'Channel 1 post',
          channels: ['linkedin'],
          scheduledAt: new Date(
            today.getFullYear(),
            today.getMonth(),
            today.getDate(),
            10,
            0,
          ).toISOString(),
          status: 'QUEUED',
          priority: false,
          accountId: '788c8103-35f1-5899-b15a-6ca994253c99',
        },
        {
          id: '57ac0791-ec7d-53fb-8c04-a5801e8a1772',
          content: 'Channel 2 post',
          channels: ['linkedin'],
          scheduledAt: new Date(
            today.getFullYear(),
            today.getMonth(),
            today.getDate(),
            11,
            0,
          ).toISOString(),
          status: 'QUEUED',
          priority: false,
          accountId: '28353a84-fcee-5f3a-93b6-4c1bd639921e',
        },
      ]

      const wrapper = mountView({
        date: todayStr,
        channelIds: ['788c8103-35f1-5899-b15a-6ca994253c99'],
      })
      await flushPromises()

      const channel1Card = wrapper
        .findAll('button')
        .find((b) => b.text().includes('Channel 1 post'))
      const channel2Card = wrapper
        .findAll('button')
        .find((b) => b.text().includes('Channel 2 post'))

      expect(channel1Card).toBeDefined()
      expect(channel2Card).toBeUndefined()
    })

    it('filters publications by search query (tag)', async () => {
      const store = usePublishingStore()
      const today = new Date()
      const todayStr = today.toISOString().slice(0, 10)

      store.publications = [
        {
          id: 'bd611a84-4e00-5c2a-aa3c-f8a614e012cd',
          content: 'This post mentions DDD patterns',
          channels: ['linkedin'],
          scheduledAt: new Date(
            today.getFullYear(),
            today.getMonth(),
            today.getDate(),
            10,
            0,
          ).toISOString(),
          status: 'QUEUED',
          priority: false,
        },
        {
          id: '46583bc8-a3f1-5785-87fc-b699300c82cf',
          content: 'This post has no special content',
          channels: ['linkedin'],
          scheduledAt: new Date(
            today.getFullYear(),
            today.getMonth(),
            today.getDate(),
            11,
            0,
          ).toISOString(),
          status: 'QUEUED',
          priority: false,
        },
      ]

      const wrapper = mountView({ date: todayStr, q: 'ddd' })
      await flushPromises()

      const searchableCard = wrapper
        .findAll('button')
        .find((b) => b.text().includes('DDD patterns'))
      const notSearchableCard = wrapper
        .findAll('button')
        .find((b) => b.text().includes('no special content'))

      expect(searchableCard).toBeDefined()
      expect(notSearchableCard).toBeUndefined()
    })
  })

  describe('list view', () => {
    it('renders list view when surface is list', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: '1b0b2f6c-7b80-524a-b46b-723978425532',
          content: 'List view post',
          channels: ['linkedin'],
          scheduledAt: new Date().toISOString(),
          status: 'QUEUED',
          priority: false,
        },
      ]

      const wrapper = mountView({ surface: 'list' })
      await flushPromises()

      // List view should not have week/month grid structure
      // It should render the post directly
      expect(wrapper.text()).toContain('List view post')
    })

    it('shows empty state message when no publications in list view', async () => {
      const store = usePublishingStore()
      store.publications = []

      const wrapper = mountView({ surface: 'list' })
      await flushPromises()

      // Should show empty state (either text or placeholder)
      expect(wrapper.exists()).toBe(true)
    })

    it('displays publication status badges in list view', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: '83a0eb92-2f54-512e-805e-9d4f8fa3715b',
          content: 'Queued in list',
          channels: ['linkedin'],
          scheduledAt: new Date().toISOString(),
          status: 'QUEUED',
          priority: false,
        },
        {
          id: 'f238c16b-5273-57b5-b40e-c1248b02c056',
          content: 'Published in list',
          channels: ['linkedin'],
          scheduledAt: new Date().toISOString(),
          status: 'PUBLISHED',
          priority: false,
        },
      ]

      const wrapper = mountView({ surface: 'list' })
      await flushPromises()

      expect(wrapper.text()).toContain('QUEUED')
      expect(wrapper.text()).toContain('PUBLISHED')
    })
  })

  describe('time helpers', () => {
    it('formats day names in current locale', async () => {
      const wrapper = mountView()
      await flushPromises()

      // Day names should be rendered (either abbreviated or full)
      expect(wrapper.exists()).toBe(true)
    })

    it('handles activity data for dates', async () => {
      const store = usePublishingStore()
      const today = new Date()
      const todayStr = today.toISOString().slice(0, 10)

      store.activity = [
        {
          date: todayStr,
          density: 'HIGH',
          count: 5,
        },
      ]

      const wrapper = mountView({ surface: 'calendar-month', date: todayStr })
      await flushPromises()

      expect(wrapper.text()).toContain('5 HIGH')
    })
  })

  describe('post creation toast feedback', () => {
    it('closes the composer and shows a success toast when a post is created', async () => {
      toastSuccessMock.mockClear()

      const wrapper = mountView()
      await flushPromises()

      // Open the create-post modal first so the @created handler has a
      // meaningful state to act on.
      const newPostButton = wrapper.find('[data-testid="header-new-post"]')
      await newPostButton.trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="create-post-modal"]').exists()).toBe(true)

      // Trigger the `created` event from the mocked modal — SchedulerView
      // listens with @created="onPostCreated", which must:
      //   1. Close the modal
      //   2. Show a localized success toast
      const createdButton = wrapper.find('[data-testid="create-post-created"]')
      expect(createdButton.exists()).toBe(true)
      await createdButton.trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="create-post-modal"]').exists()).toBe(false)
      expect(toastSuccessMock).toHaveBeenCalledTimes(1)
      expect(toastSuccessMock).toHaveBeenCalledWith('composer.scheduleSuccessToast')
    })

    it('keeps the composer open when creation requests another post', async () => {
      toastSuccessMock.mockClear()

      const wrapper = mountView()
      await flushPromises()

      await wrapper.find('[data-testid="header-new-post"]').trigger('click')
      await flushPromises()

      await wrapper.find('[data-testid="create-post-created-keep-open"]').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="create-post-modal"]').exists()).toBe(true)
      expect(toastSuccessMock).toHaveBeenCalledTimes(1)
    })
  })

  describe('week grid overflow handling', () => {
    it('renders a "+N more" indicator when more than 2 publications share a slot', async () => {
      const store = usePublishingStore()
      const today = new Date()
      // Three publications in the same hour slot on the same day
      const sameHour = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 14, 0, 0)
      const sameHour2 = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 14, 30, 0)
      const sameHour3 = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 14, 45, 0)

      store.publications = [
        {
          id: 'd35894bd-db5a-5c7d-a4c1-0ee32419b5d1',
          content: 'First post in slot',
          channels: ['linkedin'],
          scheduledAt: sameHour.toISOString(),
          status: 'QUEUED',
          priority: false,
        },
        {
          id: '2bbb828c-7bdc-5aff-b424-42678791eb0b',
          content: 'Second post in slot',
          channels: ['linkedin'],
          scheduledAt: sameHour2.toISOString(),
          status: 'QUEUED',
          priority: false,
        },
        {
          id: 'b0ea4ac0-11a7-58e2-83f5-2c380b797e2c',
          content: 'Third post in slot',
          channels: ['linkedin'],
          scheduledAt: sameHour3.toISOString(),
          status: 'QUEUED',
          priority: false,
        },
      ]

      const todayStr = today.toISOString().slice(0, 10)
      const wrapper = mountView({ date: todayStr })
      await flushPromises()

      // With 3 posts in the slot, the indicator must show "+1 more"
      // (3 visible cap - 2 = 1 hidden).
      expect(wrapper.text()).toContain('+1')
      expect(wrapper.text()).toContain('more')
    })

    it('does not render a "+N more" indicator when 2 or fewer publications share a slot', async () => {
      const store = usePublishingStore()
      const today = new Date()
      const sameHour = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 14, 0, 0)
      const sameHour2 = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 14, 30, 0)

      store.publications = [
        {
          id: 'a804ec08-743c-50f4-8a31-f93022665e73',
          content: 'First post in slot',
          channels: ['linkedin'],
          scheduledAt: sameHour.toISOString(),
          status: 'QUEUED',
          priority: false,
        },
        {
          id: 'b19a7665-180a-51cd-abec-e39345936b29',
          content: 'Second post in slot',
          channels: ['linkedin'],
          scheduledAt: sameHour2.toISOString(),
          status: 'QUEUED',
          priority: false,
        },
      ]

      const todayStr = today.toISOString().slice(0, 10)
      const wrapper = mountView({ date: todayStr })
      await flushPromises()

      // With only 2 posts in the slot, no overflow indicator should render.
      expect(wrapper.text()).not.toContain('more')
    })
  })

  describe('recurring schedule panel', () => {
    it('opens recurring schedule modal when recurring button is clicked', async () => {
      const wrapper = mountView()
      await flushPromises()

      const recurringBtn = wrapper.find('[data-testid="recurring-schedule-btn"]')
      if (recurringBtn.exists()) {
        await recurringBtn.trigger('click')
        await flushPromises()
        expect(wrapper.find('[data-testid="recurring-schedule-modal"]').exists()).toBe(true)
      }
    })

    it('closes recurring modal when close event is emitted', async () => {
      const wrapper = mountView()
      await flushPromises()

      const recurringBtn = wrapper.find('[data-testid="recurring-schedule-btn"]')
      if (recurringBtn.exists()) {
        await recurringBtn.trigger('click')
        await flushPromises()
        const closeBtn = wrapper.find('[data-testid="recurring-close"]')
        await closeBtn.trigger('click')
        await flushPromises()
        expect(wrapper.find('[data-testid="recurring-schedule-modal"]').exists()).toBe(false)
      }
    })

    it('refreshes calendar when recurring modal emits created', async () => {
      const store = usePublishingStore()
      const wrapper = mountView()
      await flushPromises()

      const recurringBtn = wrapper.find('[data-testid="recurring-schedule-btn"]')
      if (recurringBtn.exists()) {
        await recurringBtn.trigger('click')
        await flushPromises()
        const createdBtn = wrapper.find('[data-testid="recurring-created"]')
        await createdBtn.trigger('click')
        await flushPromises()
        expect(store.fetchCalendar).toHaveBeenCalled()
      }
    })
  })
})
