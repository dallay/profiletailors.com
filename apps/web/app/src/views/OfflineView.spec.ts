import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import OfflineView from './OfflineView.vue'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import type { AuthTokens } from '@modules/auth/infrastructure/auth-api'

const mockRefreshSession = vi.fn()
const mockGetCurrentUserProfile = vi.fn()
const mockLogoutSession = vi.fn()
const mockLogin = vi.fn()
const mockRegister = vi.fn()
const mockReplace = vi.fn()
const mockQuery = { redirect: '/scheduler/calendar/week' }

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: mockQuery }),
  useRouter: () => ({ replace: (...args: unknown[]) => mockReplace(...args) }),
}))

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  createApiFetch: () => {
    async function apiFetch(): Promise<never> {
      throw new Error('auth-api mock: apiFetch not implemented')
    }
    return Object.assign(apiFetch, { raw: async () => new Response(null, { status: 204 }) })
  },
  refreshSession: (..._args: unknown[]) => mockRefreshSession(),
  getCurrentUserProfile: (..._args: unknown[]) => mockGetCurrentUserProfile(),
  logoutSession: (..._args: unknown[]) => mockLogoutSession(),
  login: (..._args: unknown[]) => mockLogin(),
  register: (..._args: unknown[]) => mockRegister(),
}))

const fakeTokens: AuthTokens = {
  accessToken: 'access-retry',
  tokenType: 'Bearer',
  expiresIn: 3600,
  principalId: 'user-1',
  email: 'user@example.com',
  username: 'testuser',
  emailStatus: 'VERIFIED',
}

const mockFetch = vi.fn()

async function loginForTest(auth: ReturnType<typeof useAuthStore>): Promise<void> {
  mockLogin.mockResolvedValueOnce(fakeTokens)
  mockGetCurrentUserProfile.mockResolvedValue({
    principalId: 'user-1',
    email: 'user@example.com',
    username: 'testuser',
    displayIdentity: 'testuser',
    emailStatus: 'VERIFIED',
  })
  await auth.loginWithPassword({ email: 'user@example.com', password: 'secret' })
}

describe('OfflineView', () => {
  const wrappers: VueWrapper[] = []

  beforeEach(() => {
    mockRefreshSession.mockReset()
    mockGetCurrentUserProfile.mockReset()
    mockLogoutSession.mockReset()
    mockLogin.mockReset()
    mockRegister.mockReset()
    mockReplace.mockReset()
    mockFetch.mockReset()
    mockFetch.mockResolvedValue(new Response(null, { status: 200 }))
    vi.stubGlobal('fetch', mockFetch)
    mockQuery.redirect = '/scheduler/calendar/week'
    setActivePinia(createPinia())
  })

  afterEach(() => {
    while (wrappers.length) wrappers.pop()!.unmount()
    vi.unstubAllGlobals()
  })

  function mountView(): VueWrapper {
    const wrapper = mount(OfflineView)
    wrappers.push(wrapper)
    return wrapper
  }

  it('renders the offline title and action buttons', () => {
    const wrapper = mountView()
    expect(wrapper.find('h1').text()).toContain('pwa.offline.title')
    expect(wrapper.text()).toContain('pwa.offline.retry')
    expect(wrapper.text()).toContain('pwa.offline.backToLogin')
  })

  it('shows the api-unreachable hint when connectivity status is api-unreachable', async () => {
    const wrapper = mountView()
    await flushPromises()
    wrapper.vm.status = 'api-unreachable'
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('pwa.offline.apiUnreachable')
  })

  it('retries hydration and navigates to the redirect target when the session recovers', async () => {
    mockRefreshSession.mockResolvedValue(fakeTokens)
    mockGetCurrentUserProfile.mockResolvedValue({
      principalId: 'user-1',
      email: 'user@example.com',
      username: 'testuser',
      displayIdentity: 'testuser',
      emailStatus: 'VERIFIED',
    })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.findAll('button')[0]!.trigger('click')
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith('/scheduler/calendar/week')
  })

  it('retries hydration and navigates when the connectivity probe fails but the session can be restored', async () => {
    mockRefreshSession.mockResolvedValue(fakeTokens)
    mockGetCurrentUserProfile.mockResolvedValue({
      principalId: 'user-1',
      email: 'user@example.com',
      username: 'testuser',
      displayIdentity: 'testuser',
      emailStatus: 'VERIFIED',
    })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.findAll('button')[0]!.trigger('click')
    await flushPromises()

    expect(mockRefreshSession).toHaveBeenCalledOnce()
    expect(mockReplace).toHaveBeenCalledWith('/scheduler/calendar/week')
  })

  it('does not navigate when the session cannot be restored', async () => {
    mockRefreshSession.mockResolvedValue(null)
    const wrapper = mountView()
    await flushPromises()

    await wrapper.findAll('button')[0]!.trigger('click')
    await flushPromises()

    expect(mockRefreshSession).toHaveBeenCalledOnce()
    expect(mockReplace).not.toHaveBeenCalled()
  })

  it('clears the local session before navigating to login', async () => {
    const auth = useAuthStore()
    await loginForTest(auth)
    const clearLocalSessionSpy = vi.spyOn(auth, 'clearLocalSession')
    const wrapper = mountView()
    await flushPromises()

    await wrapper.findAll('button')[1]!.trigger('click')
    await flushPromises()

    expect(clearLocalSessionSpy).toHaveBeenCalledOnce()
    expect(mockReplace).toHaveBeenCalledWith({
      path: '/login',
      query: { redirect: '/scheduler/calendar/week' },
    })
  })

  it('uses the first element of an array redirect query', async () => {
    mockQuery.redirect = ['/media', '/scheduler']
    mockRefreshSession.mockResolvedValue(fakeTokens)
    mockGetCurrentUserProfile.mockResolvedValue({
      principalId: 'user-1',
      email: 'user@example.com',
      username: 'testuser',
      displayIdentity: 'testuser',
      emailStatus: 'VERIFIED',
    })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.findAll('button')[0]!.trigger('click')
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith('/media')
  })

  it('falls back to home when redirect query is absent', async () => {
    mockQuery.redirect = undefined
    mockRefreshSession.mockResolvedValue(fakeTokens)
    mockGetCurrentUserProfile.mockResolvedValue({
      principalId: 'user-1',
      email: 'user@example.com',
      username: 'testuser',
      displayIdentity: 'testuser',
      emailStatus: 'VERIFIED',
    })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.findAll('button')[0]!.trigger('click')
    await flushPromises()

    expect(mockReplace).toHaveBeenCalledWith('/')
  })

  it('disables the retry button while rehydrating', async () => {
    mockRefreshSession.mockImplementation(() => new Promise(() => {}))
    const wrapper = mountView()
    await flushPromises()

    wrapper.findAll('button')[0]!.trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('button')[0]!.attributes('disabled')).toBeDefined()
  })
})
