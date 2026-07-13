<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { PostingTimeSlot } from '@modules/dashboard/domain/dashboard.types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import HeatmapGrid from './shared/HeatmapGrid.vue'

defineProps<{
  slots: PostingTimeSlot[]
}>()

const { t } = useI18n()
</script>

<template>
  <Card aria-labelledby="section-posting-times">
    <CardHeader>
      <CardTitle
        id="section-posting-times"
        class="font-[var(--font-space-mono)] text-xs font-bold tracking-[0.08em] text-[var(--text-secondary)] uppercase"
      >
        {{ t('dashboard.postingTimes.title') }}
      </CardTitle>
      <p class="text-[11px] text-[var(--text-secondary)] mt-1">
        {{ t('dashboard.postingTimes.subtitle') }}
      </p>
    </CardHeader>

    <CardContent>
      <div class="space-y-4">
        <HeatmapGrid :slots="slots" />

        <div class="rounded-lg bg-[var(--background-primary)] border border-[var(--border-color)] p-3">
          <p class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)] uppercase tracking-wider mb-1">
            AI Recommendation
          </p>
          <p class="text-sm text-[var(--text-body)] leading-relaxed">
            {{ t('dashboard.postingTimes.subtitle') }}
          </p>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
