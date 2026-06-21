import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import {
  type MediaAssetSummary,
  reserveAsset,
  uploadAsset,
  listAssets,
  deleteAsset,
} from '@/lib/media-api'

// ---------------------------------------------------------------------------
// Retry configuration (SPA upload contract)
// ---------------------------------------------------------------------------

const RETRY_INITIAL_DELAY_MS = 2_000 // 2 seconds
const RETRY_MAX_DELAY_MS = 30_000 // 30 seconds
const RETRY_MAX_ATTEMPTS = 3

/**
 * Maps HTTP status to retry policy.
 * - 'retry'       → retry up to 3 times with exponential backoff
 * - 'no-retry'    → surface the error immediately
 * - 'terminal'     → surface the error immediately; caller should treat as fatal
 */
type RetryPolicy = 'retry' | 'no-retry' | 'terminal'

function classifyError(status: number | undefined, _errorCode: string | undefined): RetryPolicy {
  if (status === undefined) {
    // Network timeout / fetch failure
    return 'retry'
  }
  if (status >= 500) return 'retry'
  if (status === 409) return 'no-retry' // CONFLICT / IN_PROGRESS
  if (status === 413) return 'no-retry' // PAYLOAD_TOO_LARGE
  if (status === 429) return 'no-retry' // RATE_LIMITED
  if (status === 400) return 'no-retry' // VALIDATION
  if (status === 404) return 'terminal' // ASSET NOT FOUND
  return 'no-retry'
}

function nextDelay(attempt: number): number {
  const delay = RETRY_INITIAL_DELAY_MS * 2 ** (attempt - 1)
  return Math.min(delay, RETRY_MAX_DELAY_MS)
}

// ---------------------------------------------------------------------------
// Upload item (in-memory state for an in-flight upload)
// ---------------------------------------------------------------------------

export interface UploadItem {
  /** Client-generated temporary key so callers can track by their own ref. */
  tempKey: string
  assetId: string
  file: File
  progress: number // 0–100
  status: 'uploading' | 'done' | 'failed' | 'conflict'
  asset?: MediaAssetSummary
  errorTitle?: string
  errorDetail?: string
}

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------

export const useMediaStore = defineStore('media', () => {
  // ---------------------------------------------------------------------------
  // State
  // ---------------------------------------------------------------------------

  /**
   * All assets known to the store (loaded via listAssets).
   * Keyed by assetId for O(1) lookup.
   */
  const assetsById = ref<Record<string, MediaAssetSummary>>({})

  /** Ordered list of asset IDs (newest-first from the API). */
  const assetIds = ref<string[]>([])

  /** Continuation cursor for the next list page; null when no more pages. */
  const nextCursor = ref<string | null>(null)

  /** Asset IDs selected for use in a publication draft. */
  const selectedAssetIds = ref<string[]>([])

  /** Currently in-flight upload operations. */
  const uploads = ref<Record<string, UploadItem>>({})

  /** Whether the list is currently being fetched. */
  const isLoading = ref(false)

  /** Error from the last list operation (cleared on the next load). */
  const loadError = ref<string | null>(null)

  // ---------------------------------------------------------------------------
  // Computed
  // ---------------------------------------------------------------------------

  const selectedAssets = computed<MediaAssetSummary[]>(() =>
    selectedAssetIds.value
      .map((id) => assetsById.value[id])
      .filter((a): a is MediaAssetSummary => a !== undefined),
  )

  const uploadList = computed<UploadItem[]>(() => Object.values(uploads.value))

  /** Upload items that are still in-progress. */
  const pendingUploads = computed<UploadItem[]>(() =>
    uploadList.value.filter((u) => u.status === 'uploading'),
  )

  /** Upload items that completed successfully. */
  const completedUploads = computed<UploadItem[]>(() =>
    uploadList.value.filter((u) => u.status === 'done'),
  )

  /** Upload items that failed or are in conflict. */
  const failedUploads = computed<UploadItem[]>(() =>
    uploadList.value.filter((u) => u.status === 'failed' || u.status === 'conflict'),
  )

  // ---------------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------------

  function upsertAsset(asset: MediaAssetSummary) {
    assetsById.value[asset.assetId] = asset
    if (!assetIds.value.includes(asset.assetId)) {
      // Prepend to maintain newest-first order in the UI list
      assetIds.value.unshift(asset.assetId)
    }
  }

  function upsertAssets(assets: MediaAssetSummary[]) {
    for (const asset of assets) {
      upsertAsset(asset)
    }
  }

  function removeAssetLocally(assetId: string) {
    delete assetsById.value[assetId]
    assetIds.value = assetIds.value.filter((id) => id !== assetId)
    selectedAssetIds.value = selectedAssetIds.value.filter((id) => id !== assetId)
  }

  function updateUpload(tempKey: string, updates: Partial<UploadItem>) {
    uploads.value[tempKey] = {
      ...uploads.value[tempKey],
      ...updates,
    }
  }

  function markUploadDone(tempKey: string, asset: MediaAssetSummary) {
    updateUpload(tempKey, {
      status: 'done',
      asset,
      progress: 100,
    })
  }

  function terminalUploadState(err: unknown): {
    status: UploadItem['status']
    errorTitle: string
    errorDetail: string
    policy: RetryPolicy
  } {
    const apiErr = err as { status?: number; errorCode?: string; detail?: string }
    const policy = classifyError(apiErr.status, apiErr.errorCode)
    const errorTitle =
      apiErr.status === 409
        ? 'Upload conflict'
        : apiErr.status === 413
          ? 'File too large'
          : (apiErr.errorCode ?? 'Upload failed')
    const errorDetail = apiErr.detail ?? `Server returned ${apiErr.status ?? 'a network error'}.`

    return {
      status: apiErr.status === 409 ? 'conflict' : 'failed',
      errorTitle,
      errorDetail,
      policy,
    }
  }

  async function executeWithRetry<T>(
    fn: () => Promise<T>,
    onNetworkError?: (attempt: number) => void,
  ): Promise<T> {
    let lastError: unknown

    for (let attempt = 1; attempt <= RETRY_MAX_ATTEMPTS; attempt++) {
      try {
        return await fn()
      } catch (err) {
        lastError = err

        const apiErr = err as {
          status?: number
          errorCode?: string
          title?: string
          detail?: string
        }
        const policy = classifyError(apiErr.status, apiErr.errorCode)

        if (policy === 'no-retry' || policy === 'terminal') {
          throw err
        }

        // policy === 'retry'
        if (attempt < RETRY_MAX_ATTEMPTS) {
          const delay = nextDelay(attempt)
          if (apiErr.status === undefined) {
            onNetworkError?.(attempt)
          }
          await new Promise<void>((resolve) => setTimeout(resolve, delay))
        }
      }
    }

    throw lastError
  }

  async function attemptUploadWithRetries(
    tempKey: string,
    assetId: string,
    file: File,
    onProgress?: (pct: number) => void,
  ): Promise<MediaAssetSummary> {
    let lastError: unknown

    for (let attempt = 1; attempt <= RETRY_MAX_ATTEMPTS; attempt++) {
      try {
        const uploaded = await uploadAsset(assetId, file, (pct) => {
          updateUpload(tempKey, { progress: pct })
          onProgress?.(pct)
        })

        upsertAsset(uploaded)
        markUploadDone(tempKey, uploaded)
        return uploaded
      } catch (err) {
        lastError = err
        const terminalState = terminalUploadState(err)

        if (terminalState.policy === 'no-retry' || terminalState.policy === 'terminal') {
          updateUpload(tempKey, {
            status: terminalState.status,
            errorTitle: terminalState.errorTitle,
            errorDetail: terminalState.errorDetail,
          })
          throw err
        }

        if (attempt < RETRY_MAX_ATTEMPTS) {
          await new Promise<void>((resolve) => setTimeout(resolve, nextDelay(attempt)))
          updateUpload(tempKey, { progress: 0 })
          onProgress?.(0)
        }
      }
    }

    const err = lastError as { detail?: string }
    updateUpload(tempKey, {
      status: 'failed',
      errorTitle: 'Upload failed',
      errorDetail: err.detail ?? 'Maximum retry attempts exceeded.',
    })
    throw lastError
  }

  // ---------------------------------------------------------------------------
  // Actions
  // ---------------------------------------------------------------------------

  /**
   * Loads or reloads the first page of assets from the media API.
   * Clears previous list state before fetching.
   *
   * @param status  Comma-separated status values. Defaults to 'READY'.
   */
  async function loadAssets(status = 'READY') {
    isLoading.value = true
    loadError.value = null
    assetIds.value = []
    nextCursor.value = null

    try {
      const result = await executeWithRetry(() => listAssets({ status, pageSize: 50 }))
      upsertAssets(result.assets)
      nextCursor.value = result.nextCursor
    } catch (err) {
      const apiErr = err as { title?: string; detail?: string }
      loadError.value = apiErr.detail ?? apiErr.title ?? 'Failed to load media library.'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  /**
   * Loads the next page of assets. Safe to call when nextCursor is null (no-op).
   */
  async function loadNextPage(status = 'READY') {
    if (!nextCursor.value) return

    try {
      const result = await executeWithRetry(() =>
        listAssets({ status, pageSize: 50, cursor: nextCursor.value }),
      )
      upsertAssets(result.assets)
      nextCursor.value = result.nextCursor
    } catch (err) {
      const apiErr = err as { title?: string; detail?: string }
      loadError.value = apiErr.detail ?? apiErr.title ?? 'Failed to load more media.'
      throw err
    }
  }

  /**
   * Creates a reserved asset and immediately uploads the given file.
   * On success, the asset is added to the store and optionally to selection.
   *
   * @param file       The browser File to upload
   * @param tempKey    Caller-provided temporary key for tracking this upload in the UI
   * @param onProgress Called with progress percentage (0–100) during upload
   * @returns The READY MediaAssetSummary
   */
  async function createAndUpload(
    file: File,
    tempKey: string,
    onProgress?: (pct: number) => void,
  ): Promise<MediaAssetSummary> {
    const reserved = await executeWithRetry(() =>
      reserveAsset({
        mediaType: file.type,
        originalFilename: file.name,
      }),
    )

    uploads.value[tempKey] = {
      tempKey,
      assetId: reserved.assetId,
      file,
      progress: 0,
      status: 'uploading',
    }

    try {
      return await attemptUploadWithRetries(tempKey, reserved.assetId, file, onProgress)
    } catch (err) {
      if (uploads.value[tempKey]?.status === 'uploading') {
        updateUpload(tempKey, {
          status: 'failed',
          errorTitle: 'Upload failed',
          errorDetail: (err as { detail?: string }).detail ?? String(err),
        })
      }
      throw err
    }
  }

  /**
   * Retries an upload for a FAILED or CONFLICT upload item.
   * Only works for items whose status is 'failed' or 'conflict'.
   */
  async function retryUpload(
    tempKey: string,
    onProgress?: (pct: number) => void,
  ): Promise<MediaAssetSummary> {
    const item = uploads.value[tempKey]
    if (!item) throw new Error(`No upload found with key: ${tempKey}`)
    if (item.status === 'uploading') throw new Error(`Upload ${tempKey} is already in progress.`)
    if (item.status === 'done') throw new Error(`Upload ${tempKey} already succeeded.`)

    uploads.value[tempKey] = {
      ...item,
      status: 'uploading',
      progress: 0,
      errorTitle: undefined,
      errorDetail: undefined,
    }

    return attemptUploadWithRetries(tempKey, item.assetId, item.file, onProgress)
  }

  /**
   * Loads PROCESSING and FAILED assets from prior sessions so the UI can
   * surface dangling uploads as recoverable or in-progress.
   */
  async function loadDanglingAssets() {
    await loadAssets('PROCESSING,FAILED')
  }

  /**
   * Adds an asset to the current publication draft selection.
   * Idempotent — duplicate IDs are ignored.
   */
  function addToSelection(assetId: string) {
    if (!selectedAssetIds.value.includes(assetId)) {
      selectedAssetIds.value.push(assetId)
    }
  }

  /**
   * Removes an asset from the current publication draft selection.
   */
  function removeFromSelection(assetId: string) {
    selectedAssetIds.value = selectedAssetIds.value.filter((id) => id !== assetId)
  }

  /**
   * Clears all selected assets.
   */
  function clearSelection() {
    selectedAssetIds.value = []
  }

  /**
   * Deletes a persisted asset from the backend and removes it from local state.
   */
  async function deletePersistedAsset(assetId: string) {
    await deleteAsset(assetId)
    removeAssetLocally(assetId)
  }

  /**
   * Removes a failed upload tracking entry.
   */
  function dismissUpload(tempKey: string) {
    delete uploads.value[tempKey]
  }

  /**
   * Clears all in-memory upload tracking entries.
   */
  function clearUploads() {
    uploads.value = {}
  }

  // ---------------------------------------------------------------------------
  // Public surface
  // ---------------------------------------------------------------------------

  return {
    // State
    assetsById,
    assetIds,
    selectedAssetIds,
    uploads,
    nextCursor,
    isLoading,
    loadError,
    // Computed
    selectedAssets,
    uploadList,
    pendingUploads,
    completedUploads,
    failedUploads,
    // Actions
    loadAssets,
    loadNextPage,
    createAndUpload,
    retryUpload,
    loadDanglingAssets,
    addToSelection,
    removeFromSelection,
    clearSelection,
    deletePersistedAsset,
    dismissUpload,
    clearUploads,
  }
})
