<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

type Props = {
  score: number
  size?: number
  strokeWidth?: number
}

const props = withDefaults(defineProps<Props>(), {
  size: 120,
  strokeWidth: 8,
})

const { t } = useI18n()

const radius = computed(() => (props.size - props.strokeWidth) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const offset = computed(() => circumference.value * (1 - props.score / 100))
const center = computed(() => props.size / 2)

const scoreColor = computed(() => {
  if (props.score >= 80) return 'var(--success-color)'
  if (props.score >= 50) return 'var(--warning-color)'
  return 'var(--error-color)'
})
</script>

<template>
  <div
    class="relative inline-flex items-center justify-center"
    :style="{ width: `${size}px`, height: `${size}px` }"
    role="meter"
    :aria-valuenow="score"
    aria-valuemin="0"
    aria-valuemax="100"
    :aria-label="`${score} ${t('dashboard.growthScore.outOf100')}`"
  >
    <svg
      :width="size"
      :height="size"
      :viewBox="`0 0 ${size} ${size}`"
      class="rotate-[-90deg]"
      aria-hidden="true"
    >
      <!-- Track -->
      <circle
        :cx="center"
        :cy="center"
        :r="radius"
        fill="none"
        :stroke-width="strokeWidth"
        stroke="var(--score-gauge-track)"
      />
      <!-- Fill -->
      <circle
        :cx="center"
        :cy="center"
        :r="radius"
        fill="none"
        :stroke-width="strokeWidth"
        :stroke="scoreColor"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="offset"
        stroke-linecap="round"
        class="transition-all duration-700 ease-out"
      />
    </svg>
    <div class="absolute inset-0 flex flex-col items-center justify-center">
      <span class="text-2xl font-semibold text-[var(--text-display)] tabular-nums">
        {{ score }}
      </span>
      <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)] uppercase tracking-wider">
        {{ t('dashboard.growthScore.outOf100') }}
      </span>
    </div>
  </div>
</template>
