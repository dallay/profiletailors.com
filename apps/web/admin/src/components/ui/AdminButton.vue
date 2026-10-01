<script setup lang="ts">
import type { ButtonHTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

defineOptions({ name: 'AdminButton', inheritAttrs: false })

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'
type Size = 'sm' | 'md'

const props = withDefaults(defineProps<{
  class?: ButtonHTMLAttributes['class']
  type?: ButtonHTMLAttributes['type']
  variant?: Variant
  size?: Size
  disabled?: boolean
}>(), {
  type: 'button',
  variant: 'primary',
  size: 'md',
  disabled: false,
})

const variants: Record<Variant, string> = {
  primary: 'bg-text-display text-bg-primary hover:opacity-85',
  secondary: 'border border-border-visible bg-transparent text-text-body hover:bg-bg-surface',
  danger: 'admin-button-danger border border-error/40 bg-error/10 text-error hover:bg-error/20',
  ghost: 'text-text-secondary hover:bg-bg-surface hover:text-text-display',
}

const sizes: Record<Size, string> = {
  sm: 'min-h-11 px-3 py-1.5 text-xs',
  md: 'min-h-11 px-4 py-2 text-sm',
}
</script>

<template>
  <button
    v-bind="$attrs"
    :type="props.type"
    :disabled="props.disabled"
    :class="cn('inline-flex items-center justify-center gap-2 rounded-full font-mono font-bold uppercase tracking-[0.06em] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-text-display focus-visible:ring-offset-2 focus-visible:ring-offset-bg-primary disabled:pointer-events-none disabled:opacity-40', variants[props.variant], sizes[props.size], props.class)"
  >
    <slot />
  </button>
</template>
