import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'
import SchedulerView from './SchedulerView.vue'
import { toast } from 'vue-sonner'

const mockController = {
  state: ref({
    surface: 'calendar-week' as const,
    view: 'week' as const,
    date: '2026-09-28',
    status: 'all' as const,
    timezone: 'Europe/Madrid',
    q: '',
    channelIds: [] as string[],
    postId: null,
  }),
  needsCanonicalization: ref(false),
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

const isMobile = ref(true)
const startAppTour = vi.fn()
let mockHasNoChannels = false

vi.mock('@modules/publishing/application/useCalendarUrl', () => ({
  useCalendarUrl: () => mockController,
}))

vi.mock('@/lib/app-tour', () => ({
  startAppTour: () => startAppTour(),
}))

vi.mock('@vueuse/core', () => ({
  useMediaQuery: () => isMobile,
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    query: {},
  }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: { value: 'en' },
  }),
}))

vi.mock('@modules/publishing/infrastructure/publishing.store', () => ({
  usePublishingStore: () => ({
    fetchCalendar: vi.fn().mockResolvedValue(undefined),
    fetchRecurringSchedules: vi.fn().mockResolvedValue([]),
    subscribePublicationEvents: vi.fn().mockResolvedValue(null),
    unsubscribePublicationEvents: vi.fn(),
    connectLinkedInPersonalProfile: vi.fn().mockResolvedValue(undefined),
    deletePost: vi.fn().mockResolvedValue(undefined),
    publications: [],
    recurringSchedules: [],
    hasReconnectRequiredChannels: false,
    get hasNoChannels() {
      return mockHasNoChannels
    },
  }),
}))

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({ isAuthenticated: true }),
}))

vi.mock('@modules/workspace/infrastructure/workspace.store', () => ({
  useWorkspaceStore: () => ({
    activeWorkspaceId: 'workspace-1',
    setActiveWorkspaceId: vi.fn(),
  }),
}))

vi.mock('@modules/publishing/application/useReactiveClock', () => ({
  useReactiveClock: () => ({ now: ref(new Date()), stop: vi.fn() }),
}))

vi.mock('@modules/publishing/application/useCalendarRevalidation', () => ({
  useCalendarRevalidation: () => ({ request: vi.fn(), setVisible: vi.fn() }),
}))

vi.mock('@modules/publishing/application/usePublicationEventReconnect', () => ({
  usePublicationEventReconnect: () => ({ start: vi.fn(), stop: vi.fn() }),
}))

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  createApiFetch: () => async () => ({}),
  refreshSession: vi.fn().mockResolvedValue(null),
  getCurrentUserProfile: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  register: vi.fn(),
  logoutSession: vi.fn(),
}))

vi.mock('vue-sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('@modules/publishing/presentation/components/CreatePostModal.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@modules/publishing/presentation/components/PostDetailModal.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@modules/publishing/presentation/components/RecurringScheduleModal.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@modules/publishing/presentation/components/BulkImportModal.vue', () => ({
  default: { template: '<div />' },
}))

vi.mock('@modules/publishing/presentation/components/mobile/MobileSchedulerShell.vue', () => ({
  default: {
    name: 'MobileSchedulerShell',
    props: [
      'title',
      'view',
      'periodLabel',
      'days',
      'hourSlots',
      'status',
      'timezone',
      'channelIds',
      'q',
      'hasNoChannels',
    ],
    emits: [
      'new-post',
      'prev',
      'next',
      'today',
      'change:view',
      'change:filter',
      'open-bulk-import',
      'start-tour',
      'open-post-detail',
      'open-new-post',
    ],
    template: `
      <div data-testid="scheduler-mobile-shell">
        <button data-testid="mobile-new-post" @click="$emit('new-post')">New Post</button>
        <button data-testid="prev-period" @click="$emit('prev')">Prev</button>
        <button data-testid="next-period" @click="$emit('next')">Next</button>
        <button data-testid="today-period" @click="$emit('today')">Today</button>
        <button data-testid="day-view-button" @click="$emit('change:view', 'day')">Day</button>
        <button data-testid="open-bulk-import" @click="$emit('open-bulk-import')">Bulk Import</button>
        <button data-testid="start-tour-btn" @click="$emit('start-tour')">Tour</button>
        <button data-testid="mobile-filters-trigger" @click="$emit('change:filter', { status: 'queued' })">Filters</button>
      </div>
    `,
  },
}))

vi.mock('@/components/ui/dropdown-menu', () => ({
  DropdownMenu: { template: '<div><slot /></div>' },
  DropdownMenuTrigger: { template: '<div><slot /></div>' },
  DropdownMenuContent: { template: '<div><slot /></div>' },
  DropdownMenuItem: { template: '<div><slot /></div>' },
}))

vi.mock('@/components/ui/sheet', () => ({
  Sheet: { template: '<div><slot /></div>' },
  SheetClose: { template: '<div><slot /></div>' },
  SheetContent: { template: '<div><slot /></div>' },
  SheetFooter: { template: '<header><slot /></header>' },
  SheetHeader: { template: '<header><slot /></header>' },
  SheetTitle: { template: '<h2><slot /></h2>' },
}))

describe('SchedulerView mobile branch', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    isMobile.value = true
    mockHasNoChannels = false
  })

  afterEach(() => {
    vi.clearAllMocks()
    isMobile.value = false
  })

  it('renders the mobile shell and hides the desktop workspace on narrow viewports', async () => {
    const wrapper = mount(SchedulerView, {
      global: { mocks: { $t: (key: string) => key } },
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="scheduler-mobile-shell"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="scheduler-workspace"]').exists()).toBe(false)
  })

  it('defaults mobile view to 3-days when view query is omitted', async () => {
    mount(SchedulerView, {
      global: { mocks: { $t: (key: string) => key } },
    })
    await flushPromises()

    expect(mockController.setView).toHaveBeenCalledWith('3-days')
  })

  it('routes New Post, prev/next/today, and view change events to the URL controller', async () => {
    const wrapper = mount(SchedulerView, {
      global: { mocks: { $t: (key: string) => key } },
    })
    await flushPromises()

    await wrapper.get('[data-testid="mobile-new-post"]').trigger('click')
    await wrapper.get('[data-testid="prev-period"]').trigger('click')
    await wrapper.get('[data-testid="next-period"]').trigger('click')
    await wrapper.get('[data-testid="today-period"]').trigger('click')
    expect(mockController.stepPeriod).toHaveBeenCalledWith('backward')
    expect(mockController.stepPeriod).toHaveBeenCalledWith('forward')

    await wrapper.get('[data-testid="day-view-button"]').trigger('click')
    await flushPromises()
    expect(mockController.setView).toHaveBeenCalledWith('day')

    await wrapper.get('[data-testid="open-bulk-import"]').trigger('click')
    await wrapper.get('[data-testid="start-tour-btn"]').trigger('click')
    expect(startAppTour).toHaveBeenCalled()
  })

  it('shows warning toast when user attempts to create post with no channels connected', async () => {
    mockHasNoChannels = true
    const wrapper = mount(SchedulerView, {
      global: { mocks: { $t: (key: string) => key } },
    })
    await flushPromises()

    await wrapper.get('[data-testid="mobile-new-post"]').trigger('click')
    expect(toast.warning).toHaveBeenCalledWith('scheduler.noChannelTitle')
  })

  it('routes the mobile filter change event through url.setFilters', async () => {
    const wrapper = mount(SchedulerView, {
      global: { mocks: { $t: (key: string) => key } },
    })
    await flushPromises()

    await wrapper.get('[data-testid="mobile-filters-trigger"]').trigger('click')
    await flushPromises()
    expect(mockController.setFilters).toHaveBeenCalledWith({ status: 'queued' })
  })
})
