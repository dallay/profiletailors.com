import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { DateValue } from 'reka-ui'
import { CalendarDate, getLocalTimeZone, today } from '@internationalized/date'

export type ComposerScheduleMode = 'now' | 'next' | 'custom'

export interface UseComposerSchedulingOptions {
  /**
   * ISO string de fecha inicial para pre-cargar el calendario
   * (usado en edit mode o cuando se crea desde una celda del calendario)
   */
  initialDate?: string

  /**
   * Modo inicial de scheduling
   */
  initialMode?: ComposerScheduleMode
}

/**
 * Composable que maneja toda la lógica de scheduling del composer:
 * - Modos: now, next, custom
 * - Validación de fechas y horas
 * - Formateo de labels
 * - Reloj en tiempo real para validación de "today"
 *
 * @example
 * ```ts
 * const scheduling = useComposerScheduling({
 *   initialDate: props.editingPublication?.scheduledAt,
 *   initialMode: 'custom'
 * })
 *
 * // Usar en template
 * v-model="scheduling.scheduleMode.value"
 * :min-time-for-date="scheduling.minTimeForDate.value"
 * ```
 */
export function useComposerScheduling(options: UseComposerSchedulingOptions = {}) {
  // ============================================================================
  // STATE
  // ============================================================================

  const scheduleMode = ref<ComposerScheduleMode>(options.initialMode ?? 'now')
  const selectedCalendarDate = ref<DateValue>()
  const scheduleTime = ref('10:00')
  const isDatePickerOpen = ref(false)

  /**
   * Reloj en tiempo real que se actualiza cada minuto.
   * Necesario para validar "today" correctamente cuando el usuario
   * tiene el modal abierto por varios minutos.
   */
  const now = ref(new Date())

  // ============================================================================
  // LIFECYCLE - Clock ticker
  // ============================================================================

  let ticker: ReturnType<typeof setInterval> | null = null

  onMounted(() => {
    ticker = setInterval(() => {
      now.value = new Date()
    }, 60_000) // Actualizar cada minuto
  })

  onUnmounted(() => {
    if (ticker) clearInterval(ticker)
  })

  // ============================================================================
  // COMPUTED
  // ============================================================================

  const todayDateValue = computed(() => today(getLocalTimeZone()))

  /**
   * Tiempo mínimo permitido para la fecha seleccionada.
   *
   * - Si es today: now + 5min (para dar tiempo al backend a procesar)
   * - Si estamos cerca de midnight rollover (now+5min cruza a mañana): '23:59' (imposible, fuerza elegir fecha futura)
   * - Si es fecha futura: '00:00' (cualquier hora válida)
   *
   * El input type="time" usa esto como atributo `min`.
   */
  const minTimeForDate = computed(() => {
    if (!selectedCalendarDate.value) return '00:00'

    // Comparar si la fecha seleccionada es hoy
    if (selectedCalendarDate.value.compare(todayDateValue.value) === 0) {
      const future = new Date(now.value.getTime() + 5 * 60_000) // now + 5min

      // Check for midnight rollover: if now+5min crosses into tomorrow,
      // no valid time remains for today — return an impossible value
      const futureDate = new CalendarDate(
        future.getFullYear(),
        future.getMonth() + 1,
        future.getDate(),
      )

      if (futureDate.compare(todayDateValue.value) !== 0) {
        // now+5min es mañana, así que no hay tiempo válido hoy
        return '23:59'
      }

      // Retornar now + 5min como mínimo
      return `${String(future.getHours()).padStart(2, '0')}:${String(future.getMinutes()).padStart(2, '0')}`
    }

    // Future date: any time is valid
    return '00:00'
  })

  /**
   * Label formateado de la fecha seleccionada (e.g. "Aug 15, 2026")
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
   * Texto de ayuda que explica cuándo se publicará el post
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
   * Validación: el schedule está completo y listo para submit
   */
  const isScheduleValid = computed(() => {
    if (scheduleMode.value === 'now' || scheduleMode.value === 'next') {
      return true
    }
    // custom mode requiere fecha y hora
    return !!(selectedCalendarDate.value && scheduleTime.value)
  })

  /**
   * ISO string de la fecha/hora efectiva para enviar al backend.
   * - 'now': null (backend usa server timestamp)
   * - 'next': null (backend calcula el next slot)
   * - 'custom': ISO string con la fecha y hora seleccionadas en UTC
   */
  const effectiveScheduledAt = computed<string | null>(() => {
    if (scheduleMode.value !== 'custom') {
      return null
    }

    if (!selectedCalendarDate.value || !scheduleTime.value) {
      return null
    }

    const [hoursRaw, minutesRaw] = scheduleTime.value.split(':').map(Number)

    // Validar que los valores sean números válidos
    if (Number.isNaN(hoursRaw) || Number.isNaN(minutesRaw)) {
      return null
    }

    const date = selectedCalendarDate.value.toDate(getLocalTimeZone())
    date.setHours(hoursRaw ?? 0, minutesRaw ?? 0, 0, 0)

    return date.toISOString()
  })

  /**
   * Backend schedule mode para el payload
   * Mapeo: 'now' → 'NOW', 'next' → 'NEXT_SLOT', 'custom' → 'SCHEDULED_AT'
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
   * Si hay initialDate, pre-cargar el calendario en custom mode
   */
  if (options.initialDate) {
    const initial = new Date(options.initialDate)
    selectedCalendarDate.value = new CalendarDate(
      initial.getFullYear(),
      initial.getMonth() + 1,
      initial.getDate(),
    )
    scheduleTime.value = `${String(initial.getHours()).padStart(2, '0')}:${String(initial.getMinutes()).padStart(2, '0')}`
    scheduleMode.value = 'custom'
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

  /**
   * Reset a estado inicial (now mode, sin fecha/hora)
   */
  function resetSchedule() {
    scheduleMode.value = options.initialMode ?? 'now'
    selectedCalendarDate.value = undefined
    scheduleTime.value = '10:00'
    isDatePickerOpen.value = false
  }

  /**
   * Cargar scheduling desde un publication existente (edit mode)
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
      const dateSrc = new Date(publication.scheduledAt)
      selectedCalendarDate.value = new CalendarDate(
        dateSrc.getFullYear(),
        dateSrc.getMonth() + 1,
        dateSrc.getDate(),
      )
      scheduleTime.value = `${String(dateSrc.getHours()).padStart(2, '0')}:${String(dateSrc.getMinutes()).padStart(2, '0')}`
    } else {
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
  }
}
