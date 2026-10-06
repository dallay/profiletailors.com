<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterView, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  Bell,
  LayoutDashboard,
  ListChecks,
  LogOut,
  MailPlus,
  PanelsTopLeft,
  ScrollText,
  Settings2,
  ShieldAlert,
  Users,
} from '@lucide/vue'
import type { Component } from 'vue'
import { DashboardHeader, DashboardShell } from '@profiletailors/vue-ui/shell'
import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from '@profiletailors/vue-ui/shell/sidebar'
import lightOnDarkLogoUrl from '@shared/assets/profiletailors-logotype-light.svg'
import { useAdminAuthStore } from '@/stores/auth.store'
import { visibleNavEntries } from '@/router/nav-registry'
import { VersionBadge } from '@profiletailors/vue-ui'
import BackendVersionBadge from '@/shared/ui/BackendVersionBadge.vue'

const { t } = useI18n()
const gitSha = __GIT_SHA__
const router = useRouter()
const authStore = useAdminAuthStore()

const iconByName: Record<string, Component> = {
  LayoutDashboard,
  ListChecks,
  MailPlus,
  Users,
  ScrollText,
  PanelsTopLeft,
  Bell,
  ShieldAlert,
  Settings2,
}

const fallbackIcon: Component = LayoutDashboard

function resolveIcon(name: string): Component {
  return iconByName[name] ?? fallbackIcon
}

const groupOrder = ['operations', 'observability', 'trust', 'system'] as const
const navGroups = computed(() => {
  const entries = visibleNavEntries((permission) => authStore.hasPermission(permission))
  return groupOrder
    .map((group) => ({
      key: group,
      label: t(`nav.groups.${group}`),
      items: entries
        .filter((entry) => entry.group === group)
        .map((entry) => ({
          key: entry.key,
          labelKey: entry.labelKey,
          label: t(entry.labelKey),
          routeName: entry.routeName,
          iconComponent: resolveIcon(entry.icon),
        })),
    }))
    .filter((group) => group.items.length > 0)
})

const pageTitle = computed(() => {
  const entry = navGroups.value.flatMap((group) => group.items)
    .find((item) => item.routeName === router.currentRoute.value.name)
  if (entry) return entry.label
  if (router.currentRoute.value.name === 'waitlist-entry') return t('nav.waitlist')
  if (router.currentRoute.value.name === 'user-detail') return t('nav.users')
  if (router.currentRoute.value.name === 'governance-detail') return t('nav.governance')
  return t('auth.platformAdmin')
})

const navPort = computed(() => ({ groups: navGroups.value }))

watch(
  () => authStore.isAuthenticated,
  (isAuthenticated) => {
    if (!isAuthenticated) void router.replace({ name: 'login' })
  },
)

async function signOut() {
  await authStore.signOut()
}
</script>

<template>
  <DashboardShell :nav="navPort">
    <template #header>
      <div class="space-y-1 px-1 group-data-[collapsible=icon]:hidden">
        <img :src="lightOnDarkLogoUrl" alt="Profile Tailors" class="h-9 w-auto max-w-[140px] object-contain object-left">
        <p class="label-mono text-text-secondary">{{ t('auth.platformAdmin') }}</p>
        <p class="truncate text-sm text-text-body">{{ authStore.principal?.email }}</p>
        <div class="flex flex-wrap gap-1.5">
          <span
            v-for="role in authStore.principal?.platformRoles"
            :key="role"
            class="status-badge status-badge-neutral"
          >{{ role }}</span>
        </div>
      </div>
    </template>

    <template #content>
      <SidebarGroup v-for="group in navGroups" :key="group.key" class="gap-2">
        <SidebarGroupLabel class="group-data-[collapsible=icon]:hidden">
          {{ group.label }}
        </SidebarGroupLabel>
        <SidebarMenu>
          <SidebarMenuItem v-for="item in group.items" :key="item.routeName">
            <RouterLink
              :to="{ name: item.routeName }"
              :title="item.label"
              class="flex w-full items-center gap-3 rounded-xl border border-transparent px-3 py-2 text-sm text-text-secondary transition-colors no-underline hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground group-data-[collapsible=icon]:px-0 group-data-[collapsible=icon]:justify-center"
              active-class="bg-sidebar-accent text-sidebar-accent-foreground border-sidebar-accent font-medium"
            >
              <component
                :is="item.iconComponent"
                :size="18"
                :stroke-width="1.7"
                class="size-4 shrink-0"
                aria-hidden="true"
              />
              <span class="sr-only">{{ item.label }}</span>
              <span class="min-w-0 flex-1 truncate group-data-[collapsible=icon]:hidden">{{ item.label }}</span>
            </RouterLink>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarGroup>
    </template>

    <template #account>
      <div class="border-t border-sidebar-border px-2 pt-2">
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              :tooltip="t('auth.signOut')"
              :aria-label="t('auth.signOut')"
              class="min-h-11 rounded-xl text-text-secondary hover:bg-sidebar-accent hover:text-sidebar-accent-foreground group-data-[collapsible=icon]:size-11!"
              @click="signOut"
            >
              <LogOut :size="18" :stroke-width="1.7" aria-hidden="true" />
              <span class="group-data-[collapsible=icon]:hidden">{{ t('auth.signOut') }}</span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </div>
    </template>

    <template #footer>
      <div class="space-y-1 px-3 pb-2 group-data-[collapsible=icon]:hidden">
        <div class="flex min-h-8 items-center justify-between gap-2">
          <VersionBadge />
          <a
            :href="`https://github.com/dallay/profiletailors.com/commit/${gitSha}`"
            class="inline-flex min-h-11 min-w-11 items-center justify-center rounded-md text-text-secondary transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            :aria-label="t('common.sourceCode')"
            :title="t('common.sourceCode')"
          >
            <svg viewBox="0 0 24 24" class="size-[18px]" fill="currentColor" aria-hidden="true">
              <path d="M12 .9a11.1 11.1 0 0 0-3.51 21.63c.55.1.76-.24.76-.53v-2.08c-3.1.67-3.76-1.32-3.76-1.32-.5-1.28-1.23-1.62-1.23-1.62-1.01-.69.08-.68.08-.68 1.12.08 1.7 1.15 1.7 1.15 1 1.7 2.61 1.21 3.24.92.1-.72.39-1.21.71-1.49-2.47-.28-5.07-1.24-5.07-5.5 0-1.21.43-2.2 1.15-2.97-.12-.28-.5-1.41.11-2.94 0 0 .94-.3 3.05 1.14a10.6 10.6 0 0 1 5.55 0c2.12-1.44 3.05-1.14 3.05-1.14.61 1.53.23 2.66.12 2.94.71.77 1.14 1.76 1.14 2.97 0 4.27-2.6 5.21-5.08 5.49.4.35.76 1.02.76 2.06V22c0 .29.2.63.77.53A11.1 11.1 0 0 0 12 .9Z" />
            </svg>
            <span class="sr-only">{{ t('common.sourceCode') }}</span>
          </a>
        </div>
        <BackendVersionBadge
          v-if="authStore.hasPermission('platform.system.build-info.read')"
        />
      </div>
    </template>

    <template #inset>
      <div class="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <DashboardHeader
          :eyebrow="t('auth.platformAdmin')"
          :title="pageTitle"
          :toggle-label="t('nav.openNavigation')"
        >
          <template #actions>
            <span class="label-mono text-[10px] text-text-secondary">PT</span>
          </template>
        </DashboardHeader>
        <main id="main-content" tabindex="-1" class="admin-main dot-grid min-w-0 flex-1 overflow-y-auto">
          <RouterView />
        </main>
      </div>
    </template>
  </DashboardShell>
</template>
