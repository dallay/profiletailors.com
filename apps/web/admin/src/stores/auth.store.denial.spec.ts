import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAdminAuthStore } from '@/stores/auth.store'

const OWNER_PRINCIPAL = {
  principalId: 'e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea',
  email: 'admin@example.com',
  displayName: null,
  platformRoles: ['PLATFORM_OWNER'],
} as const

describe('admin session denial', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('drops the authorized admin state when an admin request is forbidden', async () => {
    const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>()
    fetchMock.mockResolvedValueOnce(jsonResponse({ token: 'csrf-proof' }))
    fetchMock.mockResolvedValueOnce(forbiddenResponse())
    vi.stubGlobal('fetch', fetchMock)
    const store = useAdminAuthStore()
    store.principal = { ...OWNER_PRINCIPAL, platformRoles: [...OWNER_PRINCIPAL.platformRoles] }

    const response = await store.request('/api/admin/waitlist-entries', {
      method: 'POST',
      body: '{}',
    })

    expect(response.status).toBe(403)
    expect(store.principal).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(store.hasPlatformAccess).toBe(false)
    expect(store.accessDenied).toBe(true)
  })

  it('keeps a valid admin session on a successful admin request', async () => {
    const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>()
    fetchMock.mockResolvedValueOnce(jsonResponse({ token: 'csrf-proof' }))
    fetchMock.mockResolvedValueOnce(jsonResponse({}))
    vi.stubGlobal('fetch', fetchMock)
    const store = useAdminAuthStore()
    store.principal = { ...OWNER_PRINCIPAL, platformRoles: [...OWNER_PRINCIPAL.platformRoles] }

    const response = await store.request('/api/admin/waitlist-entries', {
      method: 'POST',
      body: '{}',
    })

    expect(response.ok).toBe(true)
    expect(store.isAuthenticated).toBe(true)
    expect(store.accessDenied).toBe(false)
  })

  it('clears the admin session when the backend refuses the admin session itself', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<(input: string) => Promise<Response>>().mockResolvedValue(forbiddenResponse()),
    )
    const store = useAdminAuthStore()
    store.principal = { ...OWNER_PRINCIPAL, platformRoles: [...OWNER_PRINCIPAL.platformRoles] }

    await store.hydrateSession()

    expect(store.principal).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(store.hasPlatformAccess).toBe(false)
  })

  it('does not retry a forbidden sensitive mutation and does not start another authorization flow', async () => {
    const assign = vi.fn<(url: string) => void>()
    vi.stubGlobal('location', { assign, origin: 'http://localhost' })
    const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>()
    fetchMock.mockResolvedValueOnce(jsonResponse({ token: 'csrf-proof' }))
    fetchMock.mockResolvedValueOnce(forbiddenResponse())
    vi.stubGlobal('fetch', fetchMock)
    const store = useAdminAuthStore()
    store.principal = { ...OWNER_PRINCIPAL, platformRoles: [...OWNER_PRINCIPAL.platformRoles] }

    const response = await store.request('/api/admin/operators/e7249f5c/roles', {
      method: 'POST',
      body: '{}',
    })

    expect(response.ok).toBe(false)
    expect(response.status).toBe(403)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(assign).not.toHaveBeenCalled()
  })

  it('treats a forbidden sensitive mutation as a visible denial because the backend step-up signal is indistinguishable from a permission denial', async () => {
    const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>()
    fetchMock.mockResolvedValueOnce(jsonResponse({ token: 'csrf-proof' }))
    fetchMock.mockResolvedValueOnce(forbiddenResponse())
    vi.stubGlobal('fetch', fetchMock)
    const store = useAdminAuthStore()
    store.principal = { ...OWNER_PRINCIPAL, platformRoles: [...OWNER_PRINCIPAL.platformRoles] }

    const response = await store.request('/api/admin/configuration/registration-mode', {
      method: 'POST',
      body: '{}',
    })

    expect(response.status).toBe(403)
    expect(store.accessDenied).toBe(true)
    expect(store.principal).toBeNull()
    const requestedUrls = fetchMock.mock.calls.map(([url]) => url)
    expect(requestedUrls).toHaveLength(2)
    for (const url of requestedUrls) {
      expect(url).not.toMatch(/otp|acr|step-up|challenge/i)
    }
  })

  it('reports a forbidden response to the caller instead of reporting success', async () => {
    const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>()
    fetchMock.mockResolvedValueOnce(jsonResponse({ token: 'csrf-proof' }))
    fetchMock.mockResolvedValueOnce(forbiddenResponse())
    vi.stubGlobal('fetch', fetchMock)
    const store = useAdminAuthStore()
    store.principal = { ...OWNER_PRINCIPAL, platformRoles: [...OWNER_PRINCIPAL.platformRoles] }

    const response = await store.request('/api/admin/waitlist-entries', {
      method: 'POST',
      body: '{}',
    })

    expect(response.ok).toBe(false)
    expect(response.status).toBe(403)
  })
})

function jsonResponse(body: unknown): Response {
  return {
    ok: true,
    status: 200,
    json: async () => body,
  } as Response
}

function forbiddenResponse(): Response {
  return {
    ok: false,
    status: 403,
    json: async (): Promise<unknown> => {
      throw new SyntaxError('Unexpected end of JSON input')
    },
  } as Response
}
