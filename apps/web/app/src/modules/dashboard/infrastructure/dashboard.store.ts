import { ref } from 'vue'
import { defineStore } from 'pinia'
import { useAnalyticsStore } from './analytics.store'
import { useInsightsStore } from './insights.store'
import { useContentPipelineStore } from './content-pipeline.store'

// ---------------------------------------------------------------------------
// Dashboard Orchestrator Store
// ---------------------------------------------------------------------------

export type DashboardSection =
  | 'executiveOverview'
  | 'aiInsights'
  | 'growthScore'
  | 'contentPerformance'
  | 'crossChannel'
  | 'audienceGrowth'
  | 'upcomingSchedule'
  | 'contentPipeline'
  | 'postingTimes'
  | 'inbox'
  | 'teamActivity'

export const useDashboardStore = defineStore('dashboard', () => {
  const isLoading = ref(false)
  const loadingError = ref<string | null>(null)

  // Section visibility — all visible by default
  const sectionVisibility = ref<Record<DashboardSection, boolean>>({
    executiveOverview: true,
    aiInsights: true,
    growthScore: true,
    contentPerformance: true,
    crossChannel: true,
    audienceGrowth: true,
    upcomingSchedule: true,
    contentPipeline: true,
    postingTimes: true,
    inbox: true,
    teamActivity: true,
  })

  function toggleSection(section: DashboardSection): void {
    sectionVisibility.value[section] = !sectionVisibility.value[section]
  }

  function setSectionVisibility(section: DashboardSection, visible: boolean): void {
    sectionVisibility.value[section] = visible
  }

  async function refreshAll(): Promise<void> {
    isLoading.value = true
    loadingError.value = null

    try {
      const analytics = useAnalyticsStore()
      const insights = useInsightsStore()
      const pipeline = useContentPipelineStore()

      await Promise.all([analytics.refreshAll(), insights.refreshAll(), pipeline.refreshAll()])
    } catch (err) {
      loadingError.value = err instanceof Error ? err.message : 'Failed to refresh dashboard'
    } finally {
      isLoading.value = false
    }
  }

  return {
    isLoading,
    loadingError,
    sectionVisibility,
    toggleSection,
    setSectionVisibility,
    refreshAll,
  }
})
