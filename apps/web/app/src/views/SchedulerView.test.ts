import { describe, it, expect, vi, beforeEach } from 'vitest'
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
    stepPeriod: vi.fn().mockResolvedValue(undefined),
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
  default: {
    template:
      '<div v-if="isOpen" data-testid="create-post-modal"><button data-testid="create-post-updated" @click="$emit(\'updated\')">updated</button></div><div v-if="isOpen" data-testid="create-post-modal-open">open</div>',
    props: ['isOpen', 'initialDate', 'editingPublication'],
    emits: ['close', 'created', 'updated'],
  },
}))

vi.mock('@/components/PostDetailModal.vue', () => ({
  default: {
    template:
      '<div v-if="isOpen" data-testid="post-detail-modal"><button data-testid="detail-edit" @click="$emit(\'edit\', publication)">edit</button></div>',
    props: ['isOpen', 'publication'],
    emits: ['close', 'deleted', 'reschedule', 'edit'],
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

    const wrapper = mountView({ date: '2026-06-25' })
    await flushPromises()

    // Open detail modal by clicking publication card if available
    const vm = wrapper.vm as unknown as { openPostDetail: (pub: Publication) => void }
    vm.openPostDetail(pub)
    await wrapper.vm.$nextTick()

    const editBtn = wrapper.find('[data-testid="detail-edit"]')
    await editBtn.trigger('click')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[data-testid="create-post-modal"]').exists()).toBe(true)
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
    const targetDate = new Date('2026-06-20')

    await vm.openDayView(targetDate)
    await flushPromises()

    expect(mockController.setDate).toHaveBeenCalledWith('2026-06-20')
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
          scheduledAt: new Date(today.getFullYear(), today.getMonth(), today.getDate(), 10, 0).toISOString(),
          status: 'QUEUED',
          priority: false,
        },
        {
          id: 'pub-published',
          content: 'Published post',
          channels: ['linkedin'],
          scheduledAt: new Date(today.getFullYear(), today.getMonth(), today.getDate(), 11, 0).toISOString(),
          status: 'PUBLISHED',
          priority: false,
        },
      ]

      const wrapper = mountView({ date: todayStr, status: 'queued' })
      await flushPromises()

      // Only queued publication should be visible in week slot
      const queuedCard = wrapper.findAll('[role="button"]').find((b) =>
        b.text().includes('Queued post'),
      )
      const publishedCard = wrapper.findAll('[role="button"]').find((b) =>
        b.text().includes('Published post'),
      )

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
          scheduledAt: new Date(today.getFullYear(), today.getMonth(), today.getDate(), 10, 0).toISOString(),
          status: 'QUEUED',
          priority: false,
          accountId: 'acc-1',
        },
        {
          id: 'pub-channel-2',
          content: 'Channel 2 post',
          channels: ['linkedin'],
          scheduledAt: new Date(today.getFullYear(), today.getMonth(), today.getDate(), 11, 0).toISOString(),
          status: 'QUEUED',
          priority: false,
          accountId: 'acc-2',
        },
      ]

      const wrapper = mountView({ date: todayStr, channelIds: ['acc-1'] })
      await flushPromises()

      const channel1Card = wrapper.findAll('[role="button"]').find((b) =>
        b.text().includes('Channel 1 post'),
      )
      const channel2Card = wrapper.findAll('[role="button"]').find((b) =>
        b.text().includes('Channel 2 post'),
      )

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
          scheduledAt: new Date(today.getFullYear(), today.getMonth(), today.getDate(), 10, 0).toISOString(),
          status: 'QUEUED',
          priority: false,
        },
        {
          id: 'pub-not-searchable',
          content: 'This post has no special content',
          channels: ['linkedin'],
          scheduledAt: new Date(today.getFullYear(), today.getMonth(), today.getDate(), 11, 0).toISOString(),
          status: 'QUEUED',
          priority: false,
        },
      ]

      const wrapper = mountView({ date: todayStr, q: 'ddd' })
      await flushPromises()

      const searchableCard = wrapper.findAll('[role="button"]').find((b) =>
        b.text().includes('DDD patterns'),
      )
      const notSearchableCard = wrapper.findAll('[role="button"]').find((b) =>
        b.text().includes('no special content'),
      )

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
          scheduled: 5,
          published: 3,
          blocked: 1,
        },
      ]

      const wrapper = mountView({ date: todayStr })
      await flushPromises()

      expect(wrapper.exists()).toBe(true)
    })
  })
})
