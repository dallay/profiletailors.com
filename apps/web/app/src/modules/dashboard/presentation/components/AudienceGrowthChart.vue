<script setup lang="ts">
import { ref, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AudienceGrowthPoint } from '@modules/dashboard/domain/dashboard.types'
import { formatNumber } from '@shared/lib/formatters'
import { useAudienceChartScaling, useAudienceChartVisualization } from '@modules/dashboard/application'
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

const hoveredIndex = ref<number | null>(null)

// Use composables for chart logic
const scaling = useAudienceChartScaling(
  toRef(props, 'data'),
)
const visualization = useAudienceChartVisualization(
  toRef(props, 'data'),
  scaling,
)
const { MARGIN, CHART_DIMS, xScale, yScale } = scaling
const { hasData, linePath, areaPath, milestones, yTicks, xLabels } = visualization

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
          <svg
            :viewBox="`0 0 ${CHART_DIMS.width} ${CHART_DIMS.height}`"
            class="w-full h-full overflow-visible"
            preserveAspectRatio="xMidYMid meet"
            role="img"
            aria-label="Audience growth line chart"
            @mouseleave="hoveredIndex = null"
          >
            <g v-for="tick in yTicks" :key="tick.y">
              <line
                :x1="MARGIN.left"
                :y1="tick.y"
                :x2="CHART_DIMS.width - MARGIN.right"
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

            <g v-for="(label, i) in xLabels" :key="i">
              <text
                :x="label.x"
                :y="CHART_DIMS.height - 8"
                text-anchor="middle"
                class="text-[10px] fill-[var(--text-secondary)] font-[var(--font-space-mono)]"
              >
                {{ label.label }}
              </text>
            </g>

            <path
              v-if="areaPath"
              :d="areaPath"
              fill="var(--chart-area)"
              opacity="0.15"
            />
            <path
              v-if="linePath"
              :d="linePath"
              fill="none"
              stroke="var(--chart-line)"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />

            <!-- biome-ignore lint/a11y/noStaticElementInteractions: SVG <g> hover for chart tooltip, no semantic alternative -->
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

            <g v-for="(m, i) in milestones" :key="i">
              <line
                :x1="m.x"
                :y1="m.y"
                :x2="m.x"
                :y2="CHART_DIMS.height - MARGIN.bottom"
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

          <div
            v-if="hasData && hoveredIndex !== null && hoveredIndex < data.length"
            class="absolute bg-[var(--background-surface)] border border-[var(--border-color)] rounded-lg px-3 py-2 text-xs shadow-xl pointer-events-none"
            :style="{
              left: (xScale[hoveredIndex] ?? 0) < CHART_DIMS.width / 2
                ? `${((xScale[hoveredIndex] ?? 0) / CHART_DIMS.width) * 100 + 2}%`
                : `${((xScale[hoveredIndex] ?? 0) / CHART_DIMS.width) * 100 - 20}%`,
              top: `${((yScale[hoveredIndex] ?? 0) / CHART_DIMS.height) * 100 - 10}%`,
            }"
          >
            <p class="text-[var(--text-secondary)] font-[var(--font-space-mono)] text-[10px] uppercase tracking-wider">
              {{ data[hoveredIndex]?.date }}
            </p>
            <p class="text-[var(--text-display)] font-semibold tabular-nums mt-0.5">
              {{ formatTooltipValue(data[hoveredIndex]?.followers ?? 0) }}
            </p>
          </div>
        </div>
      </template>
    </ChartContainer>
  </section>
</template>
