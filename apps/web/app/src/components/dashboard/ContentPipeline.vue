<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDragAndDrop } from '@formkit/drag-and-drop/vue'
import type { PipelineColumn, PipelineCard, Platform } from '@/lib/types/dashboard'
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

// ---------------------------------------------------------------------------
// Drag & Drop — @formkit/drag-and-drop
// ---------------------------------------------------------------------------

interface DragEventData {
  draggedNode?: { data?: { value?: PipelineCard } }
}

const columnIds = props.columns.map((c) => c.id)

// Track the dragged card across dragstart/dragend
const draggedCard = ref<PipelineCard | null>(null)

function findColumnForCard(cardId: string): string | null {
  for (let i = 0; i < 4; i++) {
    if (allItems[i].value.some((c) => c.id === cardId)) {
      return columnIds[i] ?? null
    }
  }
  return null
}

function createDragEndHandler(colIndex: number) {
  return () => {
    if (!draggedCard.value) return
    const targetColId = findColumnForCard(draggedCard.value.id)
    if (targetColId && targetColId !== columnIds[colIndex]) {
      // Calculate the index at which the library placed the card in the target column
      const targetColIdx = columnIds.indexOf(targetColId)
      const targetIndex = targetColIdx >= 0
        ? allItems[targetColIdx]?.value.findIndex((c) => c.id === draggedCard.value!.id)
        : -1
      emit('moveCard', draggedCard.value.id, columnIds[colIndex], targetColId, targetIndex >= 0 ? targetIndex : undefined)
    }
    draggedCard.value = null
  }
}

// One useDragAndDrop per column — all share the same group for cross-list transfer
const [col0Ref, col0Items] = useDragAndDrop(
  [...props.columns[0].cards],
  {
    group: 'pipeline',
    onDragstart: (data: DragEventData) => { draggedCard.value = data.draggedNode?.data?.value ?? null },
    onDragend: createDragEndHandler(0),
  },
)
const [col1Ref, col1Items] = useDragAndDrop(
  [...props.columns[1].cards],
  {
    group: 'pipeline',
    onDragstart: (data: DragEventData) => { draggedCard.value = data.draggedNode?.data?.value ?? null },
    onDragend: createDragEndHandler(1),
  },
)
const [col2Ref, col2Items] = useDragAndDrop(
  [...props.columns[2].cards],
  {
    group: 'pipeline',
    onDragstart: (data: DragEventData) => { draggedCard.value = data.draggedNode?.data?.value ?? null },
    onDragend: createDragEndHandler(2),
  },
)
const [col3Ref, col3Items] = useDragAndDrop(
  [...props.columns[3].cards],
  {
    group: 'pipeline',
    onDragstart: (data: DragEventData) => { draggedCard.value = data.draggedNode?.data?.value ?? null },
    onDragend: createDragEndHandler(3),
  },
)

const allItems = [col0Items, col1Items, col2Items, col3Items]
const colRefs = [col0Ref, col1Ref, col2Ref, col3Ref]

function setColumnRef(colIndex: number, el: Element | null): void {
  const targetRef = colRefs[colIndex]
  if (!targetRef) return
  targetRef.value = el instanceof HTMLElement ? el : undefined
}

// Sync local DnD state when props change (e.g. after move-button click updates the store)
watch(
  () => props.columns,
  (newCols) => {
    for (let i = 0; i < 4; i++) {
      allItems[i].value = [...newCols[i]!.cards]
    }
  },
  { deep: true },
)
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
            v-for="(column, colIndex) in columns"
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
                {{ allItems[colIndex]?.value.length ?? column.cards.length }}
              </span>
            </div>

            <!-- Cards (draggable container) -->
            <div
              :ref="(el) => setColumnRef(colIndex, el as Element | null)"
              class="space-y-2 min-h-[48px]"
            >
              <div
                v-for="card in allItems[colIndex]?.value ?? column.cards"
                :key="card.id"
                :data-dnd-draggable="card.id"
                class="rounded-lg bg-[var(--background-primary)] border border-[var(--border-color)] p-3 space-y-2 cursor-grab active:cursor-grabbing transition-opacity data-[dnd-dragging]:opacity-50"
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
                v-if="(allItems[colIndex]?.value.length ?? column.cards.length) === 0"
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
