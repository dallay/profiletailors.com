<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PostingTimeSlot } from '@/lib/types/dashboard'

type Props = {
  slots: PostingTimeSlot[]
}

const props = defineProps<Props>()
const { t } = useI18n()

const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const dayKeys: Record<string, string> = {
  Mon: 'dashboard.postingTimes.mon',
  Tue: 'dashboard.postingTimes.tue',
  Wed: 'dashboard.postingTimes.wed',
  Thu: 'dashboard.postingTimes.thu',
  Fri: 'dashboard.postingTimes.fri',
  Sat: 'dashboard.postingTimes.sat',
  Sun: 'dashboard.postingTimes.sun',
}

// Build a lookup map: `day-hour` -> score
const scoreMap = computed(() => {
  const map = new Map<string, number>()
  for (const slot of props.slots) {
    map.set(`${slot.day}-${slot.hour}`, slot.score)
  }
  return map
})

function getScore(day: string, hour: number): number {
  return scoreMap.value.get(`${day}-${hour}`) ?? 0
}

function getHeatColor(score: number): string {
  // Interpolate from low to high using CSS variables
  const opacity = Math.max(0.05, score / 100)
  return `color-mix(in srgb, var(--heatmap-high) ${Math.round(opacity * 100)}%, var(--heatmap-low))`
}

// Show every 3 hours on the axis
const hourLabels = [0, 3, 6, 9, 12, 15, 18, 21]
</script>

<template>
  <div class="overflow-x-auto">
    <div class="min-w-[480px]">
      <div class="flex ml-10 mb-1">
        <div
          v-for="h in hourLabels"
          :key="h"
          class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)]"
          :style="{ width: `${(24 / hourLabels.length) * (480 - 40) / 24}px` }"
        >
          {{ `${h}:00` }}
        </div>
      </div>
      <div v-for="day in days" :key="day" class="flex items-center gap-1 mb-1">
        <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)] w-8 text-right shrink-0 uppercase tracking-wider">
          {{ t(dayKeys[day] ?? '') }}
        </span>
        <div class="flex gap-px flex-1">
          <div
            v-for="hour in 24"
            :key="hour"
            class="flex-1 aspect-square rounded-[2px] transition-colors duration-200"
            :style="{ backgroundColor: getHeatColor(getScore(day, hour - 1)) }"
            :title="`${t(dayKeys[day] ?? '')} ${hour - 1}:00 — ${getScore(day, hour - 1)}`"
          />
        </div>
      </div>
      <div class="flex items-center gap-2 mt-3 ml-10">
        <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)] uppercase">
          {{ t('dashboard.postingTimes.low') }}
        </span>
        <div class="flex gap-px">
          <div
            v-for="level in 5"
            :key="level"
            class="w-4 h-2 rounded-[1px]"
            :style="{ backgroundColor: getHeatColor(level * 20) }"
          />
        </div>
        <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)] uppercase">
          {{ t('dashboard.postingTimes.high') }}
        </span>
      </div>
    </div>
  </div>
</template>
