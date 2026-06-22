import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { deleteAsset } from './media-api'

// ---------------------------------------------------------------------------
// Mock auth-api (createApiFetch + refreshSession)
// ---------------------------------------------------------------------------
vi.mock('@/lib/auth-api', () => ({
  createApiFetch: () =>
    Object.assign(
      async function apiFetch<T>() {
        return {} as T
      },
      {
        raw: async () => new Response(null, { status: 204 }),
      },
    ),
  refreshSession: vi.fn().mockResolvedValue(null),
}))

// ---------------------------------------------------------------------------
// Auth store mock (controlled per test)
// ---------------------------------------------------------------------------
const mockApiFetch = vi.fn()

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    get isAuthenticated() {
      return mockIsAuthenticated
    },
    apiFetch: mockApiFetch,
    apiFetchRaw: vi.fn(),
    accessToken: { value: 'fake-token' },
    workspace: { activeWorkspaceId: 'ws-media-test' },
    $reset: vi.fn(),
  }),
}))

vi.mock('@/stores/workspace', () => ({
  useWorkspaceStore: () => ({
    activeWorkspaceId: 'ws-media-test',
  }),
}))

let mockIsAuthenticated = true

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------


describe('deleteAsset', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockIsAuthenticated = true
  })

  it('throws a 401 error when the user is not authenticated', async () => {
    mockIsAuthenticated = false

    await expect(deleteAsset('asset-123')).rejects.toMatchObject({
      title: 'Not authenticated',
      detail: 'You must be signed in.',
      status: 401,
    })

    expect(mockApiFetch).not.toHaveBeenCalled()
  })

  it('calls apiFetch with DELETE method and the correct asset path', async () => {
    mockApiFetch.mockResolvedValueOnce(undefined)

    await deleteAsset('asset-abc')

    expect(mockApiFetch).toHaveBeenCalledOnce()
    expect(mockApiFetch).toHaveBeenCalledWith('/api/media/assets/asset-abc', {
      method: 'DELETE',
      workspaceScoped: true,
    })
  })

  it('resolves without returning a value on success', async () => {
    mockApiFetch.mockResolvedValueOnce(undefined)

    const result = await deleteAsset('asset-xyz')

    expect(result).toBeUndefined()
  })

  it('propagates API errors to the caller', async () => {
    mockApiFetch.mockRejectedValueOnce({
      title: 'Not Found',
      detail: 'The asset does not exist.',
      status: 404,
    })

    await expect(deleteAsset('missing-asset')).rejects.toMatchObject({
      title: 'Not Found',
      status: 404,
    })
  })

  it('uses the workspaceScoped flag so the X-Workspace-Id header is injected', async () => {
    mockApiFetch.mockResolvedValueOnce(undefined)

    await deleteAsset('asset-ws-check')

    const [, options] = mockApiFetch.mock.calls[0] as [string, { workspaceScoped?: boolean }]
    expect(options.workspaceScoped).toBe(true)
  })

  it('constructs the correct path for an asset with a complex id', async () => {
    mockApiFetch.mockResolvedValueOnce(undefined)

    await deleteAsset('asset/with spaces?special=chars')

    expect(mockApiFetch.mock.calls[0]?.[0]).toBe(
      `/api/media/assets/${encodeURIComponent('asset/with spaces?special=chars')}`,
    )
  })
})
