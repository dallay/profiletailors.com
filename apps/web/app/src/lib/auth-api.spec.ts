import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthTokens } from './auth-api'
import { verifyEmail } from './auth-api'

const fetchMock = vi.fn()

describe('auth-api verifyEmail', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  it('posts the token to the verify-email endpoint and returns issued auth tokens', async () => {
    const tokens: AuthTokens = {
      accessToken: 'access-verified',
      tokenType: 'Bearer',
      expiresIn: 3600,
      principalId: 'user-1',
      email: 'verified@example.com',
      username: 'verified-user',
      emailStatus: 'VERIFIED',
      workspaceId: 'ws-1',
    }

    fetchMock.mockResolvedValue(
      new Response(JSON.stringify(tokens), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await verifyEmail('token-123')

    expect(result).toEqual(tokens)
    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/auth/verify-email', {
      method: 'POST',
      body: JSON.stringify({ token: 'token-123' }),
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/vnd.api.v1+json',
      },
    })
  })
})
