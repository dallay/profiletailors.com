<script setup lang="ts">
import { cn } from '@/lib/utils'

const props = defineProps<{
  class?: string
}>()
</script>

<template>
  <ul
    data-slot="sidebar-menu-sub"
    :class="cn(
      'mx-3.5 flex min-w-0 translate-x-px flex-col gap-1 border-l border-border-subtle px-2.5 py-0.5',
      'group-data-[collapsible=icon]:hidden',
      props.class,
    )"
  >
    <slot />
  </ul>
</template>
