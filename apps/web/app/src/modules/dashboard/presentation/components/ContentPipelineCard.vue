<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import {
  draggable,
  dropTargetForElements,
} from '@atlaskit/pragmatic-drag-and-drop/element/adapter'
import { Button } from '@/components/ui/button'
import type { PipelineCard, Platform } from '@modules/dashboard/domain/dashboard.types'

type CleanupFn = () => void

const props = defineProps<{
  card: PipelineCard
  columnId: string
  isDragging: boolean
  isFirstColumn: boolean
  isLastColumn: boolean
}>()

const emit = defineEmits<{
  (e: 'move-left'): void
  (e: 'move-right'): void
}>()

const platformLabels: Record<Platform, string> = {
  linkedin: 'LinkedIn',
  twitter: 'X',
  bluesky: 'Bluesky',
  threads: 'Threads',
}

const platformBadgeColor: Record<Platform, string> = {
  linkedin: 'text-[#0A66C2]',
  twitter: 'text-[#1DA1F2]',
  bluesky: 'text-[#0085FF]',
  threads: 'text-[#E1306C]',
}

const cardEl = ref<HTMLElement | null>(null)
const cleanups: CleanupFn[] = []

onMounted(() => {
  if (!cardEl.value) return
  cleanups.push(
    draggable({
      element: cardEl.value,
      getInitialData: () => ({ cardId: props.card.id, columnId: props.columnId }),
    }),
    dropTargetForElements({
      element: cardEl.value,
      getData: () => ({ kind: 'card', cardId: props.card.id, columnId: props.columnId }),
    }),
  )
})

onBeforeUnmount(() => {
  for (const cleanup of cleanups) cleanup()
  cleanups.length = 0
})
</script>

<template>
  <div
    ref="cardEl"
    :data-dnd-draggable="card.id"
    draggable="true"
    :class="[
      'rounded-lg bg-[var(--background-primary)] border border-[var(--border-color)] p-3 space-y-2 cursor-grab active:cursor-grabbing transition-opacity',
      isDragging ? 'opacity-50' : '',
    ]"
  >
    <p class="text-sm font-medium text-[var(--text-display)] line-clamp-2 leading-snug">
      {{ card.title }}
    </p>

    <span
      :class="[
        'text-[10px] font-[var(--font-space-mono)] uppercase tracking-wider font-medium block',
        platformBadgeColor[card.platform] ?? 'text-[var(--text-secondary)]',
      ]"
    >
      {{ platformLabels[card.platform] ?? card.platform }}
    </span>

    <div class="flex items-center gap-2 flex-wrap">
      <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)]">
        {{ card.author }}
      </span>
      <span
        v-for="tag in card.tags.slice(0, 3)"
        :key="tag"
        class="text-[10px] text-[var(--text-secondary)] bg-[var(--background-surface)] px-1.5 py-0.5 rounded"
      >
        {{ tag }}
      </span>
    </div>

    <div class="flex items-center gap-1 pt-1">
      <Button
        variant="ghost"
        size="icon-xs"
        :disabled="isFirstColumn"
        class="opacity-60 hover:opacity-100 disabled:opacity-20"
        aria-label="Move left"
        @click="emit('move-left')"
      >
        <svg width="10" height="12" viewBox="0 0 10 12" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
          <path d="M5.5 2.5L2.5 6L5.5 9.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </Button>
      <Button
        variant="ghost"
        size="icon-xs"
        :disabled="isLastColumn"
        class="opacity-60 hover:opacity-100 disabled:opacity-20"
        aria-label="Move right"
        @click="emit('move-right')"
      >
        <svg width="10" height="12" viewBox="0 0 10 12" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
          <path d="M4.5 2.5L7.5 6L4.5 9.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </Button>
    </div>
  </div>
</template>
