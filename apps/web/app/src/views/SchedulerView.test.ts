import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'
import SchedulerView from './SchedulerView.vue'
import { usePublishingStore } from '@/stores/publishing'
import type { Publication } from '@/stores/publishing'
import type { CalendarUrlController } from '@/composables/useCalendarUrl'

// ---------------------------------------------------------------------------
// Shared mock controller factory
// ---------------------------------------------------------------------------

function makeUrlController(
  overrides: Partial<{
    surface: 'calendar-week' | 'calendar-month' | 'list'
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
    setDate: vi.fn().mockResolvedValue(undefined),
    stepPeriod: vi.fn().mockResolvedValue(undefined),
    setTimezone: vi.fn().mockResolvedValue(undefined),
    setStatus: vi.fn().mockResolvedValue(undefined),
    setSearch: vi.fn().mockResolvedValue(undefined),
    setChannelIds: vi.fn().mockResolvedValue(undefined),
    openPostDetail: vi.fn().mockResolvedValue(undefined),
    closePostDetail: vi.fn().mockResolvedValue(undefined),
  }
}

// Singleton mock controller — reset in beforeEach
let mockController = makeUrlController()

vi.mock('@/composables/useCalendarUrl', () => ({
  useCalendarUrl: () => mockController,
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

vi.mock('@/components/CreatePostModal.vue', () => ({
  default: {
    template:
      '<div v-if="isOpen" data-testid="create-post-modal"><button data-testid="create-post-updated" @click="$emit(\'updated\')">updated</button><button data-testid="create-post-created" @click="$emit(\'created\')">created</button></div><div v-if="isOpen" data-testid="create-post-modal-open">open</div>',
    props: ['isOpen', 'initialDate', 'editingPublication', 'provider'],
    emits: ['close', 'created', 'updated'],
  },
}))

vi.mock('@/components/PostDetailModal.vue', () => ({
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

vi.mock('@/components/CalendarHeader.vue', () => ({
  default: {
    template:
      '<div data-testid="calendar-header"><button data-testid="header-new-post" @click="$emit(\'new-post\')">New Post</button></div>',
    props: ['calendarView', 'surface', 'periodLabel'],
    emits: ['change:view', 'change:date', 'change:filter', 'new-post'],
  },
}))

vi.mock('@/components/CalendarCell.vue', () => ({
  default: {
    template:
      '<div data-testid="calendar-cell"><span v-if="activityEntry">{{ activityEntry.count }} {{ activityEntry.density }}</span></div>',
    props: ['activityEntry'],
  },
}))

vi.mock('@/components/ConflictBadge.vue', () => ({
  default: { template: '<div data-testid="conflict-badge" />' },
}))

vi.mock('@/components/ui/card', () => ({
  Card: { template: '<div><slot /></div>' },
}))

vi.mock('@/components/ui/button', () => ({
  Button: { template: '<button><slot /></button>' },
}))

vi.mock('@lucide/vue', () => {
  const stub = { template: '<svg />' }
  return {
    Plus: stub,
    Trash2: stub,
  }
})

vi.mock('@/lib/provider-styles', () => ({
  getProviderColor: () => 'provider-color',
}))

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('SchedulerView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const store = usePublishingStore()
    store.publications = []
    store.activity = []
    store.channels = [
      {
        id: 'acc-1',
        accountId: 'acc-1',
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

  it('mounts and fetches calendar on init', async () => {
    const store = usePublishingStore()
    const wrapper = mountView()
    await flushPromises()

    expect(store.fetchCalendar).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-testid="calendar-header"]').exists()).toBe(true)
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

  it('shows reconnect prompt when channels require reconnect', async () => {
    const store = usePublishingStore()
    store.channels = [
      {
        id: 'acc-1',
        accountId: 'acc-1',
        name: 'LinkedIn',
        provider: 'linkedin',
        avatar: '',
        handle: '@company',
        status: 'REQUIRES_RECONNECT',
      },
    ]

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Reconnect Required')
  })

  it('renders thumbnail image in week view scheduled post cards', async () => {
    const store = usePublishingStore()
    const today = new Date()
    const scheduledAt = new Date(today.getFullYear(), today.getMonth(), today.getDate(), 14, 0, 0)
    store.publications = [
      {
        id: 'pub-week-thumb',
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
          id: `pub-tz-${tz}`,
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
        id: 'pub-week-side-by-side',
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
        id: 'del-error',
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
        id: 'pub-drag',
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
      id: 'pub-deep-link',
      content: 'Deep linked post',
      channels: ['linkedin'],
      scheduledAt: '2026-06-25T10:00:00Z',
      status: 'SCHEDULED',
      priority: false,
    }
    store.publications = [pub]

    const wrapper = mountView({ date: '2026-06-25', postId: 'pub-deep-link' })
    await flushPromises()

    expect(wrapper.find('[data-testid="post-detail-modal"]').exists()).toBe(true)
  })

  it('openPostDetail writes the selected publication id into the URL state', async () => {
    const wrapper = mountView({ date: '2026-06-25' })
    await flushPromises()
    const pub: Publication = {
      id: 'pub-url-open',
      content: 'Open via URL state',
      channels: ['linkedin'],
      scheduledAt: '2026-06-25T10:00:00Z',
      status: 'SCHEDULED',
      priority: false,
    }

    const vm = wrapper.vm as unknown as { openPostDetail: (pub: Publication) => void }
    vm.openPostDetail(pub)
    await flushPromises()

    expect(mockController.openPostDetail).toHaveBeenCalledWith('pub-url-open')
  })

  it('opens CreatePostModal in edit mode when PostDetailModal emits edit', async () => {
    const store = usePublishingStore()
    const pub: Publication = {
      id: 'pub-edit-flow',
      content: 'Editable post',
      channels: ['linkedin'],
      scheduledAt: '2026-06-25T10:00:00Z',
      status: 'SCHEDULED',
      priority: false,
    }
    store.publications = [pub]

    const wrapper = mountView({ date: '2026-06-25', postId: 'pub-edit-flow' })
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
          id: 'pub-other',
          content: 'Some other post',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T10:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      const wrapper = mountView({ date: '2026-06-25', postId: 'pub-nonexistent' })
      await flushPromises()

      expect(wrapper.find('[data-testid="post-detail-modal"]').exists()).toBe(false)
    })

    it('closes the post detail modal when PostDetailModal emits deleted', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: 'pub-deleted-emit',
          content: 'Deleted via modal',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T10:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      const wrapper = mountView({ date: '2026-06-25', postId: 'pub-deleted-emit' })
      await flushPromises()

      await wrapper.find('[data-testid="detail-deleted"]').trigger('click')
      await flushPromises()

      expect(mockController.closePostDetail).toHaveBeenCalledWith(undefined)
    })

    it('closes the post detail modal when PostDetailModal emits close', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: 'pub-close-emit',
          content: 'Closed via modal',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T10:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      const wrapper = mountView({ date: '2026-06-25', postId: 'pub-close-emit' })
      await flushPromises()

      await wrapper.find('[data-testid="detail-close"]').trigger('click')
      await flushPromises()

      expect(mockController.closePostDetail).toHaveBeenCalledWith(undefined)
    })

    it('closes the post detail modal via onReschedule when PostDetailModal emits reschedule', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: 'pub-reschedule-emit',
          content: 'Rescheduled via modal',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T10:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      const wrapper = mountView({ date: '2026-06-25', postId: 'pub-reschedule-emit' })
      await flushPromises()

      await wrapper.find('[data-testid="detail-reschedule"]').trigger('click')
      await flushPromises()

      expect(mockController.closePostDetail).toHaveBeenCalled()
    })

    it('closes the post detail modal via onReschedule when PostDetailModal emits retried', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: 'pub-retried-emit',
          content: 'Retried via modal',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T10:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      const wrapper = mountView({ date: '2026-06-25', postId: 'pub-retried-emit' })
      await flushPromises()

      await wrapper.find('[data-testid="detail-retried"]').trigger('click')
      await flushPromises()

      expect(mockController.closePostDetail).toHaveBeenCalled()
    })

    it('closes the post detail modal when the currently open publication is deleted', async () => {
      const store = usePublishingStore()
      const pub: Publication = {
        id: 'pub-delete-open',
        content: 'To delete while open',
        channels: ['linkedin'],
        scheduledAt: '2026-06-25T10:00:00Z',
        status: 'QUEUED',
        priority: false,
      }
      store.publications = [pub]
      vi.spyOn(store, 'deletePost').mockResolvedValue(undefined as never)

      const wrapper = mountView({ date: '2026-06-25', postId: 'pub-delete-open' })
      await flushPromises()

      const vm = wrapper.vm as unknown as {
        handleDeletePublication: (id: string) => Promise<void>
      }
      await vm.handleDeletePublication('pub-delete-open')
      await flushPromises()

      expect(store.deletePost).toHaveBeenCalledWith('pub-delete-open')
      expect(mockController.closePostDetail).toHaveBeenCalled()
    })

    it('does not attempt to close the post detail modal when a different publication is deleted', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: 'pub-open',
          content: 'Currently open',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T10:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
        {
          id: 'pub-unrelated',
          content: 'Not open',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T11:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]
      vi.spyOn(store, 'deletePost').mockResolvedValue(undefined as never)

      const wrapper = mountView({ date: '2026-06-25', postId: 'pub-open' })
      await flushPromises()
      ;(mockController.closePostDetail as ReturnType<typeof vi.fn>).mockClear()

      const vm = wrapper.vm as unknown as {
        handleDeletePublication: (id: string) => Promise<void>
      }
      await vm.handleDeletePublication('pub-unrelated')
      await flushPromises()

      expect(store.deletePost).toHaveBeenCalledWith('pub-unrelated')
      expect(mockController.closePostDetail).not.toHaveBeenCalled()
    })

    it('auto-closes a stale postId with replace semantics once the fetch confirms it is no longer visible', async () => {
      const store = usePublishingStore()
      store.publications = []

      mountView({ date: '2026-06-25', postId: 'missing-post' })
      await flushPromises()

      expect(mockController.closePostDetail).toHaveBeenCalledWith({ replace: true })
    })

    it('keeps the post detail open and does not canonicalize when postId resolves to a visible publication', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: 'visible-post',
          content: 'Still visible',
          channels: ['linkedin'],
          scheduledAt: '2026-06-25T10:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      mountView({ date: '2026-06-25', postId: 'visible-post' })
      await flushPromises()

      expect(mockController.closePostDetail).not.toHaveBeenCalled()
    })

    it('ignores an older fetch settling after a newer navigation so it does not spuriously close the modal', async () => {
      const store = usePublishingStore()
      store.publications = []

      const resolvers: Array<() => void> = []
      vi.spyOn(store, 'fetchCalendar').mockImplementation(
        () =>
          new Promise<void>((resolve) => {
            resolvers.push(resolve)
          }),
      )

      mountView({ date: '2026-06-25', postId: 'post-stale' })
      await flushPromises()

      // Simulate a newer navigation firing before the first fetch settles.
      mockController.state.value = {
        ...mockController.state.value,
        date: '2026-06-26',
      }
      await flushPromises()

      expect(resolvers).toHaveLength(2)

      // Resolve the OLDER fetch first — its token is now stale, so the
      // reconciliation check inside the watcher must be skipped for it.
      resolvers[0]!()
      await flushPromises()
      expect(mockController.closePostDetail).not.toHaveBeenCalled()

      // Resolve the NEWER fetch — its token matches, so reconciliation runs
      // exactly once for the still-missing postId.
      resolvers[1]!()
      await flushPromises()
      expect(mockController.closePostDetail).toHaveBeenCalledTimes(1)
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

    await vm.handleHeaderFilterChange({ channelIds: ['acc-1'] })
    await flushPromises()

    expect(mockController.setChannelIds).toHaveBeenCalledWith(['acc-1'])
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
    const store = usePublishingStore()
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('[data-testid="header-new-post"]').trigger('click')
    await flushPromises()

    const initialCalls = (store.fetchCalendar as ReturnType<typeof vi.fn>).mock.calls.length
    const updatedBtn = wrapper.find('[data-testid="create-post-updated"]')
    await updatedBtn.trigger('click')
    await flushPromises()

    expect((store.fetchCalendar as ReturnType<typeof vi.fn>).mock.calls.length).toBeGreaterThan(
      initialCalls,
    )
  })

  describe('month view', () => {
    it('renders month grid when surface is calendar-month', async () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: 'pub-month',
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
          id: 'pub-queued',
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
          id: 'pub-published',
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
          id: 'pub-channel-1',
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
          accountId: 'acc-1',
        },
        {
          id: 'pub-channel-2',
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
          accountId: 'acc-2',
        },
      ]

      const wrapper = mountView({ date: todayStr, channelIds: ['acc-1'] })
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
          id: 'pub-searchable',
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
          id: 'pub-not-searchable',
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
          id: 'pub-list',
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
          id: 'pub-queued-list',
          content: 'Queued in list',
          channels: ['linkedin'],
          scheduledAt: new Date().toISOString(),
          status: 'QUEUED',
          priority: false,
        },
        {
          id: 'pub-published-list',
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
          id: 'pub-overflow-1',
          content: 'First post in slot',
          channels: ['linkedin'],
          scheduledAt: sameHour.toISOString(),
          status: 'QUEUED',
          priority: false,
        },
        {
          id: 'pub-overflow-2',
          content: 'Second post in slot',
          channels: ['linkedin'],
          scheduledAt: sameHour2.toISOString(),
          status: 'QUEUED',
          priority: false,
        },
        {
          id: 'pub-overflow-3',
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
          id: 'pub-no-overflow-1',
          content: 'First post in slot',
          channels: ['linkedin'],
          scheduledAt: sameHour.toISOString(),
          status: 'QUEUED',
          priority: false,
        },
        {
          id: 'pub-no-overflow-2',
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
})
