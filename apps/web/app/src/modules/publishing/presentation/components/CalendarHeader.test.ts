import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'
import { Clock, Check, Ban, Folder } from '@lucide/vue'
import CalendarHeader from './CalendarHeader.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key, locale: { value: 'en' } }),
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

vi.mock('@profiletailors/vue-ui/shell/button', () => ({
  Button: { template: '<button><slot /></button>' },
}))

vi.mock('@lucide/vue', () => {
  const stub = { template: '<svg />' }
  return {
    Ban: stub,
    Bookmark: stub,
    CalendarDays: stub,
    Check: stub,
    ChevronDown: stub,
    ChevronLeft: stub,
    ChevronRight: stub,
    Clock: stub,
    Filter: stub,
    Folder: stub,
    Globe: stub,
    Plus: stub,
    Radio: stub,
  }
})

describe('CalendarHeader', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const publishingStore = usePublishingStore()
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
        timezone: 'UTC',
        status: 'all',
        channelIds: [],
        ...overrides,
      },
      global: {
        mocks: {
          $t: (key: string) => key,
        },
      },
    })
  }

  function buttonByLabel(wrapper: ReturnType<typeof mountHeader>, label: string) {
    const button = wrapper
      .findAll('button')
      .find((candidate) => (candidate.attributes('aria-label') ?? candidate.text()) === label)
    if (!button) throw new Error(`Missing button: ${label}`)
    return button
  }

  it('renders the period label and calendar mode controls', () => {
    const wrapper = mountHeader()

    expect(wrapper.text()).toContain('Jun 8 – 14, 2026')
    expect(wrapper.text()).toContain('scheduler.calendar')
    expect(wrapper.text()).toContain('scheduler.list')
    expect(wrapper.text()).toContain('scheduler.viewWeek')
    expect(wrapper.text()).toContain('scheduler.viewThreeDays')
  })

  it.each([
    { label: 'scheduler.viewDay', expectedView: 'day', scenario: 'day' },
    { label: 'scheduler.viewThreeDays', expectedView: '3-days', scenario: 'three days' },
    { label: 'scheduler.viewWeek', expectedView: 'week', scenario: 'week' },
    { label: 'scheduler.viewMonth', expectedView: 'month', scenario: 'month' },
  ])(
    'emits change:period with $expectedView when $scenario is clicked',
    async ({ label, expectedView }) => {
      const wrapper = mountHeader()
      await buttonByLabel(wrapper, label).trigger('click')

      expect(wrapper.emitted('change:period')).toEqual([[expectedView]])
    },
  )

  it('emits change:view=calendar-month when calendar toggle is clicked from list mode', async () => {
    const wrapper = mountHeader({ surface: 'list', calendarView: 'month' })
    await buttonByLabel(wrapper, 'scheduler.calendar').trigger('click')

    expect(wrapper.emitted('change:format')).toEqual([['calendar-month']])
  })

  it('emits navigation and action events', async () => {
    const wrapper = mountHeader()
    await buttonByLabel(wrapper, 'scheduler.previousPeriod').trigger('click')
    await buttonByLabel(wrapper, 'scheduler.nextPeriod').trigger('click')
    await buttonByLabel(wrapper, 'scheduler.today').trigger('click')
    await buttonByLabel(wrapper, 'scheduler.newPost').trigger('click')

    expect(wrapper.emitted('change:date')).toHaveLength(3)
    expect(wrapper.emitted('change:date')).toEqual([['backward'], ['forward'], ['today']])
    expect(wrapper.emitted('newPost')).toHaveLength(1)
  })

  it('disables new post button when there are no channels', () => {
    const publishingStore = usePublishingStore()
    publishingStore.channels = []

    const wrapper = mountHeader()
    const newPostButton = buttonByLabel(wrapper, 'scheduler.newPost')

    expect(newPostButton.attributes('disabled')).toBeDefined()
  })

  it('renders SocialProviderIcon when channelIds contains a matching accountId', () => {
    const wrapper = mountHeader({ channelIds: ['acc-1'] })
    const providerIcon = wrapper.findComponent({ name: 'SocialProviderIcon' })
    expect(providerIcon.exists()).toBe(true)
    expect(providerIcon.props('provider')).toBe('linkedin')
  })

  it.each([
    ['queued', Clock],
    ['published', Check],
    ['cancelled', Ban],
    ['all', Folder],
  ])('renders correct statusIcon for status %s', (status, iconComponent) => {
    const wrapper = mountHeader({ status })
    expect(wrapper.findComponent(iconComponent).exists()).toBe(true)
  })
})
