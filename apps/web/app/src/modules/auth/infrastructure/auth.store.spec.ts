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
    getBffSession: vi.fn(),
    getCurrentUserProfile: vi.fn(),
    logoutBffSession: vi.fn(),
    buildBffLoginUrl: (returnTo: string) =>
      `/oauth2/authorization/pt-app?returnTo=${encodeURIComponent(returnTo)}`,
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

  it('preserves the pre-cutover verification action and refreshes its BFF profile', async () => {
    const { verifyEmail, getBffSession } = await import('@modules/auth/infrastructure/auth-api')

    const tokens: AuthTokens = {
      accessToken: 'verified-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      principalId: '18f3c8ec-b5fd-5d86-ac83-465965479890',
      email: 'verified@example.com',
      username: 'verified-user',
      emailStatus: 'PENDING',
      workspaceId: '7e871ce9-881c-5a35-8bfb-192397f375cf',
    }

    const profile: CurrentUserProfile = {
      principalId: '18f3c8ec-b5fd-5d86-ac83-465965479890',
      email: 'verified@example.com',
      username: 'verified-user',
      displayIdentity: 'Verified User',
      emailStatus: 'VERIFIED',
    }

    vi.mocked(verifyEmail).mockResolvedValue(tokens)
    vi.mocked(getBffSession).mockResolvedValue(profile)

    const auth = useAuthStore()

    await auth.verifyEmail('token-verified')

    expect(verifyEmail).toHaveBeenCalledWith('token-verified')
    expect(getBffSession).toHaveBeenCalledWith()
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.accessToken).toBeNull()
    expect(auth.user).toMatchObject({
      principalId: '18f3c8ec-b5fd-5d86-ac83-465965479890',
      emailStatus: 'VERIFIED',
      displayIdentity: 'Verified User',
    })
  })

  it('clearError sets error to null', async () => {
    const auth = useAuthStore()
    auth.error = 'Existing request error'
    expect(auth.error).toBe('Existing request error')
    auth.clearError()
    expect(auth.error).toBeNull()
  })

  it('logout calls the BFF and clears local session state', async () => {
    const { logoutBffSession } = await import('@modules/auth/infrastructure/auth-api')
    vi.mocked(logoutBffSession).mockResolvedValue(undefined)
    const auth = useAuthStore()
    // Seed an authenticated session so logout has something to clear
    auth.$patch({
      user: {
        principalId: '18f3c8ec-b5fd-5d86-ac83-465965479890',
        email: 'test@example.com',
        username: 't',
        displayIdentity: 'Test',
        emailStatus: 'VERIFIED',
      },
    })

    await auth.logout()

    expect(logoutBffSession).toHaveBeenCalledOnce()
    expect(auth.isAuthenticated).toBe(false)
  })
})
