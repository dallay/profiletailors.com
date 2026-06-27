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
import { usePublishingStore, type Publication } from '@/stores/publishing'
import { useMediaStore } from '@/stores/media'
import { proxyImageUrl, resolveApiUrl } from '@/lib/auth-api'
import PostPreviewPanel from '@/components/composer/PostPreviewPanel.vue'
import type { LinkedInPreviewModel, PostPreviewMedia } from '@/components/composer/post-preview.types'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'

type ComposerScheduleMode = 'now' | 'next' | 'custom'

const props = withDefaults(
  defineProps<{
    isOpen: boolean
    initialDate?: string // ISO string
    editingPublication?: Publication // Pre-fill for editing
  }>(),
  {
    isOpen: false,
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
const isCreating = computed(() => !isEditMode.value)

// ---------------------------------------------------------------------------
// Media picker state (replaces local-only File attachment truth)
// ---------------------------------------------------------------------------
// Blob URL for instant preview during upload (purely transient UX)
const uploadPreviewBlob = ref<string | null>(null)
const selectedUploadFile = ref<File | null>(null)
const uploadTempKey = ref<string | null>(null)
const uploadProgress = ref(0)
const fileInput = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)

function clearUploadPreviewBlob() {
  if (uploadPreviewBlob.value) {
    URL.revokeObjectURL(uploadPreviewBlob.value)
    uploadPreviewBlob.value = null
  }
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

onUnmounted(() => clearInterval(timer))

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

function initEditMode(pub: NonNullable<typeof props.editingPublication>) {
  postText.value = pub.content ?? ''
  firstComment.value = ''
  priorityMode.value = pub.priority ?? false
  const modeMap: Record<string, ComposerScheduleMode> = {
    NOW: 'now',
    NEXT_SLOT: 'next',
    SCHEDULED_AT: 'custom',
  }
  scheduleMode.value = modeMap[pub.scheduleMode ?? 'SCHEDULED_AT'] ?? 'custom'

  mediaStore.clearSelection()
  if (pub.assetIds?.length) {
    for (const assetId of pub.assetIds) {
      mediaStore.addToSelection(assetId)
    }
  }

  const pubChannelId = pub.accountId
    ?? publishingStore.channels.find((ch) => pub.channels?.includes(ch.provider))?.id
    ?? null
  selectedChannelId.value = pubChannelId

  const dateSrc = pub.scheduledAt ? new Date(pub.scheduledAt) : new Date()
  selectedCalendarDate.value = new CalendarDate(
    dateSrc.getFullYear(),
    dateSrc.getMonth() + 1,
    dateSrc.getDate(),
  )
  scheduleTime.value = `${String(dateSrc.getHours()).padStart(2, '0')}:${String(dateSrc.getMinutes()).padStart(2, '0')}`
}

function initCreateMode() {
  postText.value = ''
  firstComment.value = ''
  priorityMode.value = false
  scheduleMode.value = props.initialDate ? 'custom' : 'now'
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

  if (isEditMode.value && props.editingPublication) {
    initEditMode(props.editingPublication)
  } else {
    initCreateMode()
  }

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
    !isSubmitting.value
  )
})

// Drag and drop state
// isDragging is declared above in the state block

// Methods
function handleDragOver(e: DragEvent) {
  e.preventDefault()
  isDragging.value = true
}

function handleDragLeave() {
  isDragging.value = false
}

function handleDrop(e: DragEvent) {
  e.preventDefault()
  isDragging.value = false
  if (e.dataTransfer?.files) {
    addFiles(Array.from(e.dataTransfer.files))
  }
}

function openFilePicker() {
  fileInput.value?.click()
}

function handleFileSelect(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files?.length) {
    addFiles(Array.from(target.files))
    target.value = ''
  }
}

function addFiles(filesList: File[]) {
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
 * Retries a failed upload tracked in the media store.
 */
async function retryUploadItem(tempKey: string) {
  try {
    await mediaStore.retryUpload(tempKey, () => {
      // progress is tracked in the store
    })
  } catch {
    // Error already reflected in the store
  }
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

  const first = mediaStore.selectedAssets[0]
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

  const first = mediaStore.selectedAssets[0]
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

  const first = mediaStore.selectedAssets[0]
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

function resolveScheduleMode(mode: ComposerScheduleMode): string {
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

async function handleEditSubmit(
  normalizedPostText: string,
  scheduledDate: Date | null,
  backendScheduleMode: string,
) {
  await publishingStore.updatePost(props.editingPublication?.id, {
    content: normalizedPostText,
    scheduledAt: scheduledDate?.toISOString(),
    priority: priorityMode.value,
    assetIds: [...mediaStore.selectedAssetIds],
    scheduleMode: backendScheduleMode,
  })
  emit('updated')
  emit('close')
}

async function handleCreateSubmit(
  normalizedPostText: string,
  scheduledDate: Date | null,
  backendScheduleMode: string,
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
    assetIds: [...mediaStore.selectedAssetIds],
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
      <!-- Modal Wrapper: using <div role="dialog"> instead of <dialog> to avoid UA default margin/padding that breaks flex centering -->
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

          <!-- Media Attachment (direct upload only for now) -->
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <span class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block">
                Media Attachment
              </span>
            </div>

            <!-- Upload progress or failed upload state -->
            <div
              v-if="currentUpload"
              class="border border-border-visible rounded-xl p-4 space-y-2"
            >
              <!-- In-progress upload -->
              <div v-if="currentUpload.status === 'uploading'" class="flex items-center gap-3">
                <Loader2 class="size-4 text-text-secondary animate-spin shrink-0" />
                <div class="flex-1 min-w-0">
                  <p class="text-xs text-text-secondary truncate">
                    Uploading {{ currentUpload.file.name }}
                  </p>
                  <div class="mt-1.5 h-1 bg-border-visible rounded-full overflow-hidden">
                    <div
                      class="h-full bg-text-display rounded-full transition-all duration-300"
                      :style="{ width: `${uploadProgress}%` }"
                    />
                  </div>
                  <p class="mt-0.5 font-mono text-[9px] text-text-secondary">{{ uploadProgress }}%</p>
                </div>
                <button
                  @click="removeFile"
                  class="shrink-0 p-1 hover:bg-error/10 rounded transition-colors cursor-pointer"
                  title="Cancel upload"
                >
                  <X class="size-3 text-text-secondary hover:text-error" />
                </button>
              </div>

              <!-- Failed upload -->
              <div v-if="currentUpload.status === 'failed' || currentUpload.status === 'conflict'" class="flex items-start gap-2">
                <AlertCircle class="size-4 text-error shrink-0 mt-0.5" />
                <div class="flex-1 min-w-0">
                  <p class="text-xs text-error font-medium">{{ currentUpload.errorTitle ?? 'Upload failed' }}</p>
                  <p class="text-[10px] text-text-secondary mt-0.5">{{ currentUpload.errorDetail }}</p>
                  <div class="flex gap-2 mt-2">
                    <button
                      @click="retryUploadItem(currentUpload.tempKey)"
                      class="flex items-center gap-1 font-mono text-[8px] uppercase tracking-wider font-bold text-text-display hover:underline cursor-pointer"
                    >
                      <RotateCcw class="size-3" /> Retry
                    </button>
                    <button
                      @click="removeFile"
                      class="font-mono text-[8px] uppercase tracking-wider font-bold text-text-secondary hover:text-error cursor-pointer"
                    >
                      Dismiss
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <!-- Selected READY asset preview -->
            <div
              v-else-if="mediaStore.selectedAssetIds.length > 0 && selectedAssetIsImage && selectedAssetPreviewUrl"
              class="relative w-full max-w-[180px] h-16 rounded-lg overflow-hidden group"
            >
              <img :src="selectedAssetPreviewUrl" alt="Selected media preview" class="w-full h-full object-cover" />
              <button
                @click.stop="removeFile"
                class="absolute top-1 right-1 bg-black/60 text-white rounded-full p-1 hover:bg-black/90 transition-colors cursor-pointer"
              >
                <X class="size-3" />
              </button>
            </div>

            <!-- Drop zone / select file button -->
            <label v-if="!currentUpload" for="create-post-file-input" class="sr-only">
              Upload media file
            </label>
            <input
              v-if="!currentUpload"
              id="create-post-file-input"
              ref="fileInput"
              type="file"
              class="hidden"
              accept="image/*,video/mp4"
              @change="handleFileSelect"
            />
            <button
              v-if="!currentUpload"
              type="button"
              @dragover="handleDragOver"
              @dragleave="handleDragLeave"
              @drop="handleDrop"
              class="border border-dashed border-border-visible rounded-xl p-5 text-center transition-all flex flex-col items-center justify-center min-h-[80px] cursor-pointer hover:border-text-secondary w-full"
              :class="{
                'border-text-display bg-bg-primary/40': isDragging,
                'bg-bg-primary/20': !isDragging,
              }"
              :aria-label="$t('composer.dragDrop')"
              @click="openFilePicker"
            >
              <ImageIcon class="size-5 text-text-secondary mx-auto" />
              <p class="text-xs text-text-secondary font-light mt-1.5">
                {{ $t('composer.dragDrop') }}
                <span class="underline text-text-display cursor-pointer font-medium">{{ $t('composer.selectFile') }}</span>
              </p>
            </button>

          </div>

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
