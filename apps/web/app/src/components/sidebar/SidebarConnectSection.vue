<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useConnectMessage } from '@/composables/useConnectMessage'

export interface ConnectChannel {
  id: 'linkedin' | 'threads' | 'bluesky' | 'facebook'
  label: string
  badge: string
}

const props = defineProps<{
  providers: ConnectChannel[]
}>()

const emit = defineEmits<{
  (e: 'connect', channel: ConnectChannel): void
  (e: 'more'): void
}>()

const { t } = useI18n()
const { message, show } = useConnectMessage({ defaultDurationMs: 3500 })

function onConnect(channel: ConnectChannel) {
  if (channel.id === 'linkedin') {
    show(t('channels.connectingLinkedIn'))
    emit('connect', channel)
    return
  }
  show(`${channel.label} ${channel.id === 'threads' ? t('channels.threadsComingSoon') : t('channels.connectionAvailableSoon')}`)
  emit('connect', channel)
}

function onMore() {
  show(t('channels.moreChannels') || 'More channels coming soon')
  emit('more')
}
</script>

<template>
  <div class="mt-3 space-y-1.5 border-t border-border-subtle pt-3">
    <p class="px-2 font-mono text-[9px] uppercase tracking-[0.18em] text-text-secondary/80 group-data-[collapsible=icon]:hidden">
      {{ t('channels.connect') }}
    </p>

    <button
      v-for="channel in providers"
      :key="channel.id"
      class="flex w-full items-center gap-3 rounded-xl border border-transparent px-2.5 py-2 text-left text-[13px] text-text-secondary transition-colors hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display group-data-[collapsible=icon]:px-0 group-data-[collapsible=icon]:justify-center"
      type="button"
      @click="onConnect(channel)"
    >
      <span class="flex size-5 shrink-0 items-center justify-center rounded-full border border-border-visible bg-bg-primary font-mono text-[9px] font-bold uppercase text-text-display">
        {{ channel.badge }}
      </span>
      <span class="sr-only">{{ channel.label }} - {{ t('channels.connectAction') }}</span>
      <span class="min-w-0 flex-1 truncate group-data-[collapsible=icon]:hidden">{{ channel.label }}</span>
      <span class="font-mono text-[9px] uppercase tracking-[0.12em] text-text-secondary/80 group-data-[collapsible=icon]:hidden">+ {{ t('channels.connectAction') }}</span>
    </button>

    <button
      class="flex w-full items-center gap-2 rounded-lg border border-dashed border-border-visible px-2 py-1.5 text-left font-mono text-[9px] uppercase tracking-[0.12em] text-text-secondary transition-colors hover:border-text-secondary hover:text-text-display group-data-[collapsible=icon]:px-0 group-data-[collapsible=icon]:justify-center"
      type="button"
      @click="onMore"
    >
      <span class="sr-only">{{ t('channels.more') }}</span>
      <span class="truncate group-data-[collapsible=icon]:hidden">{{ t('channels.more') }}</span>
      <span class="hidden group-data-[collapsible=icon]:block">+</span>
    </button>

    <p
      v-if="message"
      class="mt-2 px-2 font-mono text-[9px] uppercase tracking-[0.12em] text-text-secondary group-data-[collapsible=icon]:sr-only"
      aria-live="polite"
    >
      {{ message }}
    </p>
  </div>
</template>
