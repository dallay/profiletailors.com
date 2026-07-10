<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ChannelPerformance } from '@/lib/types/dashboard'
import { formatNumber, formatPercent } from '@/lib/formatters'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

const props = defineProps<{
  channels: ChannelPerformance[]
}>()

const { t } = useI18n()

const maxFollowers = computed(() => {
  if (props.channels.length === 0) return 1
  return Math.max(...props.channels.map((ch) => ch.followers))
})

const platformLabels: Record<string, string> = {
  linkedin: 'LinkedIn',
  twitter: 'X',
  bluesky: 'Bluesky',
  threads: 'Threads',
}

const platformNames: Record<string, string> = {
  linkedin: 'LinkedIn',
  twitter: 'X (Twitter)',
  bluesky: 'Bluesky',
  threads: 'Threads',
}
</script>

<template>
  <Card aria-labelledby="section-cross-channel">
    <CardHeader>
      <CardTitle
        id="section-cross-channel"
        class="font-[var(--font-space-mono)] text-xs font-bold tracking-[0.08em] text-[var(--text-secondary)] uppercase"
      >
        {{ t('dashboard.crossChannel.title') }}
      </CardTitle>
      <p class="text-[11px] text-[var(--text-secondary)] mt-1">
        {{ t('dashboard.crossChannel.subtitle') }}
      </p>
    </CardHeader>

    <CardContent>
      <div class="space-y-1">
        <div
          v-for="channel in channels"
          :key="channel.platform"
          class="space-y-1"
        >
          <div class="flex items-center gap-3 py-2">
            <span class="text-xs text-[var(--text-secondary)] w-16 shrink-0 font-[var(--font-space-mono)] uppercase tracking-wider">
              {{ platformLabels[channel.platform] ?? channel.platform }}
            </span>
            <div class="flex-1 h-2 rounded-full bg-[var(--heatmap-low)] overflow-hidden">
              <div
                class="h-full rounded-full transition-all duration-500"
                :style="{
                  width: `${Math.round((channel.followers / maxFollowers) * 100)}%`,
                  backgroundColor: channel.color,
                }"
              />
            </div>
            <span class="text-xs text-[var(--text-body)] w-14 text-right tabular-nums">
              {{ formatNumber(channel.followers) }}
            </span>
            <span class="text-[10px] text-[var(--text-secondary)] w-12 text-right tabular-nums">
              {{ formatPercent(channel.engagementRate) }}
            </span>
          </div>
          <p class="text-[10px] text-[var(--text-secondary)] pl-0 pb-1 font-[var(--font-space-mono)]">
            {{ t('dashboard.crossChannel.followerContext', {
              count: formatNumber(channel.followers),
              platform: platformNames[channel.platform] ?? channel.platform,
            }) }}
          </p>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
