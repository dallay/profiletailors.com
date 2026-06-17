<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { LayoutGrid } from '@lucide/vue'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
} from '@/components/ui/sidebar'
import { TooltipProvider } from '@/components/ui/tooltip'
import AppHeader from './AppHeader.vue'
import { getProviderBadge } from '@/lib/provider-styles'
import { useAuthStore } from '@/stores/auth'
import { useWorkspaceStore } from '@/stores/workspace'
import { usePublishingStore, type Channel } from '@/stores/publishing'
import SidebarHeaderSection from '@/components/sidebar/SidebarHeaderSection.vue'
import SidebarNavSection, { type NavGroup } from '@/components/sidebar/SidebarNavSection.vue'
import SidebarChannelsSection, { type SidebarChannel } from '@/components/sidebar/SidebarChannelsSection.vue'
import SidebarConnectSection, { type ConnectChannel } from '@/components/sidebar/SidebarConnectSection.vue'
import SidebarAccountSection from '@/components/sidebar/SidebarAccountSection.vue'
import { useQueuedCounts } from '@/composables/useQueuedCounts'

// ---------------------------------------------------------------------------
// Stores
// ---------------------------------------------------------------------------

const auth = useAuthStore()
const workspace = useWorkspaceStore()
const publishingStore = usePublishingStore()
const router = useRouter()
const route = useRoute()

// ---------------------------------------------------------------------------
// Auth bootstrap watcher — fires on token change
// ---------------------------------------------------------------------------

watch(
  () => [auth.isAuthenticated, auth.accessToken] as const,
  ([isAuthenticated, accessToken]) => {
    if (isAuthenticated && accessToken) {
      workspace.loadWorkspaces(accessToken).catch((err) => {
        console.warn('Unable to load workspaces', err)
      })
      publishingStore.fetchChannels().catch((err) => {
        console.warn('Unable to load connected channels', err)
      })
    }
  },
  { immediate: true },
)

// ---------------------------------------------------------------------------
// Nav groups
// ---------------------------------------------------------------------------

const { total: totalQueuedCount, byProvider: queuedByProvider } = useQueuedCounts()

const navigationGroups = computed<NavGroup[]>(() => [
  {
    label: 'Workspace',
    items: [
      { labelKey: 'nav.dashboard', to: '/', icon: LayoutGrid },
      { labelKey: 'nav.scheduler', to: '/scheduler', icon: LayoutGrid },
      { labelKey: 'nav.analytics', to: '/analytics', icon: LayoutGrid, badge: 'Live' },
    ],
  },
  {
    label: 'System',
    items: [{ labelKey: 'nav.settings', to: '/settings', icon: LayoutGrid }],
  },
])

// ---------------------------------------------------------------------------
// Channels (drives SidebarChannelsSection)
// ---------------------------------------------------------------------------

const sidebarChannels = computed<SidebarChannel[]>(() =>
  (publishingStore.channels as Channel[]).map((channel) => ({
    ...channel,
    badge: getProviderBadge(channel.provider),
    queuedCount: queuedByProvider.value.get(channel.provider) ?? 0,
  })),
)

// ---------------------------------------------------------------------------
// Connect providers
// ---------------------------------------------------------------------------

const connectChannels = computed<ConnectChannel[]>(() => [
  { id: 'linkedin', label: 'LinkedIn profile', badge: 'in' },
  { id: 'threads', label: 'Threads', badge: '@' },
  { id: 'bluesky', label: 'Bluesky', badge: 'b' },
  { id: 'facebook', label: 'Facebook', badge: 'f' },
])

// ---------------------------------------------------------------------------
// Header pieces
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Sidebar handlers
// ---------------------------------------------------------------------------

function isSchedulerRoute() {
  return route.path === '/scheduler'
}

async function showAllChannels() {
  publishingStore.filterChannel = ''
  publishingStore.filterSocialAccountId = ''
  try {
    await router.push('/scheduler')
  } catch (e) {
    console.error('Failed to navigate to scheduler', e)
  }
}

async function selectChannel(channel: SidebarChannel) {
  publishingStore.filterChannel = channel.provider
  publishingStore.filterSocialAccountId = ''
  try {
    await router.push('/scheduler')
  } catch (e) {
    console.error('Failed to navigate to scheduler', e)
  }
}

function onNavNavigate(to: string) {
  router.push(to).catch((e) => console.error('Failed to navigate', e))
}

function selectWorkspace(ws: { workspaceId: string }) {
  workspace.setActiveWorkspaceId(ws.workspaceId)
}

async function handleConnectChannel(channel: ConnectChannel) {
  if (channel.id === 'linkedin') {
    try {
      await publishingStore.connectLinkedInPersonalProfile()
    } catch (err) {
      // Transient message is shown by SidebarConnectSection via useConnectMessage.
      console.error('Failed to connect LinkedIn', err)
    }
  }
}

function handleMoreChannels() {
  // Transient message is owned by SidebarConnectSection via useConnectMessage.
}

function onOpenSettings() {
  router.push('/settings').catch((err) => {
    console.error('Navigation to settings failed', err)
  })
}

async function handleLogout() {
  try {
    await auth.logout()
  } catch (e) {
    console.error('Logout failed', e)
  } finally {
    try {
      await router.replace('/login')
    } catch (e) {
      console.error('Navigation failed', e)
    }
  }
}

onBeforeUnmount(() => {
  // Reserved for future shell-level cleanup.
})
</script>

<template>
  <!--
    Skip-to-content link — FIRST focusable element in the shell. Hidden by
    default, surfaces on focus. Targets #main-content on the <main> below.
  -->
  <a
    href="#main-content"
    class="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded focus:bg-bg-surface focus:px-3 focus:py-2"
  >
    Skip to main content
  </a>

  <TooltipProvider>
    <SidebarProvider :default-open="true" class="bg-bg-primary font-sans text-text-body transition-colors duration-250">
      <Sidebar collapsible="icon">
        <SidebarHeader class="gap-3">
          <SidebarHeaderSection
            :active-workspace="workspace.activeWorkspace"
            :options="workspace.workspaces"
            :is-loading="workspace.isLoadingWorkspaces"
            @select="selectWorkspace"
          />
        </SidebarHeader>

        <SidebarContent class="gap-6">
          <SidebarGroup class="gap-2">
            <SidebarGroupLabel class="group-data-[collapsible=icon]:hidden">
              Workspace
            </SidebarGroupLabel>
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarNavSection
                  :groups="navigationGroups"
                  :total-queued-count="totalQueuedCount"
                  @navigate="onNavNavigate"
                />
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroup>

          <SidebarGroup class="gap-2">
            <SidebarGroupLabel class="group-data-[collapsible=icon]:hidden">
              {{ $t('channels.title') }}
            </SidebarGroupLabel>

            <SidebarChannelsSection
              :channels="sidebarChannels"
              :active-provider="publishingStore.filterChannel"
              :total-queued-count="totalQueuedCount"
              :is-scheduler-route="isSchedulerRoute()"
              @select-all="showAllChannels"
              @select-channel="selectChannel"
            />

            <SidebarConnectSection
              :providers="connectChannels"
              @connect="handleConnectChannel"
              @more="handleMoreChannels"
            />
          </SidebarGroup>
        </SidebarContent>

        <SidebarFooter>
          <SidebarAccountSection
            :user="{
              displayName: auth.displayName,
              email: auth.user?.email ?? null,
              initials: auth.userInitials,
              isRefreshing: auth.isRefreshingProfile,
            }"
            @open-settings="onOpenSettings"
            @logout="handleLogout"
          />
        </SidebarFooter>

        <SidebarRail />
      </Sidebar>

      <SidebarInset>
        <div class="flex min-w-0 flex-1 flex-col">
          <AppHeader />

          <main
            id="main-content"
            tabindex="-1"
            class="dot-grid flex-1 overflow-y-auto px-4 py-6 md:px-6 lg:px-8 lg:py-8"
          >
            <RouterView />
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
