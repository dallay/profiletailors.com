<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Plus,
  Trash2,
} from '@lucide/vue'
import { usePublishingStore, type Publication, type ActivityEntry, type RecurringSchedule } from '@modules/publishing/infrastructure/publishing.store'
import { useCalendarUrl } from '@modules/publishing/application/useCalendarUrl'
import CreatePostModal from '@modules/publishing/presentation/components/CreatePostModal.vue'
import PostDetailModal from '@modules/publishing/presentation/components/PostDetailModal.vue'
import RecurringScheduleModal from '@modules/publishing/presentation/components/RecurringScheduleModal.vue'
import BulkImportModal from '@modules/publishing/presentation/components/BulkImportModal.vue'
import CalendarHeader from '@modules/publishing/presentation/components/CalendarHeader.vue'
import CalendarCell from '@modules/publishing/presentation/components/CalendarCell.vue'
import ConflictBadge from '@modules/publishing/presentation/components/ConflictBadge.vue'
import SocialProviderIcon from '@shared/components/SocialProviderIcon.vue'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { getProviderColor } from '@shared/lib/provider-styles'
import { toast } from 'vue-sonner'

const publishingStore = usePublishingStore()
const { locale: i18nLocale, t } = useI18n()


const url = useCalendarUrl()

/** Calendar sub-view derived from URL surface */
const calendarView = computed(() => {
  if (url.state.value.surface === 'calendar-month') return 'month' as const
  return 'week' as const
})

/** Navigation base date derived from URL date param */
const currentBaseDate = computed(() => {
  const parsed = new Date(`${url.state.value.date}T00:00:00`)
  return Number.isNaN(parsed.getTime()) ? new Date() : parsed
})


const isModalOpen = ref(false)
const isBulkModalOpen = ref(false)
const selectedCellDate = ref<string | undefined>(undefined)
const editingPublication = ref<Publication | null>(null)
const editingRecurringSchedule = ref<RecurringSchedule | null>(null)
const recurringPublication = ref<Publication | null>(null)

onMounted(() => {
  publishingStore.fetchRecurringSchedules().catch(() => undefined)
})

async function toggleRecurringSchedule(schedule: RecurringSchedule): Promise<void> {
  try {
    await publishingStore.updateRecurringSchedule(schedule.id, {
      status: schedule.status === 'PAUSED' ? 'ACTIVE' : 'PAUSED',
    })
  } catch (err: unknown) {
    console.error('Failed to toggle recurring schedule', err)
  }
}

async function cancelRecurringSchedule(schedule: RecurringSchedule): Promise<void> {
  try {
    await publishingStore.cancelRecurringSchedule(schedule.id)
  } catch (err: unknown) {
    console.error('Failed to cancel recurring schedule', err)
  }
}

function openRecurringEditor(schedule: RecurringSchedule): void {
  const publication = publishingStore.publications.find((item) => item.id === schedule.templatePostId)
  recurringPublication.value = publication ?? null
  editingRecurringSchedule.value = schedule
}


const dragData = ref<{ id: string; previousScheduledAt: string } | null>(null)

function onDragStart(e: DragEvent, pub: Publication) {
  if (!e.dataTransfer) return
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', pub.id)
  dragData.value = { id: pub.id, previousScheduledAt: pub.scheduledAt }

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
  url.stepPeriod('forward')
}

function goBackward() {
  url.stepPeriod('backward')
}

function goToToday() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  url.setDate(`${y}-${m}-${d}`)
}

function handleHeaderViewChange(surface: 'calendar-week' | 'calendar-month' | 'list') {
  url.setSurface(surface)
}

function handleHeaderDateChange(action: 'forward' | 'backward' | 'today') {
  if (action === 'forward') {
    goForward()
    return
  }
  if (action === 'backward') {
    goBackward()
    return
  }
  goToToday()
}

function handleHeaderFilterChange(filter: {
  status?: 'all' | 'queued' | 'published' | 'cancelled'
  timezone?: string
  channelIds?: string[]
}) {
  if (filter.status !== undefined) {
    url.setStatus(filter.status)
  }
  if (filter.timezone !== undefined) {
    url.setTimezone(filter.timezone)
  }
  if (filter.channelIds !== undefined) {
    url.setChannelIds(filter.channelIds)
  }
}


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

const filteredPublications = computed(() => {
  const activeChannelId = url.state.value.channelIds[0]
  return publishingStore.publications.filter((pub) =>
    publicationMatchesFilters(pub, {
      socialAccountId: activeChannelId || undefined,
      tag: url.state.value.q || undefined,
      postType: url.state.value.status === 'all' ? undefined : url.state.value.status,
    }),
  )
})

const detailPublication = computed<Publication | null>(() => {
  const postId = url.state.value.postId
  if (!postId) return null
  return filteredPublications.value.find((pub) => pub.id === postId) ?? null
})

const isDetailModalOpen = computed(() => detailPublication.value !== null)

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

function slotKey(date: Date, hour: number): string {
  return `${dateKey(date)}#${hour}`
}

const publicationsBySlot = computed(() => {
  const map = new Map<string, Publication[]>()
  for (const pub of filteredPublications.value) {
    const pubDate = new Date(pub.scheduledAt)
    const key = slotKey(pubDate, pubDate.getHours())
    const bucket = map.get(key)
    if (bucket) bucket.push(pub)
    else map.set(key, [pub])
  }
  return map
})

function publicationsForSlot(date: Date, hour: number): Publication[] {
  return publicationsBySlot.value.get(slotKey(date, hour)) ?? []
}


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
  // Use local date components instead of toISOString() (UTC), otherwise a
  // positive UTC offset (e.g. Europe/Madrid in summer) shifts the key of a
  // local-midnight column date back a day while a post scheduled later in
  // the day keeps its key, misaligning the two and placing posts under the
  // wrong day column.
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function activityForDate(date: Date): ActivityEntry | undefined {
  return activityByDate.value.get(dateKey(date))
}


function openNewPostForSlot(date: Date, hour?: number) {
  if (publishingStore.hasNoChannels) return

  const d = new Date(date)
  if (hour === undefined) d.setHours(12, 0, 0, 0)
  else d.setHours(hour, 0, 0, 0)
  selectedCellDate.value = d.toISOString()
  isModalOpen.value = true
}

function openNewPostGeneral() {
  if (publishingStore.hasNoChannels) return

  selectedCellDate.value = undefined
  isModalOpen.value = true
}

function openDayView(date: Date) {
  // Format using local date components to avoid UTC-offset day shifts.
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  url.setDate(`${y}-${m}-${d}`)
}

async function handleDeletePublication(id: string) {
  const wasDetailPublication = detailPublication.value?.id === id
  try {
    await publishingStore.deletePost(id)
    if (wasDetailPublication) {
      await closePostDetail()
    }
  } catch (err) {
    console.warn('Delete failed', err)
  }
}

function openPostDetail(pub: Publication) {
  url.openPostDetail(pub.id).catch(() => undefined)
}

function closePostDetail(options?: { replace?: boolean }) {
  return url.closePostDetail(options)
}

async function handleEditPublication(publication: Publication) {
  editingPublication.value = publication
  isModalOpen.value = true
  await closePostDetail()
}

async function handleUpdated() {
  isModalOpen.value = false
  editingPublication.value = null

  const state = url.state.value
  const baseDate = new Date(`${state.date}T00:00:00`)
  const from =
    state.surface === 'calendar-month'
      ? new Date(baseDate.getFullYear(), baseDate.getMonth(), 1)
      : new Date(baseDate.getFullYear(), baseDate.getMonth(), baseDate.getDate() - baseDate.getDay())
  const to =
    state.surface === 'calendar-month'
      ? new Date(baseDate.getFullYear(), baseDate.getMonth() + 1, 0)
      // Week view: end is exclusive (backend uses `scheduled_for < :to`),
      // so add 7 days to cover Sunday→Saturday fully including the last day.
      : new Date(from.getFullYear(), from.getMonth(), from.getDate() + 7)

  try {
    await publishingStore.fetchCalendar(from.toISOString(), to.toISOString(), {
      status: state.status === 'all' ? undefined : state.status,
      socialAccountId: state.channelIds[0],
      timezone: state.timezone,
    })
  } catch {}
}

function handleBulkScheduled(jobId: string) {
  isBulkModalOpen.value = false
  toast.success(`Bulk job ${jobId} scheduled`)
  handleUpdated()
}

function onPostCreated(options: { keepOpen?: boolean } = {}) {
  if (!options.keepOpen) isModalOpen.value = false
  toast.success(t('composer.scheduleSuccessToast'))
}

function onReschedule() {
  // Store already updated by PostDetailModal; just close
  closePostDetail().catch(() => undefined)
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


watch(
  () => url.needsCanonicalization.value,
  async (needsCanonicalization) => {
    if (needsCanonicalization) {
      await url.canonicalize()
    }
  },
  { immediate: true },
)

let latestFetchToken = 0

watch(
  () => url.state.value,
  async (state) => {
    const fetchToken = ++latestFetchToken
    const baseDate = new Date(`${state.date}T00:00:00`)
    const from =
      state.surface === 'calendar-month'
        ? new Date(baseDate.getFullYear(), baseDate.getMonth(), 1)
        : new Date(baseDate.getFullYear(), baseDate.getMonth(), baseDate.getDate() - baseDate.getDay())
    const to =
      state.surface === 'calendar-month'
        ? new Date(baseDate.getFullYear(), baseDate.getMonth() + 1, 0)
        // Week view: end is exclusive (backend uses `scheduled_for < :to`),
        // so add 7 days to cover Sunday→Saturday fully including the last day.
        : new Date(from.getFullYear(), from.getMonth(), from.getDate() + 7)

    try {
      await publishingStore.fetchCalendar(from.toISOString(), to.toISOString(), {
        status: state.status === 'all' ? undefined : state.status,
        socialAccountId: state.channelIds[0],
        timezone: state.timezone,
      })
    } catch {
      return
    }

    if (fetchToken !== latestFetchToken) {
      return
    }

    if (state.postId && !filteredPublications.value.some((pub) => pub.id === state.postId)) {
      await closePostDetail({ replace: true })
    }
  },
  { immediate: true, deep: true },
)
</script>

<template>
  <div data-testid="scheduler-root" class="flex min-h-0 flex-1 flex-col gap-6">
    <CalendarHeader
      :calendar-view="calendarView"
      :period-label="periodLabel"
      :surface="url.state.value.surface"
      :timezone="url.state.value.timezone"
      :status="url.state.value.status"
      :channel-ids="url.state.value.channelIds"
      @change:view="handleHeaderViewChange"
      @change:date="handleHeaderDateChange"
      @change:filter="handleHeaderFilterChange"
      @new-post="openNewPostGeneral"
    />
    <div class="flex justify-end">
      <Button data-testid="open-bulk-import" variant="outline" class="gap-2" @click="isBulkModalOpen = true">Bulk Import</Button>
    </div>

    <!-- Reconnect prompt for LinkedIn accounts requiring re-authentication -->
    <div
      v-if="publishingStore.hasReconnectRequiredChannels"
      class="flex shrink-0 items-center gap-3 rounded-xl border border-warning/30 bg-warning/5 px-4 py-3"
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

    <Card v-if="publishingStore.recurringSchedules.length" class="shrink-0 border border-border-subtle bg-bg-surface p-4">
      <div class="mb-3 flex items-center justify-between">
        <h2 class="font-mono text-xs font-bold uppercase tracking-widest text-text-display">{{ t('scheduler.recurringSchedules') }}</h2>
        <span class="text-[10px] text-text-secondary">{{ publishingStore.recurringSchedules.length }}</span>
      </div>
      <div class="grid gap-2 md:grid-cols-2">
        <div v-for="schedule in publishingStore.recurringSchedules" :key="schedule.id" class="flex items-center gap-3 rounded-xl border border-border-subtle bg-bg-primary/30 px-3 py-2">
          <div class="min-w-0 flex-1">
            <p class="truncate text-xs font-semibold text-text-display">{{ t(`postDetail.recurring.${schedule.frequency}`) }} · {{ schedule.interval }}</p>
            <p class="text-[10px] text-text-secondary">{{ t(`scheduler.status.${schedule.status.toLowerCase()}`) }} · {{ schedule.nextScheduledAt ? new Date(schedule.nextScheduledAt).toLocaleString(i18nLocale) : '—' }}</p>
          </div>
          <button type="button" class="rounded-lg border border-border-visible px-2 py-1 text-[10px] font-mono uppercase" @click="openRecurringEditor(schedule)">{{ t('scheduler.edit') }}</button>
          <button v-if="schedule.status !== 'CANCELLED'" type="button" class="rounded-lg border border-border-visible px-2 py-1 text-[10px] font-mono uppercase" @click="toggleRecurringSchedule(schedule)">{{ schedule.status === 'PAUSED' ? t('scheduler.resume') : t('scheduler.pause') }}</button>
          <button v-if="schedule.status !== 'CANCELLED'" type="button" class="rounded-lg border border-error/50 px-2 py-1 text-[10px] font-mono uppercase text-error" @click="cancelRecurringSchedule(schedule)">{{ t('scheduler.cancel') }}</button>
        </div>
      </div>
    </Card>

    <div data-testid="scheduler-workspace" class="flex min-w-0 min-h-0 flex-1 flex-col overflow-hidden">
        <div v-if="url.state.value.surface !== 'list'" data-testid="calendar-mode" class="flex min-h-0 flex-1 flex-col gap-4">
          <div v-if="calendarView === 'month'" class="flex h-full min-h-0 flex-col">
            <Card class="bg-bg-surface border border-border-subtle p-0 overflow-hidden flex min-h-0 flex-1 flex-col">
              <div class="grid grid-cols-7 border-b border-border-subtle bg-bg-primary shrink-0">
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

              <div class="thin-scrollbar min-h-0 flex-1 overflow-y-auto divide-y divide-border-subtle">
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
                    :draggable="true"
                    @click-day="openDayView"
                    @click-publication="openPostDetail"
                    @dragstart="(p) => onDragStart(p.event, p.pub)"
                    @dragend="onDragEnd"
                    @drop-cell="(p) => onDropCell(p.event, p.date)"
                  />
                </div>
              </div>
            </Card>
          </div>

          <div v-if="calendarView === 'week'" class="flex min-h-0 flex-1 flex-col">
            <Card class="flex min-h-0 flex-1 flex-col overflow-hidden border border-border-subtle bg-bg-surface p-0">
              <div class="shrink-0 grid grid-cols-[48px_repeat(7,minmax(0,1fr))] border-b border-border-subtle bg-bg-primary">
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

              <div data-testid="week-timeline-viewport" class="thin-scrollbar relative min-h-0 flex-1 overflow-y-auto">
                <div v-for="slot in hourSlots" :key="slot.hour" class="grid h-[96px] grid-cols-[48px_repeat(7,minmax(0,1fr))] border-b border-border-subtle last:border-b-0">
                  <div class="py-2 border-r border-border-subtle flex items-start justify-center">
                    <span class="font-mono text-[9px] tracking-wider text-text-secondary">
                      {{ slot.label }}
                    </span>
                  </div>
                  <button
                    v-for="day in weekDays"
                    :key="day.toISOString()"
                    type="button"
                    :disabled="isPastSlot(day, slot.hour)"
                    :aria-label="`Slot for ${formatDayName(day)} at ${slot.label}`"
                    @click="isPastSlot(day, slot.hour) ? undefined : openNewPostForSlot(day, slot.hour)"
                    @keydown.enter.prevent="isPastSlot(day, slot.hour) ? undefined : openNewPostForSlot(day, slot.hour)"
                    @keydown.space.prevent="isPastSlot(day, slot.hour) ? undefined : openNewPostForSlot(day, slot.hour)"
                    @dragover.prevent="!isPastSlot(day, slot.hour)"
                    @drop.prevent="!isPastSlot(day, slot.hour) ? onDropCell($event, day, slot.hour) : undefined"
                    class="relative p-2 border-r border-border-subtle last:border-r-0 transition-all group/cell flex flex-col justify-start gap-1 select-none overflow-hidden"
                    :class="isPastSlot(day, slot.hour)
                      ? 'bg-text-secondary/5 text-text-secondary cursor-not-allowed after:absolute after:inset-0 after:bg-[repeating-linear-gradient(-45deg,transparent,transparent_10px,var(--border-color)_10px,var(--border-color)_11px)] after:opacity-10 after:z-0'
                      : 'hover:bg-bg-primary/20 cursor-pointer'"
                    :aria-disabled="isPastSlot(day, slot.hour)"
                    :title="isPastSlot(day, slot.hour) ? 'Past time slots are disabled (read-only)' : undefined"
                  >
                    <template v-for="slotPubs in [publicationsForSlot(day, slot.hour)]" :key="slotPubs.length">
                    <!-- biome-ignore lint/a11y/noStaticElementInteractions: non-button container required to avoid nested buttons (delete btn inside card) -->
                    <div
                      v-for="pub in slotPubs.slice(0, 2)"
                      :key="pub.id"
                      :draggable="true"
                      @click.stop="openPostDetail(pub)"
                      @keydown.enter.self.stop.prevent="openPostDetail(pub)"
                      @keydown.space.self.stop.prevent="openPostDetail(pub)"
                      @dragstart="onDragStart($event, pub)"
                      @dragend="onDragEnd($event)"
                      class="relative z-10 grid w-full min-w-0 overflow-hidden rounded-md border bg-bg-surface text-left shadow-sm transition-[box-shadow,transform] group/card cursor-pointer hover:-translate-y-px hover:shadow-md"
                      :class="[
                        getProviderColor(pub.channels[0] || 'linkedin'),
                        slotPubs.length > 1
                          ? 'h-[36px] grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-1.5 px-2 py-1'
                          : 'h-[72px] grid-cols-[minmax(0,1fr)_auto] grid-rows-[auto_1fr] gap-x-2 px-2 py-1.5',
                      ]"
                    >
                      <div
                        class="flex min-w-0 items-center gap-1.5"
                        :class="slotPubs.length > 1 ? '' : 'col-start-1 row-start-1'"
                      >
                        <span
                          v-for="channel in pub.channels"
                          :key="channel"
                          class="flex size-3 shrink-0 items-center justify-center rounded-[3px]"
                        >
                          <SocialProviderIcon :provider="channel" />
                        </span>
                        <span class="shrink-0 font-mono text-[8px] font-bold tracking-wider opacity-80 uppercase">
                          {{ new Date(pub.scheduledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
                        </span>
                        <p
                          v-if="slotPubs.length > 1"
                          class="min-w-0 truncate text-[10px] font-medium leading-tight text-text-body"
                        >
                          {{ pub.content }}
                        </p>
                      </div>

                      <p
                        v-if="slotPubs.length === 1"
                        class="col-start-1 row-start-2 min-w-0 overflow-hidden text-[11px] font-light leading-snug text-text-body break-words [display:-webkit-box] [-webkit-box-orient:vertical] [-webkit-line-clamp:2] [overflow-wrap:anywhere]"
                      >
                          {{ pub.content }}
                      </p>

                      <div
                        class="flex shrink-0 items-center justify-end gap-1"
                        :class="slotPubs.length > 1 ? 'col-start-3' : 'col-start-2 row-span-2 row-start-1 self-stretch'"
                      >
                        <span
                          v-if="pub.status === 'BLOCKED'"
                          class="rounded-sm border border-warning/30 bg-warning/20 px-1 py-0.5 text-[7px] font-bold tracking-wider text-warning uppercase"
                        >
                          BLOCKED
                        </span>
                        <ConflictBadge v-if="pub.hasConflict" variant="badge" />
                        <div
                          v-if="pub.thumbnail"
                          class="overflow-hidden rounded-sm border border-border-subtle/80"
                          :class="slotPubs.length > 1 ? 'size-6' : 'h-full w-14'"
                        >
                          <img :src="pub.thumbnail" class="h-full w-full object-cover" alt="" />
                        </div>
                      </div>

                      <!-- Delete button overlay on card hover (not for published posts) -->
                      <button type="button"
                        v-if="publishingStore.isPublicationDeletable(pub.status)"
                        @click.stop="handleDeletePublication(pub.id)"
                        class="absolute top-1 right-1 opacity-0 group-hover/card:opacity-100 size-5 flex items-center justify-center rounded-full bg-black/60 text-white hover:bg-error transition-all"
                        title="Delete publication"
                      >
                        <Trash2 class="size-2.5" />
                      </button>
                    </div>

                    <!-- "+N more" indicator when posts exceed visible limit -->
                    <div
                      v-if="slotPubs.length > 2"
                      class="text-[7px] font-mono text-text-secondary pl-1"
                    >
                      {{ t('scheduler.morePosts', { count: slotPubs.length - 2 }) }}
                    </div>

                    <!-- Add post button (only in enabled slots) -->
                    <button type="button"
                      v-if="!isPastSlot(day, slot.hour)"
                      @click.stop="openNewPostForSlot(day, slot.hour)"
                      class="hidden group-hover/cell:flex items-center justify-center size-6 rounded-lg border border-dashed border-text-secondary/30 text-text-secondary/50 hover:border-text-display/40 hover:text-text-display/60 hover:bg-bg-primary/30 transition-all mt-auto cursor-pointer"
                      :title="$t('scheduler.addPost')"
                    >
                      <Plus class="size-3" />
                    </button>
                    </template>
                  </button>
                </div>
              </div>
            </Card>
          </div>

        </div>

        <div v-else class="flex h-full min-h-0 flex-col gap-4">
          <div v-if="filteredPublications.length === 0" class="border border-dashed border-border-visible rounded-2xl p-12 text-center text-text-secondary font-mono text-xs uppercase tracking-wider">
            {{ $t('dashboard.noPosts') || 'No posts match your current filters.' }}
          </div>

          <div v-else class="thin-scrollbar min-h-0 flex-1 overflow-y-auto space-y-3 pr-1">
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
                  <!-- biome-ignore lint/a11y/useSemanticElements: parent is <button>, cannot nest HTML buttons -->
                  <span
                    v-if="pub.status === 'BLOCKED'"
                    role="button"
                    tabindex="0"
                    @click.stop="handleReconnect"
                    @keydown.enter.stop="handleReconnect"
                    @keydown.space.stop="handleReconnect"
                    class="text-[9px] underline text-warning hover:text-warning/80 font-medium cursor-pointer"
                  >
                    Reconnect
                  </span>
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

                <!-- biome-ignore lint/a11y/useSemanticElements: parent is <button>, cannot nest HTML buttons -->
                <span
                  v-if="publishingStore.isPublicationDeletable(pub.status)"
                  role="button"
                  tabindex="0"
                  @click.stop="handleDeletePublication(pub.id)"
                  @keydown.enter.stop="handleDeletePublication(pub.id)"
                  @keydown.space.stop="handleDeletePublication(pub.id)"
                  class="group-hover/card:opacity-100 opacity-0 size-8 flex items-center justify-center rounded-xl border border-border-visible hover:border-error text-text-secondary hover:text-error transition-all bg-bg-primary cursor-pointer"
                  title="Delete publication"
                >
                  <Trash2 class="size-4" />
                </span>
              </div>
            </button>
          </div>
        </div>
    </div>

    <CreatePostModal
      :is-open="isModalOpen"
      :initial-date="selectedCellDate"
      :editing-publication="editingPublication ?? undefined"
      provider="unsplash"
      @close="isModalOpen = false; editingPublication = null"
      @created="onPostCreated"
      @updated="handleUpdated"
    />

    <PostDetailModal
      :is-open="isDetailModalOpen"
      :publication="detailPublication"
      @close="closePostDetail"
      @deleted="() => closePostDetail()"
      @reschedule="onReschedule"
      @retried="onReschedule"
      @edit="handleEditPublication"
    />

    <BulkImportModal :is-open="isBulkModalOpen" @close="isBulkModalOpen = false" @scheduled="handleBulkScheduled" />

    <RecurringScheduleModal
      :is-open="Boolean(editingRecurringSchedule)"
      :publication="recurringPublication"
      :schedule="editingRecurringSchedule"
      @close="editingRecurringSchedule = null; recurringPublication = null"
      @saved="editingRecurringSchedule = null; recurringPublication = null"
    />
  </div>
</template>
