import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import HeatmapGrid from './HeatmapGrid.vue'
import type { PostingTimeSlot } from '@/lib/types/dashboard'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

function makeSlots(count = 168): PostingTimeSlot[] {
  const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
  const slots: PostingTimeSlot[] = []
  for (const day of days) {
    for (let hour = 0; hour < 24; hour++) {
      slots.push({ day, hour, score: Math.floor(Math.random() * 100) })
    }
  }
  return slots.slice(0, count)
}

describe('HeatmapGrid', () => {
  it('renders all 7 day labels', () => {
    const wrapper = mount(HeatmapGrid, {
      props: { slots: makeSlots() },
    })
    for (const key of [
      'dashboard.postingTimes.mon',
      'dashboard.postingTimes.tue',
      'dashboard.postingTimes.wed',
      'dashboard.postingTimes.thu',
      'dashboard.postingTimes.fri',
      'dashboard.postingTimes.sat',
      'dashboard.postingTimes.sun',
    ]) {
      expect(wrapper.text()).toContain(key)
    }
  })

  it('renders hour labels at 3-hour intervals', () => {
    const wrapper = mount(HeatmapGrid, {
      props: { slots: makeSlots() },
    })
    for (const h of [0, 3, 6, 9, 12, 15, 18, 21]) {
      expect(wrapper.text()).toContain(`${h}:00`)
    }
  })

  it('renders the legend with low and high labels', () => {
    const wrapper = mount(HeatmapGrid, {
      props: { slots: makeSlots() },
    })
    expect(wrapper.text()).toContain('dashboard.postingTimes.low')
    expect(wrapper.text()).toContain('dashboard.postingTimes.high')
  })

  it('renders 24 cells per day row', () => {
    const wrapper = mount(HeatmapGrid, {
      props: { slots: makeSlots() },
    })
    // 7 days * 24 cells = 168 cells
    const cells = wrapper.findAll('.aspect-square')
    expect(cells).toHaveLength(168)
  })

  it('handles empty slots gracefully', () => {
    const wrapper = mount(HeatmapGrid, {
      props: { slots: [] },
    })
    const cells = wrapper.findAll('.aspect-square')
    expect(cells).toHaveLength(168) // still renders grid with score=0
  })
})
