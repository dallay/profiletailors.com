import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
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
      publicationsForSlotFn?: () => Publication[]
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

  it('renders the Now time indicator when current day is in days', () => {
    const targetDay = days(3)[1]!
    const wrapper = mountTimeline({
      isTodayFn: (d) => d.toISOString() === targetDay.toISOString(),
    })

    const nowIndicator = wrapper.find('[data-testid="scheduler-now-indicator"]')
    expect(nowIndicator.exists()).toBe(true)
  })
})
