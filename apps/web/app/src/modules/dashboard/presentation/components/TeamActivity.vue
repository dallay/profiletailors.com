<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TeamActivityEvent, TeamMember } from '@modules/dashboard/domain/dashboard.types'
import { formatRelativeTime } from '@/lib/formatters'

const props = defineProps<{
  events: TeamActivityEvent[]
  members: TeamMember[]
}>()

const { t } = useI18n()

const recentEvents = computed(() => {
  return props.events.slice(0, 5)
})

const memberOnlineMap = computed(() => {
  const map = new Map<string, boolean>()
  for (const member of props.members) {
    map.set(member.id, member.online)
  }
  return map
})

const isOnline = (memberId: string): boolean => {
  return memberOnlineMap.value.get(memberId) ?? false
}

const isEmpty = computed(() => props.events.length === 0)
</script>

<template>
  <section class="space-y-4" aria-labelledby="section-team-activity">
    <div class="flex items-center justify-between border-b border-[var(--border-color)] pb-4">
      <div>
        <h3
          id="section-team-activity"
          class="font-[var(--font-space-mono)] text-xs font-bold tracking-[0.08em] text-[var(--text-display)] uppercase"
        >
          {{ t('dashboard.teamActivity.title') }}
        </h3>
        <p class="text-[11px] text-[var(--text-secondary)] mt-1">
          {{ t('dashboard.teamActivity.subtitle') }}
        </p>
      </div>
      <router-link
        v-if="!isEmpty"
        to="#"
        class="text-[10px] text-[var(--text-secondary)] hover:text-[var(--text-display)] font-[var(--font-space-mono)] uppercase tracking-wider transition-colors"
      >
        {{ t('dashboard.viewAll') }}
      </router-link>
    </div>

    <p
      v-if="isEmpty"
      class="text-sm text-[var(--text-secondary)] text-center py-8"
    >
      {{ t('dashboard.teamActivity.noActivity') }}
    </p>

    <div v-else class="space-y-2">
      <div
        v-for="event in recentEvents"
        :key="event.id"
        class="flex items-start gap-3 p-3 rounded-lg bg-[var(--background-surface)] border border-[var(--border-color)]"
      >
        <div class="relative shrink-0">
          <span
            class="w-7 h-7 rounded-full bg-[var(--background-primary)] border border-[var(--border-color)] flex items-center justify-center text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)] uppercase"
            :aria-label="isOnline(event.memberId) ? t('dashboard.teamActivity.online') : t('dashboard.teamActivity.offline')"
          >
            {{ event.memberName.charAt(0) }}
          </span>
          <span
            v-if="isOnline(event.memberId)"
            class="absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full bg-[var(--success-color)] border-2 border-[var(--background-surface)]"
            aria-hidden="true"
          />
        </div>

        <div class="flex-1 min-w-0">
          <p class="text-sm text-[var(--text-body)] leading-snug">
            <span class="font-medium text-[var(--text-display)]">
              {{ event.memberName }}
            </span>
            {{ ' ' }}
            <span class="text-[var(--text-secondary)]">
              {{ event.action }}
            </span>
          </p>
          <span class="text-[10px] text-[var(--text-secondary)] font-[var(--font-space-mono)] mt-1 block">
            {{ formatRelativeTime(event.timestamp) }}
          </span>
        </div>
      </div>
    </div>
  </section>
</template>
