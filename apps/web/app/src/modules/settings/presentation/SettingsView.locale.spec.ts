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

describe('SettingsView preferences panel relocation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    routeQuery.value = {}
  })

  it('renders the preferences panel as a Card slot instead of an aside', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    const panel = wrapper.find('[data-testid="settings-preferences-panel"]')
    expect(panel.exists()).toBe(true)
    expect(panel.attributes('data-slot')).toBe('card')
    expect(panel.element.tagName.toLowerCase()).not.toBe('aside')
  })

  it('moves the language segmented control out of the hero overview', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    const overview = wrapper.find('[data-testid="settings-overview"]')
    expect(overview.exists()).toBe(true)
    expect(overview.find('[data-testid="settings-language-en"]').exists()).toBe(false)
    expect(overview.find('[data-testid="settings-language-es"]').exists()).toBe(false)
  })

  it('places the language segmented control inside the preferences panel', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    const panel = wrapper.find('[data-testid="settings-preferences-panel"]')
    expect(panel.find('[data-testid="settings-language-en"]').exists()).toBe(true)
    expect(panel.find('[data-testid="settings-language-es"]').exists()).toBe(true)
  })

  it('does not render the legacy aside wrapping the preferences panel', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    expect(wrapper.find('aside[data-testid="settings-preferences-panel"]').exists()).toBe(false)
  })
})
