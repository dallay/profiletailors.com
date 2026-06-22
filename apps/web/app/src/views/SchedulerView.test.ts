import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SchedulerView from './SchedulerView.vue'
import { usePublishingStore } from '@/stores/publishing'

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
    template: '<div data-testid="calendar-header" @click="$emit(\'new-post\')"></div>',
    props: ['calendarView', 'periodLabel'],
    emits: ['update:calendarView', 'forward', 'backward', 'today', 'new-post'],
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
  })

  function mountView() {
    return mount(SchedulerView, {
      global: {
        mocks: { $t: (key: string) => key },
      },
    })
  }

  it('mounts and fetches calendar on init', async () => {
    const store = usePublishingStore()

    mountView()
    await flushPromises()

    expect(store.fetchCalendar).toHaveBeenCalledTimes(1)
  })

  it('opens the create post modal from calendar header new-post event', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('[data-testid="calendar-header"]').trigger('click')

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
})
