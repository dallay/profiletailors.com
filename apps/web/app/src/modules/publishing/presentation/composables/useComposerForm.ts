import { ref, computed } from 'vue'
import type { DateValue } from 'reka-ui'
import { CalendarDate, getLocalTimeZone } from '@internationalized/date'
import type { Publication } from '@modules/publishing/infrastructure/publishing.store'
import type { ComposerScheduleMode } from '@modules/publishing/presentation/components/composer/composer.types'
import { useMediaStore } from '@modules/media'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'

interface UseComposerFormOptions {
  editingPublication?: Publication | null
  initialDate?: string
  onAssetsTouched?: () => void
}

export function useComposerForm(options: UseComposerFormOptions = {}) {
  const _mediaStore = useMediaStore()
  const publishingStore = usePublishingStore()

  // Form state
  const postText = ref('')
  const firstComment = ref('')
  const selectedChannelId = ref<string | null>(null)
  const scheduleMode = ref<ComposerScheduleMode>('now')
  const selectedCalendarDate = ref<DateValue>()
  const scheduleTime = ref('10:00')
  const priorityMode = ref(false)
  const createAnother = ref(false)
  const assetsTouched = ref(false)

  // UI state
  const isSubmitting = ref(false)
  const isAiProcessing = ref(false)
  const submitError = ref('')
  const isDropzoneActive = ref(false)

  // Computed
  const isEditMode = computed(() => !!options.editingPublication)

  // Validation
  function validateCustomSchedule(now: Date, finalScheduledDate: Date): string | undefined {
    if (!selectedCalendarDate.value) {
      return 'Select a date.'
    }

    const [hoursRaw, minutesRaw] = scheduleTime.value.split(':').map(Number)
    const hours = hoursRaw ?? Number.NaN
    const minutes = minutesRaw ?? Number.NaN

    const isValidHours = Number.isInteger(hours) && hours >= 0 && hours <= 23
    const isValidMinutes = Number.isInteger(minutes) && minutes >= 0 && minutes <= 59
    if (!isValidHours || !isValidMinutes) {
      return 'Invalid time selected.'
    }

    finalScheduledDate.setHours(hours, minutes, 0, 0)

    const earliestAllowed = new Date(now.getTime() + 5 * 60_000)
    if (finalScheduledDate < earliestAllowed) {
      return 'Selected date and time must be in the future.'
    }

    return undefined
  }

  function resolveScheduleMode(
    mode: ComposerScheduleMode,
  ): NonNullable<Publication['scheduleMode']> {
    if (mode === 'now') return 'NOW'
    if (mode === 'next') return 'NEXT_SLOT'
    return 'SCHEDULED_AT'
  }

  function resolveScheduledDate(now: Date): Date | undefined {
    if (scheduleMode.value !== 'custom') return undefined
    if (!selectedCalendarDate.value) {
      submitError.value = 'Select a date.'
      return undefined
    }
    const date = selectedCalendarDate.value.toDate(getLocalTimeZone())
    const error = validateCustomSchedule(now, date)
    if (error) {
      submitError.value = error
      return undefined
    }
    return date
  }

  // Initialization
  async function initEditMode(pub: Publication) {
    postText.value = pub.content ?? ''
    firstComment.value = ''
    priorityMode.value = pub.priority ?? false
    const modeMap: Record<string, ComposerScheduleMode> = {
      NOW: 'now',
      NEXT_SLOT: 'next',
      SCHEDULED_AT: 'custom',
    }
    scheduleMode.value = modeMap[pub.scheduleMode ?? 'SCHEDULED_AT'] ?? 'custom'

    assetsTouched.value = false
    selectedChannelId.value =
      publishingStore.channels.find((channel) => channel.accountId === pub.accountId)?.id ??
      publishingStore.channels[0]?.id ??
      null

    if (pub.scheduledAt && scheduleMode.value === 'custom') {
      const dateSrc = new Date(pub.scheduledAt)
      selectedCalendarDate.value = new CalendarDate(
        dateSrc.getFullYear(),
        dateSrc.getMonth() + 1,
        dateSrc.getDate(),
      )
      scheduleTime.value = `${String(dateSrc.getHours()).padStart(2, '0')}:${String(dateSrc.getMinutes()).padStart(2, '0')}`
    } else {
      selectedCalendarDate.value = undefined
      scheduleTime.value = ''
    }
  }

  function initCreateMode() {
    postText.value = ''
    firstComment.value = ''
    priorityMode.value = false
    scheduleMode.value = options.initialDate ? 'custom' : 'now'
    assetsTouched.value = false
    selectedChannelId.value = publishingStore.channels[0]?.id ?? null

    const defaultDate = options.initialDate ? new Date(options.initialDate) : new Date()
    selectedCalendarDate.value = new CalendarDate(
      defaultDate.getFullYear(),
      defaultDate.getMonth() + 1,
      defaultDate.getDate(),
    )
    scheduleTime.value = `${String(defaultDate.getHours()).padStart(2, '0')}:${String(defaultDate.getMinutes()).padStart(2, '0')}`
  }

  async function initialize() {
    submitError.value = ''
    isDropzoneActive.value = false

    if (isEditMode.value && options.editingPublication) {
      await initEditMode(options.editingPublication)
    } else {
      initCreateMode()
    }
  }

  function reset() {
    postText.value = ''
    firstComment.value = ''
    assetsTouched.value = false
  }

  function clearError() {
    submitError.value = ''
  }

  return {
    // State
    postText,
    firstComment,
    selectedChannelId,
    scheduleMode,
    selectedCalendarDate,
    scheduleTime,
    priorityMode,
    createAnother,
    assetsTouched,
    isSubmitting,
    isAiProcessing,
    submitError,
    isDropzoneActive,

    // Computed
    isEditMode,

    // Methods
    validateCustomSchedule,
    resolveScheduleMode,
    resolveScheduledDate,
    initialize,
    reset,
    clearError,
  }
}
