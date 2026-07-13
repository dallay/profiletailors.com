import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import UpcomingSchedule from './UpcomingSchedule.vue'
import type { ScheduleItem } from '@modules/dashboard/domain/dashboard.types'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

function makeItem(id: string, overrides: Partial<ScheduleItem> = {}): ScheduleItem {
  return {
    id,
    title: 'Kotlin coroutines cheat sheet',
    platform: 'linkedin',
    scheduledFor: '2026-06-15T12:00:00Z',
    status: 'scheduled',
    ...overrides,
  }
}

describe('UpcomingSchedule', () => {
  it('renders the title and subtitle', () => {
    const wrapper = mount(UpcomingSchedule, {
      props: { items: [makeItem('s1')] },
    })
    expect(wrapper.text()).toContain('dashboard.upcomingSchedule.title')
    expect(wrapper.text()).toContain('dashboard.upcomingSchedule.subtitle')
  })

  it('renders up to 5 schedule items', () => {
    const items = Array.from({ length: 7 }, (_, i) => makeItem(`s${i}`, { title: `Post ${i}` }))
    const wrapper = mount(UpcomingSchedule, {
      props: { items },
    })
    // Should show at most 5
    expect(wrapper.text()).toContain('Post 0')
    expect(wrapper.text()).toContain('Post 4')
    expect(wrapper.text()).not.toContain('Post 5')
    expect(wrapper.text()).not.toContain('Post 6')
  })

  it('renders the time for each item in local timezone', () => {
    const wrapper = mount(UpcomingSchedule, {
      props: { items: [makeItem('s1', { scheduledFor: '2026-06-15T12:00:00Z' })] },
    })
    // formatTime uses toLocaleTimeString which is timezone-dependent.
    // For UTC it would be 12:00; for UTC+2 it would be 14:00, etc.
    // We verify the time is rendered in HH:mm format by matching the pattern.
    const timePattern = /\d{2}:\d{2}/
    expect(wrapper.text()).toMatch(timePattern)
  })

  it('renders platform labels', () => {
    const wrapper = mount(UpcomingSchedule, {
      props: { items: [makeItem('s1', { platform: 'linkedin' })] },
    })
    expect(wrapper.text()).toContain('LinkedIn')
  })

  it('renders status labels for each item', () => {
    const wrapper = mount(UpcomingSchedule, {
      props: {
        items: [makeItem('s1', { status: 'scheduled' }), makeItem('s2', { status: 'queued' })],
      },
    })
    expect(wrapper.text()).toContain('dashboard.upcomingSchedule.scheduled')
    expect(wrapper.text()).toContain('dashboard.upcomingSchedule.queued')
  })

  it('shows empty state when no items', () => {
    const wrapper = mount(UpcomingSchedule, {
      props: { items: [] },
    })
    expect(wrapper.text()).toContain('dashboard.upcomingSchedule.noItems')
  })

  it('renders the new post CTA button', () => {
    const wrapper = mount(UpcomingSchedule, {
      props: { items: [makeItem('s1')] },
    })
    expect(wrapper.text()).toContain('scheduler.newPost')
  })
})
