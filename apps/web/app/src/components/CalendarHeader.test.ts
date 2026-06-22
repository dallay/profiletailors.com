import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { usePublishingStore } from '@/stores/publishing'
import CalendarHeader from './CalendarHeader.vue'

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

vi.mock('@/components/ui/button', () => ({
  Button: { template: '<button><slot /></button>' },
}))

vi.mock('@lucide/vue', () => {
  const stub = { template: '<svg />' }
  return {
    Bookmark: stub,
    CalendarDays: stub,
    ChevronDown: stub,
    ChevronLeft: stub,
    ChevronRight: stub,
    Clock: stub,
    Filter: stub,
    Globe: stub,
    Plus: stub,
  }
})

describe('CalendarHeader', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const publishingStore = usePublishingStore()
    publishingStore.viewMode = 'calendar'
    publishingStore.channels = [
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
  })

  function mountHeader() {
    return mount(CalendarHeader, {
      props: {
        calendarView: 'week',
        periodLabel: 'Jun 8 – 14, 2026',
      },
      global: {
        mocks: {
          $t: (key: string) => key,
        },
      },
    })
  }

  it('renders the period label and calendar mode controls', () => {
    const wrapper = mountHeader()

    expect(wrapper.text()).toContain('Jun 8 – 14, 2026')
    expect(wrapper.text()).toContain('scheduler.calendar')
    expect(wrapper.text()).toContain('scheduler.list')
    expect(wrapper.text()).toContain('scheduler.weekView')
  })

  it('emits update calendar view when month week and day are clicked', async () => {
    const wrapper = mountHeader()
    const buttons = wrapper.findAll('button')

    await buttons[0]?.trigger('click') // month
    await buttons[1]?.trigger('click') // week
    await buttons[2]?.trigger('click') // day

    expect(wrapper.emitted('update:calendarView')).toEqual([['month'], ['week'], ['day']])
  })

  it('switches publishing view mode between calendar and list', async () => {
    const wrapper = mountHeader()
    const publishingStore = usePublishingStore()
    const buttons = wrapper.findAll('button')

    await buttons[4]?.trigger('click') // list toggle
    expect(publishingStore.viewMode).toBe('list')

    await buttons[3]?.trigger('click') // calendar toggle
    expect(publishingStore.viewMode).toBe('calendar')
  })

  it('emits navigation and action events', async () => {
    const wrapper = mountHeader()
    const buttons = wrapper.findAll('button')

    await buttons[5]?.trigger('click') // new post
    await buttons[6]?.trigger('click') // backward
    await buttons[7]?.trigger('click') // forward
    await buttons[8]?.trigger('click') // today

    expect(wrapper.emitted('newPost')).toHaveLength(1)
    expect(wrapper.emitted('backward')).toHaveLength(1)
    expect(wrapper.emitted('forward')).toHaveLength(1)
    expect(wrapper.emitted('today')).toHaveLength(1)
  })

  it('disables new post button when there are no channels', () => {
    const publishingStore = usePublishingStore()
    publishingStore.channels = []

    const wrapper = mountHeader()
    const buttons = wrapper.findAll('button')
    const newPostButton = buttons[5]

    expect(newPostButton?.attributes('disabled')).toBeDefined()
  })
})
