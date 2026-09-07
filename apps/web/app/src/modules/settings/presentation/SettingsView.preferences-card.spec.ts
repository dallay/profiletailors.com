import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SettingsView from './SettingsView.vue'

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
        PrivacySection: { template: '<div data-testid="settings-privacy-panel-stub" />' },
        WorkspaceIconModal: { template: '<div />' },
        WorkspaceAvatar: { template: '<div />' },
        SocialProviderIcon: { template: '<div />' },
        Pencil: { template: '<svg />' },
      },
    },
  })
}

describe('SettingsView preferences card content and ordering', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    routeQuery.value = {}
  })

  it('renders the preferences eyebrow inside the preferences card', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    const panel = wrapper.find('[data-testid="settings-preferences-panel"]')
    expect(panel.exists()).toBe(true)
    expect(panel.text()).toContain('settings.preferencesEyebrow')
  })

  it('wraps the eyebrow in a card header slot', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    const panel = wrapper.find('[data-testid="settings-preferences-panel"]')
    const header = panel.find('[data-slot="card-header"]')
    expect(header.exists()).toBe(true)
    expect(header.text()).toContain('settings.preferencesEyebrow')
  })

  it('keeps the language segmented control inside the preferences card', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    const panel = wrapper.find('[data-testid="settings-preferences-panel"]')
    expect(panel.find('[data-testid="settings-language-en"]').exists()).toBe(true)
    expect(panel.find('[data-testid="settings-language-es"]').exists()).toBe(true)
  })

  it('renders the preferences card after the channels and workspace grid', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    const html = wrapper.html()
    const channelsIndex = html.indexOf('settings-channels-panel')
    const workspaceMarker = 'workspace.title'
    const workspaceIndex = html.indexOf(workspaceMarker)
    const preferencesIndex = html.indexOf('settings-preferences-panel')

    expect(channelsIndex).toBeGreaterThan(-1)
    expect(workspaceIndex).toBeGreaterThan(-1)
    expect(preferencesIndex).toBeGreaterThan(channelsIndex)
    expect(preferencesIndex).toBeGreaterThan(workspaceIndex)
  })

  it('renders the preferences card before the privacy section', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    const html = wrapper.html()
    const preferencesIndex = html.indexOf('settings-preferences-panel')
    const privacyIndex = html.indexOf('settings-privacy-panel-stub')

    expect(preferencesIndex).toBeGreaterThan(-1)
    expect(privacyIndex).toBeGreaterThan(-1)
    expect(preferencesIndex).toBeLessThan(privacyIndex)
  })
})
