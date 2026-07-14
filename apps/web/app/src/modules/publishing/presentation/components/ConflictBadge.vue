<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = withDefaults(
  defineProps<{
    /** Display variant: 'dot' (small inline), 'badge' (medium standalone), 'inline' (text + icon) */
    variant?: 'dot' | 'badge' | 'inline'
    /** Optional conflict reason text */
    reason?: string
  }>(),
  {
    variant: 'badge',
  },
)

const { t } = useI18n()
const resolvedReason = computed(() => props.reason ?? t('composer.conflictBadge.reason'))

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
    :title="resolvedReason"
    :aria-label="t('composer.conflictBadge.conflict')"
  >
    <template v-if="variant === 'dot'" />
    <template v-else-if="variant === 'badge'">
      !
    </template>
    <template v-else-if="variant === 'inline'">
      <span class="size-2 rounded-full bg-error inline-block" />
      {{ t('composer.conflictBadge.conflict') }}
    </template>
  </output>
</template>
