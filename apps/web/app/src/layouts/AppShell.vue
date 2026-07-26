<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { extractFirstChannelId, useCalendarUrl } from '@modules/publishing/application/useCalendarUrl'
import { Images, LayoutGrid, Shield } from '@lucide/vue'
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
import {
  getProviderPresentation,
  PROVIDER_ACTIONS,
  type ProviderCatalogItem,
} from '@shared/lib/provider-presentation'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'
import { usePublishingStore, type Channel } from '@modules/publishing/infrastructure/publishing.store'
import SidebarHeaderSection from '@layouts/sidebar/SidebarHeaderSection.vue'
import SidebarNavSection, { type NavGroup } from '@layouts/sidebar/SidebarNavSection.vue'
import SidebarChannelsSection, { type SidebarChannel } from '@layouts/sidebar/SidebarChannelsSection.vue'
import SidebarConnectSection from '@layouts/sidebar/SidebarConnectSection.vue'
import SidebarAccountSection from '@layouts/sidebar/SidebarAccountSection.vue'
import UploadProgressToast from '@layouts/UploadProgressToast.vue'
import EmailVerificationBanner from '@layouts/EmailVerificationBanner.vue'
import { Toaster } from '@/components/ui/sonner'
import ConsentBanner from '@/components/consent/ConsentBanner.vue'
import CookieSettings from '@/components/consent/CookieSettings.vue'
import { useConsentStore } from '@modules/settings/infrastructure/consent.store'
import { useQueuedCounts } from '@modules/publishing/application/useQueuedCounts'

// ---------------------------------------------------------------------------
// Stores
// ---------------------------------------------------------------------------

const auth = useAuthStore()
const workspace = useWorkspaceStore()
const publishingStore = usePublishingStore()
const _consentStore = useConsentStore()
const showCookieSettings = ref(false)
const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const calendarUrl = useCalendarUrl()

// Page title for SPA route announcer (screen readers)
const pageTitle = computed(() => {
  const name = route.name
  if (typeof name === 'string' && t(`nav.${name}`) !== `nav.${name}`) {
    return t(`nav.${name}`)
  }
  if (name === 'linkedin-callback') return 'LinkedIn'
  return String(name ?? '')
})

const shouldShowEmailVerificationBanner = computed(
  () => auth.isAuthenticated && !auth.isEmailVerified,
)

// ---------------------------------------------------------------------------
// Auth/workspace bootstrap watchers
// ---------------------------------------------------------------------------

watch(
  () => [auth.isAuthenticated, auth.accessToken] as const,
  ([isAuthenticated, accessToken]) => {
    if (!isAuthenticated || !accessToken) return

    workspace.loadWorkspaces(accessToken).catch((err) => {
      console.warn('Unable to load workspaces', err)
    })
  },
  { immediate: true },
)

watch(
  () => [auth.isAuthenticated, workspace.activeWorkspaceId] as const,
  ([isAuthenticated, activeWorkspaceId]) => {
    if (!isAuthenticated || !activeWorkspaceId) return

    publishingStore.refreshWorkspaceData().catch((err) => {
      console.warn('Unable to load workspace publishing data', err)
    })
  },
  { immediate: true },
)

// ---------------------------------------------------------------------------
// Nav groups
// ---------------------------------------------------------------------------

const { total: totalQueuedCount, byProvider: queuedByProvider } = useQueuedCounts()

const navigationGroups = computed<NavGroup[]>(() => [
  {
    label: t('workspace.title'),
    items: [
      { labelKey: 'nav.dashboard', to: '/', icon: LayoutGrid },
      { labelKey: 'nav.scheduler', to: '/scheduler', icon: LayoutGrid },
      { labelKey: 'nav.analytics', to: '/analytics', icon: LayoutGrid, badge: 'Live' },
      { labelKey: 'nav.media', to: '/media', icon: Images },
      { labelKey: 'nav.governance', to: '/governance/takedown', icon: Shield },
    ],
  },
  {
    label: t('nav.system'),
    items: [{ labelKey: 'nav.settings', to: '/settings', icon: LayoutGrid }],
  },
])

// ---------------------------------------------------------------------------
// Channels (drives SidebarChannelsSection)
// ---------------------------------------------------------------------------

const sidebarChannels = computed<SidebarChannel[]>(() =>
  (publishingStore.channels as Channel[]).map((channel) => ({
    ...channel,
    badge: getProviderPresentation(channel.provider).badge,
    queuedCount: queuedByProvider.value.get(channel.provider) ?? 0,
  })),
)

// ---------------------------------------------------------------------------
// Header pieces
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Sidebar handlers
// ---------------------------------------------------------------------------

function isSchedulerRoute() {
  return route.path.startsWith('/scheduler')
}

async function showAllChannels() {
  try {
    if (isSchedulerRoute()) {
      await calendarUrl.setChannelIds([])
      return
    }

    await router.push({
      name: 'scheduler-calendar-week',
      query: {},
    })
  } catch (e) {
    console.error('Failed to navigate to scheduler', e)
  }
}

async function selectChannel(accountId: string) {
  try {
    if (isSchedulerRoute()) {
      await calendarUrl.setChannelIds([accountId])
      return
    }

    await router.push({
      name: 'scheduler-calendar-week',
      query: { 'channels[]': [accountId] },
    })
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

async function handleConnectProvider(provider: ProviderCatalogItem) {
  if (
    provider.state !== 'AVAILABLE' ||
    getProviderPresentation(provider.provider).action !== PROVIDER_ACTIONS.CONNECT_LINKEDIN_PERSONAL_PROFILE
  ) {
    return
  }

  try {
    await publishingStore.connectLinkedInPersonalProfile()
  } catch (err) {
    console.error('Failed to connect LinkedIn', err)
  }
}

function onOpenSettings() {
  router.push('/settings').catch((err) => {
    console.error('Navigation to settings failed', err)
  })
}

function openCookieSettings() {
  showCookieSettings.value = true
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

  <!-- SPA route announcer — announces page changes to screen readers -->
  <output
    aria-live="polite"
    aria-atomic="true"
    class="sr-only"
  >
    {{ pageTitle }}
  </output>

  <TooltipProvider>
    <SidebarProvider class="bg-bg-primary font-sans text-text-body transition-colors duration-250">
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
              {{ $t('workspace.title') }}
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
              :active-channel-id="extractFirstChannelId(route.query)"
              :total-queued-count="totalQueuedCount"
              :is-scheduler-route="isSchedulerRoute()"
              @select-all="showAllChannels"
              @select-channel="selectChannel"
            />

            <SidebarConnectSection
              :providers="publishingStore.providerCatalog"
              @connect="handleConnectProvider"
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
        <div class="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
          <AppHeader />

          <EmailVerificationBanner v-if="shouldShowEmailVerificationBanner" />

          <main
            id="main-content"
            tabindex="-1"
            :class="[
              'dot-grid flex-1 px-4 py-6 md:px-6 lg:px-8 lg:py-8',
              isSchedulerRoute() ? 'flex min-h-0 flex-col overflow-hidden' : 'overflow-y-auto',
            ]"
          >
            <RouterView />
          </main>

          <!-- Cookie settings footer link -->
          <div class="flex items-center justify-center border-t border-border-subtle px-4 py-2">
            <button
              type="button"
              class="text-xs text-text-secondary transition-colors hover:text-text-display hover:underline"
              data-testid="cookie-settings-link"
              @click="openCookieSettings"
            >
              {{ t('consent.footer.cookieSettings') }}
            </button>
          </div>

          <UploadProgressToast />
          <Toaster position="bottom-right" />
          <ConsentBanner />
          <CookieSettings v-model:open="showCookieSettings" />
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
