<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAdminAuthStore } from '@/stores/auth.store'

const { t } = useI18n()
const router = useRouter()
const authStore = useAdminAuthStore()

const navItems = computed(() => [
  {
    name: 'dashboard',
    label: t('nav.dashboard'),
    permission: 'platform.dashboard.read',
    icon: '⊞',
  },
  {
    name: 'waitlist',
    label: t('nav.waitlist'),
    permission: 'platform.waitlist.read',
    icon: '☰',
  },
  {
    name: 'users',
    label: t('nav.users'),
    permission: 'platform.users.read',
    icon: '👥',
  },
  {
    name: 'audit',
    label: t('nav.audit'),
    permission: 'platform.audit.read',
    icon: '📋',
  },
].filter(item => authStore.hasPermission(item.permission)))

async function signOut() {
  await fetch('/api/auth/logout', { method: 'POST' })
  authStore.clearSession()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="min-h-screen flex bg-slate-950 text-slate-100">
    <!-- Sidebar -->
    <aside
      class="w-64 flex flex-col bg-slate-900 border-r border-slate-800"
      aria-label="Platform administration navigation"
    >
      <!-- Brand header -->
      <div class="p-6 border-b border-slate-800">
        <p class="text-xs uppercase tracking-widest text-amber-400 font-semibold mb-1">
          {{ t('auth.platformAdmin') }}
        </p>
        <p class="text-sm text-slate-400 truncate">
          {{ authStore.principal?.email }}
        </p>
        <div class="mt-2 flex flex-wrap gap-1">
          <span
            v-for="role in authStore.principal?.platformRoles"
            :key="role"
            class="text-xs px-2 py-0.5 rounded bg-amber-900/40 text-amber-300"
          >
            {{ role }}
          </span>
        </div>
      </div>

      <!-- Nav links -->
      <nav class="flex-1 p-4 space-y-1">
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          :to="{ name: item.name }"
          class="flex items-center gap-3 px-3 py-2 rounded-lg text-sm text-slate-300 hover:bg-slate-800 hover:text-white transition-colors"
          active-class="bg-slate-800 text-white"
          :aria-label="item.label"
        >
          <span aria-hidden="true">{{ item.icon }}</span>
          {{ item.label }}
        </RouterLink>
      </nav>

      <!-- Sign out -->
      <div class="p-4 border-t border-slate-800">
        <button
          class="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          @click="signOut"
        >
          {{ t('auth.signOut') }}
        </button>
      </div>
    </aside>

    <!-- Main content -->
    <main class="flex-1 overflow-auto" id="main-content" tabindex="-1">
      <RouterView />
    </main>
  </div>
</template>
