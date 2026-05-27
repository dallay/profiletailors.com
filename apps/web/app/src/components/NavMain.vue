<script setup lang="ts">
import type { Component } from 'vue'
import { ChevronRight } from '@lucide/vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/sidebar'
import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
} from '@/components/ui/sidebar'

interface NavItem {
  labelKey: string
  to: string
  icon: Component
  badge?: string
  isActive?: boolean
  items?: Array<{ title: string; to: string }>
}

interface NavGroup {
  label: string
  items: NavItem[]
}

defineProps<{
  groups: NavGroup[]
}>()

const { t } = useI18n()
</script>

<template>
  <SidebarGroup
    v-for="group in groups"
    :key="group.label"
    class="gap-2"
  >
    <SidebarGroupLabel class="group-data-[collapsible=icon]:hidden">
      {{ group.label }}
    </SidebarGroupLabel>

    <SidebarMenu>
      <SidebarMenuItem
        v-for="item in group.items"
        :key="item.to"
      >
        <Collapsible
          v-if="item.items"
          as-child
          :default-open="item.isActive"
          class="group/collapsible"
        >
          <div>
            <CollapsibleTrigger as-child>
              <SidebarMenuButton :tooltip="t(item.labelKey)">
                <component :is="item.icon" class="size-4 shrink-0" />
                <span>{{ t(item.labelKey) }}</span>
                <ChevronRight class="ml-auto transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90" />
              </SidebarMenuButton>
            </CollapsibleTrigger>
            <CollapsibleContent>
              <SidebarMenuSub>
                <SidebarMenuSubItem v-for="subItem in item.items" :key="subItem.title">
                  <SidebarMenuSubButton as-child>
                    <RouterLink :to="subItem.to">
                      <span>{{ subItem.title }}</span>
                    </RouterLink>
                  </SidebarMenuSubButton>
                </SidebarMenuSubItem>
              </SidebarMenuSub>
            </CollapsibleContent>
          </div>
        </Collapsible>

        <SidebarMenuButton
          v-else
          :is-active="item.isActive"
          :tooltip="t(item.labelKey)"
          v-slot="{ className, collapsed }"
        >
          <RouterLink :to="item.to" :class="className">
            <component :is="item.icon" class="size-4 shrink-0" />
            <span v-if="!collapsed" class="truncate">{{ t(item.labelKey) }}</span>
            <span
              v-if="!collapsed && item.badge"
              class="ml-auto rounded-full border border-border-visible bg-bg-primary px-2 py-0.5 font-mono text-[9px] uppercase tracking-[0.12em] text-text-secondary"
            >
              {{ item.badge }}
            </span>
          </RouterLink>
        </SidebarMenuButton>
      </SidebarMenuItem>
    </SidebarMenu>
  </SidebarGroup>
</template>
