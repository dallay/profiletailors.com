<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Bookmark,
  CalendarDays,
  ChevronDown,
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
import { usePublishingStore, type Publication, type ActivityEntry } from '@/stores/publishing'
import CreatePostModal from '@/components/CreatePostModal.vue'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

const publishingStore = usePublishingStore()
const { locale: i18nLocale } = useI18n()

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------
const isModalOpen = ref(false)
const selectedCellDate = ref<string | undefined>(undefined)

/** Calendar sub-view: month | week | day */
const calendarView = ref<'month' | 'week' | 'day'>('week')

/** Navigation base date */
const currentBaseDate = ref(new Date())

// ---------------------------------------------------------------------------
// Drag-and-drop state
// ---------------------------------------------------------------------------
const dragData = ref<{ id: string; previousScheduledAt: string } | null>(null)

function onDragStart(e: DragEvent, pub: Publication) {
  if (!e.dataTransfer) return
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', pub.id)
  dragData.value = { id: pub.id, previousScheduledAt: pub.scheduledAt }
  // Slight opacity feedback
  const el = e.target as HTMLElement
  if (el) el.style.opacity = '0.4'
}

function onDragEnd(e: DragEvent) {
  const el = e.target as HTMLElement
  if (el) el.style.opacity = '1'
  dragData.value = null
}

async function onDropCell(e: DragEvent, targetDate: Date, targetHour?: number) {
  e.preventDefault()
  if (!e.dataTransfer) return
  const pubId = e.dataTransfer.getData('text/plain')
  if (!pubId) return

  const d = new Date(targetDate)
  if (targetHour !== undefined) {
    d.setHours(targetHour, 0, 0, 0)
  }
  const newDateIso = d.toISOString()

  try {
    await publishingStore.reschedulePublication(pubId, newDateIso)
  } catch {
    // Rollback is handled in the store; we just show feedback
    console.warn('Reschedule failed, rolled back')
  }
  dragData.value = null
}

// ---------------------------------------------------------------------------
// Month helpers
// ---------------------------------------------------------------------------

/** Build a 6×7 grid (weeks × days) for the current month. */
const monthGrid = computed(() => {
  const year = currentBaseDate.value.getFullYear()
  const month = currentBaseDate.value.getMonth()

  const firstDay = new Date(year, month, 1)
  const lastDay = new Date(year, month + 1, 0)

  // Start from the Sunday before (or on) the 1st
  const start = new Date(firstDay)
  start.setDate(start.getDate() - start.getDay())

  const weeks: Date[][] = []
  let current: Date[] = []
  const cursor = new Date(start)

  while (cursor <= lastDay || current.length > 0 && cursor.getDay() !== 0) {
    if (cursor.getDay() === 0 && current.length > 0) {
      weeks.push(current)
      current = []
    }
    current.push(new Date(cursor))
    cursor.setDate(cursor.getDate() + 1)
    // Safety: cap at 42 cells (6 weeks)
    if (weeks.length === 6 && current.length === 7) {
      current = []
    }
  }
  if (current.length > 0) weeks.push(current)
  // Pad to avoid flickering grids
  while (weeks.length < 6) {
    const lastWeek = weeks[weeks.length - 1]
    const lastDay = lastWeek?.[lastWeek.length - 1]
    const startOfPad = lastDay ? new Date(lastDay) : new Date(start)
    startOfPad.setDate(startOfPad.getDate() + 1)
    const padWeek: Date[] = []
    for (let i = 0; i < 7; i++) {
      const d = new Date(startOfPad)
      d.setDate(startOfPad.getDate() + i)
      padWeek.push(d)
    }
    weeks.push(padWeek)
  }

  return weeks
})

const isCurrentMonth = (d: Date) =>
  d.getMonth() === currentBaseDate.value.getMonth() &&
  d.getFullYear() === currentBaseDate.value.getFullYear()

/** Build a lookup from date string to ActivityEntry. */
const activityByDate = computed<Map<string, ActivityEntry>>(() => {
  const map = new Map<string, ActivityEntry>()
  for (const entry of publishingStore.activity) {
    map.set(entry.date, entry)
  }
  return map
})

// ---------------------------------------------------------------------------
// Week helpers
// ---------------------------------------------------------------------------

const weekDays = computed(() => {
  const days: Date[] = []
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

// ---------------------------------------------------------------------------
// Labels & navigation
// ---------------------------------------------------------------------------

const periodLabel = computed(() => {
  const options: Intl.DateTimeFormatOptions = { month: 'long', year: 'numeric' }
  const locale = i18nLocale.value === 'es' ? 'es-ES' : 'en-US'

  if (calendarView.value === 'month') {
    return currentBaseDate.value.toLocaleDateString(locale, options)
  }
  if (calendarView.value === 'week') {
    const start = weekDays.value[0]
    const end = weekDays.value[weekDays.value.length - 1]
    if (!start || !end) return ''
    const dayOpts: Intl.DateTimeFormatOptions = { day: 'numeric' }
    return `${start.toLocaleDateString(locale, dayOpts)} – ${end.toLocaleDateString(locale, { ...dayOpts, month: 'short', year: 'numeric' })}`
  }
  // Day view
  return currentBaseDate.value.toLocaleDateString(locale, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  })
})

function goForward() {
  const d = new Date(currentBaseDate.value)
  if (calendarView.value === 'month') d.setMonth(d.getMonth() + 1)
  else if (calendarView.value === 'week') d.setDate(d.getDate() + 7)
  else d.setDate(d.getDate() + 1)
  currentBaseDate.value = d
}

function goBackward() {
  const d = new Date(currentBaseDate.value)
  if (calendarView.value === 'month') d.setMonth(d.getMonth() - 1)
  else if (calendarView.value === 'week') d.setDate(d.getDate() - 7)
  else d.setDate(d.getDate() - 1)
  currentBaseDate.value = d
}

function goToToday() {
  currentBaseDate.value = new Date()
}

// ---------------------------------------------------------------------------
// Publication queries
// ---------------------------------------------------------------------------

const filteredPublications = computed(() => {
  return publishingStore.publications.filter((pub) => {
    if (publishingStore.filterChannel && !(pub.channels as string[]).includes(publishingStore.filterChannel)) {
      return false
    }
    if (publishingStore.filterTag && !pub.content.toLowerCase().includes(publishingStore.filterTag.toLowerCase())) {
      return false
    }
    if (publishingStore.filterPostType !== 'all') {
      if (publishingStore.filterPostType === 'queued' && pub.status !== 'QUEUED') return false
      if (publishingStore.filterPostType === 'published' && pub.status !== 'PUBLISHED') return false
      if (publishingStore.filterPostType === 'cancelled' && pub.status !== 'CANCELLED') return false
    }
    return true
  })
})

function getPublicationsForDate(date: Date): Publication[] {
  return filteredPublications.value.filter((pub) => {
    const pubDate = new Date(pub.scheduledAt)
    return (
      pubDate.getDate() === date.getDate() &&
      pubDate.getMonth() === date.getMonth() &&
      pubDate.getFullYear() === date.getFullYear()
    )
  })
}

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

// ---------------------------------------------------------------------------
// UI helpers
// ---------------------------------------------------------------------------

function formatDayName(date: Date) {
  const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
  const daysEs = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado']
  const index = date.getDay()
  return (i18nLocale.value === 'es' ? daysEs : days)[index] ?? ''
}

function isToday(date: Date) {
  const now = new Date()
  return (
    date.getDate() === now.getDate() &&
    date.getMonth() === now.getMonth() &&
    date.getFullYear() === now.getFullYear()
  )
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

function dateKey(date: Date): string {
  return date.toISOString().split('T')[0] ?? ''
}

function activityForDate(date: Date): ActivityEntry | undefined {
  return activityByDate.value.get(dateKey(date))
}

function activityDotColor(density: string | undefined): string {
  switch (density) {
    case 'LIGHT':
      return 'bg-yellow-400'
    case 'MEDIUM':
      return 'bg-orange-400'
    case 'HIGH':
      return 'bg-green-500'
    default:
      return ''
  }
}

// ---------------------------------------------------------------------------
// Modal helpers
// ---------------------------------------------------------------------------

function openNewPostForSlot(date: Date, hour?: number) {
  const d = new Date(date)
  if (hour !== undefined) d.setHours(hour, 0, 0, 0)
  else d.setHours(12, 0, 0, 0)
  selectedCellDate.value = d.toISOString()
  isModalOpen.value = true
}

function openNewPostGeneral() {
  selectedCellDate.value = undefined
  isModalOpen.value = true
}

function openDayView(date: Date) {
  currentBaseDate.value = new Date(date)
  calendarView.value = 'day'
}

// Time slots mapping
const hourSlots = [
  { label: '6 AM', hour: 6 },
  { label: '8 AM', hour: 8 },
  { label: '10 AM', hour: 10 },
  { label: '12 PM', hour: 12 },
  { label: '2 PM', hour: 14 },
  { label: '4 PM', hour: 16 },
  { label: '6 PM', hour: 18 },
  { label: '8 PM', hour: 20 },
  { label: '10 PM', hour: 22 },
]

// ---------------------------------------------------------------------------
// Init
// ---------------------------------------------------------------------------

onMounted(() => {
  // Attempt to load from API on mount (range = current month)
  const now = new Date()
  const from = new Date(now.getFullYear(), now.getMonth(), 1)
  const to = new Date(now.getFullYear(), now.getMonth() + 3, 0)
  publishingStore.fetchCalendar(from.toISOString(), to.toISOString())
})
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
        <!-- Calendar View toggle (month/week/day) — only in calendar mode -->
        <div v-if="publishingStore.viewMode === 'calendar'" class="flex items-center rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[9px] tracking-wider uppercase font-bold">
          <button
            class="cursor-pointer rounded-full px-2.5 py-1 transition-all"
            :class="calendarView === 'month' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
            @click="calendarView = 'month'"
          >
            {{ $t('scheduler.calendar') || 'Month' }}
          </button>
          <button
            class="cursor-pointer rounded-full px-2.5 py-1 transition-all"
            :class="calendarView === 'week' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
            @click="calendarView = 'week'"
          >
            {{ $t('scheduler.weekView') || 'Week' }}
          </button>
          <button
            class="cursor-pointer rounded-full px-2.5 py-1 transition-all"
            :class="calendarView === 'day' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
            @click="calendarView = 'day'"
          >
            Day
          </button>
        </div>

        <!-- View mode toggle (calendar / list) -->
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
            v-model="publishingStore.userTimezone"
            class="bg-bg-surface border border-border-subtle rounded-full px-3 py-1.5 text-[10px] font-mono font-bold text-text-secondary appearance-none pr-8 cursor-pointer focus:outline-none focus:border-text-display"
          >
            <option :value="publishingStore.userTimezone">🌐 {{ publishingStore.userTimezone || 'UTC' }}</option>
            <option value="Europe/Madrid">📍 Europe/Madrid</option>
            <option value="UTC">🌐 UTC</option>
            <option value="America/New_York">🇺🇸 America/New_York</option>
          </select>
          <ChevronDown class="absolute right-3 top-1/2 -translate-y-1/2 size-3 text-text-secondary pointer-events-none" />
        </div>

        <!-- Platform/Account filter -->
        <div class="relative shrink-0">
          <select
            v-model="publishingStore.filterSocialAccountId"
            class="bg-bg-surface border border-border-subtle rounded-full px-3 py-1.5 text-[10px] font-mono font-bold text-text-secondary appearance-none pr-8 cursor-pointer focus:outline-none focus:border-text-display"
          >
            <option value="">👤 {{ $t('scheduler.channelsLabel') || 'Platform' }}</option>
            <option
              v-for="ch in publishingStore.channels"
              :key="ch.accountId"
              :value="ch.accountId"
            >
              {{ ch.provider }} ({{ ch.handle }})
            </option>
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
            <button class="flex items-center justify-between gap-1.5 p-2 rounded-xl border border-dashed border-border-visible bg-transparent hover:border-text-secondary text-text-secondary hover:text-text-display transition-all cursor-pointer font-mono text-[9px] uppercase tracking-wider">
              <span>{{ $t('scheduler.moreChannels') || '+ More channels' }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Right Main: Calendar Planner -->
      <div class="flex-1 min-w-0">
        <!-- Calendar Mode -->
        <div v-if="publishingStore.viewMode === 'calendar'" class="space-y-4">
          <!-- Calendar Subheader: Navigation -->
          <div class="flex items-center justify-between bg-bg-surface border border-border-subtle p-3 rounded-xl">
            <!-- Navigation arrows -->
            <div class="flex items-center gap-1">
              <button
                @click="goBackward"
                class="size-8 flex items-center justify-center rounded-lg border border-border-visible hover:border-text-secondary hover:text-text-display bg-bg-primary transition-colors cursor-pointer text-text-secondary"
              >
                <ChevronLeft class="size-4" />
              </button>
              <button
                @click="goForward"
                class="size-8 flex items-center justify-center rounded-lg border border-border-visible hover:border-text-secondary hover:text-text-display bg-bg-primary transition-colors cursor-pointer text-text-secondary"
              >
                <ChevronRight class="size-4" />
              </button>
            </div>

            <!-- Date Range Label -->
            <span class="font-mono text-[10px] font-bold uppercase tracking-widest text-text-display">
              {{ periodLabel }}
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
                {{ calendarView === 'month' ? 'Month' : calendarView === 'week' ? 'Week' : 'Day' }}
              </span>
            </div>
          </div>

          <!-- ================================================================ -->
          <!-- MONTH VIEW -->
          <!-- ================================================================ -->
          <div v-if="calendarView === 'month'">
            <Card class="bg-bg-surface border border-border-subtle p-0 overflow-hidden">
              <!-- Day-of-week header -->
              <div class="grid grid-cols-7 border-b border-border-subtle bg-bg-primary">
                <div
                  v-for="(_, idx) in 7"
                  :key="idx"
                  class="py-2.5 text-center border-r border-border-subtle last:border-r-0"
                >
                  <span class="font-mono text-[8px] font-bold tracking-widest text-text-secondary uppercase">
                    {{ formatDayName(new Date(2026, 0, idx + 1)).substring(0, 3) }}
                  </span>
                </div>
              </div>

              <!-- Grid body: 6 weeks × 7 days -->
              <div class="divide-y divide-border-subtle">
                <div
                  v-for="(week, wkIdx) in monthGrid"
                  :key="wkIdx"
                  class="grid grid-cols-7"
                >
              <div
                  v-for="day in week"
                  :key="day.toISOString()"
                  class="relative min-h-[90px] border-r border-border-subtle last:border-r-0 p-1.5 transition-all"
                  :class="{
                    'bg-bg-surface/30': !isCurrentMonth(day),
                    'bg-bg-primary/10': isCurrentMonth(day),
                    'cursor-pointer hover:bg-bg-primary/20': isCurrentMonth(day),
                  }"
                  @click="isCurrentMonth(day) ? openDayView(day) : undefined"
                >
                    <!-- Day number -->
                    <div class="flex items-center justify-between mb-1">
                      <span
                        class="font-mono text-[10px] font-bold leading-none size-5 flex items-center justify-center rounded-full"
                        :class="{
                          'bg-text-display text-bg-primary': isToday(day),
                          'text-text-display': !isToday(day) && isCurrentMonth(day),
                          'text-text-secondary/40': !isCurrentMonth(day),
                        }"
                      >
                        {{ day.getDate() }}
                      </span>

                      <!-- Activity dot -->
                      <div
                        v-if="activityForDate(day) && isCurrentMonth(day)"
                        class="size-2 rounded-full shrink-0"
                        :class="activityDotColor(activityForDate(day)?.density)"
                      />
                    </div>

                    <!-- Publication snippets for this day (max 3) -->
                    <div class="space-y-0.5">
                      <div
                        v-for="pub in getPublicationsForDate(day).slice(0, 3)"
                        :key="pub.id"
                        class="flex items-center gap-1 rounded-md px-1 py-0.5 text-[7px] font-mono truncate"
                        :class="getProviderColor(pub.channels[0] || 'linkedin')"
                        :draggable="publishingStore.viewMode === 'calendar'"
                        @dragstart="onDragStart($event, pub)"
                        @dragend="onDragEnd($event)"
                      >
                        <span class="shrink-0">{{ getProviderBadge(pub.channels[0] || 'linkedin') }}</span>
                        <span class="truncate">{{ pub.title || pub.content.substring(0, 20) }}</span>
                        <!-- Conflict badge -->
                        <span v-if="pub.hasConflict" class="shrink-0 size-2.5 rounded-full bg-error/20 text-error flex items-center justify-center text-[6px] font-bold">!</span>
                      </div>
                      <div
                        v-if="getPublicationsForDate(day).length > 3"
                        class="text-[7px] font-mono text-text-secondary pl-1"
                      >
                        +{{ getPublicationsForDate(day).length - 3 }} more
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </Card>
          </div>

          <!-- ================================================================ -->
          <!-- WEEK VIEW -->
          <!-- ================================================================ -->
          <div v-if="calendarView === 'week'">
            <Card class="bg-bg-surface border border-border-subtle p-0 overflow-hidden">
              <!-- Grid Header: Days of the week -->
              <div class="grid grid-cols-7 border-b border-border-subtle bg-bg-primary">
                <div
                  v-for="day in weekDays"
                  :key="day.toISOString()"
                  class="py-3.5 text-center border-r border-border-subtle last:border-r-0 flex flex-col gap-0.5"
                  :class="{
                    'bg-bg-surface/50': isToday(day)
                  }"
                >
                  <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase">
                    {{ formatDayName(day).substring(0, 3) }}
                  </span>
                  <span
                    class="font-mono text-xs font-bold leading-none size-6 flex items-center justify-center mx-auto rounded-full"
                    :class="isToday(day)
                      ? 'bg-text-display text-bg-primary'
                      : 'text-text-display'"
                  >
                    {{ day.getDate() }}
                  </span>
                </div>
              </div>

              <!-- Grid Body: Hourly timeline rows -->
              <div class="relative">
                <div v-for="slot in hourSlots" :key="slot.hour" class="grid grid-cols-7 border-b border-border-subtle last:border-b-0 min-h-[140px]">
                  <div
                    v-for="day in weekDays"
                    :key="day.toISOString()"
                    @click="openNewPostForSlot(day, slot.hour)"
                    @dragover.prevent
                    @drop.prevent="onDropCell($event, day, slot.hour)"
                    class="relative p-2 border-r border-border-subtle last:border-r-0 hover:bg-bg-primary/20 transition-all group/cell flex flex-col justify-start gap-2 select-none cursor-pointer"
                  >
                    <!-- Hour slot stamp -->
                    <span class="absolute top-1 left-2 font-mono text-[7px] tracking-wider text-text-secondary opacity-0 group-hover/cell:opacity-100 transition-opacity pointer-events-none">
                      {{ slot.label }}
                    </span>

                    <!-- Scheduled Posts -->
                    <div
                      v-for="pub in getPublicationsForSlot(day, slot.hour)"
                      :key="pub.id"
                      :draggable="true"
                      @dragstart="onDragStart($event, pub)"
                      @dragend="onDragEnd($event)"
                      class="relative border rounded-xl p-3 space-y-2.5 transition-all text-left shadow-sm group/card bg-bg-surface overflow-hidden"
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
                          <!-- Conflict badge -->
                          <span
                            v-if="pub.hasConflict"
                            class="size-3.5 rounded-full bg-error/15 text-error flex items-center justify-center font-mono text-[6px] font-bold cursor-help"
                            title="Conflicts with another publication"
                          >
                            !
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

          <!-- ================================================================ -->
          <!-- DAY VIEW -->
          <!-- ================================================================ -->
          <div v-if="calendarView === 'day'">
            <Card class="bg-bg-surface border border-border-subtle p-4 overflow-hidden">
              <!-- All-day publications -->
              <div class="mb-4">
                <span class="font-mono text-[9px] font-bold tracking-widest text-text-secondary uppercase block mb-2">
                  All day · {{ currentBaseDate.toLocaleDateString(i18nLocale === 'es' ? 'es-ES' : 'en-US', { weekday: 'long', month: 'long', day: 'numeric' }) }}
                </span>

                <div
                  v-for="pub in getPublicationsForDate(currentBaseDate)"
                  :key="pub.id"
                  :draggable="true"
                  @dragstart="onDragStart($event, pub)"
                  @dragend="onDragEnd($event)"
                  class="relative border rounded-xl p-4 space-y-2.5 transition-all text-left shadow-sm group/card bg-bg-surface overflow-hidden mb-3 last:mb-0"
                  :class="getProviderColor(pub.channels[0] || 'linkedin')"
                >
                  <div class="flex items-center justify-between">
                    <span class="font-mono text-[9px] font-bold tracking-wider opacity-80 uppercase">
                      {{ new Date(pub.scheduledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
                    </span>
                    <div class="flex items-center gap-1.5">
                      <span
                        v-for="channel in pub.channels"
                        :key="channel"
                        class="size-5 rounded-full border border-current/20 flex items-center justify-center font-mono text-[9px] uppercase tracking-wider font-bold"
                      >
                        {{ getProviderBadge(channel) }}
                      </span>
                      <span
                        v-if="pub.hasConflict"
                        class="size-4 rounded-full bg-error/15 text-error flex items-center justify-center font-mono text-[7px] font-bold cursor-help"
                        title="Conflicts with another publication"
                      >
                        !
                      </span>
                    </div>
                  </div>
                  <p class="text-sm font-light leading-relaxed text-text-body">
                    {{ pub.content }}
                  </p>
                  <div v-if="pub.title" class="text-xs font-semibold text-text-display">
                    {{ pub.title }}
                  </div>
                  <div v-if="pub.thumbnail" class="h-24 w-full rounded overflow-hidden mt-2">
                    <img :src="pub.thumbnail" class="w-full h-full object-cover grayscale opacity-75 group-hover/card:grayscale-0 group-hover/card:opacity-100 transition-all" alt="" />
                  </div>
                  <button
                    @click.stop="publishingStore.deletePost(pub.id)"
                    class="absolute top-2 right-2 opacity-0 group-hover/card:opacity-100 size-6 flex items-center justify-center rounded-full bg-black/60 text-white hover:bg-error transition-all"
                    title="Delete publication"
                  >
                    <Trash2 class="size-3" />
                  </button>
                </div>

                <!-- Empty state -->
                <div
                  v-if="getPublicationsForDate(currentBaseDate).length === 0"
                  class="border border-dashed border-border-visible rounded-xl p-12 text-center"
                >
                  <p class="font-mono text-[10px] uppercase tracking-wider text-text-secondary">
                    No publications for this day
                  </p>
                  <Button @click="openNewPostForSlot(currentBaseDate)" class="mt-3 gap-1.5 text-[10px] uppercase font-mono tracking-wider">
                    <Plus class="size-3" />
                    <span>Add Publication</span>
                  </Button>
                </div>
              </div>
            </Card>
          </div>
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
                      'bg-text-display/10 text-text-display border border-border-visible': pub.status === 'QUEUED',
                    }"
                  >
                    {{ pub.status }}
                  </span>
                  <!-- Conflict badge in list view -->
                  <span
                    v-if="pub.hasConflict"
                    class="bg-error/10 text-error border border-error/20 font-mono text-[9px] uppercase tracking-widest px-2 py-0.5 rounded-md flex items-center gap-1"
                  >
                    <span class="size-2.5 rounded-full bg-error inline-block" />
                    Conflict
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
