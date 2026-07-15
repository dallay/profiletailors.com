import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import KpiCard from './KpiCard.vue'
import type { KpiMetric } from '@modules/dashboard/domain/dashboard.types'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

function makeKpi(overrides: Partial<KpiMetric> = {}): KpiMetric {
  return {
    id: 'kpi-1',
    label: 'dashboard.executiveOverview.totalFollowers',
    value: '24.7K',
    delta: 8,
    deltaLabel: 'dashboard.executiveOverview.vsLast30',
    sparklineData: [100, 120, 115, 140, 135, 150, 170],
    trend: 'up',
    ...overrides,
  }
}

describe('KpiCard', () => {
  it('renders the metric label and value', () => {
    const wrapper = mount(KpiCard, {
      props: { metric: makeKpi() },
    })
    expect(wrapper.text()).toContain('dashboard.executiveOverview.totalFollowers')
    expect(wrapper.text()).toContain('24.7K')
  })

  it('renders the delta and delta label', () => {
    const wrapper = mount(KpiCard, {
      props: { metric: makeKpi() },
    })
    expect(wrapper.text()).toContain('+8%')
    expect(wrapper.text()).toContain('dashboard.executiveOverview.vsLast30')
  })

  it('shows up arrow for positive trend', () => {
    const wrapper = mount(KpiCard, {
      props: { metric: makeKpi({ trend: 'up' }) },
    })
    expect(wrapper.html()).toContain('\u2191')
  })

  it('shows down arrow for negative trend', () => {
    const wrapper = mount(KpiCard, {
      props: { metric: makeKpi({ trend: 'down', delta: -5 }) },
    })
    expect(wrapper.html()).toContain('\u2193')
    expect(wrapper.text()).toContain('-5%')
  })

  it('renders a SparklineChart when sparklineData is present', () => {
    const wrapper = mount(KpiCard, {
      props: { metric: makeKpi() },
    })
    // SparklineChart renders an SVG
    const svg = wrapper.find('svg')
    expect(svg.exists()).toBe(true)
  })
})
