<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import ThemeToggle from '@/components/ThemeToggle.vue'
import { useAuthStore } from '@/stores/auth'
import { useSettingsStore } from '@/stores/settings'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const settings = useSettingsStore()

const isAuthRoute = computed(() => route.name === 'login' || route.name === 'register')
const currentSectionLabel = computed(() => {
  if (!route.name) {
    return 'dashboard'
  }

  return route.name === 'login' || route.name === 'register'
    ? 'login'
    : String(route.name)
})

function isActive(path: string) {
  return route.path === path
}

async function handleLogout() {
  auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <RouterView v-if="isAuthRoute" />

  <div v-else class="flex min-h-screen bg-bg-primary font-sans text-text-body transition-colors duration-250">
    <aside class="sticky top-0 flex h-screen w-64 flex-col justify-between border-r border-border-subtle bg-bg-primary">
      <div class="flex flex-col gap-8 py-6">
        <div class="flex items-center gap-2 px-6">
          <div class="size-2 animate-pulse rounded-full bg-text-display" />
          <span class="font-mono text-[11px] font-bold uppercase tracking-widest text-text-display">
            PROFILE TAILORS
          </span>
        </div>

        <nav class="flex flex-col gap-1 px-3">
          <RouterLink
            to="/"
            class="flex items-center gap-3 rounded-md px-3 py-2 font-mono text-[11px] font-bold uppercase tracking-[0.08em] transition-colors"
            :class="isActive('/') ? 'border border-border-visible bg-bg-surface text-text-display' : 'text-text-secondary hover:bg-bg-surface/50 hover:text-text-display'"
          >
            <svg role="img" aria-label="Dashboard" class="size-4 shrink-0" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6A2.25 2.25 0 0 1 6 3.75h2.25A2.25 2.25 0 0 1 10.5 6v2.25a2.25 2.25 0 0 1-2.25 2.25H6a2.25 2.25 0 0 1-2.25-2.25V6ZM3.75 15.75A2.25 2.25 0 0 1 6 13.5h2.25a2.25 2.25 0 0 1 2.25 2.25V18a2.25 2.25 0 0 1-2.25 2.25H6a2.25 2.25 0 0 1-2.25-2.25V6ZM13.5 6a2.25 2.25 0 0 1 2.25-2.25H18A2.25 2.25 0 0 1 20.25 6v2.25A2.25 2.25 0 0 1 18 10.5h-2.25a2.25 2.25 0 0 1-2.25-2.25V6ZM13.5 15.75a2.25 2.25 0 0 1 2.25-2.25H18a2.25 2.25 0 0 1 2.25 2.25V18A2.25 2.25 0 0 1 18 20.25h-2.25A2.25 2.25 0 0 1 13.5 18v-2.25Z"/></svg>
            {{ $t('nav.dashboard') }}
          </RouterLink>

          <RouterLink
            to="/scheduler"
            class="flex items-center gap-3 rounded-md px-3 py-2 font-mono text-[11px] font-bold uppercase tracking-[0.08em] transition-colors"
            :class="isActive('/scheduler') ? 'border border-border-visible bg-bg-surface text-text-display' : 'text-text-secondary hover:bg-bg-surface/50 hover:text-text-display'"
          >
            <svg role="img" aria-label="Scheduler" class="size-4 shrink-0" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 0 1 2.25-2.25h13.5A2.25 2.25 0 0 1 21 7.5v11.25m-18 0A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75m-18 0v-7.5A2.25 2.25 0 0 1 5.25 9h13.5A2.25 2.25 0 0 1 21 11.25v7.5m-9-6h.008v.008H12v-.008ZM12 15h.008v.008H12V15Zm0 2.25h.008v.008H12v-.008ZM9.75 15h.008v.008H9.75V15Zm0 2.25h.008v.008H9.75v-.008ZM7.5 15h.008v.008H7.5V15Zm0 2.25h.008v.008H7.5v-.008Zm6.75-4.5h.008v.008h-.008v-.008Zm0 2.25h.008v.008h-.008V15Zm0 2.25h.008v.008h-.008v-.008Zm2.25-4.5h.008v.008H16.5v-.008Zm0 2.25h.008v.008H16.5V15Z"/></svg>
            {{ $t('nav.scheduler') }}
          </RouterLink>

          <RouterLink
            to="/analytics"
            class="flex items-center gap-3 rounded-md px-3 py-2 font-mono text-[11px] font-bold uppercase tracking-[0.08em] transition-colors"
            :class="isActive('/analytics') ? 'border border-border-visible bg-bg-surface text-text-display' : 'text-text-secondary hover:bg-bg-surface/50 hover:text-text-display'"
          >
            <svg role="img" aria-label="Analytics" class="size-4 shrink-0" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 0 1 3 19.875v-6.75ZM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 0 1-1.125-1.125V8.625ZM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 0 1-1.125-1.125V4.125Z"/></svg>
            {{ $t('nav.analytics') }}
          </RouterLink>

          <RouterLink
            to="/settings"
            class="flex items-center gap-3 rounded-md px-3 py-2 font-mono text-[11px] font-bold uppercase tracking-[0.08em] transition-colors"
            :class="isActive('/settings') ? 'border border-border-visible bg-bg-surface text-text-display' : 'text-text-secondary hover:bg-bg-surface/50 hover:text-text-display'"
          >
            <svg role="img" aria-label="Settings" class="size-4 shrink-0" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.324.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 0 1 1.37.49l1.296 2.247a1.125 1.125 0 0 1-.26 1.43l-1.003.828c-.293.241-.438.613-.43.992a7.723 7.723 0 0 1 0 .255c-.008.378.137.75.43.991l1.004.827c.424.35.534.954.26 1.43l-1.298 2.247a1.125 1.125 0 0 1-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.47 6.47 0 0 1-.22.128c-.331.183-.581.495-.644.869l-.213 1.281c-.09.543-.56.94-1.11.94h-2.594c-.55 0-1.019-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 0 1-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 0 1-1.369-.49l-1.297-2.247a1.125 1.125 0 0 1 .26-1.43l1.004-.827c.292-.24.437-.613.43-.991a6.932 6.932 0 0 1 0-.255c.007-.38-.138-.751-.43-.992l-1.004-.827a1.125 1.125 0 0 1-.26-1.43l1.297-2.247a1.125 1.125 0 0 1 1.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.086.22-.128.332-.183.582-.495.644-.869l.214-1.28Z"/><path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z"/></svg>
            {{ $t('nav.settings') }}
          </RouterLink>
        </nav>
      </div>

      <div class="flex flex-col gap-4 border-t border-border-subtle bg-bg-primary p-4">
        <div class="flex items-center justify-between">
          <span class="font-mono text-[9px] uppercase tracking-wider text-text-secondary">
            {{ settings.currentTheme }} mode
          </span>
          <ThemeToggle />
        </div>
        <button
          class="w-full cursor-pointer text-left font-mono text-[9px] uppercase tracking-wider text-text-secondary transition-colors hover:text-text-display"
          @click="handleLogout"
        >
          [ {{ $t('nav.logout') }} ]
        </button>
      </div>
    </aside>

    <div class="flex min-w-0 flex-1 flex-col">
      <header class="sticky top-0 z-10 flex h-16 items-center justify-between border-b border-border-subtle bg-bg-primary px-8">
        <div class="flex items-center gap-4">
          <h1 class="font-mono text-xs font-bold uppercase tracking-widest text-text-display">
            {{ $t(`nav.${currentSectionLabel}`) || currentSectionLabel }}
          </h1>
        </div>

        <div class="flex items-center gap-6">
          <div class="flex items-center rounded-full border border-border-visible p-0.5 font-mono text-[10px]">
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

        <div class="flex items-center gap-3">
          <div class="flex size-8 items-center justify-center rounded-full border border-border-visible bg-bg-surface font-mono text-[9px] font-bold text-text-display">
            {{ auth.userInitials }}
          </div>
          <div class="hidden text-right sm:block">
            <p class="text-sm text-text-display">
              {{ auth.isRefreshingProfile ? 'Refreshing session...' : auth.displayName }}
            </p>
            <p class="font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
              {{ auth.user?.email }}
            </p>
          </div>
        </div>

        </div>
      </header>

      <main class="dot-grid flex-1 overflow-y-auto p-8 lg:p-12">
        <div class="mx-auto max-w-5xl">
          <RouterView />
        </div>
      </main>
    </div>
  </div>
</template>
