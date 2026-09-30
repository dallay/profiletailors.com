<script setup lang="ts">
import { computed, onBeforeUnmount, nextTick, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  Bell,
  ChevronRight,
  LayoutDashboard,
  ListChecks,
  LogOut,
  MailPlus,
  Menu,
  PanelsTopLeft,
  ScrollText,
  Settings2,
  ShieldAlert,
  Users,
  X,
} from '@lucide/vue'
import type { Component } from 'vue'
import lightOnDarkLogoUrl from '@shared/assets/profiletailors-logotype-light.svg'
import { useAdminAuthStore } from '@/stores/auth.store'
import { visibleNavEntries } from '@/router/nav-registry'
import { VersionBadge } from '@profiletailors/vue-ui'
import Button from '@/components/ui/AdminButton.vue'
import BackendVersionBadge from '@/shared/ui/BackendVersionBadge.vue'

const { t } = useI18n()
const gitSha = __GIT_SHA__
const router = useRouter()
const authStore = useAdminAuthStore()
const mobileNavOpen = ref(false)
const isMobileViewport = useMediaQuery('(max-width: 767px)')
const mobileNavButton = ref<HTMLButtonElement | null>(null)
const mobileCloseButton = ref<HTMLButtonElement | null>(null)
const mobileSidebar = ref<HTMLElement | null>(null)

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

const groupOrder = ['operations', 'observability', 'trust', 'system'] as const
const navGroups = computed(() => {
  const entries = visibleNavEntries((permission) => authStore.hasPermission(permission))
  return groupOrder
    .map((group) => ({
      key: group,
      label: t(`nav.groups.${group}`),
      items: entries
        .filter((entry) => entry.group === group)
        .map((entry) => ({ ...entry, label: t(entry.labelKey), iconComponent: iconByName[entry.icon] })),
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

watch(() => router.currentRoute.value.fullPath, () => {
  mobileNavOpen.value = false
})

function trapMobileNavigationFocus(event: KeyboardEvent) {
  if (!isMobileViewport.value || !mobileNavOpen.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    mobileNavOpen.value = false
    return
  }
  if (event.key !== 'Tab') return

  const sidebar = mobileSidebar.value
  if (!sidebar) return
  const focusable = Array.from(sidebar.querySelectorAll<HTMLElement>(
    'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
  ))
  const first = focusable[0]
  const last = focusable.at(-1)
  if (!first || !last) {
    event.preventDefault()
    sidebar.focus()
    return
  }

  const activeIsOutside = !sidebar.contains(document.activeElement)
  if (event.shiftKey && (document.activeElement === first || activeIsOutside)) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && (document.activeElement === last || activeIsOutside)) {
    event.preventDefault()
    first.focus()
  }
}

watch(mobileNavOpen, async (open) => {
  if (!isMobileViewport.value) return
  if (open) {
    document.addEventListener('keydown', trapMobileNavigationFocus)
    await nextTick()
    mobileCloseButton.value?.focus()
  } else {
    document.removeEventListener('keydown', trapMobileNavigationFocus)
    await nextTick()
    mobileNavButton.value?.focus()
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', trapMobileNavigationFocus)
})

async function signOut() {
  await authStore.signOut()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="admin-shell flex min-h-screen bg-bg-primary text-text-body">
    <button
      v-if="mobileNavOpen"
      type="button"
      class="admin-nav-backdrop fixed inset-0 z-30 bg-black/70 md:hidden"
      :aria-label="t('common.close')"
      @click="mobileNavOpen = false"
    />

    <aside
      class="admin-sidebar fixed inset-y-0 left-0 z-40 flex w-[min(19rem,86vw)] -translate-x-full flex-col border-r border-border-subtle bg-bg-surface transition-transform duration-200 md:sticky md:top-0 md:w-64 md:translate-x-0"
      :class="{ 'admin-sidebar-open': mobileNavOpen }"
      :aria-label="t('nav.platformAdministration')"
      :role="isMobileViewport && mobileNavOpen ? 'dialog' : undefined"
      :aria-modal="isMobileViewport && mobileNavOpen ? 'true' : undefined"
      :inert="isMobileViewport && !mobileNavOpen"
      ref="mobileSidebar"
    >
      <div class="flex min-h-[84px] items-center justify-between border-b border-border-subtle px-5">
        <img :src="lightOnDarkLogoUrl" alt="Profile Tailors" class="h-9 w-auto max-w-[140px] object-contain object-left">
        <button
          type="button"
          class="admin-icon-button md:hidden"
          :aria-label="t('common.close')"
          @click="mobileNavOpen = false"
          ref="mobileCloseButton"
        >
          <X :size="18" aria-hidden="true" />
        </button>
      </div>

      <div class="border-b border-border-subtle px-5 py-4">
        <p class="label-mono mb-1 text-text-secondary">{{ t('auth.platformAdmin') }}</p>
        <p class="truncate text-sm text-text-body">{{ authStore.principal?.email }}</p>
        <div class="mt-3 flex flex-wrap gap-1.5">
          <span
            v-for="role in authStore.principal?.platformRoles"
            :key="role"
            class="status-badge status-badge-neutral"
          >{{ role }}</span>
        </div>
      </div>

      <nav class="min-h-0 flex-1 space-y-5 overflow-y-auto px-3 py-5">
        <section v-for="group in navGroups" :key="group.key" :aria-label="group.label">
          <h2 class="label-mono px-3 pb-2 text-[10px] text-text-secondary">{{ group.label }}</h2>
          <div class="space-y-1">
            <RouterLink
              v-for="item in group.items"
              :key="item.routeName"
              :to="{ name: item.routeName }"
              class="admin-nav-link flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm text-text-secondary transition-colors"
              active-class="admin-nav-link-active"
              :aria-label="item.label"
            >
              <component :is="item.iconComponent" :size="18" :stroke-width="1.7" aria-hidden="true" />
              <span class="min-w-0 flex-1 truncate">{{ item.label }}</span>
              <ChevronRight class="admin-nav-current" :size="14" aria-hidden="true" />
            </RouterLink>
          </div>
        </section>
      </nav>

      <div class="border-t border-border-subtle px-3 py-3">
        <Button variant="ghost" class="w-full justify-start px-3" @click="signOut">
          <LogOut :size="17" aria-hidden="true" />
          {{ t('auth.signOut') }}
        </Button>
      </div>

      <div class="border-t border-border-subtle px-4 py-3">
        <div class="flex flex-col gap-1">
          <VersionBadge />
          <BackendVersionBadge v-if="authStore.hasPermission('platform.system.build-info.read')" />
        </div>
        <a
          :href="`https://github.com/dallay/profiletailors.com/commit/${gitSha}`"
          class="mt-2 inline-flex min-h-11 items-center text-xs text-text-secondary transition-colors hover:text-text-display focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          {{ t('common.sourceCode') }}
        </a>
      </div>
    </aside>

    <main class="admin-main min-w-0 flex-1" id="main-content" tabindex="-1" :inert="isMobileViewport && mobileNavOpen">
      <header class="admin-mobile-header sticky top-0 z-20 flex min-h-14 items-center gap-3 border-b border-border-subtle bg-bg-primary px-4 md:hidden">
        <button
          type="button"
          class="admin-icon-button"
          :aria-label="t('nav.openNavigation')"
          :aria-expanded="mobileNavOpen"
          ref="mobileNavButton"
          @click="mobileNavOpen = true"
        >
          <Menu :size="19" aria-hidden="true" />
        </button>
        <span class="min-w-0 flex-1 truncate text-sm font-medium text-text-display">{{ pageTitle }}</span>
        <span class="label-mono text-[10px] text-text-secondary">PT</span>
      </header>
      <RouterView />
    </main>
  </div>
</template>
