import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'
import SettingsView from './SettingsView.vue'

const routeQuery = vi.hoisted(() => ({ value: {} as Record<string, unknown> }))
const renameWorkspaceMock = vi.hoisted(() => vi.fn())
const authStoreState = vi.hoisted(() => ({
  accessToken: 'access-token-1',
  logout: vi.fn(),
}))

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
        WorkspaceAvatar: { template: '<div />' },
        WorkspaceIconModal: { template: '<div />' },
        Pencil: { template: '<svg />' },
      },
    },
  })
}

describe('SettingsView workspace rename validation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    routeQuery.value = {}
    renameWorkspaceMock.mockReset()
  })

  it('does not submit rename when the workspace name is blank after trimming', async () => {
    const workspace = useWorkspaceStore()
    workspace.setActiveWorkspaceId('ws-1')
    workspace.setWorkspaceName('Current name')

    const wrapper = mountSettings()
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('workspace.rename'))
      ?.trigger('click')
    const input = wrapper.find('input[type="text"]')
    await input.setValue('   ')
    await input.trigger('keyup.enter')

    expect(renameWorkspaceMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('workspace.workspaceNameRequired')
  })

  it('trims the workspace name before submitting rename', async () => {
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
    const input = wrapper.find('input[type="text"]')
    await input.setValue('  Studio PT  ')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('workspace.save'))
      ?.trigger('click')

    expect(renameWorkspaceMock).toHaveBeenCalledWith('Studio PT', expect.anything(), 'ws-1')
  })
})
