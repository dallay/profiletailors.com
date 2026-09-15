import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import UserDetailView from './UserDetailView.vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

const mockUser = {
  principalId: 'user-1',
  email: 'user@example.com',
  displayIdentity: 'User Example',
  principalType: 'USER',
  createdAt: '2024-01-01T00:00:00Z',
  lastAuthenticatedAt: '2024-06-01T00:00:00Z',
  authenticationMethods: ['jwt'],
  workspaceMemberships: [],
  platformRoles: [],
  status: 'ACTIVE',
  version: 1,
}

const mockWorkspaces: object[] = []

const mockRequest = vi.fn()
vi.mock('@/stores/auth.store', () => ({
  useAdminAuthStore: () => ({ request: mockRequest }),
}))

function createView() {
  setActivePinia(createPinia())
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', redirect: '/users' },
      { path: '/users', name: 'users', component: { template: '<div />' } },
      {
        path: '/admin/users/:principalId',
        name: 'user-detail',
        component: UserDetailView,
      },
    ],
  })
  const i18n = createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        users: {
          title: 'Users',
          deactivate: 'Deactivate',
          reactivate: 'Reactivate',
          active: 'Active',
          deactivated: 'Deactivated',
          suspended: 'Suspended',
        },
        common: { loading: 'Loading...', error: 'Error', noData: 'No data' },
      },
    },
  })
  const wrapper = mount(UserDetailView, {
    global: {
      plugins: [router, i18n],
      stubs: {
        Field: {
          props: ['label', 'value'],
          template: '<div class="field">{{ label }}: {{ value }}</div>',
        },
      },
    },
  })
  return { wrapper, router }
}

describe('UserDetailView', () => {
  beforeEach(() => {
    mockRequest.mockReset()
    mockRequest
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(mockUser) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(mockWorkspaces) })
  })

  it('shows user details and status after loading', async () => {
    const { wrapper, router } = createView()
    router.push({ name: 'user-detail', params: { principalId: 'user-1' } })
    await router.isReady()
    await flushPromises()
    expect(wrapper.text()).toContain('user@example.com')
    expect(wrapper.text()).toContain('Active')
  })

  it('shows deactivate button for ACTIVE user', async () => {
    const { wrapper, router } = createView()
    router.push({ name: 'user-detail', params: { principalId: 'user-1' } })
    await router.isReady()
    await flushPromises()
    const btn = wrapper.findAll('button').find((b) => b.text().includes('Deactivate'))
    expect(btn?.exists()).toBe(true)
  })

  it('shows reactivate button for DEACTIVATED user', async () => {
    mockRequest.mockReset()
    mockRequest
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ ...mockUser, status: 'DEACTIVATED' }),
      })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(mockWorkspaces) })
    const { wrapper, router } = createView()
    router.push({ name: 'user-detail', params: { principalId: 'user-1' } })
    await router.isReady()
    await flushPromises()
    const btn = wrapper.findAll('button').find((b) => b.text().includes('Reactivate'))
    expect(btn?.exists()).toBe(true)
  })
})
