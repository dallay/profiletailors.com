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
    createAndUpload: vi
      .fn()
      .mockResolvedValue({ assetId: '83b32657-fb8d-55f2-bebe-030cd4f0694f' } as MediaAssetSummary),
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
      id: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
      accountId: '92c0751b-b11e-5b27-b9bd-8c4b03c253ef',
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

function makeMediaAsset(overrides: Partial<MediaAssetSummary> = {}): MediaAssetSummary {
  return {
    assetId: '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
    workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
    sourceType: 'UPLOADED',
    mediaType: 'image/png',
    status: 'READY',
    originalFilename: 'asset.png',
    fileSizeBytes: 1024,
    createdAt: '2026-06-19T12:00:00Z',
    previewUrl: null,
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

  describe('library search', () => {
    it('matches loaded assets by filename, asset ID, and media type without case sensitivity', () => {
      const assets = [
        makeMediaAsset({
          assetId: '2da13f9f-d9f6-5812-9932-947261d9695a',
          originalFilename: 'Spring-Hero.PNG',
        }),
        makeMediaAsset({
          assetId: 'a88123c3-df0b-58de-aaef-ba815d623ae4',
          originalFilename: 'notes.txt',
          mediaType: 'text/plain',
        }),
        makeMediaAsset({
          assetId: '6cff963c-c793-56ea-a7f7-aa0d7e7b9d31',
          originalFilename: 'statement',
          mediaType: 'application/pdf',
        }),
      ]
      const mediaStore = createFakeMediaStore({
        assetsById: ref(Object.fromEntries(assets.map((asset) => [asset.assetId, asset]))),
        assetIds: ref(assets.map((asset) => asset.assetId)),
      })
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore: createFakePublishingStore(),
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      expect(picker.filteredPickerAssets.value).toHaveLength(3)

      picker.handleLibrarySearch({ query: '  HERO  ' })
      expect(picker.filteredPickerAssets.value.map((asset) => asset.assetId)).toEqual([
        '2da13f9f-d9f6-5812-9932-947261d9695a',
      ])

      picker.handleLibrarySearch({ query: 'A88123C3-DF0B-58DE-AAEF-BA815D623AE4' })
      expect(picker.filteredPickerAssets.value.map((asset) => asset.assetId)).toEqual([
        'a88123c3-df0b-58de-aaef-ba815d623ae4',
      ])

      picker.handleLibrarySearch({ query: 'APPLICATION/PDF' })
      expect(picker.filteredPickerAssets.value.map((asset) => asset.assetId)).toEqual([
        '6cff963c-c793-56ea-a7f7-aa0d7e7b9d31',
      ])

      picker.handleLibrarySearch({ query: '   ' })
      expect(picker.filteredPickerAssets.value.map((asset) => asset.assetId)).toEqual(
        assets.map((asset) => asset.assetId),
      )
    })

    it('preserves staged, pending, and auto-staged IDs while filtering and applying', () => {
      const assets = [
        makeMediaAsset({
          assetId: '3d16f71a-564f-5dea-bc04-9d412a3bf253',
          originalFilename: 'hero.png',
        }),
        makeMediaAsset({
          assetId: 'a328e5b5-2b06-5e9a-86f2-d46fa7ca7fff',
          originalFilename: 'portrait.png',
        }),
        makeMediaAsset({
          assetId: 'f96ab610-da70-5026-90e6-b870c3f63b48',
          originalFilename: 'pending.png',
        }),
      ]
      const mediaStore = createFakeMediaStore({
        assetsById: ref(Object.fromEntries(assets.map((asset) => [asset.assetId, asset]))),
        assetIds: ref(assets.map((asset) => asset.assetId)),
      })
      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore: createFakePublishingStore(),
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })
      picker.draftAttachmentIds.value = [
        '3d16f71a-564f-5dea-bc04-9d412a3bf253',
        'a328e5b5-2b06-5e9a-86f2-d46fa7ca7fff',
      ]
      picker.openMediaPicker()
      picker.ensurePickerAssetVisible('f96ab610-da70-5026-90e6-b870c3f63b48')
      picker.autoStagedAssetIds.value = ['a328e5b5-2b06-5e9a-86f2-d46fa7ca7fff']

      picker.handleLibrarySearch({ query: 'hero' })

      expect(picker.filteredPickerAssets.value.map((asset) => asset.assetId)).toEqual([
        '3d16f71a-564f-5dea-bc04-9d412a3bf253',
      ])
      expect(picker.pickerSelectionIds.value).toEqual([
        '3d16f71a-564f-5dea-bc04-9d412a3bf253',
        'a328e5b5-2b06-5e9a-86f2-d46fa7ca7fff',
      ])
      expect(picker.pendingPickerAssets.value).toEqual(['f96ab610-da70-5026-90e6-b870c3f63b48'])
      expect(picker.autoStagedAssetIds.value).toEqual(['a328e5b5-2b06-5e9a-86f2-d46fa7ca7fff'])

      picker.applyPickerSelection()

      expect(picker.draftAttachmentIds.value).toEqual([
        '3d16f71a-564f-5dea-bc04-9d412a3bf253',
        'a328e5b5-2b06-5e9a-86f2-d46fa7ca7fff',
      ])
      expect(picker.libraryQuery.value).toBe('')
      expect(picker.filteredPickerAssets.value).toHaveLength(3)
    })
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
      expect(picker.effectiveProvider.value).toBeNull()
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
            id: 'b431194a-5205-577d-b212-98398966a944',
            accountId: 'b431194a-5205-577d-b212-98398966a944',
            name: 'LinkedIn',
            provider: 'linkedin',
            avatar: '',
            handle: 'linkedin',
            status: 'ACTIVE',
            maxAttachments: 9,
          },
          {
            id: 'c86487f2-0190-5d05-911c-d60a612366e0',
            accountId: 'c86487f2-0190-5d05-911c-d60a612366e0',
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
        initialChannelId: ref('c86487f2-0190-5d05-911c-d60a612366e0'),
      })
      expect(picker.effectiveAttachmentLimit.value).toBe(4)
    })

    it('effectiveAttachmentLimit returns Infinity when no active channels', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore({
        channels: ref([
          {
            id: 'ac7e79c4-b055-5476-9ab3-edd5250e24ca',
            accountId: 'ac7e79c4-b055-5476-9ab3-edd5250e24ca',
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
            id: 'de3f34c5-50c6-5d59-92d5-5cb765fec322',
            accountId: 'de3f34c5-50c6-5d59-92d5-5cb765fec322',
            name: 'Active',
            provider: 'linkedin',
            avatar: '',
            handle: 'active',
            status: 'ACTIVE',
          },
          {
            id: 'ac7e79c4-b055-5476-9ab3-edd5250e24ca',
            accountId: 'ac7e79c4-b055-5476-9ab3-edd5250e24ca',
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
      expect(picker.activeChannels.value[0]!.id).toBe('de3f34c5-50c6-5d59-92d5-5cb765fec322')
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

      picker.draftAttachmentIds.value = [
        'f464d618-0f38-5b38-adda-9cf4a0b674eb',
        '13001fc9-a24f-54d3-bde1-cfa79074593c',
      ]
      picker.openMediaPicker()
      expect(picker.pickerSelectionIds.value).toEqual([
        'f464d618-0f38-5b38-adda-9cf4a0b674eb',
        '13001fc9-a24f-54d3-bde1-cfa79074593c',
      ])
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

      picker.draftAttachmentIds.value = ['f464d618-0f38-5b38-adda-9cf4a0b674eb']
      picker.openMediaPicker()
      expect(picker.pickerSelectionIds.value).toEqual(['f464d618-0f38-5b38-adda-9cf4a0b674eb'])
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

      picker.draftAttachmentIds.value = ['f464d618-0f38-5b38-adda-9cf4a0b674eb']
      picker.openMediaPicker()
      picker.togglePickerAsset('13001fc9-a24f-54d3-bde1-cfa79074593c')
      expect(picker.pickerSelectionIds.value).toEqual([
        'f464d618-0f38-5b38-adda-9cf4a0b674eb',
        '13001fc9-a24f-54d3-bde1-cfa79074593c',
      ])
      picker.closeMediaPicker()
      // draft remains unchanged (cancel discards staged changes)
      expect(picker.draftAttachmentIds.value).toEqual(['f464d618-0f38-5b38-adda-9cf4a0b674eb'])
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
      picker.togglePickerAsset('f464d618-0f38-5b38-adda-9cf4a0b674eb')
      expect(picker.pickerSelectionIds.value).toContain('f464d618-0f38-5b38-adda-9cf4a0b674eb')
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
      picker.togglePickerAsset('f464d618-0f38-5b38-adda-9cf4a0b674eb')
      expect(picker.pickerSelectionIds.value).toContain('f464d618-0f38-5b38-adda-9cf4a0b674eb')
      picker.togglePickerAsset('f464d618-0f38-5b38-adda-9cf4a0b674eb')
      expect(picker.pickerSelectionIds.value).not.toContain('f464d618-0f38-5b38-adda-9cf4a0b674eb')
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
      picker.autoStagedAssetIds.value = ['f464d618-0f38-5b38-adda-9cf4a0b674eb']
      picker.pickerSelectionIds.value = ['f464d618-0f38-5b38-adda-9cf4a0b674eb']
      // User manually deselects it
      picker.togglePickerAsset('f464d618-0f38-5b38-adda-9cf4a0b674eb')
      expect(picker.manuallyDeselectedAutoStageIds.value).toContain(
        'f464d618-0f38-5b38-adda-9cf4a0b674eb',
      )
      expect(picker.pickerSelectionIds.value).not.toContain('f464d618-0f38-5b38-adda-9cf4a0b674eb')
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
      picker.togglePickerAsset('f464d618-0f38-5b38-adda-9cf4a0b674eb')
      picker.togglePickerAsset('13001fc9-a24f-54d3-bde1-cfa79074593c')
      picker.applyPickerSelection()

      expect(picker.draftAttachmentIds.value).toEqual([
        'f464d618-0f38-5b38-adda-9cf4a0b674eb',
        '13001fc9-a24f-54d3-bde1-cfa79074593c',
      ])
    })

    it('blocks apply when selection exceeds effectiveAttachmentLimit', () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore({
        channels: ref([
          {
            id: 'c86487f2-0190-5d05-911c-d60a612366e0',
            accountId: 'c86487f2-0190-5d05-911c-d60a612366e0',
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
        initialChannelId: ref('c86487f2-0190-5d05-911c-d60a612366e0'),
      })

      picker.openMediaPicker()
      picker.togglePickerAsset('f464d618-0f38-5b38-adda-9cf4a0b674eb')
      picker.togglePickerAsset('13001fc9-a24f-54d3-bde1-cfa79074593c') // over limit
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
      picker.togglePickerAsset('f464d618-0f38-5b38-adda-9cf4a0b674eb')
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

      picker.draftAttachmentIds.value = [
        'f464d618-0f38-5b38-adda-9cf4a0b674eb',
        '13001fc9-a24f-54d3-bde1-cfa79074593c',
      ]
      picker.removeDraftAttachment('f464d618-0f38-5b38-adda-9cf4a0b674eb')
      expect(picker.draftAttachmentIds.value).toEqual(['13001fc9-a24f-54d3-bde1-cfa79074593c'])
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
        assetId: 'e0851379-4b7a-55f3-9708-fa1fdde1c46c',
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        sourceType: 'UPLOADED',
        mediaType: 'image/png',
        status: 'READY',
        originalFilename: 'recon.png',
        fileSizeBytes: 1024,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: '/api/media/assets/e0851379-4b7a-55f3-9708-fa1fdde1c46c/preview',
      })
      const upsertSpy = vi.spyOn(mediaStore, 'upsertAsset')

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref(null),
        initialChannelId: ref(null),
      })

      picker.startAssetReconciliation('e0851379-4b7a-55f3-9708-fa1fdde1c46c')

      // Advance past the RECONCILIATION_POLL_INTERVAL_MS (1000ms)
      await vi.advanceTimersByTimeAsync(1100)
      expect(loadAssetSpy).toHaveBeenCalledWith('e0851379-4b7a-55f3-9708-fa1fdde1c46c')
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
      picker.startAssetReconciliation('e0851379-4b7a-55f3-9708-fa1fdde1c46c')
      expect(picker.reconciliationPollers.value.has('e0851379-4b7a-55f3-9708-fa1fdde1c46c')).toBe(
        true,
      )
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

      picker.startAssetReconciliation('e0851379-4b7a-55f3-9708-fa1fdde1c46c')
      expect(picker.reconciliationPollers.value.has('e0851379-4b7a-55f3-9708-fa1fdde1c46c')).toBe(
        true,
      )
      picker.stopReconciliationPoller('e0851379-4b7a-55f3-9708-fa1fdde1c46c')
      expect(picker.reconciliationPollers.value.has('e0851379-4b7a-55f3-9708-fa1fdde1c46c')).toBe(
        false,
      )
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

      picker.startAssetReconciliation('8bdec504-2ed1-59e4-8a4a-c068789d03d2')
      picker.startAssetReconciliation('b909d06f-92ea-5671-9f13-73475a89080e')
      expect(picker.reconciliationPollers.value.size).toBe(2)
      picker.stopAllReconciliationPollers()
      expect(picker.reconciliationPollers.value.size).toBe(0)
    })

    it('polls up to RECONCILIATION_MAX_ATTEMPTS times', async () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      vi.spyOn(mediaStore, 'loadAsset').mockResolvedValue({
        assetId: '57620eeb-c0e5-5a74-ac42-95dbeae9426a',
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
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
      picker.startAssetReconciliation('57620eeb-c0e5-5a74-ac42-95dbeae9426a')

      // Advance past all polling intervals
      await vi.advanceTimersByTimeAsync(5_000 + 100)
      expect(mediaStore.loadAsset).toHaveBeenCalledTimes(5) // MAX_ATTEMPTS = 5
    })
  })

  // -------------------------------------------------------------------------
  // Phase 4 — Provider search/import
  // -------------------------------------------------------------------------

  describe('handleProviderSearch', () => {
    it('loads editorial example photos when the query is empty', async () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const searchUnsplash = vi.fn().mockResolvedValue([
        {
          externalId: 'editorial-1',
          name: 'Editorial photo',
          previewUrl: 'https://images.unsplash.com/editorial-1',
          sourceUrl: 'https://unsplash.com/photos/editorial-1',
          authorName: 'Author',
          authorUrl: 'https://unsplash.com/@author',
        },
      ])

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref('unsplash'),
        initialChannelId: ref(null),
        searchUnsplash,
      })

      await picker.handleProviderSearch({ query: '' })
      expect(picker.providerQuery.value).toBe('')
      expect(searchUnsplash).toHaveBeenCalledWith(undefined)
      expect(picker.providerResults.value[0]?.externalId).toBe('editorial-1')
    })

    it('searches with the normalized provider query', async () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const searchUnsplash = vi.fn().mockResolvedValue([
        {
          externalId: 'mountain-1',
          name: 'Mountain',
          previewUrl: 'https://images.unsplash.com/mountain-1',
          sourceUrl: 'https://unsplash.com/photos/mountain-1',
          authorName: 'Author',
          authorUrl: 'https://unsplash.com/@author',
        },
      ])

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref('unsplash'),
        initialChannelId: ref(null),
        searchUnsplash,
      })

      await picker.handleProviderSearch({ query: '  mountain  ' })

      expect(picker.providerQuery.value).toBe('mountain')
      expect(searchUnsplash).toHaveBeenCalledWith('mountain')
      expect(picker.providerResults.value[0]?.externalId).toBe('mountain-1')
    })

    it('surfaces provider failures and clears the loading state', async () => {
      const picker = useComposerMediaPicker({
        mediaStore: createFakeMediaStore(),
        publishingStore: createFakePublishingStore(),
        editingPublication: ref(null),
        provider: ref('unsplash'),
        initialChannelId: ref(null),
        searchUnsplash: vi.fn().mockRejectedValue(new Error('Rate limited')),
      })

      await picker.handleProviderSearch({ query: 'mountain' })

      expect(picker.providerSearching.value).toBe(false)
      expect(picker.providerResults.value).toEqual([])
      expect(picker.providerSearchError.value).toBe('Rate limited')
    })

    it('ignores stale provider search results from earlier requests', async () => {
      let resolveFirst: (
        photos: Awaited<
          ReturnType<NonNullable<Parameters<typeof useComposerMediaPicker>[0]['searchUnsplash']>>
        >,
      ) => void
      const searchUnsplash = vi
        .fn()
        .mockImplementationOnce(
          () =>
            new Promise((resolve) => {
              resolveFirst = resolve
            }),
        )
        .mockResolvedValueOnce([
          {
            externalId: 'newer-result',
            name: 'Newer result',
            previewUrl: 'https://images.unsplash.com/newer-result',
            sourceUrl: 'https://unsplash.com/photos/newer-result',
            authorName: 'Author',
            authorUrl: 'https://unsplash.com/@author',
          },
        ])
      const picker = useComposerMediaPicker({
        mediaStore: createFakeMediaStore(),
        publishingStore: createFakePublishingStore(),
        editingPublication: ref(null),
        provider: ref('unsplash'),
        initialChannelId: ref(null),
        searchUnsplash,
      })

      const firstSearch = picker.handleProviderSearch({ query: 'old' })
      await picker.handleProviderSearch({ query: 'new' })
      resolveFirst!([
        {
          externalId: 'older-result',
          name: 'Older result',
          previewUrl: 'https://images.unsplash.com/older-result',
          sourceUrl: 'https://unsplash.com/photos/older-result',
          authorName: 'Author',
          authorUrl: 'https://unsplash.com/@author',
        },
      ])
      await firstSearch

      expect(picker.providerResults.value.map((result) => result.externalId)).toEqual([
        'newer-result',
      ])
      expect(picker.providerSearching.value).toBe(false)
    })
  })

  describe('handleProviderImport', () => {
    it('imports a READY asset, stages it, and prevents duplicate imports', async () => {
      const mediaStore = createFakeMediaStore()
      const publishingStore = createFakePublishingStore()
      const upsertSpy = vi.spyOn(mediaStore, 'upsertAsset')
      const importedAsset: MediaAssetSummary = {
        assetId: '1a378d75-3e06-5a66-9f68-ec6f418e3068',
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        sourceType: 'EXTERNAL',
        mediaType: 'image/jpeg',
        status: 'READY',
        originalFilename: 'mountain-1.jpg',
        fileSizeBytes: 1024,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: '/api/media/assets/1a378d75-3e06-5a66-9f68-ec6f418e3068/preview',
        sourceProvider: 'unsplash',
        externalId: 'mountain-1',
      }
      const importUnsplash = vi.fn().mockResolvedValue(importedAsset)

      const picker = useComposerMediaPicker({
        mediaStore,
        publishingStore,
        editingPublication: ref(null),
        provider: ref('unsplash'),
        initialChannelId: ref(null),
        importUnsplash,
      })

      await picker.handleProviderImport({ externalId: 'mountain-1' })
      await picker.handleProviderImport({ externalId: 'mountain-1' })

      expect(importUnsplash).toHaveBeenCalledOnce()
      expect(upsertSpy).toHaveBeenCalledWith(importedAsset)
      expect(picker.pickerSelectionIds.value).toContain('1a378d75-3e06-5a66-9f68-ec6f418e3068')
      expect(picker.providerImportResolution.value['mountain-1']).toBe(
        '1a378d75-3e06-5a66-9f68-ec6f418e3068',
      )
    })

    it('allows retry after an import failure', async () => {
      const importUnsplash = vi
        .fn()
        .mockRejectedValueOnce(new Error('Import failed'))
        .mockResolvedValueOnce({
          assetId: '1ba5762a-06ea-5304-84a0-52261731aa43',
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
          sourceType: 'EXTERNAL',
          mediaType: 'image/jpeg',
          status: 'READY',
          originalFilename: 'retry.jpg',
          fileSizeBytes: 100,
          createdAt: '2026-06-19T12:00:00Z',
        } satisfies MediaAssetSummary)
      const picker = useComposerMediaPicker({
        mediaStore: createFakeMediaStore(),
        publishingStore: createFakePublishingStore(),
        editingPublication: ref(null),
        provider: ref('unsplash'),
        initialChannelId: ref(null),
        importUnsplash,
      })

      await picker.handleProviderImport({ externalId: 'retry' })
      expect(picker.providerSearchError.value).toBe('Import failed')

      await picker.handleProviderImport({ externalId: 'retry' })
      expect(importUnsplash).toHaveBeenCalledTimes(2)
      expect(picker.providerImportResolution.value.retry).toBe(
        '1ba5762a-06ea-5304-84a0-52261731aa43',
      )
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

      picker.scheduleAssetReconciliation('8bdec504-2ed1-59e4-8a4a-c068789d03d2')
      picker.scheduleAssetReconciliation('b909d06f-92ea-5671-9f13-73475a89080e')
      picker.pendingPickerAssets.value = ['8bdec504-2ed1-59e4-8a4a-c068789d03d2']
      picker.autoStagedAssetIds.value = ['b909d06f-92ea-5671-9f13-73475a89080e']
      picker.manuallyDeselectedAutoStageIds.value = ['24add7a3-5877-5ff8-a4cb-11ff965d127f']

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

      picker.autoStagedAssetIds.value = ['f464d618-0f38-5b38-adda-9cf4a0b674eb']
      picker.stageAssetOnce('f464d618-0f38-5b38-adda-9cf4a0b674eb')
      // Should not add duplicate
      expect(
        picker.autoStagedAssetIds.value.filter(
          (id) => id === 'f464d618-0f38-5b38-adda-9cf4a0b674eb',
        ),
      ).toHaveLength(1)
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

      picker.manuallyDeselectedAutoStageIds.value = ['f464d618-0f38-5b38-adda-9cf4a0b674eb']
      picker.stageAssetOnce('f464d618-0f38-5b38-adda-9cf4a0b674eb')
      expect(picker.autoStagedAssetIds.value).not.toContain('f464d618-0f38-5b38-adda-9cf4a0b674eb')
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

      picker.stageAssetOnce('222d5a39-3627-55e2-859c-489f66bfc783')
      expect(picker.autoStagedAssetIds.value).toContain('222d5a39-3627-55e2-859c-489f66bfc783')
      expect(picker.pickerSelectionIds.value).toContain('222d5a39-3627-55e2-859c-489f66bfc783')
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

      picker.pendingPickerAssets.value = ['f464d618-0f38-5b38-adda-9cf4a0b674eb']
      picker.addPendingPickerAsset('f464d618-0f38-5b38-adda-9cf4a0b674eb')
      expect(
        picker.pendingPickerAssets.value.filter(
          (id) => id === 'f464d618-0f38-5b38-adda-9cf4a0b674eb',
        ),
      ).toHaveLength(1)
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

      picker.pendingPickerAssets.value = [
        'f464d618-0f38-5b38-adda-9cf4a0b674eb',
        '13001fc9-a24f-54d3-bde1-cfa79074593c',
      ]
      picker.clearPendingPickerAsset('f464d618-0f38-5b38-adda-9cf4a0b674eb')
      expect(picker.pendingPickerAssets.value).toEqual(['13001fc9-a24f-54d3-bde1-cfa79074593c'])
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
