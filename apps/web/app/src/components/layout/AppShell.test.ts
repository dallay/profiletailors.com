import { describe, it, expect, vi, beforeEach } from 'vitest'
import { reactive } from 'vue'
import { mount } from '@vue/test-utils'

const routeState = reactive({
  name: 'scheduler-calendar-week',
  path: '/scheduler/calendar/week',
  query: {} as Record<string, unknown>,
})

const push = vi.fn().mockResolvedValue(undefined)
const replace = vi.fn().mockResolvedValue(undefined)

vi.mock('vue-router', () => ({
  RouterView: { template: '<div class="router-view" />' },
  useRoute: () => routeState,
  useRouter: () => ({ push, replace }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/composables/useCalendarUrl', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/composables/useCalendarUrl')>()

  return {
    ...actual,
    useCalendarUrl: () => ({
      state: {
        value: {
          surface:
            routeState.name === 'scheduler-calendar-month'
              ? 'calendar-month'
              : routeState.name === 'scheduler-list'
                ? 'list'
                : 'calendar-week',
          date: '2026-06-15',
          timezone: 'UTC',
          status: 'all',
          q: '',
          channelIds: Array.isArray(routeState.query['channels[]'])
            ? routeState.query['channels[]']
            : typeof routeState.query['channels[]'] === 'string'
              ? [routeState.query['channels[]']]
              : [],
        },
      },
      setChannelIds: vi.fn().mockImplementation(async (ids: string[]) => {
        routeState.query = ids.length ? { 'channels[]': ids } : {}
      }),
    }),
  }
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isAuthenticated: true,
    accessToken: 'test-token',
    displayName: 'Test User',
    user: { email: 'test@example.com' },
    userInitials: 'TU',
    isRefreshingProfile: false,
    logout: vi.fn().mockResolvedValue(undefined),
  }),
}))

vi.mock('@/stores/workspace', () => ({
  useWorkspaceStore: () => ({
    activeWorkspaceId: 'ws-1',
    activeWorkspace: null,
    workspaces: [],
    isLoadingWorkspaces: false,
    loadWorkspaces: vi.fn().mockResolvedValue(undefined),
    setActiveWorkspaceId: vi.fn(),
  }),
}))

vi.mock('@/stores/publishing', () => ({
  usePublishingStore: () => ({
    channels: [
      {
        id: 'acc-linkedin',
        accountId: 'acc-linkedin',
        name: 'LinkedIn',
        provider: 'linkedin',
        avatar: '',
        handle: '@company',
        status: 'ACTIVE',
      },
      {
        id: 'acc-bluesky',
        accountId: 'acc-bluesky',
        name: 'Bluesky',
        provider: 'twitter',
        avatar: '',
        handle: '@bluesky',
        status: 'ACTIVE',
      },
    ],
    fetchChannels: vi.fn().mockResolvedValue([]),
    connectLinkedInPersonalProfile: vi.fn().mockResolvedValue(undefined),
  }),
}))

vi.mock('@/lib/provider-styles', () => ({
  getProviderBadge: () => 'LI',
}))

vi.mock('@/composables/useQueuedCounts', () => ({
  useQueuedCounts: () => ({
    total: { value: 0 },
    byProvider: { value: new Map() },
  }),
}))

vi.mock('@/components/ui/sidebar', () => ({
  Sidebar: { template: '<div><slot /></div>' },
  SidebarContent: { template: '<div><slot /></div>' },
  SidebarFooter: { template: '<div><slot /></div>' },
  SidebarGroup: { template: '<div><slot /></div>' },
  SidebarGroupLabel: { template: '<div><slot /></div>' },
  SidebarHeader: { template: '<div><slot /></div>' },
  SidebarInset: { template: '<div><slot /></div>' },
  SidebarMenu: { template: '<div><slot /></div>' },
  SidebarMenuItem: { template: '<div><slot /></div>' },
  SidebarProvider: { template: '<div><slot /></div>' },
  SidebarRail: { template: '<div />' },
}))

vi.mock('@/components/ui/tooltip', () => ({
  TooltipProvider: { template: '<div><slot /></div>' },
}))

vi.mock('@/components/layout/AppHeader.vue', () => ({
  default: { template: '<div class="app-header" />' },
}))

vi.mock('@/components/UploadProgressToast.vue', () => ({
  default: { template: '<div class="upload-progress" />' },
}))

vi.mock('@/components/sidebar/SidebarHeaderSection.vue', () => ({
  default: { template: '<div class="sidebar-header" />' },
}))

vi.mock('@/components/sidebar/SidebarNavSection.vue', () => ({
  default: { template: '<div class="sidebar-nav" />' },
}))

vi.mock('@/components/sidebar/SidebarConnectSection.vue', () => ({
  default: { template: '<div class="sidebar-connect" />' },
}))

vi.mock('@/components/sidebar/SidebarAccountSection.vue', () => ({
  default: { template: '<div class="sidebar-account" />' },
}))

vi.mock('@/components/sidebar/SidebarChannelsSection.vue', () => ({
  default: {
    props: ['channels', 'activeChannelId', 'totalQueuedCount', 'isSchedulerRoute'],
    emits: ['selectAll', 'selectChannel'],
    template: `
      <div>
        <button data-testid="all-channels" @click="$emit('selectAll')">All channels</button>
        <button data-testid="channel-linkedin" @click="$emit('selectChannel', 'acc-linkedin')">LinkedIn</button>
        <button data-testid="channel-bluesky" @click="$emit('selectChannel', 'acc-bluesky')">Bluesky</button>
      </div>
    `,
  },
}))

vi.mock('@lucide/vue', () => ({
  Images: { template: '<svg />' },
  LayoutGrid: { template: '<svg />' },
}))

import AppShell from './AppShell.vue'

describe('AppShell scheduler sidebar navigation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.name = 'scheduler-calendar-week'
    routeState.path = '/scheduler/calendar/week'
    routeState.query = {}
  })

  it('clears channel filters without leaving scheduler when All channels is selected', async () => {
    routeState.query = { 'channels[]': ['acc-linkedin'] }
    const wrapper = mount(AppShell, {
      global: {
        mocks: {
          $t: (key: string) => key,
        },
      },
    })

    await wrapper.find('[data-testid="all-channels"]').trigger('click')

    expect(push).not.toHaveBeenCalled()
  })

  it('adds selected account id to scheduler route state when already on scheduler', async () => {
    const wrapper = mount(AppShell, {
      global: {
        mocks: {
          $t: (key: string) => key,
        },
      },
    })

    await wrapper.find('[data-testid="channel-linkedin"]').trigger('click')

    expect(push).not.toHaveBeenCalled()
  })

  it('navigates to canonical scheduler week route with channels[] when outside scheduler', async () => {
    routeState.name = 'dashboard'
    routeState.path = '/'
    const wrapper = mount(AppShell, {
      global: {
        mocks: {
          $t: (key: string) => key,
        },
      },
    })

    await wrapper.find('[data-testid="channel-bluesky"]').trigger('click')

    expect(push).toHaveBeenCalledWith({
      name: 'scheduler-calendar-week',
      query: { 'channels[]': ['acc-bluesky'] },
    })
  })
})
