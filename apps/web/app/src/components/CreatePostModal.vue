<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import type { DateValue } from 'reka-ui'
import { CalendarDate, getLocalTimeZone, today } from '@internationalized/date'
// biome-ignore lint/correctness/noUnusedImports: used in template
import { Image as ImageIcon } from '@lucide/vue'
import {
  Check,
  Hash,
  Loader2,
  Sparkles,
  X,
  Upload,
  AlertCircle,
  RotateCcw,
} from '@lucide/vue'
import { useAuthStore } from '@/stores/auth'
import { usePublishingStore } from '@/stores/publishing'
import { useMediaStore } from '@/stores/media'
import { proxyImageUrl, resolveApiUrl } from '@/lib/auth-api'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'

type ComposerScheduleMode = 'now' | 'next' | 'custom'

const props = withDefaults(
  defineProps<{
    isOpen: boolean
    initialDate?: string // ISO string
  }>(),
  {
    isOpen: false,
  }
)

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'created'): void
}>()

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

// Initialize Date
watch(
  () => props.isOpen,
  async (open) => {
    if (open) {
      // Clear form
      postText.value = ''
      firstComment.value = ''
      priorityMode.value = false
      submitError.value = ''
      scheduleMode.value = props.initialDate ? 'custom' : 'now'
      isDatePickerOpen.value = false
      avatarLoadFailed.value = {}
      selectedChannelId.value = publishingStore.channels[0]?.id ?? null

      // Clear selected/uploaded media state
      mediaStore.clearSelection()
      clearUploadPreviewBlob()
      selectedUploadFile.value = null
      uploadTempKey.value = null
      uploadProgress.value = 0

      const defaultDate = props.initialDate ? new Date(props.initialDate) : new Date()
      selectedCalendarDate.value = new CalendarDate(
        defaultDate.getFullYear(),
        defaultDate.getMonth() + 1,
        defaultDate.getDate(),
      )

      const hours = String(defaultDate.getHours()).padStart(2, '0')
      const minutes = String(defaultDate.getMinutes()).padStart(2, '0')
      scheduleTime.value = `${hours}:${minutes}`

      // Load dangling uploads from prior sessions (PROCESSING + FAILED)
      // so the user can recover or retry them.
      if (auth.isAuthenticated) {
        try {
          await mediaStore.loadDanglingAssets()
        } catch {
          // Non-critical — dangling load failure shouldn't block the composer
        }
      }
    }
  }
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
    selectedChannel.value !== null &&
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
  const validFiles = filesList.filter((file) => {
    const isSupported =
      file.type.startsWith('image/') ||
      file.type === 'video/mp4' ||
      file.type === 'image/webp'
    const isUnderLimit = file.size <= 10 * 1024 * 1024 // 10MB
    if (!isSupported) alert('Unsupported media format. Supported formats: JPEG, PNG, WEBP, GIF, MP4.')
    if (!isUnderLimit) alert('File size exceeds 10MB limit.')
    return isSupported && isUnderLimit
  })

  // Limit to max 1 image for LinkedIn MVP simple preview.
  // File is stored locally for deferred upload — no server call until Schedule Post.
  if (validFiles.length > 0) {
    const file = validFiles[0]
    clearUploadPreviewBlob()
    selectedUploadFile.value = file
    uploadPreviewBlob.value = URL.createObjectURL(file)
    uploadTempKey.value = `modal-upload-${Date.now()}`
    uploadProgress.value = 0
  }
}

/**
 * Reserves an asset, begins upload, and tracks progress.
 * On success, the READY asset is added to the media store selection.
 */
async function uploadAndTrack(file: File) {
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

  const assets = mediaStore.selectedAssets
  if (assets.length === 0) return false
  return assets[0].mediaType.startsWith('image/')
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

  const assets = mediaStore.selectedAssets
  if (assets.length === 0) return null
  const first = assets[0]
  if (!first.mediaType.startsWith('image/')) return null
  return first.previewUrl ? resolveApiUrl(first.previewUrl) : null
})

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

async function handleSchedule() {
  if (!canSubmit.value) return

  const normalizedPostText = postText.value.trim()
  const shouldCreateAnother = createAnother.value

  isSubmitting.value = true
  submitError.value = ''

  try {
    let finalScheduledDate: Date | undefined

    if (scheduleMode.value === 'custom') {
      if (!selectedCalendarDate.value) {
        submitError.value = 'Select a date.'
        return
      }
      finalScheduledDate = selectedCalendarDate.value.toDate(getLocalTimeZone())
      const error = validateCustomSchedule(finalScheduledDate)
      if (error) {
        submitError.value = error
        return
      }
    }

    // Deferred upload: if a file was selected but not yet uploaded, upload it now
    // before creating the post. This ensures the asset is READY before the
    // publication references it via assetIds.
    if (selectedUploadFile.value && uploadTempKey.value) {
      try {
        const asset = await mediaStore.createAndUpload(
          selectedUploadFile.value,
          uploadTempKey.value,
          (pct) => {
            uploadProgress.value = pct
          },
        )
        mediaStore.addToSelection(asset.assetId)
      } catch {
        // Error is already tracked in mediaStore.uploadList; surface it to the user
        submitError.value = 'Media upload failed. Please try again.'
        return
      }
    }

    const backendScheduleMode = scheduleMode.value === 'now'
      ? 'NOW'
      : scheduleMode.value === 'next'
        ? 'NEXT_SLOT'
        : 'SCHEDULED_AT'

    await publishingStore.schedulePost({
      content: normalizedPostText,
      title: 'Post from App',
      channels: selectedProviders.value,
      scheduledAt: finalScheduledDate?.toISOString(),
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

    if (shouldCreateAnother) {
      postText.value = ''
      removeFile()
      firstComment.value = ''
    } else {
      emit('close')
    }
  } catch (err) {
    submitError.value = err instanceof Error ? err.message : 'Unable to schedule post.'
    console.error('Error scheduling post', err)
    if (shouldCreateAnother) {
      postText.value = ''
      removeFile()
      firstComment.value = ''
    }
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <!-- Modal Backdrop — click.self closes modal; role=presentation intentional for overlay -->
    <!-- biome-ignore lint/a11y/noStaticElementInteractions: overlay backdrop, closes on outside click -->
    <div
      v-if="isOpen"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-fade-in"
      role="presentation"
      @click.self="emit('close')"
      @keydown.escape="emit('close')"
    >
      <!-- Modal Wrapper -->
      <div
        class="flex flex-col lg:flex-row w-full max-w-5xl h-[90vh] lg:h-[750px] bg-bg-surface border border-border-subtle rounded-2xl overflow-hidden shadow-2xl animate-zoom-in"
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
            <h3 class="font-mono text-xs font-bold tracking-widest text-text-display uppercase">
              {{ $t('composer.title') }}
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
                @click="selectChannel(ch.id)"
                class="relative flex items-center gap-2 border rounded-full px-3 py-1.5 font-mono text-[10px] tracking-wide transition-all cursor-pointer"
                :class="selectedChannelId === ch.id
                  ? 'border-text-display bg-bg-primary text-text-display font-bold'
                  : 'border-border-visible text-text-secondary hover:text-text-display bg-bg-primary/50'"
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
            <textarea
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
            <input
              v-if="!currentUpload"
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
            <span class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block">
              {{ $t('composer.firstComment') }}
            </span>
            <input
              v-model="firstComment"
              type="text"
              :placeholder="$t('composer.firstCommentPlaceholder')"
              class="w-full bg-bg-primary border border-border-visible rounded-xl px-4 py-2.5 text-xs text-text-body placeholder:text-text-secondary focus:outline-none focus:border-text-display font-sans"
            />
          </div>
        </div>

        <!-- Right Column: LinkedIn Live Preview -->
        <div class="w-full lg:w-[420px] bg-bg-primary p-6 flex flex-col justify-between overflow-y-auto space-y-6">
          <div class="border-b border-border-subtle pb-4">
            <h3 class="font-mono text-xs font-bold tracking-widest text-text-display uppercase">
              {{ $t('composer.linkedinPreview') }}
            </h3>
          </div>

          <!-- LinkedIn Card Body -->
          <div class="flex-1 flex items-center justify-center p-2">
            <div class="w-full max-w-[360px] bg-[#1d2226] text-white border border-[#2d3135] rounded-xl overflow-hidden shadow-md font-sans text-xs">
              <!-- Post Header -->
              <div class="p-3.5 flex gap-3">
                <img
                  v-if="selectedChannel?.avatarUrl"
                  :src="proxyImageUrl(selectedChannel.avatarUrl ?? '')"
                  :alt="`${selectedChannel.name} avatar`"
                  class="size-10 rounded-full object-cover border border-[#404448]"
                />
                <div
                  v-else
                  class="flex size-10 shrink-0 items-center justify-center rounded-full border border-[#404448] bg-[#111417] font-mono text-[11px] font-bold uppercase text-white"
                >
                  {{ selectedChannelInitials }}
                </div>
                <div class="min-w-0 flex-1">
                  <div class="flex items-center gap-1.5">
                    <p class="font-semibold text-white text-[13px] hover:text-[#70b5f9] hover:underline cursor-pointer truncate">
                      {{ selectedChannel?.name || auth.user?.username || 'Profile Tailors' }}
                    </p>
                    <span class="text-[10px] text-gray-400 font-normal shrink-0"> • 1st</span>
                  </div>
                  <p class="text-[11px] text-gray-400 truncate">
                    {{ selectedChannel?.handle || 'LinkedIn Member' }}
                  </p>
                  <p class="text-[10px] text-gray-400 flex items-center gap-1 mt-0.5">
                    <span>Just now</span>
                    <span>•</span>
                    <span>🌐</span>
                  </p>
                </div>
              </div>

              <!-- Post text -->
              <div class="px-3.5 pb-3.5 text-white text-[13px] leading-relaxed whitespace-pre-wrap break-words">
                <span v-if="postText.trim().length === 0" class="text-gray-500 italic">
                  {{ $t('composer.seePreviewHere') }}
                </span>
                <span v-else>{{ postText }}</span>
              </div>

              <!-- Uploaded Media Preview (uses transient blob URL for UX) -->
              <div v-if="selectedAssetPreviewUrl" class="border-t border-[#2d3135] max-h-[220px] overflow-hidden bg-black/30 flex items-center justify-center">
                <img
                  :src="selectedAssetPreviewUrl"
                  alt="Media preview"
                  class="w-full h-auto max-h-[220px] object-contain"
                />
              </div>

              <!-- LinkedIn Action Buttons -->
              <div class="border-t border-[#2d3135] py-2 px-1 flex justify-around text-gray-400 font-semibold text-[11px]">
                <span class="flex items-center gap-1 px-2 py-1 rounded hover:bg-white/5 cursor-pointer">👍 Like</span>
                <span class="flex items-center gap-1 px-2 py-1 rounded hover:bg-white/5 cursor-pointer">💬 Comment</span>
                <span class="flex items-center gap-1 px-2 py-1 rounded hover:bg-white/5 cursor-pointer">🔄 Repost</span>
                <span class="flex items-center gap-1 px-2 py-1 rounded hover:bg-white/5 cursor-pointer">📤 Send</span>
              </div>
            </div>
          </div>

          <!-- Bottom controls (Scheduler picker) -->
          <div class="border-t border-border-subtle pt-6 space-y-4">
            <!-- Schedule controls -->
            <div class="space-y-3">
              <div class="flex items-center gap-4 bg-bg-surface border border-border-subtle p-3 rounded-xl">
                <CalendarIcon class="size-4 text-text-secondary shrink-0" />
                <div class="flex-1 space-y-2 text-xs">
                  <span class="text-text-secondary">Schedule Mode:</span>
                  <div class="grid grid-cols-3 gap-1 rounded-lg bg-bg-primary/60 p-1">
                    <button
                      @click="scheduleMode = 'now'"
                      class="px-2 py-1 rounded font-mono text-[9px] uppercase tracking-wider font-bold transition-all cursor-pointer"
                      :class="scheduleMode === 'now' ? 'bg-text-display text-bg-primary' : 'bg-transparent text-text-secondary hover:text-text-display'"
                    >
                      Now
                    </button>
                    <button
                      @click="scheduleMode = 'next'"
                      class="px-2 py-1 rounded font-mono text-[9px] uppercase tracking-wider font-bold transition-all cursor-pointer"
                      :class="scheduleMode === 'next' ? 'bg-text-display text-bg-primary' : 'bg-transparent text-text-secondary hover:text-text-display'"
                    >
                      Next Schedule
                    </button>
                    <button
                      @click="scheduleMode = 'custom'"
                      class="px-2 py-1 rounded font-mono text-[9px] uppercase tracking-wider font-bold transition-all cursor-pointer"
                      :class="scheduleMode === 'custom' ? 'bg-text-display text-bg-primary' : 'bg-transparent text-text-secondary hover:text-text-display'"
                    >
                      Pick Date
                    </button>
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
                <input
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
                <label class="flex items-center gap-1.5 cursor-pointer hover:text-text-display select-none">
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
                {{ scheduleMode === 'now' ? 'Schedule Now' : scheduleMode === 'next' ? 'Next Schedule' : $t('composer.scheduleBtn') }}
              </Button>
            </div>
          </div>
        </div>
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
