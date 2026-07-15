import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ExecutiveOverview from './ExecutiveOverview.vue'
import type { KpiMetric } from '@modules/dashboard/domain/dashboard.types'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

function makeKpi(id: string, overrides: Partial<KpiMetric> = {}): KpiMetric {
  return {
    id,
    label: 'dashboard.executiveOverview.totalFollowers',
    value: '24.7K',
    delta: 8,
    deltaLabel: 'dashboard.executiveOverview.vsLast30',
    sparklineData: [100, 110, 120, 130, 140, 150, 160],
    trend: 'up',
    ...overrides,
  }
}

describe('ExecutiveOverview', () => {
  it('renders the section title', () => {
    const wrapper = mount(ExecutiveOverview, {
      props: { kpis: [] },
    })
    expect(wrapper.text()).toContain('dashboard.executiveOverview.title')
    expect(wrapper.text()).toContain('dashboard.executiveOverview.last30Days')
  })

  it('renders a KpiCard for each metric', () => {
    const kpis = [
      makeKpi('kpi-1', { value: '24.7K' }),
      makeKpi('kpi-2', { value: '4.2%' }),
      makeKpi('kpi-3', { value: '182K' }),
      makeKpi('kpi-4', { value: '38' }),
    ]
    const wrapper = mount(ExecutiveOverview, {
      props: { kpis },
    })
    expect(wrapper.text()).toContain('24.7K')
    expect(wrapper.text()).toContain('4.2%')
    expect(wrapper.text()).toContain('182K')
    expect(wrapper.text()).toContain('38')
  })

  it('renders 4 KPI cards in a grid', () => {
    const kpis = Array.from({ length: 4 }, (_, i) => makeKpi(`kpi-${i}`))
    const wrapper = mount(ExecutiveOverview, {
      props: { kpis },
    })
    const cards = wrapper.findAllComponents({ name: 'KpiCard' })
    expect(cards).toHaveLength(4)
  })

  it('handles empty KPI array gracefully', () => {
    const wrapper = mount(ExecutiveOverview, {
      props: { kpis: [] },
    })
    expect(wrapper.find('section').exists()).toBe(true)
  })
})
