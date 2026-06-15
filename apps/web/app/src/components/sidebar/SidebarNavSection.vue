<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useI18n } from 'vue-i18n'

export interface NavItem {
  labelKey: string
  to: string
  icon: Component
  badge?: string
  items?: Array<{ title: string; to: string }>
}

export interface NavGroup {
  label: string
  items: NavItem[]
}

const { t } = useI18n()

const props = defineProps<{
  groups: NavGroup[]
  totalQueuedCount: number
}>()

const emit = defineEmits<(e: 'navigate', to: string) => void>()

/** Format a queue count badge: 0..9 zero-padded, 10+ raw. */
function formatBadge(n: number): string {
  return n < 10 ? `0${n}` : String(n)
}

/**
 * Decorate each group's items: when an item's `to` is exactly `/` (the
 * Dashboard), override its `badge` with the formatted queue count. Other
 * items pass through unchanged.
 */
const renderedGroups = computed<NavGroup[]>(() =>
  props.groups.map((group) => ({
    ...group,
    items: group.items.map((item) =>
      item.to === '/'
        ? { ...item, badge: formatBadge(props.totalQueuedCount) }
        : item,
    ),
  })),
)
</script>

<template>
  <div class="space-y-6">
    <div
      v-for="group in renderedGroups"
      :key="group.label"
      class="space-y-2"
    >
      <p class="px-2 font-mono text-[10px] font-bold uppercase tracking-[0.18em] text-text-secondary">
        {{ group.label }}
      </p>
      <ul class="space-y-1">
        <li
          v-for="item in group.items"
          :key="item.to"
        >
          <button
            type="button"
            class="flex w-full items-center gap-3 rounded-xl border border-transparent px-3 py-2 text-left text-sm text-text-secondary transition-colors hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display"
            @click="emit('navigate', item.to)"
          >
            <component
              :is="item.icon"
              class="size-4 shrink-0 text-text-secondary"
            />
            <span class="truncate">{{ t(item.labelKey) }}</span>
            <span
              v-if="item.badge"
              class="ml-auto inline-flex min-w-8 items-center justify-center rounded-full border border-border-visible bg-bg-primary px-2 py-0.5 font-mono text-[9px] uppercase tracking-[0.12em] text-text-secondary"
            >
              {{ item.badge }}
            </span>
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>
