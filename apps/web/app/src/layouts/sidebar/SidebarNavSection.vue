<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LucideIcon } from '@lucide/vue'
import { useSidebar } from '@profiletailors/vue-ui/shell/sidebar/utils'

export interface NavItem {
  labelKey: string
  to: string
  icon: LucideIcon
  badge?: string
}

export interface NavGroup {
  label: string
  items: NavItem[]
}

const props = defineProps<{
  groups: NavGroup[]
  totalQueuedCount: number
}>()

const { t } = useI18n()
const { isMobile, setOpenMobile } = useSidebar()

function handleNavigation(): void {
  if (isMobile.value) {
    setOpenMobile(false)
  }
}

function formatBadge(count: number): string {
  if (count <= 0) return ''
  return count < 10 ? `0${count}` : String(count)
}

/**
 * Derives a "live" version of the nav groups that injects/updates `badge` with the formatted queue count.
 * If the count is zero, the badge is omitted (no badge in zero-state).
 * Other items pass through unchanged.
 */
const renderedGroups = computed<NavGroup[]>(() =>
  props.groups.map((group) => ({
    ...group,
    items: group.items.map((item) => {
      if (item.to !== '/') return item
      const badge = formatBadge(props.totalQueuedCount)
      if (badge === '') {
        const { badge: _omit, ...rest } = item
        return rest as NavItem
      }
      return { ...item, badge }
    }),
  })),
)
</script>

<template>
  <div class="space-y-4">
    <div
      v-for="group in renderedGroups"
      :key="group.label"
      class="space-y-1"
    >
      <p v-if="group.label" class="flex h-5 items-center px-2 font-mono text-[10px] uppercase tracking-[0.14em] text-text-secondary/70 group-data-[collapsible=icon]:hidden">
        {{ group.label }}
      </p>
      <ul class="space-y-0.5">
        <li
          v-for="item in group.items"
          :key="item.to"
        >
          <RouterLink
            :to="item.to"
            :title="t(item.labelKey)"
            class="flex w-full items-center gap-3 rounded-xl border border-transparent px-3 py-2 text-sm text-text-secondary transition-colors no-underline hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground group-data-[collapsible=icon]:px-0 group-data-[collapsible=icon]:justify-center"
            active-class="bg-sidebar-accent text-sidebar-accent-foreground border-sidebar-accent font-medium"
            @click="handleNavigation"
          >
            <component
              :is="item.icon"
              class="size-4 shrink-0"
            />
            <span class="sr-only">{{ t(item.labelKey) }}</span>
            <span class="truncate group-data-[collapsible=icon]:hidden">{{ t(item.labelKey) }}</span>
            <span
              v-if="item.badge"
              class="ml-auto inline-flex min-w-8 items-center justify-center rounded-full border border-border-visible bg-bg-primary px-2 py-0.5 font-mono text-[10px] uppercase tracking-[0.14em] text-text-secondary group-data-[collapsible=icon]:hidden"
            >
              {{ item.badge }}
            </span>
          </RouterLink>
        </li>
      </ul>
    </div>
  </div>
</template>
