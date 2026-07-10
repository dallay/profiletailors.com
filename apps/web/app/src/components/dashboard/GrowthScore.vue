<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { GrowthScore as GrowthScoreType } from '@/lib/types/dashboard'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import ScoreGauge from './shared/ScoreGauge.vue'

const props = defineProps<{
  score: GrowthScoreType
}>()

const { t } = useI18n()

const trendLabel = computed(() => {
  return t(`dashboard.growthScore.${props.score.trend}`)
})

const trendIcon = computed(() => {
  if (props.score.trend === 'improving') return '\u2191'
  if (props.score.trend === 'declining') return '\u2193'
  return '\u2192'
})

const trendClass = computed(() => {
  if (props.score.trend === 'improving') return 'text-[var(--success-color)]'
  if (props.score.trend === 'declining') return 'text-[var(--error-color)]'
  return 'text-[var(--text-secondary)]'
})

const breakdownEntries = computed(() => {
  return Object.entries(props.score.breakdown) as [
    string,
    number,
  ][]
})

const barColor = (value: number) => {
  if (value >= 80) return 'var(--success-color)'
  if (value >= 50) return 'var(--warning-color)'
  return 'var(--error-color)'
}
</script>

<template>
  <Card aria-labelledby="section-growth">
    <CardHeader>
      <div class="flex items-center justify-between">
        <div>
          <CardTitle
            id="section-growth"
            class="font-[var(--font-space-mono)] text-xs font-bold tracking-[0.08em] text-[var(--text-secondary)] uppercase"
          >
            {{ t('dashboard.growthScore.title') }}
          </CardTitle>
          <p class="text-[11px] text-[var(--text-secondary)] mt-1">
            {{ t('dashboard.growthScore.subtitle') }}
          </p>
        </div>
        <Badge :variant="score.trend === 'improving' ? 'outline' : 'secondary'" :class="trendClass">
          <span aria-hidden="true">{{ trendIcon }}</span>
          {{ trendLabel }}
        </Badge>
      </div>
    </CardHeader>

    <CardContent>
      <div class="flex flex-col items-center gap-6">
        <ScoreGauge :score="score.overall" :size="140" :stroke-width="10" />

        <div class="w-full space-y-3">
          <div
            v-for="([key, value]) in breakdownEntries"
            :key="key"
            class="space-y-1"
          >
            <div class="flex items-center justify-between">
              <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)] uppercase tracking-wider">
                {{ t(`dashboard.growthScore.breakdown.${key}`) }}
              </span>
              <span class="text-xs font-medium text-[var(--text-display)] tabular-nums">
                {{ value }}
              </span>
            </div>
            <div class="h-1.5 rounded-full bg-[var(--background-surface)] overflow-hidden">
              <div
                class="h-full rounded-full transition-all duration-500"
                :style="{ width: `${value}%`, backgroundColor: barColor(value) }"
              />
            </div>
          </div>
        </div>

        <div
          v-if="score.topOpportunity"
          class="w-full rounded-lg bg-[var(--background-primary)] border border-[var(--border-color)] p-3"
        >
          <p class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)] uppercase tracking-wider mb-1">
            {{ t('dashboard.growthScore.topOpportunity') }}
          </p>
          <p class="text-sm text-[var(--text-body)]">
            {{ score.topOpportunity }}
          </p>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
