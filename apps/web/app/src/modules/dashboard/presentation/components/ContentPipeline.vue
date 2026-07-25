<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  monitorForElements,
  type ElementEventPayloadMap,
} from '@atlaskit/pragmatic-drag-and-drop/element/adapter'
import type { PipelineColumn } from '@modules/dashboard/domain/dashboard.types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import ContentPipelineColumn from './ContentPipelineColumn.vue'

const props = defineProps<{
  columns: PipelineColumn[]
}>()

const emit = defineEmits<{
  moveCard: [cardId: string, fromColumn: string, toColumn: string, toIndex?: number]
}>()

const { t } = useI18n()
const draggedCardId = ref<string | null>(null)

type DropTargetData = {
  cardId?: string
  columnId: string
  kind: 'card' | 'column'
}

function getColumnIndex(columnId: string): number {
  return props.columns.findIndex((c) => c.id === columnId)
}

function isFirstColumn(columnId: string): boolean {
  return getColumnIndex(columnId) === 0
}

function isLastColumn(columnId: string): boolean {
  return getColumnIndex(columnId) === props.columns.length - 1
}

function handleMoveLeft(cardId: string, columnId: string): void {
  const idx = getColumnIndex(columnId)
  if (idx > 0) {
    const target = props.columns[idx - 1]
    if (target) emit('moveCard', cardId, columnId, target.id)
  }
}

function handleMoveRight(cardId: string, columnId: string): void {
  const idx = getColumnIndex(columnId)
  if (idx >= 0 && idx < props.columns.length - 1) {
    const target = props.columns[idx + 1]
    if (target) emit('moveCard', cardId, columnId, target.id)
  }
}

function findCardLocation(cardId: string): { cardIndex: number; columnId: string } | null {
  for (const column of props.columns) {
    const cardIndex = column.cards.findIndex((card) => card.id === cardId)
    if (cardIndex >= 0) return { cardIndex, columnId: column.id }
  }
  return null
}

function getDropTargetData(event: ElementEventPayloadMap['onDrop']): DropTargetData | null {
  for (const target of event.location.current.dropTargets) {
    const { kind, columnId } = target.data
    if ((kind === 'card' || kind === 'column') && typeof columnId === 'string') {
      return {
        kind: kind as 'card' | 'column',
        columnId,
        cardId: typeof target.data.cardId === 'string' ? target.data.cardId : undefined,
      }
    }
  }
  return null
}

function resolveTargetIndex(target: DropTargetData, inputY: number): number | undefined {
  if (target.kind === 'card' && target.cardId) {
    const col = props.columns.find((c) => c.id === target.columnId)
    const tIdx = col?.cards.findIndex((c) => c.id === target.cardId) ?? -1
    if (tIdx < 0) return undefined
    const el = document.querySelector(`[data-dnd-draggable="${target.cardId}"]`)
    if (!el) return tIdx
    const { top, height } = el.getBoundingClientRect()
    return tIdx + (inputY >= top + height / 2 ? 1 : 0)
  }
  return props.columns.find((c) => c.id === target.columnId)?.cards.length
}

function handleDrop(event: ElementEventPayloadMap['onDrop']): void {
  draggedCardId.value = null
  const sourceCardId = event.source.data.cardId
  const sourceColumnId = event.source.data.columnId
  if (typeof sourceCardId !== 'string' || typeof sourceColumnId !== 'string') return

  const sourceLocation = findCardLocation(sourceCardId)
  const target = getDropTargetData(event)
  if (!sourceLocation || !target) return

  const targetIndex = resolveTargetIndex(target, event.location.current.input.clientY)
  if (targetIndex == null) return

  if (sourceLocation.columnId === target.columnId) {
    const normalizedIndex = sourceLocation.cardIndex < targetIndex ? targetIndex - 1 : targetIndex
    if (normalizedIndex !== sourceLocation.cardIndex) {
      emit('moveCard', sourceCardId, sourceLocation.columnId, target.columnId, normalizedIndex)
    }
    return
  }

  emit('moveCard', sourceCardId, sourceLocation.columnId, target.columnId, targetIndex)
}

let monitorCleanup: (() => void) | null = null

onMounted(() => {
  monitorCleanup = monitorForElements({
    onDragStart: ({ source }) => {
      draggedCardId.value = typeof source.data.cardId === 'string' ? source.data.cardId : null
    },
    onDrop: handleDrop,
  })
})

onBeforeUnmount(() => {
  monitorCleanup?.()
  monitorCleanup = null
})
</script>

<template>
  <Card aria-labelledby="section-pipeline">
    <CardHeader>
      <div class="flex items-center justify-between">
        <div>
          <CardTitle
            id="section-pipeline"
            class="font-[var(--font-space-mono)] text-xs font-bold tracking-[0.08em] text-[var(--text-secondary)] uppercase"
          >
            {{ t('dashboard.pipeline.title') }}
          </CardTitle>
          <p class="text-[11px] text-[var(--text-secondary)] mt-1">
            {{ t('dashboard.pipeline.subtitle') }}
          </p>
        </div>
      </div>
    </CardHeader>

    <CardContent>
      <div class="overflow-x-auto -mx-1 px-1">
        <div class="flex lg:grid lg:grid-cols-4 gap-3 min-w-[640px] lg:min-w-0">
          <ContentPipelineColumn
            v-for="column in columns"
            :key="column.id"
            :column="column"
            :is-first-column="isFirstColumn(column.id)"
            :is-last-column="isLastColumn(column.id)"
            :dragged-card-id="draggedCardId"
            @move-card-left="handleMoveLeft($event, column.id)"
            @move-card-right="handleMoveRight($event, column.id)"
          />
        </div>
      </div>
    </CardContent>
  </Card>
</template>
