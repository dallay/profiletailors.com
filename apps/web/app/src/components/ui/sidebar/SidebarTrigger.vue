<script setup lang="ts">
import { inject } from 'vue'
import { Button } from '@/components/ui/button'
import { sidebarContextKey } from './utils'

const sidebar = inject(sidebarContextKey)
if (!sidebar) {
  throw new Error('SidebarTrigger must be used within SidebarProvider')
}
</script>

<template>
  <Button variant="ghost" size="icon" class="size-10 rounded-xl" @click="sidebar.toggleSidebar()">
    <slot />
  </Button>
</template>
