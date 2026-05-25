<script setup lang="ts">
import { cn } from '@/lib/utils'

const props = defineProps<{
  class?: string
}>()
</script>

<template>
  <ul data-slot="sidebar-menu" :class="cn('flex w-full min-w-0 flex-col gap-1.5', props.class)">
    <slot />
  </ul>
</template>
