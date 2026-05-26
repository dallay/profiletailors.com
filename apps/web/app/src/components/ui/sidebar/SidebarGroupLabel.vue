<script setup lang="ts">
import { cn } from '@/lib/utils'

const props = defineProps<{
  class?: string
}>()
</script>

<template>
  <p data-slot="sidebar-group-label" :class="cn('px-3 font-mono text-[10px] uppercase tracking-[0.16em] text-text-secondary', props.class)">
    <slot />
  </p>
</template>
