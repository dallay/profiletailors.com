export type ApiErrorPayload = {
  title?: string
  detail?: string
  code?: string
  errorCode?: string
  properties?: { code?: string }
}

export class ApiRequestError extends Error {
  readonly status: number
  readonly payload: ApiErrorPayload | null

  constructor(status: number, payload: ApiErrorPayload | null) {
    super(payload?.detail ?? payload?.title ?? 'Request failed')
    this.name = 'ApiRequestError'
    this.status = status
    this.payload = payload
  }

  get code(): string | undefined {
    return this.payload?.code ?? this.payload?.errorCode ?? this.payload?.properties?.code
  }
}

export function resolveApiBaseUrl(): string {
  const value = import.meta.env.VITE_API_BASE_URL
  if (typeof value !== 'string') return 'http://localhost:7638'
  return value.trim().replace(/\/+$/, '')
}

export function resolveApiUrl(path: string): string {
  if (!path.startsWith('/')) return path
  const baseUrl = resolveApiBaseUrl()
  return baseUrl ? `${baseUrl}${path}` : path
}

export async function apiFetch(
  path: string,
  init: RequestInit = {},
  accessToken?: string | null,
): Promise<Response> {
  const headers = new Headers(init.headers)
  const isFormDataBody = typeof FormData !== 'undefined' && init.body instanceof FormData

  if (!headers.has('Accept')) headers.set('Accept', 'application/vnd.api.v1+json')
  if (!headers.has('Content-Type') && !isFormDataBody && init.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)

  return fetch(resolveApiUrl(path), {
    ...init,
    credentials: 'include',
    headers,
  })
}

export async function readApiError(response: Response): Promise<ApiErrorPayload | null> {
  try {
    return (await response.json()) as ApiErrorPayload
  } catch {
    return null
  }
}

export async function ensureApiSuccess(response: Response): Promise<Response> {
  if (!response.ok) throw new ApiRequestError(response.status, await readApiError(response))
  return response
}

export async function readApiJson<T>(response: Response): Promise<T> {
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
