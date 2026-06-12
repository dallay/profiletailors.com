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
async function request<T>(path: string, init: RequestInit = {}, token?: string | null): Promise<T> {
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
  onRefresh: TokenRefresher
  onUnauthenticated: () => void
}) {
  return async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
    const token = opts.getToken()

    try {
      return await request<T>(path, init, token)
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
      return request<T>(path, init, newToken)
    }
  }
}

export type { LoginPayload, RegisterPayload }
