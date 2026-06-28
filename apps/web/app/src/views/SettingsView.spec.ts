import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SettingsView from './SettingsView.vue'
import { usePublishingStore } from '@/stores/publishing'
import { useWorkspaceStore } from '@/stores/workspace'

const routeQuery = vi.hoisted(() => ({ value: {} as Record<string, unknown> }))
const renameWorkspaceMock = vi.hoisted(() => vi.fn())
const authStoreState = vi.hoisted(() => ({ accessToken: 'access-token-1' }))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQuery.value }),
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

vi.mock('@/lib/auth-api', () => ({
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
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authStoreState,
}))

function mountSettings() {
  return mount(SettingsView, {
    global: {
      mocks: {
        $t: (key: string) => key,
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

  it('clicking LinkedIn connect CTA starts connection flow', async () => {
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

      expect(clearTimeoutSpy).toHaveBeenCalledOnce()
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
    expect(wrapper.text()).toContain('settings.preferencesEyebrow')
    expect(wrapper.text()).toContain('settings.workspaceIdentityTitle')
    expect(wrapper.text()).toContain('settings.channelStatusTitle')
    // Theme toggle is no longer in the settings panel — it lives in SidebarAccountSection.
    expect(wrapper.find('[data-testid="settings-language-en"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="settings-language-es"]').exists()).toBe(true)
  })
})
