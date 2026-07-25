import { computed } from 'vue'
import type { AudienceGrowthPoint } from '@modules/dashboard/domain/dashboard.types'

interface ChartScaling {
  MARGIN: { top: number; right: number; bottom: number; left: number }
  CHART_DIMS: { width: number; height: number }
  yMin: globalThis.Ref<number>
  yMax: globalThis.Ref<number>
  yRange: globalThis.Ref<number>
  xScale: globalThis.Ref<number[]>
  yScale: globalThis.Ref<number[]>
}

export function useAudienceChartVisualization(
  data: globalThis.Ref<AudienceGrowthPoint[]>,
  scaling: ChartScaling,
) {
  const { MARGIN, CHART_DIMS, yMin, yRange, xScale, yScale } = scaling

  const hasData = computed(() => data.value.length >= 2)

  const linePath = computed(() => {
    const xs = xScale.value
    const ys = yScale.value
    if (xs.length < 2) return ''
    return xs.map((x, i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${(ys[i] ?? 0).toFixed(1)}`).join('')
  })

  const areaPath = computed(() => {
    const xs = xScale.value
    const ys = yScale.value
    if (xs.length < 2) return ''
    const h = CHART_DIMS.height - MARGIN.bottom
    const firstX = xs.at(0) ?? 0
    const lastX = xs.at(-1) ?? 0
    const points = xs.map((x, i) => `${x.toFixed(1)},${(ys[i] ?? 0).toFixed(1)}`).join('L')
    return `M${firstX.toFixed(1)},${h}L${points}L${lastX.toFixed(1)},${h}Z`
  })

  const milestones = computed(() => {
    const xs = xScale.value
    const ys = yScale.value
    return data.value
      .map((d, i) => ({ point: d, index: i, x: xs[i] ?? 0, y: ys[i] ?? 0 }))
      .filter((m) => m.point.milestone)
  })

  const yTicks = computed(() => {
    const ticks: { value: number; y: number }[] = []
    const h = CHART_DIMS.height - MARGIN.top - MARGIN.bottom
    for (let i = 0; i <= 4; i++) {
      const value = yMin.value + (yRange.value * i) / 4
      const y = MARGIN.top + h - (i / 4) * h
      ticks.push({ value: Math.round(value), y })
    }
    return ticks
  })

  const xLabels = computed(() => {
    const n = data.value.length
    if (n < 2) return [] as { label: string; x: number }[]
    const xs = xScale.value
    const indices = [0, Math.floor(n / 2), n - 1]
    return indices.map((i) => ({
      label: data.value[i]?.date,
      x: xs[i] ?? 0,
    }))
  })

  return {
    hasData,
    linePath,
    areaPath,
    milestones,
    yTicks,
    xLabels,
  }
}
