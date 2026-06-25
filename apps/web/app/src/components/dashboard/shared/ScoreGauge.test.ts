import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ScoreGauge from './ScoreGauge.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

describe('ScoreGauge', () => {
  it('renders the score value', () => {
    const wrapper = mount(ScoreGauge, {
      props: { score: 74 },
    })
    expect(wrapper.text()).toContain('74')
  })

  it('renders the "out of 100" label', () => {
    const wrapper = mount(ScoreGauge, {
      props: { score: 74 },
    })
    expect(wrapper.text()).toContain('dashboard.growthScore.outOf100')
  })

  it('has correct attributes', () => {
    const wrapper = mount(ScoreGauge, {
      props: { score: 74 },
    })
    const meter = wrapper.find('[role="meter"]')
    expect(meter.attributes('aria-valuenow')).toBe('74')
    expect(meter.attributes('aria-valuemin')).toBe('0')
    expect(meter.attributes('aria-valuemax')).toBe('100')
  })

  it('renders an SVG circle for the gauge', () => {
    const wrapper = mount(ScoreGauge, {
      props: { score: 74 },
    })
    const circles = wrapper.findAll('circle')
    expect(circles).toHaveLength(2) // track + fill
  })

  it('applies success color for score >= 80', () => {
    const wrapper = mount(ScoreGauge, {
      props: { score: 85 },
    })
    const fillCircle = wrapper.findAll('circle')[1]!
    expect(fillCircle.attributes('stroke')).toBe('var(--success-color)')
  })

  it('applies warning color for score 50-79', () => {
    const wrapper = mount(ScoreGauge, {
      props: { score: 74 },
    })
    const fillCircle = wrapper.findAll('circle')[1]!
    expect(fillCircle.attributes('stroke')).toBe('var(--warning-color)')
  })

  it('applies error color for score < 50', () => {
    const wrapper = mount(ScoreGauge, {
      props: { score: 42 },
    })
    const fillCircle = wrapper.findAll('circle')[1]!
    expect(fillCircle.attributes('stroke')).toBe('var(--error-color)')
  })

  it('uses custom size and strokeWidth', () => {
    const wrapper = mount(ScoreGauge, {
      props: { score: 74, size: 140, strokeWidth: 10 },
    })
    const svg = wrapper.find('svg')
    expect(svg.attributes('width')).toBe('140')
    expect(svg.attributes('height')).toBe('140')
  })
})
