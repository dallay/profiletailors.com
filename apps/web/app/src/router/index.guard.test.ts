import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import type { AuthTokens } from '@modules/auth/infrastructure/auth-api'

const mockRefreshSession = vi.fn()

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  createApiFetch: () =>
    Object.assign(
      async function apiFetch<T>() {
        return {} as T
      },
      { raw: async () => new Response(null, { status: 204 }) },
    ),
  refreshSession: (...args: unknown[]) => mockRefreshSession(...args),
  getCurrentUserProfile: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  register: vi.fn(),
  logoutSession: vi.fn(),
  fetchPublicCapabilities: vi.fn(),
}))

const mockPublicCapabilitiesLoad = vi.fn()

vi.mock('@modules/auth/infrastructure/public-capabilities.store', () => ({
  usePublicCapabilitiesStore: () => ({
    load: mockPublicCapabilitiesLoad,
    registrationEnabled: false,
    capabilityChecked: true,
  }),
}))

const fakeTokens: AuthTokens = {
  accessToken: 'access-hydrated',
  tokenType: 'Bearer',
  expiresIn: 3600,
  principalId: 'user-1',
  email: 'user@example.com',
  username: 'testuser',
  emailStatus: 'ACTIVE',
  workspaceId: 'ws-1',
}

describe('router real guard navigation', { timeout: 15000 }, () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    mockRefreshSession.mockReset()
    vi.resetModules()
  })

  it('redirects unauthenticated user from canonical scheduler route to /login', async () => {
    mockRefreshSession.mockResolvedValue(null)
    const { default: router } = await import('./index')

    await router.push('/scheduler/calendar/week')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/scheduler/calendar/week')
  })

  it('redirects authenticated user away from /login to /', async () => {
    mockRefreshSession.mockResolvedValue(fakeTokens)
    const { default: router } = await import('./index')
    const auth = useAuthStore()
    await auth.hydrateSession()

    await router.push('/login')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/')
  })

  it('redirects an authenticated user away from guest-only forgot password', async () => {
    mockRefreshSession.mockResolvedValue(fakeTokens)
    const { default: router } = await import('./index')

    await router.push('/forgot-password')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/')
  })

  it('allows an authenticated user to open reset password with its capability', async () => {
    mockRefreshSession.mockResolvedValue(fakeTokens)
    const { default: router } = await import('./index')

    await router.push('/reset-password?token=opaque-capability')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/reset-password')
    expect(router.currentRoute.value.query.token).toBe('opaque-capability')
  })

  it('redirects unauthenticated user from the relocated /media route to /login', async () => {
    mockRefreshSession.mockResolvedValue(null)
    const { default: router } = await import('./index')

    await router.push('/media')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/media')
  })

  it('preserves /register so the route can render its fail-closed state in place', async () => {
    mockRefreshSession.mockResolvedValue(null)
    const { default: router } = await import('./index')

    await router.push('/register?email=user@example.com')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/register')
    expect(router.currentRoute.value.query.email).toBe('user@example.com')
    expect(mockPublicCapabilitiesLoad).not.toHaveBeenCalled()
  })

  it('allows navigation to /register when registration is enabled', async () => {
    mockRefreshSession.mockResolvedValue(null)
    mockPublicCapabilitiesLoad.mockResolvedValue(undefined)
    vi.doMock('@modules/auth/infrastructure/public-capabilities.store', () => ({
      usePublicCapabilitiesStore: () => ({
        load: mockPublicCapabilitiesLoad,
        registrationEnabled: true,
        capabilityChecked: true,
      }),
    }))
    vi.resetModules()
    const { default: router } = await import('./index')

    await router.push('/register')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/register')
  })
})
