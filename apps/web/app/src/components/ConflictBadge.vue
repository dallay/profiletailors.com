<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** Display variant: 'dot' (small inline), 'badge' (medium standalone), 'inline' (text + icon) */
    variant?: 'dot' | 'badge' | 'inline'
    /** Optional conflict reason text */
    reason?: string
  }>(),
  {
    variant: 'badge',
    reason: 'Conflicts with another publication',
  },
)

const sizeClasses = computed(() => {
  switch (props.variant) {
    case 'dot':
      return 'size-3 rounded-full bg-error/60'  /* larger size + more opaque for visibility */
    case 'badge':
      return 'size-4 rounded-full bg-error/15 text-error flex items-center justify-center font-mono text-[7px] font-bold cursor-help'
    case 'inline':
      return 'bg-error/10 text-error border border-error/20 font-mono text-[9px] uppercase tracking-widest px-2 py-0.5 rounded-md flex items-center gap-1'
    default:
      return 'size-4 rounded-full bg-error/15 text-error flex items-center justify-center font-mono text-[7px] font-bold cursor-help'
  }
})
</script>

<template>
  <output
    :class="sizeClasses"
    :title="reason"
    aria-label="Conflict"
  >
    <!-- Dot: no inner content -->
    <template v-if="variant === 'dot'" />
    <!-- Badge: exclamation mark -->
    <template v-else-if="variant === 'badge'">
      !
    </template>
    <!-- Inline: dot icon + "Conflict" label -->
    <template v-else-if="variant === 'inline'">
      <span class="size-2 rounded-full bg-error inline-block" />
      Conflict
    </template>
  </output>
</template>
