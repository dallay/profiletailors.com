<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '../lib/utils'

defineOptions({ inheritAttrs: false })

type BadgeVariant = 'neutral' | 'success' | 'warning' | 'danger'

const props = withDefaults(defineProps<{
  variant?: BadgeVariant
  class?: HTMLAttributes['class']
}>(), {
  variant: 'neutral',
})

const variantClasses: Record<BadgeVariant, string> = {
  neutral: 'border-border-visible bg-bg-primary text-text-secondary',
  success: 'border-success/40 bg-success/10 text-success',
  warning: 'border-warning/40 bg-warning/10 text-warning',
  danger: 'border-error/40 bg-error/10 text-error',
}
</script>

<template>
  <span
    v-bind="$attrs"
    :class="cn('inline-flex items-center rounded-full border px-2 py-1 font-mono text-[10px] font-bold uppercase tracking-[0.06em]', variantClasses[props.variant], props.class)"
  >
    <slot />
  </span>
</template>
