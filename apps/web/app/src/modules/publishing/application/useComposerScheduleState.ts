import { computed, ref } from 'vue'
import type { DateValue } from 'reka-ui'
import { CalendarDate, getLocalTimeZone, today } from '@internationalized/date'

/**
 * Provides schedule mode state and date/time calculations for composer schedule footer
 */
export function useComposerScheduleState(now: Date) {
  const isDatePickerOpen = ref(false)
  const todayDateValue = computed(() => today(getLocalTimeZone()))

  const minTimeForDate = (selectedCalendarDate: DateValue | undefined): string => {
    if (selectedCalendarDate?.compare(todayDateValue.value) === 0) {
      const future = new Date(now.getTime() + 5 * 60_000)
      const futureDate = new CalendarDate(
        future.getFullYear(),
        future.getMonth() + 1,
        future.getDate(),
      )
      if (futureDate.compare(todayDateValue.value) !== 0) {
        return '23:59'
      }
      return `${String(future.getHours()).padStart(2, '0')}:${String(future.getMinutes()).padStart(2, '0')}`
    }
    return '00:00'
  }

  const selectedDateLabel = (selectedCalendarDate: DateValue | undefined): string => {
    if (!selectedCalendarDate) return 'Select date'
    const date = selectedCalendarDate.toDate(getLocalTimeZone())
    return date.toLocaleDateString(undefined, {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    })
  }

  return {
    isDatePickerOpen,
    todayDateValue,
    minTimeForDate,
    selectedDateLabel,
  }
}
