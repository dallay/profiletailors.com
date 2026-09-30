<script setup lang="ts">
import { CalendarDays, ChevronLeft, ChevronRight, Filter, Plus } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import type { SchedulerView } from '@modules/publishing/application/useCalendarUrl'

withDefaults(
  defineProps<{
    title: string
    view: SchedulerView
    periodLabel: string
    filtersCount: number
    hasNoChannels?: boolean
  }>(),
  {
    hasNoChannels: false,
  },
)
const emit = defineEmits<{
  (event: 'newPost'): void
  (event: 'prev'): void
  (event: 'next'): void
  (event: 'today'): void
  (event: 'openFilters'): void
}>()
</script>
<template>
  <div class="flex flex-col gap-3 border-b border-border-subtle pb-3">
    <div class="flex items-center justify-between gap-2">
      <div class="flex min-w-0 items-center gap-2">
        <CalendarDays class="size-4 shrink-0 text-text-secondary" />
        <h2 class="truncate text-lg font-light tracking-tight text-text-display">{{ title }}</h2>
      </div>
      <Button
        data-testid="mobile-new-post"
        class="min-h-11 shrink-0 gap-1.5 px-4 text-xs"
        :disabled="Boolean(hasNoChannels)"
        :title="hasNoChannels ? $t('scheduler.noChannelTitle') : undefined"
        @click="emit('newPost')"
      >
        <Plus class="size-4" />
        <span>{{ $t('scheduler.newPost') }}</span>
      </Button>
    </div>
    <div class="flex items-center justify-between gap-2">
      <div class="flex items-center gap-1">
        <button
          data-testid="prev-period"
          type="button"
          class="min-h-11 min-w-11 size-11 rounded-lg border border-border-visible bg-bg-primary text-text-secondary"
          :aria-label="$t('scheduler.previousPeriod')"
          @click="emit('prev')"
        >
          <ChevronLeft class="mx-auto size-4" />
        </button>
        <button
          data-testid="next-period"
          type="button"
          class="min-h-11 min-w-11 size-11 rounded-lg border border-border-visible bg-bg-primary text-text-secondary"
          :aria-label="$t('scheduler.nextPeriod')"
          @click="emit('next')"
        >
          <ChevronRight class="mx-auto size-4" />
        </button>
      </div>
      <span class="min-w-0 flex-1 truncate text-center font-mono text-xs font-bold uppercase tracking-widest text-text-display">
        {{ periodLabel }}
      </span>
      <div class="flex items-center gap-1">
        <button
          data-testid="today-period"
          type="button"
          class="min-h-11 rounded-lg border border-border-visible bg-bg-primary px-3 font-mono text-xs text-text-secondary"
          @click="emit('today')"
        >
          {{ $t('scheduler.today') }}
        </button>
        <button
          data-testid="mobile-filters-trigger"
          type="button"
          class="relative min-h-11 min-w-11 size-11 rounded-lg border border-border-visible bg-bg-primary text-text-secondary"
          :aria-label="$t('scheduler.filters')"
          @click="emit('openFilters')"
        >
          <Filter class="mx-auto size-4" />
          <span v-if="filtersCount" class="absolute -right-1 -top-1 flex size-5 items-center justify-center rounded-full bg-text-display font-mono text-[10px] text-bg-primary">
            {{ filtersCount }}
          </span>
        </button>
      </div>
    </div>
  </div>
</template>
