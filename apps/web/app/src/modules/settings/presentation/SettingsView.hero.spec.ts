import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import enSettings from '@shared/i18n/locales/en/settings'
import esSettings from '@shared/i18n/locales/es/settings'
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

describe('SettingsView hero', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    routeQuery.value = {}
  })

  it('renders the page title testid inside the hero', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    expect(wrapper.find('[data-testid="settings-page-title"]').exists()).toBe(true)
  })

  it('uses the display-lg token on the hero h1', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    const heroTitle = wrapper.find('[data-testid="settings-page-title"]')
    expect(heroTitle.exists()).toBe(true)
    expect(heroTitle.element.tagName.toLowerCase()).toBe('h1')
    const className = heroTitle.classes().join(' ')
    expect(className).toContain('display-lg')
  })

  it('renders the mono eyebrow above the h1 with the nav.settings key', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    const overview = wrapper.find('[data-testid="settings-overview"]')
    expect(overview.exists()).toBe(true)
    expect(overview.text()).toContain('nav.settings')
  })

  it('binds the hero subtitle to the settings.subtitle locale key', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    const overview = wrapper.find('[data-testid="settings-overview"]')
    expect(overview.exists()).toBe(true)
    expect(overview.text()).toContain('settings.subtitle')
  })

  it('keeps the approved English subtitle under sixty characters', () => {
    expect(enSettings.subtitle).toBe('Connect a channel, name your workspace, change the surface.')
    expect(enSettings.subtitle.length).toBeLessThanOrEqual(60)
  })

  it('keeps the approved Spanish subtitle', () => {
    expect(esSettings.subtitle).toBe('Conecta un canal, nombra tu workspace, ajusta la superficie.')
  })

  it('keeps the overview pill testid inside the hero', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    expect(wrapper.find('[data-testid="settings-overview"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('settings.overviewBadge')
  })
})
