import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
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
  accountState: 'ACTIVE',
}

const mockWorkspaces: object[] = []

const mockRequest = vi.fn()
const mockHasPermission = vi.fn(() => true)
vi.mock('@/stores/auth.store', () => ({
  useAdminAuthStore: () => ({ request: mockRequest, hasPermission: mockHasPermission }),
}))

async function createView() {
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
          displayName: 'Name',
          principalType: 'Type',
          accountState: 'Account state',
          verificationState: 'Verification state',
          disable: 'Disable account',
          enable: 'Enable account',
          revokeSessions: 'Revoke sessions',
          disableConfirm: 'Disable this account and revoke all active sessions?',
          enableConfirm: 'Enable this account?',
          revokeSessionsConfirm: 'Revoke all active sessions for this account?',
          lastAuthenticated: 'Last Authenticated',
          platformRoles: 'Platform Roles',
          workspaces: 'Workspaces',
        },
        common: { loading: 'Loading...', error: 'Error', noData: 'No data', createdAt: 'Created' },
      },
    },
  })
  await router.push({ name: 'user-detail', params: { principalId: 'user-1' } })
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
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  beforeEach(() => {
    mockRequest.mockReset()
    mockHasPermission.mockReset()
    mockHasPermission.mockReturnValue(true)
    mockRequest
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(mockUser) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(mockWorkspaces) })
  })

  it('shows user details and account state after loading', async () => {
    const { wrapper } = await createView()
    await flushPromises()
    expect(wrapper.text()).toContain('user@example.com')
    expect(wrapper.text()).toContain('ACTIVE')
  })

  it('shows disable and revoke controls for ACTIVE user with manage permission', async () => {
    vi.stubGlobal(
      'confirm',
      vi.fn(() => true),
    )
    vi.stubGlobal('crypto', { randomUUID: vi.fn(() => 'request-id') })
    const { wrapper } = await createView()
    await flushPromises()
    const buttons = wrapper.findAll('button').map((button) => button.text())
    expect(buttons).toContain('Disable account')
    expect(buttons).toContain('Revoke sessions')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Disable account')
      ?.trigger('click')
    await flushPromises()
    const mutationRequest = mockRequest.mock.calls[2]
    expect(mutationRequest?.[0]).toBe('/api/admin/users/user-1/disable')
    expect(mutationRequest?.[1]?.headers).toEqual({
      'Idempotency-Key': 'admin-user-disable-user-1-request-id',
    })
  })

  it('shows enable button for DISABLED user', async () => {
    mockRequest.mockReset()
    mockRequest
      .mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({ ...mockUser, accountState: 'DISABLED' }),
      })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(mockWorkspaces) })
    const { wrapper } = await createView()
    await flushPromises()
    const buttons = wrapper.findAll('button').map((button) => button.text())
    expect(buttons).toContain('Enable account')
    expect(buttons).toContain('Revoke sessions')
  })

  it('hides account controls without manage permission', async () => {
    mockHasPermission.mockReturnValue(false)
    const { wrapper } = await createView()
    await flushPromises()
    const buttons = wrapper
      .findAll('button')
      .filter((button) =>
        ['Disable account', 'Enable account', 'Revoke sessions'].includes(button.text()),
      )
    expect(buttons.length).toBe(0)
  })
})
