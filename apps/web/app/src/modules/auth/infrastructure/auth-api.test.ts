import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  login,
  register,
  refreshSession,
  logoutSession,
  getCurrentUserProfile,
  resendVerification,
  createApiFetch,
  proxyImageUrl,
  resolveApiUrl,
  renameWorkspace,
  verifyEmail,
  type AuthTokens,
  type CurrentUserProfile,
} from './auth-api'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function mockFetch(response: Response) {
  const fetchMock = vi.fn(() => response) as ReturnType<typeof vi.fn>
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function fetchHeaders(fetchMock: ReturnType<typeof vi.fn>, callIndex = 0) {
  return fetchMock.mock.calls[callIndex]?.[1]?.headers as Record<string, string>
}

function mockImportMetaEnv(env: Record<string, string> = {}) {
  // Vite maps process.env to import.meta.env — use this for reliable mocking
  if (env.VITE_API_BASE_URL !== undefined) {
    process.env.VITE_API_BASE_URL = env.VITE_API_BASE_URL
  } else {
    delete process.env.VITE_API_BASE_URL
  }
}

// ---------------------------------------------------------------------------
// login
// ---------------------------------------------------------------------------

describe('login', () => {
  beforeEach(() => {
    mockImportMetaEnv({})
  })

  it('returns auth tokens on successful login', async () => {
    const tokens: AuthTokens = {
      accessToken: 'access-123',
      tokenType: 'Bearer',
      expiresIn: 3600,
      principalId: 'user-1',
      email: 'user@example.com',
      username: 'testuser',
      emailStatus: 'ACTIVE',
      workspaceId: null,
    }
    const fetchMock = mockFetch(
      new Response(JSON.stringify(tokens), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await login({ email: 'user@example.com', password: 'password123' })

    expect(result).toEqual(tokens)
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:7638/api/auth/login',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        headers: expect.objectContaining({
          'Content-Type': 'application/json',
          Accept: 'application/vnd.api.v1+json',
        }),
        body: JSON.stringify({ email: 'user@example.com', password: 'password123' }),
      }),
    )
  })

  it('throws ApiError on login failure', async () => {
    mockFetch(
      new Response(JSON.stringify({ title: 'Unauthorized', detail: 'Invalid credentials' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(login({ email: 'user@example.com', password: 'wrong' })).rejects.toMatchObject({
      title: 'Unauthorized',
      detail: 'Invalid credentials',
      status: 401,
    })
  })

  it('throws ApiError on server error', async () => {
    mockFetch(
      new Response(
        JSON.stringify({ title: 'Internal Server Error', detail: 'Something went wrong' }),
        {
          status: 500,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )

    await expect(login({ email: 'user@example.com', password: 'password' })).rejects.toMatchObject({
      title: 'Internal Server Error',
      detail: 'Something went wrong',
      status: 500,
    })
  })

  it('throws ApiError when server returns non-JSON error body', async () => {
    mockFetch(
      new Response('plain text error', {
        status: 400,
        headers: { 'Content-Type': 'text/plain' },
      }),
    )

    await expect(login({ email: 'user@example.com', password: 'password' })).rejects.toMatchObject({
      title: 'Request failed',
      detail: 'An unexpected error occurred.',
      status: 400,
    })
  })
})

// ---------------------------------------------------------------------------
// register
// ---------------------------------------------------------------------------

describe('register', () => {
  beforeEach(() => {
    mockImportMetaEnv({})
  })

  it('returns auth tokens on successful registration', async () => {
    const tokens: AuthTokens = {
      accessToken: 'access-456',
      tokenType: 'Bearer',
      expiresIn: 7200,
      principalId: 'user-2',
      email: 'newuser@example.com',
      username: 'newuser',
      emailStatus: 'PENDING',
      workspaceId: null,
    }
    const fetchMock = mockFetch(
      new Response(JSON.stringify(tokens), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await register({
      email: 'newuser@example.com',
      password: 'password123',
    })

    expect(result).toEqual(tokens)
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:7638/api/auth/register',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          email: 'newuser@example.com',
          password: 'password123',
        }),
      }),
    )
  })
})

// ---------------------------------------------------------------------------
// refreshSession
// ---------------------------------------------------------------------------

describe('refreshSession', () => {
  beforeEach(() => {
    mockImportMetaEnv({})
  })

  it('returns auth tokens on successful refresh', async () => {
    const tokens: AuthTokens = {
      accessToken: 'access-refreshed',
      tokenType: 'Bearer',
      expiresIn: 3600,
      principalId: 'user-1',
      email: 'user@example.com',
      username: null,
      emailStatus: 'ACTIVE',
      workspaceId: null,
    }
    mockFetch(
      new Response(JSON.stringify(tokens), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await refreshSession()

    expect(result).toEqual(tokens)
  })

  it('returns null on 401 (no active session)', async () => {
    mockFetch(new Response(null, { status: 401 }))

    const result = await refreshSession()

    expect(result).toBeNull()
  })

  it('re-throws non-401 errors', async () => {
    mockFetch(
      new Response(JSON.stringify({ title: 'Server Error', detail: 'Database error' }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(refreshSession()).rejects.toMatchObject({
      title: 'Server Error',
      detail: 'Database error',
      status: 500,
    })
  })
})

// ---------------------------------------------------------------------------
// logoutSession
// ---------------------------------------------------------------------------

describe('logoutSession', () => {
  beforeEach(() => {
    mockImportMetaEnv({})
  })

  it('always resolves even on network error', async () => {
    mockFetch(new Response(null, { status: 500 }))

    // Should not throw
    await expect(logoutSession()).resolves.toBeUndefined()
  })

  it('resolves successfully on 204 No Content', async () => {
    mockFetch(new Response(null, { status: 204 }))

    await expect(logoutSession()).resolves.toBeUndefined()
  })
})

// ---------------------------------------------------------------------------
// getCurrentUserProfile
// ---------------------------------------------------------------------------

describe('getCurrentUserProfile', () => {
  beforeEach(() => {
    mockImportMetaEnv({})
  })

  it('returns current user profile with authoritative email status', async () => {
    const profile: CurrentUserProfile = {
      principalId: 'user-1',
      email: 'user@example.com',
      username: 'testuser',
      displayIdentity: 'testuser (user@example.com)',
      emailStatus: 'PENDING',
    }
    const fetchMock = mockFetch(
      new Response(JSON.stringify(profile), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await getCurrentUserProfile('access-token-123')

    expect(result).toEqual(profile)
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:7638/api/auth/me',
      expect.objectContaining({
        method: 'GET',
        headers: expect.objectContaining({
          Authorization: 'Bearer access-token-123',
        }),
      }),
    )
  })

  it('throws ApiError on failure', async () => {
    mockFetch(
      new Response(JSON.stringify({ title: 'Unauthorized', detail: 'Invalid token' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(getCurrentUserProfile('bad-token')).rejects.toMatchObject({
      title: 'Unauthorized',
      detail: 'Invalid token',
      status: 401,
    })
  })
})

// ---------------------------------------------------------------------------
// resendVerification
// ---------------------------------------------------------------------------

describe('resendVerification', () => {
  beforeEach(() => {
    mockImportMetaEnv({})
  })

  it('posts the current email to the resend endpoint', async () => {
    const fetchMock = mockFetch(new Response(null, { status: 202 }))

    await expect(resendVerification('user@example.com')).resolves.toBeUndefined()

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:7638/api/auth/resend-verification',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ email: 'user@example.com' }),
      }),
    )
  })
})

// ---------------------------------------------------------------------------
// createApiFetch
// ---------------------------------------------------------------------------

describe('createApiFetch', () => {
  beforeEach(() => {
    mockImportMetaEnv({})
  })

  it('returns data on successful request', async () => {
    mockFetch(
      new Response(JSON.stringify({ id: 'post-1', text: 'Hello' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const apiFetch = createApiFetch({
      getToken: () => 'access-token',
      onRefresh: async () => null,
      onUnauthenticated: vi.fn(),
    })

    const result = await apiFetch('/api/posts/1')

    expect(result).toEqual({ id: 'post-1', text: 'Hello' })
  })

  it('retries once after successful token refresh on 401', async () => {
    let callCount = 0
    const fetchMock = vi.fn(() => {
      callCount++
      if (callCount === 1) {
        return Promise.resolve(new Response(null, { status: 401 }))
      }
      return Promise.resolve(
        new Response(JSON.stringify({ id: 'post-1', text: 'Hello' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
    })
    vi.stubGlobal('fetch', fetchMock)

    const onUnauthenticated = vi.fn()
    const apiFetch = createApiFetch({
      getToken: () => 'expired-token',
      onRefresh: async () => 'new-access-token',
      onUnauthenticated,
    })

    const result = await apiFetch('/api/posts/1')

    expect(result).toEqual({ id: 'post-1', text: 'Hello' })
    expect(callCount).toBe(2)
    // First call should have used the old token
    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      'http://localhost:7638/api/posts/1',
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: 'Bearer expired-token' }),
      }),
    )
    // Second call should have used the new token
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      'http://localhost:7638/api/posts/1',
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: 'Bearer new-access-token' }),
      }),
    )
    expect(onUnauthenticated).not.toHaveBeenCalled()
  })

  it('calls onUnauthenticated and re-throws when refresh returns null on 401', async () => {
    mockFetch(new Response(null, { status: 401 }))

    const onUnauthenticated = vi.fn()
    const apiFetch = createApiFetch({
      getToken: () => 'expired-token',
      onRefresh: async () => null,
      onUnauthenticated,
    })

    await expect(apiFetch('/api/posts/1')).rejects.toMatchObject({
      title: 'Request failed',
      detail: 'An unexpected error occurred.',
      status: 401,
    })
    expect(onUnauthenticated).toHaveBeenCalledOnce()
  })

  it('calls onUnauthenticated and propagates refresh errors when token refresh rejects', async () => {
    mockFetch(new Response(null, { status: 401 }))

    const refreshError = new Error('refresh failed')
    const onUnauthenticated = vi.fn()
    const apiFetch = createApiFetch({
      getToken: () => 'expired-token',
      onRefresh: async () => {
        throw refreshError
      },
      onUnauthenticated,
    })

    await expect(apiFetch('/api/posts/1')).rejects.toBe(refreshError)
    expect(onUnauthenticated).toHaveBeenCalledOnce()
  })

  it('calls onUnauthenticated and propagates retry failure when retried request fails with 401', async () => {
    mockFetch(
      new Response(null, {
        status: 401,
        statusText: 'Unauthorized',
      }),
    )

    const onUnauthenticated = vi.fn()
    const apiFetch = createApiFetch({
      getToken: () => 'expired-token',
      onRefresh: async () => 'new-access-token',
      onUnauthenticated,
    })

    await expect(apiFetch('/api/posts/1')).rejects.toMatchObject({
      status: 401,
    })
    expect(onUnauthenticated).toHaveBeenCalledOnce()
  })

  it('re-throws non-401 errors without refresh attempt', async () => {
    mockFetch(
      new Response(JSON.stringify({ title: 'Forbidden', detail: 'Access denied' }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const onRefresh = vi.fn()
    const apiFetch = createApiFetch({
      getToken: () => 'access-token',
      onRefresh,
      onUnauthenticated: vi.fn(),
    })

    await expect(apiFetch('/api/posts/1')).rejects.toMatchObject({
      title: 'Forbidden',
      detail: 'Access denied',
      status: 403,
    })
    expect(onRefresh).not.toHaveBeenCalled()
  })

  it('injects workspace header for workspace-scoped requests', async () => {
    const fetchMock = mockFetch(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const apiFetch = createApiFetch({
      getToken: () => 'token',
      getWorkspaceId: () => 'workspace-1',
      onRefresh: async () => null,
      onUnauthenticated: vi.fn(),
    })

    await apiFetch('/api/publishing/channels', { workspaceScoped: true })

    expect(fetchHeaders(fetchMock)['X-Workspace-Id']).toBe('workspace-1')
  })

  it('prevents workspace-scoped requests when workspace is missing', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    const apiFetch = createApiFetch({
      getToken: () => 'token',
      getWorkspaceId: () => null,
      onRefresh: async () => null,
      onUnauthenticated: vi.fn(),
    })

    await expect(
      apiFetch('/api/publishing/channels', { workspaceScoped: true }),
    ).rejects.toMatchObject({
      title: 'Workspace context required',
      detail: 'Workspace context is required for this request.',
      status: 400,
    })
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('does not require workspace for non-workspace requests', async () => {
    const fetchMock = mockFetch(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const apiFetch = createApiFetch({
      getToken: () => 'token',
      getWorkspaceId: () => null,
      onRefresh: async () => null,
      onUnauthenticated: vi.fn(),
    })

    await apiFetch('/api/auth/me')

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchHeaders(fetchMock)['X-Workspace-Id']).toBeUndefined()
  })

  it('preserves workspace header after successful token refresh on 401', async () => {
    let callCount = 0
    const fetchMock = vi.fn(() => {
      callCount++
      if (callCount === 1) return Promise.resolve(new Response(null, { status: 401 }))
      return Promise.resolve(
        new Response(JSON.stringify({ ok: true }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
    })
    vi.stubGlobal('fetch', fetchMock)

    const apiFetch = createApiFetch({
      getToken: () => 'expired-token',
      getWorkspaceId: () => 'workspace-1',
      onRefresh: async () => 'new-token',
      onUnauthenticated: vi.fn(),
    })

    await apiFetch('/api/publishing/channels', { workspaceScoped: true })

    expect(fetchHeaders(fetchMock)['X-Workspace-Id']).toBe('workspace-1')
    expect(fetchHeaders(fetchMock, 1)['X-Workspace-Id']).toBe('workspace-1')
  })

  it('returns raw streaming responses with workspace and accept headers', async () => {
    const fetchMock = mockFetch(new Response('event: heartbeat\n\n', { status: 200 }))
    const apiFetch = createApiFetch({
      getToken: () => 'token',
      getWorkspaceId: () => 'workspace-1',
      onRefresh: async () => null,
      onUnauthenticated: vi.fn(),
    })

    const response = await apiFetch.raw('/api/publishing/channels/events', {
      method: 'GET',
      headers: { Accept: 'text/event-stream' },
      workspaceScoped: true,
    })

    expect(response).toBeInstanceOf(Response)
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:7638/api/publishing/channels/events',
      expect.objectContaining({
        method: 'GET',
        headers: expect.objectContaining({
          Accept: 'text/event-stream',
          Authorization: 'Bearer token',
          'X-Workspace-Id': 'workspace-1',
        }),
      }),
    )
  })

  it('does not force application/json content type for FormData bodies', async () => {
    const fetchMock = mockFetch(new Response(null, { status: 204 }))
    const apiFetch = createApiFetch({
      getToken: () => 'token',
      getWorkspaceId: () => 'workspace-1',
      onRefresh: async () => null,
      onUnauthenticated: vi.fn(),
    })

    const formData = new FormData()
    formData.append('file', new Blob(['abc'], { type: 'text/plain' }), 'sample.txt')

    await apiFetch.raw('/api/media/assets/asset-1/upload', {
      method: 'POST',
      body: formData,
      workspaceScoped: true,
    })

    const headers = fetchHeaders(fetchMock)
    expect(headers.Accept).toBe('application/vnd.api.v1+json')
    expect(headers.Authorization).toBe('Bearer token')
    expect(headers['X-Workspace-Id']).toBe('workspace-1')
    expect(headers['Content-Type']).toBeUndefined()
  })

  it('uses custom API base URL from environment', async () => {
    // Vite maps process.env to import.meta.env — use this to override
    const original = process.env.VITE_API_BASE_URL
    process.env.VITE_API_BASE_URL = 'https://api.example.com'

    const fetchMock = mockFetch(
      new Response(JSON.stringify({ id: '1' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const apiFetch = createApiFetch({
      getToken: () => 'token',
      onRefresh: async () => null,
      onUnauthenticated: vi.fn(),
    })

    await apiFetch('/api/test')

    expect(fetchMock).toHaveBeenCalledWith('https://api.example.com/api/test', expect.any(Object))

    // Restore
    if (original === undefined) {
      delete process.env.VITE_API_BASE_URL
    } else {
      process.env.VITE_API_BASE_URL = original
    }
  })
})

// ---------------------------------------------------------------------------
// resolveApiUrl
// ---------------------------------------------------------------------------

describe('resolveApiUrl', () => {
  beforeEach(() => {
    mockImportMetaEnv({})
  })

  it('prepends the default API base URL to an absolute path', () => {
    delete process.env.VITE_API_BASE_URL
    expect(resolveApiUrl('/api/media/assets/abc')).toBe(
      'http://localhost:7638/api/media/assets/abc',
    )
  })

  it('returns the path unchanged when it does not start with /', () => {
    delete process.env.VITE_API_BASE_URL
    expect(resolveApiUrl('https://cdn.example.com/image.jpg')).toBe(
      'https://cdn.example.com/image.jpg',
    )
  })

  it('prepends a custom API base URL configured via env', () => {
    process.env.VITE_API_BASE_URL = 'https://api.staging.example.com'
    expect(resolveApiUrl('/api/media/assets/xyz')).toBe(
      'https://api.staging.example.com/api/media/assets/xyz',
    )
    delete process.env.VITE_API_BASE_URL
  })

  it('returns the path as-is when API base is empty string (same-origin)', () => {
    process.env.VITE_API_BASE_URL = ''
    // resolveApiBaseUrl() returns '' → apiBase is falsy → returns path unchanged
    expect(resolveApiUrl('/api/media/assets/abc')).toBe('/api/media/assets/abc')
    delete process.env.VITE_API_BASE_URL
  })
})

// ---------------------------------------------------------------------------
// login — Zod validation
// ---------------------------------------------------------------------------

describe('login — Zod validation', () => {
  beforeEach(() => {
    mockImportMetaEnv({})
  })

  it('trims and lowercases the email before sending', async () => {
    const tokens = {
      accessToken: 'tok',
      tokenType: 'Bearer',
      expiresIn: 3600,
      principalId: 'p1',
      email: 'user@example.com',
      username: null,
      emailStatus: 'ACTIVE',
      workspaceId: null,
    }
    const fetchMock = mockFetch(
      new Response(JSON.stringify(tokens), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await login({ email: '  User@Example.COM  ', password: 'secret123' })

    const sentBody = JSON.parse(
      (fetchMock.mock.calls[0]?.[1] as RequestInit | undefined)?.body as string,
    )
    expect(sentBody.email).toBe('user@example.com')
    expect(sentBody.password).toBe('secret123')
  })

  it('trims whitespace from the password before sending', async () => {
    const tokens = {
      accessToken: 'tok',
      tokenType: 'Bearer',
      expiresIn: 3600,
      principalId: 'p1',
      email: 'user@example.com',
      username: null,
      emailStatus: 'ACTIVE',
      workspaceId: null,
    }
    const fetchMock = mockFetch(
      new Response(JSON.stringify(tokens), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await login({ email: 'user@example.com', password: '  mypassword  ' })

    const sentBody = JSON.parse(
      (fetchMock.mock.calls[0]?.[1] as RequestInit | undefined)?.body as string,
    )
    expect(sentBody.password).toBe('mypassword')
  })

  it('rejects invalid email format before making a fetch call', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(login({ email: 'not-an-email', password: 'secret' })).rejects.toThrow()

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('rejects blank password before making a fetch call', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(login({ email: 'user@example.com', password: '   ' })).rejects.toThrow()

    expect(fetchMock).not.toHaveBeenCalled()
  })
})

// ---------------------------------------------------------------------------
// register — Zod validation
// ---------------------------------------------------------------------------

describe('register — Zod validation', () => {
  beforeEach(() => {
    mockImportMetaEnv({})
  })

  it('trims and lowercases the email before sending', async () => {
    const tokens = {
      accessToken: 'tok',
      tokenType: 'Bearer',
      expiresIn: 3600,
      principalId: 'p1',
      email: 'newuser@example.com',
      username: null,
      emailStatus: 'PENDING',
      workspaceId: null,
    }
    const fetchMock = mockFetch(
      new Response(JSON.stringify(tokens), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await register({ email: '  NewUser@Example.COM  ', password: 'password123' })

    const sentBody = JSON.parse(
      (fetchMock.mock.calls[0]?.[1] as RequestInit | undefined)?.body as string,
    )
    expect(sentBody.email).toBe('newuser@example.com')
  })

  it('rejects invalid email format before making a fetch call', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(register({ email: 'bad@@email', password: 'password' })).rejects.toThrow()

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('rejects blank password before making a fetch call', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(register({ email: 'user@example.com', password: '   ' })).rejects.toThrow()

    expect(fetchMock).not.toHaveBeenCalled()
  })
})

// ---------------------------------------------------------------------------
// renameWorkspace — Zod validation
// ---------------------------------------------------------------------------

describe('renameWorkspace', () => {
  beforeEach(() => {
    mockImportMetaEnv({})
  })

  it('trims the workspace name before sending', async () => {
    const fetchMock = mockFetch(
      new Response(JSON.stringify({ workspaceId: 'ws-1', name: 'My Studio' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await renameWorkspace('  My Studio  ', 'access-token', 'ws-1')

    const sentBody = JSON.parse(
      (fetchMock.mock.calls[0]?.[1] as RequestInit | undefined)?.body as string,
    )
    expect(sentBody.name).toBe('My Studio')
  })

  it('rejects blank workspace name before making a fetch call', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(renameWorkspace('   ', 'access-token', 'ws-1')).rejects.toThrow()

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('rejects workspace name longer than 255 characters', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    await expect(renameWorkspace('a'.repeat(256), 'access-token', 'ws-1')).rejects.toThrow()

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('sends the workspace id as a header', async () => {
    const fetchMock = mockFetch(
      new Response(JSON.stringify({ workspaceId: 'ws-1', name: 'Studio' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await renameWorkspace('Studio', 'access-token', 'ws-1')

    expect(fetchHeaders(fetchMock)['X-Workspace-Id']).toBe('ws-1')
  })
})

// ---------------------------------------------------------------------------
// verifyEmail
// ---------------------------------------------------------------------------

describe('verifyEmail', () => {
  beforeEach(() => {
    mockImportMetaEnv({})
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
    const fetchMock = mockFetch(
      new Response(JSON.stringify(tokens), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const result = await verifyEmail('token-123')

    expect(result).toEqual(tokens)
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:7638/api/auth/verify-email',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ token: 'token-123' }),
      }),
    )
  })
})

// ---------------------------------------------------------------------------
// proxyImageUrl
// ---------------------------------------------------------------------------

describe('proxyImageUrl', () => {
  beforeEach(() => {
    mockImportMetaEnv({})
  })

  it('uses the media proxy path when the API base is same-origin', () => {
    process.env.VITE_API_BASE_URL = ''

    expect(proxyImageUrl('https://media.licdn.com/dms/image/v2/avatar.jpg')).toBe(
      '/api/media/proxy?url=https%3A%2F%2Fmedia.licdn.com%2Fdms%2Fimage%2Fv2%2Favatar.jpg',
    )
  })

  it('returns already proxied URLs unchanged in same-origin mode', () => {
    process.env.VITE_API_BASE_URL = ''

    expect(
      proxyImageUrl(
        'http://localhost:3000/api/media/proxy?url=https%3A%2F%2Fmedia.licdn.com%2Fa.jpg',
      ),
    ).toBe('http://localhost:3000/api/media/proxy?url=https%3A%2F%2Fmedia.licdn.com%2Fa.jpg')
  })

  it('uses the configured absolute API base for external images', () => {
    process.env.VITE_API_BASE_URL = 'https://api.example.com'

    expect(proxyImageUrl('https://media.licdn.com/dms/image/v2/avatar.jpg')).toBe(
      'https://api.example.com/api/media/proxy?url=https%3A%2F%2Fmedia.licdn.com%2Fdms%2Fimage%2Fv2%2Favatar.jpg',
    )
  })
})
