<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Plus,
  Trash2,
} from '@lucide/vue'
import { usePublishingStore, type Publication, type ActivityEntry } from '@/stores/publishing'
import CreatePostModal from '@/components/CreatePostModal.vue'
import PostDetailModal from '@/components/PostDetailModal.vue'
import CalendarHeader from '@/components/CalendarHeader.vue'
import CalendarCell from '@/components/CalendarCell.vue'
import ConflictBadge from '@/components/ConflictBadge.vue'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { getProviderColor, getProviderBadge } from '@/lib/provider-styles'

const publishingStore = usePublishingStore()
const { locale: i18nLocale } = useI18n()

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------
const isModalOpen = ref(false)
const selectedCellDate = ref<string | undefined>(undefined)
const isDetailModalOpen = ref(false)
const detailPublication = ref<Publication | null>(null)

/** Calendar sub-view: month | week */
const calendarView = ref<'month' | 'week'>('week')

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

async function handleReconnect() {
  try {
    await publishingStore.connectLinkedInPersonalProfile()
  } catch (err: unknown) {
    console.error('LinkedIn reconnect failed', err)
  }
}

const deletableStatuses: Publication['status'][] = ['DRAFT', 'QUEUED', 'SCHEDULED']

function isPublicationDeletable(status: Publication['status']) {
  return deletableStatuses.includes(status)
}

function toErrorMessage(err: unknown) {
  if (err && typeof err === 'object') {
    const maybeError = err as { detail?: string; title?: string; message?: string }
    return maybeError.detail ?? maybeError.title ?? maybeError.message ?? 'Failed to delete publication.'
  }
  return 'Failed to delete publication.'
}

async function handleDelete(publicationId: string) {
  try {
    await publishingStore.deletePost(publicationId)
  } catch (err: unknown) {
    window.alert(`Delete failed: ${toErrorMessage(err)}`)
  }
}

async function onDropCell(e: DragEvent, targetDate: Date, targetHour?: number) {
  e.preventDefault()
  if (!e.dataTransfer) return
  const pubId = e.dataTransfer.getData('text/plain')
  if (!pubId) return

  const d = new Date(targetDate)
  if (targetHour !== undefined) {
    d.setHours(targetHour, 0, 0, 0)
  } else if (dragData.value?.previousScheduledAt) {
    // Preserve original time when dropping in month view
    const prev = new Date(dragData.value.previousScheduledAt)
    d.setHours(prev.getHours(), prev.getMinutes(), 0, 0)
  }

  // Guard: reject drops to past time slots
  const earliestAllowed = new Date(Date.now() + 5 * 60_000)
  if (d < earliestAllowed) {
    console.warn('Cannot reschedule to a past time slot.')
    dragData.value = null
    return
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
  return ''
})

function goForward() {
  const d = new Date(currentBaseDate.value)
  if (calendarView.value === 'month') d.setMonth(d.getMonth() + 1)
  else d.setDate(d.getDate() + 7)
  currentBaseDate.value = d
}

function goBackward() {
  const d = new Date(currentBaseDate.value)
  if (calendarView.value === 'month') d.setMonth(d.getMonth() - 1)
  else d.setDate(d.getDate() - 7)
  currentBaseDate.value = d
}

function goToToday() {
  currentBaseDate.value = new Date()
}

// ---------------------------------------------------------------------------
// Publication queries
// ---------------------------------------------------------------------------

function publicationMatchesFilters(
  pub: Publication,
  filters: {
    channel?: string
    socialAccountId?: string
    tag?: string
    postType?: string
  },
): boolean {
  if (filters.channel && !(pub.channels as string[]).includes(filters.channel)) {
    return false
  }
  if (filters.socialAccountId && pub.accountId !== filters.socialAccountId) {
    return false
  }
  if (filters.tag && !pub.content.toLowerCase().includes(filters.tag.toLowerCase())) {
    return false
  }

  switch (filters.postType) {
    case 'queued':
      return pub.status === 'QUEUED'
    case 'published':
      return pub.status === 'PUBLISHED'
    case 'cancelled':
      return pub.status === 'CANCELLED'
    default:
      return true
  }
}

const filteredPublications = computed(() =>
  publishingStore.publications.filter((pub) =>
    publicationMatchesFilters(pub, {
      channel: publishingStore.filterChannel || undefined,
      socialAccountId: publishingStore.filterSocialAccountId || undefined,
      tag: publishingStore.filterTag || undefined,
      postType: publishingStore.filterPostType !== 'all' ? publishingStore.filterPostType : undefined,
    }),
  ),
)

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

// ---------------------------------------------------------------------------
// Past-date / past-slot helpers
// ---------------------------------------------------------------------------
function isPastDate(date: Date): boolean {
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const cellDate = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  return cellDate < today
}

function isPastSlot(date: Date, hour: number): boolean {
  const now = new Date()
  const slotDate = new Date(date)
  slotDate.setHours(hour, 0, 0, 0)
  return slotDate.getTime() < now.getTime() + 5 * 60_000
}

function dateKey(date: Date): string {
  return date.toISOString().split('T')[0] ?? ''
}

function activityForDate(date: Date): ActivityEntry | undefined {
  return activityByDate.value.get(dateKey(date))
}

// ---------------------------------------------------------------------------
// Modal helpers
// ---------------------------------------------------------------------------

function openNewPostForSlot(date: Date, hour?: number) {
  if (publishingStore.hasNoChannels) return

  const d = new Date(date)
  if (hour !== undefined) d.setHours(hour, 0, 0, 0)
  else d.setHours(12, 0, 0, 0)
  selectedCellDate.value = d.toISOString()
  isModalOpen.value = true
}

function openNewPostGeneral() {
  if (publishingStore.hasNoChannels) return

  selectedCellDate.value = undefined
  isModalOpen.value = true
}

function openPostDetail(pub: Publication) {
  detailPublication.value = pub
  isDetailModalOpen.value = true
}

function closePostDetail() {
  isDetailModalOpen.value = false
  detailPublication.value = null
}

// Time slots mapping (24 hours starting at 12 AM)
const hourSlots = Array.from({ length: 24 }, (_, i) => {
  const ampm = i >= 12 ? 'PM' : 'AM'
  const displayHour = i % 12 === 0 ? 12 : i % 12
  return {
    label: `${displayHour} ${ampm}`,
    hour: i,
  }
})

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
  <div class="flex h-full min-h-0 flex-col gap-6">
    <!-- Calendar Header: navigation, view toggle, filters -->
    <CalendarHeader
      :calendar-view="calendarView"
      :period-label="periodLabel"
      @update:calendar-view="calendarView = $event"
      @forward="goForward"
      @backward="goBackward"
      @today="goToToday"
      @new-post="openNewPostGeneral"
    />

    <!-- Reconnect prompt for LinkedIn accounts requiring re-authentication -->
    <div
      v-if="publishingStore.hasReconnectRequiredChannels"
      class="flex items-center gap-3 px-4 py-3 rounded-xl border border-warning/30 bg-warning/5"
    >
      <span class="font-mono text-[10px] font-bold tracking-wider uppercase text-warning">
        Reconnect Required
      </span>
      <span class="text-xs text-text-secondary">
        Some LinkedIn accounts need re-authentication to resume publishing.
      </span>
      <Button
        @click="publishingStore.connectLinkedInPersonalProfile()"
        class="ml-auto gap-1.5 text-[10px] uppercase font-mono tracking-wider bg-warning/10 text-warning border border-warning/30 hover:bg-warning/20"
      >
        Reconnect
      </Button>
    </div>

    <!-- Main Workspace Layout -->
    <div class="min-w-0 min-h-0 flex-1">
        <!-- Calendar Mode -->
        <div v-if="publishingStore.viewMode === 'calendar'" class="flex h-full min-h-0 flex-col gap-4">
          <!-- ================================================================ -->
          <!-- MONTH VIEW -->
          <!-- ================================================================ -->
          <div v-if="calendarView === 'month'" class="flex h-full min-h-0 flex-col">
            <Card class="bg-bg-surface border border-border-subtle p-0 overflow-hidden flex-1 min-h-0 flex flex-col">
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
                  <CalendarCell
                    v-for="day in week"
                    :key="day.toISOString()"
                    :date="day"
                    :is-current-month="isCurrentMonth(day)"
                    :is-today="isToday(day)"
                    :is-past="isPastDate(day)"
                    :publications="getPublicationsForDate(day)"
                    :activity-entry="activityForDate(day) ?? null"
                    :draggable="publishingStore.viewMode === 'calendar'"
                    @dragstart="(p) => onDragStart(p.event, p.pub)"
                    @dragend="onDragEnd"
                    @drop-cell="(p) => onDropCell(p.event, p.date)"
                  />
                </div>
              </div>
            </Card>
          </div>

          <!-- ================================================================ -->
          <!-- WEEK VIEW -->
          <!-- ================================================================ -->
          <div v-if="calendarView === 'week'" class="flex h-full min-h-0 flex-col">
            <Card class="bg-bg-surface border border-border-subtle p-0 overflow-hidden flex-1 min-h-0 flex flex-col">
              <!-- Grid Header: Time-axis label + Days of the week -->
              <div class="grid grid-cols-[48px_repeat(7,1fr)] border-b border-border-subtle bg-bg-primary">
                <!-- Time-axis header spacer -->
                <div class="py-3.5 border-r border-border-subtle" />
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

              <!-- Grid Body: Single left time-axis + 7 day columns — scrolls internally, header stays sticky -->
              <div class="relative min-h-0 flex-1 overflow-y-auto">
                <div v-for="slot in hourSlots" :key="slot.hour" class="grid h-[96px] grid-cols-[48px_repeat(7,1fr)] border-b border-border-subtle last:border-b-0">
                  <!-- Single left time-axis label -->
                  <div class="py-2 border-r border-border-subtle flex items-start justify-center">
                    <span class="font-mono text-[9px] tracking-wider text-text-secondary">
                      {{ slot.label }}
                    </span>
                  </div>
                  <!-- Day columns -->
                  <!-- biome-ignore lint/a11y/noStaticElementInteractions: role is set conditionally, dragover/drop handlers are passive -->
                  <div
                    v-for="day in weekDays"
                    :key="day.toISOString()"
                    :role="!isPastSlot(day, slot.hour) ? 'button' : undefined"
                    :tabindex="!isPastSlot(day, slot.hour) ? 0 : -1"
                    @click="!isPastSlot(day, slot.hour) ? openNewPostForSlot(day, slot.hour) : undefined"
                    @keydown.enter.prevent="!isPastSlot(day, slot.hour) ? openNewPostForSlot(day, slot.hour) : undefined"
                    @keydown.space.prevent="!isPastSlot(day, slot.hour) ? openNewPostForSlot(day, slot.hour) : undefined"
                    @dragover.prevent="!isPastSlot(day, slot.hour)"
                    @drop.prevent="!isPastSlot(day, slot.hour) ? onDropCell($event, day, slot.hour) : undefined"
                    class="relative p-2 border-r border-border-subtle last:border-r-0 transition-all group/cell flex flex-col justify-start gap-2 select-none"
                    :class="isPastSlot(day, slot.hour)
                      ? 'bg-text-secondary/5 text-text-secondary c‍ursor-not-allowed after:absolute after:inset-0 after:bg-[repeating-linear-gradient(-45deg,transparent,transparent_10px,var(--border-color)_10px,var(--border-color)_11px)] after:opacity-10 after:z-0'
                      : 'hover:bg-bg-primary/20 cursor-pointer'"
                    :aria-disabled="isPastSlot(day, slot.hour)"
                    :title="isPastSlot(day, slot.hour) ? 'Past time slots are disabled (read-only)' : undefined"
                  >
                    <!-- Scheduled Posts -->
                    <button
                      v-for="pub in getPublicationsForSlot(day, slot.hour)"
                      :key="pub.id"
                      :draggable="true"
                      type="button"
                      @click.stop="openPostDetail(pub)"
                      @keydown.enter.self.stop.prevent="openPostDetail(pub)"
                      @keydown.space.self.stop.prevent="openPostDetail(pub)"
                      @dragstart="onDragStart($event, pub)"
                      @dragend="onDragEnd($event)"
                      class="relative z-10 flex h-[72px] w-full min-w-0 flex-col overflow-hidden rounded-xl border bg-bg-surface p-3 text-left shadow-sm transition-all group/card cursor-pointer"
                      :class="getProviderColor(pub.channels[0] || 'linkedin')"
                    >
                      <!-- Header -->
                      <div class="flex shrink-0 items-center justify-between gap-2">
                        <span class="font-mono text-[8px] font-bold tracking-wider opacity-80 uppercase">
                          {{ new Date(pub.scheduledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
                        </span>
                        <div class="flex min-w-0 shrink-0 gap-1">
                          <span
                            v-for="channel in pub.channels"
                            :key="channel"
                            class="size-4.5 rounded-full border border-current/20 flex items-center justify-center font-mono text-[8px] uppercase tracking-wider font-bold"
                          >
                            {{ getProviderBadge(channel) }}
                          </span>
                          <!-- BLOCKED indicator -->
                          <span
                            v-if="pub.status === 'BLOCKED'"
                            class="px-1.5 py-0.5 rounded text-[7px] font-bold tracking-wider uppercase bg-warning/20 text-warning border border-warning/30"
                          >
                            BLOCKED
                          </span>
                          <!-- Conflict badge -->
                          <ConflictBadge
                            v-if="pub.hasConflict"
                            variant="badge"
                          />
                        </div>
                      </div>

                      <div class="flex flex-row items-stretch gap-2 min-h-0 flex-1">
                        <!-- Text content -->
                        <p class="min-w-0 flex-1 overflow-hidden text-[11px] font-light leading-relaxed text-text-body break-words [display:-webkit-box] [-webkit-box-orient:vertical] [-webkit-line-clamp:3] [overflow-wrap:anywhere]">
                          {{ pub.content }}
                        </p>

                        <div v-if="pub.thumbnail" class="h-full w-14 shrink-0 overflow-hidden rounded-md border border-border-subtle/80">
                          <img
                            :src="pub.thumbnail"
                            class="h-full w-full object-cover"
                            alt=""
                          />
                        </div>
                      </div>

                      <!-- Delete button overlay on card hover (not for published posts) -->
                      <button
                        v-if="isPublicationDeletable(pub.status)"
                        @click.stop="handleDelete(pub.id)"
                        class="absolute top-1 right-1 opacity-0 group-hover/card:opacity-100 size-5 flex items-center justify-center rounded-full bg-black/60 text-white hover:bg-error transition-all"
                        title="Delete publication"
                      >
                        <Trash2 class="size-2.5" />
                      </button>
                    </button>

                    <!-- Add post button (only in enabled slots) -->
                    <button
                      v-if="!isPastSlot(day, slot.hour)"
                      @click.stop="openNewPostForSlot(day, slot.hour)"
                      class="hidden group-hover/cell:flex items-center justify-center size-6 rounded-lg border border-dashed border-text-secondary/30 text-text-secondary/50 hover:border-text-display/40 hover:text-text-display/60 hover:bg-bg-primary/30 transition-all mt-auto cursor-pointer"
                      :title="$t('scheduler.addPost')"
                    >
                      <Plus class="size-3" />
                    </button>
                  </div>
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
              <button
                v-for="pub in filteredPublications"
                :key="pub.id"
                type="button"
                class="group/card flex flex-col md:flex-row md:items-center justify-between gap-4 p-5 rounded-2xl border border-border-subtle bg-bg-surface hover:border-text-secondary transition-all cursor-pointer w-full text-left"
                @click="openPostDetail(pub)"
                @keydown.enter.self.stop.prevent="openPostDetail(pub)"
                @keydown.space.self.stop.prevent="openPostDetail(pub)"
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
                      'bg-warning/10 text-warning border border-warning/20': pub.status === 'BLOCKED',
                      'bg-error/10 text-error border border-error/20': pub.status === 'FAILED',
                    }"
                  >
                    {{ pub.status }}
                  </span>
                  <!-- BLOCKED reconnect prompt in list view -->
                  <button
                    v-if="pub.status === 'BLOCKED'"
                    @click="handleReconnect"
                    class="text-[9px] underline text-warning hover:text-warning/80 font-medium"
                  >
                    Reconnect
                  </button>
                  <!-- Conflict badge in list view -->
                  <ConflictBadge
                    v-if="pub.hasConflict"
                    variant="inline"
                  />
                </div>
                <p class="text-sm font-light text-text-body leading-relaxed break-words">
                  {{ pub.content }}
                </p>
                <div v-if="pub.thumbnail" class="h-24 w-full overflow-hidden rounded-xl border border-border-subtle">
                  <img
                    :src="pub.thumbnail"
                    class="h-full w-full object-cover"
                    alt=""
                  />
                </div>
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
                  v-if="pub.status !== 'PUBLISHED'"
                  @click.stop="handleDelete(pub.id)"
                  class="group-hover/card:opacity-100 opacity-0 size-8 flex items-center justify-center rounded-xl border border-border-visible hover:border-error text-text-secondary hover:text-error transition-all bg-bg-primary cursor-pointer"
                  title="Delete publication"
                >
                  <Trash2 class="size-4" />
                </button>
              </div>
            </button>
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

    <!-- Read-only post detail modal -->
    <PostDetailModal
      :is-open="isDetailModalOpen"
      :publication="detailPublication"
      @close="closePostDetail"
      @deleted="closePostDetail"
    />
  </div>
</template>
