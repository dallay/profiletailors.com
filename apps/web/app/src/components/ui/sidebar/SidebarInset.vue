<script setup lang="ts">
import { computed, inject } from 'vue'
import { cn } from '@/lib/utils'
import { sidebarContextKey } from './utils'

const props = defineProps<{
  class?: string
}>()

const sidebar = inject(sidebarContextKey)
if (!sidebar) {
  throw new Error('SidebarInset must be used within SidebarProvider')
}

const isCollapsed = computed(() => !sidebar.open.value)
</script>

<template>
  <main
    data-slot="sidebar-inset"
    :class="cn('flex min-w-0 flex-1 flex-col', isCollapsed ? 'md:pl-0' : 'md:pl-0', props.class)"
  >
    <slot />
  </main>
</template>
