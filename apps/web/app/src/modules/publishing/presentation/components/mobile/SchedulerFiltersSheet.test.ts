import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SchedulerFiltersSheet from './SchedulerFiltersSheet.vue'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'

vi.mock('vue-i18n', () => ({
  createI18n: () => ({ global: { locale: { value: 'en' } } }),
  useI18n: () => ({ t: (key: string) => key }),
}))
vi.mock('@/components/ui/button', () => ({ Button: { template: '<button><slot /></button>' } }))
vi.mock('@/components/ui/sheet', () => ({
  Sheet: { template: '<div><slot /></div>' },
  SheetClose: { template: '<div><slot /></div>' },
  SheetContent: {
    inheritAttrs: false,
    props: ['side'],
    template: '<section v-bind="$attrs" :data-side="side"><slot /></section>',
  },
  SheetFooter: { template: '<footer><slot /></footer>' },
  SheetHeader: { template: '<header><slot /></header>' },
  SheetTitle: { template: '<h2><slot /></h2>' },
}))

describe('SchedulerFiltersSheet', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    usePublishingStore().channels = [
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

  it('renders bottom-sheet filters and active count', () => {
    const wrapper = mount(SchedulerFiltersSheet, {
      props: {
        open: true,
        status: 'queued',
        timezone: 'Europe/Madrid',
        channelIds: ['acc-1'],
        q: '',
        filtersCount: 3,
      },
      global: { mocks: { $t: (key: string) => key } },
    })

    expect(wrapper.get('[data-testid="mobile-filters-sheet"]').attributes('data-side')).toBe(
      'bottom',
    )
    expect(wrapper.text()).toContain('scheduler.channelsLabel')
    expect(wrapper.text()).toContain('scheduler.allPosts')
    expect(wrapper.text()).toContain('scheduler.timezoneLabel')
    expect(wrapper.text()).toContain('scheduler.apply')
    expect(wrapper.text()).toContain('scheduler.reset')
  })

  it('applies and resets draft filters', async () => {
    const wrapper = mount(SchedulerFiltersSheet, {
      props: {
        open: true,
        status: 'queued',
        timezone: 'Europe/Madrid',
        channelIds: ['acc-1'],
        q: '',
        filtersCount: 3,
      },
      global: { mocks: { $t: (key: string) => key } },
    })

    const applyButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'scheduler.apply')
    await applyButton?.trigger('click')

    expect(wrapper.emitted('change:filter')?.[0]?.[0]).toEqual({
      status: 'queued',
      timezone: 'Europe/Madrid',
      channelIds: ['acc-1'],
      q: '',
    })
    expect(wrapper.emitted('update:open')).toEqual([[false]])

    const resetButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'scheduler.reset')
    const browserTimezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
    await resetButton?.trigger('click')
    expect(wrapper.emitted('change:filter')?.[1]?.[0]).toEqual({
      status: 'all',
      timezone: browserTimezone,
      channelIds: [],
      q: '',
    })
  })
})
