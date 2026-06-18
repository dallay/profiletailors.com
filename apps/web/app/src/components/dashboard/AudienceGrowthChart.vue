<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AudienceGrowthPoint } from '@/lib/types/dashboard'
import { formatNumber } from '@/lib/formatters'
import ChartContainer from '@/components/ui/chart/ChartContainer.vue'

type Props = {
  data: AudienceGrowthPoint[]
}

const props = defineProps<Props>()
const { t } = useI18n()

const chartConfig = {
  followers: {
    label: 'Followers',
    color: 'var(--chart-line)',
  },
}

const MARGIN = { top: 20, right: 20, bottom: 36, left: 48 }
const hoveredIndex = ref<number | null>(null)

const chartDims = { width: 600, height: 240 }

const hasData = computed(() => props.data.length >= 2)

const xScale = computed(() => {
  const n = props.data.length
  if (n < 2) return [] as number[]
  return props.data.map((_, i) => {
    return MARGIN.left + (i / (n - 1)) * (chartDims.width - MARGIN.left - MARGIN.right)
  })
})

const yMin = computed(() => {
  if (props.data.length === 0) return 0
  return Math.min(...props.data.map((d) => d.followers))
})

const yMax = computed(() => {
  if (props.data.length === 0) return 1
  return Math.max(...props.data.map((d) => d.followers))
})

const yRange = computed(() => yMax.value - yMin.value || 1)

const yScale = computed(() => {
  const h = chartDims.height - MARGIN.top - MARGIN.bottom
  return props.data.map((d) => {
    return MARGIN.top + h - ((d.followers - yMin.value) / yRange.value) * h
  })
})

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
  const h = chartDims.height - MARGIN.bottom
  const firstX = xs[0]!
  const lastX = xs[xs.length - 1]!
  const points = xs.map((x, i) => `${x.toFixed(1)},${(ys[i] ?? 0).toFixed(1)}`).join('L')
  return `M${firstX.toFixed(1)},${h}L${points}L${lastX.toFixed(1)},${h}Z`
})

const milestones = computed(() => {
  const xs = xScale.value
  const ys = yScale.value
  return props.data
    .map((d, i) => ({ point: d, index: i, x: xs[i] ?? 0, y: ys[i] ?? 0 }))
    .filter((m) => m.point.milestone)
})

// Y-axis ticks: 4 evenly spaced labels
const yTicks = computed(() => {
  const ticks: { value: number; y: number }[] = []
  const h = chartDims.height - MARGIN.top - MARGIN.bottom
  for (let i = 0; i <= 4; i++) {
    const value = yMin.value + (yRange.value * i) / 4
    const y = MARGIN.top + h - (i / 4) * h
    ticks.push({ value: Math.round(value), y })
  }
  return ticks
})

// X-axis labels: first, middle, last
const xLabels = computed(() => {
  const n = props.data.length
  if (n < 2) return [] as { label: string; x: number }[]
  const xs = xScale.value
  const indices = [0, Math.floor(n / 2), n - 1]
  return indices.map((i) => ({
    label: props.data[i]?.date,
    x: xs[i] ?? 0,
  }))
})

function formatTooltipValue(value: number): string {
  return formatNumber(value)
}
</script>

<template>
  <section class="space-y-4" aria-labelledby="section-audience-growth">
    <div class="flex items-center justify-between border-b border-[var(--border-color)] pb-4">
      <h2
        id="section-audience-growth"
        class="font-[var(--font-space-mono)] text-xs font-bold tracking-[0.08em] text-[var(--text-display)] uppercase"
      >
        {{ t('dashboard.audienceGrowth.title') }}
      </h2>
      <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)] uppercase tracking-wider">
        {{ t('dashboard.audienceGrowth.subtitle') }}
      </span>
    </div>

    <ChartContainer :config="chartConfig" class="aspect-auto h-[280px] w-full">
      <template #default="{ id }">
        <div class="relative w-full h-full" :data-chart="id">
          <!-- SVG chart -->
          <svg
            :viewBox="`0 0 ${chartDims.width} ${chartDims.height}`"
            class="w-full h-full overflow-visible"
            preserveAspectRatio="xMidYMid meet"
            role="img"
            aria-label="Audience growth line chart"
            @mouseleave="hoveredIndex = null"
          >
            <!-- Y-axis grid lines and labels -->
            <g v-for="tick in yTicks" :key="tick.y">
              <line
                :x1="MARGIN.left"
                :y1="tick.y"
                :x2="chartDims.width - MARGIN.right"
                :y2="tick.y"
                stroke="var(--border-color)"
                stroke-width="1"
                stroke-dasharray="3,3"
              />
              <text
                :x="MARGIN.left - 8"
                :y="tick.y + 4"
                text-anchor="end"
                class="text-[10px] fill-[var(--text-secondary)] font-[var(--font-space-mono)]"
              >
                {{ formatTooltipValue(tick.value) }}
              </text>
            </g>

            <!-- X-axis labels -->
            <g v-for="(label, i) in xLabels" :key="i">
              <text
                :x="label.x"
                :y="chartDims.height - 8"
                text-anchor="middle"
                class="text-[10px] fill-[var(--text-secondary)] font-[var(--font-space-mono)]"
              >
                {{ label.label }}
              </text>
            </g>

            <!-- Area fill -->
            <path
              v-if="areaPath"
              :d="areaPath"
              fill="var(--chart-area)"
              opacity="0.15"
            />
            <!-- Line -->
            <path
              v-if="linePath"
              :d="linePath"
              fill="none"
              stroke="var(--chart-line)"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />

            <!-- Data points -->
            <g
              v-for="(point, i) in xScale"
              :key="i"
              @mouseenter="hoveredIndex = i"
            >
              <circle
                :cx="point"
                :cy="yScale[i]"
                :r="hoveredIndex === i ? 5 : 3"
                :fill="hoveredIndex === i ? 'var(--chart-line)' : 'var(--background-primary)'"
                :stroke="hoveredIndex === i ? 'var(--background-primary)' : 'var(--chart-line)'"
                stroke-width="2"
                class="transition-all duration-200 cursor-pointer"
              />
            </g>

            <!-- Milestone annotations -->
            <g v-for="(m, i) in milestones" :key="i">
              <line
                :x1="m.x"
                :y1="m.y"
                :x2="m.x"
                :y2="chartDims.height - MARGIN.bottom"
                stroke="var(--success-color)"
                stroke-width="1"
                stroke-dasharray="4,3"
              />
              <circle
                :cx="m.x"
                :cy="m.y"
                r="6"
                fill="var(--success-color)"
                stroke="var(--background-primary)"
                stroke-width="2"
              />
              <text
                :x="m.x"
                :y="m.y - 12"
                text-anchor="middle"
                class="text-[9px] fill-[var(--success-color)] font-[var(--font-space-mono)]"
              >
                {{ t('dashboard.audienceGrowth.milestone') }}
              </text>
            </g>
          </svg>

            <!-- Tooltip -->
          <div
            v-if="hasData && hoveredIndex !== null && hoveredIndex < data.length"
            class="absolute bg-[var(--background-surface)] border border-[var(--border-color)] rounded-lg px-3 py-2 text-xs shadow-xl pointer-events-none"
            :style="{
              left: (xScale[hoveredIndex] ?? 0) < chartDims.width / 2
                ? `${((xScale[hoveredIndex] ?? 0) / chartDims.width) * 100 + 2}%`
                : `${((xScale[hoveredIndex] ?? 0) / chartDims.width) * 100 - 20}%`,
              top: `${((yScale[hoveredIndex] ?? 0) / chartDims.height) * 100 - 10}%`,
            }"
          >
            <p class="text-[var(--text-secondary)] font-[var(--font-space-mono)] text-[10px] uppercase tracking-wider">
              {{ data[hoveredIndex]!.date }}
            </p>
            <p class="text-[var(--text-display)] font-semibold tabular-nums mt-0.5">
              {{ formatTooltipValue(data[hoveredIndex]!.followers) }}
            </p>
          </div>
        </div>
      </template>
    </ChartContainer>
  </section>
</template>
