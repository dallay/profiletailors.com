<script setup lang="ts">
import type { Component } from 'vue'
import { ChevronsUpDown, Sparkles } from '@lucide/vue'
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from '@/components/ui/sidebar'

interface AccountOption {
  name: string
  plan: string
  icon: Component
}

defineProps<{
  teams: AccountOption[]
}>()

const activeTeam = ref(0)
</script>

<template>
  <SidebarMenu>
    <SidebarMenuItem>
      <SidebarMenuButton
        size="lg"
        class="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
        as-child
      >
        <RouterLink
          to="/"
          class="flex min-w-0 items-center gap-3 rounded-2xl border border-border-subtle bg-bg-surface/70 px-3 py-2 group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:px-2"
        >
          <div class="flex size-10 shrink-0 items-center justify-center rounded-xl bg-text-display text-bg-primary">
            <Sparkles class="size-4" />
          </div>

          <div class="min-w-0 flex-1 group-data-[collapsible=icon]:hidden">
            <p class="truncate font-mono text-[11px] font-bold uppercase tracking-[0.18em] text-text-display">
              {{ teams[activeTeam]?.name }}
            </p>
            <p class="truncate text-xs text-text-secondary">
              {{ teams[activeTeam]?.plan }}
            </p>
          </div>

          <ChevronsUpDown class="ml-auto size-4 group-data-[collapsible=icon]:hidden" />
        </RouterLink>
      </SidebarMenuButton>
    </SidebarMenuItem>
  </SidebarMenu>
</template>
