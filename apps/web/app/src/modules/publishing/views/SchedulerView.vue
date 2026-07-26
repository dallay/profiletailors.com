<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePublishingStore, type Publication, type ActivityEntry } from '@modules/publishing/infrastructure/publishing.store'
import { useCalendarUrl, useCalendarGrid, usePublicationFilters, useDragAndDrop, useSchedulerWeekTimeline } from '@modules/publishing/application'
import CreatePostModal from '@modules/publishing/presentation/components/CreatePostModal.vue'
import PostDetailModal from '@modules/publishing/presentation/components/PostDetailModal.vue'
import CalendarHeader from '@modules/publishing/presentation/components/CalendarHeader.vue'
import SchedulerReconnectBanner from '@modules/publishing/presentation/components/scheduler/SchedulerReconnectBanner.vue'
import SchedulerMonthGrid from '@modules/publishing/presentation/components/scheduler/SchedulerMonthGrid.vue'
import SchedulerWeekTimeline from '@modules/publishing/presentation/components/scheduler/SchedulerWeekTimeline.vue'
import SchedulerListView from '@modules/publishing/presentation/components/scheduler/SchedulerListView.vue'
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

// Calendar Grid Management
const { monthGrid, weekDays } = useCalendarGrid(currentBaseDate)
const { slotKey } = useSchedulerWeekTimeline()


const isModalOpen = ref(false)
const selectedCellDate = ref<string | undefined>(undefined)
const editingPublication = ref<Publication | null>(null)

// Drag and Drop Management
const { dragData, onDragStart, onDragEnd, extractDroppedId, resolveDateFromDrop } = useDragAndDrop()

async function handleReconnect() {
  try {
    await publishingStore.connectLinkedInPersonalProfile()
  } catch (err: unknown) {
    console.error('LinkedIn reconnect failed', err)
  }
}

async function onDropCell(e: DragEvent, targetDate: Date, targetHour?: number) {
  e.preventDefault()
  const pubId = extractDroppedId(e)
  if (!pubId) return

  const newDate = resolveDateFromDrop(targetDate, targetHour, true)

  // Guard: reject drops to past time slots
  const earliestAllowed = new Date(Date.now() + 5 * 60_000)
  if (newDate < earliestAllowed) {
    console.warn('Cannot reschedule to a past time slot.')
    dragData.value = null
    return
  }

  const newDateIso = newDate.toISOString()

  try {
    await publishingStore.reschedulePublication(pubId, newDateIso)
  } catch {
    console.warn('Reschedule failed, rolled back')
  }
  dragData.value = null
}


/** Build a 6×7 grid (weeks × days) for the current month. */
// (Moved to useCalendarGrid composable)

const _isCurrentMonth = (d: Date) =>
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


const filteredPublications = computed(() => {
  const activeChannelId = url.state.value.channelIds[0]
  const { filterMatches } = usePublicationFilters()
  return publishingStore.publications.filter((pub) =>
    filterMatches(pub, {
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

function _publicationDragStart(e: DragEvent, pub: Publication) {
  onDragStart(e, pub.id, pub.scheduledAt)
}

function _publicationDragEnd(e: DragEvent) {
  onDragEnd(e)
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

  await publishingStore.fetchCalendar(from.toISOString(), to.toISOString(), {
    status: state.status === 'all' ? undefined : state.status,
    socialAccountId: state.channelIds[0],
    timezone: state.timezone,
  })
}

function onPostCreated() {
  isModalOpen.value = false
  toast.success(t('composer.scheduleSuccessToast'))
}

function onReschedule() {
  // Store already updated by PostDetailModal; just close
  closePostDetail().catch(() => undefined)
}

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

    await publishingStore.fetchCalendar(from.toISOString(), to.toISOString(), {
      status: state.status === 'all' ? undefined : state.status,
      socialAccountId: state.channelIds[0],
      timezone: state.timezone,
    })

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

    <SchedulerReconnectBanner
      v-if="publishingStore.hasReconnectRequiredChannels"
      @reconnect="handleReconnect"
    />

    <div data-testid="scheduler-workspace" class="flex min-w-0 min-h-0 flex-1 flex-col overflow-hidden">
      <div v-if="url.state.value.surface !== 'list'" data-testid="calendar-mode" class="flex min-h-0 flex-1 flex-col gap-4">
        <SchedulerMonthGrid
          v-if="calendarView === 'month'"
          :month-grid="monthGrid"
          :current-base-date="currentBaseDate"
          :publications="filteredPublications"
          :activity-by-date="activityByDate"
          @click-day="openDayView"
          @click-publication="openPostDetail"
           @dragstart="_publicationDragStart($event.event, $event.pub)"
           @dragend="_publicationDragEnd($event)"
          @drop-cell="onDropCell($event.event, $event.date)"
        />
        <SchedulerWeekTimeline
          v-if="calendarView === 'week'"
          :week-days="weekDays"
          :hour-slots="hourSlots"
          :publications-by-slot="publicationsBySlot"
          @click-publication="openPostDetail"
          @add-post="openNewPostForSlot($event.date, $event.hour)"
          @delete-publication="handleDeletePublication"
           @dragstart="_publicationDragStart($event.event, $event.pub)"
           @dragend="_publicationDragEnd($event)"
          @drop-cell="onDropCell($event.event, $event.date, $event.hour)"
        />
      </div>

      <SchedulerListView
        v-else
        :publications="filteredPublications"
        @click-publication="openPostDetail"
        @delete-publication="handleDeletePublication"
        @reconnect="handleReconnect"
      />
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
  </div>
</template>
