import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import type { AuthTokens } from '@/lib/auth-api'

const mockRefreshSession = vi.fn()

vi.mock('@/lib/auth-api', () => ({
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
})
