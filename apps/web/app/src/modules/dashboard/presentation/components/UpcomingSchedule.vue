<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { ScheduleItem, Platform } from '@modules/dashboard/domain/dashboard.types'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

type Props = {
  items: ScheduleItem[]
}

defineProps<Props>()

const { t } = useI18n()

const statusKeys: Record<string, string> = {
  queued: 'dashboard.upcomingSchedule.queued',
  scheduled: 'dashboard.upcomingSchedule.scheduled',
  published: 'dashboard.upcomingSchedule.published',
}

const statusColors: Record<string, string> = {
  queued: 'text-[var(--warning-color)]',
  scheduled: 'text-[var(--info-color)]',
  published: 'text-[var(--success-color)]',
}

const platformLabels: Record<Platform, string> = {
  linkedin: 'LinkedIn',
  twitter: 'X',
  bluesky: 'Bluesky',
  threads: 'Threads',
}

const platformBadgeColor: Record<Platform, string> = {
  linkedin: 'text-[#0A66C2]',
  twitter: 'text-[#1DA1F2]',
  bluesky: 'text-[#0085FF]',
  threads: 'text-[#E1306C]',
}

function formatTime(isoDate: string): string {
  const date = new Date(isoDate)
  return date.toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}
</script>

<template>
  <Card aria-labelledby="section-upcoming-schedule">
    <CardHeader>
      <CardTitle
        id="section-upcoming-schedule"
        class="font-[var(--font-space-mono)] text-xs font-bold tracking-[0.08em] text-[var(--text-secondary)] uppercase"
      >
        {{ t('dashboard.upcomingSchedule.title') }}
      </CardTitle>
      <p class="text-[11px] text-[var(--text-secondary)] mt-1">
        {{ t('dashboard.upcomingSchedule.subtitle') }}
      </p>
    </CardHeader>

    <CardContent>
      <p
        v-if="items.length === 0"
        class="text-sm text-[var(--text-secondary)] text-center py-8"
      >
        {{ t('dashboard.upcomingSchedule.noItems') }}
      </p>

      <div v-else class="space-y-2">
        <div
          v-for="item in items.slice(0, 5)"
          :key="item.id"
          class="flex items-center gap-3 p-3 rounded-lg bg-[var(--background-primary)] border border-[var(--border-color)]"
        >
          <div class="shrink-0 text-center w-12">
            <p class="text-xs font-semibold text-[var(--text-display)] tabular-nums leading-tight">
              {{ formatTime(item.scheduledFor) }}
            </p>
          </div>

          <div class="w-px h-8 bg-[var(--border-color)] shrink-0" />

          <div class="flex-1 min-w-0">
            <p class="text-sm text-[var(--text-display)] line-clamp-1 leading-snug">
              {{ item.title }}
            </p>
            <div class="flex items-center gap-2 mt-1">
              <span
                :class="[
                  'text-[10px] font-[var(--font-space-mono)] uppercase tracking-wider font-medium',
                  platformBadgeColor[item.platform],
                ]"
              >
                {{ platformLabels[item.platform] }}
              </span>
            </div>
          </div>

          <span
            :class="[
              'text-[10px] font-[var(--font-space-mono)] uppercase tracking-wider shrink-0',
              statusColors[item.status] ?? 'text-[var(--text-secondary)]',
            ]"
          >
            {{ t(statusKeys[item.status] ?? '') }}
          </span>
        </div>
      </div>

      <div class="mt-4 pt-4 border-t border-[var(--border-color)]">
        <Button
          variant="outline"
          size="sm"
          class="w-full h-8 text-[10px] font-[var(--font-space-mono)] uppercase tracking-wider rounded-none"
        >
          {{ t('scheduler.newPost') }}
        </Button>
      </div>
    </CardContent>
  </Card>
</template>
