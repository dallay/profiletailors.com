import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAnalyticsStore } from './analytics.store'
import { useAuthStore } from '@modules/auth'

describe('analytics store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it.each([
    ['last7', '2026-07-28'],
    ['last30', '2026-07-05'],
    ['last90', '2026-05-06'],
  ] as const)('calculates the inclusive %s preset range', (value, startDate) => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-03T12:00:00Z'))

    try {
      const store = useAnalyticsStore()

      store.setPreset(value)

      expect(store.activeDateRange).toEqual({ startDate, endDate: '2026-08-03' })
    } finally {
      vi.useRealTimers()
    }
  })

  it('switches to custom mode and preserves both custom dates', () => {
    const store = useAnalyticsStore()

    store.setCustomRange('2026-07-01', '2026-07-15')

    expect(store.preset).toBe('custom')
    expect(store.customStart).toBe('2026-07-01')
    expect(store.customEnd).toBe('2026-07-15')
    expect(store.activeDateRange).toEqual({ startDate: '2026-07-01', endDate: '2026-07-15' })
  })

  it('loads all analytics views with the active date range', async () => {
    const auth = useAuthStore()
    const apiFetch = vi.spyOn(auth, 'apiFetch').mockImplementation(async (path) => {
      if (path.includes('/overview')) return { totalImpressions: 10, dailyMetrics: [] }
      if (path.includes('/posts')) return { posts: [], total: 0, page: 0, size: 20 }
      return { slots: [] }
    })
    const store = useAnalyticsStore()

    store.setCustomRange('2026-08-01', '2026-08-03')
    await store.refresh()

    expect(apiFetch).toHaveBeenCalledWith(
      '/api/analytics/overview?startDate=2026-08-01&endDate=2026-08-03',
      { method: 'GET', workspaceScoped: true },
    )
    expect(apiFetch).toHaveBeenCalledWith(
      '/api/analytics/posts?startDate=2026-08-01&endDate=2026-08-03&page=0&size=20',
      { method: 'GET', workspaceScoped: true },
    )
    expect(apiFetch).toHaveBeenCalledWith('/api/analytics/best-times', {
      method: 'GET',
      workspaceScoped: true,
    })
    expect(store.loadingOverview).toBe(false)
    expect(store.loadingPosts).toBe(false)
    expect(store.loadingBestTimes).toBe(false)
  })

  it('captures fetch failures and clears loading state', async () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('analytics unavailable'))
    const store = useAnalyticsStore()

    await store.fetchOverview()

    expect(store.error).toBe('analytics unavailable')
    expect(store.loadingOverview).toBe(false)
  })

  it('uses the overview fallback message for non-Error failures', async () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'apiFetch').mockRejectedValue('offline')
    const store = useAnalyticsStore()

    await store.fetchOverview()

    expect(store.error).toBe('Failed to load analytics overview.')
    expect(store.loadingOverview).toBe(false)
  })

  it('loads paginated post analytics and stores the response', async () => {
    const auth = useAuthStore()
    const response = { posts: [], total: 0, page: 2, size: 5 }
    const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue(response)
    const store = useAnalyticsStore()

    store.setCustomRange('2026-08-01', '2026-08-03')
    await store.fetchPostAnalytics(2, 5)

    expect(apiFetch).toHaveBeenCalledWith(
      '/api/analytics/posts?startDate=2026-08-01&endDate=2026-08-03&page=2&size=5',
      { method: 'GET', workspaceScoped: true },
    )
    expect(store.postAnalytics).toEqual(response)
    expect(store.loadingPosts).toBe(false)
  })

  it('uses the post analytics fallback message for non-Error failures', async () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'apiFetch').mockRejectedValue('offline')
    const store = useAnalyticsStore()

    await store.fetchPostAnalytics()

    expect(store.error).toBe('Failed to load post analytics.')
    expect(store.loadingPosts).toBe(false)
  })

  it('loads best-time recommendations and uses its fallback error message', async () => {
    const auth = useAuthStore()
    const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({ slots: [] })
    const store = useAnalyticsStore()

    await store.fetchBestTimes()

    expect(apiFetch).toHaveBeenCalledWith('/api/analytics/best-times', {
      method: 'GET',
      workspaceScoped: true,
    })
    expect(store.bestTimes).toEqual({ slots: [] })
    expect(store.loadingBestTimes).toBe(false)

    apiFetch.mockRejectedValueOnce('offline')
    await store.fetchBestTimes()

    expect(store.error).toBe('Failed to load best times.')
    expect(store.loadingBestTimes).toBe(false)
  })

  it('uses preset dates and records failed exports', async () => {
    const auth = useAuthStore()
    const apiFetchRaw = vi
      .spyOn(auth, 'apiFetchRaw')
      .mockResolvedValue(new Response(null, { status: 500 }))
    const store = useAnalyticsStore()

    store.setPreset('last7')
    await store.exportCsv()

    expect(apiFetchRaw).toHaveBeenCalledWith(
      expect.stringMatching(
        /^\/api\/analytics\/export\?startDate=\d{4}-\d{2}-\d{2}&endDate=\d{4}-\d{2}-\d{2}$/,
      ),
      { method: 'POST', workspaceScoped: true },
    )
    expect(store.error).toBe('Export failed.')
    expect(store.exporting).toBe(false)
  })

  it('downloads a successful CSV export and releases its object URL', async () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'apiFetchRaw').mockResolvedValue(
      new Response('date,impressions\n2026-08-01,10', { status: 200 }),
    )
    const createObjectURL = vi.fn(() => 'blob:analytics')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })
    const anchor = document.createElement('a')
    const click = vi.spyOn(anchor, 'click').mockImplementation(() => undefined)
    vi.spyOn(document, 'createElement').mockReturnValue(anchor)
    const store = useAnalyticsStore()
    store.setCustomRange('2026-08-01', '2026-08-03')

    await store.exportCsv()

    expect(createObjectURL).toHaveBeenCalledOnce()
    expect(anchor.download).toBe('analytics-export-2026-08-01-2026-08-03.csv')
    expect(click).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:analytics')
    expect(store.exporting).toBe(false)
  })

  it('records a rejected CSV blob and always clears exporting state', async () => {
    const auth = useAuthStore()
    const apiFetchRaw = vi.spyOn(auth, 'apiFetchRaw').mockResolvedValue({
      ok: true,
      blob: vi.fn().mockRejectedValue(new Error('CSV body unavailable')),
    } as unknown as Response)
    const store = useAnalyticsStore()

    await store.exportCsv()

    expect(apiFetchRaw).toHaveBeenCalledOnce()
    expect(store.error).toBe('CSV body unavailable')
    expect(store.exporting).toBe(false)
  })

  it('uses the generic export fallback for non-Error failures', async () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'apiFetchRaw').mockRejectedValue('offline')
    const store = useAnalyticsStore()

    await store.exportCsv()

    expect(store.error).toBe('Export failed.')
    expect(store.exporting).toBe(false)
  })

  it('releases the CSV object URL when starting the download fails', async () => {
    const auth = useAuthStore()
    vi.spyOn(auth, 'apiFetchRaw').mockResolvedValue(
      new Response('date,impressions\n2026-08-01,10', { status: 200 }),
    )
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { createObjectURL: vi.fn(() => 'blob:analytics'), revokeObjectURL })
    const anchor = document.createElement('a')
    vi.spyOn(anchor, 'click').mockImplementation(() => {
      throw new Error('download blocked')
    })
    vi.spyOn(document, 'createElement').mockReturnValue(anchor)
    const store = useAnalyticsStore()

    await store.exportCsv()

    expect(store.error).toBe('download blocked')
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:analytics')
    expect(store.exporting).toBe(false)
  })
})
