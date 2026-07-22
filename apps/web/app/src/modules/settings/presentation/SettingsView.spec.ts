import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SettingsView from './SettingsView.vue'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'

const routeQuery = vi.hoisted(() => ({ value: {} as Record<string, unknown> }))
const renameWorkspaceMock = vi.hoisted(() => vi.fn())
const authStoreState = vi.hoisted(() => ({ accessToken: 'access-token-1', logout: vi.fn() }))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQuery.value }),
  useRouter: () => ({ replace: vi.fn() }),
}))

vi.mock('vue-i18n', () => ({
  createI18n: () => ({
    global: {
      locale: { value: 'en' },
    },
  }),
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  createApiFetch: () =>
    async function apiFetch<T>() {
      return {} as T
    },
  refreshSession: vi.fn().mockResolvedValue(null),
  getCurrentUserProfile: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  register: vi.fn(),
  logoutSession: vi.fn(),
  proxyImageUrl: (url: string) => url,
  renameWorkspace: (...args: unknown[]) => renameWorkspaceMock(...args),
  updateWorkspaceIcon: vi.fn(),
  closeAccount: vi.fn(),
}))

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => authStoreState,
}))

function mountSettings() {
  return mount(SettingsView, {
    global: {
      mocks: {
        $t: (key: string) => key,
      },
      stubs: {
        AccountClosureSection: { template: '<div />' },
      },
    },
  })
}

describe('SettingsView channel connection CTA', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    routeQuery.value = {}
  })

  it('shows empty channels state with LinkedIn connect CTA', async () => {
    const publishing = usePublishingStore()
    vi.spyOn(publishing, 'fetchChannels').mockResolvedValue([])
    vi.spyOn(publishing, 'fetchConfiguredProviders').mockImplementation(async () => {
      publishing.configuredProviders = ['linkedin']
    })

    const wrapper = mountSettings()
    await flushPromises()

    expect(wrapper.text()).toContain('channels.noChannels')
    expect(wrapper.text()).toContain('channels.connectLinkedInProfile')
  })

  it('clicking LinkedIn connect CTA starts the store connection flow', async () => {
    const publishing = usePublishingStore()
    vi.spyOn(publishing, 'fetchChannels').mockResolvedValue([])
    vi.spyOn(publishing, 'fetchConfiguredProviders').mockImplementation(async () => {
      publishing.configuredProviders = ['linkedin']
    })
    const connect = vi.spyOn(publishing, 'connectLinkedInPersonalProfile').mockResolvedValue({
      authorizationUrl: 'https://linkedin.example/auth',
      state: 'state-1',
      expiresAt: '2026-06-12T12:10:00Z',
    })

    const wrapper = mountSettings()
    await flushPromises()
    const connectButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('channels.connectLinkedInProfile'))

    expect(connectButton).toBeTruthy()
    await connectButton?.trigger('click')

    expect(connect).toHaveBeenCalledOnce()
  })

  it('renders normalized LinkedIn channels returned by the publishing store', async () => {
    const publishing = usePublishingStore()
    publishing.channels = [
      {
        id: 'linkedin-1',
        accountId: 'linkedin-1',
        name: 'Profile Tailors',
        provider: 'linkedin',
        avatar: '',
        handle: 'Profile Tailors',
        status: 'ACTIVE',
      },
    ]
    vi.spyOn(publishing, 'fetchChannels').mockResolvedValue(publishing.channels)
    vi.spyOn(publishing, 'fetchConfiguredProviders').mockResolvedValue()

    const wrapper = mountSettings()
    await flushPromises()

    expect(wrapper.find('[data-testid="settings-connected-channel"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Profile Tailors')
    expect(wrapper.text()).toContain('channels.active')
  })

  it('shows needsReconnect badge for a non-ACTIVE LinkedIn channel', async () => {
    const publishing = usePublishingStore()
    publishing.channels = [
      {
        id: 'linkedin-2',
        accountId: 'linkedin-2',
        name: 'Profile Tailors',
        provider: 'linkedin',
        avatar: '',
        handle: 'Profile Tailors',
        status: 'REQUIRES_RECONNECT',
      },
    ]
    vi.spyOn(publishing, 'fetchChannels').mockResolvedValue(publishing.channels)
    vi.spyOn(publishing, 'fetchConfiguredProviders').mockResolvedValue()

    const wrapper = mountSettings()
    await flushPromises()

    const badge = wrapper.find('[data-testid="settings-connected-channel"] span')
    expect(badge.text()).toContain('channels.needsReconnect')
    // Badge does NOT show the success styling for a non-ACTIVE channel
    expect(badge.classes()).not.toContain('text-success')
  })

  it('uses the LinkedIn callback query contract for success and panel focus', async () => {
    routeQuery.value = { connected: 'linkedin', panel: 'channels', provider: 'linkedin' }
    const publishing = usePublishingStore()
    vi.spyOn(publishing, 'fetchChannels').mockResolvedValue([])
    vi.spyOn(publishing, 'fetchConfiguredProviders').mockResolvedValue()

    const wrapper = mountSettings()
    await flushPromises()

    expect(wrapper.text()).toContain('linkedinCallback.successMessage')
    expect(wrapper.find('[data-testid="settings-channels-panel"]').classes().join(' ')).toContain(
      'shadow-[0_0_0_1px_rgba(255,255,255,0.12)]',
    )
  })

  it('loads channels and provider configuration on direct visits', async () => {
    const publishing = usePublishingStore()
    const fetchChannels = vi.spyOn(publishing, 'fetchChannels').mockResolvedValue([])
    const fetchConfiguredProviders = vi
      .spyOn(publishing, 'fetchConfiguredProviders')
      .mockResolvedValue()

    mountSettings()
    await flushPromises()

    expect(fetchChannels).toHaveBeenCalledOnce()
    expect(fetchConfiguredProviders).toHaveBeenCalledOnce()
  })

  it('dismisses rename success feedback after three seconds', async () => {
    vi.useFakeTimers()
    try {
      const workspace = useWorkspaceStore()
      workspace.setActiveWorkspaceId('ws-1')
      workspace.setWorkspaceName('Current name')
      renameWorkspaceMock.mockResolvedValue({ workspaceId: 'ws-1', name: 'Studio PT' })

      const wrapper = mountSettings()
      await flushPromises()
      await wrapper
        .findAll('button')
        .find((button) => button.text().includes('workspace.rename'))
        ?.trigger('click')
      await wrapper.find('input[type="text"]').setValue('Studio PT')
      await wrapper
        .findAll('button')
        .find((button) => button.text().includes('workspace.save'))
        ?.trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('workspace.renameSuccess')

      await vi.advanceTimersByTimeAsync(3_000)

      expect(wrapper.text()).not.toContain('workspace.renameSuccess')
    } finally {
      vi.useRealTimers()
    }
  })

  it('clears pending rename feedback timer when unmounted', async () => {
    vi.useFakeTimers()
    const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout')
    try {
      const workspace = useWorkspaceStore()
      workspace.setActiveWorkspaceId('ws-1')
      workspace.setWorkspaceName('Current name')
      renameWorkspaceMock.mockResolvedValue({ workspaceId: 'ws-1', name: 'Studio PT' })

      const wrapper = mountSettings()
      await flushPromises()
      await wrapper
        .findAll('button')
        .find((button) => button.text().includes('workspace.rename'))
        ?.trigger('click')
      await wrapper.find('input[type="text"]').setValue('Studio PT')
      await wrapper
        .findAll('button')
        .find((button) => button.text().includes('workspace.save'))
        ?.trigger('click')
      await flushPromises()

      wrapper.unmount()

      expect(clearTimeoutSpy).toHaveBeenCalled()
    } finally {
      clearTimeoutSpy.mockRestore()
      vi.useRealTimers()
    }
  })

  it('renders the integrated settings overview layout', async () => {
    const publishing = usePublishingStore()
    vi.spyOn(publishing, 'fetchChannels').mockResolvedValue([])
    vi.spyOn(publishing, 'fetchConfiguredProviders').mockImplementation(async () => {
      publishing.configuredProviders = ['linkedin']
    })

    const wrapper = mountSettings()
    await flushPromises()

    expect(wrapper.find('[data-testid="settings-shell"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="settings-overview"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="settings-preferences-panel"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('settings.overviewBadge')
    expect(wrapper.text()).toContain('settings.languageLabel')
    expect(wrapper.text()).toContain('settings.subtitle')
    // Theme toggle is no longer in the settings panel — it lives in SidebarAccountSection.
    expect(wrapper.find('[data-testid="settings-language-en"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="settings-language-es"]').exists()).toBe(true)
  })
})
