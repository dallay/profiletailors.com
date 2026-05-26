<script setup lang="ts">
import { computed, inject } from 'vue'
import { cn } from '@/lib/utils'
import { sidebarContextKey } from './utils'

const props = withDefaults(defineProps<{
  class?: string
  collapsible?: 'offcanvas' | 'icon' | 'none'
}>(), {
  collapsible: 'icon',
})

const sidebar = inject(sidebarContextKey)
if (!sidebar) {
  throw new Error('Sidebar must be used within SidebarProvider')
}

const mobileStateClass = computed(() => (sidebar.openMobile.value ? 'translate-x-0' : '-translate-x-full md:translate-x-0'))
const desktopWidthClass = computed(() => (sidebar.open.value ? 'md:w-[var(--sidebar-width)]' : 'md:w-[var(--sidebar-width-icon)]'))
</script>

<template>
  <aside
    data-slot="sidebar"
    :data-state="sidebar.state.value"
    :data-collapsible="sidebar.state.value === 'collapsed' ? props.collapsible : ''"
    :class="cn(
      'fixed inset-y-0 left-0 z-50 flex h-screen w-[--sidebar-width-mobile] flex-col border-r border-border-subtle bg-bg-primary/95 backdrop-blur transition-all duration-200 ease-linear md:sticky md:top-0 md:z-30 md:bg-bg-primary md:backdrop-blur-none',
      desktopWidthClass,
      mobileStateClass,
      props.class,
    )"
  >
    <slot />
  </aside>
</template>
