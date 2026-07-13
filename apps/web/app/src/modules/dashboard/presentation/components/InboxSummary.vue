<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InboxItem, Platform } from '@modules/dashboard/domain/dashboard.types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

const props = defineProps<{
  items: InboxItem[]
}>()

const { t } = useI18n()

type InboxType = InboxItem['type']

const platformLabels: Record<Platform, string> = {
  linkedin: 'LinkedIn',
  twitter: 'X',
  bluesky: 'Bluesky',
  threads: 'Threads',
}

const typeLabels: Record<InboxType, string> = {
  comment: 'dashboard.inbox.comment',
  mention: 'dashboard.inbox.mention',
  message: 'dashboard.inbox.message',
  lead: 'dashboard.inbox.lead',
}

const typeIcons: Record<InboxType, string> = {
  comment: '\u2026',
  mention: '@',
  message: '\u2709',
  lead: '\u2192',
}

const typeCounts = computed(() => {
  const counts: Record<InboxType, number> = { comment: 0, mention: 0, message: 0, lead: 0 }
  for (const item of props.items) {
    counts[item.type]++
  }
  return counts
})

const typeEntries = computed(() => {
  return (Object.entries(typeCounts.value) as [InboxType, number][]).filter(
    ([, count]) => count > 0,
  )
})

const highPriorityCount = computed(() => {
  return props.items.filter((item) => item.priority === 'high').length
})

const platformsByType = computed(() => {
  const map: Record<InboxType, Platform[]> = { comment: [], mention: [], message: [], lead: [] }
  for (const item of props.items) {
    if (!map[item.type].includes(item.platform)) {
      map[item.type].push(item.platform)
    }
  }
  return map
})

const isHighType = (type: InboxType): boolean => {
  return type === 'lead'
}

const cardAccent = (type: InboxType): string => {
  if (isHighType(type)) return 'border-l-[var(--warning-color)]'
  return ''
}
</script>

<template>
  <section class="space-y-4" aria-labelledby="section-inbox">
    <div class="flex items-center justify-between border-b border-[var(--border-color)] pb-4">
      <div>
        <h3
          id="section-inbox"
          class="font-[var(--font-space-mono)] text-xs font-bold tracking-[0.08em] text-[var(--text-display)] uppercase"
        >
          {{ t('dashboard.inbox.title') }}
        </h3>
        <p class="text-[11px] text-[var(--text-secondary)] mt-1">
          {{ t('dashboard.inbox.subtitle') }}
        </p>
      </div>
      <div v-if="highPriorityCount > 0" class="flex items-center gap-1.5">
        <span class="w-2 h-2 rounded-full bg-[var(--warning-color)]" aria-hidden="true" />
        <span class="text-[10px] text-[var(--warning-color)] font-[var(--font-space-mono)] tabular-nums">
          {{ highPriorityCount }}
        </span>
      </div>
    </div>

    <p
      v-if="items.length === 0"
      class="text-sm text-[var(--text-secondary)] text-center py-8"
    >
      {{ t('dashboard.inbox.noItems') }}
    </p>

    <template v-else>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <div
          v-for="[type, count] in typeEntries"
          :key="type"
          :class="[
            'rounded-lg bg-[var(--background-surface)] border border-[var(--border-color)] p-3 border-l-2',
            cardAccent(type),
          ]"
        >
          <div class="flex items-center justify-between mb-2">
            <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)] uppercase tracking-wider">
              {{ t(typeLabels[type]) }}
            </span>
            <span
              class="text-lg font-semibold tabular-nums"
              :class="isHighType(type) ? 'text-[var(--warning-color)]' : 'text-[var(--text-display)]'"
            >
              {{ count }}
            </span>
          </div>

          <div class="flex items-center gap-2 mt-1">
            <span
              class="w-7 h-7 rounded-md bg-[var(--background-primary)] border border-[var(--border-color)] flex items-center justify-center text-xs text-[var(--text-secondary)] font-[var(--font-space-mono)] shrink-0"
              aria-hidden="true"
            >
              {{ typeIcons[type] }}
            </span>
            <div class="flex items-center gap-1 flex-wrap min-w-0">
              <Badge
                v-for="platform in platformsByType[type]"
                :key="platform"
                variant="outline"
                class="text-[9px] h-4 px-1.5"
              >
                {{ platformLabels[platform] }}
              </Badge>
            </div>
          </div>
        </div>
      </div>

      <div class="flex justify-end pt-1">
        <Button variant="outline" size="sm" class="text-[10px] font-[var(--font-space-mono)] uppercase tracking-wider">
          {{ t('dashboard.inbox.viewAll') }}
        </Button>
      </div>
    </template>
  </section>
</template>
