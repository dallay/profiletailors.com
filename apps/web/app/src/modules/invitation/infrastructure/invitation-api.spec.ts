import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { acceptInvitationRequest } from './invitation-api'

function stubFetch(response: Response) {
  const fetchMock = vi.fn(() => Promise.resolve(response))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function stubFetchThrows(error: unknown) {
  const fetchMock = vi.fn(() => Promise.reject(error))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function getFetchArgs(mock: ReturnType<typeof vi.fn>, index = 0) {
  const args = mock.mock.calls[index] as [string, RequestInit] | undefined
  if (!args) throw new Error('fetch not called')
  return { url: args[0], init: args[1] }
}

describe('acceptInvitationRequest', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    delete process.env.VITE_API_BASE_URL
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    delete process.env.VITE_API_BASE_URL
  })

  it('posts token to the invitation endpoint with required headers and credentials', async () => {
    const fetchMock = stubFetch(
      new Response(JSON.stringify({ workspaceId: 'ws-1', membershipStatus: 'ACTIVE' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await acceptInvitationRequest('raw-token-123')

    expect(result).toEqual({
      workspaceId: 'ws-1',
      membershipStatus: 'ACTIVE',
      errorCode: null,
      errorStatus: null,
    })

    expect(fetchMock).toHaveBeenCalledOnce()
    const { url, init } = getFetchArgs(fetchMock)
    expect(url).toBe('http://localhost:7638/api/invitations/accept')
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    expect(init.headers).toMatchObject({
      'Content-Type': 'application/json',
      Accept: 'application/vnd.api.v1+json',
    })
    expect(init.body).toBe(JSON.stringify({ invitationToken: 'raw-token-123' }))
  })

  it('uses custom API base URL when configured', async () => {
    process.env.VITE_API_BASE_URL = 'https://api.example.com'
    const fetchMock = stubFetch(
      new Response(JSON.stringify({ workspaceId: 'ws-1', membershipStatus: 'ACTIVE' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await acceptInvitationRequest('tok')

    const { url } = getFetchArgs(fetchMock)
    expect(url).toBe('https://api.example.com/api/invitations/accept')
  })

  it('uses same-origin path when API base is empty string', async () => {
    process.env.VITE_API_BASE_URL = ''
    const fetchMock = stubFetch(
      new Response(JSON.stringify({ workspaceId: 'ws-1' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await acceptInvitationRequest('tok')

    const { url } = getFetchArgs(fetchMock)
    expect(url).toBe('/api/invitations/accept')
  })

  it('returns workspace and membership from successful payload', async () => {
    stubFetch(
      new Response(JSON.stringify({ workspaceId: 'ws-abc', membershipStatus: 'PENDING' }), {
        status: 200,
      }),
    )

    const result = await acceptInvitationRequest('tok')

    expect(result.workspaceId).toBe('ws-abc')
    expect(result.membershipStatus).toBe('PENDING')
    expect(result.errorCode).toBeNull()
    expect(result.errorStatus).toBeNull()
  })

  it('normalises missing workspace and membership to null on success', async () => {
    stubFetch(new Response(JSON.stringify({}), { status: 200 }))

    const result = await acceptInvitationRequest('tok')

    expect(result.workspaceId).toBeNull()
    expect(result.membershipStatus).toBeNull()
  })

  it('returns null fields when success payload omits workspaceId', async () => {
    stubFetch(new Response(JSON.stringify({ membershipStatus: 'ACTIVE' }), { status: 200 }))

    const result = await acceptInvitationRequest('tok')

    expect(result.workspaceId).toBeNull()
    expect(result.membershipStatus).toBe('ACTIVE')
  })

  it('handles invalid JSON on success by returning null fields', async () => {
    stubFetch(new Response('not-json', { status: 200 }))

    const result = await acceptInvitationRequest('tok')

    expect(result.workspaceId).toBeNull()
    expect(result.membershipStatus).toBeNull()
    expect(result.errorCode).toBeNull()
    expect(result.errorStatus).toBeNull()
  })

  it('handles 204 No Content success as empty payload', async () => {
    stubFetch(new Response(null, { status: 204 }))

    const result = await acceptInvitationRequest('tok')

    expect(result).toEqual({
      workspaceId: null,
      membershipStatus: null,
      errorCode: null,
      errorStatus: null,
    })
  })

  it('returns INTERNAL_ERROR on network failure', async () => {
    stubFetchThrows(new Error('network down'))

    const result = await acceptInvitationRequest('tok')

    expect(result).toEqual({
      workspaceId: null,
      membershipStatus: null,
      errorCode: 'INTERNAL_ERROR',
      errorStatus: 500,
    })
  })

  it('returns INTERNAL_ERROR when fetch throws a non-Error', async () => {
    stubFetchThrows('oops')

    const result = await acceptInvitationRequest('tok')

    expect(result.errorCode).toBe('INTERNAL_ERROR')
    expect(result.errorStatus).toBe(500)
  })

  it('propagates server errorCode when present', async () => {
    stubFetch(
      new Response(JSON.stringify({ errorCode: 'INVITATION_NOT_FOUND', detail: 'not found' }), {
        status: 404,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await acceptInvitationRequest('tok')

    expect(result.errorCode).toBe('INVITATION_NOT_FOUND')
    expect(result.errorStatus).toBe(404)
    expect(result.workspaceId).toBeNull()
  })

  it('falls back to code field when errorCode is absent', async () => {
    stubFetch(
      new Response(JSON.stringify({ code: 'INVITATION_EXPIRED' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await acceptInvitationRequest('tok')

    expect(result.errorCode).toBe('INVITATION_EXPIRED')
    expect(result.errorStatus).toBe(400)
  })

  it('prefers errorCode over code when both are present', async () => {
    stubFetch(
      new Response(JSON.stringify({ errorCode: 'INVITATION_NOT_FOUND', code: 'OTHER' }), {
        status: 404,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await acceptInvitationRequest('tok')

    expect(result.errorCode).toBe('INVITATION_NOT_FOUND')
  })

  it('handles non-JSON error body by classifying status', async () => {
    stubFetch(new Response('plain text error', { status: 400 }))

    const result = await acceptInvitationRequest('tok')

    expect(result.errorCode).toBe('INVITATION_NOT_ACCEPTABLE')
    expect(result.errorStatus).toBe(400)
  })

  it('classifies 400 as INVITATION_NOT_ACCEPTABLE', async () => {
    stubFetch(new Response(JSON.stringify({}), { status: 400 }))

    const result = await acceptInvitationRequest('tok')

    expect(result.errorCode).toBe('INVITATION_NOT_ACCEPTABLE')
    expect(result.errorStatus).toBe(400)
  })

  it('classifies 409 as INVITATION_NOT_ACCEPTABLE', async () => {
    stubFetch(new Response(JSON.stringify({}), { status: 409 }))

    const result = await acceptInvitationRequest('tok')

    expect(result.errorCode).toBe('INVITATION_NOT_ACCEPTABLE')
    expect(result.errorStatus).toBe(409)
  })

  it('classifies 401 as INVITATION_REQUIRES_LOGIN', async () => {
    stubFetch(new Response(JSON.stringify({}), { status: 401 }))

    const result = await acceptInvitationRequest('tok')

    expect(result.errorCode).toBe('INVITATION_REQUIRES_LOGIN')
    expect(result.errorStatus).toBe(401)
  })

  it('classifies 404 as INVITATION_NOT_FOUND', async () => {
    stubFetch(new Response(JSON.stringify({}), { status: 404 }))

    const result = await acceptInvitationRequest('tok')

    expect(result.errorCode).toBe('INVITATION_NOT_FOUND')
  })

  it('classifies 429 as INVITATION_RATE_LIMITED', async () => {
    stubFetch(new Response(JSON.stringify({}), { status: 429 }))

    const result = await acceptInvitationRequest('tok')

    expect(result.errorCode).toBe('INVITATION_RATE_LIMITED')
    expect(result.errorStatus).toBe(429)
  })

  it('classifies 500 as INTERNAL_ERROR', async () => {
    stubFetch(new Response(JSON.stringify({}), { status: 500 }))

    const result = await acceptInvitationRequest('tok')

    expect(result.errorCode).toBe('INTERNAL_ERROR')
  })

  it('classifies 502 as INTERNAL_ERROR', async () => {
    stubFetch(new Response(JSON.stringify({}), { status: 502 }))

    const result = await acceptInvitationRequest('tok')

    expect(result.errorCode).toBe('INTERNAL_ERROR')
  })

  it('classifies unknown status as INTERNAL_ERROR', async () => {
    stubFetch(new Response(JSON.stringify({}), { status: 418 }))

    const result = await acceptInvitationRequest('tok')

    expect(result.errorCode).toBe('INTERNAL_ERROR')
  })

  it('preserves status from error response even when payload contains status field', async () => {
    stubFetch(
      new Response(JSON.stringify({ errorCode: 'CUSTOM', status: 999 }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await acceptInvitationRequest('tok')

    expect(result.errorCode).toBe('CUSTOM')
    expect(result.errorStatus).toBe(403)
  })

  it('does not send Authorization header', async () => {
    const fetchMock = stubFetch(
      new Response(JSON.stringify({ workspaceId: 'ws-1' }), { status: 200 }),
    )

    await acceptInvitationRequest('tok')

    const { init } = getFetchArgs(fetchMock)
    const headers = init.headers as Record<string, string>
    expect(headers.Authorization).toBeUndefined()
    expect(headers['X-Workspace-Id']).toBeUndefined()
  })
})
