import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import type { DateValue } from 'reka-ui'
import { CalendarDate } from '@internationalized/date'
import ComposerSchedulePanel from './ComposerSchedulePanel.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

type ScheduleMode = 'now' | 'next' | 'custom'

function makeTodayDateValue(): CalendarDate {
  const now = new Date()
  return new CalendarDate(now.getFullYear(), now.getMonth() + 1, now.getDate())
}

function mountPanel(propsOverride: Record<string, unknown> = {}) {
  const today = makeTodayDateValue()

  return mount(ComposerSchedulePanel, {
    props: {
      scheduleMode: 'now' as ScheduleMode,
      selectedCalendarDate: undefined as DateValue | undefined,
      scheduleTime: '10:00',
      isDatePickerOpen: false,
      todayDateValue: today,
      minTimeForDate: '00:00',
      selectedDateLabel: 'Select date',
      scheduleHelperText: 'Publishes with the creation date and time.',
      ...propsOverride,
    },
  })
}

describe('ComposerSchedulePanel.vue', () => {
  it('renders all three schedule mode options', (): void => {
    const wrapper = mountPanel()
    expect(wrapper.text()).toContain('Now')
    expect(wrapper.text()).toContain('Next Schedule')
    expect(wrapper.text()).toContain('Pick Date')
  })

  it('emits update:scheduleMode when Now is clicked', async (): Promise<void> => {
    const wrapper = mountPanel({ scheduleMode: 'next' as ScheduleMode })

    await wrapper.get('[data-testid="schedule-mode-now"]').trigger('click')

    const emissions = wrapper.emitted('update:scheduleMode') ?? []
    expect(emissions).toHaveLength(1)
    expect(emissions[0]).toEqual(['now'])
  })

  it('emits update:scheduleMode when Next Schedule is clicked', async (): Promise<void> => {
    const wrapper = mountPanel({ scheduleMode: 'now' as ScheduleMode })

    await wrapper.get('[data-testid="schedule-mode-next"]').trigger('click')

    const emissions = wrapper.emitted('update:scheduleMode') ?? []
    expect(emissions).toHaveLength(1)
    expect(emissions[0]).toEqual(['next'])
  })

  it('emits update:scheduleMode when Pick Date is clicked', async (): Promise<void> => {
    const wrapper = mountPanel({ scheduleMode: 'now' as ScheduleMode })

    await wrapper.get('[data-testid="schedule-mode-custom"]').trigger('click')

    const emissions = wrapper.emitted('update:scheduleMode') ?? []
    expect(emissions).toHaveLength(1)
    expect(emissions[0]).toEqual(['custom'])
  })

  it('does not show date picker or time input in now mode', (): void => {
    const wrapper = mountPanel({ scheduleMode: 'now' as ScheduleMode })
    expect(wrapper.find('[data-testid="schedule-date-picker"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="schedule-time-input"]').exists()).toBe(false)
  })

  it('does not show date picker or time input in next mode', (): void => {
    const wrapper = mountPanel({ scheduleMode: 'next' as ScheduleMode })
    expect(wrapper.find('[data-testid="schedule-date-picker"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="schedule-time-input"]').exists()).toBe(false)
  })

  it('shows date picker trigger and time input in custom mode', (): void => {
    const wrapper = mountPanel({ scheduleMode: 'custom' as ScheduleMode })
    expect(wrapper.find('[data-testid="schedule-date-trigger"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="schedule-time-input"]').exists()).toBe(true)
  })

  it('displays the correct helper text for now mode', (): void => {
    const wrapper = mountPanel({
      scheduleMode: 'now' as ScheduleMode,
      scheduleHelperText: 'Publishes with the creation date and time.',
    })
    expect(wrapper.text()).toContain('Publishes with the creation date and time.')
  })

  it('displays the correct helper text for next mode', (): void => {
    const wrapper = mountPanel({
      scheduleMode: 'next' as ScheduleMode,
      scheduleHelperText: 'Publishes in the next available schedule slot.',
    })
    expect(wrapper.text()).toContain('Publishes in the next available schedule slot.')
  })

  it('displays the correct helper text for custom mode', (): void => {
    const wrapper = mountPanel({
      scheduleMode: 'custom' as ScheduleMode,
      selectedDateLabel: 'Jul 28, 2026',
      scheduleTime: '14:30',
      scheduleHelperText: 'Publishes on Jul 28, 2026 at 14:30.',
    })
    expect(wrapper.text()).toContain('Publishes on Jul 28, 2026 at 14:30.')
  })

  it('shows selected date label in the date picker trigger', (): void => {
    const wrapper = mountPanel({
      scheduleMode: 'custom' as ScheduleMode,
      selectedDateLabel: 'Jul 28, 2026',
    })
    expect(wrapper.text()).toContain('Jul 28, 2026')
  })

  it('emits update:scheduleTime when time input changes', async (): Promise<void> => {
    const wrapper = mountPanel({ scheduleMode: 'custom' as ScheduleMode })

    const timeInput = wrapper.get('[data-testid="schedule-time-input"]')
    await timeInput.setValue('15:30')

    const emissions = wrapper.emitted('update:scheduleTime') ?? []
    expect(emissions).toHaveLength(1)
    expect(emissions[0]).toEqual(['15:30'])
  })

  it('sets min attribute on time input from minTimeForDate prop', (): void => {
    const wrapper = mountPanel({ scheduleMode: 'custom' as ScheduleMode, minTimeForDate: '09:00' })

    const timeInput = wrapper.get<HTMLInputElement>('[data-testid="schedule-time-input"]')
    expect(timeInput.attributes('min')).toBe('09:00')
  })

  it('radio group has correct aria-label for accessibility', (): void => {
    const wrapper = mountPanel()
    expect(wrapper.find('[role="radiogroup"]').attributes('aria-label')).toBe('Schedule mode')
  })

  it('applies bold styling to the active schedule mode option', (): void => {
    const wrapper = mountPanel({ scheduleMode: 'now' as ScheduleMode })

    const nowLabel = wrapper.get('[data-testid="schedule-mode-now"]')
    expect(nowLabel.classes()).toContain('bg-text-display')
    expect(nowLabel.classes()).toContain('text-bg-primary')

    const nextLabel = wrapper.get('[data-testid="schedule-mode-next"]')
    expect(nextLabel.classes()).not.toContain('bg-text-display')
  })

  it('date picker trigger emits update:isDatePickerOpen when opened', async (): Promise<void> => {
    const wrapper = mountPanel({ scheduleMode: 'custom' as ScheduleMode, isDatePickerOpen: false })

    await wrapper.get('[data-testid="schedule-date-trigger"]').trigger('click')

    const emissions = wrapper.emitted('update:isDatePickerOpen') ?? []
    expect(emissions).toHaveLength(1)
    expect(emissions[0]).toEqual([true])
  })

  it('renders schedule mode section with CalendarIcon', (): void => {
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="schedule-section"]').exists()).toBe(true)
  })
})
