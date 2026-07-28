import { ref, computed, onUnmounted, getCurrentInstance } from 'vue'
import type { DateValue } from 'reka-ui'
import { CalendarDate, getLocalTimeZone, today } from '@internationalized/date'

export type ComposerScheduleMode = 'now' | 'next' | 'custom'

export type UseComposerSchedulingOptions = {
  /** ISO date string to pre-load the calendar (edit mode or calendar-cell creation). */
  initialDate?: string

  /** Initial scheduling mode. */
  initialMode?: ComposerScheduleMode
}

export type UseComposerSchedulingResult = {
  scheduleMode: import('vue').Ref<ComposerScheduleMode>
  selectedCalendarDate: import('vue').Ref<DateValue | undefined>
  scheduleTime: import('vue').Ref<string>
  isDatePickerOpen: import('vue').Ref<boolean>
  now: import('vue').Ref<Date>
  todayDateValue: import('vue').ComputedRef<import('@internationalized/date').CalendarDate>
  minTimeForDate: import('vue').ComputedRef<string>
  selectedDateLabel: import('vue').ComputedRef<string>
  scheduleHelperText: import('vue').ComputedRef<string>
  isScheduleValid: import('vue').ComputedRef<boolean>
  effectiveScheduledAt: import('vue').ComputedRef<string | null>
  backendScheduleMode: import('vue').ComputedRef<'NOW' | 'NEXT_SLOT' | 'SCHEDULED_AT'>
  setScheduleMode: (mode: ComposerScheduleMode) => void
  setScheduleDate: (date: DateValue | undefined) => void
  setScheduleTime: (time: string) => void
  resetSchedule: () => void
  loadFromPublication: (publication: {
    scheduleMode?: 'NOW' | 'NEXT_SLOT' | 'SCHEDULED_AT' | null
    scheduledAt?: string | null
  }) => void
  stopTicker: () => void
}

/**
 * Manages composer scheduling state, validation, formatting, and clock updates.
 *
 * @param options - Initial scheduling mode and optional scheduled date.
 * @returns Reactive scheduling state, derived values, and scheduling actions.
 *
 * @example
 * ```ts
 * const scheduling = useComposerScheduling({
 *   initialDate: publication.scheduledAt,
 *   initialMode: 'custom',
 * })
 * ```
 */
export function useComposerScheduling(
  options: UseComposerSchedulingOptions = {},
): UseComposerSchedulingResult {
  // ============================================================================
  // STATE
  // ============================================================================

  const scheduleMode = ref<ComposerScheduleMode>(options.initialMode ?? 'now')
  const selectedCalendarDate = ref<DateValue>()
  const scheduleTime = ref('10:00')
  const isDatePickerOpen = ref(false)

  const now = ref(new Date())

  // ============================================================================
  // CLOCK TICKER
  // ============================================================================

  let ticker: ReturnType<typeof setInterval> | null = null

  /**
   * Stops the scheduling clock updates.
   */
  function stopTicker(): void {
    if (ticker !== null) {
      clearInterval(ticker)
      ticker = null
    }
  }

  // Start eagerly so the clock works even without a component mount
  ticker = setInterval(() => {
    now.value = new Date()
  }, 60_000)

  // Only register onUnmounted when called from an active component
  if (getCurrentInstance()) {
    onUnmounted(() => {
      stopTicker()
    })
  }

  // ============================================================================
  // COMPUTED
  // ============================================================================

  const todayDateValue = computed(() => today(getLocalTimeZone()))

  /**
   * Minimum allowed time for the selected date.
   * - Today: now + 5min (allows backend processing time)
   * - Near midnight rollover (now+5min crosses into tomorrow): '23:59' (impossible — forces future date)
   * - Future date: '00:00' (any valid time)
   *
   * Used as the `min` attribute on the time input.
   */
  const minTimeForDate = computed(() => {
    if (!selectedCalendarDate.value) return '00:00'

    // Comparar si la fecha seleccionada es hoy
    if (selectedCalendarDate.value.compare(todayDateValue.value) === 0) {
      const future = new Date(now.value.getTime() + 5 * 60_000)

      // If now+5min crosses into tomorrow, no valid time remains for today
      const futureDate = new CalendarDate(
        future.getFullYear(),
        future.getMonth() + 1,
        future.getDate(),
      )

      if (futureDate.compare(todayDateValue.value) !== 0) {
        // now+5min es mañana, así que no hay tiempo válido hoy
        return '23:59'
      }

      return `${String(future.getHours()).padStart(2, '0')}:${String(future.getMinutes()).padStart(2, '0')}`
    }

    return '00:00'
  })

  /**
   * Formatted label for the selected date (e.g. "Aug 15, 2026").
   */
  const selectedDateLabel = computed(() => {
    if (!selectedCalendarDate.value) return 'Select date'

    const date = selectedCalendarDate.value.toDate(getLocalTimeZone())
    return date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    })
  })

  /**
   * Helper text explaining when the post will be published.
   */
  const scheduleHelperText = computed(() => {
    if (scheduleMode.value === 'now') {
      return 'Publishes with the creation date and time.'
    }
    if (scheduleMode.value === 'next') {
      return 'Publishes in the next available schedule slot.'
    }
    // custom
    if (!selectedCalendarDate.value) {
      return 'Select a date and time to schedule this post.'
    }
    return `Publishes on ${selectedDateLabel.value} at ${scheduleTime.value}.`
  })

  /**
   * Whether the schedule configuration is complete and ready to submit.
   */
  const isScheduleValid = computed(() => {
    if (scheduleMode.value === 'now' || scheduleMode.value === 'next') {
      return true
    }
    // custom mode requires both date and time
    return !!(selectedCalendarDate.value && scheduleTime.value)
  })

  /**
   * Effective date/time ISO string for the backend payload.
   * - 'now': null (backend uses server timestamp)
   * - 'next': null (backend calculates next slot)
   * - 'custom': ISO string with selected date and time in UTC
   */
  const effectiveScheduledAt = computed<string | null>(() => {
    if (scheduleMode.value !== 'custom') {
      return null
    }

    if (!selectedCalendarDate.value || !scheduleTime.value) {
      return null
    }

    const [hoursRaw, minutesRaw] = scheduleTime.value.split(':').map(Number)

    // Validate numbers and range (hours 0—23, minutes 0—59)
    if (
      Number.isNaN(hoursRaw) ||
      Number.isNaN(minutesRaw) ||
      hoursRaw < 0 ||
      hoursRaw > 23 ||
      minutesRaw < 0 ||
      minutesRaw > 59
    ) {
      return null
    }

    const date = selectedCalendarDate.value.toDate(getLocalTimeZone())
    date.setHours(hoursRaw, minutesRaw, 0, 0)

    return date.toISOString()
  })

  /**
   * Backend schedule mode for the payload.
   * Mapping: 'now' → 'NOW', 'next' → 'NEXT_SLOT', 'custom' → 'SCHEDULED_AT'
   */
  const backendScheduleMode = computed<'NOW' | 'NEXT_SLOT' | 'SCHEDULED_AT'>(() => {
    const modeMap: Record<ComposerScheduleMode, 'NOW' | 'NEXT_SLOT' | 'SCHEDULED_AT'> = {
      now: 'NOW',
      next: 'NEXT_SLOT',
      custom: 'SCHEDULED_AT',
    }
    return modeMap[scheduleMode.value]
  })

  // ============================================================================
  // INITIALIZATION
  // ============================================================================

  /**
   * Applies a parsed date to the custom scheduling state.
   *
   * @param raw - The date string to parse.
   */
  function applyDate(raw: string): void {
    const parsed = new Date(raw)
    if (Number.isNaN(parsed.getTime())) return

    selectedCalendarDate.value = new CalendarDate(
      parsed.getFullYear(),
      parsed.getMonth() + 1,
      parsed.getDate(),
    )
    scheduleTime.value = `${String(parsed.getHours()).padStart(2, '0')}:${String(parsed.getMinutes()).padStart(2, '0')}`
    scheduleMode.value = 'custom'
  }

  if (options.initialDate) {
    applyDate(options.initialDate)
  }

  // ============================================================================
  // ACTIONS
  // ============================================================================

  function setScheduleMode(mode: ComposerScheduleMode) {
    scheduleMode.value = mode
  }

  function setScheduleDate(date: DateValue | undefined) {
    selectedCalendarDate.value = date
  }

  /**
   * Updates the selected scheduling time.
   *
   * @param time - The scheduling time in `HH:MM` format
   */
  function setScheduleTime(time: string) {
    scheduleTime.value = time
  }

  /**
   * Resets scheduling state to its initial mode and default date-picker settings.
   */
  function resetSchedule() {
    scheduleMode.value = options.initialMode ?? 'now'
    selectedCalendarDate.value = undefined
    scheduleTime.value = '10:00'
    isDatePickerOpen.value = false
  }

  /**
   * Loads scheduling settings from an existing publication for editing.
   *
   * @param publication - The publication's scheduling mode and optional scheduled date.
   */
  function loadFromPublication(publication: {
    scheduleMode?: 'NOW' | 'NEXT_SLOT' | 'SCHEDULED_AT' | null
    scheduledAt?: string | null
  }) {
    const modeMap: Record<string, ComposerScheduleMode> = {
      NOW: 'now',
      NEXT_SLOT: 'next',
      SCHEDULED_AT: 'custom',
    }

    scheduleMode.value = modeMap[publication.scheduleMode ?? 'SCHEDULED_AT'] ?? 'custom'

    if (scheduleMode.value === 'custom' && publication.scheduledAt) {
      applyDate(publication.scheduledAt)
    } else {
      selectedCalendarDate.value = undefined
      scheduleTime.value = '10:00'
    }
  }

  // ============================================================================
  // RETURN
  // ============================================================================

  return {
    scheduleMode,
    selectedCalendarDate,
    scheduleTime,
    isDatePickerOpen,
    now,
    todayDateValue,
    minTimeForDate,
    selectedDateLabel,
    scheduleHelperText,
    isScheduleValid,
    effectiveScheduledAt,
    backendScheduleMode,
    setScheduleMode,
    setScheduleDate,
    setScheduleTime,
    resetSchedule,
    loadFromPublication,
    stopTicker,
  }
}
