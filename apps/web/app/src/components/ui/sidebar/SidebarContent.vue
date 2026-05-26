<script setup lang="ts">
import { cn } from '@/lib/utils'

const props = defineProps<{
  class?: string
}>()
</script>

<template>
  <div data-slot="sidebar-content" :class="cn('flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto p-3', props.class)">
    <slot />
  </div>
</template>
