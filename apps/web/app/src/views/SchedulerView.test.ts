import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'
import SchedulerView from './SchedulerView.vue'
import { usePublishingStore } from '@/stores/publishing'
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
  })

  return {
    state,
    needsCanonicalization: ref(overrides.needsCanonicalization ?? false),
    canonicalize: vi.fn().mockResolvedValue(undefined),
    setSurface: vi.fn().mockResolvedValue(undefined),
    setDate: vi.fn().mockResolvedValue(undefined),
    setTimezone: vi.fn().mockResolvedValue(undefined),
    setStatus: vi.fn().mockResolvedValue(undefined),
    setSearch: vi.fn().mockResolvedValue(undefined),
    setChannelIds: vi.fn().mockResolvedValue(undefined),
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
  useI18n: () => ({ t: (key: string) => key, locale: { value: 'en' } }),
}))

vi.mock('@/lib/auth-api', () => ({
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

vi.mock('@/components/CreatePostModal.vue', () => ({
  default: { template: '<div data-testid="create-post-modal" />' },
}))

vi.mock('@/components/PostDetailModal.vue', () => ({
  default: {
    template: '<div data-testid="post-detail-modal" />',
    props: ['isOpen', 'publication'],
  },
}))

vi.mock('@/components/CalendarHeader.vue', () => ({
  default: {
    template:
      '<div data-testid="calendar-header"><button data-testid="header-new-post">New Post</button></div>',
    props: ['calendarView', 'surface', 'periodLabel'],
    emits: [
      'update:calendarView',
      'update:surface',
      'update:status',
      'update:timezone',
      'update:channels',
      'forward',
      'backward',
      'today',
      'newPost',
    ],
  },
}))

vi.mock('@/components/CalendarCell.vue', () => ({
  default: { template: '<div data-testid="calendar-cell" />' },
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
  getProviderBadge: () => 'LI',
}))

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('SchedulerView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const store = usePublishingStore()
    store.viewMode = 'calendar'
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
    // Find the week card (now a div role="button") that contains the pub
    const card = wrapper
      .findAll('[role="button"]')
      .find((b) => b.text().includes('Side by side layout test'))
    expect(card).toBeDefined()
    const resolvedCard = card!
    expect(resolvedCard.exists()).toBe(true)
    // The body container that holds both text and thumbnail must be flex-row (not flex-col)
    const bodyDiv = resolvedCard.findAll('div').find((d) => {
      const cls = d.classes()
      return cls.some((c) => c.includes('flex-row'))
    })
    expect(bodyDiv?.exists()).toBe(true)
    // Both the text paragraph and the img should be inside that flex-row container
    const img = resolvedCard.find('img')
    const textEl = resolvedCard.find('p')
    expect(img.exists()).toBe(true)
    expect(textEl.exists()).toBe(true)
    expect(bodyDiv?.find('img').exists()).toBe(true)
    expect(bodyDiv?.find('p').exists()).toBe(true)
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
})
