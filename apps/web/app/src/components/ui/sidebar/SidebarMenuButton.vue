<script setup lang="ts">
import { ChevronRight } from '@lucide/vue'
import { computed, inject } from 'vue'
import { cn } from '@/lib/utils'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { sidebarContextKey } from './utils'

const props = withDefaults(defineProps<{
  class?: string
  isActive?: boolean
  tooltip?: string
  hasSubmenu?: boolean
  isSubmenuOpen?: boolean
}>(), {
  isActive: false,
  tooltip: undefined,
  hasSubmenu: false,
  isSubmenuOpen: false,
})

const context = inject(sidebarContextKey)
const isCollapsed = computed(() => context?.state.value === 'collapsed')
</script>

<template>
  <div class="relative">
    <Tooltip v-if="isCollapsed && props.tooltip" :delay-duration="0">
      <TooltipTrigger as-child>
        <slot
          :class-name="cn(
            'group flex items-center gap-3 rounded-xl border px-3 py-2.5 text-sm transition-all',
            isCollapsed ? 'justify-center px-2' : 'justify-start',
            props.isActive
              ? 'border-border-visible bg-bg-surface text-text-display shadow-[inset_0_1px_0_rgba(255,255,255,0.03)]'
              : 'border-transparent text-text-secondary hover:border-border-subtle hover:bg-bg-surface/70 hover:text-text-display',
            props.class,
          )"
          :collapsed="isCollapsed"
        />
      </TooltipTrigger>
      <TooltipContent side="right" class="border-border-visible bg-bg-surface text-text-display">
        {{ props.tooltip }}
      </TooltipContent>
    </Tooltip>

    <slot
      v-else
      :class-name="cn(
        'group flex items-center gap-3 rounded-xl border px-3 py-2.5 text-sm transition-all',
        isCollapsed ? 'justify-center px-2' : 'justify-start',
        props.isActive
          ? 'border-border-visible bg-bg-surface text-text-display shadow-[inset_0_1px_0_rgba(255,255,255,0.03)]'
          : 'border-transparent text-text-secondary hover:border-border-subtle hover:bg-bg-surface/70 hover:text-text-display',
        props.class,
      )"
      :collapsed="isCollapsed"
    />

    <ChevronRight
      v-if="props.hasSubmenu && !isCollapsed"
      class="pointer-events-none absolute right-3 size-3.5 text-text-secondary transition-transform duration-200"
      :class="props.isSubmenuOpen && 'rotate-90 text-text-display'"
    />
  </div>
</template>

