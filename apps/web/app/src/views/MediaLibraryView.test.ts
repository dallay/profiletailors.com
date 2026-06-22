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

    expect(wrapper.text()).toContain('hero.jpg')
    expect(wrapper.text()).toContain('READY')
    expect(wrapper.text()).toContain('image/jpeg')
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

    const selectVisibleButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('media.selectAllVisible'))
    expect(selectVisibleButton).toBeTruthy()
    await selectVisibleButton?.trigger('click')

    expect(wrapper.text()).toContain('2 media.selectedCountSuffix')

    const deleteSelectedButtons = wrapper
      .findAll('button')
      .filter((button) => button.text().includes('media.deleteSelectedAction'))
    expect(deleteSelectedButtons.length).toBeGreaterThan(0)
    await deleteSelectedButtons[deleteSelectedButtons.length - 1]?.trigger('click')

    expect(deleteSpy).toHaveBeenCalledTimes(2)
    expect(deleteSpy).toHaveBeenCalledWith('asset-1')
    expect(deleteSpy).toHaveBeenCalledWith('asset-2')
  })
})
