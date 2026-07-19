import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PrivacySection from './PrivacySection.vue'
import type { DsarRequest } from '@modules/settings/infrastructure/privacy.store'
import { usePrivacyStore } from '@modules/settings/infrastructure/privacy.store'

const mockUser = {
  principalId: 'user-1',
  email: 'test@test.com',
  username: 'testuser',
  emailStatus: 'VERIFIED',
  displayIdentity: 'testuser',
}

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@shared/i18n', () => ({
  default: { global: { locale: { value: 'en' } } },
}))

vi.mock('@modules/workspace/infrastructure/workspace.store', () => ({
  useWorkspaceStore: () => ({
    activeWorkspaceId: 'ws-1',
  }),
}))

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({
    apiFetch: vi.fn(),
    isAuthenticated: true,
    accessToken: 'mock-token',
    user: mockUser,
  }),
}))

function mountPrivacySection() {
  return mount(PrivacySection, {
    global: {
      stubs: {
        DsarRequestForm: {
          template:
            '<div data-testid="dsar-request-form-stub"><button data-testid="stub-submit" @click="$emit(\'submit\', { type: \'ACCESS\' })">Submit</button></div>',
        },
        DsarRequestList: {
          template: '<div data-testid="dsar-request-list-stub" />',
        },
      },
    },
  })
}

describe('PrivacySection', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders privacy section with title and description', async () => {
    const store = usePrivacyStore()
    vi.spyOn(store, 'fetchRequests').mockResolvedValue([])

    const wrapper = mountPrivacySection()
    await flushPromises()

    expect(wrapper.find('[data-testid="settings-privacy-panel"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('settings.privacy.title')
    expect(wrapper.text()).toContain('settings.privacy.description')
  })

  it('renders the form and list sub-components', async () => {
    const store = usePrivacyStore()
    vi.spyOn(store, 'fetchRequests').mockResolvedValue([])

    const wrapper = mountPrivacySection()
    await flushPromises()

    expect(wrapper.find('[data-testid="dsar-request-form-stub"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="dsar-request-list-stub"]').exists()).toBe(true)
  })

  it('displays success message after form submission', async () => {
    const store = usePrivacyStore()
    vi.spyOn(store, 'fetchRequests').mockResolvedValue([])

    const mockResponse: DsarRequest = {
      id: 'req-new',
      workspaceId: 'ws-1',
      type: 'ACCESS',
      status: 'PENDING',
      notes: null,
      correctionData: null,
      resultRef: null,
      createdAt: '2026-07-19T12:00:00Z',
      updatedAt: '2026-07-19T12:00:00Z',
    }
    vi.spyOn(store, 'submitRequest').mockResolvedValue(mockResponse)

    const wrapper = mountPrivacySection()
    await flushPromises()

    await wrapper.find('[data-testid="stub-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="dsar-submit-success"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('settings.privacy.form.success')
  })

  it('calls fetchRequests on mount', async () => {
    const store = usePrivacyStore()
    const fetchSpy = vi.spyOn(store, 'fetchRequests').mockResolvedValue([])

    mountPrivacySection()
    await flushPromises()

    expect(fetchSpy).toHaveBeenCalledOnce()
  })

  it('calls submitRequest with the payload when form emits submit', async () => {
    const store = usePrivacyStore()
    vi.spyOn(store, 'fetchRequests').mockResolvedValue([])
    const submitSpy = vi.spyOn(store, 'submitRequest').mockResolvedValue({
      id: 'req-new',
      workspaceId: 'ws-1',
      type: 'ACCESS',
      status: 'PENDING',
      notes: null,
      correctionData: null,
      resultRef: null,
      createdAt: '2026-07-19T12:00:00Z',
      updatedAt: '2026-07-19T12:00:00Z',
    })

    const wrapper = mountPrivacySection()
    await flushPromises()

    await wrapper.find('[data-testid="stub-submit"]').trigger('click')
    await flushPromises()

    expect(submitSpy).toHaveBeenCalledWith({ type: 'ACCESS' })
  })
})
