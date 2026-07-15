<script setup lang="ts">
import type { ChannelPerformance } from '@modules/dashboard/domain/dashboard.types'
import { formatNumber, formatPercent } from '@shared/lib/formatters'

type Props = {
  channel: ChannelPerformance
  maxFollowers: number
}

const props = defineProps<Props>()

const barWidth = Math.round((props.channel.followers / props.maxFollowers) * 100)

const platformLabels: Record<string, string> = {
  linkedin: 'LinkedIn',
  twitter: 'Twitter',
  bluesky: 'Bluesky',
  threads: 'Threads',
}
</script>

<template>
  <div class="flex items-center gap-3 py-2">
    <span class="text-xs text-[var(--text-secondary)] w-16 shrink-0 font-[var(--font-space-mono)] uppercase tracking-wider">
      {{ platformLabels[channel.platform] ?? channel.platform }}
    </span>
    <div class="flex-1 h-2 rounded-full bg-[var(--heatmap-low)] overflow-hidden">
      <div
        class="h-full rounded-full transition-all duration-500"
        :style="{ width: `${barWidth}%`, backgroundColor: channel.color }"
      />
    </div>
    <span class="text-xs text-[var(--text-body)] w-14 text-right tabular-nums">
      {{ formatNumber(channel.followers) }}
    </span>
    <span class="text-[10px] text-[var(--text-secondary)] w-12 text-right tabular-nums">
      {{ formatPercent(channel.engagementRate) }}
    </span>
  </div>
</template>
