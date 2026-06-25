// ---------------------------------------------------------------------------
// Types — Media API
// ---------------------------------------------------------------------------

export type MediaStatus = 'PROCESSING' | 'READY' | 'FAILED'
export type MediaSourceType = 'UPLOADED'
export type MediaType =
  | 'image/jpeg'
  | 'image/png'
  | 'image/gif'
  | 'image/webp'
  | 'video/mp4'
  | 'application/pdf'
  | 'application/msword'
  | 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  | 'application/vnd.ms-powerpoint'
  | 'application/vnd.openxmlformats-officedocument.presentationml.presentation'

/** Summary shape returned by list and upload-success responses. */
export interface MediaAssetSummary {
  assetId: string
  workspaceId: string
  sourceType: MediaSourceType
  mediaType: string
  status: MediaStatus
  originalFilename: string | null
  fileSizeBytes: number | null
  createdAt: string
  previewUrl?: string | null
  downloadUrl?: string | null
}

/** Paginated list response from GET /api/media/assets */
export interface MediaAssetListResponse {
  assets: MediaAssetSummary[]
  nextCursor: string | null
}

// ---------------------------------------------------------------------------
// Error shapes
// ---------------------------------------------------------------------------

export interface MediaApiError {
  errorCode: string
  message: string
  details?: Record<string, unknown>
}

function isMediaApiError(body: unknown): body is MediaApiError {
  return (
    typeof body === 'object' &&
    body !== null &&
    'errorCode' in body &&
    typeof (body as Record<string, unknown>).errorCode === 'string'
  )
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

import { createApiFetch, refreshSession } from '@/lib/auth-api'
import { useAuthStore } from '@/stores/auth'
import { useWorkspaceStore } from '@/stores/workspace'

function mediaApiError(title: string, detail: string, status: number, errorCode?: string) {
  return Object.assign(new Error(title), { title, detail, status, errorCode })
}

/** Creates an authenticated fetch wrapper scoped to the media API. */
function createMediaFetch() {
  return createApiFetch({
    getToken: () => useAuthStore().accessToken,
    getWorkspaceId: () => useWorkspaceStore().activeWorkspaceId,
    onRefresh: async () => {
      const tokens = await refreshSession()
      if (tokens) return tokens.accessToken
      return null
    },
    onUnauthenticated: () => {
      useAuthStore().$reset()
    },
  })
}

// ---------------------------------------------------------------------------
// Reserve / Create asset
// ---------------------------------------------------------------------------

export interface ReserveAssetPayload {
  mediaType: string
  originalFilename?: string
}

export async function reserveAsset(payload: ReserveAssetPayload): Promise<MediaAssetSummary> {
  const fetch_ = createMediaFetch()
  const auth = useAuthStore()

  if (!auth.isAuthenticated) {
    throw mediaApiError('Not authenticated', 'You must be signed in to upload media.', 401)
  }

  return fetch_<MediaAssetSummary>('/api/media/assets', {
    method: 'POST',
    body: JSON.stringify({
      sourceType: 'UPLOADED',
      mediaType: payload.mediaType,
      originalFilename: payload.originalFilename,
    }),
    workspaceScoped: true,
  })
}

// ---------------------------------------------------------------------------
// Upload binary
// ---------------------------------------------------------------------------

/**
 * Uploads a binary file to a previously reserved asset.
 *
 * @param assetId  The asset id returned by reserveAsset()
 * @param file     The browser File to upload
 * @param onProgress  Optional progress callback (0–100)
 */
// Note: onProgress is accepted for API symmetry but is not yet wired to XHR upload
// progress events. Progress tracking is currently driven by retry state in the caller.
export async function uploadAsset(
  assetId: string,
  file: File,
  _onProgress?: (pct: number) => void,
): Promise<MediaAssetSummary> {
  const auth = useAuthStore()

  if (!auth.isAuthenticated) {
    throw mediaApiError('Not authenticated', 'You must be signed in to upload media.', 401)
  }

  const formData = new FormData()
  formData.append('file', file)

  const response = await auth.apiFetchRaw(`/api/media/assets/${assetId}/upload`, {
    method: 'POST',
    body: formData,
    // Let the browser set Content-Type with the multipart boundary
    headers: {},
    workspaceScoped: true,
  })

  if (!response.ok) {
    let body: unknown = null
    try {
      body = await response.json()
    } catch {
      // non-JSON error body
    }

    const title = isMediaApiError(body) ? body.errorCode : `Upload failed (${response.status})`
    const detail = isMediaApiError(body) ? body.message : `Server returned ${response.status}.`

    throw mediaApiError(
      title,
      detail,
      response.status,
      isMediaApiError(body) ? body.errorCode : undefined,
    )
  }

  return response.json() as Promise<MediaAssetSummary>
}

// ---------------------------------------------------------------------------
// List assets
// ---------------------------------------------------------------------------

export interface ListAssetsOptions {
  /**
   * Filter by status(es). Defaults to READY only.
   * Multiple statuses may be comma-separated.
   * Pass 'READY,PROCESSING,FAILED' to include all.
   */
  status?: string
  pageSize?: number
  cursor?: string | null
}

export async function listAssets(opts: ListAssetsOptions = {}): Promise<MediaAssetListResponse> {
  const auth = useAuthStore()

  if (!auth.isAuthenticated) {
    return { assets: [], nextCursor: null }
  }

  const params = new URLSearchParams()
  if (opts.status) {
    params.set('status', opts.status)
  } else {
    params.set('status', 'READY')
  }
  if (opts.pageSize) params.set('pageSize', String(opts.pageSize))
  if (opts.cursor) params.set('cursor', opts.cursor)

  const qs = params.toString()
  const path = `/api/media/assets${qs ? `?${qs}` : ''}`

  return auth.apiFetch<MediaAssetListResponse>(path, {
    method: 'GET',
    workspaceScoped: true,
  })
}

// ---------------------------------------------------------------------------
// Get single asset
// ---------------------------------------------------------------------------

export async function getAsset(assetId: string): Promise<MediaAssetSummary> {
  const auth = useAuthStore()

  if (!auth.isAuthenticated) {
    throw mediaApiError('Not authenticated', 'You must be signed in.', 401)
  }

  return auth.apiFetch<MediaAssetSummary>(`/api/media/assets/${assetId}`, {
    method: 'GET',
    workspaceScoped: true,
  })
}

export async function deleteAsset(assetId: string): Promise<void> {
  const auth = useAuthStore()

  if (!auth.isAuthenticated) {
    throw mediaApiError('Not authenticated', 'You must be signed in.', 401)
  }

  await auth.apiFetch<unknown>(`/api/media/assets/${encodeURIComponent(assetId)}`, {
    method: 'DELETE',
    workspaceScoped: true,
  })
}
