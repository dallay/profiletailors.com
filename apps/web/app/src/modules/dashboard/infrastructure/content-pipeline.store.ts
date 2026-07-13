import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { PipelineColumn, PipelineCard } from '@modules/dashboard/domain/dashboard.types'
import { pipelineColumns as mockColumns } from '@modules/dashboard/infrastructure/mock-data/content-pipeline'

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------

export const useContentPipelineStore = defineStore('contentPipeline', () => {
  const isLoading = ref(false)
  const columns = ref<PipelineColumn[]>(
    mockColumns.map((col) => ({
      ...col,
      cards: col.cards.map((card) => ({ ...card })),
    })),
  )

  const totalCards = computed(() => columns.value.reduce((sum, col) => sum + col.cards.length, 0))

  const scheduledCount = computed(() => {
    const scheduledCol = columns.value.find((col) => col.id === 'scheduled')
    return scheduledCol ? scheduledCol.cards.length : 0
  })

  function moveCard(
    cardId: string,
    fromColumnId: string,
    toColumnId: string,
    toIndex?: number,
  ): void {
    const fromCol = columns.value.find((col) => col.id === fromColumnId)
    const toCol = columns.value.find((col) => col.id === toColumnId)
    if (!fromCol || !toCol) return

    const cardIndex = fromCol.cards.findIndex((c) => c.id === cardId)
    if (cardIndex === -1) return

    const [card] = fromCol.cards.splice(cardIndex, 1)
    if (!card) return

    const insertAt = toIndex ?? toCol.cards.length
    toCol.cards.splice(insertAt, 0, card)
  }

  function addCard(columnId: string, card: PipelineCard): void {
    const col = columns.value.find((c) => c.id === columnId)
    if (col) {
      col.cards.push(card)
    }
  }

  function removeCard(columnId: string, cardId: string): void {
    const col = columns.value.find((c) => c.id === columnId)
    if (col) {
      col.cards = col.cards.filter((c) => c.id !== cardId)
    }
  }

  async function refreshAll(): Promise<void> {
    isLoading.value = true
    try {
      console.log('[contentPipeline] refreshAll — mock mode, no-op')
    } finally {
      isLoading.value = false
    }
  }

  return {
    isLoading,
    columns,
    totalCards,
    scheduledCount,
    moveCard,
    addCard,
    removeCard,
    refreshAll,
  }
})
