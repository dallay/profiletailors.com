<script setup lang="ts">
import type { NavPort } from './ports'
import { TooltipProvider } from './tooltip'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarInset,
  SidebarProvider,
  SidebarRail,
} from './sidebar'

defineProps<{
  nav: NavPort
}>()

defineEmits<{
  (e: 'signOut'): void
  (e: 'openSettings'): void
}>()
</script>

<template>
  <TooltipProvider>
    <SidebarProvider class="font-sans text-text-body transition-colors duration-250">
      <Sidebar collapsible="icon">
        <SidebarHeader class="gap-3">
          <slot name="header" />
        </SidebarHeader>

        <SidebarContent class="gap-6">
          <slot name="content" :groups="nav.groups" />
        </SidebarContent>

        <SidebarFooter>
          <slot name="pwa" />
          <slot name="account" :sign-out="() => $emit('signOut')" :open-settings="() => $emit('openSettings')" />
          <slot name="footer" />
        </SidebarFooter>

        <SidebarRail />
      </Sidebar>

      <SidebarInset>
        <slot name="inset" />
      </SidebarInset>
    </SidebarProvider>
  </TooltipProvider>
</template>
