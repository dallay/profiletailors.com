import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import type { AuthTokens } from '@modules/auth/infrastructure/auth-api'

// ---------------------------------------------------------------------------
// Mock auth-api
// ---------------------------------------------------------------------------
const mockRefreshSession = vi.fn()
const mockGetCurrentUserProfile = vi.fn()
const mockResendVerification = vi.fn()

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  createApiFetch: () =>
    Object.assign(
      async function apiFetch<T>() {
        return {} as T
      },
      { raw: async () => new Response(null, { status: 204 }) },
    ),
  refreshSession: (...args: unknown[]) => mockRefreshSession(...args),
  getCurrentUserProfile: (...args: unknown[]) => mockGetCurrentUserProfile(...args),
  resendVerification: (...args: unknown[]) => mockResendVerification(...args),
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
  emailStatus: 'VERIFIED',
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
    mockResendVerification.mockReset()
  })

  it('restores session from refresh token cookie and trusts profile email status', async () => {
    mockRefreshSession.mockResolvedValue({ ...fakeTokens, emailStatus: 'VERIFIED' })
    mockGetCurrentUserProfile.mockResolvedValue({
      principalId: 'user-1',
      email: 'user@example.com',
      username: 'testuser',
      displayIdentity: 'testuser',
      emailStatus: 'PENDING',
    })

    const auth = useAuthStore()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.sessionChecked).toBe(false)

    await auth.hydrateSession()

    expect(auth.isAuthenticated).toBe(true)
    expect(auth.sessionChecked).toBe(true)
    expect(auth.accessToken).toBe('access-hydrated')
    expect(auth.user?.principalId).toBe('user-1')
    expect(auth.user?.emailStatus).toBe('PENDING')
    expect(auth.isEmailVerified).toBe(false)
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
      emailStatus: 'VERIFIED',
    })

    const auth = useAuthStore()
    const { useWorkspaceStore } = await import('@modules/workspace/infrastructure/workspace.store')
    const workspace = useWorkspaceStore()

    expect(workspace.activeWorkspaceId).toBeNull()

    await auth.hydrateSession()

    expect(workspace.activeWorkspaceId).toBe('ws-1')
  })

  it('applies workspaceId from tokens even when a workspace is already selected', async () => {
    mockRefreshSession.mockResolvedValue(fakeTokens)
    mockGetCurrentUserProfile.mockResolvedValue({
      principalId: 'user-1',
      email: 'user@example.com',
      username: 'testuser',
      displayIdentity: 'testuser',
      emailStatus: 'VERIFIED',
    })

    const auth = useAuthStore()
    const { useWorkspaceStore } = await import('@modules/workspace/infrastructure/workspace.store')
    const workspace = useWorkspaceStore()

    // User already selected a different workspace (e.g. from previous account)
    workspace.setActiveWorkspaceId('ws-existing')

    await auth.hydrateSession()

    // Token's workspaceId should overwrite — it is the authoritative context
    expect(workspace.activeWorkspaceId).toBe(fakeTokens.workspaceId)
  })

  it('resends verification for the authenticated user and exposes status states', async () => {
    mockRefreshSession.mockResolvedValue({ ...fakeTokens, emailStatus: 'PENDING' })
    mockGetCurrentUserProfile.mockResolvedValue({
      principalId: 'user-1',
      email: 'user@example.com',
      username: 'testuser',
      displayIdentity: 'testuser',
      emailStatus: 'PENDING',
    })
    mockResendVerification.mockResolvedValue(undefined)

    const auth = useAuthStore()
    await auth.hydrateSession()

    await auth.resendVerificationEmail()

    expect(mockResendVerification).toHaveBeenCalledWith('user@example.com')
    expect(auth.resendVerificationStatus).toBe('success')
    expect(auth.resendVerificationError).toBeNull()
  })

  it('records resend verification errors', async () => {
    mockRefreshSession.mockResolvedValue({ ...fakeTokens, emailStatus: 'PENDING' })
    mockGetCurrentUserProfile.mockResolvedValue({
      principalId: 'user-1',
      email: 'user@example.com',
      username: 'testuser',
      displayIdentity: 'testuser',
      emailStatus: 'PENDING',
    })
    mockResendVerification.mockRejectedValue({ detail: 'Please wait before retrying.' })

    const auth = useAuthStore()
    await auth.hydrateSession()

    await expect(auth.resendVerificationEmail()).rejects.toMatchObject({
      detail: 'Please wait before retrying.',
    })

    expect(auth.resendVerificationStatus).toBe('error')
    expect(auth.resendVerificationError).toBe('Please wait before retrying.')
  })

  it('fails resend verification when no user email is available', async () => {
    const auth = useAuthStore()
    auth.user = {
      principalId: 'user-1',
      email: '',
      username: 'testuser',
      displayIdentity: 'testuser',
      emailStatus: 'PENDING',
    }

    await expect(auth.resendVerificationEmail()).rejects.toThrow(
      'No email address is available for verification resend.',
    )

    expect(auth.resendVerificationStatus).toBe('error')
    expect(auth.resendVerificationError).toBe(
      'No email address is available for verification resend.',
    )
    expect(mockResendVerification).not.toHaveBeenCalled()
  })
})
