<script setup lang="ts">
import { cn } from '@/lib/utils'

const props = defineProps<{
  class?: string
}>()
</script>

<template>
  <div data-slot="sidebar-footer" :class="cn('mt-auto flex flex-col gap-3 border-t border-border-subtle p-3', props.class)">
    <slot />
  </div>
</template>
