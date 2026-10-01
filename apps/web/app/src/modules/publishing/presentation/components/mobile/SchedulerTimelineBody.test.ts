import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import SchedulerTimelineBody from './SchedulerTimelineBody.vue'
import type { Publication } from '@modules/publishing/infrastructure/publishing.store'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))

const days = (count: number) =>
  Array.from(
    { length: count },
    (_, index) => new Date(`2026-06-${String(index + 15).padStart(2, '0')}T00:00:00Z`),
  )

describe('SchedulerTimelineBody', () => {
  function mountTimeline(
    options: {
      dayCount?: number
      isTodayFn?: (d: Date) => boolean
      isPastSlotFn?: (d: Date, hour: number) => boolean
      publicationsForSlotFn?: (day: Date, hour: number) => Publication[]
    } = {},
  ) {
    const dayCount = options.dayCount ?? 3
    return mount(SchedulerTimelineBody, {
      props: {
        days: days(dayCount),
        hourSlots: [{ hour: 9, label: '9 AM' }],
        publicationsForSlot: options.publicationsForSlotFn ?? (() => []),
        isToday: options.isTodayFn ?? (() => false),
        formatDayName: () => 'Monday',
        isPastSlot: options.isPastSlotFn ?? (() => false),
        hasNoChannels: false,
        now: new Date('2026-06-15T09:30:00'),
        formatCurrentTime: (date: Date) =>
          date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      },
      global: { mocks: { $t: (key: string) => key } },
    })
  }

  it.each([1, 3, 7])('renders %s day columns with the sticky time gutter', (dayCount) => {
    const wrapper = mountTimeline({ dayCount })
    const headerRow = wrapper.get('[data-testid="scheduler-day-header-row"]')

    expect(headerRow.attributes('style')).toContain(`repeat(${dayCount}, minmax(120px, 1fr))`)
    expect(headerRow.classes()).toContain('sticky')
    expect(headerRow.classes()).toContain('top-0')
    expect(wrapper.get('.sticky.left-0').exists()).toBe(true)
    expect(wrapper.find('[data-testid="scheduler-timeline-viewport"]').classes()).toContain(
      'overflow-x-auto',
    )
  })

  it('renders visual + affordance in available slots and emits openNewPost on tap', async () => {
    const wrapper = mountTimeline({ isPastSlotFn: () => false })

    const affordances = wrapper.findAll('[data-testid="slot-add-affordance"]')
    expect(affordances.length).toBeGreaterThan(0)

    const slotBtn = wrapper.find('button[aria-label^="Slot for"]')
    expect(slotBtn.exists()).toBe(true)
    expect(slotBtn.attributes('disabled')).toBeUndefined()

    await slotBtn.trigger('click')
    expect(wrapper.emitted('openNewPost')).toHaveLength(1)
    expect(wrapper.emitted('openNewPost')?.[0]).toEqual([days(3)[0], 9])
  })

  it('renders past slots with disabled button and diagonal pattern class, ignoring taps', async () => {
    const wrapper = mountTimeline({ isPastSlotFn: () => true })

    expect(wrapper.find('[data-testid="slot-add-affordance"]').exists()).toBe(false)

    const pastCell = wrapper.find('[data-past-slot="true"]')
    expect(pastCell.exists()).toBe(true)
    expect(pastCell.classes().join(' ')).toContain('repeating-linear-gradient')

    const slotBtn = pastCell.find('button')
    expect(slotBtn.attributes('disabled')).toBeDefined()
  })

  it('updates the Now marker position when the current time changes', async () => {
    const targetDay = days(3)[1]!
    const wrapper = mountTimeline({
      isTodayFn: (d) => d.toISOString() === targetDay.toISOString(),
    })

    const nowIndicator = wrapper.find('[data-testid="scheduler-now-indicator"]')
    expect(nowIndicator.attributes('style')).toContain('top: 50%')

    await wrapper.setProps({ now: new Date('2026-06-15T09:45:00') })
    expect(nowIndicator.attributes('style')).toContain('top: 75%')

    await wrapper.setProps({ now: new Date('2026-06-15T10:00:00') })
    expect(wrapper.find('[data-testid="scheduler-now-indicator"]').exists()).toBe(false)
  })

  it('opens near the current hour when Today is in the visible range', async () => {
    const targetDay = days(3)[1]!
    const wrapper = mountTimeline({
      isTodayFn: (d) => d.toISOString() === targetDay.toISOString(),
    })
    const viewport = wrapper.get('[data-testid="scheduler-timeline-viewport"]').element
    Object.defineProperty(viewport, 'clientHeight', { value: 320 })

    await wrapper.setProps({ days: days(3) })
    await nextTick()

    expect(viewport.scrollTop).toBe(9 * 96 - 64)
    wrapper.unmount()
  })

  it.each([
    { name: 'partly behind the gutter', todayIndex: 1, scrollLeft: 140, expected: 120 },
    { name: 'offscreen to the left', todayIndex: 1, scrollLeft: 400, expected: 120 },
    { name: 'the first column', todayIndex: 0, scrollLeft: 20, expected: 0 },
    { name: 'offscreen to the right', todayIndex: 6, scrollLeft: 0, expected: 568 },
    { name: 'already visible', todayIndex: 1, scrollLeft: 100, expected: 100 },
    { name: 'exactly beside the gutter', todayIndex: 1, scrollLeft: 120, expected: 120 },
    { name: 'outside the date range', todayIndex: -1, scrollLeft: 100, expected: 100 },
  ])(
    'reveals Today after changing days when $name',
    async ({ todayIndex, scrollLeft, expected }) => {
      const targetDay = days(7)[todayIndex]
      const wrapper = mountTimeline({
        dayCount: 7,
        isTodayFn: (day) => day.getTime() === targetDay?.getTime(),
      })
      await nextTick()
      const viewport = wrapper.get('[data-testid="scheduler-timeline-viewport"]').element
      Object.defineProperty(viewport, 'clientWidth', { value: 320 })
      viewport.scrollLeft = scrollLeft

      await wrapper.setProps({ days: days(7) })
      await nextTick()

      expect(viewport.scrollLeft).toBe(expected)
      wrapper.unmount()
    },
  )
})
