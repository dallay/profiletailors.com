<script setup lang="ts">
import { computed, type Component } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import lightOnDarkLogoUrl from '@shared/assets/profiletailors-logotype-light.svg'
import { useAdminAuthStore } from '@/stores/auth.store'
import { visibleNavEntries } from '@/router/nav-registry'
import { VersionBadge } from '@profiletailors/vue-ui'
import { Badge } from '@/components/ui/badge'
import {
  LayoutDashboardIcon,
  ListFilterIcon,
  MailIcon,
  UsersIcon,
  FileTextIcon,
  LayoutGridIcon,
  BellIcon,
  ShieldAlertIcon,
  SettingsIcon,
  LogOutIcon,
} from '@lucide/vue'

const { t } = useI18n()
const router = useRouter()
const authStore = useAdminAuthStore()

const iconMap: Record<string, Component> = {
  dashboard: LayoutDashboardIcon,
  waitlist: ListFilterIcon,
  'direct-invitations': MailIcon,
  users: UsersIcon,
  audit: FileTextIcon,
  overview: LayoutGridIcon,
  notifications: BellIcon,
  governance: ShieldAlertIcon,
  configuration: SettingsIcon,
}

interface NavItem {
  key: string
  name: string
  label: string
  iconComponent?: Component
}

const navItems = computed<NavItem[]>(() =>
  visibleNavEntries((permission) => authStore.hasPermission(permission)).map((entry) => ({
    key: entry.key,
    name: entry.routeName,
    label: t(entry.labelKey),
    iconComponent: iconMap[entry.key],
  })),
)

async function signOut() {
  await authStore.signOut()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="flex min-h-screen bg-background text-foreground">
    <aside
      class="flex w-64 shrink-0 flex-col border-r border-border bg-card"
      :aria-label="t('nav.platformAdministration')"
    >
      <div class="border-b border-border p-6">
        <img :src="lightOnDarkLogoUrl" alt="" class="mb-5 h-10 w-9" aria-hidden="true">
        <p class="label-mono mb-1 text-muted-foreground">
          {{ t('auth.platformAdmin') }}
        </p>
        <p class="truncate text-sm text-muted-foreground">
          {{ authStore.principal?.email }}
        </p>
        <div class="mt-2 flex flex-wrap gap-1">
          <Badge
            v-for="role in authStore.principal?.platformRoles"
            :key="role"
            variant="outline"
            class="text-[10px] font-mono uppercase"
          >
            {{ role }}
          </Badge>
        </div>
      </div>

      <nav class="flex-1 space-y-1 p-4">
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          :to="{ name: item.name }"
          class="flex items-center gap-3 rounded-xl px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          active-class="bg-muted text-foreground font-medium"
          :aria-label="item.label"
        >
          <component
            :is="item.iconComponent"
            v-if="item.iconComponent"
            class="size-4 shrink-0"
            aria-hidden="true"
          />
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="border-t border-border p-4">
        <button
          type="button"
          class="flex w-full items-center gap-3 rounded-xl px-3 py-2 text-left text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          @click="signOut"
        >
          <LogOutIcon class="size-4 shrink-0" aria-hidden="true" />
          {{ t('auth.signOut') }}
        </button>
      </div>

      <div class="border-t border-border px-4 py-3">
        <VersionBadge />
      </div>
    </aside>

    <main class="min-w-0 flex-1 overflow-auto" id="main-content" tabindex="-1">
      <RouterView />
    </main>
  </div>
</template>
