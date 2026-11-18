import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import type { AuthTokens } from '@modules/auth/infrastructure/auth-api'

// ---------------------------------------------------------------------------
// Mock auth-api
// ---------------------------------------------------------------------------
const mockGetBffSession = vi.fn()
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
  getBffSession: (...args: unknown[]) => mockGetBffSession(...args),
  logoutBffSession: vi.fn(),
  buildBffLoginUrl: (returnTo: string) =>
    `/oauth2/authorization/pt-app?returnTo=${encodeURIComponent(returnTo)}`,
  resendVerification: (...args: unknown[]) => mockResendVerification(...args),
  register: vi.fn(),
}))

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
const fakeTokens: AuthTokens = {
  accessToken: 'access-hydrated',
  tokenType: 'Bearer',
  expiresIn: 3600,
  principalId: '18f3c8ec-b5fd-5d86-ac83-465965479890',
  email: 'user@example.com',
  username: 'testuser',
  emailStatus: 'VERIFIED',
  workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe('Auth store — hydrateSession', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    mockGetBffSession.mockReset()
    mockGetCurrentUserProfile.mockReset()
    mockResendVerification.mockReset()
  })

  it('restores the authenticated user from the BFF session profile', async () => {
    mockGetBffSession.mockResolvedValue({
      principalId: '18f3c8ec-b5fd-5d86-ac83-465965479890',
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
    expect(auth.accessToken).toBeNull()
    expect(auth.user?.principalId).toBe('18f3c8ec-b5fd-5d86-ac83-465965479890')
    expect(auth.user?.emailStatus).toBe('PENDING')
    expect(auth.isEmailVerified).toBe(false)
    expect(mockGetBffSession).toHaveBeenCalledOnce()
  })

  it('marks sessionChecked when the BFF session is unauthenticated', async () => {
    mockGetBffSession.mockRejectedValue({ status: 401 })

    const auth = useAuthStore()

    await auth.hydrateSession()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.sessionChecked).toBe(true)
    expect(mockGetBffSession).toHaveBeenCalledOnce()
  })

  it('marks the session unreachable when the BFF cannot be reached', async () => {
    mockGetBffSession.mockRejectedValue(new TypeError('Failed to fetch'))

    const auth = useAuthStore()

    await auth.hydrateSession()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.sessionChecked).toBe(true)
    expect(auth.bootstrapState).toBe('unreachable')
  })

  it('marks sessionChecked after a non-network BFF session error', async () => {
    mockGetBffSession.mockRejectedValue(new Error('session error'))

    const auth = useAuthStore()

    await auth.hydrateSession()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.sessionChecked).toBe(true)
  })

  it('does not derive workspace authorization from the session profile', async () => {
    mockGetBffSession.mockResolvedValue({
      principalId: fakeTokens.principalId,
      email: fakeTokens.email,
      username: fakeTokens.username,
      displayIdentity: 'testuser',
      emailStatus: 'VERIFIED',
    })
    mockGetCurrentUserProfile.mockResolvedValue({
      principalId: '18f3c8ec-b5fd-5d86-ac83-465965479890',
      email: 'user@example.com',
      username: 'testuser',
      displayIdentity: 'testuser',
      emailStatus: 'VERIFIED',
    })

    const auth = useAuthStore()
    const { useWorkspaceStore } = await import('@modules/workspace/infrastructure/workspace.store')
    const workspace = useWorkspaceStore()

    workspace.setActiveWorkspaceId('e90e78f2-caf5-5675-83d8-ba865fedae33')

    await auth.hydrateSession()

    expect(workspace.activeWorkspaceId).toBe('e90e78f2-caf5-5675-83d8-ba865fedae33')
  })

  it('resends verification for the authenticated user and exposes status states', async () => {
    mockGetBffSession.mockResolvedValue({
      principalId: '18f3c8ec-b5fd-5d86-ac83-465965479890',
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
    mockGetBffSession.mockResolvedValue({
      principalId: '18f3c8ec-b5fd-5d86-ac83-465965479890',
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
      principalId: '18f3c8ec-b5fd-5d86-ac83-465965479890',
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
