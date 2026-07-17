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

  it('renders accessible SVG gauge with score', () => {
    const wrapper = mount(ScoreGauge, {
      props: { score: 74 },
    })
    const svg = wrapper.find('svg[aria-hidden="true"]')
    expect(svg.exists()).toBe(true)
    expect(svg.attributes('width')).toBe('120')
    expect(svg.attributes('height')).toBe('120')
    const circles = svg.findAll('circle')
    expect(circles).toHaveLength(2)
  })

  it('renders an SVG circle for the gauge', () => {
    const wrapper = mount(ScoreGauge, {
      props: { score: 74 },
    })
    const circles = wrapper.findAll('circle')
    expect(circles).toHaveLength(2) // track + fill
  })

  it.each([
    { score: 85, expectedColor: 'var(--success-color)', range: '>= 80' },
    { score: 74, expectedColor: 'var(--warning-color)', range: '50-79' },
    { score: 42, expectedColor: 'var(--error-color)', range: '< 50' },
  ])('applies $expectedColor for score $range', ({ score, expectedColor }) => {
    const wrapper = mount(ScoreGauge, {
      props: { score },
    })
    const fillCircle = wrapper.findAll('circle')[1]!
    expect(fillCircle.attributes('stroke')).toBe(expectedColor)
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
