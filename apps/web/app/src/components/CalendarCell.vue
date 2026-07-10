<script setup lang="ts">
import { computed } from 'vue'
import { Plus } from '@lucide/vue'
import ConflictBadge from '@/components/ConflictBadge.vue'
import SocialProviderIcon from '@/components/SocialProviderIcon.vue'
import type { Publication, ActivityEntry } from '@/stores/publishing'
import { getProviderColor } from '@/lib/provider-styles'

const props = withDefaults(
  defineProps<{
    /** The date this cell represents */
    date: Date
    /** Whether the date belongs to the currently viewed month */
    isCurrentMonth: boolean
    /** Whether the date is today */
    isToday: boolean
    /** Whether this cell is in the past (no interaction allowed) */
    isPast?: boolean
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
    isPast: false,
    maxVisible: 3,
  },
)

const emit = defineEmits<{
  (e: 'click-day', date: Date): void
  (e: 'click-publication', pub: Publication): void
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
    class="relative min-h-[90px] border-r border-border-subtle last:border-r-0 p-1.5 transition-all group/cell"
    :class="{
      'bg-bg-surface/30': !isCurrentMonth,
      'bg-bg-primary/10': isCurrentMonth && !isPast,
      'bg-text-secondary/5 text-text-secondary cursor-not-allowed after:absolute after:inset-0 after:bg-[repeating-linear-gradient(-45deg,transparent,transparent_10px,var(--border-color)_10px,var(--border-color)_11px)] after:opacity-10 after:z-0': isPast,
      'cursor-pointer hover:bg-bg-primary/20': isCurrentMonth && !isPast,
    }"
    :tabindex="isCurrentMonth && !isPast ? 0 : -1"
    :role="isCurrentMonth && !isPast ? 'button' : undefined"
    :aria-disabled="isCurrentMonth && isPast ? true : undefined"
    :aria-label="isCurrentMonth ? `Calendar cell for ${date.toLocaleDateString()}${isPast ? ' (past)' : ''}` : undefined"
    @click="isCurrentMonth && !isPast ? emit('click-day', date) : undefined"
    @keydown="onKeyDown"
    @dragover.prevent="!isPast"
    @drop.prevent="!isPast ? onDrop($event) : undefined"
  >
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

      <div
        v-if="activityEntry && isCurrentMonth && activityDotColor"
        class="size-2 rounded-full shrink-0"
        :class="activityDotColor"
      />
    </div>

    <div class="space-y-0.5">
      <div
        v-for="pub in visiblePublications"
        :key="pub.id"
        class="relative z-10 overflow-hidden rounded-md"
      >
        <button
          type="button"
          class="flex flex-row items-center gap-1 rounded-md px-1 py-0.5 text-[7px] font-mono cursor-pointer w-full text-left"
          :class="getProviderColor(pub.channels[0] || 'linkedin')"
          :draggable="draggable"
          @click.stop="emit('click-publication', pub)"
          @keydown.enter.stop.prevent="emit('click-publication', pub)"
          @keydown.space.stop.prevent="emit('click-publication', pub)"
          @dragstart="onDragStart($event, pub)"
          @dragend="onDragEnd"
        >
          <span class="size-2.5 shrink-0">
            <SocialProviderIcon :provider="pub.channels[0] || 'linkedin'" />
          </span>
          <span class="min-w-0 flex-1 truncate">{{ pub.title || pub.content.substring(0, 20) }}</span>
          <img
            v-if="pub.thumbnail"
            :src="pub.thumbnail"
            alt=""
            class="h-5 w-5 shrink-0 rounded-sm object-cover opacity-85"
            :draggable="false"
          >
          <ConflictBadge
            v-if="pub.hasConflict"
            variant="dot"
          />
        </button>
      </div>
      <div
        v-if="remainingCount > 0"
        class="text-[7px] font-mono text-text-secondary pl-1"
      >
        +{{ remainingCount }} more
      </div>
    </div>

    <button
      v-if="isCurrentMonth && !isPast"
      @click.stop="emit('click-day', date)"
      class="hidden group-hover/cell:flex items-center justify-center size-5 mt-auto ml-auto rounded border border-dashed border-text-secondary/30 text-text-secondary/50 hover:border-text-display/40 hover:text-text-display/60 hover:bg-bg-primary/30 transition-all cursor-pointer"
      aria-label="Add post"
    >
      <Plus class="size-2.5" />
    </button>
  </div>
</template>
