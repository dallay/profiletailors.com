import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import AudienceGrowthChart from './AudienceGrowthChart.vue'
import type { AudienceGrowthPoint } from '@modules/dashboard/domain/dashboard.types'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

function makeData(): AudienceGrowthPoint[] {
  return [
    { date: 'Jan', followers: 5000 },
    { date: 'Feb', followers: 5200 },
    { date: 'Mar', followers: 5600, milestone: '10K total' },
    { date: 'Apr', followers: 5900 },
    { date: 'May', followers: 6300 },
    { date: 'Jun', followers: 6800 },
  ]
}

describe('AudienceGrowthChart', () => {
  it('renders the section title and subtitle', () => {
    const wrapper = mount(AudienceGrowthChart, {
      props: { data: makeData() },
    })
    expect(wrapper.text()).toContain('dashboard.audienceGrowth.title')
    expect(wrapper.text()).toContain('dashboard.audienceGrowth.subtitle')
  })

  it('renders an SVG with line chart', () => {
    const wrapper = mount(AudienceGrowthChart, {
      props: { data: makeData() },
    })
    const svg = wrapper.find('svg')
    expect(svg.exists()).toBe(true)
    expect(svg.attributes('role')).toBe('img')
    expect(svg.attributes('aria-label')).toBe('Audience growth line chart')
  })

  it('renders milestone annotations when data has milestones', () => {
    const wrapper = mount(AudienceGrowthChart, {
      props: { data: makeData() },
    })
    expect(wrapper.text()).toContain('dashboard.audienceGrowth.milestone')
  })

  it('renders y-axis tick labels', () => {
    const wrapper = mount(AudienceGrowthChart, {
      props: { data: makeData() },
    })
    // Should have 5 y-axis ticks (0-4)
    const yTicks = wrapper.findAll('text[text-anchor="end"]')
    expect(yTicks.length).toBeGreaterThanOrEqual(5)
  })

  it('renders x-axis date labels', () => {
    const wrapper = mount(AudienceGrowthChart, {
      props: { data: makeData() },
    })
    expect(wrapper.text()).toContain('Jan')
    expect(wrapper.text()).toContain('Jun')
  })

  it('handles minimal data (less than 2 points)', () => {
    const wrapper = mount(AudienceGrowthChart, {
      props: { data: [{ date: 'Jan', followers: 5000 }] },
    })
    // Should still render section, SVG has no line
    expect(wrapper.find('section').exists()).toBe(true)
    const svg = wrapper.find('svg')
    expect(svg.exists()).toBe(true)
  })

  it('handles empty data', () => {
    const wrapper = mount(AudienceGrowthChart, {
      props: { data: [] },
    })
    expect(wrapper.find('section').exists()).toBe(true)
  })

  it('formats tooltip values with formatNumber', () => {
    const wrapper = mount(AudienceGrowthChart, {
      props: { data: makeData() },
    })
    // The component uses formatNumber internally; SVG renders with numbers
    const svg = wrapper.find('svg')
    expect(svg.text()).toMatch(/[0-9,]+/)
  })
})
