<script setup lang="ts">
import { cn } from '@/lib/utils'

const props = withDefaults(defineProps<{
  class?: string
  isActive?: boolean
  size?: 'sm' | 'md'
}>(), {
  isActive: false,
  size: 'md',
})
</script>

<template>
  <slot
    :class-name="cn(
      'flex h-7 min-w-0 -translate-x-px items-center gap-2 overflow-hidden rounded-md px-2 text-sm text-text-secondary outline-none ring-sidebar-ring transition-all hover:bg-bg-surface hover:text-text-display focus-visible:ring-2 disabled:pointer-events-none disabled:opacity-50',
      props.isActive && 'bg-bg-surface text-text-display font-medium',
      props.size === 'sm' && 'text-xs h-6',
      props.class,
    )"
  />
</template>
