// ---------------------------------------------------------------------------
// Types — Media API
// ---------------------------------------------------------------------------

/**
 * CAS media asset status values.
 */
export type MediaStatus = 'PENDING_UPLOAD' | 'UPLOADING' | 'READY' | 'FAILED' | 'DELETED'
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

// ─── CAS PUT response ─────────────────────────────────────────────────────────

/** Response from PUT /api/workspaces/{workspaceId}/media/assets/{assetId} */
export interface PutAssetResponse {
  assetId: string
  workspaceId: string
  status: 'PENDING_UPLOAD' | 'READY'
  mediaType: string
  deduped: boolean
  uploadUrl?: string
  createdAt: string
}

/** Response from POST /api/workspaces/{workspaceId}/media/assets/{assetId}/upload */
export interface UploadAssetResponse {
  assetId: string
  workspaceId: string
  status: 'READY'
  mediaType: string
  detectedMediaType: string
  deduped: boolean
  fileSizeBytes: number
  createdAt: string
}

/** Response from DELETE /api/workspaces/{workspaceId}/media/assets/{assetId} */
export interface DeleteAssetResponse {
  deleted: boolean
  blobScheduledForGC: boolean
}

// ─── CAS Error shapes ────────────────────────────────────────────────────────

export interface MediaApiError {
  errorCode: string
  message: string
  details?: Record<string, unknown>
  existingFileHash?: string
  retryAfterSeconds?: number
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
import { computeFileHash, sanitizeFilename } from '@/composables/useFileHash'

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

/** Default polling delay in milliseconds for 202 WAITING_FOR_BLOB responses. */
const DEFAULT_POLL_DELAY_MS = 3_000

/**
 * Polls the PUT endpoint until the blob is READY or the upload fails.
 * Used when the server returns 202 (another upload is in progress).
 */
async function pollUntilReady(
  _workspaceId: string,
  assetId: string,
  file: File,
  fileHash: string,
  maxAttempts = 10,
): Promise<PutAssetResponse | UploadAssetResponse> {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    // Respect Retry-After if server provided it; fall back to 3s default
    const pollResp = await createMediaFetch().raw(`/api/media/assets/${assetId}`, {
      method: 'PUT',
      workspaceScoped: true,
      body: JSON.stringify({
        fileHash,
        fileSizeBytes: file.size,
        declaredMediaType: file.type || 'application/octet-stream',
        originalFilename: sanitizeFilename(file.name),
      }),
    })

    const body = await pollResp.json()

    if ((pollResp.status === 200 || pollResp.status === 201) && body.status === 'READY') {
      return body as PutAssetResponse
    }

    if (pollResp.status === 202) {
      // Still waiting — respect Retry-After or use default
      const retryAfter = pollResp.headers.get('Retry-After')
      const delayMs = retryAfter ? parseInt(retryAfter, 10) * 1000 : DEFAULT_POLL_DELAY_MS
      await new Promise<void>((resolve) => setTimeout(resolve, delayMs))
      continue
    }

    if (pollResp.status === 409) {
      throw {
        title: 'Asset hash mismatch',
        detail: 'Asset already exists with a different file hash.',
        status: 409,
      }
    }

    if (!pollResp.ok) {
      const err = body as MediaApiError
      throw {
        title: err.errorCode ?? 'PUT failed',
        detail: err.message ?? `Server returned ${pollResp.status}`,
        status: pollResp.status,
      }
    }
  }

  throw {
    title: 'Upload timeout',
    detail: 'The blob upload did not complete in time. Please try again.',
    status: 408,
  }
}

// ---------------------------------------------------------------------------
// PUT-first CAS upload flow
// ---------------------------------------------------------------------------

/**
 * PUT-first CAS upload flow.
 *
 * 1. Compute file hash client-side (SHA-256).
 * 2. PUT /api/workspaces/{workspaceId}/media/assets/{assetId}
 *    with the hash to check for dedup.
 *    - 201: new asset → POST the file bytes.
 *    - 200: dedup hit → no upload needed.
 *    - 202: blob uploading → poll PUT until READY.
 *    - 409: hash mismatch → error.
 *    - 429: rate limited → retry after Retry-After.
 * 3. POST raw bytes to the uploadUrl.
 * 4. Return the final UploadAssetResponse.
 */
export async function putAsset(
  file: File,
  workspaceId: string,
  /** Pass a stable assetId on retries so the PUT is idempotent. */
  assetId?: string,
): Promise<PutAssetResponse | UploadAssetResponse> {
  const auth = useAuthStore()

  if (!auth.isAuthenticated) {
    throw {
      title: 'Not authenticated',
      detail: 'You must be signed in to upload media.',
      status: 401,
    }
  }

  const stableId = assetId ?? crypto.randomUUID()
  const fileHash = await computeFileHash(file)

  const putResp = await createMediaFetch().raw(`/api/media/assets/${stableId}`, {
    method: 'PUT',
    workspaceScoped: true,
    body: JSON.stringify({
      fileHash,
      fileSizeBytes: file.size,
      declaredMediaType: file.type || 'application/octet-stream',
      originalFilename: sanitizeFilename(file.name),
    }),
  })

  // Handle 202: another upload in progress — poll
  if (putResp.status === 202) {
    return pollUntilReady(workspaceId, stableId, file, fileHash)
  }

  // Handle 429: rate limited
  if (putResp.status === 429) {
    const body = (await putResp.json()) as MediaApiError
    throw {
      title: 'Rate limit exceeded',
      detail: body.message ?? 'Hourly creation limit exceeded.',
      status: 429,
      errorCode: 'RATE_LIMIT_EXCEEDED',
      retryAfterSeconds: body.retryAfterSeconds,
    }
  }

  // Handle non-OK responses
  if (!putResp.ok) {
    const body = await putResp.json().catch(() => ({}))
    const err = body as MediaApiError & { code?: string; detail?: string }

    const errCode = err.code ?? err.errorCode
    if (putResp.status === 403 && errCode === 'EMAIL_VERIFICATION_REQUIRED') {
      throw mediaApiError(
        'Email verification required',
        err.detail ?? 'Please verify your email before uploading media.',
        403,
        errCode,
      )
    }

    if (putResp.status === 409) {
      throw {
        title: 'Asset hash mismatch',
        detail: err.message ?? 'Asset already exists with a different file hash.',
        status: 409,
        errorCode: 'ASSET_HASH_MISMATCH',
        existingFileHash: err.existingFileHash,
      }
    }

    if (putResp.status === 400) {
      throw {
        title: 'Validation error',
        detail: err.message ?? `Server rejected the request (${putResp.status}).`,
        status: 400,
        errorCode: err.errorCode ?? 'VALIDATION_ERROR',
      }
    }

    throw {
      title: err.errorCode ?? 'PUT failed',
      detail: err.message ?? `Server returned ${putResp.status}.`,
      status: putResp.status,
    }
  }

  const putBody = (await putResp.json()) as PutAssetResponse

  // Dedup hit — no upload needed
  if (putResp.status === 200 || putBody.status === 'READY') {
    return putBody
  }

  // 201: need to upload bytes
  if (putBody.status === 'PENDING_UPLOAD' && putBody.uploadUrl) {
    const uploadResp = await createMediaFetch().raw(putBody.uploadUrl, {
      method: 'POST',
      workspaceScoped: true,
      body: file,
      // Override Content-Type to raw bytes
      headers: { 'Content-Type': 'application/octet-stream' },
    })

    if (!uploadResp.ok) {
      const body = await uploadResp.json().catch(() => ({}))
      const err = body as MediaApiError & { code?: string; detail?: string }

      const uploadErrCode = err.code ?? err.errorCode
      if (uploadResp.status === 403 && uploadErrCode === 'EMAIL_VERIFICATION_REQUIRED') {
        throw mediaApiError(
          'Email verification required',
          err.detail ?? 'Please verify your email before uploading media.',
          403,
          uploadErrCode,
        )
      }

      if (uploadResp.status === 422) {
        throw {
          title: err.errorCode ?? 'Upload verification failed',
          detail: err.message ?? 'Server-side hash or size check failed.',
          status: 422,
          errorCode: err.errorCode,
        }
      }

      throw {
        title: err.errorCode ?? 'Upload failed',
        detail: err.message ?? `Server returned ${uploadResp.status}.`,
        status: uploadResp.status,
      }
    }

    return (await uploadResp.json()) as UploadAssetResponse
  }

  return putBody
}

// ---------------------------------------------------------------------------
// Legacy API (kept for backward compatibility)
// ---------------------------------------------------------------------------

export interface ReserveAssetPayload {
  mediaType: string
  originalFilename?: string
}

/** @deprecated Use putAsset() instead for the CAS upload flow. */
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

/**
 * Uploads a binary file to a previously reserved asset.
 * @deprecated Use putAsset() instead.
 */
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
// List / Get / Delete
// ---------------------------------------------------------------------------

export interface ListAssetsOptions {
  /**
   * Filter by status(es). Defaults to READY only.
   * Multiple statuses may be comma-separated.
   * Pass 'READY,PENDING_UPLOAD,UPLOADING,FAILED' to include all.
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
  const path = qs ? `/api/media/assets?${qs}` : '/api/media/assets'

  return auth.apiFetch<MediaAssetListResponse>(path, {
    method: 'GET',
    workspaceScoped: true,
  })
}

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

/**
 * Deletes an asset using the CAS soft-delete endpoint.
 * The underlying blob is scheduled for GC if no other assets reference it.
 */
export async function deleteAsset(assetId: string): Promise<DeleteAssetResponse> {
  const auth = useAuthStore()

  if (!auth.isAuthenticated) {
    throw mediaApiError('Not authenticated', 'You must be signed in.', 401)
  }

  return auth.apiFetch<DeleteAssetResponse>(`/api/media/assets/${encodeURIComponent(assetId)}`, {
    method: 'DELETE',
    workspaceScoped: true,
  })
}
