<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Sparkles } from '@lucide/vue'

defineProps<{
  unreadCount: number
}>()

const emit = defineEmits<{
  open: []
}>()

const { t } = useI18n()
</script>

<template>
  <button
    type="button"
    data-testid="changelog-trigger"
    class="flex min-h-[36px] w-full items-center justify-between gap-2 rounded-lg border border-transparent px-2 py-1.5 text-left text-xs text-text-secondary transition-colors hover:border-border-subtle hover:bg-bg-surface hover:text-text-display group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:px-0"
    :aria-label="
      unreadCount > 0
        ? `${t('changelog.trigger')} (${t('changelog.unreadSrOnly', { count: unreadCount })})`
        : t('changelog.trigger')
    "
    @click="emit('open')"
  >
    <span class="flex min-w-0 items-center gap-2">
      <Sparkles class="size-3.5 shrink-0" aria-hidden="true" />
      <span class="truncate font-mono uppercase tracking-[0.06em] group-data-[collapsible=icon]:hidden">
        {{ t('changelog.trigger') }}
      </span>
    </span>

    <span
      v-if="unreadCount > 0"
      data-testid="changelog-unread-badge"
      class="inline-flex shrink-0 items-center gap-1 rounded-full border border-border-visible bg-bg-primary px-2 py-0.5 font-mono text-[10px] uppercase tracking-wider text-text-display group-data-[collapsible=icon]:hidden"
    >
      <span class="size-1.5 rounded-full bg-status-success" aria-hidden="true" />
      <span>{{ t('changelog.unreadBadge', { count: unreadCount }) }}</span>
    </span>
  </button>
</template>
