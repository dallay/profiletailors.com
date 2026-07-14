import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, type Ref } from 'vue'
import { useComposerMediaPicker } from './useComposerMediaPicker'
import type { MediaAssetSummary } from '@modules/media/services/media-api'
import type { Channel, Publication } from '@modules/publishing/infrastructure/publishing.store'

// ---------------------------------------------------------------------------
// Store fake helpers
// ---------------------------------------------------------------------------

function createFakeMediaStore(
  overrides: Partial<{
    assetsById: Ref<Record<string, MediaAssetSummary>>
    assetIds: Ref<string[]>
    isLoading: Ref<boolean>
    loadError: Ref<string | null>
    upsertAsset: ReturnType<typeof vi.fn>
    loadAssets: ReturnType<typeof vi.fn>
    loadAsset: ReturnType<typeof vi.fn>
    createAndUpload: ReturnType<typeof vi.fn>
  }> = {},
): {
  assetsById: Ref<Record<string, MediaAssetSummary>>
  assetIds: Ref<string[]>
  isLoading: Ref<boolean>
  loadError: Ref<string | null>
  upsertAsset: ReturnType<typeof vi.fn>
  loadAssets: ReturnType<typeof vi.fn>
  loadAsset: ReturnType<typeof vi.fn>
  createAndUpload: ReturnType<typeof vi.fn>
} {
  return {
    assetsById: ref<Record<string, MediaAssetSummary>>({}),
    assetIds: ref<string[]>([]),
    isLoading: ref(false),
    loadError: ref<string | null>(null),
    upsertAsset: vi.fn(),
    loadAssets: vi.fn().mockResolvedValue(undefined),
    loadAsset: vi.fn().mockResolvedValue({} as MediaAssetSummary),
    createAndUpload: vi.fn().mockResolvedValue({ assetId: 'new-asset' } as MediaAssetSummary),
    ...overrides,
  }
}

function createFakePublishingStore(
  overrides: Partial<{
    channels: Ref<Channel[]>
  }> = {},
): {
  channels: Ref<Channel[]>
} {
  const defaultChannels: Channel[] = [
    {
      id: 'ch-1',
      accountId: 'ch-1',
      name: 'LinkedIn',
      provider: 'linkedin',
      avatar: '',
      handle: 'linkedin',
      status: 'ACTIVE',
      maxAttachments: 9,
    },
  ]
  return {
    channels: ref<Channel[]>(defaultChannels),
    ...overrides,
  }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('useComposerMediaPicker', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  // -------------------------------------------------------------------------
  // Phase 1 — Composable skeleton initial state
  // -------------------------------------------------------------------------

  describe('initial state', () => {
    it('isMediaPickerOpen starts as false', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })
      expect(picker.isMediaPickerOpen.value).toBe(false)
    })

    it('draftAttachmentIds starts as empty array', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })
      expect(picker.draftAttachmentIds.value).toEqual([])
    })

    it('pickerAssets starts as empty array', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })
      expect(picker.pickerAssets.value).toEqual([])
    })

    it('effectiveProvider is null when provider is not unsplash', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })
      expect(picker.effectiveProvider.value).toBe(null)
    })

    it('effectiveProvider is unsplash when provider is unsplash', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref('unsplash'),
        initialChannelId: ref(null),
      })
      expect(picker.effectiveProvider.value).toBe('unsplash')
    })

    it("effectiveAttachmentLimit returns the selected channel's limit", () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore({
        channels: ref([
          {
            id: 'ch-li',
            accountId: 'ch-li',
            name: 'LinkedIn',
            provider: 'linkedin',
            avatar: '',
            handle: 'linkedin',
            status: 'ACTIVE',
            maxAttachments: 9,
          },
          {
            id: 'ch-tw',
            accountId: 'ch-tw',
            name: 'Twitter',
            provider: 'twitter',
            avatar: '',
            handle: 'twitter',
            status: 'ACTIVE',
            maxAttachments: 4,
          },
        ]),
      })
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        // Twitter is the selected channel
        initialChannelId: ref('ch-tw'),
      })
      expect(picker.effectiveAttachmentLimit.value).toBe(4)
    })

    it('effectiveAttachmentLimit returns Infinity when no active channels', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore({
        channels: ref([
          {
            id: 'ch-inactive',
            accountId: 'ch-inactive',
            name: 'Inactive Channel',
            provider: 'linkedin',
            avatar: '',
            handle: 'linkedin',
            status: 'INACTIVE',
            maxAttachments: 9,
          },
        ]),
      })
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })
      expect(picker.effectiveAttachmentLimit.value).toBe(Number.POSITIVE_INFINITY)
    })

    it('isAttachmentLimitExceeded is false when draft is empty', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })
      expect(picker.isAttachmentLimitExceeded.value).toBe(false)
    })

    it('isPickerSelectionOverLimit is false when selection is empty', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })
      expect(picker.isPickerSelectionOverLimit.value).toBe(false)
    })

    it('activeChannels returns only ACTIVE channels', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore({
        channels: ref([
          {
            id: 'ch-active',
            accountId: 'ch-active',
            name: 'Active',
            provider: 'linkedin',
            avatar: '',
            handle: 'active',
            status: 'ACTIVE',
          },
          {
            id: 'ch-inactive',
            accountId: 'ch-inactive',
            name: 'Inactive',
            provider: 'linkedin',
            avatar: '',
            handle: 'inactive',
            status: 'INACTIVE',
          },
        ]),
      })
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })
      expect(picker.activeChannels.value).toHaveLength(1)
      expect(picker.activeChannels.value[0]!.id).toBe('ch-active')
    })

    it('providerQuery starts empty', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })
      expect(picker.providerQuery.value).toBe('')
    })

    it('providerResults starts empty', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })
      expect(picker.providerResults.value).toEqual([])
    })

    it('providerSearching starts false', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })
      expect(picker.providerSearching.value).toBe(false)
    })

    it('providerSearchError starts null', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })
      expect(picker.providerSearchError.value).toBeNull()
    })
  })

  // -------------------------------------------------------------------------
  // Phase 2 — Lifecycle methods
  // -------------------------------------------------------------------------

  describe('openMediaPicker', () => {
    it('loads assets via mediaStore.loadAssets', async () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const loadAssetsSpy = vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue(undefined)

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.openMediaPicker()

      expect(loadAssetsSpy).toHaveBeenCalledWith('READY,PENDING_UPLOAD,UPLOADING,FAILED')
    })

    it('sets isMediaPickerOpen to true', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      expect(picker.isMediaPickerOpen.value).toBe(false)
      picker.openMediaPicker()
      expect(picker.isMediaPickerOpen.value).toBe(true)
    })

    it('seeds pickerSelectionIds from current draftAttachmentIds', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref<Publication | null>(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.draftAttachmentIds.value = ['asset-a', 'asset-b']
      picker.openMediaPicker()
      expect(picker.pickerSelectionIds.value).toEqual(['asset-a', 'asset-b'])
    })
  })

  describe('closeMediaPicker', () => {
    it('sets isMediaPickerOpen to false', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.openMediaPicker()
      expect(picker.isMediaPickerOpen.value).toBe(true)
      picker.closeMediaPicker()
      expect(picker.isMediaPickerOpen.value).toBe(false)
    })

    it('clears pickerSelectionIds', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.draftAttachmentIds.value = ['asset-a']
      picker.openMediaPicker()
      expect(picker.pickerSelectionIds.value).toEqual(['asset-a'])
      picker.closeMediaPicker()
      expect(picker.pickerSelectionIds.value).toEqual([])
    })

    it('preserves draftAttachmentIds after close (cancel discards staged, not draft)', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.draftAttachmentIds.value = ['asset-a']
      picker.openMediaPicker()
      picker.togglePickerAsset('asset-b')
      expect(picker.pickerSelectionIds.value).toEqual(['asset-a', 'asset-b'])
      picker.closeMediaPicker()
      // draft remains unchanged (cancel discards staged changes)
      expect(picker.draftAttachmentIds.value).toEqual(['asset-a'])
    })
  })

  describe('togglePickerAsset', () => {
    it('adds asset to pickerSelectionIds when not selected', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.openMediaPicker()
      picker.togglePickerAsset('asset-a')
      expect(picker.pickerSelectionIds.value).toContain('asset-a')
    })

    it('removes asset from pickerSelectionIds when already selected', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.openMediaPicker()
      picker.togglePickerAsset('asset-a')
      expect(picker.pickerSelectionIds.value).toContain('asset-a')
      picker.togglePickerAsset('asset-a')
      expect(picker.pickerSelectionIds.value).not.toContain('asset-a')
    })

    it('tracks manually deselected auto-staged assets', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.openMediaPicker()
      // Simulate auto-staged asset
      picker.autoStagedAssetIds.value = ['asset-a']
      picker.pickerSelectionIds.value = ['asset-a']
      // User manually deselects it
      picker.togglePickerAsset('asset-a')
      expect(picker.manuallyDeselectedAutoStageIds.value).toContain('asset-a')
      expect(picker.pickerSelectionIds.value).not.toContain('asset-a')
    })
  })

  describe('applyPickerSelection', () => {
    it('writes draftAttachmentIds when selection is within limit', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.openMediaPicker()
      picker.togglePickerAsset('asset-a')
      picker.togglePickerAsset('asset-b')
      picker.applyPickerSelection()

      expect(picker.draftAttachmentIds.value).toEqual(['asset-a', 'asset-b'])
    })

    it('blocks apply when selection exceeds effectiveAttachmentLimit', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore({
        channels: ref([
          {
            id: 'ch-tw',
            accountId: 'ch-tw',
            name: 'Twitter',
            provider: 'twitter',
            avatar: '',
            handle: 'twitter',
            status: 'ACTIVE',
            maxAttachments: 1,
          },
        ]),
      })

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref('ch-tw'),
      })

      picker.openMediaPicker()
      picker.togglePickerAsset('asset-a')
      picker.togglePickerAsset('asset-b') // over limit
      picker.applyPickerSelection()

      // draft should NOT change — apply was blocked
      expect(picker.draftAttachmentIds.value).toEqual([])
      expect(picker.isMediaPickerOpen.value).toBe(true) // picker stays open
    })

    it('closes the picker after successful apply', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.openMediaPicker()
      picker.togglePickerAsset('asset-a')
      picker.applyPickerSelection()

      expect(picker.isMediaPickerOpen.value).toBe(false)
    })
  })

  describe('removeDraftAttachment', () => {
    it('removes the asset from draftAttachmentIds', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.draftAttachmentIds.value = ['asset-a', 'asset-b']
      picker.removeDraftAttachment('asset-a')
      expect(picker.draftAttachmentIds.value).toEqual(['asset-b'])
    })
  })

  // -------------------------------------------------------------------------
  // Phase 3 — Reconciliation polling
  // -------------------------------------------------------------------------

  describe('reconciliation polling', () => {
    it('startAssetReconciliation calls loadAsset and upsertAsset', async () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const loadAssetSpy = vi.spyOn(mediaStore, 'loadAsset').mockResolvedValue({
        assetId: 'asset-recon',
        workspaceId: 'ws-1',
        sourceType: 'UPLOADED',
        mediaType: 'image/png',
        status: 'READY',
        originalFilename: 'recon.png',
        fileSizeBytes: 1024,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: '/api/media/assets/asset-recon/preview',
      })
      const upsertSpy = vi.spyOn(mediaStore, 'upsertAsset')

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.startAssetReconciliation('asset-recon')

      // Advance past the RECONCILIATION_POLL_INTERVAL_MS (1000ms)
      await vi.advanceTimersByTimeAsync(1100)
      expect(loadAssetSpy).toHaveBeenCalledWith('asset-recon')
      expect(upsertSpy).toHaveBeenCalled()
    })

    it('scheduleAssetReconciliation schedules a timer', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      // scheduleAssetReconciliation is internal — use startAssetReconciliation
      picker.startAssetReconciliation('asset-recon')
      expect(picker.reconciliationPollers.value.has('asset-recon')).toBe(true)
    })

    it('stopReconciliationPoller clears the timer', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.startAssetReconciliation('asset-recon')
      expect(picker.reconciliationPollers.value.has('asset-recon')).toBe(true)
      picker.stopReconciliationPoller('asset-recon')
      expect(picker.reconciliationPollers.value.has('asset-recon')).toBe(false)
    })

    it('stopAllReconciliationPollers clears all timers', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.startAssetReconciliation('asset-1')
      picker.startAssetReconciliation('asset-2')
      expect(picker.reconciliationPollers.value.size).toBe(2)
      picker.stopAllReconciliationPollers()
      expect(picker.reconciliationPollers.value.size).toBe(0)
    })

    it('polls up to RECONCILIATION_MAX_ATTEMPTS times', async () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      vi.spyOn(mediaStore, 'loadAsset').mockResolvedValue({
        assetId: 'asset-poll',
        workspaceId: 'ws-1',
        sourceType: 'UPLOADED',
        mediaType: 'image/png',
        status: 'UPLOADING', // stays non-READY
        originalFilename: 'poll.png',
        fileSizeBytes: 1024,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: null,
      })

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.openMediaPicker()
      picker.startAssetReconciliation('asset-poll')

      // Advance past all polling intervals
      await vi.advanceTimersByTimeAsync(5_000 + 100)
      expect(mediaStore.loadAsset).toHaveBeenCalledTimes(5) // MAX_ATTEMPTS = 5
    })
  })

  // -------------------------------------------------------------------------
  // Phase 4 — Provider search/import
  // -------------------------------------------------------------------------

  describe('handleProviderSearch', () => {
    it('sets providerQuery and clears results on empty query', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref('unsplash'),
        initialChannelId: ref(null),
      })

      picker.handleProviderSearch({ query: '' })
      expect(picker.providerQuery.value).toBe('')
      expect(picker.providerResults.value).toEqual([])
    })

    it('synthesizes providerResults in DEV/test mode', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref('unsplash'),
        initialChannelId: ref(null),
      })

      picker.handleProviderSearch({ query: 'mountain' })

      expect(picker.providerQuery.value).toBe('mountain')
      expect(picker.providerResults.value.length).toBeGreaterThan(0)
      expect(picker.providerResults.value[0]?.externalId).toContain('mountain')
    })
  })

  describe('handleProviderImport', () => {
    it('upserts synthetic asset and starts reconciliation in DEV/test', async () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const upsertSpy = vi.spyOn(mediaStore, 'upsertAsset')
      vi.spyOn(mediaStore, 'loadAsset').mockResolvedValue({
        assetId: 'unsplash-mountain-1',
        workspaceId: 'ws-1',
        sourceType: 'EXTERNAL',
        mediaType: 'image/jpeg',
        status: 'READY',
        originalFilename: 'mountain-1.jpg',
        fileSizeBytes: 1024,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: '/api/media/assets/unsplash-mountain-1/preview',
        sourceProvider: 'unsplash',
        externalId: 'mountain-1',
      })

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref('unsplash'),
        initialChannelId: ref(null),
        workspaceId: 'ws-1',
      })

      await picker.handleProviderImport({ externalId: 'mountain-1' })

      expect(upsertSpy).toHaveBeenCalled()
      expect(picker.providerImportResolution.value['mountain-1']).toBe('unsplash-mountain-1')
    })
  })

  // -------------------------------------------------------------------------
  // Phase 5 — Session tracking helpers
  // -------------------------------------------------------------------------

  describe('resetPickerSessionTracking', () => {
    it('clears all session state and stops all pollers', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.scheduleAssetReconciliation('asset-1')
      picker.scheduleAssetReconciliation('asset-2')
      picker.pendingPickerAssets.value = ['asset-1']
      picker.autoStagedAssetIds.value = ['asset-2']
      picker.manuallyDeselectedAutoStageIds.value = ['asset-3']

      picker.resetPickerSessionTracking()

      expect(picker.reconciliationPollers.value.size).toBe(0)
      expect(picker.pendingPickerAssets.value).toEqual([])
      expect(picker.autoStagedAssetIds.value).toEqual([])
      expect(picker.manuallyDeselectedAutoStageIds.value).toEqual([])
    })
  })

  describe('stageAssetOnce', () => {
    it('is a no-op when asset is already in autoStagedAssetIds', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.autoStagedAssetIds.value = ['asset-a']
      picker.stageAssetOnce('asset-a')
      // Should not add duplicate
      expect(picker.autoStagedAssetIds.value.filter((id) => id === 'asset-a').length).toBe(1)
    })

    it('is a no-op when asset is in manuallyDeselectedAutoStageIds', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.manuallyDeselectedAutoStageIds.value = ['asset-a']
      picker.stageAssetOnce('asset-a')
      expect(picker.autoStagedAssetIds.value).not.toContain('asset-a')
    })

    it('adds to autoStagedAssetIds and pickerSelectionIds when not tracked', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.stageAssetOnce('asset-new')
      expect(picker.autoStagedAssetIds.value).toContain('asset-new')
      expect(picker.pickerSelectionIds.value).toContain('asset-new')
    })
  })

  describe('addPendingPickerAsset', () => {
    it('prepends asset to pendingPickerAssets', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.pendingPickerAssets.value = ['existing']
      picker.addPendingPickerAsset('new')
      expect(picker.pendingPickerAssets.value[0]).toBe('new')
      expect(picker.pendingPickerAssets.value).toContain('existing')
    })

    it('is idempotent — does not duplicate', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.pendingPickerAssets.value = ['asset-a']
      picker.addPendingPickerAsset('asset-a')
      expect(picker.pendingPickerAssets.value.filter((id) => id === 'asset-a').length).toBe(1)
    })
  })

  describe('clearPendingPickerAsset', () => {
    it('removes the asset from pendingPickerAssets', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.pendingPickerAssets.value = ['asset-a', 'asset-b']
      picker.clearPendingPickerAsset('asset-a')
      expect(picker.pendingPickerAssets.value).toEqual(['asset-b'])
    })
  })

  // -------------------------------------------------------------------------
  // Constants are exported
  // -------------------------------------------------------------------------

  it('exports RECONCILIATION_POLL_INTERVAL_MS = 1000', async () => {
    const { RECONCILIATION_POLL_INTERVAL_MS } = await import('./useComposerMediaPicker')
    expect(RECONCILIATION_POLL_INTERVAL_MS).toBe(1000)
  })

  it('exports RECONCILIATION_MAX_ATTEMPTS = 5', async () => {
    const { RECONCILIATION_MAX_ATTEMPTS } = await import('./useComposerMediaPicker')
    expect(RECONCILIATION_MAX_ATTEMPTS).toBe(5)
  })
})
