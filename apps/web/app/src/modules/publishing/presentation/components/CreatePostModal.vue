<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import type { DateValue } from 'reka-ui'
import { useFocusTrap } from '@shared/composables/useFocusTrap'
import { useComposerMediaPicker } from '@modules/publishing/application/useComposerMediaPicker'
import { CalendarDate, getLocalTimeZone, today } from '@internationalized/date'
import { ImageIcon, ChevronDown, Hash, Smile, Sparkles, X } from '@lucide/vue'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { useMarkdownEditor } from '@modules/publishing/application/useMarkdownEditor'
import { channelCharLimit, DEFAULT_CHAR_LIMIT } from '@modules/publishing/application/channel-limits'
import { normalizeForSubmission } from '@modules/publishing/application/markdown'
import {
  generateAiPostContent,
  optimizeAiPostContent,
  regenerateAiPostContent,
  type AiContentResponse,
  type AiGenerationRequest,
  type AiGenerationFormat,
  type AiGenerationLength,
  type AiGenerationTone,
} from '@modules/publishing/services/ai-content-api'
import {
  isSocialProvider,
  usePublishingStore,
  type Publication,
} from '@modules/publishing/infrastructure/publishing.store'
import { useMediaStore } from '@modules/media'
import { resolveApiUrl } from '@modules/auth/infrastructure/auth-api'
import { useComposerShortlinks } from '@modules/shortlinks'
import PostPreviewPanel from '@modules/publishing/presentation/components/composer/PostPreviewPanel.vue'
import HashtagSuggestionPanel from '@modules/publishing/presentation/components/composer/HashtagSuggestionPanel.vue'
import { useHashtagSuggestions } from '@modules/publishing/presentation/composables/useHashtagSuggestions'
import type {
  LinkedInPreviewModel,
  PostPreviewMedia,
  PreviewProvider,
  ThreadsPreviewModel,
} from '@modules/publishing/presentation/components/composer/post-preview.types'
import { Button } from '@profiletailors/vue-ui/shell/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Progress } from '@/components/ui/progress'
import Spinner from '@/components/ui/spinner/Spinner.vue'
import ComposerMediaPickerShell from '@modules/publishing/presentation/components/composer/ComposerMediaPickerShell.vue'
import MediaProviderPanel from '@modules/publishing/presentation/components/composer/MediaProviderPanel.vue'
import ComposerSchedulePanel from '@modules/publishing/presentation/components/composer/ComposerSchedulePanel.vue'
import MarkdownToolbar from '@modules/publishing/presentation/components/composer/MarkdownToolbar.vue'
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
    initialContent?: string | null
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
    initialContent: null,
  }
)

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'connectChannels'): void
  (e: 'created', options: { keepOpen: boolean; publicationId?: string }): void
  (e: 'updated'): void
}>()

const { t } = useI18n()
const auth = useAuthStore()
const publishingStore = usePublishingStore()
const mediaStore = useMediaStore()

type AiVersion = {
  id: string
  content: string
}


const postText = ref('')
const selectedChannelIds = ref<string[]>([])
const selectionTouched = ref(false)
const overrides = ref<Record<string, string>>({})
const customizing = ref(false)
const activeTabId = ref<string | null>(null)

const activeText = computed({
  get: () => {
    if (activeTabId.value === null) return postText.value
    return overrides.value[activeTabId.value] ?? postText.value
  },
  set: (value: string) => {
    if (activeTabId.value === null) {
      postText.value = value
      return
    }
    if (value === postText.value) {
      delete overrides.value[activeTabId.value]
      return
    }
    overrides.value[activeTabId.value] = value
  },
})

const markdownEditor = useMarkdownEditor({ postText: activeText })
const _composerTextareaEl = markdownEditor.textareaEl

const picker = useComposerMediaPicker({
  mediaStore,
  publishingStore,
  editingPublication: () => props.editingPublication ?? null,
  provider: () => props.provider ?? null,
  initialChannelId: () => strictestChannelId.value,
  onAttachmentsChanged: () => {
    assetsTouched.value = true
  },
})
const submitError = ref('')
const {
  failedUrls: failedShortlinkUrls,
  shortlinkWarning,
  shorten: shortenComposerUrls,
  reset: resetShortlinks,
} = useComposerShortlinks()
const firstComment = ref('')
const createAnother = ref(false)
const priorityMode = ref(false)
const scheduleMode = ref<ComposerScheduleMode>('now')
const isDatePickerOpen = ref(false)
const isAiPanelOpen = ref(false)
const isHashtagPanelOpen = ref(false)

const hashtags = useHashtagSuggestions()
const isAiGenerating = ref(false)
const aiPrompt = ref('')
const aiIndustry = ref('')
const aiTargetAudience = ref('')
const aiFormat = ref<AiGenerationFormat>('standard')
const aiTone = ref<AiGenerationTone>('professional')
const aiLength = ref<AiGenerationLength>('medium')
const aiKeywordsRaw = ref('')
const aiReviewError = ref('')
const aiResetAt = ref<string | null>(null)
const aiVersions = ref<AiVersion[]>([])
const aiSelectedVersionId = ref<string | null>(null)

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
  selectedChannelIds.value = pubChannelId ? [pubChannelId] : []
  selectionTouched.value = true
  overrides.value = {}
  activeTabId.value = null
  customizing.value = false

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
  const hasPrefill = typeof props.initialContent === 'string' && props.initialContent.trim().length > 0
  postText.value = hasPrefill ? props.initialContent?.trim() : ''
  resetShortlinks()
  firstComment.value = ''
  priorityMode.value = false
  scheduleMode.value = props.initialDate ? 'custom' : 'now'
  assetsTouched.value = false
  picker.draftAttachmentIds.value = []
  picker.pickerSelectionIds.value = []
  picker.resetPickerSessionTracking()
  mediaStore.clearSelection()
  selectedChannelIds.value = hasPrefill ? [] : defaultActiveChannelIds()
  selectionTouched.value = hasPrefill
  overrides.value = {}
  activeTabId.value = null
  customizing.value = false

  const defaultDate = props.initialDate ? new Date(props.initialDate) : new Date()
  selectedCalendarDate.value = new CalendarDate(
    defaultDate.getFullYear(),
    defaultDate.getMonth() + 1,
    defaultDate.getDate(),
  )
  scheduleTime.value = `${String(defaultDate.getHours()).padStart(2, '0')}:${String(defaultDate.getMinutes()).padStart(2, '0')}`
}

function requestChannelConnection() {
  emit('close')
  emit('connectChannels')
}

async function initializeComposerForOpen() {
  submitError.value = ''
  mediaError.value = null
  isDatePickerOpen.value = false
  resetAiAssistantState()
  resetHashtagPanelState()
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

let analyzeDebounceTimer: ReturnType<typeof setTimeout> | undefined
watch(
  () => activeText.value,
  (text) => {
    if (!isHashtagPanelOpen.value) return
    clearTimeout(analyzeDebounceTimer)
    analyzeDebounceTimer = setTimeout(() => {
      hashtags.analyze(text)
    }, 600)
  },
)

watch(
  () => publishingStore.channels,
  (channels) => {
    const activeIds = new Set(channels.filter((ch) => ch.status === 'ACTIVE').map((ch) => ch.id))
    const pruned = selectedChannelIds.value.filter((id) => activeIds.has(id))
    if (pruned.length !== selectedChannelIds.value.length) {
      selectedChannelIds.value = pruned
    }
    if (activeTabId.value && !activeIds.has(activeTabId.value)) {
      activeTabId.value = null
    }
    if (!selectionTouched.value && !isEditMode.value && pruned.length === 0 && activeIds.size > 0) {
      selectedChannelIds.value = [...activeIds]
    }
  },
)


const isSubmitting = ref(false)
const counterLimit = computed(() => {
  if (activeTabId.value) {
    const channel = selectedChannels.value.find((ch) => ch.id === activeTabId.value)
    if (channel) return channelCharLimit(channel.provider)
  }
  const limits = selectedChannels.value.map((ch) => channelCharLimit(ch.provider))
  return limits.length > 0 ? Math.min(...limits) : DEFAULT_CHAR_LIMIT
})
const counterRemaining = computed(
  () => counterLimit.value - normalizeForSubmission(activeText.value).length,
)
const isCounterOverLimit = computed(() => counterRemaining.value < 0)
const selectedChannels = computed(() =>
  publishingStore.channels.filter((channel) => selectedChannelIds.value.includes(channel.id)),
)
function effectiveRawFor(channelId: string): string {
  return overrides.value[channelId] ?? postText.value
}

function effectiveSubmitFor(channelId: string): string {
  return normalizeForSubmission(effectiveRawFor(channelId))
}

const baseSubmitLength = computed(() => normalizeForSubmission(postText.value).length)

const channelStatuses = computed(() =>
  selectedChannels.value.map((channel) => {
    const limit = channelCharLimit(channel.provider)
    const length = effectiveSubmitFor(channel.id).length
    return {
      channelId: channel.id,
      name: channel.name,
      limit,
      length,
      remaining: limit - length,
      tooLong: length > limit,
      empty: baseSubmitLength.value > 0 && length === 0,
    }
  }),
)
const allChannelsValid = computed(() =>
  channelStatuses.value.length > 0
  && channelStatuses.value.every((status) => !status.tooLong && !status.empty),
)
const channelLimitErrors = computed(() => [
  ...channelStatuses.value
    .filter((status) => status.tooLong)
    .map((status) => t('composer.channelLimitExceeded', { name: status.name, limit: status.limit })),
  ...channelStatuses.value
    .filter((status) => status.empty)
    .map((status) => t('composer.channelEmptyText', { name: status.name })),
])
function channelInitials(name: string | null | undefined): string {
  const trimmed = name?.trim()
  if (!trimmed) return 'PT'

  const parts = trimmed.split(/\s+/).filter(Boolean)
  if (parts.length === 1) {
    return (parts[0]?.slice(0, 2) ?? 'PT').toUpperCase()
  }

  return parts
    .slice(0, 2)
    .map((part) => part[0] ?? '')
    .join('')
    .toUpperCase()
}

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
    baseSubmitLength.value > 0 &&
    allChannelsValid.value &&
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

  if (rejected.length > 0) {
    mediaError.value = rejected[0] ?? null
  }
  if (valid.length === 0) return

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

const previewChannel = computed(() => {
  if (activeTabId.value) {
    return selectedChannels.value.find((channel) => channel.id === activeTabId.value) ?? null
  }
  return selectedChannels.value[0] ?? null
})

const previewProvider = computed<PreviewProvider>(() =>
  previewChannel.value?.provider === 'threads' ? 'threads' : 'linkedin',
)

const previewTitle = computed(() =>
  previewProvider.value === 'threads' ? t('composer.threadsPreview') : t('composer.linkedinPreview'),
)

function previewModelFor(channelId: string | null): LinkedInPreviewModel {
  const channel = channelId
    ? (selectedChannels.value.find((entry) => entry.id === channelId) ?? null)
    : (selectedChannels.value[0] ?? null)
  return {
    authorName: channel?.name || auth.user?.username || 'Profile Tailors',
    authorHandle:
      channel?.handle ||
      (previewProvider.value === 'threads'
        ? t('composer.threadsAuthorFallback')
        : t('composer.linkedinAuthorFallback')),
    authorAvatarUrl: channel?.avatarUrl ?? null,
    authorInitials: channelInitials(channel?.name),
    text: markdownEditor.stripMarkdown(channelId ? effectiveRawFor(channelId) : postText.value),
    placeholderText: t('composer.seePreviewHere'),
    media: selectedPreviewMedia.value,
  }
}

const activePreview = computed<LinkedInPreviewModel | ThreadsPreviewModel>(() =>
  previewModelFor(activeTabId.value),
)

const customizeTabs = computed(() => {
  const remainingValues = channelStatuses.value.map((status) => status.remaining)
  const baseTab = {
    id: null as string | null,
    name: t('composer.customizeBase'),
    provider: null as string | null,
    remaining: remainingValues.length > 0 ? Math.min(...remainingValues) : 0,
    limit: null as number | null,
    customized: false,
  }
  const channelTabs = selectedChannels.value.map((channel) => {
    const status = channelStatuses.value.find((entry) => entry.channelId === channel.id)
    return {
      id: channel.id as string | null,
      name: channel.name,
      provider: channel.provider as string | null,
      remaining: status?.remaining ?? 0,
      limit: (status?.limit ?? channelCharLimit(channel.provider)) as number | null,
      customized: overrides.value[channel.id] !== undefined,
    }
  })
  return [baseTab, ...channelTabs]
})

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

const selectedAiVersion = computed(() =>
  aiVersions.value.find((version) => version.id === aiSelectedVersionId.value) ?? aiVersions.value[0] ?? null,
)

const aiSelectedContent = computed(() => selectedAiVersion.value?.content ?? '')
const aiHasMultipleVersions = computed(() => aiVersions.value.length > 1)

const submitButtonLabel = computed(() => {
  if (isEditMode.value) return t('composer.saveChanges')
  if (scheduleMode.value === 'now') return t('composer.scheduleNowBtn')
  if (scheduleMode.value === 'next') return t('composer.nextScheduleBtn')
  return t('composer.scheduleBtn')
})

const aiResetLabel = computed(() => {
  if (!aiResetAt.value) return ''
  return new Date(aiResetAt.value).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
})

function defaultActiveChannelIds(): string[] {
  return publishingStore.channels.filter((ch) => ch.status === 'ACTIVE').map((ch) => ch.id)
}

const strictestChannelId = computed(() => {
  const selected = selectedChannels.value.filter((ch) => ch.status === 'ACTIVE')
  let strictest: (typeof selected)[number] | null = null
  for (const channel of selected) {
    const current = strictest?.maxAttachments ?? Number.POSITIVE_INFINITY
    const next = channel.maxAttachments ?? Number.POSITIVE_INFINITY
    if (strictest === null || next < current) {
      strictest = channel
    }
  }
  return strictest?.id ?? null
})

function toggleChannel(channelId: string) {
  selectionTouched.value = true
  selectedChannelIds.value = selectedChannelIds.value.includes(channelId)
    ? selectedChannelIds.value.filter((id) => id !== channelId)
    : [...selectedChannelIds.value, channelId]
  if (activeTabId.value === channelId && !selectedChannelIds.value.includes(channelId)) {
    activeTabId.value = null
  }
}

function resetOverride(channelId: string) {
  delete overrides.value[channelId]
}

function toggleHashtagPanel(): void {
  isHashtagPanelOpen.value = !isHashtagPanelOpen.value
  if (isHashtagPanelOpen.value) {
    hashtags.loadTrending()
    hashtags.loadSavedSets()
    if (activeText.value.length >= 50) {
      hashtags.analyze(activeText.value)
    }
  }
}

function handleAddHashtag(tag: string): void {
  if (hashtags.addHashtag(tag)) {
    const block = hashtags.buildHashtagBlock()
    const stripped = activeText.value.replace(/\n\n#[\w\s#]+$/, '').trimEnd()
    activeText.value = block ? `${stripped}${block}` : stripped
  }
}

function handleRemoveHashtag(tag: string): void {
  hashtags.removeHashtag(tag)
  const block = hashtags.buildHashtagBlock()
  const stripped = activeText.value.replace(/\n\n#[\w\s#]+$/, '').trimEnd()
  activeText.value = block ? `${stripped}${block}` : stripped
}

function handleApplySet(set: import('@modules/publishing/services/hashtag-api').HashtagSavedSet): void {
  hashtags.applySet(set)
  const block = hashtags.buildHashtagBlock()
  const stripped = activeText.value.replace(/\n\n#[\w\s#]+$/, '').trimEnd()
  activeText.value = block ? `${stripped}${block}` : stripped
}

async function handleSaveCurrentAsSet(tagList: string[]): Promise<void> {
  const name = prompt(t('composer.hashtags.setNamePrompt'))
  if (name?.trim()) {
    await hashtags.saveSet(name.trim(), tagList)
  }
}

async function handleDeleteSet(setId: string): Promise<void> {
  await hashtags.deleteSet(setId)
}

function handleEmojiPicker() {
  activeText.value = `${activeText.value}${activeText.value ? ' ' : ''}🙂`
}

function openAiAssistant(): void {
  isAiPanelOpen.value = true
  aiReviewError.value = ''
}

function closeAiAssistant(): void {
  isAiPanelOpen.value = false
  aiReviewError.value = ''
}

function resetHashtagPanelState(): void {
  isHashtagPanelOpen.value = false
  hashtags.addedHashtags.value = new Set()
}

function resetAiAssistantState(): void {
  isAiPanelOpen.value = false
  isAiGenerating.value = false
  aiPrompt.value = ''
  aiIndustry.value = ''
  aiTargetAudience.value = ''
  aiFormat.value = 'standard'
  aiTone.value = 'professional'
  aiLength.value = 'medium'
  aiKeywordsRaw.value = ''
  aiReviewError.value = ''
  aiResetAt.value = null
  aiVersions.value = []
  aiSelectedVersionId.value = null
}

function parseKeywords(raw: string): string[] {
  return raw
    .split(',')
    .map((keyword) => keyword.trim())
    .filter((keyword) => keyword.length > 0)
}

function buildAiRequest(prompt: string) {
  return {
    prompt,
    context: {
      user_profile: auth.user
        ? {
            display_name: auth.displayName,
            email: auth.user.email,
            username: auth.user.username,
          }
        : undefined,
      industry: aiIndustry.value.trim() || undefined,
      target_audience: aiTargetAudience.value.trim() || undefined,
    },
    options: {
      format: aiFormat.value,
      tone: aiTone.value,
      length: aiLength.value,
      keywords: parseKeywords(aiKeywordsRaw.value),
    },
  }
}

function isAiQuotaError(error: unknown): error is Error & { status?: number; resetAt?: string; upgradeOptions?: string[] } {
  return Boolean(
    error &&
    typeof error === 'object' &&
    'status' in error &&
    (error as { status?: number }).status === 429,
  )
}

function resolveAiResetAt(resetAt: string | null): string {
  if (!resetAt) {
    const fallback = new Date()
    fallback.setMonth(fallback.getMonth() + 1, 1)
    fallback.setHours(0, 0, 0, 0)
    return fallback.toISOString()
  }

  return resetAt
}

function setAiVersions(content: string, keepExisting = false): void {
  const nextVersion: AiVersion = {
    id: crypto.randomUUID(),
    content,
  }

  if (keepExisting && aiVersions.value.length > 0) {
    aiVersions.value = [...aiVersions.value, nextVersion].slice(-2)
    aiSelectedVersionId.value = nextVersion.id
    return
  }

  aiVersions.value = [nextVersion]
  aiSelectedVersionId.value = nextVersion.id
}

async function runAiGeneration(mode: 'generate' | 'optimize' | 'regenerate'): Promise<void> {
  const prompt = aiPrompt.value.trim()
  const draft = activeText.value.trim()

  if (mode === 'generate' && !prompt) {
    aiReviewError.value = t('composer.ai.errors.promptRequired')
    return
  }

  if (mode === 'optimize' && !draft) {
    aiReviewError.value = t('composer.ai.errors.currentDraftRequired')
    return
  }

  if (mode === 'regenerate' && !aiSelectedContent.value.trim()) {
    aiReviewError.value = t('composer.ai.errors.previousVersionRequired')
    return
  }

  isAiGenerating.value = true
  aiReviewError.value = ''
  aiResetAt.value = null

  try {
    const request = buildAiRequest(prompt || draft)
    const response = await requestAiContent(mode, request, draft)

    const nextContent = response.content?.trim() ?? ''
    if (!nextContent) {
      throw new Error(t('composer.ai.errors.emptyResponse'))
    }

    setAiVersions(nextContent, mode === 'regenerate')
    isAiPanelOpen.value = true
  } catch (error) {
    if (isAiQuotaError(error)) {
      aiResetAt.value = resolveAiResetAt(error.resetAt ?? null)
      aiReviewError.value = t('composer.ai.errors.monthlyLimitReached')
      return
    }

    aiReviewError.value =
      error instanceof Error && error.message ? error.message : t('composer.ai.errors.generic')
  } finally {
    isAiGenerating.value = false
  }
}

async function requestAiContent(
  mode: 'generate' | 'optimize' | 'regenerate',
  request: AiGenerationRequest,
  draft: string,
): Promise<AiContentResponse> {
  if (mode === 'optimize') {
    return optimizeAiPostContent({
      ...request,
      content: draft,
    })
  }

  if (mode === 'regenerate') {
    return regenerateAiPostContent({
      ...request,
      previous_content: aiSelectedContent.value,
    })
  }

  return generateAiPostContent(request)
}

async function handleAiAssist(): Promise<void> {
  openAiAssistant()
  if (activeText.value.trim()) {
    aiPrompt.value = activeText.value.trim()
  }
}

function acceptAiVersion(): void {
  if (!aiSelectedContent.value.trim()) {
    return
  }

  activeText.value = aiSelectedContent.value.trim()
  closeAiAssistant()
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
  overrides.value = {}
  activeTabId.value = null
  customizing.value = false
  resetShortlinks()
  mediaError.value = null
  removeFile()
  firstComment.value = ''
  createAnother.value = false
  priorityMode.value = false
  picker.draftAttachmentIds.value = []
  picker.pickerSelectionIds.value = []
  picker.resetPickerSessionTracking()
  resetAiAssistantState()
}

function finalizeAfterCreate(shouldCreateAnother: boolean) {
  if (shouldCreateAnother) {
    resetPostForm()
  } else {
    emit('close')
  }
}

async function shortenPostUrls(content: string): Promise<string> {
  return shortenComposerUrls(content, isEditMode.value)
}

async function handleSchedule() {
  if (!canSubmit.value) return

  isSubmitting.value = true
  submitError.value = ''

  try {
    const scheduledDate = resolveScheduledDate()
    if (submitError.value) return

    const uploadOk = selectedUploadFile.value && uploadTempKey.value
      ? await uploadDeferredFile()
      : true
    if (!uploadOk) return

    const backendScheduleMode = resolveScheduleMode(scheduleMode.value)

    if (isEditMode.value && props.editingPublication) {
      await handleEditSubmit(markdownEditor.plainTextForSubmit(), scheduledDate, backendScheduleMode)
    } else {
      await handleCreateSubmit(scheduledDate, backendScheduleMode)
    }
  } catch (err) {
    submitError.value = err instanceof Error ? err.message : 'Unable to schedule post.'
    console.error('Error scheduling post', err)
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
  scheduledDate: Date | undefined,
  backendScheduleMode: NonNullable<Publication['scheduleMode']>,
) {
  const targets = await Promise.all(
    selectedChannels.value.flatMap((target) => {
      const provider = target.provider
      if (!isSocialProvider(provider)) return []
      return shortenPostUrls(effectiveSubmitFor(target.id)).then((content) => ({
        socialAccountId: target.accountId,
        provider,
        content,
      }))
    }),
  )
  if (targets.length === 0) return
  const result = await publishingStore.scheduleMultiChannel({
    title: 'Post from App',
    scheduleMode: backendScheduleMode,
    scheduledAt: scheduledDate?.toISOString(),
    nextSlotAfter: scheduleMode.value === 'next' ? now.value.toISOString() : undefined,
    priority: priorityMode.value,
    assetIds: [...picker.draftAttachmentIds.value],
    targets,
  })
  if (result.status === 'ALL_FAILED') {
    submitError.value = result.results
      .map((item) => item.errorMessage)
      .filter(Boolean)
      .join(' ') || 'Unable to schedule posts.'
    return
  }
  const failedTargets = result.results.filter((item) => !item.publicationId)
  if (failedTargets.length > 0) {
    submitError.value = failedTargets
      .map((item) => `${selectedChannels.value.find((channel) => channel.accountId === item.socialAccountId)?.name ?? item.socialAccountId}: ${item.errorMessage ?? 'Unable to schedule post.'}`)
      .join('\n')
    const failedAccountIds = new Set(failedTargets.map((target) => target.socialAccountId))
    selectedChannelIds.value = selectedChannels.value
      .filter((channel) => failedAccountIds.has(channel.accountId))
      .map((channel) => channel.id)
    if (activeTabId.value && !selectedChannelIds.value.includes(activeTabId.value)) {
      activeTabId.value = null
    }
  }
  const firstCreated = result.results.find((item) => item.publicationId)?.publicationId
  if (!firstCreated) return
  if (failedShortlinkUrls.value.length > 0) {
    toast.warning(`${t('shortlinks.createFailed')} ${failedShortlinkUrls.value.join(', ')}`)
  }
  emit('created', {
    keepOpen: createAnother.value || failedTargets.length > 0,
    publicationId: firstCreated,
  })
  if (failedTargets.length > 0) return
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
      @keydown.escape.stop="emit('close')"
    >
      <dialog
        ref="modalContainer"
        open
        tabindex="-1"
        aria-modal="true"
        aria-labelledby="create-post-title"
        class="relative m-0 flex max-h-[92dvh] lg:h-[min(92vh,750px)] w-full max-w-5xl flex-col overflow-y-auto lg:overflow-hidden rounded-2xl border border-border-subtle bg-bg-surface shadow-2xl animate-zoom-in lg:flex-row focus:outline-none"
      >
        <button type="button"
          @click="emit('close')"
          aria-label="Close"
          class="sticky top-4 right-4 z-50 float-right -mb-8 flex size-8 items-center justify-center rounded-full border border-border-subtle bg-bg-primary text-text-secondary hover:text-text-display lg:hidden"
        >
          <X class="size-4" />
        </button>

        <div class="flex flex-col space-y-6 border-b border-border-subtle p-4 sm:p-6 lg:min-h-0 lg:flex-1 lg:border-b-0 lg:border-r lg:overflow-hidden">
          <div class="flex items-center justify-between">
              <h3 id="create-post-title" class="font-mono text-xs font-bold tracking-widest text-text-display uppercase">
                {{ isEditMode ? $t('composer.editTitle') : $t('composer.title') }}
              </h3>
            <button type="button"
              @click="emit('close')"
              aria-label="Close"
              class="hidden lg:flex size-7 items-center justify-center rounded-xl border border-border-subtle bg-bg-primary text-text-secondary hover:text-text-display cursor-pointer"
            >
              <X class="size-3.5" />
            </button>
          </div>

          <ComposerChannelSelector
            :channels="publishingStore.channels"
            :selected-channel-ids="selectedChannelIds"
            :is-edit-mode="isEditMode"
            @toggle="toggleChannel"
          />

          <ul
            v-if="channelLimitErrors.length > 0"
            data-testid="composer-channel-limit-errors"
            class="space-y-1 rounded-xl border border-error/30 bg-error/10 px-3 py-2 text-xs text-error"
          >
            <li v-for="error in channelLimitErrors" :key="error">
              {{ error }}
            </li>
          </ul>

          <div v-if="!isEditMode && selectedChannels.length > 0" class="flex items-center gap-2">
            <button
              type="button"
              data-testid="customize-toggle"
              class="rounded-full border border-border-visible px-3 py-1.5 font-mono text-[10px] tracking-wide text-text-secondary transition-all hover:text-text-display"
              :aria-pressed="customizing ? 'true' : 'false'"
              @click="customizing = !customizing"
            >
              {{ t('composer.customizePerNetwork') }}
            </button>
          </div>

          <div
            v-if="customizing && !isEditMode && selectedChannels.length > 0"
            data-testid="customize-tabs"
            class="flex flex-wrap items-center gap-2"
            role="tablist"
          >
            <button
              v-for="tab in customizeTabs"
              :key="tab.id ?? 'base'"
              type="button"
              data-testid="customize-tab"
              role="tab"
              :aria-selected="activeTabId === tab.id ? 'true' : 'false'"
              class="flex items-center gap-2 rounded-full border px-3 py-1.5 font-mono text-[10px] tracking-wide transition-all"
              :class="activeTabId === tab.id
                ? 'border-text-display bg-bg-primary text-text-display font-bold'
                : 'border-border-visible text-text-secondary hover:text-text-display bg-bg-primary/50'"
              @click="activeTabId = tab.id"
            >
              <span class="max-w-[120px] truncate">{{ tab.name }}</span>
              <span class="text-text-secondary">{{ tab.remaining }}<template v-if="tab.limit !== null"> / {{ tab.limit }}</template></span>
              <span
                v-if="tab.customized"
                data-testid="customize-dot"
                class="size-1.5 rounded-full bg-text-display"
              />
            </button>
            <button
              v-if="activeTabId !== null && overrides[activeTabId] !== undefined"
              type="button"
              data-testid="customize-reset"
              class="rounded-full border border-dashed border-border-visible px-3 py-1.5 font-mono text-[10px] tracking-wide text-text-secondary transition-all hover:text-text-display"
              @click="resetOverride(activeTabId)"
            >
              {{ t('composer.customizeReset') }}
            </button>
          </div>

          <div
            v-if="publishingStore.hasNoChannels"
            data-testid="composer-no-channels"
            role="status"
            class="flex flex-col gap-3 rounded-xl border border-border-visible bg-bg-primary/70 p-4 sm:flex-row sm:items-center sm:justify-between"
          >
            <p class="text-sm text-text-secondary">{{ $t('scheduler.noChannelTitle') }}</p>
            <Button
              type="button"
              variant="outline"
              class="min-h-11 shrink-0"
              data-testid="composer-connect-channels"
              @click="requestChannelConnection"
            >
              {{ $t('scheduler.connectChannels') }}
            </Button>
          </div>

          <div class="flex min-h-[220px] sm:min-h-[320px] lg:min-h-105 flex-1 flex-col rounded-3xl border border-border-visible bg-bg-primary/70">
            <div class="border-b border-border-subtle/70 px-3 py-2">
              <MarkdownToolbar
                :disabled="isSubmitting"
                @bold="markdownEditor.applyBold()"
                @italic="markdownEditor.applyItalic()"
                @strikethrough="markdownEditor.applyStrikethrough()"
                @heading="markdownEditor.applyHeading()"
                @unordered-list="markdownEditor.applyUnorderedList()"
                @ordered-list="markdownEditor.applyOrderedList()"
                @blockquote="markdownEditor.applyBlockquote()"
                @link="markdownEditor.applyLink()"
                @code="markdownEditor.applyInlineCode()"
              />
            </div>
            <label for="create-post-text" class="sr-only">Post content</label>
            <textarea
              id="create-post-text"
              ref="_composerTextareaEl"
              v-model="activeText"
              :placeholder="$t('composer.placeholder')"
              class="min-h-[140px] sm:min-h-[180px] lg:min-h-55 w-full flex-1 resize-none bg-transparent p-4 sm:p-5 text-sm text-text-body placeholder:text-text-secondary focus:outline-none font-sans"
              data-testid="composer-textarea"
              @dragover="handleComposerSurfaceDragOver"
              @dragleave="handleComposerSurfaceDragLeave"
              @drop="handleComposerSurfaceDrop"
              @paste="handleComposerSurfacePaste"
              @keydown="markdownEditor.handleKeyDown"
            ></textarea>
            <p v-if="shortlinkWarning && !isEditMode" role="status" class="border-t border-border-subtle/70 px-4 py-3 text-xs text-text-secondary">
              {{ t('shortlinks.createFailed') }} {{ failedShortlinkUrls.join(', ') }}
            </p>

            <div class="border-t border-border-subtle/70 px-4 py-4">
              <div class="flex flex-wrap items-center gap-3">
                <div
                  v-for="asset in visibleInlineAttachments"
                  :key="asset.key"
                  :title="asset.name"
                  class="group relative h-29.5 w-29.5 overflow-hidden rounded-[18px] border border-border-visible bg-bg-primary/50"
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
                    <Spinner class="size-5 text-(--upload-accent)" />
                    <p class="text-xs font-medium leading-tight text-white">
                      {{ asset.kind === 'local-upload' ? (currentUploadStateLabel ?? asset.uploadStateLabel ?? t('composer.media.uploadingProgress', { progress: Math.round(normalizedUploadProgress ?? 0) })) : asset.uploadStateLabel }}
                    </p>
                    <Progress
                      :model-value="asset.kind === 'local-upload' ? (normalizedUploadProgress ?? 0) : (asset.uploadProgress ?? 0)"
                      class="h-1 w-full bg-white/15 **:data-[slot=progress-indicator]:bg-(--upload-accent)"
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
                  class="flex h-29.5 w-29.5 items-center justify-center rounded-[18px] border border-dashed border-border-visible bg-bg-primary/30 font-mono text-xs tracking-[0.2em] text-text-secondary"
                  data-testid="inline-attachment-overflow"
                >
                  +{{ hiddenInlineAttachmentCount }}
                </div>

                <button
                  type="button"
                  class="flex h-29.5 w-29.5 cursor-pointer flex-col items-center justify-center rounded-[18px] border border-dashed px-4 text-center transition"
                  :class="isDropzoneActive ? 'border-(--upload-accent) bg-(--upload-accent)/10' : 'border-border-visible bg-bg-primary/30 hover:border-text-display/40'"
                  data-testid="composer-inline-dropzone"
                  @click="openUploadPicker"
                  @dragover="handleDropzoneDragOver"
                  @dragleave="handleDropzoneDragLeave"
                  @drop="handleDropzoneDrop"
                >
                  <ImageIcon class="mb-3 size-6 text-text-secondary" />
                  <p class="text-[12px] leading-5 text-text-secondary">
                    {{ t('composer.media.dropzoneTitle') }}
                    <span class="block font-medium text-(--upload-accent)">{{ t('composer.media.dropzoneBody') }}</span>
                  </p>
                </button>
              </div>

              <p
                v-if="mediaError"
                role="alert"
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
                    aria-label="Upload media"
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
                        aria-label="Toggle media sources"
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
                    aria-label="Open emoji picker"
                  >
                    <Smile class="size-4" />
                  </button>
                  <button type="button"
                    @click="toggleHashtagPanel"
                    :aria-pressed="isHashtagPanelOpen"
                    :aria-label="t('composer.hashtags.togglePanel')"
                    class="flex h-10 w-10 items-center justify-center rounded-xl text-text-secondary transition hover:bg-bg-surface hover:text-text-display"
                    :class="isHashtagPanelOpen ? 'bg-bg-surface text-text-display' : ''"
                  >
                    <Hash class="size-4" />
                  </button>
                  <button type="button"
                    @click="handleAiAssist"
                    :disabled="isAiGenerating"
                    class="flex h-10 items-center gap-1 rounded-xl px-2 text-text-secondary transition hover:bg-bg-surface hover:text-text-display"
                    :title="t('composer.ai.button')"
                    aria-label="Open AI Assistant"
                    data-testid="composer-ai-assist"
                  >
                    <Sparkles class="size-4" />
                    <span class="font-mono text-[8px] font-bold uppercase tracking-wider">AI</span>
                  </button>
                </div>

                <span
                  class="font-mono text-[10px]"
                  :class="isCounterOverLimit ? 'text-error font-bold' : 'text-text-secondary'"
                >
                  {{ counterRemaining }} / {{ counterLimit }}
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

              <div v-if="isAiPanelOpen" class="mt-4 rounded-[22px] border border-border-visible bg-bg-surface p-4 shadow-lg shadow-black/5">
                <div class="flex items-start justify-between gap-3">
                  <div class="space-y-1">
                    <p class="font-mono text-[9px] font-bold uppercase tracking-[0.24em] text-text-secondary">
                      {{ t('composer.ai.title') }}
                    </p>
                    <p class="text-sm text-text-secondary">
                      {{ t('composer.ai.description') }}
                    </p>
                  </div>

                  <button
                    type="button"
                    class="flex size-8 items-center justify-center rounded-full border border-border-subtle bg-bg-primary text-text-secondary transition hover:text-text-display"
                    :aria-label="t('composer.ai.close')"
                    @click="closeAiAssistant"
                  >
                    <X class="size-3.5" />
                  </button>
                </div>

                <div class="mt-4 grid gap-3 lg:grid-cols-2">
                  <div class="space-y-2 lg:col-span-2">
                    <label class="font-mono text-[9px] font-bold uppercase tracking-widest text-text-secondary" for="ai-prompt-input">
                      {{ t('composer.ai.prompt') }}
                    </label>
                    <textarea
                      id="ai-prompt-input"
                      v-model="aiPrompt"
                      rows="3"
                      class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-text-display focus:outline-none"
                      :placeholder="t('composer.ai.promptPlaceholder')"
                      data-testid="composer-ai-prompt"
                    />
                  </div>

                  <div class="space-y-2">
                    <label class="font-mono text-[9px] font-bold uppercase tracking-widest text-text-secondary" for="ai-industry-input">
                      {{ t('composer.ai.industry') }}
                    </label>
                    <input
                      id="ai-industry-input"
                      v-model="aiIndustry"
                      type="text"
                      class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-text-display focus:outline-none"
                      :placeholder="t('composer.ai.industryPlaceholder')"
                    >
                  </div>

                  <div class="space-y-2">
                    <label class="font-mono text-[9px] font-bold uppercase tracking-widest text-text-secondary" for="ai-audience-input">
                      {{ t('composer.ai.targetAudience') }}
                    </label>
                    <input
                      id="ai-audience-input"
                      v-model="aiTargetAudience"
                      type="text"
                      class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-text-display focus:outline-none"
                      :placeholder="t('composer.ai.targetAudiencePlaceholder')"
                    >
                  </div>

                  <div class="space-y-2">
                    <label class="font-mono text-[9px] font-bold uppercase tracking-widest text-text-secondary" for="ai-format-select">
                      {{ t('composer.ai.format') }}
                    </label>
                    <select
                      id="ai-format-select"
                      v-model="aiFormat"
                      class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body focus:border-text-display focus:outline-none"
                    >
                      <option value="standard">{{ t('composer.ai.formats.standard') }}</option>
                      <option value="thread">{{ t('composer.ai.formats.thread') }}</option>
                      <option value="tips">{{ t('composer.ai.formats.tips') }}</option>
                      <option value="question">{{ t('composer.ai.formats.question') }}</option>
                      <option value="story">{{ t('composer.ai.formats.story') }}</option>
                    </select>
                  </div>

                  <div class="space-y-2">
                    <label class="font-mono text-[9px] font-bold uppercase tracking-widest text-text-secondary" for="ai-tone-select">
                      {{ t('composer.ai.tone') }}
                    </label>
                    <select
                      id="ai-tone-select"
                      v-model="aiTone"
                      class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body focus:border-text-display focus:outline-none"
                    >
                      <option value="professional">{{ t('composer.ai.tones.professional') }}</option>
                      <option value="casual">{{ t('composer.ai.tones.casual') }}</option>
                      <option value="inspirational">{{ t('composer.ai.tones.inspirational') }}</option>
                      <option value="educational">{{ t('composer.ai.tones.educational') }}</option>
                    </select>
                  </div>

                  <div class="space-y-2">
                    <label class="font-mono text-[9px] font-bold uppercase tracking-widest text-text-secondary" for="ai-length-select">
                      {{ t('composer.ai.length') }}
                    </label>
                    <select
                      id="ai-length-select"
                      v-model="aiLength"
                      class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body focus:border-text-display focus:outline-none"
                    >
                      <option value="short">{{ t('composer.ai.lengths.short') }}</option>
                      <option value="medium">{{ t('composer.ai.lengths.medium') }}</option>
                      <option value="long">{{ t('composer.ai.lengths.long') }}</option>
                    </select>
                  </div>

                  <div class="space-y-2 lg:col-span-2">
                    <label class="font-mono text-[9px] font-bold uppercase tracking-widest text-text-secondary" for="ai-keywords-input">
                      {{ t('composer.ai.keywords') }}
                    </label>
                    <input
                      id="ai-keywords-input"
                      v-model="aiKeywordsRaw"
                      type="text"
                      class="w-full rounded-2xl border border-border-visible bg-bg-primary px-4 py-3 text-sm text-text-body placeholder:text-text-secondary focus:border-text-display focus:outline-none"
                      :placeholder="t('composer.ai.keywordsPlaceholder')"
                    >
                  </div>
                </div>

                <div class="mt-4 flex flex-wrap gap-2">
                  <button
                    type="button"
                    class="rounded-full bg-(--upload-accent) px-4 py-2 text-xs font-bold uppercase tracking-wider text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="isAiGenerating"
                    @click="runAiGeneration('generate')"
                    data-testid="composer-ai-generate"
                  >
                    {{ isAiGenerating ? t('composer.ai.generating') : t('composer.ai.generate') }}
                  </button>
                  <button
                    type="button"
                    class="rounded-full border border-border-visible bg-bg-primary px-4 py-2 text-xs font-bold uppercase tracking-wider text-text-body transition hover:border-text-display hover:text-text-display disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="isAiGenerating || !activeText.trim()"
                    @click="runAiGeneration('optimize')"
                    data-testid="composer-ai-optimize"
                  >
                    {{ t('composer.ai.optimize') }}
                  </button>
                  <button
                    type="button"
                    class="rounded-full border border-border-visible bg-bg-primary px-4 py-2 text-xs font-bold uppercase tracking-wider text-text-body transition hover:border-text-display hover:text-text-display disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="isAiGenerating || !aiSelectedContent.trim()"
                    @click="runAiGeneration('regenerate')"
                    data-testid="composer-ai-regenerate"
                  >
                    {{ t('composer.ai.regenerate') }}
                  </button>
                </div>

                <p
                  v-if="aiReviewError"
                  role="alert"
                  class="mt-3 rounded-2xl border border-error/30 bg-error/10 px-3 py-2 text-xs text-error"
                >
                  {{ aiReviewError }}
                </p>

                <div v-if="aiResetAt" class="mt-4 rounded-2xl border border-warning/30 bg-warning/10 p-4 text-xs text-text-body">
                  <p class="font-semibold text-text-display">
                    {{ t('composer.ai.errors.monthlyLimitReached') }}
                  </p>
                  <p class="mt-1 text-text-secondary">
                    {{ t('composer.ai.resetDate', { date: aiResetLabel }) }}
                  </p>
                  <div class="mt-3 flex flex-wrap gap-2">
                    <span class="rounded-full border border-border-visible bg-bg-surface px-3 py-1 text-[10px] font-bold uppercase tracking-wider text-text-body">Free 10/mo</span>
                    <span class="rounded-full border border-border-visible bg-bg-surface px-3 py-1 text-[10px] font-bold uppercase tracking-wider text-text-body">Pro 100/mo</span>
                    <span class="rounded-full border border-border-visible bg-bg-surface px-3 py-1 text-[10px] font-bold uppercase tracking-wider text-text-body">Team 500/mo</span>
                  </div>
                </div>

                <div v-if="aiVersions.length > 0" class="mt-4 space-y-3">
                  <p
                    v-if="aiHasMultipleVersions"
                    class="font-mono text-[9px] font-bold uppercase tracking-[0.24em] text-text-secondary"
                  >
                    {{ t('composer.ai.comparisonTitle') }}
                  </p>
                  <div v-if="aiHasMultipleVersions" class="grid gap-3 lg:grid-cols-2">
                    <button
                      v-for="version in aiVersions"
                      :key="version.id"
                      type="button"
                      class="rounded-2xl border p-4 text-left transition"
                      :class="version.id === aiSelectedVersionId ? 'border-(--upload-accent) bg-(--upload-accent)/5' : 'border-border-visible bg-bg-primary hover:border-text-display/50'"
                      :data-testid="`composer-ai-version-${aiVersions.indexOf(version) + 1}`"
                      @click="aiSelectedVersionId = version.id"
                    >
                      <p class="mb-2 text-[9px] font-bold uppercase tracking-[0.24em] text-text-secondary">
                        {{ t('composer.ai.version') }} {{ aiVersions.indexOf(version) + 1 }}
                      </p>
                      <p class="whitespace-pre-wrap text-sm leading-6 text-text-body">
                        {{ version.content }}
                      </p>
                    </button>
                  </div>

                  <div class="rounded-2xl border border-border-visible bg-bg-primary p-4">
                    <p class="mb-2 text-[9px] font-bold uppercase tracking-[0.24em] text-text-secondary">
                      {{ t('composer.ai.reviewTitle') }}
                    </p>
                    <p class="whitespace-pre-wrap text-sm leading-6 text-text-body">
                      {{ aiSelectedContent }}
                    </p>
                  </div>

                  <div class="flex flex-wrap items-center justify-between gap-2">
                    <p class="text-xs text-text-secondary">
                      {{ t('composer.ai.reviewHint') }}
                    </p>
                    <div class="flex gap-2">
                      <button
                        type="button"
                        class="rounded-full border border-border-visible bg-bg-primary px-4 py-2 text-xs font-bold uppercase tracking-wider text-text-body transition hover:border-text-display hover:text-text-display"
                        @click="acceptAiVersion"
                        data-testid="composer-ai-accept"
                      >
                        {{ t('composer.ai.accept') }}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
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
            :assets="picker.filteredPickerAssets.value"
            :selected-asset-ids="picker.pickerSelectionIds.value"
            :provider="picker.effectiveProvider.value"
            :apply-disabled="picker.isPickerSelectionOverLimit.value"
            :apply-disabled-message="picker.isPickerSelectionOverLimit.value ? t('composer.picker.applyDisabledLimit') : null"
            @toggle-asset="picker.togglePickerAsset($event.assetId)"
            @apply-selection="picker.applyPickerSelection()"
            @provider-search="picker.handleProviderSearch"
            @library-search="picker.handleLibrarySearch"
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

        <HashtagSuggestionPanel
          v-if="isHashtagPanelOpen"
          :suggestions="hashtags.suggestions.value"
          :trending="hashtags.trending.value"
          :saved-sets="hashtags.savedSets.value"
          :added-hashtags="hashtags.addedHashtags.value"
          :hashtag-count="hashtags.hashtagCount.value"
          :is-at-limit="hashtags.isAtLimit.value"
          :is-approaching-limit="hashtags.isApproachingLimit.value"
          :is-analyzing="hashtags.isAnalyzing.value"
          :is-saving="hashtags.isSaving.value"
          @add="handleAddHashtag"
          @remove="handleRemoveHashtag"
          @apply-set="handleApplySet"
          @save-current-as-set="handleSaveCurrentAsSet"
          @delete-set="handleDeleteSet"
        />

        <PostPreviewPanel
          :provider="previewProvider"
          :title="previewTitle"
          :preview="activePreview"
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
                  {{ submitButtonLabel }}
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
