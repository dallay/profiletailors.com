import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAdminAuthStore } from '@/stores/auth.store'

describe('useAdminAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('starts unauthenticated', () => {
    const store = useAdminAuthStore()
    expect(store.isAuthenticated).toBe(false)
    expect(store.hasPlatformAccess).toBe(false)
  })

  it('hasPermission returns false when no principal', () => {
    const store = useAdminAuthStore()
    expect(store.hasPermission('platform.waitlist.read')).toBe(false)
  })

  it('PLATFORM_OWNER has all platform permissions', () => {
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'test-id',
      email: 'admin@example.com',
      displayName: null,
      platformRoles: ['PLATFORM_OWNER'],
    }
    expect(store.hasPermission('platform.waitlist.invite')).toBe(true)
    expect(store.hasPermission('platform.operators.manage')).toBe(true)
    expect(store.hasPermission('platform.audit.read')).toBe(true)
  })

  it('PLATFORM_OPERATOR cannot manage operators', () => {
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'test-id',
      email: 'op@example.com',
      displayName: null,
      platformRoles: ['PLATFORM_OPERATOR'],
    }
    expect(store.hasPermission('platform.waitlist.invite')).toBe(true)
    expect(store.hasPermission('platform.operators.manage')).toBe(false)
  })

  it('SUPPORT_AGENT cannot invite candidates', () => {
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'test-id',
      email: 'support@example.com',
      displayName: null,
      platformRoles: ['SUPPORT_AGENT'],
    }
    expect(store.hasPermission('platform.waitlist.invite')).toBe(false)
    expect(store.hasPermission('platform.users.read')).toBe(true)
  })

  it('AUDITOR cannot execute mutations', () => {
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'test-id',
      email: 'auditor@example.com',
      displayName: null,
      platformRoles: ['AUDITOR'],
    }
    expect(store.hasPermission('platform.waitlist.invite')).toBe(false)
    expect(store.hasPermission('platform.waitlist.cancel')).toBe(false)
    expect(store.hasPermission('platform.audit.read')).toBe(true)
  })

  it('multiple roles yield union of permissions', () => {
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'test-id',
      email: 'multi@example.com',
      displayName: null,
      platformRoles: ['AUDITOR', 'SUPPORT_AGENT'],
    }
    expect(store.hasPermission('platform.audit.read')).toBe(true)
    expect(store.hasPermission('platform.users.read')).toBe(true)
    expect(store.hasPermission('platform.waitlist.invite')).toBe(false)
  })

  it('hydrateSession sets principal from 200 response', async () => {
    const mockPrincipal = {
      principalId: 'abc-123',
      email: 'test@platform.example',
      displayName: 'Test Admin',
      platformRoles: ['PLATFORM_OPERATOR'],
    }
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const path = String(input)
        if (path.endsWith('/api/auth/refresh')) {
          return Promise.resolve(jsonResponse({ accessToken: 'refresh-token' }))
        }
        return Promise.resolve(jsonResponse(mockPrincipal))
      }),
    )
    const store = useAdminAuthStore()
    await store.hydrateSession()
    expect(store.principal).toEqual(mockPrincipal)
    expect(store.isAuthenticated).toBe(true)
  })

  it('hydrateSession clears principal on non-200 response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({}) }),
    )
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'old',
      email: 'old@example.com',
      displayName: null,
      platformRoles: ['PLATFORM_OPERATOR'],
    }
    await store.hydrateSession()
    expect(store.principal).toBeNull()
    expect(store.isAuthenticated).toBe(false)
  })

  it('signIn stores the access token and loads the admin principal', async () => {
    const mockPrincipal = {
      principalId: 'abc-123',
      email: 'test@platform.example',
      displayName: 'Test Admin',
      platformRoles: ['PLATFORM_OPERATOR'],
    }
    const fetchMock = vi.fn((input: RequestInfo | URL, _init?: RequestInit) => {
      const path = input instanceof Request ? input.url : String(input)
      if (path.endsWith('/api/auth/login')) {
        return Promise.resolve(jsonResponse({ accessToken: 'access-token' }))
      }
      return Promise.resolve(jsonResponse(mockPrincipal))
    })
    vi.stubGlobal('fetch', fetchMock)

    const store = useAdminAuthStore()
    await store.signIn('admin@example.com', 'correct horse battery staple')

    expect(store.accessToken).toBe('access-token')
    expect(store.principal).toEqual(mockPrincipal)
    const sessionRequest = fetchMock.mock.calls[1]
    const sessionUrl = new URL(sessionRequest?.[0] as string, 'http://localhost')
    expect(sessionUrl.pathname).toBe('/api/admin/session')
    expect(sessionRequest?.[1]?.credentials).toBe('include')
    expect(new Headers(sessionRequest?.[1]?.headers).get('Authorization')).toBe(
      'Bearer access-token',
    )
  })

  it('coalesces concurrent refreshes before retrying protected requests', async () => {
    let protectedRequestCount = 0
    let refreshRequestCount = 0
    let resolveRefresh: (response: Response) => void = () => {}
    const refreshResponse = new Promise<Response>((resolve) => {
      resolveRefresh = resolve
    })
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const path = String(input)
        if (path.endsWith('/api/auth/refresh')) {
          refreshRequestCount += 1
          return refreshResponse
        }
        protectedRequestCount += 1
        if (protectedRequestCount <= 2) return Promise.resolve(errorResponse(401))
        return Promise.resolve(jsonResponse({ ok: true }))
      }),
    )
    const store = useAdminAuthStore()

    const requests = [store.request('/api/admin/one'), store.request('/api/admin/two')]
    await vi.waitFor(() => expect(refreshRequestCount).toBe(1))
    resolveRefresh(jsonResponse({ accessToken: 'refreshed-token' }))
    const responses = await Promise.all(requests)

    expect(responses.every((response) => response.ok)).toBe(true)
    expect(refreshRequestCount).toBe(1)
    expect(protectedRequestCount).toBe(4)
  })

  it('preserves the session and propagates an unsuccessful logout response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errorResponse(500)))
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'test-id',
      email: 'admin@example.com',
      displayName: null,
      platformRoles: ['PLATFORM_OWNER'],
    }

    await expect(store.signOut()).rejects.toMatchObject({ status: 500 })

    expect(store.principal?.principalId).toBe('test-id')
  })
})

function jsonResponse(body: unknown): Response {
  return {
    ok: true,
    status: 200,
    json: async () => body,
  } as Response
}

function errorResponse(status: number): Response {
  return {
    ok: false,
    status,
    json: async () => ({}),
  } as Response
}
