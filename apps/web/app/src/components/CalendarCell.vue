<script setup lang="ts">
import { computed } from 'vue'
import ConflictBadge from '@/components/ConflictBadge.vue'
import type { Publication, ActivityEntry } from '@/stores/publishing'
import { getProviderColor, getProviderBadge } from '@/lib/provider-styles'

const props = withDefaults(
  defineProps<{
    /** The date this cell represents */
    date: Date
    /** Whether the date belongs to the currently viewed month */
    isCurrentMonth: boolean
    /** Whether the date is today */
    isToday: boolean
    /** Whether this cell is in the past (no interaction allowed) */
    isPast: boolean
    /** Publications scheduled for this date */
    publications: Publication[]
    /** Activity density entry for this date (optional) */
    activityEntry?: ActivityEntry | null
    /** Whether dragging is allowed from this cell */
    draggable?: boolean
    /** Max publications to show before "+N more" truncation */
    maxVisible?: number
  }>(),
  {
    activityEntry: null,
    draggable: false,
    maxVisible: 3,
  },
)

const emit = defineEmits<{
  (e: 'click-day', date: Date): void
  (e: 'dragstart', payload: { event: DragEvent; pub: Publication }): void
  (e: 'dragend', event: DragEvent): void
  (e: 'drop-cell', payload: { event: DragEvent; date: Date }): void
}>()

const visiblePublications = computed(() => props.publications.slice(0, props.maxVisible))
const remainingCount = computed(() => Math.max(0, props.publications.length - props.maxVisible))

const activityDotColor = computed(() => {
  if (!props.activityEntry) return ''
  switch (props.activityEntry.density) {
    case 'LIGHT':
      return 'bg-yellow-400'
    case 'MEDIUM':
      return 'bg-orange-400'
    case 'HIGH':
      return 'bg-green-500'
    default:
      return ''
  }
})

function onDragStart(e: DragEvent, pub: Publication) {
  emit('dragstart', { event: e, pub })
}

function onDragEnd(e: DragEvent) {
  emit('dragend', e)
}

function onDrop(e: DragEvent) {
  emit('drop-cell', { event: e, date: props.date })
}

function onKeyDown(e: KeyboardEvent) {
  if (props.isCurrentMonth && !props.isPast && (e.key === 'Enter' || e.key === ' ')) {
    e.preventDefault()
    emit('click-day', props.date)
  }
}
</script>

<template>
  <div
    class="relative min-h-[90px] border-r border-border-subtle last:border-r-0 p-1.5 transition-all"
    :class="{
      'bg-bg-surface/30': !isCurrentMonth,
      'bg-bg-primary/10': isCurrentMonth && !isPast,
      'opacity-40 cursor-not-allowed pointer-events-none': isPast,
      'cursor-pointer hover:bg-bg-primary/20': isCurrentMonth && !isPast,
    }"
    :tabindex="isCurrentMonth && !isPast ? 0 : -1"
    :role="isCurrentMonth && !isPast ? 'button' : undefined"
    :aria-label="isCurrentMonth ? `Calendar cell for ${date.toLocaleDateString()}${isPast ? ' (past)' : ''}` : undefined"
    @click="isCurrentMonth && !isPast ? emit('click-day', date) : undefined"
    @keydown="onKeyDown"
    @dragover.prevent="!isPast"
    @drop.prevent="!isPast ? onDrop($event) : undefined"
  >
    <!-- Day number + activity dot -->
    <div class="flex items-center justify-between mb-1">
      <span
        class="font-mono text-[10px] font-bold leading-none size-5 flex items-center justify-center rounded-full"
        :class="{
          'bg-text-display text-bg-primary': isToday,
          'text-text-display': !isToday && isCurrentMonth,
          'text-text-secondary/40': !isCurrentMonth,
        }"
      >
        {{ date.getDate() }}
      </span>

      <!-- Activity dot -->
      <div
        v-if="activityEntry && isCurrentMonth && activityDotColor"
        class="size-2 rounded-full shrink-0"
        :class="activityDotColor"
      />
    </div>

    <!-- Publication snippets -->
    <div class="space-y-0.5">
      <div
        v-for="pub in visiblePublications"
        :key="pub.id"
        class="flex items-center gap-1 rounded-md px-1 py-0.5 text-[7px] font-mono truncate"
        :class="getProviderColor(pub.channels[0] || 'linkedin')"
        :draggable="draggable"
        @dragstart="onDragStart($event, pub)"
        @dragend="onDragEnd"
      >
        <span class="shrink-0">{{ getProviderBadge(pub.channels[0] || 'linkedin') }}</span>
        <span class="truncate">{{ pub.title || pub.content.substring(0, 20) }}</span>
        <ConflictBadge
          v-if="pub.hasConflict"
          variant="dot"
        />
      </div>
      <div
        v-if="remainingCount > 0"
        class="text-[7px] font-mono text-text-secondary pl-1"
      >
        +{{ remainingCount }} more
      </div>
    </div>
  </div>
</template>
