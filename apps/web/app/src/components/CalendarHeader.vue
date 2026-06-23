<script setup lang="ts">
import {
  Bookmark,
  CalendarDays,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Clock,
  Filter,
  Globe,
  Plus,
} from '@lucide/vue'
import { usePublishingStore, type Channel } from '@/stores/publishing'
import { Button } from '@/components/ui/button'

const publishingStore = usePublishingStore()

defineProps<{
  /** Current calendar sub-view */
  calendarView: 'month' | 'week' | 'day'
  /** Formatted period label (e.g. "June 2026", "Jun 8 – 14, 2026") */
  periodLabel: string
}>()

const emit = defineEmits<{
  (e: 'update:calendarView', view: 'month' | 'week' | 'day'): void
  (e: 'forward'): void
  (e: 'backward'): void
  (e: 'today'): void
  (e: 'newPost'): void
}>()
</script>

<template>
  <!-- Top Filter Controls Bar -->
  <div class="flex flex-col lg:flex-row gap-4 items-start lg:items-center justify-between border-b border-border-subtle pb-4">
    <!-- Title/Breadcrumb -->
    <div class="flex items-center gap-2">
      <CalendarDays class="size-4.5 text-text-secondary" />
      <h2 class="text-xl font-light tracking-tight text-text-display">
        {{ $t('scheduler.allChannels') || 'All Channels' }}
      </h2>
    </div>

    <!-- Filters & Action buttons -->
    <div class="flex flex-wrap items-center gap-3 w-full lg:w-auto">
      <!-- Calendar View toggle (month/week/day) — only in calendar mode -->
      <div
        v-if="publishingStore.viewMode === 'calendar'"
        class="flex items-center rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[9px] tracking-wider uppercase font-bold"
      >
        <button
          class="cursor-pointer rounded-full px-2.5 py-1 transition-all"
          :class="calendarView === 'month' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
          @click="emit('update:calendarView', 'month')"
        >
          {{ $t('scheduler.calendar') || 'Month' }}
        </button>
        <button
          class="cursor-pointer rounded-full px-2.5 py-1 transition-all"
          :class="calendarView === 'week' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
          @click="emit('update:calendarView', 'week')"
        >
          {{ $t('scheduler.weekView') || 'Week' }}
        </button>
        <button
          class="cursor-pointer rounded-full px-2.5 py-1 transition-all"
          :class="calendarView === 'day' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
          @click="emit('update:calendarView', 'day')"
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
        <label for="calendar-timezone-select" class="sr-only">Timezone</label>
        <select
          id="calendar-timezone-select"
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
        <label for="calendar-platform-select" class="sr-only">Platform</label>
        <select
          id="calendar-platform-select"
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
        <label for="calendar-post-status-select" class="sr-only">Post status</label>
        <select
          id="calendar-post-status-select"
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

  <!-- Calendar Subheader: Navigation -->
  <div class="flex items-center justify-between bg-bg-surface border border-border-subtle p-3 rounded-xl">
    <!-- Navigation arrows -->
    <div class="flex items-center gap-1">
      <button
        @click="emit('backward')"
        class="size-8 flex items-center justify-center rounded-lg border border-border-visible hover:border-text-secondary hover:text-text-display bg-bg-primary transition-colors cursor-pointer text-text-secondary"
      >
        <ChevronLeft class="size-4" />
      </button>
      <button
        @click="emit('forward')"
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
        @click="emit('today')"
        class="border border-border-visible hover:border-text-secondary bg-bg-primary text-text-secondary hover:text-text-display font-mono text-[9px] uppercase tracking-wider font-bold rounded-lg px-3 py-1.5 transition-colors cursor-pointer"
      >
        {{ $t('scheduler.today') || 'Today' }}
      </button>
      <span class="font-mono text-[9px] uppercase tracking-wider text-text-secondary bg-bg-primary border border-border-visible px-2.5 py-1 rounded-lg">
        {{ calendarView === 'month' ? 'Month' : calendarView === 'week' ? 'Week' : 'Day' }}
      </span>
    </div>
  </div>
</template>
