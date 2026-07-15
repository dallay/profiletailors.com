import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MediaLibraryView from './MediaLibraryView.vue'
import { useMediaStore } from '@modules/media/infrastructure/media.store'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import type { MediaAssetSummary } from '../../services/media-api'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key, locale: { value: 'en' } }),
  createI18n: () => ({ global: { locale: { value: 'en' } } }),
}))

vi.mock('@shared/i18n', () => ({
  default: { global: { locale: { value: 'en' } } },
}))

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  createApiFetch: () =>
    Object.assign(
      async function apiFetch<T>() {
        return {} as T
      },
      {
        raw: async () => new Response(null, { status: 204 }),
      },
    ),
  refreshSession: vi.fn().mockResolvedValue(null),
  getCurrentUserProfile: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  register: vi.fn(),
  logoutSession: vi.fn(),
  resolveApiUrl: (path: string) => `http://localhost:7638${path}`,
}))

vi.mock('@/components/ui/alert-dialog', () => ({
  AlertDialog: { template: '<div><slot /></div>' },
  AlertDialogTrigger: { template: '<div><slot /></div>', props: ['asChild'] },
  AlertDialogContent: { template: '<div><slot /></div>' },
  AlertDialogHeader: { template: '<div><slot /></div>' },
  AlertDialogTitle: { template: '<div><slot /></div>' },
  AlertDialogDescription: { template: '<div><slot /></div>' },
  AlertDialogFooter: { template: '<div><slot /></div>' },
  AlertDialogCancel: { template: '<button><slot /></button>' },
  AlertDialogAction: { template: '<button><slot /></button>' },
}))

function mountView(): ReturnType<typeof mount<typeof MediaLibraryView>> {
  return mount(MediaLibraryView, {
    global: {
      mocks: {
        $t: (key: string) => key,
      },
    },
  })
}

function makeAsset(overrides: Partial<MediaAssetSummary> = {}): MediaAssetSummary {
  const assetId = overrides.assetId ?? 'asset-1'

  return {
    assetId,
    workspaceId: 'ws-1',
    sourceType: 'UPLOADED',
    mediaType: 'image/jpeg',
    status: 'READY',
    originalFilename: 'asset.jpg',
    fileSizeBytes: 100,
    createdAt: '2026-06-19T12:00:00Z',
    ...overrides,
  }
}

describe('MediaLibraryView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // Mock loadAssets so onMount's refreshLibrary() doesn't clear test data
    const store = useMediaStore()
    vi.spyOn(store, 'loadAssets').mockResolvedValue()
    useAuthStore().user = {
      principalId: 'principal-1',
      email: 'owner@example.com',
      username: 'owner',
      displayIdentity: 'Owner',
      emailStatus: 'VERIFIED',
    }
  })

  it('disables upload and shows verification guidance for non-verified users', async () => {
    const auth = useAuthStore()
    auth.user = { ...auth.user!, emailStatus: 'PENDING' }
    const mediaStore = useMediaStore()
    const uploadSpy = vi.spyOn(mediaStore, 'createAndUpload')

    const wrapper = mountView()
    await flushPromises()

    const uploadButton = wrapper.get('[data-testid="media-upload-button"]')
    expect(uploadButton.attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="media-verification-guidance"]').text()).toContain(
      'media.verificationRequired',
    )

    await uploadButton.trigger('click')
    await (wrapper.vm as unknown as { uploadFiles: (files: File[]) => Promise<void> }).uploadFiles([
      new File(['bytes'], 'photo.jpg', { type: 'image/jpeg' }),
    ])
    expect(uploadSpy).not.toHaveBeenCalled()
  })

  it('allows verified users to open the upload picker', async () => {
    const wrapper = mountView()
    await flushPromises()
    const fileInput = wrapper.get('#media-library-file-input')
    const clickSpy = vi.spyOn(fileInput.element as HTMLInputElement, 'click')

    await wrapper.get('[data-testid="media-upload-button"]').trigger('click')

    expect(clickSpy).toHaveBeenCalledOnce()
  })

  it('shows verification guidance when backend denies upload with email verification problem', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'createAndUpload').mockRejectedValue({
      status: 403,
      code: 'EMAIL_VERIFICATION_REQUIRED',
      detail: 'Please verify your email before using this feature.',
    })
    const wrapper = mountView()
    await flushPromises()

    await (wrapper.vm as unknown as { uploadFiles: (files: File[]) => Promise<void> }).uploadFiles([
      new File(['bytes'], 'photo.jpg', { type: 'image/jpeg' }),
    ])
    await flushPromises()

    expect(wrapper.get('[data-testid="media-verification-guidance"]').text()).toContain(
      'media.verificationRequired',
    )
  })

  it('renders empty state when there are no assets', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('nav.media')
    expect(wrapper.text()).toContain('media.emptyTitle')
    expect(wrapper.text()).toContain('media.emptyBody')
  })

  it('shows the loading spinner when store is loading and no assets are present (lines 430-432)', async () => {
    const store = useMediaStore()
    // Prevent loadAssets from setting isLoading=false — keep it true
    vi.spyOn(store, 'loadAssets').mockImplementation(() => new Promise(() => {}))
    store.isLoading = true
    store.assetIds = []
    store.assetsById = {}

    const wrapper = mountView()
    // Do NOT await flushPromises — loading state must be observed before promise resolves

    expect(wrapper.text()).toContain('media.loading')
  })

  it('shows the no-filtered-results message when assets exist but all are filtered out (lines 441-444)', async () => {
    const mediaStore = useMediaStore()
    // Add one READY image asset
    mediaStore.assetsById['image-ready'] = makeAsset({
      assetId: 'image-ready',
      originalFilename: 'hero.jpg',
      previewUrl: '/api/media/assets/image-ready/preview',
      downloadUrl: '/api/media/assets/image-ready/content',
    })
    mediaStore.assetIds.push('image-ready')

    const wrapper = mountView()
    await flushPromises()

    // Apply a type filter that excludes the only asset (VIDEO filter when only IMAGE exists)
    await wrapper.find('[data-testid="filter-type"]').setValue('VIDEO')

    expect(wrapper.text()).toContain('media.noFilteredAssetsTitle')
    expect(wrapper.text()).toContain('media.noFilteredAssetsBody')
  })

  it('does not render external attribution metadata', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['external-asset'] = makeAsset({
      assetId: 'external-asset',
      sourceType: 'EXTERNAL',
      sourceProvider: 'unsplash',
      externalId: 'photo-123',
      sourceUrl: 'https://example.test/source-photo-123',
      authorName: 'Attribution Author Sentinel',
      authorUrl: 'https://example.test/attribution-author-sentinel',
      metadata: { attributionSentinel: 'hidden-provider-metadata' },
      originalFilename: 'external.jpg',
      fileSizeBytes: 1024,
      previewUrl: '/api/media/assets/external-asset/preview',
      downloadUrl: '/api/media/assets/external-asset/content',
    })
    mediaStore.assetIds.push('external-asset')

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('external.jpg')
    expect(wrapper.text()).not.toContain('Attribution Author Sentinel')
    expect(wrapper.html()).not.toContain('attribution-author-sentinel')
    expect(wrapper.html()).not.toContain('hidden-provider-metadata')
    expect(wrapper.html()).not.toContain('unsplash')
    expect(wrapper.html()).not.toContain('source-photo-123')
    expect(wrapper.html()).not.toContain('https://example.test/source-photo-123')
  })

  it('renders asset cards when assets exist', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['asset-1'] = makeAsset({
      assetId: 'asset-1',
      originalFilename: 'hero.jpg',
      fileSizeBytes: 1024,
      previewUrl: '/api/media/assets/asset-1/preview',
      downloadUrl: '/api/media/assets/asset-1/content',
    })
    mediaStore.assetIds.push('asset-1')

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('hero.jpg')
    expect(wrapper.text()).toContain('READY')
    expect(wrapper.find('img').attributes('src')).toContain('/api/media/assets/asset-1/preview')
  })

  it('filters assets by status and type', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['image-ready'] = makeAsset({
      assetId: 'image-ready',
      originalFilename: 'hero.jpg',
      previewUrl: '/api/media/assets/image-ready/preview',
      downloadUrl: '/api/media/assets/image-ready/content',
    })
    mediaStore.assetsById['video-processing'] = makeAsset({
      assetId: 'video-processing',
      mediaType: 'video/mp4',
      status: 'UPLOADING',
      originalFilename: 'clip.mp4',
      fileSizeBytes: 200,
      createdAt: '2026-06-18T12:00:00Z',
      downloadUrl: '/api/media/assets/video-processing/content',
    })
    mediaStore.assetIds.push('image-ready', 'video-processing')

    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('[data-testid="filter-status"]').setValue('READY')
    await wrapper.find('[data-testid="filter-type"]').setValue('IMAGE')

    expect(wrapper.text()).toContain('hero.jpg')
    expect(wrapper.text()).not.toContain('clip.mp4')
  })

  it('maps pending and uploading CAS statuses to the shared processing presentation', async () => {
    const mediaStore = useMediaStore()
    for (const [assetId, status] of [
      ['pending-asset', 'PENDING_UPLOAD'],
      ['uploading-asset', 'UPLOADING'],
    ] as const) {
      mediaStore.assetsById[assetId] = makeAsset({
        assetId,
        mediaType: 'image/png',
        status,
        originalFilename: `${assetId}.png`,
      })
      mediaStore.assetIds.push(assetId)
    }

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('media.processingTitle2')

    await wrapper.find('[data-testid="filter-status"]').setValue('PROCESSING')

    const cards = wrapper.findAll('article')
    expect(cards).toHaveLength(2)
    expect(
      cards.every((card) =>
        card.find('[data-testid="status-badge"]').classes().includes('text-text-display'),
      ),
    ).toBe(true)

    await wrapper.find('#select-all-visible').setValue(true)
    expect(wrapper.text()).not.toContain('media.selectedCountSuffix')
  })

  it('applies fallback status class for unknown/edge statuses like DELETED', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['deleted-asset'] = makeAsset({
      assetId: 'deleted-asset',
      mediaType: 'image/png',
      status: 'DELETED',
      originalFilename: 'removed.png',
    })
    mediaStore.assetIds.push('deleted-asset')

    const wrapper = mountView()
    await flushPromises()

    const cards = wrapper.findAll('article')
    const deletedCard = cards.find((card) => card.text().includes('removed.png'))
    expect(deletedCard?.exists()).toBe(true)
    const badge = deletedCard!.find('[data-testid="status-badge"]')
    expect(badge.text()).toBe('DELETED')
    expect(badge.classes()).toContain('border-border-visible')
    expect(badge.classes()).toContain('bg-bg-primary')
    expect(badge.classes()).toContain('text-text-secondary')
  })

  it('searches assets by filename', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['asset-a'] = makeAsset({
      assetId: 'asset-a',
      originalFilename: 'hero.jpg',
      previewUrl: '/api/media/assets/asset-a/preview',
      downloadUrl: '/api/media/assets/asset-a/content',
    })
    mediaStore.assetsById['asset-b'] = makeAsset({
      assetId: 'asset-b',
      mediaType: 'application/pdf',
      originalFilename: 'invoice.pdf',
      createdAt: '2026-06-18T12:00:00Z',
      downloadUrl: '/api/media/assets/asset-b/content',
    })
    mediaStore.assetIds.push('asset-a', 'asset-b')

    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('input[type="search"]').setValue('invoice')

    expect(wrapper.text()).toContain('invoice.pdf')
    expect(wrapper.text()).not.toContain('hero.jpg')
  })

  it('sorts assets by filename ascending', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['asset-z'] = makeAsset({
      assetId: 'asset-z',
      originalFilename: 'zebra.jpg',
      previewUrl: '/api/media/assets/asset-z/preview',
      downloadUrl: '/api/media/assets/asset-z/content',
    })
    mediaStore.assetsById['asset-a'] = makeAsset({
      assetId: 'asset-a',
      originalFilename: 'apple.jpg',
      createdAt: '2026-06-18T12:00:00Z',
      previewUrl: '/api/media/assets/asset-a/preview',
      downloadUrl: '/api/media/assets/asset-a/content',
    })
    mediaStore.assetIds.push('asset-z', 'asset-a')

    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('[data-testid="filter-sort"]').setValue('filename-asc')

    const cards = wrapper.findAll('article')
    expect(cards[0]?.text()).toContain('apple.jpg')
    expect(cards[1]?.text()).toContain('zebra.jpg')
  })

  it('selects deletable assets only and handles per-item failures gracefully', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['asset-1'] = makeAsset({
      assetId: 'asset-1',
      originalFilename: 'one.jpg',
      previewUrl: '/api/media/assets/asset-1/preview',
      downloadUrl: '/api/media/assets/asset-1/content',
    })
    mediaStore.assetsById['asset-2'] = makeAsset({
      assetId: 'asset-2',
      mediaType: 'application/pdf',
      status: 'FAILED',
      originalFilename: 'two.pdf',
      createdAt: '2026-06-18T12:00:00Z',
      downloadUrl: '/api/media/assets/asset-2/content',
    })
    mediaStore.assetsById['asset-3'] = makeAsset({
      assetId: 'asset-3',
      mediaType: 'image/png',
      status: 'UPLOADING',
      originalFilename: 'three.png',
      createdAt: '2026-06-20T12:00:00Z',
    })
    mediaStore.assetIds.push('asset-1', 'asset-2', 'asset-3')

    // asset-2 delete will fail; test that loop continues and PROCESSING assets are excluded
    const deleteSpy = vi
      .spyOn(mediaStore, 'deletePersistedAsset')
      .mockImplementation(async (assetId) => {
        if (assetId === 'asset-2') throw new Error('delete failed')
      })

    const wrapper = mountView()
    await flushPromises()

    const selectVisibleCheckbox = wrapper.find('#select-all-visible')
    expect(selectVisibleCheckbox.exists()).toBe(true)
    await selectVisibleCheckbox.setValue(true)

    // Only READY and FAILED assets are selected (PROCESSING excluded)
    expect(wrapper.text()).toContain('2 media.selectedCountSuffix')
    expect(wrapper.text()).not.toContain('3 media.selectedCountSuffix')

    const deleteSelectedButtons = wrapper
      .findAll('button')
      .filter((button) => button.text().includes('media.deleteSelectedAction'))
    expect(deleteSelectedButtons.length).toBeGreaterThan(0)
    await deleteSelectedButtons[deleteSelectedButtons.length - 1]?.trigger('click')

    // asset-1 and asset-2 were attempted; asset-3 (PROCESSING) was skipped
    expect(deleteSpy).toHaveBeenCalledTimes(2)
    expect(deleteSpy).toHaveBeenCalledWith('asset-1')
    expect(deleteSpy).toHaveBeenCalledWith('asset-2')
    expect(deleteSpy).not.toHaveBeenCalledWith('asset-3')
  })
})
