<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DateValue } from 'reka-ui'
import { useFocusTrap } from '@shared/composables/useFocusTrap'
import { useComposerMediaPicker } from '@modules/publishing/application/useComposerMediaPicker'
import { CalendarDate, getLocalTimeZone, today } from '@internationalized/date'
import { ImageIcon, ChevronDown, Hash, Smile, Sparkles, X } from '@lucide/vue'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import {
  isSocialProvider,
  usePublishingStore,
  type Publication,
} from '@modules/publishing/infrastructure/publishing.store'
import { useMediaStore } from '@modules/media'
import { resolveApiUrl } from '@modules/auth/infrastructure/auth-api'
import PostPreviewPanel from '@modules/publishing/presentation/components/composer/PostPreviewPanel.vue'
import type { LinkedInPreviewModel, PostPreviewMedia } from '@modules/publishing/presentation/components/composer/post-preview.types'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Progress } from '@/components/ui/progress'
import Spinner from '@/components/ui/spinner/Spinner.vue'
import ComposerMediaPickerShell from '@modules/publishing/presentation/components/composer/ComposerMediaPickerShell.vue'
import MediaProviderPanel from '@modules/publishing/presentation/components/composer/MediaProviderPanel.vue'
import ComposerSchedulePanel from '@modules/publishing/presentation/components/composer/ComposerSchedulePanel.vue'
import ComposerChannelSelector from './ComposerChannelSelector.vue'

type ComposerScheduleMode = 'now' | 'next' | 'custom'
const COMPOSER_SUPPORTED_MEDIA_TYPES = new Set([
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
  'video/mp4',
])

type ComposerInlineAttachment =
  | {
    key: string
    kind: 'draft'
    assetId: string
    name: string
    previewUrl: string | null
    isUploading: false
    uploadProgress: 100
    uploadStateLabel: null
  }
  | {
    key: string
    kind: 'local-upload'
    assetId: null
    name: string
    previewUrl: string | null
    isUploading: boolean
    uploadProgress: number | null
    uploadStateLabel: string | null
  }

const props = withDefaults(
  defineProps<{
    isOpen?: boolean
    initialDate?: string // ISO string
    editingPublication?: Publication // Pre-fill for editing
    /**
     * Provider to surface as a browsable source inside the media picker.
     * The parent owns the feature flag and only passes `provider="unsplash"`
     * when the provider is configured and enabled.
     */
    provider?: 'unsplash' | null
  }>(),
  {
    isOpen: false,
    provider: null,
  }
)

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created'): void
  (e: 'updated'): void
}>()

const { t } = useI18n()
const auth = useAuthStore()
const publishingStore = usePublishingStore()
const mediaStore = useMediaStore()


const postText = ref('')
const selectedChannelId = ref<string | null>(null)


const picker = useComposerMediaPicker({
  mediaStore,
  publishingStore,
  editingPublication: () => props.editingPublication ?? null,
  provider: () => props.provider ?? null,
  initialChannelId: () => selectedChannelId.value,
  onAttachmentsChanged: () => {
    assetsTouched.value = true
  },
})
const submitError = ref('')
const firstComment = ref('')
const createAnother = ref(false)
const priorityMode = ref(false)
const scheduleMode = ref<ComposerScheduleMode>('now')
const isDatePickerOpen = ref(false)

const modalContainer = ref<HTMLElement | null>(null)
const { activate: activateFocusTrap, deactivate: deactivateFocusTrap } = useFocusTrap(modalContainer, () => emit('close'))


const isEditMode = computed(() => !!props.editingPublication)
const _isCreating = computed(() => !isEditMode.value)
const assetsTouched = ref(false)
let suppressAssetTouchTracking = false


const uploadPreviewBlob = ref<string | null>(null)
const selectedUploadFile = ref<File | null>(null)
const uploadTempKey = ref<string | null>(null)
const uploadProgress = ref(0)
const isLocalUploadInFlight = ref(false)
const pickerSessionUploadInput = ref<HTMLInputElement | null>(null)
const isMediaSourcesOpen = ref(false)
const isDropzoneActive = ref(false)
const mediaError = ref<string | null>(null)

function clearUploadPreviewBlob() {
  if (uploadPreviewBlob.value) {
    URL.revokeObjectURL(uploadPreviewBlob.value)
    uploadPreviewBlob.value = null
  }
}


const selectedCalendarDate = ref<DateValue>()
const scheduleTime = ref('10:00')


const now = ref(new Date())
let timer: ReturnType<typeof setInterval>

onMounted(() => {
  timer = setInterval(() => {
    now.value = new Date()
  }, 60_000)
})

onUnmounted(() => {
  clearInterval(timer)
  picker.stopAllReconciliationPollers()
})

const todayDateValue = computed(() => today(getLocalTimeZone()))

const minTimeForDate = computed(() => {
  if (selectedCalendarDate.value?.compare(todayDateValue.value) === 0) {
    const future = new Date(now.value.getTime() + 5 * 60_000)
    // Check for midnight rollover: if now+5min crosses into tomorrow,
    // no valid time remains for today — return an impossible value
    const futureDate = new CalendarDate(future.getFullYear(), future.getMonth() + 1, future.getDate())
    if (futureDate.compare(todayDateValue.value) !== 0) {
      return '23:59'
    }
    return `${String(future.getHours()).padStart(2, '0')}:${String(future.getMinutes()).padStart(2, '0')}`
  }
  // Future date: any time is valid
  return '00:00'
})

async function initEditMode(pub: NonNullable<typeof props.editingPublication>) {
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
  picker.draftAttachmentIds.value = []
  picker.pickerSelectionIds.value = []
  picker.resetPickerSessionTracking()
  mediaStore.clearSelection()
  if (pub.assetIds?.length) {
    const resolvedAssetIds = await Promise.all(
      pub.assetIds.map(async (assetId) => {
        if (!mediaStore.assetsById[assetId]) {
          try {
            await mediaStore.loadAsset(assetId)
          } catch (err) {
            const status = err instanceof Error && 'status' in err ? err.status : undefined
            if (status === 404) return null
          }
        }
        return assetId
      }),
    )
    picker.draftAttachmentIds.value = resolvedAssetIds.filter((assetId): assetId is string => assetId !== null)
  }

  const pubChannelId = pub.accountId
    ?? publishingStore.channels.find((ch) => isSocialProvider(ch.provider) && pub.channels?.includes(ch.provider))?.id
    ?? null
  selectedChannelId.value = pubChannelId

  if (scheduleMode.value === 'custom' && pub.scheduledAt) {
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
  scheduleMode.value = props.initialDate ? 'custom' : 'now'
  assetsTouched.value = false
  picker.draftAttachmentIds.value = []
  picker.pickerSelectionIds.value = []
  picker.resetPickerSessionTracking()
  mediaStore.clearSelection()
  selectedChannelId.value = publishingStore.channels[0]?.id ?? null

  const defaultDate = props.initialDate ? new Date(props.initialDate) : new Date()
  selectedCalendarDate.value = new CalendarDate(
    defaultDate.getFullYear(),
    defaultDate.getMonth() + 1,
    defaultDate.getDate(),
  )
  scheduleTime.value = `${String(defaultDate.getHours()).padStart(2, '0')}:${String(defaultDate.getMinutes()).padStart(2, '0')}`
}

async function initializeComposerForOpen() {
  submitError.value = ''
  mediaError.value = null
  isDatePickerOpen.value = false
  clearUploadPreviewBlob()
  selectedUploadFile.value = null
  uploadTempKey.value = null
  uploadProgress.value = 0
  isLocalUploadInFlight.value = false
  isMediaSourcesOpen.value = false
  isDropzoneActive.value = false
  picker.isMediaPickerOpen.value = false
  picker.mediaPickerCollectionState.value = 'LOADING'

  suppressAssetTouchTracking = true
  if (isEditMode.value && props.editingPublication) {
    await initEditMode(props.editingPublication)
  } else {
    initCreateMode()
  }
  suppressAssetTouchTracking = false

  if (auth.isAuthenticated) {
    try {
      await mediaStore.loadDanglingAssets()
    } catch {
      // Non-critical — dangling load failure shouldn't block the composer
    }
  }

  await nextTick()
  if (props.isOpen) {
    activateFocusTrap()
  }
}

// Initialize Date + focus trap
watch(
  () => props.isOpen,
  async (open) => {
    if (open) {
      await initializeComposerForOpen()
    } else {
      deactivateFocusTrap()
    }
  },
)

onMounted(async () => {
  if (props.isOpen && isEditMode.value) {
    await initializeComposerForOpen()
  }
})

watch(
  () => mediaStore.selectedAssetIds,
  () => {
    if (isEditMode.value && !suppressAssetTouchTracking) {
      assetsTouched.value = true
    }
  },
  { deep: true },
)

watch(
  () => publishingStore.channels,
  (channels) => {
    if (channels.length === 0) {
      selectedChannelId.value = null
      return
    }
    const activeIds = new Set(channels.filter(ch => ch.status === 'ACTIVE').map(ch => ch.id))
    if (!activeIds.has(selectedChannelId.value ?? '')) {
      selectedChannelId.value = channels.find(ch => ch.status === 'ACTIVE')?.id ?? null
    }
  },
)


const isSubmitting = ref(false)
const isAiProcessing = ref(false)
const charLimit = 3000
const charsRemaining = computed(() => charLimit - postText.value.length)
const isTextTooLong = computed(() => charsRemaining.value < 0)
const selectedChannel = computed(() =>
  publishingStore.channels.find((channel) => channel.id === selectedChannelId.value)
  ?? publishingStore.channels[0]
  ?? null,
)
const selectedProviders = computed(() =>
  selectedChannel.value && isSocialProvider(selectedChannel.value.provider)
    ? [selectedChannel.value.provider]
    : [],
)
const selectedChannelInitials = computed(() => {
  const name = selectedChannel.value?.name?.trim()
  if (!name) return 'PT'

  const parts = name.split(/\s+/).filter(Boolean)
  if (parts.length === 1) {
    return (parts[0]?.slice(0, 2) ?? 'PT').toUpperCase()
  }

  return parts
    .slice(0, 2)
    .map((part) => part[0] ?? '')
    .join('')
    .toUpperCase()
})

const selectedDateLabel = computed(() => {
  if (!selectedCalendarDate.value) return 'Select date'
  const date = selectedCalendarDate.value.toDate(getLocalTimeZone())
  return date.toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
})

const scheduleHelperText = computed(() => {
  if (scheduleMode.value === 'now') return 'Publishes with the creation date and time.'
  if (scheduleMode.value === 'next') return 'Publishes in the next available schedule slot.'
  return `Publishes on ${selectedDateLabel.value} at ${scheduleTime.value}.`
})

const canSubmit = computed(() => {
  return (
    !!selectedChannel.value &&
    postText.value.trim().length > 0 &&
    !isTextTooLong.value &&
    !isSubmitting.value &&
    !picker.isAttachmentLimitExceeded.value
  )
})


function handleFileSelect(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files?.length) {
    const files = Array.from(target.files)
    if (picker.isMediaPickerOpen.value) {
      picker.handlePickerUploadSelection(files).catch(() => undefined)
    } else {
      addFiles(files)
    }
    target.value = ''
  }
}

function openUploadPicker() {
  pickerSessionUploadInput.value?.click()
}

function openMediaLibrary() {
  isMediaSourcesOpen.value = false
  picker.openMediaPicker('library').catch(() => undefined)
}

function openUnsplashLibrary() {
  isMediaSourcesOpen.value = false
  picker.openMediaPicker('unsplash').catch(() => undefined)
}

function handleDropzoneDragOver(event: DragEvent) {
  event.preventDefault()
  isDropzoneActive.value = true
}

function handleDropzoneDragLeave(event: DragEvent) {
  event.preventDefault()
  isDropzoneActive.value = false
}

function extractFilesFromDataTransfer(dataTransfer: DataTransfer | null): File[] {
  if (!dataTransfer) return []
  return Array.from(dataTransfer.files ?? [])
}

function extractFilesFromClipboard(clipboardData: DataTransfer | null): File[] {
  if (!clipboardData) return []

  const clipboardFiles = Array.from(clipboardData.files ?? [])
  if (clipboardFiles.length > 0) return clipboardFiles

  return Array.from(clipboardData.items ?? [])
    .filter((item) => item.kind === 'file')
    .map((item) => item.getAsFile())
    .filter((file): file is File => file !== null)
}

function handleComposerSurfaceDragOver(event: DragEvent) {
  const files = extractFilesFromDataTransfer(event.dataTransfer)
  if (files.length === 0) return
  event.preventDefault()
  isDropzoneActive.value = true
}

function handleComposerSurfaceDragLeave(event: DragEvent) {
  event.preventDefault()
  isDropzoneActive.value = false
}

function handleComposerSurfaceDrop(event: DragEvent) {
  const files = extractFilesFromDataTransfer(event.dataTransfer)
  if (files.length === 0) return
  event.preventDefault()
  isDropzoneActive.value = false
  addFiles(files)
}

function handleComposerSurfacePaste(event: ClipboardEvent) {
  const files = extractFilesFromClipboard(event.clipboardData)
  if (files.length === 0) return
  event.preventDefault()
  addFiles(files)
}

function handleDropzoneDrop(event: DragEvent) {
  handleComposerSurfaceDrop(event)
}

function addFiles(filesList: File[]) {
  if (isEditMode.value) assetsTouched.value = true
  mediaError.value = null

  const valid: File[] = []
  const rejected: string[] = []
  for (const file of filesList) {
    const isSupported = COMPOSER_SUPPORTED_MEDIA_TYPES.has(file.type)
    const isUnderLimit = file.size <= 10 * 1024 * 1024 // 10MB
    if (!isSupported) {
      rejected.push(t('composer.media.unsupportedFormat'))
    } else if (!isUnderLimit) {
      rejected.push(t('composer.media.fileSizeExceeded'))
    } else {
      valid.push(file)
    }
  }

  if (rejected.length > 0 && valid.length === 0) {
    mediaError.value = rejected[0] ?? null
    return
  }

  const file = valid[0]
  if (!file) return

  clearUploadPreviewBlob()
  selectedUploadFile.value = file
  uploadPreviewBlob.value = URL.createObjectURL(file)
  uploadTempKey.value = `modal-upload-${Date.now()}`
  uploadProgress.value = 0
  isLocalUploadInFlight.value = false
}

/**
 * Reserves an asset, begins upload, and tracks progress.
 * On success, the READY asset is added to the media store selection.
 */
async function _uploadAndTrack(file: File) {
  // Revoke any previous preview blob
  clearUploadPreviewBlob()
  uploadPreviewBlob.value = URL.createObjectURL(file)
  uploadProgress.value = 0

  const tempKey = `modal-upload-${Date.now()}`
  uploadTempKey.value = tempKey

  try {
    const asset = await mediaStore.createAndUpload(file, tempKey, (pct) => {
      uploadProgress.value = pct
    })

    if (isEditMode.value) assetsTouched.value = true
    // Upload succeeded — add to selection for the publication
    mediaStore.addToSelection(asset.assetId)

    // Keep the transient preview blob alive until the user closes/removes the media.
    // The backend does not yet expose a download/thumbnail URL for READY assets.
    uploadTempKey.value = null
    uploadProgress.value = 0
  } catch {
    // Error is already tracked in mediaStore.uploadList
    // Blob preview stays so user can see what failed
    uploadTempKey.value = null
    uploadProgress.value = 0
  }
}

/**
 * Removes the current upload selection (READY asset from store + any in-progress state).
 */
function removeFile() {
  if (isEditMode.value) assetsTouched.value = true
  clearUploadPreviewBlob()
  selectedUploadFile.value = null
  if (uploadTempKey.value) {
    mediaStore.dismissUpload(uploadTempKey.value)
  }
  uploadTempKey.value = null
  uploadProgress.value = 0
  isLocalUploadInFlight.value = false
}

/**
 * Whether the current composer media is an image that can be previewed.
 * Prefer the transient upload file while an upload is in progress, then fall back
 * to the first selected READY asset once the upload completes.
 */
const selectedAssetIsImage = computed(() => {
  const uploadFile = currentUpload.value?.file ?? selectedUploadFile.value
  if (uploadFile) {
    return uploadFile.type.startsWith('image/')
  }

  const first = picker.draftAttachmentIds.value
    .map((assetId) => mediaStore.assetsById[assetId])
    .find((asset) => asset !== undefined)
  if (!first) return false
  return first.mediaType.startsWith('image/')
})

/**
 * The preview URL for the composer media.
 * Uses the transient blob immediately after file selection so the LinkedIn preview
 * updates before the backend upload finishes.
 */
const selectedAssetPreviewUrl = computed<string | null>(() => {
  if (uploadPreviewBlob.value && selectedAssetIsImage.value) {
    return uploadPreviewBlob.value
  }

  const first = picker.draftAttachmentIds.value
    .map((assetId) => mediaStore.assetsById[assetId])
    .find((asset) => asset !== undefined)
  if (!first) return null
  if (!first.mediaType.startsWith('image/')) return null
  return first.previewUrl ? resolveApiUrl(first.previewUrl) : null
})

const selectedPreviewMedia = computed<PostPreviewMedia | null>(() => {
  const uploadFile = currentUpload.value?.file ?? selectedUploadFile.value
  if (uploadFile) {
    const isImage = uploadFile.type.startsWith('image/')

    return {
      kind: isImage ? 'image' : 'video',
      url: isImage ? uploadPreviewBlob.value : null,
      alt: 'Media preview',
      name: uploadFile.name,
    }
  }

  const first = picker.draftAttachmentIds.value
    .map((assetId) => mediaStore.assetsById[assetId])
    .find((asset) => asset !== undefined)
  if (!first) return null

  const isImage = first.mediaType.startsWith('image/')

  return {
    kind: isImage ? 'image' : 'video',
    url: isImage ? selectedAssetPreviewUrl.value : null,
    alt: 'Media preview',
    name: first.originalFilename,
  }
})

const linkedinPreview = computed<LinkedInPreviewModel>(() => ({
  authorName: selectedChannel.value?.name || auth.user?.username || 'Profile Tailors',
  authorHandle: selectedChannel.value?.handle || 'LinkedIn Member',
  authorAvatarUrl: selectedChannel.value?.avatarUrl ?? null,
  authorInitials: selectedChannelInitials.value,
  text: postText.value,
  placeholderText: t('composer.seePreviewHere'),
  media: selectedPreviewMedia.value,
}))

/**
 * The current in-flight or failed upload item being tracked (max 1 for MVP).
 * Completed uploads should fall through to the selected asset preview state.
 */
const currentUpload = computed(() => {
  const trackedUpload = uploadTempKey.value ? mediaStore.uploads[uploadTempKey.value] : undefined
  if (trackedUpload) {
    return trackedUpload.status === 'done' ? null : trackedUpload
  }

  return (
    mediaStore.uploadList.find(
      (upload) => upload.tempKey.startsWith('modal-upload-') && upload.status !== 'done',
    ) ?? null
  )
})

const normalizedUploadProgress = computed<number | null>(() => {
  if (!currentUpload.value) return null
  return Math.max(0, Math.min(100, currentUpload.value.progress ?? uploadProgress.value ?? 0))
})

const currentUploadStateLabel = computed<string | null>(() => {
  if (currentUpload.value?.status !== 'uploading') return null
  if ((normalizedUploadProgress.value ?? 0) >= 100) {
    return t('composer.media.finishingUpload')
  }
  return t('composer.media.uploadingProgress', {
    progress: Math.round(normalizedUploadProgress.value ?? 0),
  })
})

const composerInlineAttachments = computed<ComposerInlineAttachment[]>(() => {
  const localUploadAttachment: ComposerInlineAttachment[] = selectedUploadFile.value
    ? [
      {
        key: uploadTempKey.value ?? `local-upload-${selectedUploadFile.value.name}`,
        kind: 'local-upload',
        assetId: null,
        name: selectedUploadFile.value.name,
        previewUrl: selectedUploadFile.value.type.startsWith('image/') ? uploadPreviewBlob.value : null,
        isUploading: currentUpload.value?.status === 'uploading',
        uploadProgress: normalizedUploadProgress.value,
        uploadStateLabel: currentUploadStateLabel.value,
      },
    ]
    : []

  const persistedAttachments: ComposerInlineAttachment[] = picker.draftAttachmentAssets.value.map((asset) => ({
    key: asset.assetId,
    kind: 'draft',
    assetId: asset.assetId,
    name: asset.originalFilename ?? asset.assetId,
    previewUrl: asset.previewUrl ? resolveApiUrl(asset.previewUrl) : null,
    isUploading: false,
    uploadProgress: 100,
    uploadStateLabel: null,
  }))

  return [...localUploadAttachment, ...persistedAttachments]
})

const visibleInlineAttachments = computed(() => composerInlineAttachments.value.slice(0, 3))
const hiddenInlineAttachmentCount = computed(() =>
  Math.max(0, composerInlineAttachments.value.length - visibleInlineAttachments.value.length),
)

function selectChannel(channelId: string) {
  selectedChannelId.value = channelId
}

// Format hashtags helper
function appendHashtag() {
  const tag = prompt('Enter tag (e.g. #socialmedia):')
  if (tag) {
    const formatted = tag.startsWith('#') ? tag : `#${tag}`
    postText.value = postText.value ? `${postText.value} ${formatted}` : formatted
  }
}

function handleEmojiPicker() {
  postText.value = `${postText.value}${postText.value ? ' ' : ''}🙂`
}

// AI Assist helper
function handleAiAssist() {
  if (!postText.value.trim()) {
    postText.value = 'Profile Tailors is officially launching! Minimalist scheduling, analytics, and multichannel delivery designed for creators. 🚀'
    return
  }
  isAiProcessing.value = true
  setTimeout(() => {
    postText.value = `${postText.value}\n\nProgramado vía @ProfileTailors`
    isAiProcessing.value = false
  }, 800)
}

/**
 * Validates the custom schedule date and time inputs.
 * Returns an error message string on failure, or undefined on success.
 * Sets finalScheduledDate when the date is valid.
 */
function validateCustomSchedule(finalScheduledDate: Date): string | undefined {
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

  const earliestAllowed = new Date(now.value.getTime() + 5 * 60_000)
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

function resolveScheduledDate(): Date | undefined {
  if (scheduleMode.value !== 'custom') return undefined
  if (!selectedCalendarDate.value) {
    submitError.value = 'Select a date.'
    return undefined
  }
  const date = selectedCalendarDate.value.toDate(getLocalTimeZone())
  const error = validateCustomSchedule(date)
  if (error) {
    submitError.value = error
    return undefined
  }
  return date
}

async function uploadDeferredFile(): Promise<boolean> {
  if (!selectedUploadFile.value || !uploadTempKey.value) return true
  isLocalUploadInFlight.value = true
  await nextTick()
  try {
    const asset = await mediaStore.createAndUpload(
      selectedUploadFile.value,
      uploadTempKey.value,
      (pct) => {
        uploadProgress.value = pct
      },
    )
    if (isEditMode.value) assetsTouched.value = true
    mediaStore.addToSelection(asset.assetId)
    picker.draftAttachmentIds.value = [...picker.draftAttachmentIds.value, asset.assetId]
    return true
  } catch {
    submitError.value = 'Media upload failed. Please try again.'
    return false
  } finally {
    isLocalUploadInFlight.value = false
  }
}

function resetPostForm() {
  postText.value = ''
  removeFile()
  firstComment.value = ''
  picker.draftAttachmentIds.value = []
  picker.pickerSelectionIds.value = []
  picker.resetPickerSessionTracking()
}

function finalizeAfterCreate(shouldCreateAnother: boolean) {
  if (shouldCreateAnother) {
    resetPostForm()
  } else {
    emit('close')
  }
}

async function handleSchedule() {
  if (!canSubmit.value) return

  const shouldCreateAnother = createAnother.value

  isSubmitting.value = true
  submitError.value = ''

  try {
    const scheduledDate = resolveScheduledDate()
    if (submitError.value) return

    const uploadOk = selectedUploadFile.value && uploadTempKey.value
      ? await uploadDeferredFile()
      : true
    if (!uploadOk) return

    const normalizedPostText = postText.value.trim()
    const backendScheduleMode = resolveScheduleMode(scheduleMode.value)

    if (isEditMode.value && props.editingPublication) {
      await handleEditSubmit(normalizedPostText, scheduledDate, backendScheduleMode)
    } else {
      await handleCreateSubmit(normalizedPostText, scheduledDate, backendScheduleMode)
    }
  } catch (err) {
    submitError.value = err instanceof Error ? err.message : 'Unable to schedule post.'
    console.error('Error scheduling post', err)
    if (shouldCreateAnother) {
      resetPostForm()
    }
  } finally {
    isSubmitting.value = false
  }
}

async function handleEditSubmit(
  normalizedPostText: string,
  scheduledDate: Date | undefined,
  backendScheduleMode: NonNullable<Publication['scheduleMode']>,
) {
  const editingPublication = props.editingPublication
  if (!editingPublication) return

  await publishingStore.updatePost(editingPublication.id, {
    content: normalizedPostText,
    scheduledAt: scheduledDate?.toISOString(),
    priority: priorityMode.value,
    scheduleMode: backendScheduleMode,
    ...(assetsTouched.value ? { assetIds: [...picker.draftAttachmentIds.value] } : {}),
  })
  emit('updated')
  emit('close')
}

async function handleCreateSubmit(
  normalizedPostText: string,
  scheduledDate: Date | undefined,
  backendScheduleMode: NonNullable<Publication['scheduleMode']>,
) {
  await publishingStore.schedulePost({
    content: normalizedPostText,
    title: 'Post from App',
    channels: selectedProviders.value,
    scheduledAt: scheduledDate?.toISOString(),
    nextSlotAfter: scheduleMode.value === 'next' ? now.value.toISOString() : undefined,
    scheduleMode: backendScheduleMode,
    priority: priorityMode.value,
    thumbnail: selectedAssetIsImage.value
      ? (uploadPreviewBlob.value ?? selectedAssetPreviewUrl.value ?? undefined)
      : undefined,
    assetIds: [...picker.draftAttachmentIds.value],
    socialAccountId: selectedChannel.value?.accountId,
  })
  emit('created')
  finalizeAfterCreate(createAnother.value)
}
</script>

<template>
  <Teleport to="body">
    <!-- biome-ignore lint/a11y/noStaticElementInteractions: overlay backdrop, closes on outside click -->
    <div
      v-if="isOpen"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-fade-in"
      @click.self="emit('close')"
      @keydown.escape="emit('close')"
    >
      <dialog
        ref="modalContainer"
        open
        aria-modal="true"
        aria-labelledby="create-post-title"
        class="relative m-0 flex h-[min(92vh,750px)] w-full max-w-5xl flex-col overflow-hidden rounded-2xl border border-border-subtle bg-bg-surface shadow-2xl animate-zoom-in lg:flex-row"
      >
        <button type="button"
          @click="emit('close')"
          class="absolute top-4 right-4 z-50 flex size-8 items-center justify-center rounded-full border border-border-subtle bg-bg-primary text-text-secondary hover:text-text-display lg:hidden"
        >
          <X class="size-4" />
        </button>

        <div class="flex min-h-0 flex-1 flex-col space-y-6 border-b border-border-subtle p-6 lg:border-b-0 lg:border-r overflow-hidden">
          <div class="flex items-center justify-between">
              <h3 id="create-post-title" class="font-mono text-xs font-bold tracking-widest text-text-display uppercase">
                {{ isEditMode ? $t('composer.editTitle') : $t('composer.title') }}
              </h3>
            <button type="button"
              @click="emit('close')"
              class="hidden lg:flex size-7 items-center justify-center rounded-xl border border-border-subtle bg-bg-primary text-text-secondary hover:text-text-display cursor-pointer"
            >
              <X class="size-3.5" />
            </button>
          </div>

          <ComposerChannelSelector
            :channels="publishingStore.channels"
            :selected-channel-id="selectedChannelId"
            :is-edit-mode="isEditMode"
            @select="selectChannel"
          />

          <div class="flex flex-1 flex-col rounded-[24px] border border-border-visible bg-bg-primary/70 min-h-[420px]">
            <label for="create-post-text" class="sr-only">Post content</label>
            <textarea
              id="create-post-text"
              v-model="postText"
              :placeholder="$t('composer.placeholder')"
              class="min-h-[260px] w-full flex-1 resize-none bg-transparent p-5 text-sm text-text-body placeholder:text-text-secondary focus:outline-none font-sans"
              data-testid="composer-textarea"
              @dragover="handleComposerSurfaceDragOver"
              @dragleave="handleComposerSurfaceDragLeave"
              @drop="handleComposerSurfaceDrop"
              @paste="handleComposerSurfacePaste"
            ></textarea>

            <div class="border-t border-border-subtle/70 px-4 py-4">
              <div class="flex flex-wrap items-center gap-3">
                <div
                  v-for="asset in visibleInlineAttachments"
                  :key="asset.key"
                  :title="asset.name"
                  class="group relative h-[118px] w-[118px] overflow-hidden rounded-[18px] border border-border-visible bg-bg-primary/50"
                  :data-testid="asset.kind === 'draft' ? `inline-attachment-${asset.assetId}` : 'inline-local-upload'"
                >
                  <img
                    v-if="asset.previewUrl"
                    :src="asset.previewUrl"
                    alt="Selected media preview"
                    class="h-full w-full object-cover"
                    data-testid="attachment-preview-image"
                  >
                  <div
                    v-else
                    class="flex h-full w-full items-center justify-center bg-bg-primary/40 text-text-secondary"
                  >
                    <ImageIcon class="size-6" />
                  </div>

                  <div
                    v-if="asset.kind === 'local-upload' ? isLocalUploadInFlight || asset.isUploading : asset.isUploading"
                    class="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-black/55 px-3 text-center backdrop-blur-sm"
                    data-testid="inline-upload-overlay"
                  >
                    <Spinner class="size-5 text-[var(--upload-accent)]" />
                    <p class="text-xs font-medium leading-tight text-white">
                      {{ asset.kind === 'local-upload' ? (currentUploadStateLabel ?? asset.uploadStateLabel ?? t('composer.media.uploadingProgress', { progress: Math.round(normalizedUploadProgress ?? 0) })) : asset.uploadStateLabel }}
                    </p>
                    <Progress
                      :model-value="asset.kind === 'local-upload' ? (normalizedUploadProgress ?? 0) : (asset.uploadProgress ?? 0)"
                      class="h-1 w-full bg-white/15 [&_[data-slot=progress-indicator]]:bg-[var(--upload-accent)]"
                    />
                    <p class="text-[10px] leading-tight text-white/70">
                      {{ t('composer.media.keepEditingWhileUploading') }}
                    </p>
                  </div>

                  <button
                    type="button"
                    class="absolute right-2 top-2 flex size-7 items-center justify-center rounded-full bg-black/70 text-white transition hover:bg-black/85"
                    :data-testid="asset.kind === 'draft' ? `attachment-remove-${asset.assetId}` : 'attachment-remove-local-upload'"
                    :aria-label="t('composer.media.removeAttachment', { name: asset.name })"
                    @click="asset.kind === 'draft' ? picker.removeDraftAttachment(asset.assetId) : removeFile()"
                  >
                    <X class="size-3.5" />
                  </button>
                </div>

                <div
                  v-if="hiddenInlineAttachmentCount > 0"
                  class="flex h-[118px] w-[118px] items-center justify-center rounded-[18px] border border-dashed border-border-visible bg-bg-primary/30 font-mono text-xs tracking-[0.2em] text-text-secondary"
                  data-testid="inline-attachment-overflow"
                >
                  +{{ hiddenInlineAttachmentCount }}
                </div>

                <button
                  type="button"
                  class="flex h-[118px] w-[118px] cursor-pointer flex-col items-center justify-center rounded-[18px] border border-dashed px-4 text-center transition"
                  :class="isDropzoneActive ? 'border-[var(--upload-accent)] bg-[var(--upload-accent)]/10' : 'border-border-visible bg-bg-primary/30 hover:border-text-display/40'"
                  data-testid="composer-inline-dropzone"
                  @click="openUploadPicker"
                  @dragover="handleDropzoneDragOver"
                  @dragleave="handleDropzoneDragLeave"
                  @drop="handleDropzoneDrop"
                >
                  <ImageIcon class="mb-3 size-6 text-text-secondary" />
                  <p class="text-[12px] leading-5 text-text-secondary">
                    {{ t('composer.media.dropzoneTitle') }}
                    <span class="block font-medium text-[var(--upload-accent)]">{{ t('composer.media.dropzoneBody') }}</span>
                  </p>
                </button>
              </div>

              <p
                v-if="mediaError"
                role="alert"
                aria-live="polite"
                class="mt-2 rounded-xl border border-error/30 bg-error/10 px-3 py-2 text-xs text-error"
              >
                {{ mediaError }}
              </p>

              <div class="mt-4 flex items-center justify-between gap-4">
                <div class="flex items-center gap-1 text-text-secondary">
                  <button
                    type="button"
                    class="sr-only"
                    data-testid="add-media-button"
                    @click="openMediaLibrary"
                  >
                    {{ t('composer.media.addMedia') }}
                  </button>
                  <button
                    type="button"
                    class="flex h-10 w-10 items-center justify-center rounded-xl border border-border-visible bg-bg-surface transition hover:border-text-display hover:text-text-display"
                    data-testid="composer-upload-trigger"
                    @click="openUploadPicker"
                  >
                    <ImageIcon class="size-4" />
                  </button>

                  <Popover v-model:open="isMediaSourcesOpen">
                    <PopoverTrigger as-child>
                      <button
                        type="button"
                        class="flex h-10 w-10 items-center justify-center rounded-xl border border-border-visible bg-bg-surface transition hover:border-text-display hover:text-text-display"
                        data-testid="composer-sources-trigger"
                      >
                        <ChevronDown class="size-4" />
                      </button>
                    </PopoverTrigger>
                    <PopoverContent align="start" class="w-60 rounded-2xl border-border-subtle bg-bg-surface p-2">
                      <div class="space-y-1">
                        <button
                          type="button"
                          class="flex w-full items-center justify-between rounded-xl px-3 py-2 text-sm text-text-body transition hover:bg-bg-primary"
                          data-testid="composer-source-library"
                          @click="openMediaLibrary"
                        >
                          <span>{{ t('composer.media.sourceLibrary') }}</span>
                        </button>
                        <button
                          v-if="picker.effectiveProvider.value === 'unsplash'"
                          type="button"
                          class="flex w-full items-center justify-between rounded-xl px-3 py-2 text-sm text-text-body transition hover:bg-bg-primary"
                          data-testid="composer-source-unsplash"
                          @click="openUnsplashLibrary"
                        >
                          <span>{{ t('composer.media.sourceExternal') }}</span>
                        </button>
                      </div>
                    </PopoverContent>
                  </Popover>

                  <button type="button"
                    @click="handleEmojiPicker"
                    class="flex h-10 w-10 items-center justify-center rounded-xl text-text-secondary transition hover:bg-bg-surface hover:text-text-display"
                    title="Open emoji picker"
                  >
                    <Smile class="size-4" />
                  </button>
                  <button type="button"
                    @click="appendHashtag"
                    class="flex h-10 w-10 items-center justify-center rounded-xl text-text-secondary transition hover:bg-bg-surface hover:text-text-display"
                    title="Insert tag"
                  >
                    <Hash class="size-4" />
                  </button>
                  <button type="button"
                    @click="handleAiAssist"
                    class="flex h-10 items-center gap-1 rounded-xl px-2 text-text-secondary transition hover:bg-bg-surface hover:text-text-display"
                    title="AI Assist"
                  >
                    <Sparkles class="size-4" />
                    <span class="font-mono text-[8px] uppercase tracking-wider font-bold">AI</span>
                  </button>
                </div>

                <span
                  class="font-mono text-[10px]"
                  :class="isTextTooLong ? 'text-error font-bold' : 'text-text-secondary'"
                >
                  {{ charsRemaining }} / {{ charLimit }}
                </span>
              </div>

              <input
                id="create-post-file-input"
                ref="pickerSessionUploadInput"
                data-testid="picker-upload-input"
                type="file"
                class="hidden"
                accept="image/jpeg,image/png,image/gif,image/webp,video/mp4"
                aria-label="Upload media file"
                @change="handleFileSelect"
              >
            </div>
          </div>
          <p
            v-if="picker.isAttachmentLimitExceeded.value"
            data-testid="attachment-limit-warning"
            class="rounded-xl border border-error/40 bg-error/10 px-3 py-2 text-xs text-error"
          >
            {{ t('composer.media.limitWarning', {
              current: picker.draftAttachmentIds.value.length,
              max: Number.isFinite(picker.effectiveAttachmentLimit.value) ? picker.effectiveAttachmentLimit.value : t('composer.media.limitInfinite'),
            }) }}
          </p>
          <ComposerMediaPickerShell
            :is-open="picker.isMediaPickerOpen.value"
            :active-source="picker.activeMediaPickerSource.value"
            :collection-state="picker.mediaPickerCollectionState.value"
            :assets="picker.pickerAssets.value"
            :provider="picker.effectiveProvider.value"
            :apply-disabled="picker.isPickerSelectionOverLimit.value"
            :apply-disabled-message="picker.isPickerSelectionOverLimit.value ? t('composer.picker.applyDisabledLimit') : null"
            @toggle-asset="picker.togglePickerAsset($event.assetId)"
            @apply-selection="picker.applyPickerSelection()"
            @provider-search="picker.handleProviderSearch"
            @set-active-source="picker.setActiveMediaPickerSource($event.source)"
            @provider-import="picker.handleProviderImport"
            @close="picker.closeMediaPicker()"
          >
            <template
              v-if="picker.effectiveProvider.value === 'unsplash'"
              #provider
            >
              <MediaProviderPanel
                :results="picker.providerResults.value"
                :is-searching="picker.providerSearching.value"
                :search-error="picker.providerSearchError.value"
                @provider-import="picker.handleProviderImport"
              />
            </template>
          </ComposerMediaPickerShell>

          <div class="space-y-2">
            <label for="create-post-first-comment" class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block">
              {{ $t('composer.firstComment') }}
            </label>
            <input
              id="create-post-first-comment"
              v-model="firstComment"
              type="text"
              :placeholder="$t('composer.firstCommentPlaceholder')"
              class="w-full bg-bg-primary border border-border-visible rounded-xl px-4 py-2.5 text-xs text-text-body placeholder:text-text-secondary focus:outline-none focus:border-text-display font-sans"
            />
          </div>
        </div>

        <PostPreviewPanel
          provider="linkedin"
          :title="$t('composer.linkedinPreview')"
          :linkedin-preview="linkedinPreview"
        >
          <template #footer>
            <div class="border-t border-border-subtle pt-6 space-y-4">
              <ComposerSchedulePanel
                v-model:schedule-mode="scheduleMode"
                v-model:selected-calendar-date="selectedCalendarDate"
                v-model:schedule-time="scheduleTime"
                v-model:is-date-picker-open="isDatePickerOpen"
                :today-date-value="todayDateValue"
                :min-time-for-date="minTimeForDate"
                :selected-date-label="selectedDateLabel"
                :schedule-helper-text="scheduleHelperText"
              />

              <div class="flex items-center justify-between text-[10px] font-mono text-text-secondary px-1">
                <label class="flex items-center gap-1.5 cursor-pointer hover:text-text-display select-none">
                  <input type="checkbox" v-model="priorityMode" class="accent-text-display" />
                  <span>Priority Queue</span>
                </label>
                <label v-if="!isEditMode" class="flex items-center gap-1.5 cursor-pointer hover:text-text-display select-none">
                  <input type="checkbox" v-model="createAnother" class="accent-text-display" />
                  <span>Create Another</span>
                </label>
              </div>

              <p v-if="submitError" class="rounded-xl border border-error/30 bg-error/10 px-3 py-2 text-xs text-error">
                {{ submitError }}
              </p>

              <div class="grid grid-cols-3 gap-3">
                <button type="button"
                  @click="emit('close')"
                  class="col-span-1 border border-border-visible text-text-body hover:border-text-display hover:text-text-display font-mono text-[10px] font-bold uppercase tracking-wider rounded-full py-2.5 transition-all text-center cursor-pointer"
                >
                  {{ $t('composer.cancelBtn') }}
                </button>

                <Button
                  @click="handleSchedule"
                  :disabled="!canSubmit"
                  class="col-span-2 justify-center py-2.5 font-bold"
                >
                  {{ isEditMode ? $t('composer.saveChanges') : scheduleMode === 'now' ? 'Schedule Now' : scheduleMode === 'next' ? 'Next Schedule' : $t('composer.scheduleBtn') }}
                </Button>
              </div>
            </div>
          </template>
        </PostPreviewPanel>
      </dialog>
    </div>
  </Teleport>
</template>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.2s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
.animate-zoom-in {
  animation: zoomIn 0.25s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
.animate-slide-down {
  animation: slideDown 0.15s ease-out forwards;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes zoomIn {
  from { transform: scale(0.96); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
@keyframes slideDown {
  from { height: 0; opacity: 0; overflow: hidden; }
  to { height: 38px; opacity: 1; }
}
</style>
