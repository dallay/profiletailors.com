import { ref, computed, onUnmounted, getCurrentInstance, type Ref, type ComputedRef } from 'vue'
import type { DateValue } from 'reka-ui'
import { CalendarDate, getLocalTimeZone, today } from '@internationalized/date'

export type ComposerScheduleMode = 'now' | 'next' | 'custom'

export type UseComposerSchedulingOptions = {
  /**
   * Initial date ISO string for pre-populating the calendar
   * (used in edit mode or when creating from a calendar cell)
   */
  initialDate?: string

  /**
   * Initial scheduling mode
   */
  initialMode?: ComposerScheduleMode
}

export type UseComposerSchedulingResult = {
  scheduleMode: Ref<ComposerScheduleMode>
  selectedCalendarDate: Ref<DateValue | undefined>
  scheduleTime: Ref<string>
  isDatePickerOpen: Ref<boolean>
  now: Ref<Date>
  todayDateValue: ComputedRef<DateValue>
  minTimeForDate: ComputedRef<string>
  selectedDateLabel: ComputedRef<string>
  scheduleHelperText: ComputedRef<string>
  isScheduleValid: ComputedRef<boolean>
  effectiveScheduledAt: ComputedRef<string | null>
  backendScheduleMode: ComputedRef<'NOW' | 'NEXT_SLOT' | 'SCHEDULED_AT'>
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
 * Composable that handles all composer scheduling logic:
 * - Modes: now, next, custom
 * - Date and time validation
 * - Label formatting
 * - Real-time clock for "today" validation (handles midnight rollover)
 *
 * @example
 * ```ts
 * const scheduling = useComposerScheduling({
 *   initialDate: props.editingPublication?.scheduledAt,
 *   initialMode: 'custom'
 * })
 *
 * // Use in template
 * v-model="scheduling.scheduleMode.value"
 * :min-time-for-date="scheduling.minTimeForDate.value"
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
  // LIFECYCLE - Clock ticker
  // ============================================================================

  let ticker: ReturnType<typeof setInterval> | null = null

  ticker = setInterval(() => {
    now.value = new Date()
  }, 60_000)

  function stopTicker() {
    if (ticker) {
      clearInterval(ticker)
      ticker = null
    }
  }

  const instance = getCurrentInstance()
  if (instance) {
    onUnmounted(stopTicker)
  }

  // ============================================================================
  // COMPUTED
  // ============================================================================

  const todayDateValue = computed(() => today(getLocalTimeZone()))

  const minTimeForDate = computed(() => {
    if (!selectedCalendarDate.value) return '00:00'

    if (selectedCalendarDate.value.compare(todayDateValue.value) === 0) {
      const future = new Date(now.value.getTime() + 5 * 60_000)

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
  })

  const selectedDateLabel = computed(() => {
    if (!selectedCalendarDate.value) return 'Select date'

    const date = selectedCalendarDate.value.toDate(getLocalTimeZone())
    return date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    })
  })

  const scheduleHelperText = computed(() => {
    if (scheduleMode.value === 'now') {
      return 'Publishes with the creation date and time.'
    }
    if (scheduleMode.value === 'next') {
      return 'Publishes in the next available schedule slot.'
    }
    if (!selectedCalendarDate.value) {
      return 'Select a date and time to schedule this post.'
    }
    return `Publishes on ${selectedDateLabel.value} at ${scheduleTime.value}.`
  })

  const isScheduleValid = computed(() => {
    if (scheduleMode.value === 'now' || scheduleMode.value === 'next') {
      return true
    }
    return !!(selectedCalendarDate.value && scheduleTime.value)
  })

  const effectiveScheduledAt = computed<string | null>(() => {
    if (scheduleMode.value !== 'custom') {
      return null
    }

    if (!selectedCalendarDate.value || !scheduleTime.value) {
      return null
    }

    const parts = scheduleTime.value.split(':').map(Number)
    const hoursRaw = parts[0]
    const minutesRaw = parts[1]

    if (
      hoursRaw === undefined ||
      minutesRaw === undefined ||
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

  function applyDate(isoString: string) {
    const parsed = new Date(isoString)
    if (Number.isNaN(parsed.getTime())) {
      return
    }
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

  function setScheduleTime(time: string) {
    scheduleTime.value = time
  }

  function resetSchedule() {
    scheduleMode.value = options.initialMode ?? 'now'
    selectedCalendarDate.value = undefined
    scheduleTime.value = '10:00'
    isDatePickerOpen.value = false
  }

  function loadFromPublication(publication: {
    scheduleMode?: 'NOW' | 'NEXT_SLOT' | 'SCHEDULED_AT' | null
    scheduledAt?: string | null
  }) {
    const modeMap: Record<string, ComposerScheduleMode> = {
      NOW: 'now',
      NEXT_SLOT: 'next',
      SCHEDULED_AT: 'custom',
    }

    const targetMode = modeMap[publication.scheduleMode ?? 'SCHEDULED_AT'] ?? 'custom'

    if (targetMode === 'custom' && publication.scheduledAt) {
      const parsed = new Date(publication.scheduledAt)
      if (!Number.isNaN(parsed.getTime())) {
        selectedCalendarDate.value = new CalendarDate(
          parsed.getFullYear(),
          parsed.getMonth() + 1,
          parsed.getDate(),
        )
        scheduleTime.value = `${String(parsed.getHours()).padStart(2, '0')}:${String(parsed.getMinutes()).padStart(2, '0')}`
        scheduleMode.value = 'custom'
      } else {
        scheduleMode.value = targetMode
        selectedCalendarDate.value = undefined
        scheduleTime.value = '10:00'
      }
    } else {
      scheduleMode.value = targetMode
      selectedCalendarDate.value = undefined
      scheduleTime.value = '10:00'
    }
  }

  // ============================================================================
  // RETURN
  // ============================================================================

  return {
    // State
    scheduleMode,
    selectedCalendarDate,
    scheduleTime,
    isDatePickerOpen,
    now,

    // Computed
    todayDateValue,
    minTimeForDate,
    selectedDateLabel,
    scheduleHelperText,
    isScheduleValid,
    effectiveScheduledAt,
    backendScheduleMode,

    // Actions
    setScheduleMode,
    setScheduleDate,
    setScheduleTime,
    resetSchedule,
    loadFromPublication,
    stopTicker,
  }
}
