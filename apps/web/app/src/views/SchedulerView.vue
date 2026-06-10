<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  Bookmark,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Clock,
  Filter,
  Globe,
  List,
  Plus,
  Tag,
  Trash2,
  X,
} from '@lucide/vue'
import { usePublishingStore, type Publication } from '@/stores/publishing'
import CreatePostModal from '@/components/CreatePostModal.vue'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

const publishingStore = usePublishingStore()

// State
const isModalOpen = ref(false)
const selectedCellDate = ref<string | undefined>(undefined)

// Navigation state
const currentBaseDate = ref(new Date(2026, 5, 9)) // Tuesday, June 9, 2026 (matching mockup)

// Week calculations
const weekDays = computed(() => {
  const days = []
  // Get Sunday of the current week
  const startOfWeek = new Date(currentBaseDate.value)
  const dayOffset = startOfWeek.getDay()
  startOfWeek.setDate(startOfWeek.getDate() - dayOffset)

  for (let i = 0; i < 7; i++) {
    const d = new Date(startOfWeek)
    d.setDate(startOfWeek.getDate() + i)
    days.push(d)
  }
  return days
})

const monthYearLabel = computed(() => {
  const options: Intl.DateTimeFormatOptions = { month: 'long', year: 'numeric' }
  const locale = publishingStore.filterTag === 'es' ? 'es-ES' : 'en-US'
  return currentBaseDate.value.toLocaleDateString(locale, options)
})

// Navigation methods
function prevWeek() {
  const d = new Date(currentBaseDate.value)
  d.setDate(d.getDate() - 7)
  currentBaseDate.value = d
}

function nextWeek() {
  const d = new Date(currentBaseDate.value)
  d.setDate(d.getDate() + 7)
  currentBaseDate.value = d
}

function goToToday() {
  currentBaseDate.value = new Date(2026, 5, 9) // Locked to June 9, 2026 for demo consistency
}

// Time slots mapping (e.g. 6 PM, 8 PM, 10 PM)
const hourSlots = [
  { label: '6 PM', hour: 18 },
  { label: '8 PM', hour: 20 },
  { label: '10 PM', hour: 22 },
]

// Filter logic
const filteredPublications = computed(() => {
  return publishingStore.publications.filter((pub) => {
    // Channel filter
    if (publishingStore.filterChannel && !(pub.channels as string[]).includes(publishingStore.filterChannel)) {
      return false
    }
    // Search tag filter
    if (publishingStore.filterTag && !pub.content.toLowerCase().includes(publishingStore.filterTag.toLowerCase())) {
      return false
    }
    // Post type filter
    if (publishingStore.filterPostType !== 'all') {
      if (publishingStore.filterPostType === 'queued' && pub.status !== 'QUEUED') return false
      if (publishingStore.filterPostType === 'published' && pub.status !== 'PUBLISHED') return false
      if (publishingStore.filterPostType === 'cancelled' && pub.status !== 'CANCELLED') return false
    }
    return true
  })
})

// Helper to check if a publication is on a specific day and hour slot
function getPublicationsForSlot(date: Date, hour: number) {
  return filteredPublications.value.filter((pub) => {
    const pubDate = new Date(pub.scheduledAt)
    return (
      pubDate.getDate() === date.getDate() &&
      pubDate.getMonth() === date.getMonth() &&
      pubDate.getFullYear() === date.getFullYear() &&
      pubDate.getHours() === hour
    )
  })
}

// Helper to format date display
function formatDayName(date: Date) {
  const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
  const daysEs = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']
  const index = date.getDay()
  return (publishingStore.filterTag === 'es' ? daysEs[index] : days[index]) || ''
}

function getProviderColor(provider: string) {
  switch (provider) {
    case 'linkedin':
      return 'bg-[#0077b5]/10 border-[#0077b5]/30 text-[#0077b5]'
    case 'twitter':
      return 'bg-foreground/5 border-border-visible text-text-display'
    case 'instagram':
      return 'bg-pink-500/10 border-pink-500/30 text-pink-500'
    default:
      return 'bg-bg-primary border-border-visible text-text-secondary'
  }
}

function getProviderBadge(provider: string) {
  switch (provider) {
    case 'linkedin':
      return 'in'
    case 'twitter':
      return '𝕏'
    case 'instagram':
      return 'ig'
    default:
      return '•'
  }
}

// Open modal for slot
function openNewPostForSlot(date: Date, hour: number) {
  const d = new Date(date)
  d.setHours(hour)
  d.setMinutes(0)
  d.setSeconds(0)
  selectedCellDate.value = d.toISOString()
  isModalOpen.value = true
}

function openNewPostGeneral() {
  selectedCellDate.value = undefined
  isModalOpen.value = true
}
</script>

<template>
  <div class="space-y-6">
    <!-- Top Filter Controls Bar -->
    <div class="flex flex-col lg:flex-row gap-4 items-start lg:items-center justify-between border-b border-border-subtle pb-4">
      <!-- Title/Breadcrumb -->
      <div class="flex items-center gap-2">
        <Bookmark class="size-4.5 text-text-secondary" />
        <h2 class="text-xl font-light tracking-tight text-text-display">
          {{ $t('scheduler.allChannels') || 'All Channels' }}
        </h2>
      </div>

      <!-- Filters & Action buttons -->
      <div class="flex flex-wrap items-center gap-3 w-full lg:w-auto">
        <!-- View mode toggle -->
        <div class="flex items-center rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[9px] tracking-wider uppercase font-bold">
          <button
            class="cursor-pointer rounded-full px-3 py-1 transition-all"
            :class="publishingStore.viewMode === 'calendar' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
            @click="publishingStore.viewMode = 'calendar'"
          >
            {{ $t('scheduler.calendar') || 'Calendar' }}
          </button>
          <button
            class="cursor-pointer rounded-full px-3 py-1 transition-all"
            :class="publishingStore.viewMode === 'list' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
            @click="publishingStore.viewMode = 'list'"
          >
            {{ $t('scheduler.list') || 'List' }}
          </button>
        </div>

        <!-- Timezone Location Dropdown -->
        <div class="relative shrink-0">
          <select
            v-model="publishingStore.timezone"
            class="bg-bg-surface border border-border-subtle rounded-full px-3 py-1.5 text-[10px] font-mono font-bold text-text-secondary appearance-none pr-8 cursor-pointer focus:outline-none focus:border-text-display"
          >
            <option value="Madrid">📍 {{ $t('scheduler.timezoneMadrid') || 'Madrid' }}</option>
            <option value="UTC">🌐 UTC</option>
            <option value="New York">🇺🇸 New York</option>
          </select>
          <ChevronDown class="absolute right-3 top-1/2 -translate-y-1/2 size-3 text-text-secondary pointer-events-none" />
        </div>

        <!-- Post Status Filter -->
        <div class="relative shrink-0">
          <select
            v-model="publishingStore.filterPostType"
            class="bg-bg-surface border border-border-subtle rounded-full px-3 py-1.5 text-[10px] font-mono font-bold text-text-secondary appearance-none pr-8 cursor-pointer focus:outline-none focus:border-text-display"
          >
            <option value="all">📁 {{ $t('scheduler.allPosts') || 'All Posts' }}</option>
            <option value="queued">⏳ Queued</option>
            <option value="published">✅ Published</option>
            <option value="cancelled">🚫 Cancelled</option>
          </select>
          <ChevronDown class="absolute right-3 top-1/2 -translate-y-1/2 size-3 text-text-secondary pointer-events-none" />
        </div>

        <!-- Target Channel Filter -->
        <div class="relative shrink-0">
          <select
            v-model="publishingStore.filterChannel"
            class="bg-bg-surface border border-border-subtle rounded-full px-3 py-1.5 text-[10px] font-mono font-bold text-text-secondary appearance-none pr-8 cursor-pointer focus:outline-none focus:border-text-display"
          >
            <option value="">👤 {{ $t('scheduler.channelsLabel') || 'Channels' }}</option>
            <option value="linkedin">LinkedIn</option>
            <option value="twitter">X/Twitter</option>
            <option value="instagram">Instagram</option>
          </select>
          <ChevronDown class="absolute right-3 top-1/2 -translate-y-1/2 size-3 text-text-secondary pointer-events-none" />
        </div>

        <!-- Create Button -->
        <Button @click="openNewPostGeneral" class="gap-1.5 h-8.5 text-[10px] uppercase font-mono tracking-wider">
          <Plus class="size-3.5" />
          <span>{{ $t('scheduler.newPost') || 'New Post' }}</span>
        </Button>
      </div>
    </div>

    <!-- Main Workspace Layout -->
    <div class="flex flex-col lg:flex-row gap-6">
      <!-- Left Sidebar: Channel Manager -->
      <div class="w-full lg:w-60 shrink-0 space-y-6">
        <!-- Connected Channels list -->
        <div class="space-y-3">
          <span class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block">
            Active Profiles
          </span>

          <div class="space-y-2">
            <div
              v-for="ch in publishingStore.channels"
              :key="ch.id"
              class="flex items-center gap-3 p-2.5 rounded-xl border border-border-subtle bg-bg-surface"
            >
              <div class="relative">
                <img :src="ch.avatar" class="size-9 rounded-full object-cover border border-border-visible" alt="" />
                <span
                  class="absolute -bottom-1 -right-1 flex size-4.5 items-center justify-center rounded-full text-[8px] font-bold border border-bg-surface text-white"
                  :class="ch.provider === 'linkedin' ? 'bg-[#0077b5]' : 'bg-foreground'"
                >
                  {{ getProviderBadge(ch.provider) }}
                </span>
              </div>
              <div class="min-w-0 flex-1">
                <p class="truncate text-xs font-semibold text-text-display">{{ ch.name }}</p>
                <p class="truncate font-mono text-[9px] text-text-secondary">{{ ch.handle }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Platform buttons to Connect -->
        <div class="space-y-3 pt-4 border-t border-border-subtle">
          <span class="font-mono text-[9px] tracking-widest text-text-secondary uppercase block">
            {{ $t('scheduler.connectChannels') || 'Connect channels' }}
          </span>

          <div class="grid grid-cols-1 gap-2 text-xs">
            <button class="flex items-center justify-between p-2.5 rounded-xl border border-border-visible bg-transparent hover:border-text-secondary hover:text-text-display transition-colors text-left cursor-pointer">
              <span>Threads</span>
              <span class="font-mono text-[9px] text-text-secondary">+ connect</span>
            </button>
            <button class="flex items-center justify-between p-2.5 rounded-xl border border-border-visible bg-transparent hover:border-text-secondary hover:text-text-display transition-colors text-left cursor-pointer">
              <span>Bluesky</span>
              <span class="font-mono text-[9px] text-text-secondary">+ connect</span>
            </button>
            <button class="flex items-center justify-between p-2.5 rounded-xl border border-border-visible bg-transparent hover:border-text-secondary hover:text-text-display transition-colors text-left cursor-pointer">
              <span>Facebook</span>
              <span class="font-mono text-[9px] text-text-secondary">+ connect</span>
            </button>
            <button class="flex items-center justify-center gap-1.5 p-2 rounded-xl border border-dashed border-border-visible bg-transparent hover:border-text-secondary text-text-secondary hover:text-text-display transition-all cursor-pointer font-mono text-[9px] uppercase tracking-wider">
              <span>{{ $t('scheduler.moreChannels') || '+ More channels' }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Right Main: Calendar Planner (Calendar/List View) -->
      <div class="flex-1 min-w-0">
        <!-- Calendar Mode -->
        <div v-if="publishingStore.viewMode === 'calendar'" class="space-y-4">
          <!-- Calendar Subheader: Week Picker -->
          <div class="flex items-center justify-between bg-bg-surface border border-border-subtle p-3 rounded-xl">
            <!-- Navigation arrows -->
            <div class="flex items-center gap-1">
              <button
                @click="prevWeek"
                class="size-8 flex items-center justify-center rounded-lg border border-border-visible hover:border-text-secondary hover:text-text-display bg-bg-primary transition-colors cursor-pointer text-text-secondary"
              >
                <ChevronLeft class="size-4" />
              </button>
              <button
                @click="nextWeek"
                class="size-8 flex items-center justify-center rounded-lg border border-border-visible hover:border-text-secondary hover:text-text-display bg-bg-primary transition-colors cursor-pointer text-text-secondary"
              >
                <ChevronRight class="size-4" />
              </button>
            </div>

            <!-- Date Range Label -->
            <span class="font-mono text-[10px] font-bold uppercase tracking-widest text-text-display">
              {{ monthYearLabel }}
            </span>

            <!-- Actions -->
            <div class="flex items-center gap-2">
              <button
                @click="goToToday"
                class="border border-border-visible hover:border-text-secondary bg-bg-primary text-text-secondary hover:text-text-display font-mono text-[9px] uppercase tracking-wider font-bold rounded-lg px-3 py-1.5 transition-colors cursor-pointer"
              >
                {{ $t('scheduler.today') || 'Today' }}
              </button>
              <span class="font-mono text-[9px] uppercase tracking-wider text-text-secondary bg-bg-primary border border-border-visible px-2.5 py-1 rounded-lg">
                {{ $t('scheduler.weekView') || 'Week' }}
              </span>
            </div>
          </div>

          <!-- Weekly Schedule Grid -->
          <Card class="bg-bg-surface border border-border-subtle p-0 overflow-hidden">
            <!-- Grid Header: Days of the week -->
            <div class="grid grid-cols-7 border-b border-border-subtle bg-bg-primary">
              <div
                v-for="day in weekDays"
                :key="day.toISOString()"
                class="py-3.5 text-center border-r border-border-subtle last:border-r-0 flex flex-col gap-0.5"
                :class="{
                  'bg-bg-surface/50': day.getDate() === 9 && day.getMonth() === 5 && day.getFullYear() === 2026 // Today indicator
                }"
              >
                <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
                  {{ formatDayName(day).substring(0, 3) }}
                </span>
                <span
                  class="font-mono text-xs font-bold leading-none size-6 flex items-center justify-center mx-auto rounded-full"
                  :class="day.getDate() === 9 && day.getMonth() === 5 && day.getFullYear() === 2026 
                    ? 'bg-text-display text-bg-primary' 
                    : 'text-text-display'"
                >
                  {{ day.getDate() }}
                </span>
              </div>
            </div>

            <!-- Grid Body: Hourly timeline rows -->
            <div class="relative">
              <!-- Hour rows -->
              <div v-for="slot in hourSlots" :key="slot.hour" class="grid grid-cols-7 border-b border-border-subtle last:border-b-0 min-h-[140px]">
                <div
                  v-for="day in weekDays"
                  :key="day.toISOString()"
                  @click="openNewPostForSlot(day, slot.hour)"
                  class="relative p-2 border-r border-border-subtle last:border-r-0 hover:bg-bg-primary/20 transition-all group/cell flex flex-col justify-start gap-2 select-none cursor-pointer"
                >
                  <!-- Hour slot stamp (Left Column Header offset absolute display) -->
                  <span class="absolute top-1 left-2 font-mono text-[7px] tracking-wider text-text-secondary opacity-0 group-hover/cell:opacity-100 transition-opacity pointer-events-none">
                    {{ slot.label }}
                  </span>

                  <!-- Scheduled Posts rendering -->
                  <div
                    v-for="pub in getPublicationsForSlot(day, slot.hour)"
                    :key="pub.id"
                    @click.stop="publishingStore.cancelPost(pub.id)"
                    class="relative border rounded-xl p-3 space-y-2.5 transition-all text-left shadow-sm group/card hover:border-text-secondary bg-bg-surface overflow-hidden"
                    :class="getProviderColor(pub.channels[0] || 'linkedin')"
                  >
                    <!-- Header -->
                    <div class="flex items-center justify-between">
                      <span class="font-mono text-[8px] font-bold tracking-wider opacity-80 uppercase">
                        {{ new Date(pub.scheduledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
                      </span>
                      <div class="flex gap-1">
                        <span
                          v-for="channel in pub.channels"
                          :key="channel"
                          class="size-4.5 rounded-full border border-current/20 flex items-center justify-center font-mono text-[8px] uppercase tracking-wider font-bold"
                        >
                          {{ getProviderBadge(channel) }}
                        </span>
                      </div>
                    </div>

                    <!-- Text content -->
                    <p class="text-[11px] font-light leading-relaxed line-clamp-3 text-text-body">
                      {{ pub.content }}
                    </p>

                    <!-- Preview Thumbnail if any -->
                    <div v-if="pub.thumbnail" class="h-10 w-full rounded overflow-hidden">
                      <img :src="pub.thumbnail" class="w-full h-full object-cover grayscale opacity-75 group-hover/card:grayscale-0 group-hover/card:opacity-100 transition-all" alt="" />
                    </div>

                    <!-- Delete button overlay on card hover -->
                    <button
                      @click.stop="publishingStore.deletePost(pub.id)"
                      class="absolute top-1 right-1 opacity-0 group-hover/card:opacity-100 size-5 flex items-center justify-center rounded-full bg-black/60 text-white hover:bg-error transition-all"
                      title="Delete publication"
                    >
                      <Trash2 class="size-2.5" />
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </Card>
        </div>

        <!-- List Mode -->
        <div v-else class="space-y-4">
          <div v-if="filteredPublications.length === 0" class="border border-dashed border-border-visible rounded-2xl p-12 text-center text-text-secondary font-mono text-xs uppercase tracking-wider">
            {{ $t('dashboard.noPosts') || 'No scheduled posts for today.' }}
          </div>

          <div v-else class="space-y-3">
            <div
              v-for="pub in filteredPublications"
              :key="pub.id"
              class="flex flex-col md:flex-row md:items-center justify-between gap-4 p-5 rounded-2xl border border-border-subtle bg-bg-surface hover:border-text-secondary transition-all"
            >
              <div class="space-y-2 flex-1 min-w-0">
                <div class="flex items-center gap-3">
                  <span class="font-mono text-[9px] uppercase tracking-widest text-text-secondary bg-bg-primary border border-border-visible px-2 py-0.5 rounded-md">
                    {{ new Date(pub.scheduledAt).toLocaleString() }}
                  </span>
                  <span
                    class="font-mono text-[9px] uppercase tracking-widest px-2 py-0.5 rounded-md font-bold"
                    :class="{
                      'bg-success/10 text-success border border-success/20': pub.status === 'PUBLISHED',
                      'bg-text-display/10 text-text-display border border-border-visible': pub.status === 'QUEUED'
                    }"
                  >
                    {{ pub.status }}
                  </span>
                </div>
                <p class="text-sm font-light text-text-body leading-relaxed break-words">
                  {{ pub.content }}
                </p>
              </div>

              <div class="flex items-center gap-3 shrink-0">
                <div class="flex gap-1.5">
                  <span
                    v-for="ch in pub.channels"
                    :key="ch"
                    class="border border-border-visible bg-bg-primary px-2.5 py-0.5 rounded-full font-mono text-[9px] tracking-wider text-text-secondary uppercase"
                  >
                    {{ ch }}
                  </span>
                </div>

                <button
                  @click="publishingStore.deletePost(pub.id)"
                  class="size-8 flex items-center justify-center rounded-xl border border-border-visible hover:border-error text-text-secondary hover:text-error transition-colors bg-bg-primary cursor-pointer"
                >
                  <Trash2 class="size-4" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Create Post Modal component overlay -->
    <CreatePostModal
      :is-open="isModalOpen"
      :initial-date="selectedCellDate"
      @close="isModalOpen = false"
      @created="isModalOpen = false"
    />
  </div>
</template>
