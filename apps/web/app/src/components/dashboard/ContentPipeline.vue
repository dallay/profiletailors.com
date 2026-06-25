<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  draggable,
  dropTargetForElements,
  monitorForElements,
  type ElementEventPayloadMap,
} from '@atlaskit/pragmatic-drag-and-drop/element/adapter'
import type { PipelineColumn, Platform } from '@/lib/types/dashboard'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

const props = defineProps<{
  columns: PipelineColumn[]
}>()

const emit = defineEmits<{
  moveCard: [cardId: string, fromColumn: string, toColumn: string, toIndex?: number]
}>()

const { t } = useI18n()

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
    if (target) {
      emit('moveCard', cardId, columnId, target.id)
    }
  }
}

function handleMoveRight(cardId: string, columnId: string): void {
  const idx = getColumnIndex(columnId)
  if (idx >= 0 && idx < props.columns.length - 1) {
    const target = props.columns[idx + 1]
    if (target) {
      emit('moveCard', cardId, columnId, target.id)
    }
  }
}

type DragData = {
  cardId: string
  columnId: string
}

type DropTargetData = {
  cardId?: string
  columnId: string
  kind: 'card' | 'column'
}

type CleanupFn = ReturnType<typeof monitorForElements>

const columnElements = new Map<string, HTMLElement>()
const cardElements = new Map<string, HTMLElement>()
const cleanupFns = ref<CleanupFn[]>([])
const draggedCardId = ref<string | null>(null)

function setColumnRef(columnId: string, el: Element | null): void {
  if (el instanceof HTMLElement) {
    columnElements.set(columnId, el)
    return
  }

  columnElements.delete(columnId)
}

function setCardRef(cardId: string, el: Element | null): void {
  if (el instanceof HTMLElement) {
    cardElements.set(cardId, el)
    return
  }

  cardElements.delete(cardId)
}

function cleanupDragAndDrop(): void {
  for (const cleanup of cleanupFns.value) {
    cleanup()
  }
  cleanupFns.value = []
}

function findCardLocation(cardId: string): { cardIndex: number; columnId: string } | null {
  for (const column of props.columns) {
    const cardIndex = column.cards.findIndex((card) => card.id === cardId)
    if (cardIndex >= 0) {
      return { cardIndex, columnId: column.id }
    }
  }

  return null
}

function findColumn(columnId: string): PipelineColumn | undefined {
  return props.columns.find((column) => column.id === columnId)
}

function getCardDropIndex(target: DropTargetData, inputY: number): number | undefined {
  if (!target.cardId) {
    return undefined
  }

  const targetColumn = findColumn(target.columnId)
  const targetIndex = targetColumn?.cards.findIndex((card) => card.id === target.cardId) ?? -1
  if (targetIndex < 0) {
    return undefined
  }

  const targetElement = cardElements.get(target.cardId)
  if (!targetElement) {
    return targetIndex
  }

  const { top, height } = targetElement.getBoundingClientRect()
  const insertAfter = inputY >= top + (height / 2)
  return targetIndex + (insertAfter ? 1 : 0)
}

function getDropTargetData(
  event: ElementEventPayloadMap['onDrop'],
): DropTargetData | null {
  for (const target of event.location.current.dropTargets) {
    const kind = target.data.kind
    const columnId = target.data.columnId

    if (
      (kind === 'card' || kind === 'column')
      && typeof columnId === 'string'
    ) {
      return {
        kind,
        columnId,
        cardId: typeof target.data.cardId === 'string' ? target.data.cardId : undefined,
      }
    }
  }

  return null
}

function handleDrop(event: ElementEventPayloadMap['onDrop']): void {
  draggedCardId.value = null

  const sourceCardId = event.source.data.cardId
  const sourceColumnId = event.source.data.columnId
  if (typeof sourceCardId !== 'string' || typeof sourceColumnId !== 'string') {
    return
  }

  const sourceLocation = findCardLocation(sourceCardId)
  if (!sourceLocation) {
    return
  }

  const target = getDropTargetData(event)
  if (!target) {
    return
  }

  let targetIndex: number | undefined
  if (target.kind === 'card') {
    targetIndex = getCardDropIndex(target, event.location.current.input.clientY)
  } else {
    targetIndex = findColumn(target.columnId)?.cards.length
  }

  if (targetIndex == null) {
    return
  }

  if (sourceLocation.columnId === target.columnId) {
    let normalizedIndex = targetIndex
    if (sourceLocation.cardIndex < targetIndex) {
      normalizedIndex -= 1
    }

    if (normalizedIndex === sourceLocation.cardIndex) {
      return
    }

    emit('moveCard', sourceCardId, sourceLocation.columnId, target.columnId, normalizedIndex)
    return
  }

  emit('moveCard', sourceCardId, sourceLocation.columnId, target.columnId, targetIndex)
}

function registerDragAndDrop(): void {
  cleanupDragAndDrop()

  const fns: CleanupFn[] = []

  fns.push(
    monitorForElements({
      onDragStart: ({ source }) => {
        draggedCardId.value = typeof source.data.cardId === 'string' ? source.data.cardId : null
      },
      onDrop: handleDrop,
    }),
  )

  for (const column of props.columns) {
    const columnElement = columnElements.get(column.id)
    if (columnElement) {
      fns.push(
        dropTargetForElements({
          element: columnElement,
          getData: () => ({
            kind: 'column',
            columnId: column.id,
          }),
        }),
      )
    }

    for (const card of column.cards) {
      const cardElement = cardElements.get(card.id)
      if (!cardElement) {
        continue
      }

      fns.push(
        draggable({
          element: cardElement,
          getInitialData: (): DragData => ({
            cardId: card.id,
            columnId: column.id,
          }),
        }),
        dropTargetForElements({
          element: cardElement,
          getData: (): DropTargetData => ({
            kind: 'card',
            cardId: card.id,
            columnId: column.id,
          }),
        }),
      )
    }
  }

  cleanupFns.value = fns
}

watch(
  () => props.columns.map((column) => `${column.id}:${column.cards.map((card) => card.id).join(',')}`).join('|'),
  async () => {
    await nextTick()
    registerDragAndDrop()
  },
  { immediate: true },
)

onMounted(async () => {
  await nextTick()
  registerDragAndDrop()
})

onBeforeUnmount(() => {
  cleanupDragAndDrop()
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
      <!-- Kanban columns: horizontal scroll on mobile, grid on larger -->
      <div class="overflow-x-auto -mx-1 px-1">
        <div class="flex lg:grid lg:grid-cols-4 gap-3 min-w-[640px] lg:min-w-0">
          <div
            v-for="column in columns"
            :key="column.id"
            class="flex-1 lg:flex-none min-w-[150px] lg:min-w-0"
          >
            <!-- Column header -->
            <div class="flex items-center justify-between mb-3">
              <h3
                class="text-[11px] font-[var(--font-space-mono)] uppercase tracking-[0.08em] text-[var(--text-secondary)]"
              >
                {{ t(column.title) }}
              </h3>
              <span
                class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)] tabular-nums bg-[var(--background-primary)] px-1.5 py-0.5 rounded"
              >
                {{ column.cards.length }}
              </span>
            </div>

            <!-- Cards (draggable container) -->
            <div
              :ref="(el) => setColumnRef(column.id, el as Element | null)"
              :data-dnd-column="column.id"
              class="space-y-2 min-h-[48px]"
            >
              <div
                v-for="card in column.cards"
                :key="card.id"
                :ref="(el) => setCardRef(card.id, el as Element | null)"
                :data-dnd-draggable="card.id"
                draggable="true"
                :class="[
                  'rounded-lg bg-[var(--background-primary)] border border-[var(--border-color)] p-3 space-y-2 cursor-grab active:cursor-grabbing transition-opacity',
                  draggedCardId === card.id ? 'opacity-50' : '',
                ]"
              >
                <!-- Card title -->
                <p class="text-sm font-medium text-[var(--text-display)] line-clamp-2 leading-snug">
                  {{ card.title }}
                </p>

                <!-- Platform badge -->
                <span
                  :class="[
                    'text-[10px] font-[var(--font-space-mono)] uppercase tracking-wider font-medium block',
                    platformBadgeColor[card.platform] ?? 'text-[var(--text-secondary)]',
                  ]"
                >
                  {{ platformLabels[card.platform] ?? card.platform }}
                </span>

                <!-- Author & Tags -->
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

                <!-- Move buttons (keyboard/accessibility fallback) -->
                <div class="flex items-center gap-1 pt-1">
                  <Button
                    variant="ghost"
                    size="icon-xs"
                    :disabled="isFirstColumn(column.id)"
                    class="opacity-60 hover:opacity-100 disabled:opacity-20"
                    @click="handleMoveLeft(card.id, column.id)"
                    aria-label="Move left"
                  >
                    <svg
                      width="12"
                      height="12"
                      viewBox="0 0 12 12"
                      fill="none"
                      aria-hidden="true"
                    >
                      <path
                        d="M7.5 2.5L4.5 6L7.5 9.5"
                        stroke="currentColor"
                        stroke-width="1.5"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                      />
                    </svg>
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-xs"
                    :disabled="isLastColumn(column.id)"
                    class="opacity-60 hover:opacity-100 disabled:opacity-20"
                    @click="handleMoveRight(card.id, column.id)"
                    aria-label="Move right"
                  >
                    <svg
                      width="12"
                      height="12"
                      viewBox="0 0 12 12"
                      fill="none"
                      aria-hidden="true"
                    >
                      <path
                        d="M4.5 2.5L7.5 6L4.5 9.5"
                        stroke="currentColor"
                        stroke-width="1.5"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                      />
                    </svg>
                  </Button>
                </div>
              </div>

              <!-- Empty column -->
              <p
                v-if="column.cards.length === 0"
                class="text-xs text-[var(--text-secondary)] text-center py-6 border border-dashed border-[var(--border-color)] rounded-lg"
              >
                {{ t('dashboard.pipeline.noCards') }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
