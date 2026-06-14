import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import type { AuthTokens } from '@/lib/auth-api'

// ---------------------------------------------------------------------------
// Mock auth-api
// ---------------------------------------------------------------------------
const mockRefreshSession = vi.fn()
const mockGetCurrentUserProfile = vi.fn()

vi.mock('@/lib/auth-api', () => ({
  createApiFetch: () =>
    Object.assign(
      async function apiFetch<T>() {
        return {} as T
      },
      { raw: async () => new Response(null, { status: 204 }) },
    ),
  refreshSession: (...args: unknown[]) => mockRefreshSession(...args),
  getCurrentUserProfile: (...args: unknown[]) => mockGetCurrentUserProfile(...args),
  login: vi.fn(),
  register: vi.fn(),
  logoutSession: vi.fn(),
}))

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
const fakeTokens: AuthTokens = {
  accessToken: 'access-hydrated',
  tokenType: 'Bearer',
  expiresIn: 3600,
  principalId: 'user-1',
  email: 'user@example.com',
  username: 'testuser',
  workspaceId: 'ws-1',
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe('Auth store — hydrateSession', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    mockRefreshSession.mockReset()
    mockGetCurrentUserProfile.mockReset()
  })

  it('restores session from refresh token cookie', async () => {
    mockRefreshSession.mockResolvedValue(fakeTokens)
    mockGetCurrentUserProfile.mockResolvedValue({
      principalId: 'user-1',
      email: 'user@example.com',
      username: 'testuser',
      displayIdentity: 'testuser',
    })

    const auth = useAuthStore()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.sessionChecked).toBe(false)

    await auth.hydrateSession()

    expect(auth.isAuthenticated).toBe(true)
    expect(auth.sessionChecked).toBe(true)
    expect(auth.accessToken).toBe('access-hydrated')
    expect(auth.user?.principalId).toBe('user-1')
    expect(mockRefreshSession).toHaveBeenCalledOnce()
  })

  it('marks sessionChecked even when no active session (expired cookie)', async () => {
    mockRefreshSession.mockResolvedValue(null)

    const auth = useAuthStore()

    await auth.hydrateSession()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.sessionChecked).toBe(true)
    expect(mockRefreshSession).toHaveBeenCalledOnce()
  })

  it('marks sessionChecked when refreshSession throws', async () => {
    mockRefreshSession.mockRejectedValue(new Error('network error'))

    const auth = useAuthStore()

    await auth.hydrateSession()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.sessionChecked).toBe(true)
  })

  it('persists workspaceId from tokens when no workspace is selected', async () => {
    mockRefreshSession.mockResolvedValue(fakeTokens)
    mockGetCurrentUserProfile.mockResolvedValue({
      principalId: 'user-1',
      email: 'user@example.com',
      username: 'testuser',
      displayIdentity: 'testuser',
    })

    const auth = useAuthStore()
    const { useWorkspaceStore } = await import('./workspace')
    const workspace = useWorkspaceStore()

    expect(workspace.activeWorkspaceId).toBeNull()

    await auth.hydrateSession()

    expect(workspace.activeWorkspaceId).toBe('ws-1')
  })

  it('does NOT overwrite existing workspaceId from tokens', async () => {
    mockRefreshSession.mockResolvedValue(fakeTokens)
    mockGetCurrentUserProfile.mockResolvedValue({
      principalId: 'user-1',
      email: 'user@example.com',
      username: 'testuser',
      displayIdentity: 'testuser',
    })

    const auth = useAuthStore()
    const { useWorkspaceStore } = await import('./workspace')
    const workspace = useWorkspaceStore()

    // User already selected a different workspace
    workspace.setActiveWorkspaceId('ws-existing')

    await auth.hydrateSession()

    // Should NOT be overwritten by token's workspaceId
    expect(workspace.activeWorkspaceId).toBe('ws-existing')
  })
})
