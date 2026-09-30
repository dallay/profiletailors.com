<script setup lang="ts">
import { computed, ref } from 'vue'
import MobileSchedulerHeader from './MobileSchedulerHeader.vue'
import SchedulerViewSwitcher from './SchedulerViewSwitcher.vue'
import SchedulerFiltersSheet from './SchedulerFiltersSheet.vue'
import MobileOverflowMenu from './MobileOverflowMenu.vue'
import SchedulerTimelineBody from './SchedulerTimelineBody.vue'
import type { Publication } from '@modules/publishing/infrastructure/publishing.store'
import type { SchedulerStatus, SchedulerView } from '@modules/publishing/application/useCalendarUrl'

type HourSlot = { hour: number; label: string }
const props = withDefaults(
  defineProps<{
    title: string
    view: SchedulerView
    periodLabel: string
    days: Date[]
    hourSlots: HourSlot[]
    status: SchedulerStatus
    timezone: string
    channelIds: string[]
    q: string
    publicationsForSlot: (day: Date, hour: number) => Publication[]
    isToday: (day: Date) => boolean
    formatDayName: (day: Date) => string
    isPastSlot: (day: Date, hour: number) => boolean
    hasNoChannels: boolean
    now: Date
    formatCurrentTime: (date: Date) => string
  }>(),
  {},
)
const emit = defineEmits<{
  (event: 'newPost'): void
  (event: 'connectChannels'): void
  (event: 'prev'): void
  (event: 'next'): void
  (event: 'today'): void
  (event: 'change:view', view: SchedulerView): void
  (event: 'change:filter', filter: { status?: SchedulerStatus; timezone?: string; channelIds?: string[]; q?: string }): void
  (event: 'openBulkImport'): void
  (event: 'startTour'): void
  (event: 'openPostDetail', publication: Publication): void
  (event: 'openNewPost', day: Date, hour: number): void
}>()
const filtersOpen = ref(false)
function resolveBrowserTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}
const filtersCount = computed(
  () =>
    Number(props.status !== 'all') +
    Number(props.channelIds.length > 0) +
    Number(props.timezone !== resolveBrowserTimezone()),
)
function handleOpenNewPost(day: Date, hour: number) {
  emit('openNewPost', day, hour)
}
</script>
<template>
  <div data-testid="scheduler-mobile-shell" class="flex min-h-0 flex-1 flex-col gap-3">
    <MobileSchedulerHeader
      :title="title"
      :view="view"
      :period-label="periodLabel"
      :filters-count="filtersCount"
      :has-no-channels="hasNoChannels"
      @new-post="emit('newPost')"
      @connect-channels="emit('connectChannels')"
      @prev="emit('prev')"
      @next="emit('next')"
      @today="emit('today')"
      @open-filters="filtersOpen = true"
    />
    <div class="flex items-center gap-2">
      <SchedulerViewSwitcher :view="view" @change:view="emit('change:view', $event)" />
      <MobileOverflowMenu class="ml-auto" @open-bulk-import="emit('openBulkImport')" @start-tour="emit('startTour')" />
    </div>
    <SchedulerFiltersSheet :open="filtersOpen" :status="status" :timezone="timezone" :channel-ids="channelIds" :q="q" :filters-count="filtersCount" @update:open="filtersOpen = $event" @change:filter="emit('change:filter', $event)" />
    <div v-if="view === 'agenda'" data-testid="scheduler-mobile-agenda" class="min-h-0 flex-1 overflow-y-auto">
      <slot name="agendaSlot" />
    </div>
    <SchedulerTimelineBody v-else :days="days" :hour-slots="hourSlots" :publications-for-slot="publicationsForSlot" :is-today="isToday" :format-day-name="formatDayName" :is-past-slot="isPastSlot" :has-no-channels="hasNoChannels" :now="now" :format-current-time="formatCurrentTime" @open-post-detail="emit('openPostDetail', $event)" @open-new-post="handleOpenNewPost" />
  </div>
</template>
