import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type {
  KpiMetric,
  ChannelPerformance,
  TopPost,
  AudienceGrowthPoint,
  GrowthScore,
  PostingTimeSlot,
} from '@modules/dashboard/domain/dashboard.types'
import {
  kpiMetrics as mockKpiMetrics,
  channelPerformance as mockChannelPerformance,
  topPosts as mockTopPosts,
  audienceGrowthData as mockAudienceGrowth,
} from '@modules/dashboard/infrastructure/mock-data/analytics'
import { growthScore as mockGrowthScore } from '@modules/dashboard/infrastructure/mock-data/growth-score'
import { postingTimeSlots as mockPostingTimeSlots } from '@modules/dashboard/infrastructure/mock-data/scheduling'

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------

export const useAnalyticsStore = defineStore('analytics', () => {
  const isLoading = ref(false)
  const kpiMetrics = ref<KpiMetric[]>([...mockKpiMetrics])
  const channelPerformance = ref<ChannelPerformance[]>([...mockChannelPerformance])
  const topPosts = ref<TopPost[]>([...mockTopPosts])
  const audienceGrowth = ref<AudienceGrowthPoint[]>([...mockAudienceGrowth])
  const growthScore = ref<GrowthScore>({ ...mockGrowthScore })
  const postingTimeSlots = ref<PostingTimeSlot[]>([...mockPostingTimeSlots])

  const totalFollowers = computed(() =>
    channelPerformance.value.reduce((sum, ch) => sum + ch.followers, 0),
  )

  const topPlatform = computed(() => {
    if (channelPerformance.value.length === 0) return null
    return channelPerformance.value.reduce((best, ch) =>
      ch.engagementRate > best.engagementRate ? ch : best,
    )
  })

  /**
   * Refreshes analytics data while tracking the loading state.
   */
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
    kpiMetrics,
    channelPerformance,
    topPosts,
    audienceGrowth,
    growthScore,
    postingTimeSlots,
    totalFollowers,
    topPlatform,
    refreshAll,
  }
})
