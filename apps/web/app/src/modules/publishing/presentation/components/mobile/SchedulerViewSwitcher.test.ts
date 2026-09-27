import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import SchedulerViewSwitcher from './SchedulerViewSwitcher.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

describe('SchedulerViewSwitcher', () => {
  it('renders the timeline views and marks the active view', async () => {
    const wrapper = mount(SchedulerViewSwitcher, {
      props: { view: '3-days' },
      global: { mocks: { $t: (key: string) => key } },
    })

    expect(wrapper.get('[data-testid="day-view-button"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="3-days-view-button"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="week-view-button"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="3-days-view-button"]').attributes('aria-current')).toBe(
      'page',
    )
    expect(
      wrapper.get('[data-testid="day-view-button"]').attributes('aria-current'),
    ).toBeUndefined()

    await wrapper.get('[data-testid="day-view-button"]').trigger('click')

    expect(wrapper.emitted('change:view')).toEqual([['day']])
  })
})
