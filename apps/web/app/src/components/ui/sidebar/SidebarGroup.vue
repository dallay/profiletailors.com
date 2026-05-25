<script setup lang="ts">
import { cn } from '@/lib/utils'

const props = defineProps<{
  class?: string
}>()
</script>

<template>
  <div data-slot="sidebar-group" :class="cn('flex w-full min-w-0 flex-col gap-1', props.class)">
    <slot />
  </div>
</template>
