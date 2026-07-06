/**
 * Typed client for media provider search/import endpoints.
 *
 * The picker shell is data-free; this client is used by parent-owned panels only.
 */

export interface MediaProviderSearchItem {
  externalId: string
  previewUrl: string
  fullUrl: string
  width: number
  height: number
  authorName: string
  authorUrl: string
  sourceUrl: string
}

export interface MediaProviderSearchResponse {
  items: MediaProviderSearchItem[]
  page: { number: number; size: number; total: number }
}

export interface MediaProviderImportResponse {
  assetId: string
  deduped: boolean
}

export interface ProviderApiError extends Error {
  status: number
  code?: string
  errorCode?: string
  detail?: string
}

function makeError(status: number, code: string | undefined, detail: string): ProviderApiError {
  const err = new Error(`Media provider request failed: ${status}`) as ProviderApiError
  err.status = status
  err.code = code
  err.detail = detail
  return err
}

async function buildUrl(path: string): Promise<string> {
  const { resolveApiUrl } = await import('./auth-api')
  return resolveApiUrl(path)
}

export async function searchProviderPhotos(
  providerId: 'unsplash',
  workspaceId: string,
  query: string,
  page: number,
  token: string,
): Promise<MediaProviderSearchResponse> {
  const url = await buildUrl(
    `/api/workspaces/${workspaceId}/media/providers/${providerId}/search?query=${encodeURIComponent(query)}&page=${page}`,
  )
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Workspace-Id': workspaceId,
      Accept: 'application/json',
    },
  })
  if (!response.ok) {
    const body = await safeJson(response)
    throw makeError(response.status, body?.code, body?.title ?? body?.detail ?? response.statusText)
  }
  return response.json() as Promise<MediaProviderSearchResponse>
}

export async function importProviderPhoto(
  providerId: 'unsplash',
  workspaceId: string,
  externalId: string,
  token: string,
): Promise<MediaProviderImportResponse> {
  const url = await buildUrl(`/api/workspaces/${workspaceId}/media/providers/${providerId}/import`)
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Workspace-Id': workspaceId,
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({ externalId }),
  })
  if (!response.ok) {
    const body = await safeJson(response)
    throw makeError(response.status, body?.code, body?.title ?? body?.detail ?? response.statusText)
  }
  return response.json() as Promise<MediaProviderImportResponse>
}

async function safeJson(
  response: Response,
): Promise<{ title?: string; detail?: string; code?: string } | null> {
  try {
    return (await response.json()) as { title?: string; detail?: string; code?: string }
  } catch {
    return null
  }
}
