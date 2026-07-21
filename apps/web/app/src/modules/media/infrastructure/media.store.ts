import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'
import {
  type MediaAssetSummary,
  putAsset,
  listAssets,
  getAsset,
  deleteAsset,
} from '@modules/media/services/media-api'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/**
 * CAS media asset statuses.
 */
export type MediaAssetStatus =
  | 'PENDING_UPLOAD'
  | 'UPLOADING'
  | 'READY'
  | 'FAILED'
  | 'SUSPENDED'
  | 'DELETED'

/**
 * Upload item statuses tracked by the store.
 */
export type UploadItemStatus = 'uploading' | 'done' | 'failed' | 'conflict'

// ---------------------------------------------------------------------------
// Retry configuration
// ---------------------------------------------------------------------------

const RETRY_INITIAL_DELAY_MS = 2_000
const RETRY_MAX_DELAY_MS = 30_000
const RETRY_MAX_ATTEMPTS = 3

type RetryPolicy = 'retry' | 'no-retry' | 'terminal'

function classifyError(status: number | undefined, _errorCode: string | undefined): RetryPolicy {
  if (status === undefined) return 'retry'
  if (status >= 500) return 'retry'
  if (status === 409) return 'no-retry'
  if (status === 413) return 'no-retry'
  if (status === 429) return 'no-retry'
  if (status === 400) return 'no-retry'
  if (status === 404) return 'terminal'
  return 'no-retry'
}

function nextDelay(attempt: number): number {
  const delay = RETRY_INITIAL_DELAY_MS * 2 ** (attempt - 1)
  return Math.min(delay, RETRY_MAX_DELAY_MS)
}

function terminalUploadState(err: unknown): {
  status: UploadItemStatus
  errorTitle: string
  errorDetail: string
  policy: RetryPolicy
} {
  const apiErr = err as { status?: number; errorCode?: string; code?: string; detail?: string }
  const errorCode = apiErr.errorCode ?? apiErr.code
  const policy = classifyError(apiErr.status, errorCode)
  const errorTitle = titleForUploadError(apiErr.status, errorCode)
  const errorDetail = apiErr.detail ?? `Server returned ${apiErr.status ?? 'a network error'}.`
  return {
    status: apiErr.status === 409 ? 'conflict' : 'failed',
    errorTitle,
    errorDetail,
    policy,
  }
}

function titleForUploadError(status: number | undefined, errorCode: string | undefined): string {
  if (status === 403 && errorCode === 'EMAIL_VERIFICATION_REQUIRED') {
    return 'Email verification required'
  }
  if (status === 409) return 'Upload conflict'
  if (status === 413) return 'File too large'
  return errorCode ?? 'Upload failed'
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
      const apiErr = err as { status?: number; errorCode?: string }
      const policy = classifyError(apiErr.status, apiErr.errorCode)
      if (policy === 'no-retry' || policy === 'terminal') throw err
      if (attempt < RETRY_MAX_ATTEMPTS) {
        const delay = nextDelay(attempt)
        if (apiErr.status === undefined) onNetworkError?.(attempt)
        await new Promise<void>((resolve) => setTimeout(resolve, delay))
      }
    }
  }
  throw lastError
}

// ---------------------------------------------------------------------------
// Upload item (in-memory state for an in-flight upload)
// ---------------------------------------------------------------------------

export type UploadItem = {
  tempKey: string
  assetId: string
  file: File
  progress: number // 0–100
  status: UploadItemStatus
  asset?: MediaAssetSummary
  errorTitle?: string
  errorDetail?: string
}

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------

export const useMediaStore = defineStore('media', () => {
  // ─── State ───────────────────────────────────────────────────────────────

  /** All assets known to the store (loaded via listAssets). */
  const assetsById = ref<Record<string, MediaAssetSummary>>({})

  /** Ordered list of asset IDs (newest-first from the API). */
  const assetIds = ref<string[]>([])

  /** Continuation cursor for the next list page. */
  const nextCursor = ref<string | null>(null)

  /** Asset IDs selected for use in a publication draft. */
  const selectedAssetIds = ref<string[]>([])

  /** Currently in-flight upload operations. */
  const uploads = ref<Record<string, UploadItem>>({})

  const isLoading = ref(false)
  const loadError = ref<string | null>(null)
  const deleteError = ref<string | null>(null)

  // ─── Computed ─────────────────────────────────────────────────────────

  const selectedAssets = computed<MediaAssetSummary[]>(() =>
    selectedAssetIds.value
      .map((id) => assetsById.value[id])
      .filter((a): a is MediaAssetSummary => a !== undefined),
  )

  const uploadList = computed<UploadItem[]>(() => Object.values(uploads.value))
  const pendingUploads = computed<UploadItem[]>(() =>
    uploadList.value.filter((u) => u.status === 'uploading'),
  )
  const completedUploads = computed<UploadItem[]>(() =>
    uploadList.value.filter((u) => u.status === 'done'),
  )
  const failedUploads = computed<UploadItem[]>(() =>
    uploadList.value.filter((u) => u.status === 'failed' || u.status === 'conflict'),
  )

  // ─── Helpers ───────────────────────────────────────────────────────────

  function upsertAsset(asset: MediaAssetSummary): void {
    assetsById.value[asset.assetId] = asset
    if (!assetIds.value.includes(asset.assetId)) {
      assetIds.value.unshift(asset.assetId)
    }
  }

  function upsertAssets(assets: MediaAssetSummary[]): void {
    const newIds: string[] = []
    for (const asset of assets) {
      assetsById.value[asset.assetId] = asset
      if (!assetIds.value.includes(asset.assetId)) {
        newIds.push(asset.assetId)
      }
    }
    assetIds.value = [...assetIds.value, ...newIds]
  }

  function removeAssetLocally(assetId: string): void {
    delete assetsById.value[assetId]
    assetIds.value = assetIds.value.filter((id) => id !== assetId)
    selectedAssetIds.value = selectedAssetIds.value.filter((id) => id !== assetId)
  }

  function updateUpload(tempKey: string, updates: Partial<UploadItem>): void {
    const current = uploads.value[tempKey]
    if (!current) return
    uploads.value[tempKey] = { ...current, ...updates }
  }

  function markUploadDone(tempKey: string, asset: MediaAssetSummary): void {
    updateUpload(tempKey, { status: 'done', asset, progress: 100 })
  }

  // ─── Actions ───────────────────────────────────────────────────────────

  async function loadAsset(assetId: string): Promise<MediaAssetSummary> {
    const asset = await getAsset(assetId)
    upsertAsset(asset)
    return asset
  }

  const refreshAsset = loadAsset

  async function loadAssets(status = 'READY'): Promise<void> {
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

  async function loadNextPage(status = 'READY'): Promise<void> {
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
   * CAS upload flow: PUT first, then POST bytes if needed.
   *
   * The putAsset() function handles:
   * - Dedup detection (200 OK with READY = no upload needed)
   * - 202 polling (blob being uploaded by another request)
   * - 201 → POST raw bytes
   * - Error classification
   */
  async function createAndUpload(
    file: File,
    tempKey: string,
    _onProgress?: (pct: number) => void,
  ): Promise<MediaAssetSummary> {
    const workspaceId = useWorkspaceStore().activeWorkspaceId
    if (!workspaceId) {
      throw new Error('An active workspace is required to upload media.')
    }

    // Generate assetId ONCE so retries are idempotent
    const stableAssetId = crypto.randomUUID()

    uploads.value[tempKey] = {
      tempKey,
      assetId: stableAssetId,
      file,
      progress: 0,
      status: 'uploading',
    }

    try {
      const result = await executeWithRetry(() => putAsset(file, workspaceId, stableAssetId))

      // Update with assetId after PUT
      uploads.value[tempKey] = {
        ...uploads.value[tempKey],
        assetId: result.assetId,
      }

      if (result.status === 'READY' || ('deduped' in result && result.deduped)) {
        // Dedup hit — no upload needed, or upload completed
        const asset = await executeWithRetry(() => getAsset(result.assetId))
        upsertAsset(asset)
        markUploadDone(tempKey, asset)
        return asset
      }

      // CAS flow always returns a ready asset after upload
      const finalAsset = await executeWithRetry(() => getAsset(result.assetId))
      upsertAsset(finalAsset)
      markUploadDone(tempKey, finalAsset)
      return finalAsset
    } catch (err) {
      const state = terminalUploadState(err)
      updateUpload(tempKey, {
        status: state.status,
        errorTitle: state.errorTitle,
        errorDetail: state.errorDetail,
      })
      throw err
    }
  }

  /**
   * Retry a failed or conflict upload item.
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

    return createAndUpload(item.file, tempKey, onProgress)
  }

  /** Load dangling assets from prior sessions. */
  async function loadDanglingAssets(): Promise<void> {
    await loadAssets('PENDING_UPLOAD,UPLOADING,FAILED')
  }

  function addToSelection(assetId: string): void {
    if (!selectedAssetIds.value.includes(assetId)) {
      selectedAssetIds.value.push(assetId)
    }
  }

  function removeFromSelection(assetId: string): void {
    selectedAssetIds.value = selectedAssetIds.value.filter((id) => id !== assetId)
  }

  function clearSelection(): void {
    selectedAssetIds.value = []
  }

  /** Delete and remove from store. Uses CAS DELETE endpoint. */
  async function deletePersistedAsset(assetId: string): Promise<void> {
    deleteError.value = null
    try {
      await deleteAsset(assetId)
      removeAssetLocally(assetId)
    } catch (err) {
      const apiErr = err as { title?: string; detail?: string }
      deleteError.value = apiErr.detail ?? apiErr.title ?? 'Failed to delete asset.'
      throw err
    }
  }

  function dismissUpload(tempKey: string): void {
    delete uploads.value[tempKey]
  }

  function clearUploads(): void {
    uploads.value = {}
  }

  // ─── Public surface ─────────────────────────────────────────────────

  return {
    // State
    assetsById,
    assetIds,
    selectedAssetIds,
    uploads,
    nextCursor,
    isLoading,
    loadError,
    deleteError,
    // Computed
    selectedAssets,
    uploadList,
    pendingUploads,
    completedUploads,
    failedUploads,
    // Actions
    upsertAsset,
    loadAsset,
    refreshAsset,
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
