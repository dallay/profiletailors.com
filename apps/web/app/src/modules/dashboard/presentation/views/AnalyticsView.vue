<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  BarChart2,
  Download,
  Eye,
  MousePointerClick,
  TrendingUp,
  UserPlus,
  Clock,
} from '@lucide/vue'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Input } from '@/components/ui/input'
import SocialProviderIcon from '@shared/components/SocialProviderIcon.vue'
import { useAnalyticsStore } from '@modules/analytics/infrastructure/analytics.store'
import type { DateRangePreset } from '@modules/analytics/domain/types'

const { t } = useI18n()
const store = useAnalyticsStore()

onMounted(() => {
  store.refresh()
})

watch(() => store.activeDateRange, () => {
  store.refresh()
})

const DAYS_OF_WEEK = computed(() => [
  t('analytics.days.sun'),
  t('analytics.days.mon'),
  t('analytics.days.tue'),
  t('analytics.days.wed'),
  t('analytics.days.thu'),
  t('analytics.days.fri'),
  t('analytics.days.sat'),
])

const barMax = computed(() => {
  const metrics = store.overview?.dailyMetrics ?? []
  return Math.max(1, ...metrics.map((m) => m.impressions))
})

function barHeight(impressions: number): string {
  const pct = (impressions / barMax.value) * 100
  return `${Math.max(4, pct)}%`
}

function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`
  return String(n)
}

function formatRate(r: number): string {
  return `${r.toFixed(2)}%`
}

function onStartDateChange(event: Event): void {
  store.setCustomRange((event.target as HTMLInputElement).value, store.customEnd)
}

function onEndDateChange(event: Event): void {
  store.setCustomRange(store.customStart, (event.target as HTMLInputElement).value)
}

function previewText(post: { title: string | null; bodyText: string | null }): string {
  return post.title ?? post.bodyText?.slice(0, 60) ?? '—'
}
</script>

<template>
  <div class="mx-auto w-full max-w-7xl space-y-10">

    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4">
      <div class="space-y-1">
        <h2 class="text-3xl font-light tracking-tight text-text-display">
          {{ $t('nav.analytics') }}
        </h2>
        <p class="text-sm text-text-secondary">
          {{ $t('analytics.subtitle') }}
        </p>
      </div>

      <!-- Date range controls -->
      <div class="flex items-center gap-2">
        <Select
          :model-value="store.preset"
          @update:model-value="(v) => store.setPreset(v as DateRangePreset)"
        >
          <SelectTrigger class="w-36 font-mono text-[11px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="last7">{{ $t('analytics.last7') }}</SelectItem>
            <SelectItem value="last30">{{ $t('analytics.last30') }}</SelectItem>
            <SelectItem value="last90">{{ $t('analytics.last90') }}</SelectItem>
            <SelectItem value="custom">{{ $t('analytics.custom') }}</SelectItem>
          </SelectContent>
        </Select>

        <template v-if="store.preset === 'custom'">
          <Input
            type="date"
            :model-value="store.customStart"
            class="w-36 font-mono text-[11px]"
            @change="onStartDateChange"
          />
          <span class="text-text-secondary font-mono text-[11px]">→</span>
          <Input
            type="date"
            :model-value="store.customEnd"
            class="w-36 font-mono text-[11px]"
            @change="onEndDateChange"
          />
        </template>

        <Button
          variant="outline"
          size="sm"
          :disabled="store.exporting"
          class="font-mono text-[11px] gap-2"
          @click="store.exportCsv()"
        >
          <Download class="size-3" />
          {{ store.exporting ? $t('analytics.exporting') : $t('analytics.exportCsv') }}
        </Button>
      </div>
    </div>

    <!-- Error banner -->
    <div
      v-if="store.error"
      class="rounded border border-red-500/30 bg-red-500/10 px-4 py-3 font-mono text-[11px] text-red-400"
    >
      {{ store.error }}
    </div>

    <!-- Overview metrics cards -->
    <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
      <Card>
        <CardContent class="p-4 space-y-1">
          <div class="flex items-center gap-2 text-text-secondary">
            <Eye class="size-3" />
            <span class="font-mono text-[9px] uppercase">{{ $t('analytics.impressions') }}</span>
          </div>
          <div class="text-2xl font-light tracking-tight text-text-display">
            <span v-if="store.loadingOverview" class="opacity-30">—</span>
            <span v-else>{{ formatNumber(store.overview?.totalImpressions ?? 0) }}</span>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent class="p-4 space-y-1">
          <div class="flex items-center gap-2 text-text-secondary">
            <TrendingUp class="size-3" />
            <span class="font-mono text-[9px] uppercase">{{ $t('analytics.engagements') }}</span>
          </div>
          <div class="text-2xl font-light tracking-tight text-text-display">
            <span v-if="store.loadingOverview" class="opacity-30">—</span>
            <span v-else>{{ formatNumber(store.overview?.totalEngagements ?? 0) }}</span>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent class="p-4 space-y-1">
          <div class="flex items-center gap-2 text-text-secondary">
            <BarChart2 class="size-3" />
            <span class="font-mono text-[9px] uppercase">{{ $t('analytics.engagementRate') }}</span>
          </div>
          <div class="text-2xl font-light tracking-tight text-text-display">
            <span v-if="store.loadingOverview" class="opacity-30">—</span>
            <span v-else>{{ formatRate(store.overview?.engagementRate ?? 0) }}</span>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent class="p-4 space-y-1">
          <div class="flex items-center gap-2 text-text-secondary">
            <MousePointerClick class="size-3" />
            <span class="font-mono text-[9px] uppercase">{{ $t('analytics.clicks') }}</span>
          </div>
          <div class="text-2xl font-light tracking-tight text-text-display">
            <span v-if="store.loadingOverview" class="opacity-30">—</span>
            <span v-else>{{ formatNumber(store.overview?.totalClicks ?? 0) }}</span>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent class="p-4 space-y-1">
          <div class="flex items-center gap-2 text-text-secondary">
            <UserPlus class="size-3" />
            <span class="font-mono text-[9px] uppercase">{{ $t('analytics.newFollowers') }}</span>
          </div>
          <div class="text-2xl font-light tracking-tight text-text-display">
            <span v-if="store.loadingOverview" class="opacity-30">—</span>
            <span v-else>{{ formatNumber(store.overview?.newFollowers ?? 0) }}</span>
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- Trend chart + Best times -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">

      <!-- Daily impressions bar chart -->
      <Card class="lg:col-span-2">
        <CardHeader class="p-0 border-b border-border-subtle pb-4 flex flex-row items-center justify-between">
          <CardTitle class="label-mono text-text-display text-[10px]">
            {{ $t('analytics.dailyTrend') }}
          </CardTitle>
          <span class="font-mono text-[9px] text-text-secondary uppercase">[ {{ $t('analytics.impressions') }} ]</span>
        </CardHeader>
        <CardContent class="p-0 mt-6">
          <div
            v-if="store.loadingOverview"
            class="h-40 flex items-center justify-center font-mono text-[10px] text-text-secondary uppercase"
          >
            {{ $t('analytics.loading') }}
          </div>
          <div
            v-else-if="!store.overview?.dailyMetrics?.length"
            class="h-40 flex items-center justify-center font-mono text-[10px] text-text-secondary uppercase"
          >
            {{ $t('analytics.noData') }}
          </div>
          <div v-else class="h-40 flex items-end gap-px overflow-hidden">
            <div
              v-for="metric in store.overview.dailyMetrics"
              :key="metric.date"
              class="flex-1 group relative"
            >
              <div
                class="w-full bg-border-visible group-hover:bg-text-display transition-colors rounded-t-sm"
                :style="{ height: barHeight(metric.impressions) }"
                :title="`${metric.date}: ${metric.impressions} impressions`"
              />
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- Best times -->
      <Card>
        <CardHeader class="p-0 border-b border-border-subtle pb-4 flex flex-row items-center gap-2">
          <Clock class="size-3 text-text-secondary" />
          <CardTitle class="label-mono text-text-display text-[10px]">
            {{ $t('analytics.bestTimes') }}
          </CardTitle>
        </CardHeader>
        <CardContent class="p-0 mt-4 space-y-2">
          <div
            v-if="store.loadingBestTimes"
            class="font-mono text-[10px] text-text-secondary uppercase"
          >
            {{ $t('analytics.loading') }}
          </div>
          <div
            v-else-if="!store.bestTimes?.slots?.length"
            class="font-mono text-[10px] text-text-secondary uppercase"
          >
            {{ $t('analytics.noData') }}
          </div>
          <template v-else>
            <div
              v-for="(slot, i) in store.bestTimes.slots.slice(0, 5)"
              :key="i"
              class="flex items-center justify-between font-mono text-[10px]"
            >
              <span class="text-text-secondary">
                {{ DAYS_OF_WEEK[slot.dayOfWeek] }}
              </span>
              <span class="text-text-display">
                {{ String(slot.hour).padStart(2, '0') }}:00
              </span>
            </div>
          </template>
        </CardContent>
      </Card>
    </div>

    <!-- Post analytics table -->
    <Card>
      <CardHeader class="p-0 border-b border-border-subtle pb-4">
        <CardTitle class="label-mono text-text-display text-[10px]">
          {{ $t('analytics.postPerformance') }}
        </CardTitle>
      </CardHeader>
      <CardContent class="p-0 mt-4">
        <div
          v-if="store.loadingPosts"
          class="py-8 text-center font-mono text-[10px] text-text-secondary uppercase"
        >
          {{ $t('analytics.loading') }}
        </div>
        <div
          v-else-if="!store.postAnalytics?.posts?.length"
          class="py-8 text-center font-mono text-[10px] text-text-secondary uppercase"
        >
          {{ $t('analytics.noPostsInPeriod') }}
        </div>
        <table v-else class="w-full font-mono text-[10px]">
          <thead>
            <tr class="border-b border-border-subtle text-text-secondary uppercase">
              <th class="py-2 pr-4 text-left font-normal">{{ $t('analytics.post') }}</th>
              <th class="py-2 pr-4 text-right font-normal">{{ $t('analytics.impressions') }}</th>
              <th class="py-2 pr-4 text-right font-normal">{{ $t('analytics.clicks') }}</th>
              <th class="py-2 pr-4 text-right font-normal">{{ $t('analytics.engagements') }}</th>
              <th class="py-2 text-right font-normal">{{ $t('analytics.engagementRate') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="post in store.postAnalytics.posts"
              :key="post.postId"
              class="border-b border-border-subtle/50 hover:bg-surface-raised transition-colors"
            >
              <td class="py-2 pr-4">
                <div class="flex items-center gap-2">
                  <SocialProviderIcon :provider="post.provider" class="size-3 shrink-0" />
                  <span class="truncate max-w-50 text-text-primary">{{ previewText(post) }}</span>
                </div>
              </td>
              <td class="py-2 pr-4 text-right text-text-secondary">{{ formatNumber(post.impressions) }}</td>
              <td class="py-2 pr-4 text-right text-text-secondary">{{ formatNumber(post.clicks) }}</td>
              <td class="py-2 pr-4 text-right text-text-secondary">{{ formatNumber(post.engagements) }}</td>
              <td class="py-2 text-right text-text-secondary">{{ formatRate(post.engagementRate) }}</td>
            </tr>
          </tbody>
        </table>

        <!-- Pagination -->
        <div
          v-if="store.postAnalytics && (store.postAnalytics.total > store.postAnalytics.size)"
          class="mt-4 flex items-center justify-between font-mono text-[10px] text-text-secondary"
        >
          <span>
            {{ $t('analytics.showing', {
              from: (store.postAnalytics.page * store.postAnalytics.size) + 1,
              to: Math.min((store.postAnalytics.page + 1) * store.postAnalytics.size, store.postAnalytics.total),
              total: store.postAnalytics.total,
            }) }}
          </span>
          <div class="flex gap-2">
            <Button
              variant="ghost"
              size="sm"
              class="text-[10px]"
              :disabled="store.postAnalytics.page === 0"
              @click="store.fetchPostAnalytics(store.postAnalytics.page - 1)"
            >
              ←
            </Button>
            <Button
              variant="ghost"
              size="sm"
              class="text-[10px]"
              :disabled="(store.postAnalytics.page + 1) * store.postAnalytics.size >= store.postAnalytics.total"
              @click="store.fetchPostAnalytics(store.postAnalytics.page + 1)"
            >
              →
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>

  </div>
</template>
