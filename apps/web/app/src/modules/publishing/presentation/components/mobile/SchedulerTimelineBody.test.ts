import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import SchedulerTimelineBody from './SchedulerTimelineBody.vue'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

const days = (count: number) =>
  Array.from(
    { length: count },
    (_, index) => new Date(`2026-06-${String(index + 15).padStart(2, '0')}T00:00:00Z`),
  )

describe('SchedulerTimelineBody', () => {
  function mountTimeline(dayCount: number) {
    return mount(SchedulerTimelineBody, {
      props: {
        days: days(dayCount),
        hourSlots: [{ hour: 9, label: '9 AM' }],
        publicationsForSlot: () => [],
        isToday: () => false,
        formatDayName: () => 'Monday',
        isPastSlot: () => false,
      },
      global: { mocks: { $t: (key: string) => key } },
    })
  }

  it.each([1, 3, 7])('renders %s day columns with the sticky time gutter', (dayCount) => {
    const wrapper = mountTimeline(dayCount)
    const grids = wrapper.findAll('.grid')

    expect(grids[0]?.attributes('style')).toContain(`repeat(${dayCount}, minmax(120px, 1fr))`)
    expect(wrapper.get('.sticky.left-0').exists()).toBe(true)
    expect(wrapper.find('[data-testid="scheduler-timeline-viewport"]').classes()).toContain(
      'overflow-x-auto',
    )
  })
})
