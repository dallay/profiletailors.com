import { authCredentialsSchema, workspaceNameSchema } from '@shared/lib/validation/schemas'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type AuthTokens = {
  accessToken: string
  tokenType: string
  expiresIn: number
  principalId: string
  email: string
  username: string | null
  emailStatus: string
  workspaceId: string | null
}

export type CurrentUserProfile = {
  principalId: string
  email: string | null
  username: string | null
  displayIdentity: string
  emailStatus: string | null
}

export type LoginPayload = {
  email: string
  password: string
}

export type ApiError = {
  title?: string
  detail?: string
  status?: number
  code?: string
  errorCode?: string
}

export type ApiFetchOptions = RequestInit & {
  workspaceScoped?: boolean
}

/** Creates an Error that also satisfies the ApiError shape for throw sites. */
function apiError(
  title: string,
  detail: string,
  status: number,
  properties: Pick<ApiError, 'code' | 'errorCode'> = {},
): Error & ApiError {
  return Object.assign(new Error(title), { title, detail, status, ...properties })
}

// ---------------------------------------------------------------------------
// Base request helper
// ---------------------------------------------------------------------------

function resolveApiBaseUrl(): string {
  const envValue = import.meta.env.VITE_API_BASE_URL
  // When VITE_API_BASE_URL is absent, falls back to localhost for backwards compat.
  // When explicitly set to '', returns same-origin (Vite proxy in development).
  if (typeof envValue === 'string') {
    return envValue
  }
  return 'http://localhost:7638'
}

export function resolveApiUrl(path: string): string {
  if (!path.startsWith('/')) return path
  const apiBase = resolveApiBaseUrl()
  return apiBase ? `${apiBase}${path}` : path
}

/**
 * Low-level fetch wrapper.
 * Always sends cookies (`credentials: 'include'`) so the refresh-token
 * HttpOnly cookie is forwarded automatically by the browser.
 *
 * Includes API versioning via Accept header (application/vnd.api.v1+json).
 */
async function requestRaw(
  path: string,
  init: RequestInit = {},
  token?: string | null,
): Promise<Response> {
  const hasExplicitContentType = new Headers(init.headers ?? {}).has('Content-Type')
  const isFormDataBody = typeof FormData !== 'undefined' && init.body instanceof FormData

  const response = await fetch(`${resolveApiBaseUrl()}${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      ...(!hasExplicitContentType && !isFormDataBody ? { 'Content-Type': 'application/json' } : {}),
      Accept: 'application/vnd.api.v1+json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  })

  if (!response.ok) {
    let payload: ApiError | null = null

    try {
      payload = (await response.json()) as ApiError
    } catch {
      payload = null
    }

    throw apiError(
      payload?.title ?? 'Request failed',
      payload?.detail ?? 'An unexpected error occurred.',
      response.status,
      { code: payload?.code, errorCode: payload?.errorCode },
    )
  }

  return response
}

async function request<T>(path: string, init: RequestInit = {}, token?: string | null): Promise<T> {
  const response = await requestRaw(path, init, token)

  // 204 No Content — return empty object cast to T
  if (response.status === 204) {
    return Object.create(null) as T
  }

  return response.json() as Promise<T>
}

// ---------------------------------------------------------------------------
// Auth endpoints
// ---------------------------------------------------------------------------

export async function register(payload: LoginPayload): Promise<AuthTokens> {
  const validatedPayload = authCredentialsSchema.parse(payload)

  return request<AuthTokens>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(validatedPayload),
  })
}

export async function login(payload: LoginPayload): Promise<AuthTokens> {
  const validatedPayload = authCredentialsSchema.parse(payload)

  return request<AuthTokens>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(validatedPayload),
  })
}

/**
 * Uses the HttpOnly refresh-token cookie (sent automatically by the browser)
 * to obtain a fresh access token.
 * Returns null if the server responds with 401 (no active session).
 */
export async function refreshSession(): Promise<AuthTokens | null> {
  try {
    return await request<AuthTokens>('/api/auth/refresh', { method: 'POST' })
  } catch (err) {
    const apiError = err as ApiError
    if (apiError.status === 401) {
      return null
    }
    throw err
  }
}

/**
 * Invalidates the refresh-token session on the server and clears the cookie.
 * Fire-and-forget safe: always resolves.
 */
export async function logoutSession(): Promise<void> {
  try {
    await request<void>('/api/auth/logout', { method: 'POST' })
  } catch {
    // Best-effort — local session will be cleared regardless
  }
}

export async function getCurrentUserProfile(token: string): Promise<CurrentUserProfile> {
  return request<CurrentUserProfile>('/api/auth/me', { method: 'GET' }, token)
}

export async function verifyEmail(token: string): Promise<AuthTokens> {
  return request<AuthTokens>('/api/auth/verify-email', {
    method: 'POST',
    body: JSON.stringify({ token }),
  })
}

export async function resendVerification(email: string): Promise<void> {
  await requestRaw('/api/auth/resend-verification', {
    method: 'POST',
    body: JSON.stringify({ email }),
  })
}

export type RenameWorkspaceResult = {
  workspaceId: string
  name: string
}

export async function renameWorkspace(
  name: string,
  token: string,
  workspaceId: string,
): Promise<RenameWorkspaceResult> {
  const validatedName = workspaceNameSchema.parse(name)

  return request<RenameWorkspaceResult>(
    '/api/tenancy/workspaces/current/name',
    {
      method: 'PATCH',
      body: JSON.stringify({ name: validatedName }),
      headers: { 'X-Workspace-Id': workspaceId },
    },
    token,
  )
}

// ---------------------------------------------------------------------------
// Workspace endpoints
// ---------------------------------------------------------------------------

export type WorkspaceSummary = {
  workspaceId: string
  name: string
  role: string
  icon: string | null
}

/**
 * Fetch all workspaces the authenticated user belongs to.
 * Does NOT require an X-Workspace-Id header — lists across all contexts.
 */
export async function fetchWorkspaces(token: string): Promise<WorkspaceSummary[]> {
  return request<WorkspaceSummary[]>('/api/tenancy/workspaces', { method: 'GET' }, token)
}

// ---------------------------------------------------------------------------
// Configured providers
// ---------------------------------------------------------------------------

export type ConfiguredProvider = {
  name: string
  configured: boolean
}

export type ConfiguredProvidersResponse = {
  providers: ConfiguredProvider[]
}

export async function fetchConfiguredProviders(
  token: string,
  workspaceId: string,
): Promise<ConfiguredProvidersResponse> {
  return request<ConfiguredProvidersResponse>(
    '/api/publishing/channels/providers',
    {
      method: 'GET',
      headers: { 'X-Workspace-Id': workspaceId },
    },
    token,
  )
}

export type UpdateWorkspaceIconResult = {
  workspaceId: string
  icon: string | null
}

export async function updateWorkspaceIcon(
  icon: string | null,
  token: string,
  workspaceId: string,
): Promise<UpdateWorkspaceIconResult> {
  return request<UpdateWorkspaceIconResult>(
    '/api/tenancy/workspaces/current/icon',
    {
      method: 'PATCH',
      body: JSON.stringify({ icon }),
      headers: { 'X-Workspace-Id': workspaceId },
    },
    token,
  )
}

// ---------------------------------------------------------------------------
// apiFetch — authenticated wrapper with a single silent 401 retry
// ---------------------------------------------------------------------------

type TokenProvider = () => string | null
type TokenRefresher = () => Promise<string | null>

/**
 * Higher-order helper: executes a request, retries once on 401 after refreshing
 * the token, and calls onUnauthenticated if the refresh fails.
 */
async function withRetry<T>(
  requester: (init: RequestInit, token: string | null) => Promise<T>,
  init: RequestInit,
  token: string | null,
  onRefresh: TokenRefresher,
  onUnauthenticated: () => void,
): Promise<T> {
  try {
    return await requester(init, token)
  } catch (err) {
    const apiError = err as ApiError
    if (apiError.status !== 401) throw err

    let newToken: string | null
    try {
      newToken = await onRefresh()
    } catch (refreshError) {
      onUnauthenticated()
      throw refreshError
    }

    if (!newToken) {
      onUnauthenticated()
      throw err
    }

    try {
      return await requester(init, newToken)
    } catch (retryErr) {
      const apiError = retryErr as ApiError
      if (apiError.status === 401) {
        onUnauthenticated()
      }
      throw retryErr
    }
  }
}

/**
 * Creates an authenticated fetch wrapper.
 *
 * On the first 401 it attempts a token refresh via `onRefresh`.
 * If refresh succeeds it retries the original request once.
 * If refresh fails it calls `onUnauthenticated` and rejects.
 */
export function createApiFetch(opts: {
  getToken: TokenProvider
  getWorkspaceId?: () => string | null
  onRefresh: TokenRefresher
  onUnauthenticated: () => void
}): (<T>(path: string, init?: ApiFetchOptions) => Promise<T>) & {
  raw: (path: string, init?: ApiFetchOptions) => Promise<Response>
} {
  function withWorkspace(init: ApiFetchOptions): RequestInit {
    const { workspaceScoped, ...requestInit } = init

    if (!workspaceScoped) {
      return requestInit
    }

    const workspaceId = opts.getWorkspaceId?.()
    if (!workspaceId) {
      throw apiError(
        'Workspace context required',
        'Workspace context is required for this request.',
        400,
      )
    }

    return {
      ...requestInit,
      headers: {
        ...requestInit.headers,
        'X-Workspace-Id': workspaceId,
      },
    }
  }

  async function apiFetchRaw(path: string, init: ApiFetchOptions = {}): Promise<Response> {
    const token = opts.getToken()
    const requestInit = withWorkspace(init)

    return withRetry(
      (innerInit, innerToken) => requestRaw(path, innerInit, innerToken),
      requestInit,
      token,
      opts.onRefresh,
      opts.onUnauthenticated,
    )
  }

  async function apiFetch<T>(path: string, init: ApiFetchOptions = {}): Promise<T> {
    const token = opts.getToken()
    const requestInit = withWorkspace(init)

    return withRetry(
      (innerInit, innerToken) => request<T>(path, innerInit, innerToken),
      requestInit,
      token,
      opts.onRefresh,
      opts.onUnauthenticated,
    ) as Promise<T>
  }

  return Object.assign(apiFetch, {
    raw: apiFetchRaw,
  })
}

/**
 * Create a proxy URL for external images to bypass ad-blockers.
 * Routes through our backend which fetches the image server-side.
 */
export function proxyImageUrl(originalUrl: string): string {
  if (!originalUrl) return originalUrl
  // Only proxy external URLs (not relative or same-origin)
  if (originalUrl.startsWith('/')) return originalUrl
  try {
    const url = new URL(originalUrl)
    const apiBase = resolveApiBaseUrl()
    const proxyBase = apiBase || globalThis.location.origin
    const proxyUrl = new URL('/api/media/proxy', proxyBase)
    // Don't proxy URLs that are ALREADY proxied through OUR backend
    if (url.origin === proxyUrl.origin && url.pathname.startsWith(proxyUrl.pathname)) {
      return originalUrl
    }
    proxyUrl.searchParams.set('url', originalUrl)
    return apiBase ? proxyUrl.toString() : `${proxyUrl.pathname}${proxyUrl.search}`
  } catch {
    return originalUrl
  }
}
