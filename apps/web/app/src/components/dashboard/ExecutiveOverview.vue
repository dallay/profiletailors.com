<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { KpiMetric } from '@/lib/types/dashboard'
import KpiCard from './shared/KpiCard.vue'

defineProps<{
  kpis: KpiMetric[]
}>()

const { t } = useI18n()
</script>

<template>
  <section class="space-y-4" aria-labelledby="section-executive">
    <div class="flex items-center justify-between border-b border-[var(--border-color)] pb-4">
      <h2
        id="section-executive"
        class="font-[var(--font-space-mono)] text-xs font-bold tracking-[0.08em] text-[var(--text-display)] uppercase"
      >
        {{ t('dashboard.executiveOverview.title') }}
      </h2>
      <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)] uppercase tracking-wider">
        {{ t('dashboard.executiveOverview.last30Days') }}
      </span>
    </div>

    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <KpiCard
        v-for="metric in kpis"
        :key="metric.id"
        :metric="metric"
      />
    </div>
  </section>
</template>
