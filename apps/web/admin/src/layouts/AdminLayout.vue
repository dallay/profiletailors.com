<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import lightOnDarkLogoUrl from '@shared/assets/profiletailors-logotype-light.svg'
import { useAdminAuthStore, type PlatformPermission } from '@/stores/auth.store'

const { t } = useI18n()
const router = useRouter()
const authStore = useAdminAuthStore()

interface NavItem {
  name: string
  label: string
  permission: PlatformPermission
  icon: string
}

const navItems = computed<NavItem[]>(() =>
  (
    [
      {
        name: 'dashboard',
        label: t('nav.dashboard'),
        permission: 'platform.dashboard.read',
        icon: '◈',
      },
      {
        name: 'waitlist',
        label: t('nav.waitlist'),
        permission: 'platform.waitlist.read',
        icon: '≡',
      },
      {
        name: 'users',
        label: t('nav.users'),
        permission: 'platform.users.read',
        icon: '◎',
      },
      {
        name: 'audit',
        label: t('nav.audit'),
        permission: 'platform.audit.read',
        icon: '▤',
      },
    ] as NavItem[]
  ).filter(item => authStore.hasPermission(item.permission)),
)

async function signOut() {
  await authStore.signOut()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="admin-shell flex min-h-screen bg-bg-primary text-text-body">
    <aside
      class="admin-sidebar flex w-64 shrink-0 flex-col border-r border-border-subtle bg-bg-surface"
      :aria-label="t('nav.platformAdministration')"
    >
      <div class="border-b border-border-subtle p-6">
        <img :src="lightOnDarkLogoUrl" alt="" class="mb-5 h-10 w-9" aria-hidden="true">
        <p class="label-mono mb-1 text-text-secondary">
          {{ t('auth.platformAdmin') }}
        </p>
        <p class="truncate text-sm text-text-secondary">
          {{ authStore.principal?.email }}
        </p>
        <div class="mt-2 flex flex-wrap gap-1">
          <span
            v-for="role in authStore.principal?.platformRoles"
            :key="role"
            class="status-badge status-badge-neutral"
          >
            {{ role }}
          </span>
        </div>
      </div>

      <nav class="flex-1 space-y-1 p-4">
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          :to="{ name: item.name }"
          class="admin-nav-link flex items-center gap-3 rounded-xl px-3 py-2 text-sm text-text-secondary transition-colors"
          active-class="admin-nav-link-active"
          :aria-label="item.label"
        >
          <span aria-hidden="true">{{ item.icon }}</span>
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="border-t border-border-subtle p-4">
        <button
          class="admin-nav-link w-full rounded-xl px-3 py-2 text-left text-sm text-text-secondary transition-colors"
          @click="signOut"
        >
          {{ t('auth.signOut') }}
        </button>
      </div>
    </aside>

    <main class="min-w-0 flex-1 overflow-auto" id="main-content" tabindex="-1">
      <RouterView />
    </main>
  </div>
</template>
