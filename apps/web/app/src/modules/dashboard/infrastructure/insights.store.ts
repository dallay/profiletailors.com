import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { AiInsight } from '@modules/dashboard/domain/dashboard.types'
import { aiInsights as mockInsights } from '@modules/dashboard/infrastructure/mock-data/insights'

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------

export const useInsightsStore = defineStore('insights', () => {
  const isLoading = ref(false)
  const insights = ref<AiInsight[]>(mockInsights.map((i) => ({ ...i })))

  const activeInsights = computed(() => insights.value.filter((i) => !i.dismissed))

  const highPriorityCount = computed(
    () => activeInsights.value.filter((i) => i.priority === 'high').length,
  )

  function dismiss(id: string): void {
    const insight = insights.value.find((i) => i.id === id)
    if (insight) {
      insight.dismissed = true
    }
  }

  function dismissAll(): void {
    insights.value.forEach((i) => {
      i.dismissed = true
    })
  }

  async function refreshAll(): Promise<void> {
    isLoading.value = true
    try {
      // Mock mode — in production this would call the API
    } finally {
      isLoading.value = false
    }
  }

  return {
    isLoading,
    insights,
    activeInsights,
    highPriorityCount,
    dismiss,
    dismissAll,
    refreshAll,
  }
})
