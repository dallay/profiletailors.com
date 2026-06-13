import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import BestPostingTimes from './BestPostingTimes.vue'
import type { PostingTimeSlot } from '@/lib/types/dashboard'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

function makeSlots(): PostingTimeSlot[] {
  const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
  const slots: PostingTimeSlot[] = []
  for (const day of days) {
    for (let hour = 0; hour < 24; hour++) {
      slots.push({ day, hour, score: Math.floor(Math.random() * 100) })
    }
  }
  return slots
}

describe('BestPostingTimes', () => {
  it('renders the title and subtitle', () => {
    const wrapper = mount(BestPostingTimes, {
      props: { slots: makeSlots() },
    })
    expect(wrapper.text()).toContain('dashboard.postingTimes.title')
    expect(wrapper.text()).toContain('dashboard.postingTimes.subtitle')
  })

  it('renders the HeatmapGrid component', () => {
    const wrapper = mount(BestPostingTimes, {
      props: { slots: makeSlots() },
    })
    const heatmap = wrapper.findComponent({ name: 'HeatmapGrid' })
    expect(heatmap.exists()).toBe(true)
  })

  it('renders the AI recommendation section', () => {
    const wrapper = mount(BestPostingTimes, {
      props: { slots: makeSlots() },
    })
    expect(wrapper.text()).toContain('AI Recommendation')
  })

  it('handles empty slots gracefully', () => {
    const wrapper = mount(BestPostingTimes, {
      props: { slots: [] },
    })
    const heatmap = wrapper.findComponent({ name: 'HeatmapGrid' })
    expect(heatmap.exists()).toBe(true)
  })
})
