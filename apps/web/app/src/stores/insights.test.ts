import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useInsightsStore } from './insights'

describe('insights store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  describe('initial state', () => {
    it('loads mock insights on creation', () => {
      const store = useInsightsStore()
      expect(store.insights).toHaveLength(5)
    })

    it('all insights are not dismissed initially', () => {
      const store = useInsightsStore()
      expect(store.activeInsights).toHaveLength(5)
    })

    it('counts high priority insights', () => {
      const store = useInsightsStore()
      expect(store.highPriorityCount).toBe(2)
    })
  })

  describe('dismiss', () => {
    it('dismisses a single insight by id', () => {
      const store = useInsightsStore()
      store.dismiss('insight-1')
      expect(store.insights.find((i) => i.id === 'insight-1')?.dismissed).toBe(true)
      expect(store.activeInsights).toHaveLength(4)
    })

    it('does nothing for unknown id', () => {
      const store = useInsightsStore()
      store.dismiss('nonexistent')
      expect(store.activeInsights).toHaveLength(5)
    })
  })

  describe('dismissAll', () => {
    it('dismisses all insights', () => {
      const store = useInsightsStore()
      store.dismissAll()
      expect(store.activeInsights).toHaveLength(0)
      expect(store.highPriorityCount).toBe(0)
    })
  })

  describe('refreshAll', () => {
    it('completes and resets loading state', async () => {
      const store = useInsightsStore()
      await store.refreshAll()
      expect(store.isLoading).toBe(false)
    })
  })
})
