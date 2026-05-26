<script setup lang="ts">
import { computed, inject } from 'vue'
import { cn } from '@/lib/utils'
import { sidebarContextKey } from './utils'

const props = defineProps<{
  class?: string
}>()

const sidebar = inject(sidebarContextKey)
if (!sidebar) {
  throw new Error('SidebarRail must be used within SidebarProvider')
}

const railClass = computed(() => {
  return sidebar.open.value ? 'right-[-8px] cursor-w-resize' : 'right-[-8px] cursor-e-resize'
})
</script>

<template>
  <button
    type="button"
    aria-label="Toggle Sidebar"
    :class="cn('absolute inset-y-0 hidden w-4 md:block', railClass, props.class)"
    @click="sidebar.toggleSidebar()"
  >
    <span class="absolute inset-y-6 left-1/2 w-px -translate-x-1/2 bg-border-subtle transition-colors hover:bg-border-visible" />
  </button>
</template>
