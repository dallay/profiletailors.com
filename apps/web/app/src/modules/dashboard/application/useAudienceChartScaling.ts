import { computed, type Ref } from 'vue'
import type { AudienceGrowthPoint } from '@modules/dashboard/domain/dashboard.types'

const MARGIN = { top: 20, right: 20, bottom: 36, left: 48 }
const CHART_DIMS = { width: 600, height: 240 }

export function useAudienceChartScaling(data: Ref<AudienceGrowthPoint[]>) {
  const yMin = computed(() => {
    if (data.value.length === 0) return 0
    return Math.min(...data.value.map((d) => d.followers))
  })

  const yMax = computed(() => {
    if (data.value.length === 0) return 1
    return Math.max(...data.value.map((d) => d.followers))
  })

  const yRange = computed(() => yMax.value - yMin.value || 1)

  const xScale = computed(() => {
    const n = data.value.length
    if (n < 2) return [] as number[]
    return data.value.map((_, i) => {
      return MARGIN.left + (i / (n - 1)) * (CHART_DIMS.width - MARGIN.left - MARGIN.right)
    })
  })

  const yScale = computed(() => {
    const h = CHART_DIMS.height - MARGIN.top - MARGIN.bottom
    return data.value.map((d) => {
      return MARGIN.top + h - ((d.followers - yMin.value) / yRange.value) * h
    })
  })

  return {
    MARGIN,
    CHART_DIMS,
    yMin,
    yMax,
    yRange,
    xScale,
    yScale,
  }
}
