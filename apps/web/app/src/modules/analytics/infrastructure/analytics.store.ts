import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { useAuthStore } from '@modules/auth'
import type {
  AnalyticsOverview,
  BestTimesRecommendation,
  DateRangePreset,
  PostAnalyticsList,
} from '@modules/analytics/domain/types'

const PRESET_DAYS: Record<Exclude<DateRangePreset, 'custom'>, number> = {
  last7: 7,
  last30: 30,
  last90: 90,
}

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10)
}

function presetDates(preset: Exclude<DateRangePreset, 'custom'>): {
  startDate: string
  endDate: string
} {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - (PRESET_DAYS[preset] - 1))
  return { startDate: toIsoDate(start), endDate: toIsoDate(end) }
}

export const useAnalyticsStore = defineStore('analytics', () => {
  const auth = useAuthStore()

  const overview = ref<AnalyticsOverview | null>(null)
  const postAnalytics = ref<PostAnalyticsList | null>(null)
  const bestTimes = ref<BestTimesRecommendation | null>(null)

  const loadingOverview = ref(false)
  const loadingPosts = ref(false)
  const loadingBestTimes = ref(false)
  const exporting = ref(false)

  const error = ref<string | null>(null)

  const preset = ref<DateRangePreset>('last30')
  const customStart = ref<string>(toIsoDate(new Date(Date.now() - 29 * 86_400_000)))
  const customEnd = ref<string>(toIsoDate(new Date()))

  const activeDateRange = computed(() => {
    if (preset.value === 'custom') {
      return { startDate: customStart.value, endDate: customEnd.value }
    }
    return presetDates(preset.value)
  })

  async function fetchOverview(): Promise<void> {
    loadingOverview.value = true
    error.value = null
    try {
      const { startDate, endDate } = activeDateRange.value
      overview.value = await auth.apiFetch<AnalyticsOverview>(
        `/api/analytics/overview?startDate=${startDate}&endDate=${endDate}`,
        { method: 'GET', workspaceScoped: true },
      )
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load analytics overview.'
    } finally {
      loadingOverview.value = false
    }
  }

  async function fetchPostAnalytics(page = 0, size = 20): Promise<void> {
    loadingPosts.value = true
    error.value = null
    try {
      const { startDate, endDate } = activeDateRange.value
      postAnalytics.value = await auth.apiFetch<PostAnalyticsList>(
        `/api/analytics/posts?startDate=${startDate}&endDate=${endDate}&page=${page}&size=${size}`,
        { method: 'GET', workspaceScoped: true },
      )
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load post analytics.'
    } finally {
      loadingPosts.value = false
    }
  }

  async function fetchBestTimes(): Promise<void> {
    loadingBestTimes.value = true
    error.value = null
    try {
      bestTimes.value = await auth.apiFetch<BestTimesRecommendation>('/api/analytics/best-times', {
        method: 'GET',
        workspaceScoped: true,
      })
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load best times.'
    } finally {
      loadingBestTimes.value = false
    }
  }

  async function exportCsv(): Promise<void> {
    exporting.value = true
    error.value = null
    try {
      const { startDate, endDate } = activeDateRange.value
      const response = await auth.apiFetchRaw(
        `/api/analytics/export?startDate=${startDate}&endDate=${endDate}`,
        { method: 'POST', workspaceScoped: true },
      )
      if (!response.ok) {
        throw new Error('Export failed.')
      }
      const blob = await response.blob()
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `analytics-export-${startDate}-${endDate}.csv`
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Export failed.'
    } finally {
      exporting.value = false
    }
  }

  function setPreset(value: DateRangePreset): void {
    preset.value = value
  }

  function setCustomRange(start: string, end: string): void {
    customStart.value = start
    customEnd.value = end
    preset.value = 'custom'
  }

  async function refresh(): Promise<void> {
    await Promise.all([fetchOverview(), fetchPostAnalytics(), fetchBestTimes()])
  }

  return {
    overview,
    postAnalytics,
    bestTimes,
    loadingOverview,
    loadingPosts,
    loadingBestTimes,
    exporting,
    error,
    preset,
    customStart,
    customEnd,
    activeDateRange,
    fetchOverview,
    fetchPostAnalytics,
    fetchBestTimes,
    exportCsv,
    setPreset,
    setCustomRange,
    refresh,
  }
})
