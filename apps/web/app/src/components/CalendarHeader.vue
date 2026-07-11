<script setup lang="ts">
import { computed } from 'vue'
import {
  Ban,
  CalendarDays,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Clock,
  Folder,
  Globe,
  Plus,
  Radio,
} from '@lucide/vue'
import { usePublishingStore } from '@/stores/publishing'
import type { SchedulerStatus, SchedulerSurface } from '@/composables/useCalendarUrl'
import { Button } from '@/components/ui/button'
import SocialProviderIcon from '@/components/SocialProviderIcon.vue'

const publishingStore = usePublishingStore()

const props = defineProps<{
  /** Current calendar sub-view */
  calendarView: 'month' | 'week' | 'day'
  /** Full scheduler surface */
  surface: SchedulerSurface
  /** Formatted period label (e.g. "June 2026", "Jun 8 – 14, 2026") */
  periodLabel: string
  /** Active timezone (from URL) */
  timezone: string
  /** Active status filter (from URL) */
  status: string
  /** Active channel IDs filter (from URL) */
  channelIds: string[]
}>()

const emit = defineEmits<{
  (e: 'change:view', surface: SchedulerSurface): void
  (e: 'change:date', action: 'forward' | 'backward' | 'today'): void
  (e: 'change:filter', filter: {
    status?: SchedulerStatus
    timezone?: string
    channelIds?: string[]
  }): void
  (e: 'newPost'): void
}>()

/** Derives the calendar surface from the current calendarView prop for the calendar toggle. */
const calendarSurface = computed<SchedulerSurface>(() =>
  props.calendarView === 'month' ? 'calendar-month' : 'calendar-week',
)

/** Computes the currently selected channel to render its custom network icon if filtered. */
const selectedChannel = computed(() => {
  const id = props.channelIds?.[0]
  if (!id) return null
  return publishingStore.channels.find((ch) => ch.accountId === id)
})

/** Computes the status icon to dynamically show based on selected status. */
const statusIcon = computed(() => {
  switch (props.status) {
    case 'queued':
      return Clock
    case 'published':
      return Check
    case 'cancelled':
      return Ban
    default:
      return Folder
  }
})
</script>

<template>
  <div class="flex flex-col lg:flex-row gap-4 items-start lg:items-center justify-between border-b border-border-subtle pb-4">
    <div class="flex items-center gap-2">
      <CalendarDays class="size-4.5 text-text-secondary" />
      <h2 class="text-xl font-light tracking-tight text-text-display">
        {{ $t('scheduler.allChannels') || 'All Channels' }}
      </h2>
    </div>

    <div class="flex flex-wrap items-center gap-3 w-full lg:w-auto">
      <div
        v-if="surface !== 'list'"
        class="flex items-center rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[9px] tracking-wider uppercase font-bold"
      >
        <button
          class="cursor-pointer rounded-full px-2.5 py-1 transition-all"
          :class="calendarView === 'month' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
          @click="emit('change:view', 'calendar-month')"
        >
          {{ $t('scheduler.calendar') || 'Month' }}
        </button>
        <button
          class="cursor-pointer rounded-full px-2.5 py-1 transition-all"
          :class="calendarView === 'week' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
          @click="emit('change:view', 'calendar-week')"
        >
          {{ $t('scheduler.weekView') || 'Week' }}
        </button>
      </div>

      <div class="flex items-center rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[9px] tracking-wider uppercase font-bold">
        <button
          class="cursor-pointer rounded-full px-3 py-1 transition-all"
          :class="surface !== 'list' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
          @click="emit('change:view', calendarSurface)"
        >
          {{ $t('scheduler.calendar') || 'Calendar' }}
        </button>
        <button
          class="cursor-pointer rounded-full px-3 py-1 transition-all"
          :class="surface === 'list' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
          @click="emit('change:view', 'list')"
        >
          {{ $t('scheduler.list') || 'List' }}
        </button>
      </div>

      <div class="relative shrink-0">
        <label for="calendar-timezone-select" class="sr-only">Timezone</label>
        <select
          id="calendar-timezone-select"
          class="bg-bg-surface border border-border-subtle rounded-full pl-8 pr-8 py-1.5 text-[10px] font-mono font-bold text-text-secondary appearance-none cursor-pointer focus:outline-none focus:border-text-display"
          :value="timezone"
          @change="(e) => emit('change:filter', { timezone: (e.target as HTMLSelectElement).value })"
        >
          <option
            v-if="timezone && !['Europe/Madrid', 'UTC', 'America/New_York'].includes(timezone)"
            :value="timezone"
          >
            {{ timezone }}
          </option>
          <option value="Europe/Madrid">Europe/Madrid</option>
          <option value="UTC">UTC</option>
          <option value="America/New_York">America/New_York</option>
        </select>
        <Globe class="absolute left-3 top-1/2 -translate-y-1/2 size-3 text-text-secondary pointer-events-none" />
        <ChevronDown class="absolute right-3 top-1/2 -translate-y-1/2 size-3 text-text-secondary pointer-events-none" />
      </div>

      <div class="relative shrink-0">
        <label for="calendar-platform-select" class="sr-only">Platform</label>
        <select
          id="calendar-platform-select"
          class="bg-bg-surface border border-border-subtle rounded-full pl-8 pr-8 py-1.5 text-[10px] font-mono font-bold text-text-secondary appearance-none cursor-pointer focus:outline-none focus:border-text-display"
          :value="props.channelIds?.[0] ?? ''"
          @change="(e) => {
            const val = (e.target as HTMLSelectElement).value
            emit('change:filter', { channelIds: val ? [val] : [] })
          }"
        >
          <option value="">{{ $t('scheduler.channelsLabel') || 'Platform' }}</option>
          <option
            v-for="ch in publishingStore.channels"
            :key="ch.accountId"
            :value="ch.accountId"
          >
            {{ ch.provider }} ({{ ch.handle }})
          </option>
        </select>
        <div class="absolute left-3 top-1/2 -translate-y-1/2 size-3 text-text-secondary pointer-events-none flex items-center justify-center">
          <SocialProviderIcon v-if="selectedChannel" :provider="selectedChannel.provider" />
          <Radio v-else class="size-3" />
        </div>
        <ChevronDown class="absolute right-3 top-1/2 -translate-y-1/2 size-3 text-text-secondary pointer-events-none" />
      </div>

      <div class="relative shrink-0">
        <label for="calendar-post-status-select" class="sr-only">Post status</label>
        <select
          id="calendar-post-status-select"
          class="bg-bg-surface border border-border-subtle rounded-full pl-8 pr-8 py-1.5 text-[10px] font-mono font-bold text-text-secondary appearance-none cursor-pointer focus:outline-none focus:border-text-display"
          :value="props.status"
          @change="(e) => emit('change:filter', { status: (e.target as HTMLSelectElement).value as SchedulerStatus })"
        >
          <option value="all">{{ $t('scheduler.allPosts') || 'All Posts' }}</option>
          <option value="queued">Queued</option>
          <option value="published">Published</option>
          <option value="cancelled">Cancelled</option>
        </select>
        <component :is="statusIcon" class="absolute left-3 top-1/2 -translate-y-1/2 size-3 text-text-secondary pointer-events-none" />
        <ChevronDown class="absolute right-3 top-1/2 -translate-y-1/2 size-3 text-text-secondary pointer-events-none" />
      </div>

      <Button
        @click="emit('newPost')"
        :disabled="publishingStore.hasNoChannels"
        :title="publishingStore.hasNoChannels ? $t('scheduler.noChannelTitle') as string : undefined"
        class="gap-1.5 h-8.5 text-[10px] uppercase font-mono tracking-wider disabled:cursor-not-allowed disabled:opacity-50"
      >
        <Plus class="size-3.5" />
        <span>{{ $t('scheduler.newPost') || 'New Post' }}</span>
      </Button>
    </div>
  </div>

  <div class="flex items-center justify-between bg-bg-surface border border-border-subtle p-3 rounded-xl">
    <div class="flex items-center gap-1">
      <button
        @click="emit('change:date', 'backward')"
        class="size-8 flex items-center justify-center rounded-lg border border-border-visible hover:border-text-secondary hover:text-text-display bg-bg-primary transition-colors cursor-pointer text-text-secondary"
      >
        <ChevronLeft class="size-4" />
      </button>
      <button
        @click="emit('change:date', 'forward')"
        class="size-8 flex items-center justify-center rounded-lg border border-border-visible hover:border-text-secondary hover:text-text-display bg-bg-primary transition-colors cursor-pointer text-text-secondary"
      >
        <ChevronRight class="size-4" />
      </button>
    </div>

    <span class="font-mono text-[10px] font-bold uppercase tracking-widest text-text-display">
      {{ periodLabel }}
    </span>

    <div class="flex items-center gap-2">
      <button
        @click="emit('change:date', 'today')"
        class="border border-border-visible hover:border-text-secondary bg-bg-primary text-text-secondary hover:text-text-display font-mono text-[9px] uppercase tracking-wider font-bold rounded-lg px-3 py-1.5 transition-colors cursor-pointer"
      >
        {{ $t('scheduler.today') || 'Today' }}
      </button>
      <span class="font-mono text-[9px] uppercase tracking-wider text-text-secondary bg-bg-primary border border-border-visible px-2.5 py-1 rounded-lg">
        {{ calendarView === 'month' ? 'Month' : 'Week' }}
      </span>
    </div>
  </div>
</template>