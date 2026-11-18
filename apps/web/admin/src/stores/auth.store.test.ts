import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAdminAuthStore, type PlatformPermission } from '@/stores/auth.store'

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
      principalId: 'e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea',
      email: 'admin@example.com',
      displayName: null,
      platformRoles: ['PLATFORM_OWNER'],
    }
    expect(store.hasPermission('platform.waitlist.invite')).toBe(true)
    expect(store.hasPermission('platform.operators.manage')).toBe(true)
    expect(store.hasPermission('platform.audit.read')).toBe(true)
    expect(store.hasPermission('platform.users.manage')).toBe(true)
    expect(store.hasPermission('platform.configuration.read')).toBe(true)
    expect(store.hasPermission('platform.configuration.manage')).toBe(true)
  })

  it('PLATFORM_OPERATOR can manage users but not operators', () => {
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea',
      email: 'op@example.com',
      displayName: null,
      platformRoles: ['PLATFORM_OPERATOR'],
    }
    expect(store.hasPermission('platform.users.manage')).toBe(true)
    expect(store.hasPermission('platform.operators.manage')).toBe(false)
  })

  it('SUPPORT_AGENT and AUDITOR are read-only for users', () => {
    const store = useAdminAuthStore()
    for (const [email, role] of [
      ['support@example.com', 'SUPPORT_AGENT'],
      ['auditor@example.com', 'AUDITOR'],
    ] as const) {
      store.principal = {
        principalId: 'e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea',
        email,
        displayName: null,
        platformRoles: [role],
      }
      expect(store.hasPermission('platform.users.read')).toBe(true)
      expect(store.hasPermission('platform.users.manage')).toBe(false)
    }
  })

  it('PLATFORM_OPERATOR cannot manage operators', () => {
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea',
      email: 'op@example.com',
      displayName: null,
      platformRoles: ['PLATFORM_OPERATOR'],
    }
    expect(store.hasPermission('platform.waitlist.invite')).toBe(true)
    expect(store.hasPermission('platform.operators.manage')).toBe(false)
  })

  it('PLATFORM_OPERATOR and AUDITOR can read configuration but not manage it', () => {
    const store = useAdminAuthStore()
    for (const role of ['PLATFORM_OPERATOR', 'AUDITOR'] as const) {
      store.principal = {
        principalId: 'e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea',
        email: 'operator@example.com',
        displayName: null,
        platformRoles: [role],
      }
      expect(store.hasPermission('platform.configuration.read')).toBe(true)
      expect(store.hasPermission('platform.configuration.manage')).toBe(false)
    }
  })

  it('OWNER and OPERATOR hold both governance keys', () => {
    const store = useAdminAuthStore()
    for (const role of ['PLATFORM_OWNER', 'PLATFORM_OPERATOR'] as const) {
      store.principal = {
        principalId: 'e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea',
        email: 'operator@example.com',
        displayName: null,
        platformRoles: [role],
      }
      expect(store.hasPermission('platform.governance.read')).toBe(true)
      expect(store.hasPermission('platform.governance.manage')).toBe(true)
    }
  })

  it('AUDITOR is governance read-only', () => {
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea',
      email: 'auditor@example.com',
      displayName: null,
      platformRoles: ['AUDITOR'],
    }
    expect(store.hasPermission('platform.governance.read')).toBe(true)
    expect(store.hasPermission('platform.governance.manage')).toBe(false)
  })

  it.each<{
    title: string
    denied: PlatformPermission[]
    allowed: PlatformPermission[]
  }>([
    {
      title: 'SUPPORT_AGENT holds neither governance key',
      denied: ['platform.governance.read', 'platform.governance.manage'],
      allowed: [],
    },
    {
      title: 'SUPPORT_AGENT has no configuration permissions',
      denied: ['platform.configuration.read', 'platform.configuration.manage'],
      allowed: [],
    },
    {
      title: 'SUPPORT_AGENT cannot invite candidates',
      denied: ['platform.waitlist.invite'],
      allowed: ['platform.users.read'],
    },
  ])('$title', ({ denied, allowed }) => {
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea',
      email: 'support@example.com',
      displayName: null,
      platformRoles: ['SUPPORT_AGENT'],
    }
    for (const permission of denied) {
      expect(store.hasPermission(permission)).toBe(false)
    }
    for (const permission of allowed) {
      expect(store.hasPermission(permission)).toBe(true)
    }
  })

  it('AUDITOR cannot execute mutations', () => {
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea',
      email: 'auditor@example.com',
      displayName: null,
      platformRoles: ['AUDITOR'],
    }
    expect(store.hasPermission('platform.waitlist.invite')).toBe(false)
    expect(store.hasPermission('platform.waitlist.cancel')).toBe(false)
    expect(store.hasPermission('platform.users.manage')).toBe(false)
    expect(store.hasPermission('platform.audit.read')).toBe(true)
  })

  it('multiple roles yield union of permissions', () => {
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea',
      email: 'multi@example.com',
      displayName: null,
      platformRoles: ['AUDITOR', 'SUPPORT_AGENT'],
    }
    expect(store.hasPermission('platform.audit.read')).toBe(true)
    expect(store.hasPermission('platform.users.read')).toBe(true)
    expect(store.hasPermission('platform.waitlist.invite')).toBe(false)
  })

  it('hydrates typed admin session from the server', async () => {
    const mockPrincipal = {
      principalId: '304a3c09-200e-557d-a2ec-f0bab01227f3',
      email: 'test@platform.example',
      displayName: 'Test Admin',
      platformRoles: ['PLATFORM_OPERATOR'],
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(mockPrincipal)))
    const store = useAdminAuthStore()
    await store.hydrateSession()
    expect(store.principal).toEqual(mockPrincipal)
    expect(store.isAuthenticated).toBe(true)
    expect('accessToken' in store).toBe(false)
  })

  it('hydrateSession clears principal on non-200 response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({}) }),
    )
    const store = useAdminAuthStore()
    store.principal = {
      principalId: '52cef266-de0d-5049-b8dc-74b8af5eda7f',
      email: 'old@example.com',
      displayName: null,
      platformRoles: ['PLATFORM_OPERATOR'],
    }
    await store.hydrateSession()
    expect(store.principal).toBeNull()
    expect(store.isAuthenticated).toBe(false)
  })

  it('starts pt-admin login and keeps only a same-origin return path', () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { assign, origin: 'http://localhost' })
    const store = useAdminAuthStore()
    store.signIn('/waitlist?status=pending')
    expect(assign).toHaveBeenCalledWith(
      expect.stringContaining(
        '/oauth2/authorization/pt-admin?returnTo=%2Fwaitlist%3Fstatus%3Dpending',
      ),
    )
  })

  it('replaces an external return destination with the admin root', () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { assign, origin: 'http://localhost' })
    const store = useAdminAuthStore()
    store.signIn('//attacker.example/admin')
    expect(assign).toHaveBeenCalledWith(expect.stringContaining('returnTo=%2F'))
  })

  it('sends CSRF proof on unsafe session requests', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ token: 'csrf-proof' }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)
    const store = useAdminAuthStore()
    await store.request('/api/admin/operators', { method: 'POST', body: '{}' })
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('X-CSRF-TOKEN')).toBe(
      'csrf-proof',
    )
  })

  it('sends CSRF proof with logout and clears the local session', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ token: 'csrf-proof' }))
      .mockResolvedValueOnce(jsonResponse({}))
    vi.stubGlobal('fetch', fetchMock)
    const store = useAdminAuthStore()
    store.principal = {
      principalId: 'e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea',
      email: 'admin@example.com',
      displayName: null,
      platformRoles: ['PLATFORM_OWNER'],
    }
    await store.signOut()
    expect(fetchMock.mock.calls[1]?.[0]).toContain('/api/auth/logout')
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('X-CSRF-TOKEN')).toBe(
      'csrf-proof',
    )
    expect(store.principal).toBeNull()
  })
})

function jsonResponse(body: unknown): Response {
  return {
    ok: true,
    status: 200,
    json: async () => body,
  } as Response
}
