import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SparklineChart from './SparklineChart.vue'

describe('SparklineChart', () => {
  it('renders an SVG with the correct dimensions', () => {
    const wrapper = mount(SparklineChart, {
      props: { data: [10, 20, 30, 25, 35], width: 80, height: 32 },
    })
    const svg = wrapper.find('svg')
    expect(svg.exists()).toBe(true)
    expect(svg.attributes('width')).toBe('80')
    expect(svg.attributes('height')).toBe('32')
  })

  it('renders a path element when data has enough points', () => {
    const wrapper = mount(SparklineChart, {
      props: { data: [10, 20, 30] },
    })
    const paths = wrapper.findAll('path')
    expect(paths.length).toBeGreaterThanOrEqual(1)
  })

  it('renders no path when data has fewer than 2 points', () => {
    const wrapper = mount(SparklineChart, {
      props: { data: [10] },
    })
    const paths = wrapper.findAll('path')
    expect(paths).toHaveLength(0)
  })

  it('renders no path when data is empty', () => {
    const wrapper = mount(SparklineChart, {
      props: { data: [] },
    })
    const paths = wrapper.findAll('path')
    expect(paths).toHaveLength(0)
  })

  it('applies custom stroke and fill colors', () => {
    const wrapper = mount(SparklineChart, {
      props: {
        data: [10, 20, 30],
        strokeColor: 'var(--success-color)',
        fillColor: 'var(--sparkline-fill)',
      },
    })
    const linePath = wrapper.find('path:last-of-type')
    expect(linePath.attributes('stroke')).toBe('var(--success-color)')
  })

  it('uses positive trend color when last value > first value', () => {
    const wrapper = mount(SparklineChart, {
      props: { data: [10, 30] },
    })
    const linePath = wrapper.find('path:last-of-type')
    expect(linePath.attributes('stroke')).toBe('var(--success-color)')
  })

  it('uses error trend color when last value < first value', () => {
    const wrapper = mount(SparklineChart, {
      props: { data: [30, 10] },
    })
    const linePath = wrapper.find('path:last-of-type')
    expect(linePath.attributes('stroke')).toBe('var(--error-color)')
  })
})
