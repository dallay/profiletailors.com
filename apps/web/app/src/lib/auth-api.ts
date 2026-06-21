// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface AuthTokens {
  accessToken: string
  tokenType: string
  expiresIn: number
  principalId: string
  email: string
  username: string | null
  emailStatus: string
  workspaceId: string | null
}

export interface CurrentUserProfile {
  principalId: string
  email: string | null
  username: string | null
  displayIdentity: string
}

interface LoginPayload {
  email: string
  password: string
}

interface RegisterPayload extends LoginPayload {}

export interface ApiError {
  title?: string
  detail?: string
  status?: number
}

export type ApiFetchOptions = RequestInit & {
  workspaceScoped?: boolean
}

// ---------------------------------------------------------------------------
// Base request helper
// ---------------------------------------------------------------------------

const DEFAULT_API_BASE_URL = 'http://localhost:8080'

function resolveApiBaseUrl(): string {
  const envValue = import.meta.env.VITE_API_BASE_URL
  // Allow explicit '' to mean same-origin (for Vite proxy in development).
  // When unset, falls back to DEFAULT_API_BASE_URL for backwards compat.
  if (typeof envValue === 'string') {
    return envValue
  }
  return DEFAULT_API_BASE_URL
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
  const response = await fetch(`${resolveApiBaseUrl()}${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/vnd.api.v1+json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init.headers ?? {}),
    },
  })

  if (!response.ok) {
    let payload: ApiError | null = null

    try {
      payload = (await response.json()) as ApiError
    } catch {
      payload = null
    }

    throw {
      title: payload?.title ?? 'Request failed',
      detail: payload?.detail ?? 'An unexpected error occurred.',
      status: response.status,
    } satisfies ApiError
  }

  return response
}

async function request<T>(path: string, init: RequestInit = {}, token?: string | null): Promise<T> {
  const response = await requestRaw(path, init, token)

  // 204 No Content — return empty object cast to T
  if (response.status === 204) {
    return {} as T
  }

  return response.json() as Promise<T>
}

// ---------------------------------------------------------------------------
// Auth endpoints
// ---------------------------------------------------------------------------

export async function register(payload: RegisterPayload) {
  return request<AuthTokens>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function login(payload: LoginPayload) {
  return request<AuthTokens>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
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

export async function getCurrentUserProfile(token: string) {
  return request<CurrentUserProfile>('/api/auth/me', { method: 'GET' }, token)
}

export interface RenameWorkspaceResult {
  workspaceId: string
  name: string
}

export async function renameWorkspace(
  name: string,
  token: string,
  workspaceId: string,
): Promise<RenameWorkspaceResult> {
  return request<RenameWorkspaceResult>(
    '/api/tenancy/workspaces/current/name',
    {
      method: 'PATCH',
      body: JSON.stringify({ name }),
      headers: { 'X-Workspace-Id': workspaceId },
    },
    token,
  )
}

// ---------------------------------------------------------------------------
// Workspace endpoints
// ---------------------------------------------------------------------------

export interface WorkspaceSummary {
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

export interface ConfiguredProvider {
  name: string
  configured: boolean
}

export interface ConfiguredProvidersResponse {
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

export interface UpdateWorkspaceIconResult {
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
}) {
  function withWorkspace(init: ApiFetchOptions): RequestInit {
    const { workspaceScoped, ...requestInit } = init

    if (!workspaceScoped) {
      return requestInit
    }

    const workspaceId = opts.getWorkspaceId?.()
    if (!workspaceId) {
      throw {
        title: 'Workspace context required',
        detail: 'Workspace context is required for this request.',
        status: 400,
      } satisfies ApiError
    }

    return {
      ...requestInit,
      headers: {
        ...(requestInit.headers ?? {}),
        'X-Workspace-Id': workspaceId,
      },
    }
  }

  async function apiFetchRaw(path: string, init: ApiFetchOptions = {}): Promise<Response> {
    const token = opts.getToken()
    const requestInit = withWorkspace(init)

    try {
      return await requestRaw(path, requestInit, token)
    } catch (err) {
      const apiError = err as ApiError

      if (apiError.status !== 401) {
        throw err
      }

      const newToken = await opts.onRefresh()

      if (!newToken) {
        opts.onUnauthenticated()
        throw err
      }

      return requestRaw(path, requestInit, newToken)
    }
  }

  const apiFetch = async function apiFetch<T>(
    path: string,
    init: ApiFetchOptions = {},
  ): Promise<T> {
    const token = opts.getToken()
    const requestInit = withWorkspace(init)

    try {
      return await request<T>(path, requestInit, token)
    } catch (err) {
      const apiError = err as ApiError

      if (apiError.status !== 401) {
        throw err
      }

      // Silent refresh attempt
      const newToken = await opts.onRefresh()

      if (!newToken) {
        opts.onUnauthenticated()
        throw err
      }

      // Single retry with fresh token
      return request<T>(path, requestInit, newToken)
    }
  }

  return Object.assign(apiFetch, { raw: apiFetchRaw })
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
    const proxyBase = apiBase || window.location.origin
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

export type { LoginPayload, RegisterPayload }
