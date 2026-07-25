<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { dropTargetForElements } from '@atlaskit/pragmatic-drag-and-drop/element/adapter'
import type { PipelineColumn } from '@modules/dashboard/domain/dashboard.types'
import ContentPipelineCard from './ContentPipelineCard.vue'

type CleanupFn = () => void

const props = defineProps<{
  column: PipelineColumn
  isFirstColumn: boolean
  isLastColumn: boolean
  draggedCardId: string | null
}>()

const emit = defineEmits<{
  (e: 'move-card-left', cardId: string): void
  (e: 'move-card-right', cardId: string): void
}>()

const { t } = useI18n()

const dropZoneEl = ref<HTMLElement | null>(null)
let cleanup: CleanupFn | null = null

onMounted(() => {
  if (!dropZoneEl.value) return
  cleanup = dropTargetForElements({
    element: dropZoneEl.value,
    getData: () => ({ kind: 'column', columnId: props.column.id }),
  })
})

onBeforeUnmount(() => {
  cleanup?.()
  cleanup = null
})
</script>

<template>
  <div class="flex-1 lg:flex-none min-w-[150px] lg:min-w-0">
    <!-- Column header -->
    <div class="flex items-center justify-between mb-3">
      <h3 class="text-[11px] font-[var(--font-space-mono)] uppercase tracking-[0.08em] text-[var(--text-secondary)]">
        {{ t(column.title) }}
      </h3>
      <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)] tabular-nums bg-[var(--background-primary)] px-1.5 py-0.5 rounded">
        {{ column.cards.length }}
      </span>
    </div>

    <!-- Drop zone -->
    <div
      ref="dropZoneEl"
      :data-dnd-column="column.id"
      class="space-y-2 min-h-[48px]"
    >
      <ContentPipelineCard
        v-for="card in column.cards"
        :key="card.id"
        :card="card"
        :column-id="column.id"
        :is-dragging="draggedCardId === card.id"
        :is-first-column="isFirstColumn"
        :is-last-column="isLastColumn"
        @move-left="emit('move-card-left', card.id)"
        @move-right="emit('move-card-right', card.id)"
      />

      <p
        v-if="column.cards.length === 0"
        class="text-xs text-[var(--text-secondary)] text-center py-6 border border-dashed border-[var(--border-color)] rounded-lg"
      >
        {{ t('dashboard.pipeline.noCards') }}
      </p>
    </div>
  </div>
</template>
