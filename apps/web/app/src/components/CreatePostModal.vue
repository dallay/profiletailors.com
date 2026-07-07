<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import type { DateValue } from 'reka-ui'
import { useFocusTrap } from '@/composables/useFocusTrap'
import { CalendarDate, getLocalTimeZone, today } from '@internationalized/date'
import {
  AlertCircle,
  Calendar as CalendarIcon,
  Check,
  Hash,
  Image as ImageIcon,
  Loader2,
  RotateCcw,
  Sparkles,
  X,
} from '@lucide/vue'
import { useAuthStore } from '@/stores/auth'
import { usePublishingStore, type Publication, type Channel } from '@/stores/publishing'
import { useWorkspaceStore } from '@/stores/workspace'
import { useMediaStore, type MediaAssetStatus } from '@/stores/media'
import { proxyImageUrl, resolveApiUrl } from '@/lib/auth-api'
import PostPreviewPanel from '@/components/composer/PostPreviewPanel.vue'
import type { LinkedInPreviewModel, PostPreviewMedia } from '@/components/composer/post-preview.types'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import ComposerMediaPickerShell from '@/components/composer/ComposerMediaPickerShell.vue'
import type {
  ComposerMediaPickerAsset,
  ComposerMediaPickerCollectionState,
} from '@/components/composer/composer-media-picker.types'
import MediaProviderPanel, {
  type ProviderSearchResultViewModel,
} from '@/features/media-composer/providers/MediaProviderPanel.vue'

type ComposerScheduleMode = 'now' | 'next' | 'custom'

const props = withDefaults(
  defineProps<{
    isOpen: boolean
    initialDate?: string // ISO string
    editingPublication?: Publication // Pre-fill for editing
    /**
     * Provider to surface as a browsable source inside the media picker.
     * The parent owns the feature flag and only passes `provider="unsplash"`
     * when the provider is configured and enabled.
     */
    provider?: 'unsplash' | null
    /**
     * Whether the Unsplash provider is enabled by feature flag.
     * Same purpose as `provider`, kept distinct so the modal can react
     * to capability refreshes without re-creating the modal.
     */
    isUnsplashProviderEnabled?: boolean
  }>(),
  {
    isOpen: false,
    provider: null,
    isUnsplashProviderEnabled: false,
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

// State
const postText = ref('')
const selectedChannelId = ref<string | null>(null)
const avatarLoadFailed = ref<Record<string, boolean>>({})
const submitError = ref('')
const firstComment = ref('')
const createAnother = ref(false)
const priorityMode = ref(false)
const scheduleMode = ref<ComposerScheduleMode>('now')
const isDatePickerOpen = ref(false)

const modalContainer = ref<HTMLElement | null>(null)
const { activate: activateFocusTrap, deactivate: deactivateFocusTrap } = useFocusTrap(modalContainer, () => emit('close'))

// ---------------------------------------------------------------------------
// Edit mode
// ---------------------------------------------------------------------------
const isEditMode = computed(() => !!props.editingPublication)
const _isCreating = computed(() => !isEditMode.value)
const assetsTouched = ref(false)
let suppressAssetTouchTracking = false

// ---------------------------------------------------------------------------
// Media picker state
// ---------------------------------------------------------------------------
const isMediaPickerOpen = ref(false)
const mediaPickerCollectionState = ref<ComposerMediaPickerCollectionState>('LOADING')
const draftAttachmentIds = ref<string[]>([])
const pickerSelectionIds = ref<string[]>([])
const pickerSessionUploadInput = ref<HTMLInputElement | null>(null)
const autoStagedAssetIds = ref<string[]>([])
const manuallyDeselectedAutoStageIds = ref<string[]>([])
const pendingPickerAssets = ref<string[]>([])
const reconciliationPollers = new Map<string, ReturnType<typeof setTimeout>>()
const pickerSessionActiveAssetIds = new Set<string>()

const RECONCILIATION_POLL_INTERVAL_MS = 1000
const RECONCILIATION_MAX_ATTEMPTS = 5

// ---------------------------------------------------------------------------
// Work Unit 3 — Unsplash provider, capability-aware attachment limit
// ---------------------------------------------------------------------------

/**
 * Parent-owned provider feature flag. The shell only renders the provider
 * tab when `provider !== null` AND `isUnsplashProviderEnabled` is true.
 */
const effectiveProvider = computed<'unsplash' | null>(() => {
  if (!props.isUnsplashProviderEnabled) return null
  if (props.provider !== 'unsplash') return null
  return 'unsplash'
})

/**
 * Provider search state. The modal owns search orchestration: it captures
 * the typed query, exposes search results to the provider panel, and emits
 * `provider-import` resolution through the picker shell into asset import.
 */
const providerQuery = ref('')
const providerResults = ref<ProviderSearchResultViewModel[]>([])
const providerSearching = ref(false)
const providerSearchError = ref<string | null>(null)
/** externalId → assetId mapping for already-reconciled provider imports. */
const providerImportResolution = ref<Record<string, string>>({})

/**
 * Selected channels for limit evaluation.
 *
 * Today the composer is single-channel, but the spec is structured around
 * multiple selected channels. We compute the strictest limit across all
 * active channels so that adding multi-channel later is a pure data change.
 */
const activeChannels = computed<Channel[]>(
  () => publishingStore.channels.filter((ch) => ch.status === 'ACTIVE'),
)

/**
 * Effective attachment limit = min(allActiveChannels[].maxAttachments).
 * Falls back to Infinity when no active channels (limit is effectively
 * unbounded since publish will already be blocked by `canSubmit`).
 */
const effectiveAttachmentLimit = computed<number>(() => {
  if (activeChannels.value.length === 0) return Number.POSITIVE_INFINITY
  const limits = activeChannels.value
    .map((ch) => ch.maxAttachments)
    .filter((limit): limit is number => typeof limit === 'number' && Number.isFinite(limit))
  if (limits.length === 0) return Number.POSITIVE_INFINITY
  return Math.min(...limits)
})

const isAttachmentLimitExceeded = computed<boolean>(() => {
  const limit = effectiveAttachmentLimit.value
  if (!Number.isFinite(limit)) return false
  return draftAttachmentIds.value.length > limit
})

const isPickerSelectionOverLimit = computed<boolean>(() => {
  const limit = effectiveAttachmentLimit.value
  if (!Number.isFinite(limit)) return false
  return pickerSelectionIds.value.length > limit
})

// ---------------------------------------------------------------------------
// Legacy upload state (preserved for later slices)
// ---------------------------------------------------------------------------
// Blob URL for instant preview during upload (purely transient UX)
const uploadPreviewBlob = ref<string | null>(null)
const selectedUploadFile = ref<File | null>(null)
const uploadTempKey = ref<string | null>(null)
const uploadProgress = ref(0)

function clearUploadPreviewBlob() {
  if (uploadPreviewBlob.value) {
    URL.revokeObjectURL(uploadPreviewBlob.value)
    uploadPreviewBlob.value = null
  }
}

function stopReconciliationPoller(assetId: string) {
  const timerId = reconciliationPollers.get(assetId)
  if (timerId) {
    clearTimeout(timerId)
    reconciliationPollers.delete(assetId)
  }
}

function stopAllReconciliationPollers() {
  for (const assetId of reconciliationPollers.keys()) {
    stopReconciliationPoller(assetId)
  }
}

function resetPickerSessionTracking() {
  stopAllReconciliationPollers()
  pickerSessionActiveAssetIds.clear()
  pendingPickerAssets.value = []
  autoStagedAssetIds.value = []
  manuallyDeselectedAutoStageIds.value = []
}

// Calendar selector state
const selectedCalendarDate = ref<DateValue>()
const scheduleTime = ref('10:00')

// ---------------------------------------------------------------------------
// Live clock for date/time minimum validation
// ---------------------------------------------------------------------------
const now = ref(new Date())
let timer: ReturnType<typeof setInterval>

onMounted(() => {
  timer = setInterval(() => {
    now.value = new Date()
  }, 60_000)
})

onUnmounted(() => {
  clearInterval(timer)
  stopAllReconciliationPollers()
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
  draftAttachmentIds.value = []
  pickerSelectionIds.value = []
  resetPickerSessionTracking()
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
    draftAttachmentIds.value = resolvedAssetIds.filter((assetId): assetId is string => assetId !== null)
  }

  const pubChannelId = pub.accountId
    ?? publishingStore.channels.find((ch) => pub.channels?.includes(ch.provider))?.id
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
  draftAttachmentIds.value = []
  pickerSelectionIds.value = []
  resetPickerSessionTracking()
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
  isDatePickerOpen.value = false
  avatarLoadFailed.value = {}
  clearUploadPreviewBlob()
  selectedUploadFile.value = null
  uploadTempKey.value = null
  uploadProgress.value = 0
  isMediaPickerOpen.value = false
  mediaPickerCollectionState.value = 'LOADING'

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

// Computed
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
  selectedChannel.value ? [selectedChannel.value.provider] : [],
)
const selectedPreviewProvider = 'linkedin'
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
    !isAttachmentLimitExceeded.value
  )
})

// Methods
function handleFileSelect(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files?.length) {
    const files = Array.from(target.files)
    if (isMediaPickerOpen.value) {
      void handlePickerUploadSelection(files)
    } else {
      addFiles(files)
    }
    target.value = ''
  }
}

function addFiles(filesList: File[]) {
  if (isEditMode.value) assetsTouched.value = true
  const file = filesList.find((file) => {
    const isSupported =
      file.type.startsWith('image/') ||
      file.type === 'video/mp4' ||
      file.type === 'image/webp'
    const isUnderLimit = file.size <= 10 * 1024 * 1024 // 10MB
    if (!isSupported) alert('Unsupported media format. Supported formats: JPEG, PNG, WEBP, GIF, MP4.')
    if (!isUnderLimit) alert('File size exceeds 10MB limit.')
    return isSupported && isUnderLimit
  })

  if (!file) return

  // Limit to max 1 image for LinkedIn MVP simple preview.
  // File is stored locally for deferred upload — no server call until Schedule Post.
  clearUploadPreviewBlob()
  selectedUploadFile.value = file
  uploadPreviewBlob.value = URL.createObjectURL(file)
  uploadTempKey.value = `modal-upload-${Date.now()}`
  uploadProgress.value = 0
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
  // Remove all selected assets from the media store
  mediaStore.clearSelection()

  // Clean up any in-progress upload tracking
  clearUploadPreviewBlob()
  selectedUploadFile.value = null
  uploadTempKey.value = null
  uploadProgress.value = 0
  mediaStore.clearUploads()
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

  const first = draftAttachmentIds.value
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

  const first = draftAttachmentIds.value
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

  const first = draftAttachmentIds.value
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
  return mediaStore.uploadList.find((upload) => upload.status !== 'done') ?? null
})

function selectChannel(channelId: string) {
  selectedChannelId.value = channelId
}

function onChannelAvatarError(channelId: string) {
  avatarLoadFailed.value[channelId] = true
}

function shouldShowChannelAvatar(channelId: string, avatarUrl?: string): boolean {
  return !!(avatarUrl && !avatarLoadFailed.value[channelId])
}

// Format hashtags helper
function appendHashtag() {
  const tag = prompt('Enter tag (e.g. #socialmedia):')
  if (tag) {
    const formatted = tag.startsWith('#') ? tag : `#${tag}`
    postText.value = postText.value ? `${postText.value} ${formatted}` : formatted
  }
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

// ---------------------------------------------------------------------------
// Schedule helpers — extracted to reduce cognitive complexity
// ---------------------------------------------------------------------------

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
    return true
  } catch {
    submitError.value = 'Media upload failed. Please try again.'
    return false
  }
}

function resetPostForm() {
  postText.value = ''
  removeFile()
  firstComment.value = ''
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

function getLibraryCollectionState(assetCount: number): ComposerMediaPickerCollectionState {
  if (mediaStore.isLoading) return 'LOADING'
  if (mediaStore.loadError) return 'ERROR'
  if (assetCount === 0) return 'EMPTY'
  return 'READY'
}

function toPickerAssetStatus(status: MediaAssetStatus): ComposerMediaPickerAsset['status'] {
  if (status === 'READY') return 'READY'
  if (status === 'FAILED') return 'FAILED'
  return 'PROCESSING'
}

function mapAssetToPickerAsset(assetId: string): ComposerMediaPickerAsset | null {
  const asset = mediaStore.assetsById[assetId]
  if (!asset) return null

  const status = toPickerAssetStatus(asset.status)

  return {
    assetId: asset.assetId,
    name: asset.originalFilename ?? asset.assetId,
    mediaType: asset.mediaType,
    status,
    previewUrl: asset.previewUrl ? resolveApiUrl(asset.previewUrl) : null,
    selectable: status === 'READY',
    selected: pickerSelectionIds.value.includes(asset.assetId),
    sourceType: asset.sourceType,
  }
}

const pickerAssets = computed(() => {
  const mergedAssetIds = [...pendingPickerAssets.value, ...mediaStore.assetIds]
  const uniqueAssetIds = [...new Set(mergedAssetIds)]

  return uniqueAssetIds
    .map((assetId) => mapAssetToPickerAsset(assetId))
    .filter((asset): asset is ComposerMediaPickerAsset => asset !== null)
})

const draftAttachmentAssets = computed(() =>
  draftAttachmentIds.value
    .map((assetId) => mediaStore.assetsById[assetId])
    .filter((asset) => asset !== undefined),
)

function addPendingPickerAsset(assetId: string) {
  if (!pendingPickerAssets.value.includes(assetId)) {
    pendingPickerAssets.value = [assetId, ...pendingPickerAssets.value]
  }
}

function clearPendingPickerAsset(assetId: string) {
  pendingPickerAssets.value = pendingPickerAssets.value.filter((id) => id !== assetId)
}

function ensurePickerAssetVisible(assetId: string) {
  addPendingPickerAsset(assetId)
}

function isAssetSelectableStatus(status: MediaAssetStatus): boolean {
  return toPickerAssetStatus(status) === 'READY'
}

function stageAssetOnce(assetId: string) {
  if (autoStagedAssetIds.value.includes(assetId)) return
  if (manuallyDeselectedAutoStageIds.value.includes(assetId)) return
  if (pickerSelectionIds.value.includes(assetId)) return

  pickerSelectionIds.value = [...pickerSelectionIds.value, assetId]
  autoStagedAssetIds.value = [...autoStagedAssetIds.value, assetId]
}

function scheduleAssetReconciliation(assetId: string, attempt = 1) {
  if (!pickerSessionActiveAssetIds.has(assetId)) return
  if (reconciliationPollers.has(assetId)) return
  if (attempt > RECONCILIATION_MAX_ATTEMPTS) return

  const timerId = setTimeout(async () => {
    reconciliationPollers.delete(assetId)
    if (!pickerSessionActiveAssetIds.has(assetId)) return

    try {
      const asset = await mediaStore.loadAsset(assetId)
      mediaStore.upsertAsset(asset)
      ensurePickerAssetVisible(assetId)

      if (isAssetSelectableStatus(asset.status)) {
        stageAssetOnce(assetId)
        clearPendingPickerAsset(assetId)
        return
      }

      if (toPickerAssetStatus(asset.status) === 'FAILED') {
        clearPendingPickerAsset(assetId)
        return
      }
    } catch {
      // transient fetch errors retry within the same bounded policy
    }

    if (!pickerSessionActiveAssetIds.has(assetId)) return
    if (attempt < RECONCILIATION_MAX_ATTEMPTS) {
      scheduleAssetReconciliation(assetId, attempt + 1)
      return
    }

    clearPendingPickerAsset(assetId)
  }, RECONCILIATION_POLL_INTERVAL_MS)

  reconciliationPollers.set(assetId, timerId)
}

function startAssetReconciliation(assetId: string) {
  stopReconciliationPoller(assetId)
  pickerSessionActiveAssetIds.add(assetId)
  ensurePickerAssetVisible(assetId)

  const existingAsset = mediaStore.assetsById[assetId]
  if (existingAsset && isAssetSelectableStatus(existingAsset.status)) {
    stageAssetOnce(assetId)
    clearPendingPickerAsset(assetId)
    return
  }

  scheduleAssetReconciliation(assetId)
}

async function handlePickerUploadSelection(filesList: File[]) {
  const file = filesList.find((candidate) => candidate.type.startsWith('image/') || candidate.type === 'video/mp4' || candidate.type === 'image/webp')
  if (!file) return

  const tempKey = `picker-upload-${Date.now()}`
  try {
    const createdAsset = await mediaStore.createAndUpload(file, tempKey, () => {})
    mediaStore.upsertAsset(createdAsset)
    ensurePickerAssetVisible(createdAsset.assetId)
    mediaPickerCollectionState.value = 'READY'

    if (isAssetSelectableStatus(createdAsset.status)) {
      stageAssetOnce(createdAsset.assetId)
      clearPendingPickerAsset(createdAsset.assetId)
      return
    }

    startAssetReconciliation(createdAsset.assetId)
  } catch {
    submitError.value = 'Media upload failed. Please try again.'
    mediaPickerCollectionState.value = 'ERROR'
  }
}

function openMediaPicker() {
  pickerSelectionIds.value = [...draftAttachmentIds.value]
  isMediaPickerOpen.value = true
  mediaPickerCollectionState.value = pendingPickerAssets.value.length > 0 ? 'READY' : 'LOADING'
  stopAllReconciliationPollers()
  pickerSessionActiveAssetIds.clear()

  for (const assetId of pendingPickerAssets.value) {
    startAssetReconciliation(assetId)
  }

  mediaStore.loadAssets('READY,PENDING_UPLOAD,UPLOADING,FAILED')
    .catch(() => {
      // state derived below from store error
    })
    .finally(() => {
      mediaPickerCollectionState.value = getLibraryCollectionState(mediaStore.assetIds.length)
    })
}

function closeMediaPicker() {
  isMediaPickerOpen.value = false
  pickerSelectionIds.value = []
  stopAllReconciliationPollers()
  pickerSessionActiveAssetIds.clear()
}

// ---------------------------------------------------------------------------
// Work Unit 3 — provider search/import orchestration (parent-owned)
// ---------------------------------------------------------------------------

/**
 * Captures provider-search intent. Real search happens in the backend client;
 * the modal exposes only the typed interaction and a result list. The
 * synthetic result path is explicitly guarded: it only runs in DEV/test, and
 * `providerSearchError` surfaces a clear message in production when no real
 * Unsplash search client is wired.
 */
function handleProviderSearch(payload: { query: string }) {
  const q = payload.query.trim()
  providerQuery.value = q
  providerSearchError.value = null
  providerSearching.value = false

  if (!q) {
    providerResults.value = []
    return
  }

  if (!import.meta.env.DEV && !import.meta.env.MODE?.startsWith('test')) {
    providerResults.value = []
    providerSearchError.value =
      'Unsplash search is not configured. Wire the backend search client before enabling this provider.'
    return
  }

  providerResults.value = [
    { externalId: `${q}-1`, name: `${q} photo one`, previewUrl: null, authorName: 'Test author' },
    { externalId: `${q}-2`, name: `${q} photo two`, previewUrl: null, authorName: 'Test author' },
  ]
}

/**
 * Provider-import orchestration. The picker MUST remain open after emit so
 * the author can continue staged multi-selection. We synthesize a persisted
 * asset ID for the imported external result and route it through the same
 * reconciliation pipeline as uploads.
 *
 * In production, the parent would POST to a backend import endpoint that
 * returns the persisted asset; for now we generate a deterministic UUID
 * that the polling layer resolves through `mediaStore.loadAsset()`. The
 * synthetic path is guarded: it only runs in DEV/test — production callers
 * must wire a real import client before the flag can ship.
 */
async function handleProviderImport(payload: { externalId: string }): Promise<void> {
  if (!import.meta.env.DEV && !import.meta.env.MODE?.startsWith('test')) {
    providerSearchError.value =
      'Unsplash import is not configured. Wire the backend import client before enabling this provider.'
    return
  }

  const syntheticAssetId = `unsplash-${payload.externalId}`
  providerImportResolution.value = {
    ...providerImportResolution.value,
    [payload.externalId]: syntheticAssetId,
  }

  // Seed a non-READY persisted asset so the reconciliation pipeline has
  // something to poll; the asset becomes READY after loadAsset resolves.
  mediaStore.upsertAsset({
    assetId: syntheticAssetId,
    workspaceId: useMediaStoreWorkspaceId(),
    sourceType: 'EXTERNAL',
    mediaType: 'image/jpeg',
    status: 'PENDING_UPLOAD',
    originalFilename: `${payload.externalId}.jpg`,
    fileSizeBytes: null,
    createdAt: new Date().toISOString(),
    previewUrl: null,
    sourceProvider: 'unsplash',
    externalId: payload.externalId,
  })

  ensurePickerAssetVisible(syntheticAssetId)
  mediaPickerCollectionState.value = 'READY'
  startAssetReconciliation(syntheticAssetId)
}

/** Helper kept tiny to make handleProviderImport easier to mock in tests. */
function useMediaStoreWorkspaceId(): string {
  const workspaceStore = useWorkspaceStore()
  return workspaceStore.activeWorkspaceId ?? 'ws-local'
}

// Expose for tests so they can simulate an import without touching the
// internal `v-model` of the provider panel search input.
defineExpose({
  __effectiveProvider: effectiveProvider,
  __effectiveAttachmentLimit: effectiveAttachmentLimit,
  __isAttachmentLimitExceeded: isAttachmentLimitExceeded,
  __draftAttachmentIds: draftAttachmentIds,
})

function togglePickerAsset(assetId: string) {
  const index = pickerSelectionIds.value.indexOf(assetId)
  if (index >= 0) {
    pickerSelectionIds.value = pickerSelectionIds.value.filter((id) => id !== assetId)
    if (autoStagedAssetIds.value.includes(assetId) && !manuallyDeselectedAutoStageIds.value.includes(assetId)) {
      manuallyDeselectedAutoStageIds.value = [...manuallyDeselectedAutoStageIds.value, assetId]
    }
    return
  }

  pickerSelectionIds.value = [...pickerSelectionIds.value, assetId]
}

function applyPickerSelection(assetIds: string[]) {
  // Block apply when the staged selection exceeds the strictest channel
  // limit. Preserve attachments by NOT closing the picker — let the
  // author remove attachments or pick a different channel.
  if (assetIds.length > effectiveAttachmentLimit.value) {
    return
  }
  draftAttachmentIds.value = [...assetIds]
  assetsTouched.value = true
  stopAllReconciliationPollers()
  pickerSessionActiveAssetIds.clear()
  closeMediaPicker()
}

function removeDraftAttachment(assetId: string) {
  draftAttachmentIds.value = draftAttachmentIds.value.filter((id) => id !== assetId)
  assetsTouched.value = true
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
    ...(assetsTouched.value ? { assetIds: [...draftAttachmentIds.value] } : {}),
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
    assetIds: [...draftAttachmentIds.value],
    socialAccountId: selectedChannel.value?.accountId,
  })
  emit('created')
  finalizeAfterCreate(createAnother.value)
}
</script>

<template>
  <Teleport to="body">
    <!-- Modal Backdrop — click.self closes modal -->
    <!-- biome-ignore lint/a11y/noStaticElementInteractions: overlay backdrop, closes on outside click -->
    <div
      v-if="isOpen"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-fade-in"
      @click.self="emit('close')"
      @keydown.escape="emit('close')"
    >
      <div
        ref="modalContainer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-post-title"
        class="flex flex-col lg:flex-row w-full max-w-5xl max-h-[90vh] lg:h-[750px] bg-bg-surface border border-border-subtle rounded-2xl overflow-y-auto lg:overflow-hidden shadow-2xl animate-zoom-in m-0 relative"
      >
        <!-- Close Button (Absolute Mobile) -->
        <button
          @click="emit('close')"
          class="absolute top-4 right-4 z-50 flex size-8 items-center justify-center rounded-full border border-border-subtle bg-bg-primary text-text-secondary hover:text-text-display lg:hidden"
        >
          <X class="size-4" />
        </button>

        <!-- Left Column: Composer Editor -->
        <div class="flex-1 flex flex-col border-b lg:border-b-0 lg:border-r border-border-subtle p-6 overflow-y-auto space-y-6">
          <div class="flex items-center justify-between">
              <h3 id="create-post-title" class="font-mono text-xs font-bold tracking-widest text-text-display uppercase">
                {{ isEditMode ? $t('composer.editTitle') : $t('composer.title') }}
              </h3>
            <button
              @click="emit('close')"
              class="hidden lg:flex size-7 items-center justify-center rounded-xl border border-border-subtle bg-bg-primary text-text-secondary hover:text-text-display cursor-pointer"
            >
              <X class="size-3.5" />
            </button>
          </div>

          <!-- Channel Selection -->
          <div class="space-y-2">
            <span class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block">
              {{ $t('dashboard.selectChannels') }}
            </span>
            <div class="flex flex-wrap gap-2 items-center">
              <button
                v-for="ch in publishingStore.channels.filter(ch => ch.status === 'ACTIVE')"
                :key="ch.id"
                @click="isEditMode ? undefined : selectChannel(ch.id)"
                :disabled="isEditMode"
                class="relative flex items-center gap-2 border rounded-full px-3 py-1.5 font-mono text-[10px] tracking-wide transition-all"
                :class="[
                  selectedChannelId === ch.id
                    ? 'border-text-display bg-bg-primary text-text-display font-bold'
                    : 'border-border-visible text-text-secondary hover:text-text-display bg-bg-primary/50',
                  isEditMode ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer',
                ]"
                :data-edit-disabled="isEditMode ? 'true' : 'false'"
              >
                <img
                  v-if="shouldShowChannelAvatar(ch.id, ch.avatarUrl)"
                  :src="proxyImageUrl(ch.avatarUrl ?? '')"
                  :alt="`${ch.name} avatar`"
                  class="size-4.5 rounded-full object-cover border border-border-subtle"
                  @error="onChannelAvatarError(ch.id)"
                />
                <span
                  v-else
                  class="flex size-4.5 shrink-0 items-center justify-center rounded-full border border-border-visible bg-bg-primary font-mono text-[7px] font-bold uppercase text-text-display"
                >
                  {{ ch.provider === 'linkedin' ? 'in' : ch.provider.charAt(0) }}
                </span>
                <span class="max-w-[120px] truncate">{{ ch.name }}</span>
                <span
                  class="flex size-3.5 shrink-0 items-center justify-center rounded-full text-[8px] font-bold text-bg-primary"
                  :class="selectedChannelId === ch.id ? 'bg-text-display' : 'bg-border-visible text-text-secondary'"
                >
                  <component :is="selectedChannelId === ch.id ? Check : X" class="size-2" />
                </span>
              </button>

              <button
                class="flex size-8 items-center justify-center rounded-full border border-dashed border-border-visible text-text-secondary hover:text-text-display hover:border-text-display bg-transparent transition-colors cursor-pointer"
                title="Connect another channel"
              >
                <span class="text-base font-light">+</span>
              </button>
            </div>
          </div>

          <!-- Textarea + Editor controls -->
          <div class="space-y-2 flex-1 flex flex-col min-h-[160px]">
            <label for="create-post-text" class="sr-only">Post content</label>
            <textarea
              id="create-post-text"
              v-model="postText"
              :placeholder="$t('composer.placeholder')"
              class="w-full flex-1 bg-bg-primary border border-border-visible rounded-xl p-4 text-sm text-text-body placeholder:text-text-secondary focus:outline-none focus:border-text-display resize-none font-sans"
            ></textarea>

            <div class="flex items-center justify-between py-1 px-1">
              <div class="flex items-center gap-3 text-text-secondary">
                <button
                  @click="appendHashtag"
                  class="p-1.5 hover:text-text-display hover:bg-bg-primary/50 rounded-lg transition-all cursor-pointer"
                  title="Insert Tag"
                >
                  <Hash class="size-4" />
                </button>
                <button
                  @click="handleAiAssist"
                  class="p-1.5 hover:text-text-display hover:bg-bg-primary/50 rounded-lg text-text-secondary flex items-center gap-1 transition-all cursor-pointer"
                  title="AI Assist"
                >
                  <Sparkles class="size-4 text-text-secondary" />
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
          </div>

          <!-- Media Attachment -->
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <span class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block">
                {{ t('composer.media.label') }}
              </span>
              <button
                type="button"
                data-testid="add-media-button"
                class="rounded-full border border-border-visible px-3 py-1.5 font-mono text-[9px] font-bold uppercase tracking-widest text-text-display transition-colors hover:border-text-display"
                @click="openMediaPicker"
              >
                {{ t('composer.media.addMedia') }}
              </button>
            </div>

            <div v-if="draftAttachmentAssets.length > 0" class="flex flex-wrap gap-2">
              <div
                v-for="asset in draftAttachmentAssets"
                :key="asset.assetId"
                class="flex items-center gap-2 rounded-full border border-border-subtle bg-bg-primary/40 px-3 py-2"
              >
                <img
                  v-if="asset.previewUrl"
                  :src="asset.previewUrl"
                  alt="Selected media preview"
                  class="size-6 rounded-full object-cover"
                  data-testid="attachment-preview-image"
                >
                <span class="max-w-[180px] truncate text-xs text-text-display">{{ asset.originalFilename ?? asset.assetId }}</span>
                <button
                  type="button"
                  :data-testid="`attachment-remove-${asset.assetId}`"
                  :aria-label="t('composer.media.removeAttachment', { name: asset.originalFilename ?? asset.assetId })"
                  class="rounded-full text-text-secondary transition-colors hover:text-text-display"
                  @click="removeDraftAttachment(asset.assetId)"
                >
                  <X class="size-3" />
                </button>
              </div>
            </div>

            <p v-else class="rounded-xl border border-dashed border-border-visible bg-bg-primary/20 px-4 py-3 text-xs text-text-secondary">
              {{ t('composer.media.empty') }}
            </p>

            <input
              id="create-post-file-input"
              ref="pickerSessionUploadInput"
              data-testid="picker-upload-input"
              type="file"
              class="hidden"
              accept="image/*,video/mp4,image/webp"
              aria-label="Upload media file"
              @change="handleFileSelect"
            >
          </div>
          <p
            v-if="isAttachmentLimitExceeded"
            data-testid="attachment-limit-warning"
            class="rounded-xl border border-error/40 bg-error/10 px-3 py-2 text-xs text-error"
          >
            {{ t('composer.media.limitWarning', {
              current: draftAttachmentIds.length,
              max: Number.isFinite(effectiveAttachmentLimit) ? effectiveAttachmentLimit : t('composer.media.limitInfinite'),
            }) }}
          </p>
          <ComposerMediaPickerShell
            :is-open="isMediaPickerOpen"
            :collection-state="mediaPickerCollectionState"
            :assets="pickerAssets"
            :provider="effectiveProvider"
            :apply-disabled="isPickerSelectionOverLimit"
            :apply-disabled-message="isPickerSelectionOverLimit ? t('composer.picker.applyDisabledLimit') : null"
            @toggle-asset="togglePickerAsset($event.assetId)"
            @apply-selection="applyPickerSelection($event.assetIds)"
            @provider-search="handleProviderSearch"
            @provider-import="handleProviderImport"
            @close="closeMediaPicker"
          >
            <template
              v-if="effectiveProvider === 'unsplash'"
              #provider
            >
              <MediaProviderPanel
                :results="providerResults"
                :is-searching="providerSearching"
                :search-error="providerSearchError"
                @provider-search="handleProviderSearch"
                @provider-import="handleProviderImport"
              />
            </template>
          </ComposerMediaPickerShell>

          <!-- First Comment option -->
          <div class="space-y-2">
            <!-- biome-ignore lint/a11y/noLabelWithoutControl: $t() provides accessible text, Biome can't resolve i18n keys statically -->
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

        <!-- Right Column: Social Preview -->
        <PostPreviewPanel
          :provider="selectedPreviewProvider"
          :title="$t('composer.linkedinPreview')"
          :linkedin-preview="linkedinPreview"
        >
          <template #footer>
            <div class="border-t border-border-subtle pt-6 space-y-4">
              <!-- Schedule controls -->
              <div class="space-y-3">
                <div class="flex items-center gap-4 bg-bg-surface border border-border-subtle p-3 rounded-xl">
                <CalendarIcon class="size-4 text-text-secondary shrink-0" />
                <div class="flex-1 space-y-2 text-xs">
                  <span class="text-text-secondary">Schedule Mode:</span>
                  <div
                    class="grid grid-cols-3 gap-1 rounded-lg bg-bg-primary/60 p-1"
                    role="radiogroup"
                    aria-label="Schedule mode"
                  >
                  <label
                    class="px-2 py-1 rounded font-mono text-[9px] uppercase tracking-wider font-bold transition-all cursor-pointer"
                    :class="scheduleMode === 'now' ? 'bg-text-display text-bg-primary' : 'bg-transparent text-text-secondary hover:text-text-display'"
                  >
                    <input type="radio" v-model="scheduleMode" value="now" class="sr-only" />
                    Now
                  </label>
                  <label
                    class="px-2 py-1 rounded font-mono text-[9px] uppercase tracking-wider font-bold transition-all cursor-pointer"
                    :class="scheduleMode === 'next' ? 'bg-text-display text-bg-primary' : 'bg-transparent text-text-secondary hover:text-text-display'"
                  >
                    <input type="radio" v-model="scheduleMode" value="next" class="sr-only" />
                    Next Schedule
                  </label>
                  <label
                    class="px-2 py-1 rounded font-mono text-[9px] uppercase tracking-wider font-bold transition-all cursor-pointer"
                    :class="scheduleMode === 'custom' ? 'bg-text-display text-bg-primary' : 'bg-transparent text-text-secondary hover:text-text-display'"
                  >
                    <input type="radio" v-model="scheduleMode" value="custom" class="sr-only" />
                    Pick Date
                  </label>
                  </div>
                  <p class="text-[10px] leading-4 text-text-secondary">
                    {{ scheduleHelperText }}
                  </p>
                </div>
              </div>

              <!-- Date Picker Row -->
              <div v-if="scheduleMode === 'custom'" class="grid grid-cols-[1fr_112px] gap-3 animate-slide-down">
                <Popover v-model:open="isDatePickerOpen">
                  <PopoverTrigger as-child>
                    <button
                      type="button"
                      class="flex items-center justify-between gap-2 bg-bg-surface border border-border-subtle rounded-xl px-3 py-2 text-xs text-text-body hover:border-text-display focus:outline-none focus:border-text-display font-sans"
                    >
                      <span>{{ selectedDateLabel }}</span>
                      <CalendarIcon class="size-3.5 text-text-secondary" />
                    </button>
                  </PopoverTrigger>
                  <PopoverContent class="w-auto p-0 bg-bg-surface border-border-subtle" align="start">
                    <Calendar
                      v-model="selectedCalendarDate"
                      :min-value="todayDateValue"
                      layout="month-and-year"
                      initial-focus
                      @update:model-value="isDatePickerOpen = false"
                    />
                  </PopoverContent>
                </Popover>
                <label for="create-post-schedule-time" class="sr-only">Schedule time</label>
                <input
                  id="create-post-schedule-time"
                  v-model="scheduleTime"
                  type="time"
                  :min="minTimeForDate"
                  class="bg-bg-surface border border-border-subtle rounded-xl px-3 py-2 text-xs text-text-body focus:outline-none focus:border-text-display font-sans"
                />
              </div>

              <!-- Priority / Draft Toggles -->
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
            </div>

              <p v-if="submitError" class="rounded-xl border border-error/30 bg-error/10 px-3 py-2 text-xs text-error">
                {{ submitError }}
              </p>

              <!-- Primary Action Buttons -->
              <div class="grid grid-cols-3 gap-3">
                <button
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
      </div>
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
