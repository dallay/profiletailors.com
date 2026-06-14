<script setup lang="ts">
import { computed } from 'vue'

type Props = {
  data: number[]
  width?: number
  height?: number
  strokeColor?: string
  fillColor?: string
}

const props = withDefaults(defineProps<Props>(), {
  width: 80,
  height: 32,
  strokeColor: 'var(--sparkline-stroke)',
  fillColor: 'var(--sparkline-fill)',
})

const pathData = computed(() => {
  const { data, width, height } = props
  if (data.length < 2) return ''

  const min = Math.min(...data)
  const max = Math.max(...data)
  const range = max - min || 1

  const points = data.map((value, i) => {
    const x = (i / (data.length - 1)) * width
    const y = height - ((value - min) / range) * (height - 4) - 2
    return `${x},${y}`
  })

  return `M${points.join('L')}`
})

const areaPath = computed(() => {
  const { data, width, height } = props
  if (data.length < 2) return ''

  const min = Math.min(...data)
  const max = Math.max(...data)
  const range = max - min || 1

  const points = data.map((value, i) => {
    const x = (i / (data.length - 1)) * width
    const y = height - ((value - min) / range) * (height - 4) - 2
    return `${x},${y}`
  })

  return `M0,${height}L${points.join('L')}L${width},${height}Z`
})

const trendColor = computed(() => {
  const data = props.data
  if (data.length < 2) return props.strokeColor
  const last = data[data.length - 1]!
  const first = data[0]!
  if (last > first) return 'var(--success-color)'
  if (last < first) return 'var(--error-color)'
  return props.strokeColor
})
</script>

<template>
  <svg
    :width="width"
    :height="height"
    :viewBox="`0 0 ${width} ${height}`"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <path
      v-if="areaPath"
      :d="areaPath"
      :fill="fillColor"
    />
    <path
      v-if="pathData"
      :d="pathData"
      :stroke="trendColor"
      stroke-width="1.5"
      stroke-linecap="round"
      stroke-linejoin="round"
      fill="none"
    />
  </svg>
</template>
