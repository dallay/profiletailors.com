import { describe, it, expect, vi, beforeEach } from 'vitest'
import { reactive, nextTick } from 'vue'
import { mount } from '@vue/test-utils'

const routeState = reactive({
  name: 'scheduler-calendar-week',
  path: '/scheduler/calendar/week',
  query: {} as Record<string, unknown>,
})

const push = vi.fn().mockResolvedValue(undefined)
const replace = vi.fn().mockResolvedValue(undefined)
const authState = reactive({
  isAuthenticated: true,
  accessToken: 'test-token',
  displayName: 'Test User',
  user: { email: 'test@example.com', emailStatus: 'VERIFIED' as string | null },
  userInitials: 'TU',
  isRefreshingProfile: false,
  resendVerificationStatus: 'idle' as 'idle' | 'loading' | 'success' | 'error',
  resendVerificationError: null as string | null,
})
const logout = vi.fn().mockResolvedValue(undefined)
const resendVerificationEmail = vi.fn().mockImplementation(async () => {
  authState.resendVerificationStatus = 'success'
})

vi.mock('vue-router', () => ({
  RouterView: { template: '<div class="router-view" />' },
  useRoute: () => routeState,
  useRouter: () => ({ push, replace }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) =>
      ({
        'emailVerification.banner.title': 'Verify your email',
        'emailVerification.banner.description':
          'Publish, social connect, and media upload require email verification.',
        'emailVerification.banner.instructions': 'Check your inbox for the verification link.',
        'emailVerification.banner.resend': 'Resend verification email',
        'emailVerification.banner.resending': 'Sending...',
        'emailVerification.banner.success': 'Verification email sent. Check your inbox.',
        'emailVerification.banner.error': 'Unable to resend verification email.',
      })[key] ?? key,
  }),
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

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({
    get isAuthenticated() {
      return authState.isAuthenticated
    },
    get accessToken() {
      return authState.accessToken
    },
    get displayName() {
      return authState.displayName
    },
    get user() {
      return authState.user
    },
    get userInitials() {
      return authState.userInitials
    },
    get isRefreshingProfile() {
      return authState.isRefreshingProfile
    },
    get isEmailVerified() {
      return authState.user.emailStatus === 'VERIFIED'
    },
    get resendVerificationStatus() {
      return authState.resendVerificationStatus
    },
    get resendVerificationError() {
      return authState.resendVerificationError
    },
    logout,
    resendVerificationEmail,
  }),
}))

vi.mock('@modules/workspace/infrastructure/workspace.store', () => ({
  useWorkspaceStore: () => ({
    activeWorkspaceId: 'ws-1',
    activeWorkspace: null,
    workspaces: [],
    isLoadingWorkspaces: false,
    loadWorkspaces: vi.fn().mockResolvedValue(undefined),
    setActiveWorkspaceId: vi.fn(),
  }),
}))

vi.mock('@modules/publishing/infrastructure/publishing.store', () => ({
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
    authState.isAuthenticated = true
    authState.accessToken = 'test-token'
    authState.user.emailStatus = 'VERIFIED'
    authState.resendVerificationStatus = 'idle'
    authState.resendVerificationError = null
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
    expect(routeState.query).toEqual({})
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
    expect(routeState.query).toEqual({ 'channels[]': ['acc-linkedin'] })
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

  it('does not show the verification banner for verified users', () => {
    authState.user.emailStatus = 'VERIFIED'

    const wrapper = mount(AppShell, {
      global: { mocks: { $t: (key: string) => key } },
    })

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('shows a persistent unverified alert with guidance and resend action', () => {
    authState.user.emailStatus = 'PENDING'

    const wrapper = mount(AppShell, {
      global: { mocks: { $t: (key: string) => key } },
    })

    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toContain('Verify your email')
    expect(alert.text()).toContain(
      'Publish, social connect, and media upload require email verification.',
    )
    expect(alert.text()).toContain('Check your inbox for the verification link.')
    expect(wrapper.get('[data-testid="resend-verification"]').text()).toContain(
      'Resend verification email',
    )
  })

  it('uses the same banner for other non-verified statuses', () => {
    authState.user.emailStatus = 'BOUNCED'

    const wrapper = mount(AppShell, {
      global: { mocks: { $t: (key: string) => key } },
    })

    expect(wrapper.get('[role="alert"]').text()).toContain('Verify your email')
  })

  it('triggers resend and shows success state', async () => {
    authState.user.emailStatus = 'PENDING'
    const wrapper = mount(AppShell, {
      global: { mocks: { $t: (key: string) => key } },
    })

    await wrapper.get('[data-testid="resend-verification"]').trigger('click')
    authState.resendVerificationStatus = 'success'
    await nextTick()

    expect(resendVerificationEmail).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('Verification email sent. Check your inbox.')
  })

  it('disables resend while loading and shows error state', () => {
    authState.user.emailStatus = 'PENDING'
    authState.resendVerificationStatus = 'error'
    authState.resendVerificationError = 'Please wait before retrying.'

    const wrapper = mount(AppShell, {
      global: { mocks: { $t: (key: string) => key } },
    })

    expect(wrapper.text()).toContain('Please wait before retrying.')

    authState.resendVerificationStatus = 'loading'
    const loadingWrapper = mount(AppShell, {
      global: { mocks: { $t: (key: string) => key } },
    })

    expect(
      loadingWrapper.get('[data-testid="resend-verification"]').attributes('disabled'),
    ).toBeDefined()
    expect(loadingWrapper.get('[data-testid="resend-verification"]').text()).toContain('Sending...')
  })

  it('logs resend failures without crashing the banner', async () => {
    authState.user.emailStatus = 'PENDING'
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    resendVerificationEmail.mockRejectedValueOnce(new Error('boom'))

    const wrapper = mount(AppShell, {
      global: { mocks: { $t: (key: string) => key } },
    })

    await wrapper.get('[data-testid="resend-verification"]').trigger('click')
    await nextTick()

    expect(consoleErrorSpy).toHaveBeenCalledWith(
      'Failed to resend verification email',
      expect.any(Error),
    )
    expect(wrapper.find('[role="alert"]').isVisible()).toBe(true)

    consoleErrorSpy.mockRestore()
  })
})
