import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MediaLibraryView from './MediaLibraryView.vue'
import { useMediaStore } from '@/stores/media'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key, locale: { value: 'en' } }),
  createI18n: () => ({ global: { locale: { value: 'en' } } }),
}))

vi.mock('@/i18n', () => ({
  default: { global: { locale: { value: 'en' } } },
}))

vi.mock('@/lib/auth-api', () => ({
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
  resolveApiUrl: (path: string) => `http://localhost:8080${path}`,
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

function mountView() {
  return mount(MediaLibraryView, {
    global: {
      mocks: {
        $t: (key: string) => key,
      },
    },
  })
}

describe('MediaLibraryView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders empty state when there are no assets', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('nav.media')
    expect(wrapper.text()).toContain('media.emptyTitle')
    expect(wrapper.text()).toContain('media.emptyBody')
  })

  it('renders asset cards when assets exist', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['asset-1'] = {
      assetId: 'asset-1',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'hero.jpg',
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-1/preview',
      downloadUrl: '/api/media/assets/asset-1/content',
    }
    mediaStore.assetIds.push('asset-1')

    const wrapper = mountView()
    await flushPromises()

    const article = wrapper.find('article')
    expect(article.text()).toContain('hero.jpg')
    expect(article.text()).toContain('media.readyStatus')
    expect(article.text()).toContain('1.0 KB')
    expect(article.text()).toContain('jpeg')
  })

  it('filters assets by status and type', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['image-ready'] = {
      assetId: 'image-ready',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'hero.jpg',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/image-ready/preview',
      downloadUrl: '/api/media/assets/image-ready/content',
    }
    mediaStore.assetsById['video-processing'] = {
      assetId: 'video-processing',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'video/mp4',
      status: 'PROCESSING',
      originalFilename: 'clip.mp4',
      fileSizeBytes: 200,
      createdAt: '2026-06-18T12:00:00Z',
      downloadUrl: '/api/media/assets/video-processing/content',
    }
    mediaStore.assetIds.push('image-ready', 'video-processing')

    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('[data-testid="filter-status"]').setValue('READY')
    await wrapper.find('[data-testid="filter-type"]').setValue('IMAGE')

    expect(wrapper.text()).toContain('hero.jpg')
    expect(wrapper.text()).not.toContain('clip.mp4')
  })

  it('searches assets by filename', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['asset-a'] = {
      assetId: 'asset-a',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'hero.jpg',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-a/preview',
      downloadUrl: '/api/media/assets/asset-a/content',
    }
    mediaStore.assetsById['asset-b'] = {
      assetId: 'asset-b',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'application/pdf',
      status: 'READY',
      originalFilename: 'invoice.pdf',
      fileSizeBytes: 100,
      createdAt: '2026-06-18T12:00:00Z',
      downloadUrl: '/api/media/assets/asset-b/content',
    }
    mediaStore.assetIds.push('asset-a', 'asset-b')

    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('input[type="search"]').setValue('invoice')

    expect(wrapper.text()).toContain('invoice.pdf')
    expect(wrapper.text()).not.toContain('hero.jpg')
  })

  it('sorts assets by filename ascending', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['asset-z'] = {
      assetId: 'asset-z',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'zebra.jpg',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-z/preview',
      downloadUrl: '/api/media/assets/asset-z/content',
    }
    mediaStore.assetsById['asset-a'] = {
      assetId: 'asset-a',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'apple.jpg',
      fileSizeBytes: 100,
      createdAt: '2026-06-18T12:00:00Z',
      previewUrl: '/api/media/assets/asset-a/preview',
      downloadUrl: '/api/media/assets/asset-a/content',
    }
    mediaStore.assetIds.push('asset-z', 'asset-a')

    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('[data-testid="filter-sort"]').setValue('filename-asc')

    const cards = wrapper.findAll('article')
    expect(cards[0]?.text()).toContain('apple.jpg')
    expect(cards[1]?.text()).toContain('zebra.jpg')
  })

  it('displays loading spinner when no assets and still loading', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()
    mediaStore.isLoading = true

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('media.loading')
  })

  it('displays error banner when loadError is set', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()

    const wrapper = mountView()
    await flushPromises()

    mediaStore.loadError = 'Something went wrong'
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('Something went wrong')
  })

  it('shows active uploads section', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()
    mediaStore.uploads['temp-1'] = {
      tempKey: 'temp-1',
      assetId: 'asset-upload',
      file: new File(['test'], 'photo.jpg', { type: 'image/jpeg' }),
      progress: 50,
      status: 'uploading',
    }

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('media.activeUploadsTitle')
  })

  it('formats null file size as em dash and shows processing/failed status badges', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()

    mediaStore.assetsById['asset-proc'] = {
      assetId: 'asset-proc',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'PROCESSING',
      originalFilename: 'processing.jpg',
      fileSizeBytes: null,
      createdAt: '2026-06-19T12:00:00Z',
    }
    mediaStore.assetsById['asset-fail'] = {
      assetId: 'asset-fail',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'video/mp4',
      status: 'FAILED',
      originalFilename: 'failed.mp4',
      fileSizeBytes: 500,
      createdAt: '2026-06-18T12:00:00Z',
      downloadUrl: '/api/media/assets/asset-fail/content',
    }
    mediaStore.assetIds.push('asset-proc', 'asset-fail')

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('media.processingStatus')
    expect(wrapper.text()).toContain('media.failedStatus')
    expect(wrapper.text()).toContain('\u2014')
  })

  it('disables selection checkbox for processing assets', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()

    mediaStore.assetsById['asset-proc'] = {
      assetId: 'asset-proc',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'PROCESSING',
      originalFilename: 'processing.jpg',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
    }
    mediaStore.assetIds.push('asset-proc')

    const wrapper = mountView()
    await flushPromises()

    const checkbox = wrapper.find('article input[type="checkbox"]')
    expect(checkbox.attributes('disabled')).toBeDefined()
  })

  it('preserves selection for failed deletions during bulk delete', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()

    mediaStore.assetsById['asset-1'] = {
      assetId: 'asset-1',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'one.jpg',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-1/preview',
      downloadUrl: '/api/media/assets/asset-1/content',
    }
    mediaStore.assetsById['asset-2'] = {
      assetId: 'asset-2',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'application/pdf',
      status: 'FAILED',
      originalFilename: 'two.pdf',
      fileSizeBytes: 200,
      createdAt: '2026-06-18T12:00:00Z',
      downloadUrl: '/api/media/assets/asset-2/content',
    }
    mediaStore.assetIds.push('asset-1', 'asset-2')

    const _deleteSpy = vi
      .spyOn(mediaStore, 'deletePersistedAsset')
      .mockRejectedValueOnce(new Error('Network error'))
      .mockResolvedValueOnce()

    const wrapper = mountView()
    await flushPromises()

    const checkboxes = wrapper.findAll('article input[type="checkbox"]')
    await checkboxes[0]?.setValue(true)
    await checkboxes[1]?.setValue(true)
    expect(wrapper.text()).toContain('2 selected')

    const deleteSelectedTrigger = wrapper
      .findAll('button')
      .find((b) => b.text().includes('Delete selected'))
    await deleteSelectedTrigger?.trigger('click')
    await flushPromises()

    const confirmButtons = wrapper
      .findAll('button')
      .filter((b) => b.text().includes('media.deleteSelectedAction'))
    await confirmButtons[confirmButtons.length - 1]?.trigger('click')
    await flushPromises()

    // First delete failed (rejected), second succeeded — one asset remains selected
    expect(wrapper.text()).toContain('1 selected')
  })

  it('renders load more button and calls loadNextPage', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()
    const loadNextSpy = vi.spyOn(mediaStore, 'loadNextPage').mockResolvedValue()

    mediaStore.assetsById['asset-1'] = {
      assetId: 'asset-1',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'one.jpg',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-1/preview',
      downloadUrl: '/api/media/assets/asset-1/content',
    }
    mediaStore.assetIds.push('asset-1')
    mediaStore.nextCursor = 'cursor-abc'

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('media.loadMore')

    const loadMoreButton = wrapper
      .findAll('button')
      .find((b) => b.text().includes('media.loadMore'))
    await loadMoreButton?.trigger('click')
    await flushPromises()

    expect(loadNextSpy).toHaveBeenCalledWith('READY,PROCESSING,FAILED')
  })

  it('shows reset filters button and resets filters on click', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()

    mediaStore.assetsById['asset-1'] = {
      assetId: 'asset-1',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'hero.jpg',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-1/preview',
      downloadUrl: '/api/media/assets/asset-1/content',
    }
    mediaStore.assetIds.push('asset-1')

    const wrapper = mountView()
    await flushPromises()

    // Apply a filter that excludes the only asset
    await wrapper.find('[data-testid="filter-status"]').setValue('PROCESSING')
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('media.noFilteredAssetsTitle')
    expect(wrapper.text()).toContain('Reset filters')

    // Click reset button
    const resetButton = wrapper.findAll('button').find((b) => b.text().includes('Reset filters'))
    await resetButton?.trigger('click')
    await wrapper.vm.$nextTick()

    // Asset should be visible again
    expect(wrapper.text()).toContain('hero.jpg')
  })

  it('filters by type: video, pdf, and other', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()

    mediaStore.assetsById['img'] = {
      assetId: 'img',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: 'img.png',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/img/preview',
      downloadUrl: '/api/media/assets/img/content',
    }
    mediaStore.assetsById['vid'] = {
      assetId: 'vid',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'video/mp4',
      status: 'READY',
      originalFilename: 'vid.mp4',
      fileSizeBytes: 200,
      createdAt: '2026-06-18T12:00:00Z',
      downloadUrl: '/api/media/assets/vid/content',
    }
    mediaStore.assetsById['pdf'] = {
      assetId: 'pdf',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'application/pdf',
      status: 'READY',
      originalFilename: 'doc.pdf',
      fileSizeBytes: 300,
      createdAt: '2026-06-17T12:00:00Z',
      downloadUrl: '/api/media/assets/pdf/content',
    }
    mediaStore.assetsById['other'] = {
      assetId: 'other',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'text/plain',
      status: 'READY',
      originalFilename: 'notes.txt',
      fileSizeBytes: 400,
      createdAt: '2026-06-16T12:00:00Z',
    }
    mediaStore.assetIds.push('img', 'vid', 'pdf', 'other')

    const wrapper = mountView()
    await flushPromises()

    // Filter by VIDEO
    await wrapper.find('[data-testid="filter-type"]').setValue('VIDEO')
    expect(wrapper.text()).toContain('vid.mp4')
    expect(wrapper.text()).not.toContain('img.png')

    // Filter by PDF
    await wrapper.find('[data-testid="filter-type"]').setValue('PDF')
    expect(wrapper.text()).toContain('doc.pdf')
    expect(wrapper.text()).not.toContain('vid.mp4')

    // Filter by OTHER (not image, video, or pdf)
    await wrapper.find('[data-testid="filter-type"]').setValue('OTHER')
    expect(wrapper.text()).toContain('notes.txt')
    expect(wrapper.text()).not.toContain('doc.pdf')
  })

  it('clears selection when clear selection button is clicked', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()

    mediaStore.assetsById['asset-1'] = {
      assetId: 'asset-1',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'one.jpg',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-1/preview',
      downloadUrl: '/api/media/assets/asset-1/content',
    }
    mediaStore.assetIds.push('asset-1')

    const wrapper = mountView()
    await flushPromises()

    // Select the asset
    const checkbox = wrapper.find('article input[type="checkbox"]')
    await checkbox.setValue(true)
    expect(wrapper.text()).toContain('1 selected')

    // Click "Clear selection"
    const clearButton = wrapper.findAll('button').find((b) => b.text().includes('Clear selection'))
    await clearButton?.trigger('click')
    await wrapper.vm.$nextTick()

    // Selection toolbar should disappear
    expect(wrapper.text()).not.toContain('1 selected')
  })

  it('toggles select all checkbox', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()

    mediaStore.assetsById['asset-1'] = {
      assetId: 'asset-1',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'one.jpg',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-1/preview',
      downloadUrl: '/api/media/assets/asset-1/content',
    }
    mediaStore.assetsById['asset-2'] = {
      assetId: 'asset-2',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'application/pdf',
      status: 'READY',
      originalFilename: 'two.pdf',
      fileSizeBytes: 200,
      createdAt: '2026-06-18T12:00:00Z',
      downloadUrl: '/api/media/assets/asset-2/content',
    }
    mediaStore.assetIds.push('asset-1', 'asset-2')

    const wrapper = mountView()
    await flushPromises()

    // First select one asset to make the selection toolbar visible
    const galleryCheckbox = wrapper.find('article input[type="checkbox"]')
    await galleryCheckbox.setValue(true)
    expect(wrapper.text()).toContain('1 selected')

    // Select-all checkbox is first in DOM (toolbar before gallery grid in template)
    const selectAllCheckbox = wrapper.find('input[type="checkbox"]')
    expect(selectAllCheckbox.exists()).toBe(true)

    // Click select-all → both selected
    await selectAllCheckbox.setValue(true)
    expect(wrapper.text()).toContain('2 selected')

    // Click select-all again → all deselected (toolbar hides)
    ;(selectAllCheckbox.element as HTMLInputElement).checked = false
    await selectAllCheckbox.trigger('change')
    expect(wrapper.text()).not.toContain('1 selected')
    expect(wrapper.text()).not.toContain('2 selected')
  })

  it('deletes individual asset via gallery tile', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()

    mediaStore.assetsById['asset-1'] = {
      assetId: 'asset-1',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'one.jpg',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-1/preview',
      downloadUrl: '/api/media/assets/asset-1/content',
    }
    mediaStore.assetIds.push('asset-1')

    const deleteSpy = vi.spyOn(mediaStore, 'deletePersistedAsset').mockResolvedValue()

    const wrapper = mountView()
    await flushPromises()

    // Trigger individual delete: click the asset delete button (first AlertDialogTrigger)
    const deleteTileTrigger = wrapper
      .findAll('button')
      .find((b) => b.attributes('title')?.includes('media.deleteAction'))
    await deleteTileTrigger?.trigger('click')
    await flushPromises()

    // Confirm in AlertDialog
    const confirmButtons = wrapper
      .findAll('button')
      .filter((b) => b.text().includes('media.deleteAction'))
    await confirmButtons[confirmButtons.length - 1]?.trigger('click')
    await flushPromises()

    expect(deleteSpy).toHaveBeenCalledWith('asset-1')
  })

  it('dismisses a failed upload', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()
    const dismissSpy = vi.spyOn(mediaStore, 'dismissUpload')

    mediaStore.uploads['temp-fail'] = {
      tempKey: 'temp-fail',
      assetId: 'asset-fail',
      file: new File(['data'], 'broken.png', { type: 'image/png' }),
      progress: 30,
      status: 'failed',
      errorTitle: 'Upload failed',
      errorDetail: 'Server error',
    }

    const wrapper = mountView()
    await flushPromises()

    const dismissButton = wrapper.findAll('button').find((b) => b.text().includes('media.dismiss'))
    await dismissButton?.trigger('click')
    await flushPromises()

    expect(dismissSpy).toHaveBeenCalledWith('temp-fail')
  })

  it('triggers file picker when upload button is clicked', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()

    const wrapper = mountView()
    await flushPromises()

    const uploadButton = wrapper
      .findAll('button')
      .find((b) => b.text().includes('media.uploadAction'))
    expect(uploadButton).toBeDefined()

    // Click should not throw — openFilePicker accesses ref that may be null in tests
    await expect(uploadButton?.trigger('click')).resolves.toBeUndefined()
  })

  it('handles upload error gracefully', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()
    vi.spyOn(mediaStore, 'createAndUpload').mockRejectedValue(new Error('Upload failed'))

    const wrapper = mountView()
    await flushPromises()

    const fileInput = wrapper.find('#media-library-file-input')
    const file = new File(['content'], 'broken.jpg', { type: 'image/jpeg' })
    Object.defineProperty(fileInput.element, 'files', {
      value: [file],
    })
    await fileInput.trigger('change')
    await flushPromises()

    // Upload error should be caught silently — no crash
    expect(wrapper.find('.text-error').exists()).toBe(false)
  })

  it('handles retry upload error gracefully', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()
    vi.spyOn(mediaStore, 'retryUpload').mockRejectedValue(new Error('Retry failed'))

    mediaStore.uploads['temp-fail'] = {
      tempKey: 'temp-fail',
      assetId: 'asset-fail',
      file: new File(['data'], 'broken.png', { type: 'image/png' }),
      progress: 30,
      status: 'failed',
      errorTitle: 'Upload failed',
      errorDetail: 'Server error',
    }

    const wrapper = mountView()
    await flushPromises()

    const retryButton = wrapper.findAll('button').find((b) => b.text().includes('media.retry'))
    await retryButton?.trigger('click')
    await flushPromises()

    // Error is caught silently; upload section still visible
    expect(wrapper.text()).toContain('media.activeUploadsTitle')
  })

  it('triggers file upload when files are selected', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()
    const uploadSpy = vi.spyOn(mediaStore, 'createAndUpload').mockResolvedValue({
      assetId: 'new-asset',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      createdAt: '2026-06-20T12:00:00Z',
    } as any)

    const wrapper = mountView()
    await flushPromises()

    const fileInput = wrapper.find('#media-library-file-input')
    const file = new File(['content'], 'photo.jpg', { type: 'image/jpeg' })
    Object.defineProperty(fileInput.element, 'files', {
      value: [file],
    })
    await fileInput.trigger('change')
    await flushPromises()

    expect(uploadSpy).toHaveBeenCalled()
  })

  it('retries a failed upload', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()
    const retrySpy = vi.spyOn(mediaStore, 'retryUpload').mockResolvedValue({
      assetId: 'retried-asset',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      createdAt: '2026-06-20T12:00:00Z',
    } as any)

    mediaStore.uploads['temp-fail'] = {
      tempKey: 'temp-fail',
      assetId: 'asset-fail',
      file: new File(['data'], 'broken.png', { type: 'image/png' }),
      progress: 30,
      status: 'failed',
      errorTitle: 'Upload failed',
      errorDetail: 'Server error',
    }

    const wrapper = mountView()
    await flushPromises()

    // Upload section should be visible with retry button
    expect(wrapper.text()).toContain('media.activeUploadsTitle')
    expect(wrapper.text()).toContain('Server error')

    const retryButton = wrapper.findAll('button').find((b) => b.text().includes('media.retry'))
    await retryButton?.trigger('click')
    await flushPromises()

    expect(retrySpy).toHaveBeenCalledWith('temp-fail')
  })

  it('selects visible assets and bulk deletes them', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['asset-1'] = {
      assetId: 'asset-1',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'one.jpg',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-1/preview',
      downloadUrl: '/api/media/assets/asset-1/content',
    }
    mediaStore.assetsById['asset-2'] = {
      assetId: 'asset-2',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'application/pdf',
      status: 'FAILED',
      originalFilename: 'two.pdf',
      fileSizeBytes: 100,
      createdAt: '2026-06-18T12:00:00Z',
      downloadUrl: '/api/media/assets/asset-2/content',
    }
    mediaStore.assetIds.push('asset-1', 'asset-2')

    const deleteSpy = vi.spyOn(mediaStore, 'deletePersistedAsset').mockResolvedValue()

    const wrapper = mountView()
    await flushPromises()

    // Toggle individual asset selection via checkbox values
    const checkboxes = wrapper.findAll('article input[type="checkbox"]')
    expect(checkboxes.length).toBe(2)
    await checkboxes[0]?.setValue(true)
    await checkboxes[1]?.setValue(true)

    // Selection toolbar should now show with the count
    expect(wrapper.text()).toContain('2 selected')

    const deleteSelectedTrigger = wrapper
      .findAll('button')
      .find((button) => button.text().includes('Delete selected'))
    expect(deleteSelectedTrigger?.exists()).toBe(true)
    await deleteSelectedTrigger?.trigger('click')
    await flushPromises()

    const deleteActionButton = wrapper
      .findAll('button')
      .filter((button) => button.text().includes('media.deleteSelectedAction'))
    expect(deleteActionButton.length).toBeGreaterThan(0)
    await deleteActionButton[deleteActionButton.length - 1]?.trigger('click')

    expect(deleteSpy).toHaveBeenCalledTimes(2)
    expect(deleteSpy).toHaveBeenCalledWith('asset-1')
    expect(deleteSpy).toHaveBeenCalledWith('asset-2')
  })

  it('sorts by oldest, filename desc, size, and status', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()

    mediaStore.assetsById['asset-old'] = {
      assetId: 'asset-old',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'PROCESSING',
      originalFilename: 'bbb.jpg',
      fileSizeBytes: 2000,
      createdAt: '2026-06-17T12:00:00Z',
    }
    mediaStore.assetsById['asset-new'] = {
      assetId: 'asset-new',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: 'aaa.png',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-new/preview',
      downloadUrl: '/api/media/assets/asset-new/content',
    }
    mediaStore.assetIds.push('asset-old', 'asset-new')

    const wrapper = mountView()
    await flushPromises()

    // Sort oldest first
    await wrapper.find('[data-testid="filter-sort"]').setValue('oldest')
    let cards = wrapper.findAll('article')
    expect(cards[0]?.text()).toContain('bbb.jpg')

    // Sort filename descending
    await wrapper.find('[data-testid="filter-sort"]').setValue('filename-desc')
    cards = wrapper.findAll('article')
    expect(cards[0]?.text()).toContain('bbb.jpg')

    // Sort size ascending
    await wrapper.find('[data-testid="filter-sort"]').setValue('size-asc')
    cards = wrapper.findAll('article')
    expect(cards[0]?.text()).toContain('aaa.png')

    // Sort size descending
    await wrapper.find('[data-testid="filter-sort"]').setValue('size-desc')
    cards = wrapper.findAll('article')
    expect(cards[0]?.text()).toContain('bbb.jpg')

    // Sort by status (PROCESSING < READY alphabetically)
    await wrapper.find('[data-testid="filter-sort"]').setValue('status')
    cards = wrapper.findAll('article')
    expect(cards[0]?.text()).toContain('media.processingStatus')
  })

  it('toggles upload section collapse', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()
    mediaStore.uploads['temp-1'] = {
      tempKey: 'temp-1',
      assetId: 'asset-1',
      file: new File(['data'], 'photo.jpg', { type: 'image/jpeg' }),
      progress: 50,
      status: 'uploading',
    }

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('media.activeUploadsTitle')

    // Toggle collapse
    const toggleButton = wrapper
      .findAll('button')
      .find((b) => b.text().includes('media.activeUploadsTitle'))
    await toggleButton?.trigger('click')
    await wrapper.vm.$nextTick()

    // Upload content should be hidden
    expect(wrapper.text()).not.toContain('uploading')
  })

  it('handles error during individual asset deletion', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()
    const deleteSpy = vi
      .spyOn(mediaStore, 'deletePersistedAsset')
      .mockRejectedValue(new Error('Delete failed'))

    mediaStore.assetsById['asset-1'] = {
      assetId: 'asset-1',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'one.jpg',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-1/preview',
      downloadUrl: '/api/media/assets/asset-1/content',
    }
    mediaStore.assetIds.push('asset-1')

    const wrapper = mountView()
    await flushPromises()

    // Trigger individual delete tile
    const deleteTileTrigger = wrapper
      .findAll('button')
      .find((b) => b.attributes('title')?.includes('media.deleteAction'))
    await deleteTileTrigger?.trigger('click')
    await flushPromises()

    // Confirm
    const confirmButtons = wrapper
      .findAll('button')
      .filter((b) => b.text().includes('media.deleteAction'))
    await confirmButtons[confirmButtons.length - 1]?.trigger('click')
    await flushPromises()

    // Error should be caught silently — asset remains visible
    expect(deleteSpy).toHaveBeenCalledWith('asset-1')
    expect(wrapper.text()).toContain('one.jpg')
  })

  it('triggers download when download button is clicked', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()

    mediaStore.assetsById['asset-1'] = {
      assetId: 'asset-1',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'photo.jpg',
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-1/preview',
      downloadUrl: '/api/media/assets/asset-1/content',
    }
    mediaStore.assetIds.push('asset-1')

    // Spy on createElement to verify the download anchor is created
    const createElementSpy = vi.spyOn(document, 'createElement')

    const wrapper = mountView()
    await flushPromises()

    // Find the download button in the hover overlay
    const downloadButton = wrapper
      .findAll('button')
      .find((b) => b.attributes('title')?.includes('media.downloadAction'))
    expect(downloadButton).toBeDefined()

    await downloadButton?.trigger('click')
    await flushPromises()

    expect(createElementSpy).toHaveBeenCalledWith('a')
    createElementSpy.mockRestore()
  })

  it('deselects an asset by clicking its checkbox again', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()

    mediaStore.assetsById['asset-1'] = {
      assetId: 'asset-1',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'one.jpg',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-1/preview',
      downloadUrl: '/api/media/assets/asset-1/content',
    }
    mediaStore.assetIds.push('asset-1')

    const wrapper = mountView()
    await flushPromises()

    const galleryCheckbox = wrapper.find('article input[type="checkbox"]')
    await galleryCheckbox.setValue(true)
    expect(wrapper.text()).toContain('1 selected')

    // Click the same checkbox again to deselect
    await galleryCheckbox.setValue(false)
    await wrapper.vm.$nextTick()

    // Selection toolbar should hide
    expect(wrapper.text()).not.toContain('selected')
  })

  it('handles unknown asset status gracefully', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue()

    mediaStore.assetsById['asset-unknown'] = {
      assetId: 'asset-unknown',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'UNKNOWN',
      originalFilename: 'weird.jpg',
      fileSizeBytes: 100,
      createdAt: '2026-06-19T12:00:00Z',
    }
    mediaStore.assetIds.push('asset-unknown')

    const wrapper = mountView()
    await flushPromises()

    // Status badge should render with default variant
    expect(wrapper.text()).toContain('media.failedStatus')
  })
})
