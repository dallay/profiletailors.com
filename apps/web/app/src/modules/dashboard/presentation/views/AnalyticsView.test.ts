import { mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AnalyticsView from './AnalyticsView.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@lucide/vue', () => {
  const icon = { template: '<span />' }
  return {
    BarChart2: icon,
    Download: icon,
    Eye: icon,
    MousePointerClick: icon,
    TrendingUp: icon,
    UserPlus: icon,
    Clock: icon,
  }
})

const { analyticsStore, storeHolder } = vi.hoisted(() => ({
  analyticsStore: {
    overview: null as {
      totalImpressions: number
      totalEngagements: number
      engagementRate: number
      totalClicks: number
      newFollowers: number
      dailyMetrics: Array<{
        date: string
        impressions: number
        engagements: number
        clicks: number
      }>
    } | null,
    postAnalytics: null as {
      posts: Array<{
        postId: string
        title: string | null
        bodyText: string | null
        provider: string
        publishedAt: string
        impressions: number
        clicks: number
        engagements: number
        reactions: number
        comments: number
        shares: number
        engagementRate: number
      }>
      total: number
      page: number
      size: number
    } | null,
    bestTimes: null as { slots: Array<{ dayOfWeek: number; hour: number; score: number }> } | null,
    loadingOverview: false,
    loadingPosts: false,
    loadingBestTimes: false,
    exporting: false,
    error: null,
    preset: 'custom',
    customStart: '2026-08-01',
    customEnd: '2026-08-03',
    activeDateRange: { startDate: '2026-08-01', endDate: '2026-08-03' },
    refresh: vi.fn(),
    setPreset: vi.fn(),
    setCustomRange: vi.fn(),
    fetchPostAnalytics: vi.fn(),
    exportCsv: vi.fn(),
  },
  storeHolder: { current: null as Record<string, unknown> | null },
}))

storeHolder.current = reactive(analyticsStore)

vi.mock('@modules/analytics/infrastructure/analytics.store', () => ({
  useAnalyticsStore: () => storeHolder.current,
}))

const passthrough = { template: '<div><slot /></div>' }
const nativeButton = {
  inheritAttrs: false,
  template: '<button v-bind="$attrs"><slot /></button>',
}
const nativeInput = {
  inheritAttrs: false,
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
}
const nativeSelectTrigger = {
  inheritAttrs: false,
  template: '<button v-bind="$attrs"><slot /></button>',
}
const nativeSelect = {
  inheritAttrs: false,
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<div v-bind="$attrs"><button data-testid="preset-last7" type="button" @click="$emit(\'update:modelValue\', \'last7\')">last7</button><slot /></div>',
}

describe('AnalyticsView accessibility', () => {
  beforeEach(() => {
    analyticsStore.overview = null
    analyticsStore.postAnalytics = null
    analyticsStore.bestTimes = null
    analyticsStore.error = null
    analyticsStore.loadingOverview = false
    analyticsStore.loadingPosts = false
    analyticsStore.loadingBestTimes = false
    analyticsStore.exporting = false
    analyticsStore.preset = 'custom'
    analyticsStore.customStart = '2026-08-01'
    analyticsStore.customEnd = '2026-08-03'
    analyticsStore.refresh.mockReset()
    analyticsStore.setPreset.mockReset()
    analyticsStore.setCustomRange.mockReset()
    analyticsStore.fetchPostAnalytics.mockReset()
    analyticsStore.exportCsv.mockReset()
  })

  function mountView() {
    return mount(AnalyticsView, {
      global: {
        mocks: { $t: (key: string) => key },
        stubs: {
          Button: nativeButton,
          Input: nativeInput,
          Select: nativeSelect,
          SelectContent: passthrough,
          SelectItem: passthrough,
          SelectTrigger: nativeSelectTrigger,
          SelectValue: passthrough,
          Card: passthrough,
          CardContent: passthrough,
          CardHeader: passthrough,
          CardTitle: passthrough,
          Dialog: passthrough,
          DialogContent: passthrough,
          DialogDescription: passthrough,
          DialogHeader: passthrough,
          DialogTitle: passthrough,
          SocialProviderIcon: passthrough,
        },
      },
    })
  }

  it('associates every date-range control with a stable visible label', () => {
    const wrapper = mountView()

    for (const id of ['analytics-date-preset', 'analytics-start-date', 'analytics-end-date']) {
      expect(wrapper.find(`label[for="${id}"]`).exists()).toBe(true)
      expect(wrapper.find(`#${id}`).exists()).toBe(true)
    }
  })

  it('renders metrics and routes custom dates and export to the store', async () => {
    analyticsStore.overview = {
      totalImpressions: 1200,
      totalEngagements: 80,
      engagementRate: 6.666,
      totalClicks: 40,
      newFollowers: 12,
      dailyMetrics: [{ date: '2026-08-01', impressions: 1200, engagements: 80, clicks: 40 }],
    }
    analyticsStore.bestTimes = { slots: [{ dayOfWeek: 1, hour: 9, score: 10 }] }
    analyticsStore.postAnalytics = {
      posts: [
        {
          postId: 'post-1',
          title: null,
          bodyText: 'A post body that should be used as the preview',
          provider: 'linkedin',
          publishedAt: '2026-08-01T09:00:00Z',
          impressions: 1200,
          clicks: 40,
          engagements: 80,
          reactions: 10,
          comments: 2,
          shares: 1,
          engagementRate: 6.666,
        },
      ],
      total: 1,
      page: 0,
      size: 20,
    }

    const wrapper = mountView()

    expect(wrapper.text()).toContain('1.2K')
    expect(wrapper.text()).toContain('6.67%')
    expect(wrapper.text()).toContain('A post body that should be used as the preview')

    const startDate = wrapper.find('#analytics-start-date')
    await startDate.setValue('2026-08-02')
    await startDate.trigger('change')
    const exportButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('analytics.exportCsv'))
    await exportButton?.trigger('click')

    expect(analyticsStore.setCustomRange).toHaveBeenCalledWith('2026-08-02', '2026-08-03')
    expect(analyticsStore.exportCsv).toHaveBeenCalledOnce()
  })

  it('refreshes on mount and when the active date range changes', async () => {
    const wrapper = mountView()
    const refreshCallsAfterMount = analyticsStore.refresh.mock.calls.length

    expect(refreshCallsAfterMount).toBeGreaterThan(0)

    Object.assign(storeHolder.current as object, {
      activeDateRange: { startDate: '2026-08-02', endDate: '2026-08-03' },
    })
    await nextTick()

    expect(analyticsStore.refresh.mock.calls.length).toBeGreaterThan(refreshCallsAfterMount)
    wrapper.unmount()
  })

  it('routes preset selection to the store and supports changing the end date', async () => {
    const wrapper = mountView()

    await wrapper.get('[data-testid="preset-last7"]').trigger('click')
    const endDate = wrapper.get('#analytics-end-date')
    await endDate.setValue('2026-08-04')
    await endDate.trigger('change')

    expect(analyticsStore.setPreset).toHaveBeenCalledWith('last7')
    expect(analyticsStore.setCustomRange).toHaveBeenCalledWith('2026-08-01', '2026-08-04')
  })

  it('renders loading states for overview, best times, and posts', () => {
    analyticsStore.loadingOverview = true
    analyticsStore.loadingBestTimes = true
    analyticsStore.loadingPosts = true

    const wrapper = mountView()

    expect(wrapper.text()).toContain('analytics.loading')
    expect(
      wrapper
        .findAll('div')
        .filter(
          (node) => node.text() === 'analytics.loading' && node.element.children.length === 0,
        ),
    ).toHaveLength(3)
    expect(wrapper.text()).not.toContain('analytics.noData')
    expect(wrapper.text()).not.toContain('analytics.noPostsInPeriod')
  })

  it('renders empty states for all analytics sections', () => {
    analyticsStore.overview = { dailyMetrics: [] } as never
    analyticsStore.bestTimes = { slots: [] }
    analyticsStore.postAnalytics = { posts: [], total: 0, page: 0, size: 20 }

    const wrapper = mountView()

    expect(wrapper.text()).toContain('analytics.noData')
    expect(wrapper.text()).toContain('analytics.noPostsInPeriod')
  })

  it('renders errors without replacing the underlying empty states', () => {
    Object.assign(storeHolder.current as object, { error: 'analytics unavailable' })
    analyticsStore.overview = { dailyMetrics: [] } as never
    analyticsStore.bestTimes = { slots: [] }
    analyticsStore.postAnalytics = { posts: [], total: 0, page: 0, size: 20 }

    const wrapper = mountView()

    expect(wrapper.text()).toContain('analytics unavailable')
    expect(wrapper.text()).toContain('analytics.noData')
    expect(wrapper.text()).toContain('analytics.noPostsInPeriod')
  })

  it('covers compact number/rate formatting, chart minimum height, labels, and post preview fallbacks', () => {
    analyticsStore.overview = {
      totalImpressions: 1_000_000,
      totalEngagements: 999,
      engagementRate: 0,
      totalClicks: 1_001,
      newFollowers: 4,
      dailyMetrics: [
        { date: '2026-08-01', impressions: 0, engagements: 0, clicks: 0 },
        { date: '2026-08-02', impressions: 2_000_000, engagements: 0, clicks: 0 },
      ],
    }
    analyticsStore.bestTimes = {
      slots: Array.from({ length: 6 }, (_, index) => ({ dayOfWeek: index, hour: 9, score: index })),
    }
    analyticsStore.postAnalytics = {
      posts: [
        {
          postId: 'post-title',
          title: 'Title preview',
          bodyText: 'Ignored body',
          provider: 'linkedin',
          publishedAt: '2026-08-01T09:00:00Z',
          impressions: 1_000_000,
          clicks: 1_001,
          engagements: 999,
          reactions: 0,
          comments: 0,
          shares: 0,
          engagementRate: 0,
        },
        {
          postId: 'post-empty',
          title: null,
          bodyText: null,
          provider: 'linkedin',
          publishedAt: '2026-08-01T09:00:00Z',
          impressions: 0,
          clicks: 0,
          engagements: 0,
          reactions: 0,
          comments: 0,
          shares: 0,
          engagementRate: 0,
        },
      ],
      total: 2,
      page: 0,
      size: 20,
    }

    const wrapper = mountView()
    const bars = wrapper.findAll('[title$="impressions"]')

    expect(wrapper.text()).toContain('1.0M')
    expect(wrapper.text()).toContain('999')
    expect(wrapper.text()).toContain('1.0K')
    expect(wrapper.text()).toContain('0.00%')
    expect(wrapper.text()).toContain('Title preview')
    expect(wrapper.text()).toContain('—')
    expect(wrapper.text()).toContain('analytics.days.sun')
    expect(wrapper.text()).toContain('09:00')
    expect(wrapper.text()).not.toContain('analytics.days.sat')
    expect(bars[0]?.attributes('style')).toContain('height: 4%')
    expect(bars[1]?.attributes('style')).toContain('height: 100%')
  })

  it('renders pagination and requests previous and next pages', async () => {
    analyticsStore.postAnalytics = {
      posts: [],
      total: 45,
      page: 1,
      size: 20,
    }

    const wrapper = mountView()
    const arrows = wrapper.findAll('button').filter((button) => ['←', '→'].includes(button.text()))

    expect(wrapper.text()).toContain('analytics.showing')
    await arrows[0]?.trigger('click')
    await arrows[1]?.trigger('click')

    expect(analyticsStore.fetchPostAnalytics).toHaveBeenNthCalledWith(1, 0)
    expect(analyticsStore.fetchPostAnalytics).toHaveBeenNthCalledWith(2, 2)
  })

  it('opens detailed post metrics with CTR and a period comparison', async () => {
    analyticsStore.postAnalytics = {
      posts: [
        {
          postId: 'post-1',
          title: 'Launch notes',
          bodyText: null,
          provider: 'linkedin',
          publishedAt: '2026-08-01T09:00:00Z',
          impressions: 1000,
          clicks: 50,
          engagements: 80,
          reactions: 60,
          comments: 12,
          shares: 8,
          engagementRate: 8,
        },
        {
          postId: 'post-2',
          title: 'Weekly recap',
          bodyText: null,
          provider: 'linkedin',
          publishedAt: '2026-08-02T09:00:00Z',
          impressions: 500,
          clicks: 10,
          engagements: 20,
          reactions: 15,
          comments: 3,
          shares: 2,
          engagementRate: 4,
        },
      ],
      total: 2,
      page: 0,
      size: 20,
    }

    const wrapper = mountView()

    const postButton = wrapper.findAll('button').find((button) => button.text() === 'Launch notes')
    await postButton?.trigger('click')

    expect(wrapper.text()).toContain('analytics.clickThroughRate')
    expect(wrapper.text()).toContain('5.00%')
    expect(wrapper.text()).toContain('analytics.reactions')
    expect(wrapper.text()).toContain('analytics.comments')
    expect(wrapper.text()).toContain('analytics.shares')
    expect(wrapper.text()).toContain('analytics.aboveAverage')
  })

  it('disables pagination controls at the first and last pages', async () => {
    analyticsStore.postAnalytics = {
      posts: [],
      total: 40,
      page: 0,
      size: 20,
    }

    const wrapper = mountView()
    const arrows = wrapper.findAll('button').filter((button) => ['←', '→'].includes(button.text()))

    expect(arrows[0]?.attributes('disabled')).toBeDefined()
    expect(arrows[1]?.attributes('disabled')).toBeUndefined()

    Object.assign(storeHolder.current as object, {
      postAnalytics: {
        posts: [],
        total: 40,
        page: 1,
        size: 20,
      },
    })
    await nextTick()
    expect(
      wrapper
        .findAll('button')
        .filter((button) => ['←', '→'].includes(button.text()))[1]
        ?.attributes('disabled'),
    ).toBeDefined()
  })
})
