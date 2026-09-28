<script setup lang="ts">
import type { SchedulerView } from '@modules/publishing/application/useCalendarUrl'
const props = defineProps<{ view: SchedulerView }>()
const emit = defineEmits<{ 'change:view': [view: SchedulerView] }>()
const views: Array<{ value: SchedulerView; label: string }> = [
  { value: 'day', label: 'scheduler.viewDay' },
  { value: '3-days', label: 'scheduler.viewThreeDays' },
  { value: 'week', label: 'scheduler.viewWeek' },
  { value: 'agenda', label: 'scheduler.viewAgenda' },
]
</script>
<template>
  <div data-testid="scheduler-view-switcher" class="flex gap-1 overflow-x-auto rounded-lg border border-border-subtle bg-bg-surface p-1">
    <button
      v-for="entry in views"
      :key="entry.value"
      :data-testid="`${entry.value}-view-button`"
      type="button"
      class="min-h-11 shrink-0 rounded-md px-3 font-mono text-xs"
      :class="props.view === entry.value ? 'bg-text-display text-bg-primary' : 'text-text-secondary'"
      :aria-current="props.view === entry.value ? 'page' : undefined"
      @click="emit('change:view', entry.value)"
    >
      {{ $t(entry.label) }}
    </button>
  </div>
</template>
