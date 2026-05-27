<script setup lang="ts">
import type { Component } from 'vue'
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

interface ProjectLink {
  name: string
  icon: Component
  items?: Array<{ title: string; url: string }>
}

const props = defineProps<{
  projects: ProjectLink[]
  projectsOpenState: Record<string, boolean>
}>()

const emit = defineEmits<{
  toggleProject: [projectName: string, open: boolean]
}>()
</script>

<template>
  <SidebarGroup class="gap-2 group-data-[collapsible=icon]:hidden">
    <SidebarGroupLabel>Projects</SidebarGroupLabel>
    <SidebarMenu>
      <Collapsible
        v-for="project in projects"
        :key="project.name"
        v-slot="{ open }"
        :open="projectsOpenState[project.name] ?? false"
        as-child
        @update:open="(isOpen) => emit('toggleProject', project.name, isOpen)"
      >
        <SidebarMenuItem>
          <CollapsibleTrigger as-child>
            <SidebarMenuButton
              :has-submenu="!!project.items"
              :is-submenu-open="open"
              :tooltip="project.name"
              v-slot="{ className }"
            >
              <button :class="className" type="button">
                <component :is="project.icon" class="size-4 shrink-0" />
                <span class="truncate">{{ project.name }}</span>
              </button>
            </SidebarMenuButton>
          </CollapsibleTrigger>

          <CollapsibleContent v-if="project.items">
            <SidebarMenuSub>
              <SidebarMenuSubItem
                v-for="item in project.items"
                :key="item.title"
              >
                <SidebarMenuSubButton v-slot="{ className }">
                  <a :href="item.url" :class="className">
                    <span>{{ item.title }}</span>
                  </a>
                </SidebarMenuSubButton>
              </SidebarMenuSubItem>
            </SidebarMenuSub>
          </CollapsibleContent>
        </SidebarMenuItem>
      </Collapsible>
    </SidebarMenu>
  </SidebarGroup>
</template>
