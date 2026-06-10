<script setup lang="ts">
import type { Component } from 'vue'
import {
  AudioWaveform,
  BarChart3,
  CalendarDays,
  ChevronsUpDown,
  FolderKanban,
  GalleryVerticalEnd,
  LayoutGrid,
  LogOut,
  PanelLeft,
  Settings,
  Sparkles,
  Users,
} from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import ThemeToggle from '@/components/ThemeToggle.vue'
import { TooltipProvider } from '@/components/ui/tooltip'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/sidebar'
import { useAuthStore } from '@/stores/auth'
import { useSettingsStore } from '@/stores/settings'
import { usePublishingStore } from '@/stores/publishing'

interface NavItem {
  labelKey: string
  to: string
  icon: Component
  badge?: string
  items?: Array<{ title: string; to: string }>
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

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const settings = useSettingsStore()
const publishingStore = usePublishingStore()
const accountMenuOpen = ref(false)
const accountMenuRef = ref<HTMLElement | null>(null)
const projectsOpenState = ref<Record<string, boolean>>({})

const activeAccount = ref<AccountOption>({
  name: 'Profile Tailors',
  plan: 'Enterprise',
  icon: GalleryVerticalEnd,
})

const isAuthRoute = computed(() => route.name === 'login' || route.name === 'register')

const currentSectionLabel = computed(() => {
  if (!route.name) {
    return 'dashboard'
  }

  return route.name === 'login' || route.name === 'register' ? 'login' : String(route.name)
})

const navigationGroups = computed<NavGroup[]>(() => [
  {
    label: 'Workspace',
    items: [
      { 
        labelKey: 'nav.dashboard', 
        to: '/', 
        icon: LayoutGrid, 
        badge: (() => {
          const count = publishingStore.publications.filter((p) => p.status === 'QUEUED').length
          return count < 10 ? `0${count}` : String(count)
        })()
      },
      { labelKey: 'nav.scheduler', to: '/scheduler', icon: CalendarDays },
      { labelKey: 'nav.analytics', to: '/analytics', icon: BarChart3, badge: 'Live' },
    ],
  },
  {
    label: 'System',
    items: [{ labelKey: 'nav.settings', to: '/settings', icon: Settings }],
  },
])

const projectLinks = computed<ProjectLink[]>(() => [
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
])

const accountOptions = computed<AccountOption[]>(() => [
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
])

const headerSummary = computed(() => {
  return currentSectionLabel.value === 'dashboard'
    ? 'Publishing control panel'
    : `${settings.currentTheme} mode / ${settings.currentLocale.toUpperCase()}`
})

function isActive(path: string) {
  return route.path === path
}

function toggleAccountMenu() {
  accountMenuOpen.value = !accountMenuOpen.value
}

function closeAccountMenu() {
  accountMenuOpen.value = false
}

function selectAccount(account: AccountOption) {
  activeAccount.value = account
  closeAccountMenu()
}

async function handleLogout() {
  closeAccountMenu()
  await auth.logout()
  await router.replace('/login')
}

function navigateToSettings() {
  router.push('/settings')
  closeAccountMenu()
}


function handleDocumentClick(event: MouseEvent) {
  if (!accountMenuOpen.value) {
    return
  }

  const target = event.target
  if (!(target instanceof Node)) {
    return
  }

  if (!accountMenuRef.value?.contains(target)) {
    closeAccountMenu()
  }
}

function handleEscapeKey(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeAccountMenu()
  }
}

function loadProjectsState() {
  const stored = localStorage.getItem('sidebar-projects-state')
  if (stored) {
    try {
      projectsOpenState.value = JSON.parse(stored)
    } catch {
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

watch(() => route.path, () => {
  closeAccountMenu()
})

onMounted(() => {
  loadProjectsState()
  document.addEventListener('click', handleDocumentClick)
  document.addEventListener('keydown', handleEscapeKey)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleEscapeKey)
})
</script>

<template>
  <RouterView v-if="isAuthRoute" />

  <TooltipProvider v-else>
    <SidebarProvider :default-open="true" class="bg-bg-primary font-sans text-text-body transition-colors duration-250">
    <Sidebar collapsible="icon">
      <SidebarHeader class="gap-3">
        <RouterLink
          to="/"
          class="flex min-w-0 items-center gap-3 rounded-2xl border border-border-subtle bg-bg-surface/70 px-3 py-2 group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:px-2"
        >
          <div class="flex size-10 shrink-0 items-center justify-center rounded-xl bg-text-display text-bg-primary">
            <Sparkles class="size-4" />
          </div>

          <div class="min-w-0 flex-1 group-data-[collapsible=icon]:hidden">
            <p class="truncate font-mono text-[11px] font-bold uppercase tracking-[0.18em] text-text-display">
              Profile Tailors
            </p>
            <p class="truncate text-xs text-text-secondary">
              Creator workspace
            </p>
          </div>
        </RouterLink>
      </SidebarHeader>

      <SidebarContent class="gap-6">
        <SidebarGroup
          v-for="group in navigationGroups"
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
              <SidebarMenuButton
                :is-active="isActive(item.to)"
                :tooltip="$t(item.labelKey)"
                v-slot="{ className, collapsed }"
              >
                <RouterLink :to="item.to" :class="className">
                  <component :is="item.icon" class="size-4 shrink-0" />
                  <span v-if="!collapsed" class="truncate">{{ $t(item.labelKey) }}</span>
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

        <SidebarGroup class="gap-2 group-data-[collapsible=icon]:hidden">
          <SidebarGroupLabel>Projects</SidebarGroupLabel>
          <SidebarMenu>
            <Collapsible
              v-for="project in projectLinks"
              :key="project.name"
              v-slot="{ open }"
              :open="projectsOpenState[project.name] ?? false"
              as-child
              @update:open="(isOpen) => toggleProject(project.name, isOpen)"
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

      </SidebarContent>

      <SidebarFooter>
        <div ref="accountMenuRef" class="relative">
          <div
            v-if="accountMenuOpen"
            class="absolute right-0 bottom-full mb-2 w-full rounded-2xl border border-border-subtle bg-bg-surface p-2 shadow-2xl group-data-[collapsible=icon]:w-72"
          >
            <div class="px-2 py-2 group-data-[collapsible=icon]:hidden">
              <p class="truncate text-sm font-medium text-text-display">
                {{ auth.isRefreshingProfile ? 'Refreshing session...' : auth.displayName }}
              </p>
              <p class="truncate font-mono text-[10px] uppercase tracking-[0.14em] text-text-secondary">
                {{ auth.user?.email || 'Session active' }}
              </p>
            </div>

            <div class="my-2 border-t border-border-subtle" />

            <div class="space-y-1">
              <button
                v-for="account in accountOptions"
                :key="account.name"
                class="flex w-full items-center gap-3 rounded-xl border px-3 py-2.5 text-left text-sm transition-all"
                :class="activeAccount.name === account.name
                  ? 'border-border-visible bg-bg-primary text-text-display'
                  : 'border-transparent text-text-secondary hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display'"
                type="button"
                @click="selectAccount(account)"
              >
                <div class="flex size-8 shrink-0 items-center justify-center rounded-lg border border-border-visible bg-bg-primary text-text-display">
                  <component :is="account.icon" class="size-4" />
                </div>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-medium text-current">
                    {{ account.name }}
                  </p>
                  <p class="truncate text-xs text-text-secondary">
                    {{ account.plan }}
                  </p>
                </div>
              </button>
            </div>

            <div class="my-2 border-t border-border-subtle" />

            <div class="space-y-1">
              <button
                class="flex w-full items-center gap-3 rounded-xl border border-transparent px-3 py-2.5 text-left text-sm text-text-secondary transition-colors hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display"
                type="button"
                @click="navigateToSettings"
              >
                <Settings class="size-4 shrink-0" />
                <span>Account settings</span>
              </button>

              <div class="flex items-center gap-3 rounded-xl border border-transparent px-3 py-2.5 text-sm text-text-secondary">
                <ThemeToggle />
                <span>Theme</span>
                <span class="ml-auto font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
                  {{ settings.currentTheme }}
                </span>
              </div>

              <button
                class="flex w-full items-center gap-3 rounded-xl border border-transparent px-3 py-2.5 text-left text-sm text-text-secondary transition-colors hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display"
                type="button"
                @click="handleLogout"
              >
                <LogOut class="size-4 shrink-0" />
                <span>{{ $t('nav.logout') }}</span>
              </button>
            </div>
          </div>

          <button
            class="flex w-full items-center gap-3 rounded-2xl border border-border-subtle bg-bg-surface/80 p-3 text-left transition-all hover:border-border-visible hover:bg-bg-surface group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:px-2 group-data-[collapsible=icon]:py-2"
            type="button"
            @click.stop="toggleAccountMenu"
          >
            <div class="flex size-10 shrink-0 items-center justify-center rounded-xl border border-border-visible bg-bg-primary font-mono text-xs font-bold text-text-display">
              {{ auth.userInitials }}
            </div>

            <div class="min-w-0 flex-1 group-data-[collapsible=icon]:hidden">
              <p class="truncate text-sm font-medium text-text-display">
                {{ activeAccount.name }}
              </p>
              <p class="truncate font-mono text-[10px] uppercase tracking-[0.14em] text-text-secondary">
                {{ auth.user?.email || activeAccount.plan }}
              </p>
            </div>

            <ChevronsUpDown class="hidden size-4 text-text-secondary group-data-[collapsible=icon]:hidden md:block" />
          </button>
        </div>
      </SidebarFooter>

      <SidebarRail />
    </Sidebar>

    <SidebarInset>
      <div class="flex min-w-0 flex-1 flex-col">
        <header class="sticky top-0 z-20 border-b border-border-subtle bg-bg-primary/90 backdrop-blur">
          <div class="flex h-16 items-center justify-between gap-4 px-4 md:px-6 lg:px-8">
            <div class="flex min-w-0 items-center gap-3">
              <SidebarTrigger class="rounded-xl border border-border-visible bg-bg-surface text-text-display hover:bg-bg-primary">
                <PanelLeft class="size-4" />
                <span class="sr-only">Toggle navigation</span>
              </SidebarTrigger>

              <div class="min-w-0">
                <p class="font-mono text-[10px] uppercase tracking-[0.18em] text-text-secondary">
                  Workspace
                </p>
                <h1 class="truncate text-sm font-medium text-text-display md:text-base">
                  {{ $t(`nav.${currentSectionLabel}`) || currentSectionLabel }}
                </h1>
              </div>
            </div>

            <div class="hidden items-center gap-3 lg:flex">
              <div class="rounded-full border border-border-visible bg-bg-surface px-3 py-1.5 font-mono text-[10px] uppercase tracking-[0.16em] text-text-secondary">
                {{ headerSummary }}
              </div>
            </div>

            <div class="flex items-center gap-2 md:gap-3">
              <div class="flex items-center rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[10px]">
                <button
                  class="cursor-pointer rounded-full px-2.5 py-1 font-bold transition-colors"
                  :class="settings.currentLocale === 'en' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
                  @click="settings.setLocale('en')"
                >
                  EN
                </button>
                <button
                  class="cursor-pointer rounded-full px-2.5 py-1 font-bold transition-colors"
                  :class="settings.currentLocale === 'es' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
                  @click="settings.setLocale('es')"
                >
                  ES
                </button>
              </div>

            <div class="hidden items-center rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[10px] md:flex">
              <button
                class="cursor-pointer rounded-full px-2.5 py-1 font-bold transition-colors"
                :class="settings.currentTheme === 'dark' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
                @click="settings.setTheme('dark')"
              >
                dark
              </button>
              <button
                class="cursor-pointer rounded-full px-2.5 py-1 font-bold transition-colors"
                :class="settings.currentTheme === 'light' ? 'bg-text-display text-bg-primary' : 'text-text-secondary hover:text-text-display'"
                @click="settings.setTheme('light')"
              >
                light
              </button>
            </div>

            </div>
          </div>
        </header>

        <main class="dot-grid flex-1 overflow-y-auto px-4 py-6 md:px-6 lg:px-8 lg:py-8">
          <div class="mx-auto w-full max-w-7xl">
            <RouterView />
          </div>
        </main>
      </div>
    </SidebarInset>
    </SidebarProvider>
  </TooltipProvider>
</template>

