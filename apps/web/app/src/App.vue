<script setup lang="ts">
import type { Component } from 'vue'
import {
  AudioWaveform,
  BarChart3,
  CalendarDays,
  ChevronsUpDown,
  GalleryVerticalEnd,
  LayoutGrid,
  LogOut,
  PanelLeft,
  Plus,
  Settings,
  Sparkles,
  Users,
} from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
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
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
} from '@/components/ui/sidebar'
import { useAuthStore } from '@/stores/auth'
import { useSettingsStore } from '@/stores/settings'
import { usePublishingStore, type Channel } from '@/stores/publishing'
import { getProviderBadge } from '@/lib/provider-styles'

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

interface AccountOption {
  name: string
  plan: string
  icon: Component
}

interface SidebarChannel extends Channel {
  badge: string
  queuedCount: number
}

interface ConnectChannel {
  id: string
  label: string
  badge: string
}

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const settings = useSettingsStore()
const { t } = useI18n()
const publishingStore = usePublishingStore()
const accountMenuOpen = ref(false)
const accountMenuRef = ref<HTMLElement | null>(null)

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
          const count = totalQueuedCount.value
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

const queuedCounts = computed(() => {
  const counts = new Map<string, number>()
  let total = 0
  for (const pub of publishingStore.publications) {
    if (pub.status !== 'QUEUED') continue
    total++
    for (const provider of pub.channels as string[]) {
      counts.set(provider, (counts.get(provider) ?? 0) + 1)
    }
  }
  return { counts, total }
})

const sidebarChannels = computed<SidebarChannel[]>(() =>
  publishingStore.channels.map((channel) => ({
    ...channel,
    badge: getProviderBadge(channel.provider),
    queuedCount: queuedCounts.value.counts.get(channel.provider) ?? 0,
  })),
)

const totalQueuedCount = computed(() => queuedCounts.value.total)

const connectChannels = computed<ConnectChannel[]>(() => [
  { id: 'linkedin', label: t('channels.linkedinProfile'), badge: 'in' },
  { id: 'threads', label: 'Threads', badge: '@' },
  { id: 'bluesky', label: 'Bluesky', badge: 'b' },
  { id: 'facebook', label: 'Facebook', badge: 'f' },
])

const activeChannelProvider = computed(() => publishingStore.filterChannel)

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

function isSchedulerRoute() {
  return route.path === '/scheduler'
}

async function showAllChannels() {
  publishingStore.filterChannel = ''
  publishingStore.filterSocialAccountId = ''
  await router.push('/scheduler')
}

async function selectChannel(channel: SidebarChannel) {
  publishingStore.filterChannel = channel.provider
  publishingStore.filterSocialAccountId = ''
  await router.push('/scheduler')
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
  try {
    await auth.logout()
  } catch (e) {
    console.error('Logout failed', e)
  } finally {
    await router.replace('/login')
  }
}

function navigateToSettings() {
  router.push('/settings')
  closeAccountMenu()
}

const connectMessage = ref('')
let connectTimeout: ReturnType<typeof setTimeout> | null = null

async function handleConnectChannel(channel: ConnectChannel) {
  if (channel.id === 'linkedin') {
    connectMessage.value = t('channels.connectingLinkedIn')
    try {
      await publishingStore.connectLinkedInPersonalProfile()
    } catch (err) {
      connectMessage.value = err instanceof Error ? err.message : t('channels.connectLinkedInFailed')
      if (connectTimeout) clearTimeout(connectTimeout)
      connectTimeout = setTimeout(() => {
        connectMessage.value = ''
      }, 3500)
    }
    return
  }

  connectMessage.value = `${channel.label} ${channel.id === 'threads' ? 'coming soon' : 'connection available soon'}`
  if (connectTimeout) clearTimeout(connectTimeout)
  connectTimeout = setTimeout(() => {
    connectMessage.value = ''
  }, 3500)
}

function handleMoreChannels() {
  connectMessage.value = 'More channels coming soon'
  if (connectTimeout) clearTimeout(connectTimeout)
  connectTimeout = setTimeout(() => {
    connectMessage.value = ''
  }, 3500)
}

onBeforeUnmount(() => {
  if (connectTimeout) clearTimeout(connectTimeout)
})

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

watch(() => route.path, () => {
  closeAccountMenu()
})

watch(
  () => auth.isAuthenticated,
  (isAuthenticated) => {
    if (isAuthenticated) {
      publishingStore.fetchChannels().catch((err) => {
        console.warn('Unable to load connected channels', err)
      })
    }
  },
  { immediate: true },
)

onMounted(() => {
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

        <SidebarGroup class="gap-2">
          <SidebarGroupLabel class="group-data-[collapsible=icon]:hidden">
            {{ $t('channels.title') }}
          </SidebarGroupLabel>

          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton
                :is-active="isSchedulerRoute() && !activeChannelProvider"
                :tooltip="$t('channels.all')"
                v-slot="{ className, collapsed }"
              >
                <button :class="className" type="button" @click="showAllChannels">
                  <Users class="size-4 shrink-0" />
                  <span v-if="!collapsed" class="truncate">{{ $t('channels.all') }}</span>
                  <span
                    v-if="!collapsed"
                    class="ml-auto rounded-full border border-border-visible bg-bg-primary px-2 py-0.5 font-mono text-[9px] uppercase tracking-[0.12em] text-text-secondary"
                  >
                    {{ totalQueuedCount < 10 ? `0${totalQueuedCount}` : totalQueuedCount }}
                  </span>
                </button>
              </SidebarMenuButton>
            </SidebarMenuItem>

            <SidebarMenuItem
              v-for="channel in sidebarChannels"
              :key="channel.id"
            >
              <SidebarMenuButton
                :is-active="isSchedulerRoute() && activeChannelProvider === channel.provider"
                :tooltip="`${channel.name} · ${channel.handle}`"
                v-slot="{ className, collapsed }"
              >
                <button :class="className" type="button" @click="selectChannel(channel)">
                  <span class="relative flex size-5 shrink-0 items-center justify-center">
                    <img
                      :src="channel.avatar"
                      class="size-5 rounded-full border border-border-visible object-cover grayscale"
                      alt=""
                    />
                    <span class="absolute -right-1 -bottom-1 flex size-3.5 items-center justify-center rounded-full border border-bg-surface bg-bg-primary font-mono text-[7px] font-bold uppercase leading-none text-text-display">
                      {{ channel.badge }}
                    </span>
                  </span>

                  <span v-if="!collapsed" class="min-w-0 flex-1 text-left">
                    <span class="block truncate text-sm">{{ channel.name }}</span>
                    <span class="block truncate font-mono text-[9px] uppercase tracking-[0.08em] text-text-secondary">
                      {{ channel.status === 'ACTIVE' ? $t('channels.active') : $t('channels.inactive') }}
                    </span>
                  </span>

                  <span
                    v-if="!collapsed"
                    class="ml-auto font-mono text-[10px] text-text-secondary"
                  >
                    {{ channel.queuedCount }}
                  </span>
                </button>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>

          <div class="mt-2 space-y-1 border-t border-border-subtle pt-3 group-data-[collapsible=icon]:hidden">
            <p class="px-2 font-mono text-[9px] uppercase tracking-[0.16em] text-text-secondary">
              {{ $t('channels.connect') }}
            </p>

            <button
              v-for="channel in connectChannels"
              :key="channel.id"
              class="flex w-full items-center gap-2 rounded-lg border border-transparent px-2 py-1.5 text-left text-xs text-text-secondary transition-colors hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display"
              type="button"
              @click="handleConnectChannel(channel)"
            >
              <span class="flex size-5 shrink-0 items-center justify-center rounded-md border border-border-visible bg-bg-primary font-mono text-[9px] font-bold uppercase text-text-display">
                {{ channel.badge }}
              </span>
              <span class="min-w-0 flex-1 truncate">{{ channel.label }}</span>
              <span class="font-mono text-[9px] uppercase tracking-[0.1em]">+ {{ $t('channels.connectAction') }}</span>
            </button>

            <button
              class="flex w-full items-center gap-2 rounded-lg border border-dashed border-border-visible px-2 py-1.5 text-left font-mono text-[9px] uppercase tracking-[0.12em] text-text-secondary transition-colors hover:border-text-secondary hover:text-text-display"
              type="button"
              @click="handleMoreChannels"
            >
              <Plus class="size-3.5" />
              <span class="truncate">{{ $t('channels.more') }}</span>
            </button>

            <Transition name="fade">
              <p v-if="connectMessage" class="mt-2 px-2 font-mono text-[9px] uppercase tracking-[0.12em] text-text-secondary">
                {{ connectMessage }}
              </p>
            </Transition>
          </div>
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

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
