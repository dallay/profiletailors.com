<script setup lang="ts">
import type { ButtonHTMLAttributes } from 'vue'
import { cn } from '../lib/utils'

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost'
type ButtonSize = 'sm' | 'md'

interface Props {
  class?: ButtonHTMLAttributes['class']
  type?: ButtonHTMLAttributes['type']
  variant?: ButtonVariant
  size?: ButtonSize
  disabled?: boolean
}

defineOptions({ inheritAttrs: false })

const props = withDefaults(defineProps<Props>(), {
  type: 'button',
  variant: 'primary',
  size: 'md',
  disabled: false,
})

const variantClasses: Record<ButtonVariant, string> = {
  primary: 'bg-text-display text-bg-primary hover:opacity-80',
  secondary: 'border border-border-visible bg-transparent text-text-body hover:bg-bg-primary',
  danger: 'border border-error/40 bg-error/10 text-error hover:bg-error/20',
  ghost: 'text-text-body hover:bg-bg-primary',
}

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'min-h-8 px-2 py-1 text-xs',
  md: 'min-h-10 px-4 py-2 text-sm',
}
</script>

<template>
  <button
    v-bind="$attrs"
    :type="props.type"
    :disabled="props.disabled"
    :class="cn(
      'inline-flex items-center justify-center gap-2 rounded-full font-mono font-bold uppercase tracking-[0.06em] transition-opacity focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-text-display disabled:pointer-events-none disabled:opacity-40',
      variantClasses[props.variant],
      sizeClasses[props.size],
      props.class,
    )"
  >
    <slot />
  </button>
</template>
