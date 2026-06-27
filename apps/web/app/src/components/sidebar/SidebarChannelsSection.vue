<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Users } from '@lucide/vue'
import SidebarChannelRow from './SidebarChannelRow.vue'
import type { Channel } from '@/stores/publishing'

export interface SidebarChannel extends Channel {
  badge: string
  queuedCount: number
}

const props = defineProps<{
  channels: SidebarChannel[]
  activeChannelId: string | null
  totalQueuedCount: number
  isSchedulerRoute: boolean
}>()

const emit = defineEmits<{
  (e: 'selectAll'): void
  (e: 'selectChannel', accountId: string): void
}>()

/**
 * Avatar-failed map. Source of truth for the row's local `avatarLoadFailed`
 * ref is the row itself; this map is the parent's parallel record so we can
 * reset cleanly when `channels` changes (shallow reference compare is enough
 * — the store replaces the array reference on reload).
 */
const avatarLoadFailedMap = ref<Record<string, boolean>>({})

watch(
  () => props.channels,
  () => {
    avatarLoadFailedMap.value = {}
  },
  { deep: false },
)

function isRowActive(accountId: string): boolean {
  return props.isSchedulerRoute && props.activeChannelId === accountId
}

const allBadge = computed(() => {
  if (props.totalQueuedCount <= 0) return null
  const n = props.totalQueuedCount
  return n < 10 ? `0${n}` : String(n)
})
</script>

<template>
  <div class="space-y-1">
    <button
      type="button"
      class="flex w-full items-center gap-3 rounded-xl border border-transparent px-3 py-2 text-left text-sm text-text-secondary transition-colors hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display group-data-[collapsible=icon]:px-0 group-data-[collapsible=icon]:justify-center"
      :class="isSchedulerRoute && !activeChannelId ? 'border-border-visible bg-bg-primary text-text-display' : ''"
      @click="emit('selectAll')"
    >
      <Users class="size-4 shrink-0 text-text-secondary" />
      <span class="sr-only">All channels</span>
      <span class="truncate group-data-[collapsible=icon]:hidden">All channels</span>
      <span
        v-if="allBadge"
        class="ml-auto inline-flex min-w-8 items-center justify-center rounded-full border border-border-visible bg-bg-primary px-2 py-0.5 font-mono text-[9px] uppercase tracking-[0.12em] text-text-secondary group-data-[collapsible=icon]:hidden"
      >
        {{ allBadge }}
      </span>
    </button>

    <SidebarChannelRow
      v-for="channel in channels"
      :key="channel.id"
      :channel="channel"
      :is-active="isRowActive(channel.accountId)"
      :queued-count="channel.queuedCount"
      @select="emit('selectChannel', channel.accountId)"
      @avatar-error="() => { avatarLoadFailedMap[channel.id] = true }"
    />
  </div>
</template>
