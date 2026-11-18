import { describe, it, expect, afterEach, vi } from 'vitest'
import type { Router } from 'vue-router'
import type { useAdminAuthStore } from '@/stores/auth.store'

const OWNER_PRINCIPAL = {
  principalId: 'e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea',
  email: 'admin@example.com',
  displayName: null,
  platformRoles: ['PLATFORM_OWNER'],
}

type AdminStore = ReturnType<typeof useAdminAuthStore>

describe('admin route guard', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders an admin view for a validated pt-admin session', async () => {
    const fetchMock = vi.fn<(input: string) => Promise<Response>>()
    fetchMock.mockResolvedValue(jsonResponse(OWNER_PRINCIPAL))
    vi.stubGlobal('fetch', fetchMock)
    const { router } = await createHarness()

    await router.push('/waitlist')

    expect(router.currentRoute.value.name).toBe('waitlist')
  })

  it('never renders an admin view for a session that only satisfies the dashboard client', async () => {
    const fetchMock = vi.fn<(input: string) => Promise<Response>>()
    fetchMock.mockResolvedValue(forbiddenResponse())
    vi.stubGlobal('fetch', fetchMock)
    const { router } = await createHarness()

    await router.push('/waitlist')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.matched.some((record) => record.name === 'waitlist')).toBe(
      false,
    )
  })

  it('blocks admin views after the backend forbids an admin request', async () => {
    const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>()
    fetchMock.mockImplementation((input) => {
      if (input.includes('/api/admin/session')) {
        return Promise.resolve(jsonResponse(OWNER_PRINCIPAL))
      }
      if (input.includes('/api/auth/csrf')) {
        return Promise.resolve(jsonResponse({ token: 'csrf-proof' }))
      }
      return Promise.resolve(forbiddenResponse())
    })
    vi.stubGlobal('fetch', fetchMock)
    const { router, store } = await createHarness()
    await store.hydrateSession()

    await store.request('/api/admin/operators/e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea/roles', {
      method: 'POST',
      body: '{}',
    })

    expect(store.accessDenied).toBe(true)
    await router.push('/users')
    expect(router.currentRoute.value.name).toBe('access-denied')
    expect(router.currentRoute.value.matched.some((record) => record.name === 'users')).toBe(false)
  })

  it('keeps the denial when the admin session is revalidated', async () => {
    const fetchMock = vi.fn<(input: string, init?: RequestInit) => Promise<Response>>()
    fetchMock.mockImplementation((input) => {
      if (input.includes('/api/admin/session')) {
        return Promise.resolve(jsonResponse(OWNER_PRINCIPAL))
      }
      if (input.includes('/api/auth/csrf')) {
        return Promise.resolve(jsonResponse({ token: 'csrf-proof' }))
      }
      return Promise.resolve(forbiddenResponse())
    })
    vi.stubGlobal('fetch', fetchMock)
    const { router, store } = await createHarness()
    await store.hydrateSession()
    await store.request('/api/admin/operators/e7249f5c-a5d6-5aa3-8ed7-2ab683b159ea/roles', {
      method: 'POST',
      body: '{}',
    })

    await store.hydrateSession()

    expect(store.accessDenied).toBe(true)
    await router.push('/audit')
    expect(router.currentRoute.value.name).toBe('access-denied')
  })
})

async function createHarness(): Promise<{ router: Router; store: AdminStore }> {
  vi.resetModules()
  const { createPinia, setActivePinia } = await import('pinia')
  setActivePinia(createPinia())
  const { default: router } = await import('@/router')
  const { useAdminAuthStore } = await import('@/stores/auth.store')
  return { router, store: useAdminAuthStore() }
}

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
