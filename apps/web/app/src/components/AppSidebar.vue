<script setup lang="ts">
import type { Component } from 'vue'
import {
  AudioWaveform,
  BarChart3,
  CalendarDays,
  FolderKanban,
  GalleryVerticalEnd,
  LayoutGrid,
  Settings,
  Users,
} from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import NavMain from '@/components/NavMain.vue'
import NavProjects from '@/components/NavProjects.vue'
import NavUser from '@/components/NavUser.vue'
import TeamSwitcher from '@/components/TeamSwitcher.vue'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarRail,
} from '@/components/ui/sidebar'

const props = withDefaults(defineProps<{
  collapsible?: 'offcanvas' | 'icon' | 'none'
}>(), {
  collapsible: 'icon',
})

const route = useRoute()
const projectsOpenState = ref<Record<string, boolean>>({})

interface NavItem {
  labelKey: string
  to: string
  icon: Component
  badge?: string
  isActive?: boolean
}

interface NavGroup {
  label: string
  items: NavItem[]
}

interface ProjectLink {
  name: string
  icon: Component
  items?: Array<{ title: string; url: string }>
}

interface AccountOption {
  name: string
  plan: string
  icon: Component
}

const data = computed(() => ({
  teams: [
    {
      name: 'Profile Tailors',
      plan: 'Enterprise',
      icon: GalleryVerticalEnd,
    },
    {
      name: 'Acosta Studio',
      plan: 'Growth',
      icon: AudioWaveform,
    },
  ] as AccountOption[],
  navMain: [
    {
      label: 'Workspace',
      items: [
        { labelKey: 'nav.dashboard', to: '/', icon: LayoutGrid, badge: '02', isActive: route.path === '/' },
        { labelKey: 'nav.scheduler', to: '/scheduler', icon: CalendarDays, isActive: route.path === '/scheduler' },
        { labelKey: 'nav.analytics', to: '/analytics', icon: BarChart3, badge: 'Live', isActive: route.path === '/analytics' },
      ],
    },
    {
      label: 'System',
      items: [
        { labelKey: 'nav.settings', to: '/settings', icon: Settings, isActive: route.path === '/settings' },
      ],
    },
  ] as NavGroup[],
  projects: [
    {
      name: 'Launch Week',
      icon: FolderKanban,
      items: [
        { title: 'Overview', url: '#' },
        { title: 'Timeline', url: '#' },
        { title: 'Assets', url: '#' },
      ],
    },
    {
      name: 'Creator Growth',
      icon: Users,
      items: [
        { title: 'Campaigns', url: '#' },
        { title: 'Metrics', url: '#' },
      ],
    },
  ] as ProjectLink[],
}))

function loadProjectsState() {
  const stored = localStorage.getItem('sidebar-projects-state')
  if (stored) {
    try {
      projectsOpenState.value = JSON.parse(stored)
    }
    catch {
      projectsOpenState.value = {}
    }
  }
}

function saveProjectsState() {
  localStorage.setItem('sidebar-projects-state', JSON.stringify(projectsOpenState.value))
}

function toggleProject(projectName: string, open: boolean) {
  projectsOpenState.value[projectName] = open
  saveProjectsState()
}

onMounted(() => {
  loadProjectsState()
})
</script>

<template>
  <Sidebar v-bind="props">
    <SidebarHeader>
      <TeamSwitcher :teams="data.teams" />
    </SidebarHeader>
    <SidebarContent>
      <NavMain :groups="data.navMain" />
      <NavProjects
        :projects="data.projects"
        :projects-open-state="projectsOpenState"
        @toggle-project="toggleProject"
      />
    </SidebarContent>
    <SidebarFooter>
      <NavUser />
    </SidebarFooter>
    <SidebarRail />
  </Sidebar>
</template>
