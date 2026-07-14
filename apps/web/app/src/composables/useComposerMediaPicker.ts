/**
 * useComposerMediaPicker — picker orchestration composable
 *
 * Extracts all media-picker state and methods from CreatePostModal.vue into a
 * reusable, testable composable. Store dependencies (mediaStore, publishingStore)
 * are passed as explicit parameters so the composable can be unit-tested with
 * mocks without mounting the full modal.
 *
 * Reactive inputs (editingPublication, provider) are
 * accepted as MaybeRefOrGetter (Vue 3.3+) and unwrapped with toValue() at the
 * read site to avoid stale-closure bugs.
 *
 * @example
 * ```ts
 * const picker = useComposerMediaPicker({
 *   mediaStore,
 *   publishingStore,
 *   editingPublication: () => props.editingPublication,
 *   provider: () => props.provider,
 *   initialChannelId: () => props.initialChannelId,
 * })
 * onUnmounted(() => picker.stopAllReconciliationPollers())
 * ```
 */
import { toValue, ref, computed, isRef } from 'vue'
import type { MaybeRefOrGetter } from 'vue'
import type { MediaAssetStatus } from '@/stores/media'
import type { Channel, Publication } from '@/stores/publishing'
import type { MediaAssetSummary } from '@modules/media/services/media-api'
import type {
  ComposerMediaPickerAsset,
  ComposerMediaPickerCollectionState,
  ComposerMediaPickerSource,
} from '@/components/composer/composer-media-picker.types'
import { resolveApiUrl } from '@modules/auth/infrastructure/auth-api'

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

export const RECONCILIATION_POLL_INTERVAL_MS = 1000
export const RECONCILIATION_MAX_ATTEMPTS = 5

// ---------------------------------------------------------------------------
// Store param types
// ---------------------------------------------------------------------------

type StoreValue<T> = T | { value: T }

function readStoreValue<T>(value: StoreValue<T>): T {
  if (isRef(value)) return value.value as T
  if (typeof value === 'object' && value !== null && 'value' in value) {
    return (value as { value: T }).value
  }
  return value
}

type ComposerMediaPickerMediaStore = {
  assetsById: StoreValue<Record<string, MediaAssetSummary>>
  assetIds: StoreValue<string[]>
  isLoading: StoreValue<boolean>
  loadError: StoreValue<string | null>
  upsertAsset: (asset: MediaAssetSummary) => void
  loadAssets: (status?: string) => Promise<void>
  loadAsset: (assetId: string) => Promise<MediaAssetSummary>
  createAndUpload: (
    file: File,
    tempKey: string,
    onNetworkError?: (attempt: number) => void,
  ) => Promise<MediaAssetSummary>
}

type ComposerMediaPickerPublishingStore = {
  channels: StoreValue<Channel[]>
}

export type ComposerMediaPickerStoreParams = {
  mediaStore: ComposerMediaPickerMediaStore
  publishingStore: ComposerMediaPickerPublishingStore
  editingPublication: MaybeRefOrGetter<Publication | null | undefined>
  provider: MaybeRefOrGetter<'unsplash' | null>
  /** ID of the currently selected channel (reactive). */
  initialChannelId: MaybeRefOrGetter<string | null>
  /**
   * Optional workspaceId override for testing. When provided, the composable
   * uses this value instead of calling useWorkspaceStore() internally.
   */
  workspaceId?: MaybeRefOrGetter<string>
  /**
   * Optional callback invoked whenever the draft attachments change
   * (applyPickerSelection or removeDraftAttachment). The modal uses this
   * to flip its assetsTouched ref in edit mode.
   */
  onAttachmentsChanged?: () => void
}

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

export function useComposerMediaPicker(params: ComposerMediaPickerStoreParams) {
  // -------------------------------------------------------------------------
  // Store access
  // -------------------------------------------------------------------------
  // toValue handles both Ref (test fakes) and plain arrays (Pinia unwraps
  // refs on store instance access), so we use it for all store property reads.
  const mediaStore = params.mediaStore
  const publishingStore = params.publishingStore

  // -------------------------------------------------------------------------
  // Constants
  // -------------------------------------------------------------------------

  // -------------------------------------------------------------------------
  // Picker state refs
  // -------------------------------------------------------------------------

  /** Whether the media picker overlay is open. */
  const isMediaPickerOpen = ref(false)
  const activeMediaPickerSource = ref<ComposerMediaPickerSource>('library')

  /**
   * Library collection loading state driving the shell's conditional rendering:
   * LOADING | READY | EMPTY | ERROR
   */
  const mediaPickerCollectionState = ref<ComposerMediaPickerCollectionState>('LOADING')

  /**
   * IDs of attachments committed to the publication draft.
   * Written by applyPickerSelection; read by draftAttachmentAssets computed.
   */
  const draftAttachmentIds = ref<string[]>([])

  /**
   * IDs staged in the current picker session (before apply).
   * Seeded from draftAttachmentIds on open; replaced on apply.
   */
  const pickerSelectionIds = ref<string[]>([])

  /**
   * Asset IDs auto-staged by reconciliation polling.
   * Used to track which assets the user manually deselected after auto-staging.
   */
  const autoStagedAssetIds = ref<string[]>([])

  /**
   * IDs of assets the user manually deselected after they were auto-staged.
   * Prevents re-auto-staging on later reconciliation passes.
   */
  const manuallyDeselectedAutoStageIds = ref<string[]>([])

  /**
   * IDs of assets that entered the picker via upload or provider import
   * and are pending reconciliation polling.
   */
  const pendingPickerAssets = ref<string[]>([])

  // -------------------------------------------------------------------------
  // Internal-only state (exposed as refs for test access)
  // -------------------------------------------------------------------------

  /** Set of asset IDs currently tracked by any active reconciliation poller. */
  const pickerSessionActiveAssetIds = new Set<string>()

  /**
   * Map of assetId → timer handle for active reconciliation pollers.
   * Exposed as a ref so tests can inspect the map's size and keys.
   */
  const reconciliationPollers = ref(new Map<string, ReturnType<typeof setTimeout>>())

  // -------------------------------------------------------------------------
  // Provider state
  // -------------------------------------------------------------------------

  const providerQuery = ref('')
  const providerResults = ref<
    Array<{
      externalId: string
      name: string
      previewUrl: string | null
      authorName?: string | null
      selectedForImport?: boolean
    }>
  >([])
  const providerSearching = ref(false)
  const providerSearchError = ref<string | null>(null)
  /** externalId → assetId mapping for already-reconciled provider imports. */
  const providerImportResolution = ref<Record<string, string>>({})

  // -------------------------------------------------------------------------
  // Computed: active channels
  // -------------------------------------------------------------------------

  const activeChannels = computed<Channel[]>(() =>
    readStoreValue(publishingStore.channels).filter((ch) => ch.status === 'ACTIVE'),
  )

  // -------------------------------------------------------------------------
  // Computed: effective provider
  // -------------------------------------------------------------------------

  const effectiveProvider = computed<'unsplash' | null>(() => {
    if (toValue(params.provider) !== 'unsplash') return null
    return 'unsplash'
  })

  // -------------------------------------------------------------------------
  // Computed: attachment limit
  // -------------------------------------------------------------------------

  /**
   * Effective attachment limit based on the selected channel.
   * Falls back to Infinity when no channel is selected or when the
   * selected channel has no explicit limit.
   */
  const effectiveAttachmentLimit = computed<number>(() => {
    if (toValue(params.initialChannelId) === null) return Number.POSITIVE_INFINITY
    const channel = activeChannels.value.find((ch) => ch.id === toValue(params.initialChannelId))
    if (!channel) return Number.POSITIVE_INFINITY
    if (typeof channel.maxAttachments !== 'number' || !Number.isFinite(channel.maxAttachments)) {
      return Number.POSITIVE_INFINITY
    }
    return channel.maxAttachments
  })

  const isAttachmentLimitExceeded = computed<boolean>(() => {
    const limit = effectiveAttachmentLimit.value
    if (!Number.isFinite(limit)) return false
    return draftAttachmentIds.value.length > limit
  })

  const isPickerSelectionOverLimit = computed<boolean>(() => {
    const limit = effectiveAttachmentLimit.value
    if (!Number.isFinite(limit)) return false
    return pickerSelectionIds.value.length > limit
  })

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Normalizes store array access across Pinia (auto-unwrapped) and
   * plain-object test-fake stores.
   * The `mediaStore.assetIds` access establishes a Vue reactive dependency
   * when evaluated inside computed / watch; the ternary is for value coersion.
   */
  function getAssetIds(): string[] {
    const v = mediaStore.assetIds as unknown
    if (Array.isArray(v)) {
      const maybeRefValue = (v as { value?: unknown }).value
      return Array.isArray(maybeRefValue) ? maybeRefValue : v
    }
    return (v as { value: string[] }).value
  }

  function getAssetsById(): Record<string, MediaAssetSummary> {
    return readStoreValue(mediaStore.assetsById)
  }

  function getLibraryCollectionState(assetCount: number): ComposerMediaPickerCollectionState {
    if (readStoreValue(mediaStore.isLoading)) return 'LOADING'
    if (readStoreValue(mediaStore.loadError)) return 'ERROR'
    if (assetCount === 0) return 'EMPTY'
    return 'READY'
  }

  function toPickerAssetStatus(status: MediaAssetStatus): ComposerMediaPickerAsset['status'] {
    if (status === 'READY') return 'READY'
    if (status === 'FAILED') return 'FAILED'
    return 'PROCESSING'
  }

  function mapAssetToPickerAsset(assetId: string): ComposerMediaPickerAsset | null {
    const asset = getAssetsById()[assetId]
    if (!asset) return null

    const status = toPickerAssetStatus(asset.status)

    return {
      assetId: asset.assetId,
      name: asset.originalFilename ?? asset.assetId,
      mediaType: asset.mediaType,
      status,
      previewUrl: asset.previewUrl ? resolveApiUrl(asset.previewUrl) : null,
      selectable: status === 'READY',
      selected: pickerSelectionIds.value.includes(asset.assetId),
      sourceType: asset.sourceType,
    }
  }

  const pickerAssets = computed<ComposerMediaPickerAsset[]>(() => {
    const mergedAssetIds = [...pendingPickerAssets.value, ...getAssetIds()]
    const uniqueAssetIds = [...new Set(mergedAssetIds)]

    return uniqueAssetIds
      .map((assetId) => mapAssetToPickerAsset(assetId))
      .filter((asset): asset is ComposerMediaPickerAsset => asset !== null)
  })

  const draftAttachmentAssets = computed(() =>
    draftAttachmentIds.value
      .map((assetId) => getAssetsById()[assetId])
      .filter((asset) => asset !== undefined),
  )

  function addPendingPickerAsset(assetId: string) {
    if (!pendingPickerAssets.value.includes(assetId)) {
      pendingPickerAssets.value = [assetId, ...pendingPickerAssets.value]
    }
  }

  function clearPendingPickerAsset(assetId: string) {
    pendingPickerAssets.value = pendingPickerAssets.value.filter((id) => id !== assetId)
  }

  function ensurePickerAssetVisible(assetId: string) {
    addPendingPickerAsset(assetId)
  }

  function isAssetSelectableStatus(status: MediaAssetStatus): boolean {
    return toPickerAssetStatus(status) === 'READY'
  }

  function stageAssetOnce(assetId: string) {
    if (autoStagedAssetIds.value.includes(assetId)) return
    if (manuallyDeselectedAutoStageIds.value.includes(assetId)) return
    if (pickerSelectionIds.value.includes(assetId)) return

    pickerSelectionIds.value = [...pickerSelectionIds.value, assetId]
    autoStagedAssetIds.value = [...autoStagedAssetIds.value, assetId]
  }

  // -------------------------------------------------------------------------
  // Reconciliation polling
  // -------------------------------------------------------------------------

  function stopReconciliationPoller(assetId: string) {
    const timerId = reconciliationPollers.value.get(assetId)
    if (timerId) {
      clearTimeout(timerId)
      reconciliationPollers.value.delete(assetId)
    }
  }

  function stopAllReconciliationPollers() {
    for (const assetId of reconciliationPollers.value.keys()) {
      stopReconciliationPoller(assetId)
    }
  }

  function scheduleAssetReconciliation(assetId: string, attempt = 1) {
    if (!pickerSessionActiveAssetIds.has(assetId)) return
    if (reconciliationPollers.value.has(assetId)) return
    if (attempt > RECONCILIATION_MAX_ATTEMPTS) return

    const timerId = setTimeout(async () => {
      reconciliationPollers.value.delete(assetId)
      if (!pickerSessionActiveAssetIds.has(assetId)) return

      try {
        const asset = await mediaStore.loadAsset(assetId)
        mediaStore.upsertAsset(asset)
        ensurePickerAssetVisible(assetId)

        if (isAssetSelectableStatus(asset.status)) {
          stageAssetOnce(assetId)
          clearPendingPickerAsset(assetId)
          return
        }

        if (toPickerAssetStatus(asset.status) === 'FAILED') {
          clearPendingPickerAsset(assetId)
          return
        }
      } catch {
        // transient fetch errors retry within the same bounded policy
      }

      if (!pickerSessionActiveAssetIds.has(assetId)) return
      if (attempt < RECONCILIATION_MAX_ATTEMPTS) {
        scheduleAssetReconciliation(assetId, attempt + 1)
        return
      }

      clearPendingPickerAsset(assetId)
    }, RECONCILIATION_POLL_INTERVAL_MS)

    reconciliationPollers.value.set(assetId, timerId)
  }

  function startAssetReconciliation(assetId: string) {
    stopReconciliationPoller(assetId)
    pickerSessionActiveAssetIds.add(assetId)
    ensurePickerAssetVisible(assetId)

    const existingAsset = getAssetsById()[assetId]
    if (existingAsset && isAssetSelectableStatus(existingAsset.status)) {
      stageAssetOnce(assetId)
      clearPendingPickerAsset(assetId)
      return
    }

    scheduleAssetReconciliation(assetId)
  }

  function resetPickerSessionTracking() {
    stopAllReconciliationPollers()
    pickerSessionActiveAssetIds.clear()
    pendingPickerAssets.value = []
    autoStagedAssetIds.value = []
    manuallyDeselectedAutoStageIds.value = []
  }

  // -------------------------------------------------------------------------
  // Lifecycle methods
  // -------------------------------------------------------------------------

  function setActiveMediaPickerSource(source: ComposerMediaPickerSource) {
    if (source === 'unsplash' && effectiveProvider.value !== 'unsplash') {
      activeMediaPickerSource.value = 'library'
      return
    }
    activeMediaPickerSource.value = source
  }

  async function openMediaPicker(source: ComposerMediaPickerSource = 'library') {
    pickerSelectionIds.value = [...draftAttachmentIds.value]
    isMediaPickerOpen.value = true
    setActiveMediaPickerSource(source)
    mediaPickerCollectionState.value = 'LOADING'
    stopAllReconciliationPollers()
    pickerSessionActiveAssetIds.clear()

    for (const assetId of pendingPickerAssets.value) {
      startAssetReconciliation(assetId)
    }

    try {
      await mediaStore.loadAssets('READY,PENDING_UPLOAD,UPLOADING,FAILED')
    } catch {
      // state derived below from store error
    }
    mediaPickerCollectionState.value = getLibraryCollectionState(getAssetIds().length)
  }

  function closeMediaPicker() {
    isMediaPickerOpen.value = false
    pickerSelectionIds.value = []
    activeMediaPickerSource.value = 'library'
    stopAllReconciliationPollers()
    pickerSessionActiveAssetIds.clear()
  }

  function togglePickerAsset(assetId: string) {
    const index = pickerSelectionIds.value.indexOf(assetId)
    if (index >= 0) {
      pickerSelectionIds.value = pickerSelectionIds.value.filter((id) => id !== assetId)
      if (
        autoStagedAssetIds.value.includes(assetId) &&
        !manuallyDeselectedAutoStageIds.value.includes(assetId)
      ) {
        manuallyDeselectedAutoStageIds.value = [...manuallyDeselectedAutoStageIds.value, assetId]
      }
      return
    }

    pickerSelectionIds.value = [...pickerSelectionIds.value, assetId]
  }

  function applyPickerSelection() {
    // Block apply when the staged selection exceeds the strictest channel limit.
    if (pickerSelectionIds.value.length > effectiveAttachmentLimit.value) {
      return
    }
    draftAttachmentIds.value = [...pickerSelectionIds.value]
    stopAllReconciliationPollers()
    pickerSessionActiveAssetIds.clear()
    params.onAttachmentsChanged?.()
    closeMediaPicker()
  }

  function removeDraftAttachment(assetId: string) {
    draftAttachmentIds.value = draftAttachmentIds.value.filter((id) => id !== assetId)
    params.onAttachmentsChanged?.()
  }

  async function handlePickerUploadSelection(filesList: File[]) {
    const file = filesList.find(
      (candidate) =>
        candidate.type.startsWith('image/') ||
        candidate.type === 'video/mp4' ||
        candidate.type === 'image/webp',
    )
    if (!file) return

    const tempKey = `picker-upload-${Date.now()}`
    try {
      const createdAsset = await mediaStore.createAndUpload(file, tempKey, () => {})
      mediaStore.upsertAsset(createdAsset)
      ensurePickerAssetVisible(createdAsset.assetId)
      mediaPickerCollectionState.value = 'READY'

      if (isAssetSelectableStatus(createdAsset.status)) {
        stageAssetOnce(createdAsset.assetId)
        clearPendingPickerAsset(createdAsset.assetId)
        return
      }

      startAssetReconciliation(createdAsset.assetId)
    } catch {
      mediaPickerCollectionState.value = 'ERROR'
    }
  }

  // -------------------------------------------------------------------------
  // Provider search/import (parent-owned, DEV/test stub)
  // -------------------------------------------------------------------------

  /**
   * Captures provider-search intent. Real search happens in the backend client;
   * the modal exposes only the typed interaction and a result list. The
   * synthetic result path is explicitly guarded: it only runs in DEV/test, and
   * providerSearchError surfaces a clear message in production when no real
   * Unsplash search client is wired.
   */
  function handleProviderSearch(payload: { query: string }) {
    const q = payload.query.trim()
    providerQuery.value = q
    providerSearchError.value = null
    providerSearching.value = false

    if (!q) {
      providerResults.value = []
      return
    }

    if (!import.meta.env.DEV && !import.meta.env.MODE?.startsWith('test')) {
      providerResults.value = []
      providerSearchError.value =
        'Unsplash search is not configured. Wire the backend search client before enabling this provider.'
      return
    }

    providerResults.value = [
      { externalId: `${q}-1`, name: `${q} photo one`, previewUrl: null, authorName: 'Test author' },
      { externalId: `${q}-2`, name: `${q} photo two`, previewUrl: null, authorName: 'Test author' },
    ]
  }

  /** Helper to read the explicitly injected workspaceId. */
  function useMediaStoreWorkspaceId(): string {
    return params.workspaceId !== undefined ? toValue(params.workspaceId) : 'ws-local'
  }

  /**
   * Provider-import orchestration. The picker MUST remain open after emit so
   * the author can continue staged multi-selection. We synthesize a persisted
   * asset ID for the imported external result and route it through the same
   * reconciliation pipeline as uploads.
   *
   * In production, the parent would POST to a backend import endpoint that
   * returns the persisted asset; for now we generate a deterministic UUID
   * that the polling layer resolves through mediaStore.loadAsset(). The
   * synthetic path is guarded: it only runs in DEV/test — production callers
   * must wire a real import client before the flag can ship.
   */
  async function handleProviderImport(payload: { externalId: string }): Promise<void> {
    if (!import.meta.env.DEV && !import.meta.env.MODE?.startsWith('test')) {
      providerSearchError.value =
        'Unsplash import is not configured. Wire the backend import client before enabling this provider.'
      return
    }

    const syntheticAssetId = `unsplash-${payload.externalId}`
    providerImportResolution.value = {
      ...providerImportResolution.value,
      [payload.externalId]: syntheticAssetId,
    }

    // Seed a non-READY persisted asset so the reconciliation pipeline has
    // something to poll; the asset becomes READY after loadAsset resolves.
    mediaStore.upsertAsset({
      assetId: syntheticAssetId,
      workspaceId: useMediaStoreWorkspaceId(),
      sourceType: 'EXTERNAL',
      mediaType: 'image/jpeg',
      status: 'PENDING_UPLOAD',
      originalFilename: `${payload.externalId}.jpg`,
      fileSizeBytes: null,
      createdAt: new Date().toISOString(),
      previewUrl: null,
      sourceProvider: 'unsplash',
      externalId: payload.externalId,
    })

    ensurePickerAssetVisible(syntheticAssetId)
    mediaPickerCollectionState.value = 'READY'
    startAssetReconciliation(syntheticAssetId)
  }

  // -------------------------------------------------------------------------
  // Return
  // -------------------------------------------------------------------------

  return {
    // Refs
    isMediaPickerOpen,
    activeMediaPickerSource,
    mediaPickerCollectionState,
    draftAttachmentIds,
    pickerSelectionIds,
    autoStagedAssetIds,
    manuallyDeselectedAutoStageIds,
    pendingPickerAssets,
    reconciliationPollers,

    // Computeds
    effectiveProvider,
    effectiveAttachmentLimit,
    isAttachmentLimitExceeded,
    isPickerSelectionOverLimit,
    activeChannels,
    pickerAssets,
    draftAttachmentAssets,

    // Provider state
    providerQuery,
    providerResults,
    providerSearching,
    providerSearchError,
    providerImportResolution,

    // Lifecycle methods
    openMediaPicker,
    setActiveMediaPickerSource,
    closeMediaPicker,
    applyPickerSelection,
    togglePickerAsset,
    removeDraftAttachment,
    handlePickerUploadSelection,

    // Reconciliation
    startAssetReconciliation,
    scheduleAssetReconciliation,
    stopReconciliationPoller,
    stopAllReconciliationPollers,
    resetPickerSessionTracking,

    // Provider
    handleProviderSearch,
    handleProviderImport,

    // Helpers
    clearPendingPickerAsset,
    ensurePickerAssetVisible,
    addPendingPickerAsset,
    stageAssetOnce,
    getLibraryCollectionState,
    mapAssetToPickerAsset,
    toPickerAssetStatus,
    getPickerAssetStatus: isAssetSelectableStatus,
    isAssetSelectableStatus,
  }
}
