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

  function mountHeader(overrides: Record<string, unknown> = {}) {
    return mount(CalendarHeader, {
      props: {
        calendarView: 'week',
        surface: 'calendar-week',
        periodLabel: 'Jun 8 – 14, 2026',
        ...overrides,
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

  it('emits update calendar view when month and week are clicked', async () => {
    const wrapper = mountHeader()
    const buttons = wrapper.findAll('button')

    await buttons[0]?.trigger('click') // month
    await buttons[1]?.trigger('click') // week

    expect(wrapper.emitted('update:calendarView')).toEqual([['month'], ['week']])
  })

  it('emits surface=list when list toggle is clicked', async () => {
    const wrapper = mountHeader()
    const buttons = wrapper.findAll('button')

    // List button is the second in the surface toggle group
    await buttons[3]?.trigger('click') // list toggle

    expect(wrapper.emitted('update:surface')).toEqual([['list']])
  })

  it('emits surface=calendar-month when calendar toggle is clicked from list mode', async () => {
    // When surface='list', only the calendar toggle button renders (no month/week split)
    // Clicking it emits the current calendarView (here 'month') as the calendar surface.
    const wrapper = mountHeader({ surface: 'list', calendarView: 'month' })
    const calendarToggle = wrapper
      .findAll('button')
      .find((button) => button.text().includes('scheduler.calendar'))

    await calendarToggle?.trigger('click')

    expect(wrapper.emitted('update:surface')).toEqual([['calendar-month']])
  })

  it('emits navigation and action events', async () => {
    const wrapper = mountHeader()
    const buttons = wrapper.findAll('button')

    await buttons[5]?.trigger('click') // backward
    await buttons[6]?.trigger('click') // forward
    await buttons[7]?.trigger('click') // today
    await buttons[4]?.trigger('click') // new post

    expect(wrapper.emitted('backward')).toHaveLength(1)
    expect(wrapper.emitted('forward')).toHaveLength(1)
    expect(wrapper.emitted('today')).toHaveLength(1)
    expect(wrapper.emitted('newPost')).toHaveLength(1)
  })

  it('disables new post button when there are no channels', () => {
    const publishingStore = usePublishingStore()
    publishingStore.channels = []

    const wrapper = mountHeader()
    const buttons = wrapper.findAll('button')
    const newPostButton = buttons[4]

    expect(newPostButton?.attributes('disabled')).toBeDefined()
  })
})
