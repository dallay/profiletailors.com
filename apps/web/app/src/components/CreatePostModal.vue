<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import type { DateValue } from 'reka-ui'
import { CalendarDate, getLocalTimeZone, today } from '@internationalized/date'
// biome-ignore lint/correctness/noUnusedImports: used in template
import { Image as ImageIcon } from '@lucide/vue'
import {
  Calendar as CalendarIcon,
  Check,
  Hash,
  Sparkles,
  X,
} from '@lucide/vue'
import { useAuthStore } from '@/stores/auth'
import { usePublishingStore } from '@/stores/publishing'
import { proxyImageUrl } from '@/lib/auth-api'
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

// State
const postText = ref('')
const selectedChannelId = ref<string | null>(null)
const avatarLoadFailed = ref<Record<string, boolean>>({})
const mediaFiles = ref<File[]>([])
const submitError = ref('')
const mediaPreviews = ref<string[]>([])
const fileInput = ref<HTMLInputElement | null>(null)
const firstComment = ref('')
const createAnother = ref(false)
const priorityMode = ref(false)
const scheduleMode = ref<ComposerScheduleMode>('now')
const isDatePickerOpen = ref(false)

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
  (open) => {
    if (open) {
      // Clear form
      postText.value = ''
      mediaFiles.value = []
      mediaPreviews.value = []
      firstComment.value = ''
      priorityMode.value = false
      submitError.value = ''
      scheduleMode.value = props.initialDate ? 'custom' : 'now'
      isDatePickerOpen.value = false
      avatarLoadFailed.value = {}
      selectedChannelId.value = publishingStore.channels[0]?.id ?? null

      const defaultDate = props.initialDate ? new Date(props.initialDate) : new Date()
      selectedCalendarDate.value = new CalendarDate(
        defaultDate.getFullYear(),
        defaultDate.getMonth() + 1,
        defaultDate.getDate(),
      )
      
      const hours = String(defaultDate.getHours()).padStart(2, '0')
      const minutes = String(defaultDate.getMinutes()).padStart(2, '0')
      scheduleTime.value = `${hours}:${minutes}`
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
const isDragging = ref(false)

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

function handleFileSelect(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files) {
    addFiles(Array.from(target.files))
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

  // Limit to max 1 image for LinkedIn MVP simple preview
  if (validFiles.length > 0) {
    mediaFiles.value = [validFiles[0] as File]
    
    // Revoke old previews
    mediaPreviews.value.forEach(URL.revokeObjectURL)
    mediaPreviews.value = [URL.createObjectURL(validFiles[0] as File)]
  }
}

function removeFile() {
  mediaFiles.value = []
  mediaPreviews.value.forEach(URL.revokeObjectURL)
  mediaPreviews.value = []
}

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

async function handleSchedule() {
  if (!canSubmit.value) return

  // Capture createAnother state before any async work
  const shouldCreateAnother = createAnother.value
  const capturedText = postText.value

  isSubmitting.value = true
  submitError.value = ''

  try {
    let finalScheduledDate: Date | undefined
    if (scheduleMode.value === 'custom') {
      if (!selectedCalendarDate.value) {
        submitError.value = 'Select a date.'
        return
      }

      const [hoursRaw, minutesRaw] = scheduleTime.value.split(':').map(Number)
      const hours = hoursRaw ?? Number.NaN
      const minutes = minutesRaw ?? Number.NaN
      const isValidHours = Number.isInteger(hours) && hours >= 0 && hours <= 23
      const isValidMinutes = Number.isInteger(minutes) && minutes >= 0 && minutes <= 59
      if (!isValidHours || !isValidMinutes) {
        submitError.value = 'Invalid time selected.'
        return
      }

      finalScheduledDate = selectedCalendarDate.value.toDate(getLocalTimeZone())
      finalScheduledDate.setHours(hours, minutes, 0, 0)

      const earliestAllowed = new Date(now.value.getTime() + 5 * 60_000)
      if (finalScheduledDate < earliestAllowed) {
        submitError.value = 'Selected date and time must be in the future.'
        return
      }
    }

    const backendScheduleMode = scheduleMode.value === 'now'
      ? 'NOW'
      : scheduleMode.value === 'next'
        ? 'NEXT_SLOT'
        : 'SCHEDULED_AT'

    // Schedule through store — pass scheduleMode so NOW and NEXT_SLOT modes omit scheduledFor
    await publishingStore.schedulePost({
      content: postText.value,
      title: 'Post from App',
      channels: selectedProviders.value,
      scheduledAt: finalScheduledDate?.toISOString(),
      nextSlotAfter: scheduleMode.value === 'next' ? now.value.toISOString() : undefined,
      scheduleMode: backendScheduleMode,
      priority: priorityMode.value,
      mediaFiles: mediaFiles.value,
      socialAccountId: selectedChannel.value?.accountId,
    })

    emit('created')

    if (shouldCreateAnother) {
      // Reset text so the modal is ready for the next post
      postText.value = ''
      removeFile()
      firstComment.value = ''
    } else {
      emit('close')
    }
  } catch (err) {
    submitError.value = err instanceof Error ? err.message : 'Unable to schedule post.'
    console.error('Error scheduling post', err)
    // Always reset form when "Create Another" was checked, even on error
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
    <!-- Modal Backdrop -->
    <div
      v-if="isOpen"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-fade-in"
      @click.self="emit('close')"
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
                  :src="proxyImageUrl(ch.avatarUrl!)"
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

          <!-- Drag and Drop Media Upload -->
          <div class="space-y-2">
            <span class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block">
              Media Attachment (Max 10MB)
            </span>

            <div
              @dragover="handleDragOver"
              @dragleave="handleDragLeave"
              @drop="handleDrop"
              class="border border-dashed border-border-visible rounded-xl p-5 text-center transition-all flex flex-col items-center justify-center min-h-[96px] cursor-pointer hover:border-text-secondary"
              :class="{
                'border-text-display bg-bg-primary/40': isDragging,
                'bg-bg-primary/20': !isDragging,
              }"
              @click="fileInput?.click()"
            >
              <input
                ref="fileInput"
                type="file"
                class="hidden"
                accept="image/*,video/mp4"
                @change="handleFileSelect"
              />

              <div v-if="mediaPreviews.length === 0" class="space-y-1.5">
                <ImageIcon class="size-5 text-text-secondary mx-auto" />
                <p class="text-xs text-text-secondary font-light">
                  {{ $t('composer.dragDrop') }}
                  <span class="underline text-text-display cursor-pointer font-medium">{{ $t('composer.selectFile') }}</span>
                </p>
              </div>

              <!-- Media Preview -->
              <div v-else class="relative w-full max-w-[180px] h-16 rounded-lg overflow-hidden group">
                <img :src="mediaPreviews[0]" alt="Media preview" class="w-full h-full object-cover" />
                <button
                  @click.stop="removeFile"
                  class="absolute top-1 right-1 bg-black/60 text-white rounded-full p-1 hover:bg-black/90 transition-colors"
                >
                  <X class="size-3" />
                </button>
              </div>
            </div>
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
                  :src="proxyImageUrl(selectedChannel.avatarUrl!)"
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

              <!-- Uploaded Media Preview -->
              <div v-if="mediaPreviews.length > 0" class="border-t border-[#2d3135] max-h-[220px] overflow-hidden bg-black/30 flex items-center justify-center">
                <img :src="mediaPreviews[0]" alt="Media preview" class="w-full h-auto max-h-[220px] object-contain" />
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
