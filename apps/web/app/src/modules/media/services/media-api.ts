// ---------------------------------------------------------------------------
// Types — Media API
// ---------------------------------------------------------------------------

/**
 * CAS media asset status values.
 */
export type MediaStatus = 'PENDING_UPLOAD' | 'UPLOADING' | 'READY' | 'FAILED' | 'DELETED'
export type MediaSourceType = 'UPLOADED' | 'EXTERNAL'
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
export type MediaAssetSummary = {
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
  sourceProvider?: string | null
  externalId?: string | null
  sourceUrl?: string | null
  authorName?: string | null
  authorUrl?: string | null
  metadata?: Record<string, unknown> | null
}

/** Paginated list response from GET /api/media/assets */
export type MediaAssetListResponse = {
  assets: MediaAssetSummary[]
  nextCursor: string | null
}

// ─── CAS PUT response ─────────────────────────────────────────────────────────

/** Response from PUT /api/workspaces/{workspaceId}/media/assets/{assetId} */
export type PutAssetResponse = {
  assetId: string
  workspaceId: string
  status: 'PENDING_UPLOAD' | 'READY'
  mediaType: string
  deduped: boolean
  uploadUrl?: string
  createdAt: string
}

/** Response from POST /api/workspaces/{workspaceId}/media/assets/{assetId}/upload */
export type UploadAssetResponse = {
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
export type DeleteAssetResponse = {
  deleted: boolean
  blobScheduledForGC: boolean
}

// ─── CAS Error shapes ────────────────────────────────────────────────────────

export type MediaApiError = {
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

import type { createApiFetch, ApiFetchOptions } from '@modules/auth/infrastructure/auth-api'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { computeFileHash, sanitizeFilename } from '@/composables/useFileHash'

type MediaApiErrorShape = Error & {
  title: string
  detail: string
  status: number
  errorCode?: string
  retryAfterSeconds?: number
  existingFileHash?: string
}

function mediaApiError(
  title: string,
  detail: string,
  status: number,
  errorCode?: string,
): MediaApiErrorShape {
  return Object.assign(new Error(title), { title, detail, status, errorCode }) as MediaApiErrorShape
}

function withPinnedWorkspace(init: ApiFetchOptions, workspaceId: string): ApiFetchOptions {
  const { workspaceScoped, ...requestInit } = init

  if (!workspaceScoped) {
    return requestInit
  }

  return {
    ...requestInit,
    headers: {
      ...requestInit.headers,
      'X-Workspace-Id': workspaceId,
    },
  }
}

/** Creates an authenticated fetch wrapper scoped to the media API. */
function createMediaFetch(workspaceId?: string): ReturnType<typeof createApiFetch> {
  const auth = useAuthStore()

  if (!workspaceId) {
    return auth.apiFetch
  }

  const apiFetch = (async <T>(path: string, init: ApiFetchOptions = {}) => {
    return auth.apiFetch<T>(path, withPinnedWorkspace(init, workspaceId))
  }) as ReturnType<typeof createApiFetch>

  apiFetch.raw = (path: string, init: ApiFetchOptions = {}) => {
    return auth.apiFetchRaw(path, withPinnedWorkspace(init, workspaceId))
  }

  return apiFetch
}

/** Default polling delay in milliseconds for 202 WAITING_FOR_BLOB responses. */
const DEFAULT_POLL_DELAY_MS = 3_000

/** Builds the request body shared by putAsset and pollUntilReady. */
function buildPutAssetBody(file: File, fileHash: string): Record<string, unknown> {
  return {
    fileHash,
    fileSizeBytes: file.size,
    declaredMediaType: file.type || 'application/octet-stream',
    originalFilename: sanitizeFilename(file.name),
  }
}

/**
 * Polls the PUT endpoint until the blob is READY or the upload fails.
 * Used when the server returns 202 (another upload is in progress).
 */
async function pollUntilReady(
  workspaceId: string,
  assetId: string,
  file: File,
  fileHash: string,
  maxAttempts = 10,
): Promise<PutAssetResponse | UploadAssetResponse> {
  const fetch = createMediaFetch(workspaceId)
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    // Respect Retry-After if server provided it; fall back to 3s default
    const pollResp = await fetch.raw(`/api/media/assets/${assetId}`, {
      method: 'PUT',
      workspaceScoped: true,
      body: JSON.stringify(buildPutAssetBody(file, fileHash)),
    })

    const body = (await pollResp.json().catch(() => ({}))) as unknown

    if (
      (pollResp.status === 200 || pollResp.status === 201) &&
      (body as Record<string, unknown>).status === 'READY'
    ) {
      return body as PutAssetResponse
    }

    if (pollResp.status === 202) {
      // Still waiting — respect Retry-After or use default
      const retryAfter = pollResp.headers.get('Retry-After')
      const parsedDelay = retryAfter ? Number.parseInt(retryAfter, 10) : NaN
      const delayMs =
        Number.isFinite(parsedDelay) && parsedDelay > 0 ? parsedDelay * 1000 : DEFAULT_POLL_DELAY_MS
      await new Promise<void>((resolve) => setTimeout(resolve, delayMs))
      continue
    }

    if (pollResp.status === 409) {
      throw mediaApiError(
        'Asset hash mismatch',
        'Asset already exists with a different file hash.',
        409,
      )
    }

    if (!pollResp.ok) {
      throw makePutAssetError(pollResp, body)
    }
  }

  throw mediaApiError(
    'Upload timeout',
    'The blob upload did not complete in time. Please try again.',
    408,
  )
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
    throw mediaApiError('Not authenticated', 'You must be signed in to upload media.', 401)
  }

  const stableId = assetId ?? globalThis.crypto.randomUUID()
  const fileHash = await computeFileHash(file)

  const fetch = createMediaFetch(workspaceId)
  const putResp = await fetch.raw(`/api/media/assets/${stableId}`, {
    method: 'PUT',
    workspaceScoped: true,
    body: JSON.stringify(buildPutAssetBody(file, fileHash)),
  })

  // Handle 202: another upload in progress — poll
  if (putResp.status === 202) {
    return pollUntilReady(workspaceId, stableId, file, fileHash)
  }

  // Handle 429: rate limited
  if (putResp.status === 429) {
    const body = (await putResp.json()) as MediaApiError
    const err = mediaApiError(
      'Rate limit exceeded',
      body.message ?? 'Hourly creation limit exceeded.',
      429,
      'RATE_LIMIT_EXCEEDED',
    )
    err.retryAfterSeconds = body.retryAfterSeconds
    throw err
  }

  const putBodyRaw = (await putResp.json().catch(() => ({}))) as unknown

  // Handle non-OK responses
  if (!putResp.ok) {
    throw makePutAssetError(putResp, putBodyRaw)
  }

  const putBody = putBodyRaw as PutAssetResponse

  // Dedup hit — no upload needed
  if (putResp.status === 200 || putBody.status === 'READY') {
    return putBody
  }

  // 201: need to upload bytes
  if (putBody.status === 'PENDING_UPLOAD') {
    if (!putBody.uploadUrl) {
      throw mediaApiError(
        'Upload URL missing',
        'Server requested an upload but did not provide an upload URL.',
        putResp.status,
        'UPLOAD_URL_MISSING',
      )
    }
    return await uploadAssetBytes(putBody.uploadUrl, file, workspaceId)
  }

  return putBody
}

function makePutAssetError(putResp: Response, body: unknown): ReturnType<typeof mediaApiError> {
  const err = body as MediaApiError & { code?: string; detail?: string }
  const errCode = err.code ?? err.errorCode

  if (putResp.status === 403 && errCode === 'EMAIL_VERIFICATION_REQUIRED') {
    return mediaApiError(
      'Email verification required',
      err.detail ?? 'Please verify your email before uploading media.',
      403,
      errCode,
    )
  }
  if (putResp.status === 409) {
    const e409 = mediaApiError(
      'Asset hash mismatch',
      err.message ?? 'Asset already exists with a different file hash.',
      409,
      'ASSET_HASH_MISMATCH',
    )
    e409.existingFileHash = err.existingFileHash
    return e409
  }
  if (putResp.status === 400) {
    return mediaApiError(
      'Validation error',
      err.message ?? `Server rejected the request (${putResp.status}).`,
      400,
      err.errorCode ?? 'VALIDATION_ERROR',
    )
  }
  return mediaApiError(
    err.errorCode ?? 'PUT failed',
    err.message ?? `Server returned ${putResp.status}.`,
    putResp.status,
  )
}

async function uploadAssetBytes(
  uploadUrl: string,
  file: File,
  workspaceId: string,
): Promise<UploadAssetResponse> {
  const uploadResp = await createMediaFetch(workspaceId).raw(uploadUrl, {
    method: 'POST',
    workspaceScoped: true,
    body: file,
    headers: { 'Content-Type': 'application/octet-stream' },
  })

  if (!uploadResp.ok) {
    throw await makeUploadError(uploadResp)
  }

  return (await uploadResp.json()) as UploadAssetResponse
}

async function makeUploadError(uploadResp: Response): Promise<ReturnType<typeof mediaApiError>> {
  const body = await uploadResp.json().catch(() => ({}))
  const err = body as MediaApiError & { code?: string; detail?: string }
  const uploadErrCode = err.code ?? err.errorCode

  if (uploadResp.status === 403 && uploadErrCode === 'EMAIL_VERIFICATION_REQUIRED') {
    return mediaApiError(
      'Email verification required',
      err.detail ?? 'Please verify your email before uploading media.',
      403,
      uploadErrCode,
    )
  }
  if (uploadResp.status === 422) {
    return mediaApiError(
      err.errorCode ?? 'Upload verification failed',
      err.message ?? 'Server-side hash or size check failed.',
      422,
      err.errorCode,
    )
  }
  return mediaApiError(
    err.errorCode ?? 'Upload failed',
    err.message ?? `Server returned ${uploadResp.status}.`,
    uploadResp.status,
  )
}

// ---------------------------------------------------------------------------
// Legacy API (kept for backward compatibility)
// ---------------------------------------------------------------------------

export type ReserveAssetPayload = {
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

export type ListAssetsOptions = {
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
