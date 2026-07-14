import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import GrowthScore from './GrowthScore.vue'
import type { GrowthScore as GrowthScoreType } from '@modules/dashboard/domain/dashboard.types'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

function makeScore(overrides: Partial<GrowthScoreType> = {}): GrowthScoreType {
  return {
    overall: 74,
    breakdown: { consistency: 82, engagement: 71, growth: 68, reach: 75 },
    topOpportunity: 'Increase posting frequency on LinkedIn Tuesdays',
    trend: 'improving',
    ...overrides,
  }
}

describe('GrowthScore', () => {
  it('renders the title and subtitle', () => {
    const wrapper = mount(GrowthScore, {
      props: { score: makeScore() },
    })
    expect(wrapper.text()).toContain('dashboard.growthScore.title')
    expect(wrapper.text()).toContain('dashboard.growthScore.subtitle')
  })

  it('renders the overall score value', () => {
    const wrapper = mount(GrowthScore, {
      props: { score: makeScore({ overall: 74 }) },
    })
    expect(wrapper.text()).toContain('74')
  })

  it('renders the trend label for improving', () => {
    const wrapper = mount(GrowthScore, {
      props: { score: makeScore({ trend: 'improving' }) },
    })
    expect(wrapper.text()).toContain('dashboard.growthScore.improving')
  })

  it('renders the trend label for declining', () => {
    const wrapper = mount(GrowthScore, {
      props: { score: makeScore({ trend: 'declining' }) },
    })
    expect(wrapper.text()).toContain('dashboard.growthScore.declining')
  })

  it('renders all 4 breakdown bars', () => {
    const wrapper = mount(GrowthScore, {
      props: { score: makeScore() },
    })
    expect(wrapper.text()).toContain('dashboard.growthScore.breakdown.consistency')
    expect(wrapper.text()).toContain('dashboard.growthScore.breakdown.engagement')
    expect(wrapper.text()).toContain('dashboard.growthScore.breakdown.growth')
    expect(wrapper.text()).toContain('dashboard.growthScore.breakdown.reach')
  })

  it('renders the top opportunity section', () => {
    const wrapper = mount(GrowthScore, {
      props: {
        score: makeScore({ topOpportunity: 'Increase posting frequency on LinkedIn Tuesdays' }),
      },
    })
    expect(wrapper.text()).toContain('dashboard.growthScore.topOpportunity')
    expect(wrapper.text()).toContain('Increase posting frequency on LinkedIn Tuesdays')
  })

  it('renders ScoreGauge component', () => {
    const wrapper = mount(GrowthScore, {
      props: { score: makeScore() },
    })
    const gauge = wrapper.findComponent({ name: 'ScoreGauge' })
    expect(gauge.exists()).toBe(true)
  })
})
