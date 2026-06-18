<script setup lang="ts">
import { ref } from 'vue'
import { proxyImageUrl } from '@/lib/auth-api'
import { getProviderBadge } from '@/lib/provider-styles'
import type { Channel } from '@/stores/publishing'

interface SidebarChannel extends Channel {
  badge: string
  queuedCount: number
}

defineProps<{
  channel: SidebarChannel
  isActive: boolean
  queuedCount: number
}>()

const emit = defineEmits<{
  (e: 'select'): void
  (e: 'avatarError'): void
}>()

// Per-row avatar fallback state. Local so a sibling row's failure does NOT leak.
const avatarLoadFailed = ref(false)

function onAvatarError() {
  avatarLoadFailed.value = true
  emit('avatarError')
}
</script>

<template>
  <button
    type="button"
    class="flex w-full items-center gap-3 rounded-xl border px-3 py-2 text-left text-sm transition-all"
    :class="isActive
      ? 'border-border-visible bg-bg-primary text-text-display'
      : 'border-transparent text-text-secondary hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display'"
    @click="emit('select')"
  >
    <span class="relative flex size-5 shrink-0 items-center justify-center">
      <img
        v-if="channel.avatarUrl && !avatarLoadFailed"
        :src="proxyImageUrl(channel.avatarUrl ?? '')"
        :alt="`${channel.name} avatar`"
        class="size-5 rounded-full border border-border-visible object-cover grayscale"
        @error="onAvatarError"
      />
      <span
        v-else
        class="flex size-5 items-center justify-center rounded-full border border-border-visible bg-bg-primary font-mono text-[7px] font-bold uppercase leading-none text-text-display"
      >
        {{ getProviderBadge(channel.provider) }}
      </span>
      <span class="absolute -right-1 -bottom-1 flex size-3.5 items-center justify-center rounded-full border border-bg-surface bg-bg-primary font-mono text-[7px] font-bold uppercase leading-none text-text-display">
        {{ channel.badge }}
      </span>
    </span>

    <span class="min-w-0 flex-1 text-left">
      <span class="block truncate text-sm leading-none">{{ channel.name }}</span>
    </span>

    <span class="ml-auto inline-flex min-w-6 items-center justify-end font-mono text-[10px] text-text-secondary">
      {{ queuedCount }}
    </span>
  </button>
</template>
