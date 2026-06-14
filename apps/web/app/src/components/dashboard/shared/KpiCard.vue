<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { KpiMetric } from '@/lib/types/dashboard'
import { formatDelta } from '@/lib/formatters'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import SparklineChart from './SparklineChart.vue'

type Props = {
  metric: KpiMetric
}

const props = defineProps<Props>()
const { t } = useI18n()

const deltaClass = computed(() => {
  if (props.metric.trend === 'up') return 'text-[var(--success-color)]'
  if (props.metric.trend === 'down') return 'text-[var(--error-color)]'
  return 'text-[var(--text-secondary)]'
})

const deltaIcon = computed(() => {
  if (props.metric.trend === 'up') return '\u2191'
  if (props.metric.trend === 'down') return '\u2193'
  return '\u2192'
})
</script>

<template>
  <Card size="sm">
    <CardHeader class="pb-0">
      <CardTitle class="text-[var(--text-secondary)] font-[var(--font-space-mono)] text-[11px] uppercase tracking-[0.08em]">
        {{ t(metric.label) }}
      </CardTitle>
    </CardHeader>
    <CardContent>
      <div class="flex items-end justify-between gap-3">
        <div class="flex-1 min-w-0">
          <p class="text-2xl font-semibold tracking-tight text-[var(--text-display)]">
            {{ metric.value }}
          </p>
          <p :class="['text-xs font-medium mt-1 flex items-center gap-1', deltaClass]">
            <span aria-hidden="true">{{ deltaIcon }}</span>
            <span>{{ formatDelta(metric.delta) }}</span>
            <span class="text-[var(--text-secondary)] font-normal">{{ t(metric.deltaLabel) }}</span>
          </p>
        </div>
        <div class="shrink-0">
          <SparklineChart :data="metric.sparklineData" />
        </div>
      </div>
    </CardContent>
  </Card>
</template>
