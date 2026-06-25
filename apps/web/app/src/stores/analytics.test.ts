import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAnalyticsStore } from './analytics'

describe('analytics store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  describe('initial state', () => {
    it('loads mock KPI metrics on creation', () => {
      const store = useAnalyticsStore()
      expect(store.kpiMetrics).toHaveLength(4)
      expect(store.kpiMetrics[0]?.id).toBe('total-followers')
    })

    it('loads channel performance data', () => {
      const store = useAnalyticsStore()
      expect(store.channelPerformance).toHaveLength(4)
      expect(store.channelPerformance[0]?.platform).toBe('linkedin')
    })

    it('loads top posts', () => {
      const store = useAnalyticsStore()
      expect(store.topPosts.length).toBeGreaterThan(0)
    })

    it('loads audience growth data', () => {
      const store = useAnalyticsStore()
      expect(store.audienceGrowth.length).toBeGreaterThan(0)
    })

    it('loads growth score', () => {
      const store = useAnalyticsStore()
      expect(store.growthScore.overall).toBe(74)
      expect(store.growthScore.breakdown.consistency).toBe(82)
    })

    it('is not loading initially', () => {
      const store = useAnalyticsStore()
      expect(store.isLoading).toBe(false)
    })
  })

  describe('computed properties', () => {
    it('computes total followers from channels', () => {
      const store = useAnalyticsStore()
      const expected = store.channelPerformance.reduce((sum, ch) => sum + ch.followers, 0)
      expect(store.totalFollowers).toBe(expected)
    })

    it('identifies top platform by engagement rate', () => {
      const store = useAnalyticsStore()
      expect(store.topPlatform?.platform).toBe('bluesky')
      expect(store.topPlatform?.engagementRate).toBe(6.7)
    })
  })

  describe('refreshAll', () => {
    it('completes and resets loading state', async () => {
      const store = useAnalyticsStore()
      await store.refreshAll()
      expect(store.isLoading).toBe(false)
    })
  })
})
