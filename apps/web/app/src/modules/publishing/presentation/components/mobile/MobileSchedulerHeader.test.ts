import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import MobileSchedulerHeader from './MobileSchedulerHeader.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@lucide/vue', () => {
  const icon = { template: '<svg />' }
  return { CalendarDays: icon, ChevronLeft: icon, ChevronRight: icon, Filter: icon, Plus: icon }
})

vi.mock('@/components/ui/button', () => ({
  Button: {
    template: '<button :disabled="disabled" :title="title"><slot /></button>',
    props: ['disabled', 'title'],
  },
}))

describe('MobileSchedulerHeader', () => {
  function mountHeader(filtersCount = 2, hasNoChannels = false) {
    return mount(MobileSchedulerHeader, {
      props: {
        title: 'All Channels',
        view: 'week',
        periodLabel: 'Jun 15 – 21, 2026',
        filtersCount,
        hasNoChannels,
      },
      global: { mocks: { $t: (key: string) => key } },
    })
  }

  it('renders the compact header and accessible hit targets', () => {
    const wrapper = mountHeader()

    expect(wrapper.text()).toContain('All Channels')
    expect(wrapper.text()).toContain('Jun 15 – 21, 2026')
    expect(wrapper.text()).toContain('scheduler.today')
    expect(wrapper.get('[data-testid="prev-period"]').classes()).toEqual(
      expect.arrayContaining(['size-11', 'min-h-11', 'min-w-11']),
    )
    expect(wrapper.get('[data-testid="next-period"]').classes()).toEqual(
      expect.arrayContaining(['size-11', 'min-h-11', 'min-w-11']),
    )
    expect(wrapper.get('[data-testid="mobile-filters-trigger"]').text()).toContain('2')
  })

  it('disables New Post when no active channel exists', () => {
    const wrapper = mountHeader(0, true)
    const newPostButton = wrapper.get('[data-testid="mobile-new-post"]')

    expect(newPostButton.attributes('disabled')).toBeDefined()
    expect(newPostButton.attributes('title')).toBe('scheduler.noChannelTitle')
  })

  it('emits header actions', async () => {
    const wrapper = mountHeader(0)

    await wrapper.get('[data-testid="mobile-new-post"]').trigger('click')
    await wrapper.get('[data-testid="prev-period"]').trigger('click')
    await wrapper.get('[data-testid="next-period"]').trigger('click')
    await wrapper.get('[data-testid="today-period"]').trigger('click')
    await wrapper.get('[data-testid="mobile-filters-trigger"]').trigger('click')

    expect(wrapper.emitted('newPost')).toHaveLength(1)
    expect(wrapper.emitted('prev')).toHaveLength(1)
    expect(wrapper.emitted('next')).toHaveLength(1)
    expect(wrapper.emitted('today')).toHaveLength(1)
    expect(wrapper.emitted('openFilters')).toHaveLength(1)
  })

  it('disables New Post button when hasNoChannels is true', () => {
    const wrapper = mountHeader(0, true)
    const newPostBtn = wrapper.get('[data-testid="mobile-new-post"]')

    expect(newPostBtn.attributes('disabled')).toBeDefined()
    expect(newPostBtn.attributes('title')).toBe('scheduler.noChannelTitle')
  })
})
