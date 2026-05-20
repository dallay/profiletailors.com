<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const props = withDefaults(defineProps<{
  class?: HTMLAttributes['class']
  size?: 'default' | 'sm'
}>(), {
  size: 'default',
})
</script>

<template>
  <div
    data-slot="card"
    :data-size="size"
    :class="cn('bg-bg-surface text-text-body gap-4 overflow-hidden rounded-xl p-6 border border-border-subtle text-sm group/card flex flex-col', props.class)"
  >
    <slot />
  </div>
</template>
