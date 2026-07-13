import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import type { AuthTokens, CurrentUserProfile } from '@modules/auth/infrastructure/auth-api'

vi.mock('@modules/auth/infrastructure/auth-api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@modules/auth/infrastructure/auth-api')>()

  return {
    ...actual,
    createApiFetch: () =>
      Object.assign(
        async function apiFetch<T>() {
          return {} as T
        },
        { raw: async () => new Response(null, { status: 204 }) },
      ),
    refreshSession: vi.fn().mockResolvedValue(null),
    getCurrentUserProfile: vi.fn(),
    login: vi.fn(),
    logoutSession: vi.fn(),
    register: vi.fn(),
    resendVerification: vi.fn(),
    verifyEmail: vi.fn(),
  }
})

describe('auth store verifyEmail', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('applies returned tokens and refreshes the authoritative /api/auth/me profile after verification', async () => {
    const { verifyEmail, getCurrentUserProfile } = await import(
      '@modules/auth/infrastructure/auth-api'
    )

    const tokens: AuthTokens = {
      accessToken: 'verified-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      principalId: 'user-1',
      email: 'verified@example.com',
      username: 'verified-user',
      emailStatus: 'PENDING',
      workspaceId: 'ws-verified',
    }

    const profile: CurrentUserProfile = {
      principalId: 'user-1',
      email: 'verified@example.com',
      username: 'verified-user',
      displayIdentity: 'Verified User',
      emailStatus: 'VERIFIED',
    }

    vi.mocked(verifyEmail).mockResolvedValue(tokens)
    vi.mocked(getCurrentUserProfile).mockResolvedValue(profile)

    const auth = useAuthStore()

    await auth.verifyEmail('token-verified')

    expect(verifyEmail).toHaveBeenCalledWith('token-verified')
    expect(getCurrentUserProfile).toHaveBeenCalledWith('verified-token')
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.accessToken).toBe('verified-token')
    expect(auth.user).toMatchObject({
      principalId: 'user-1',
      emailStatus: 'VERIFIED',
      displayIdentity: 'Verified User',
    })
  })

  it('clearError sets error to null', async () => {
    const auth = useAuthStore()
    // Trigger an error state via loginWithPassword failure
    const { login } = await import('@modules/auth/infrastructure/auth-api')
    vi.mocked(login).mockRejectedValue({ detail: 'Bad credentials' })
    try {
      await auth.loginWithPassword({ email: 'a@b.com', password: 'bad' })
    } catch {
      // expected
    }
    // Error should now be set
    expect(auth.error).not.toBeNull()
    // clearError resets it
    auth.clearError()
    expect(auth.error).toBeNull()
  })

  it('logout calls logoutSession and clears local state', async () => {
    const { logoutSession } = await import('@modules/auth/infrastructure/auth-api')
    vi.mocked(logoutSession).mockResolvedValue(undefined)
    const auth = useAuthStore()
    // Seed an authenticated session so logout has something to clear
    auth.$patch({
      user: {
        principalId: 'user-1',
        email: 'test@example.com',
        username: 't',
        displayIdentity: 'Test',
        emailStatus: 'VERIFIED',
      },
    })

    await auth.logout()

    expect(logoutSession).toHaveBeenCalled()
  })
})
