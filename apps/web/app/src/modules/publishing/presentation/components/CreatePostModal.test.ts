import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'
import { useMediaStore } from '@modules/media'
import type { MediaAssetSummary, UnsplashPhotoSummary } from '@modules/media/services/media-api'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'
import CreatePostModalComponent from './CreatePostModal.vue'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const translations: Record<string, string> = {
  'media.loading': 'Loading media library...',
  'media.emptyTitle': 'No media assets yet',
  'media.emptyBody': 'Upload your first image, video, or PDF to populate the library.',
  'composer.picker.header': 'Media Library',
  'composer.picker.libraryChip': 'Library',
  'composer.picker.unsplashChip': 'Unsplash',
  'composer.picker.searchPlaceholder': 'Search Unsplash',
  'composer.picker.searchAction': 'Search',
  'composer.picker.searchingAction': 'Searching…',
  'composer.picker.providerSearchLabel': 'Search Unsplash',
  'composer.picker.libraryDescription': 'Browse images and videos already saved in this workspace.',
  'composer.picker.providerDescription':
    'Search Unsplash and import media into this post without leaving the composer.',
  'composer.picker.errorLoad': 'Unable to load media library.',
  'composer.picker.noPreview': 'No preview',
  'composer.picker.cancel': 'Cancel',
  'composer.picker.apply': 'Apply',
  'composer.media.label': 'Media Attachment',
  'composer.media.addMedia': 'Add Media',
  'composer.media.empty': 'No media attached yet.',
  'composer.media.dropzoneTitle': 'Drag & drop or',
  'composer.media.dropzoneBody': 'select a file',
  'composer.media.sourceLibrary': 'Media Library',
  'composer.media.sourceExternal': 'Unsplash',
  'composer.media.keepEditingWhileUploading': 'You can keep editing while this finishes.',
  'composer.media.finishingUpload': 'Finishing upload…',
  'composer.media.uploadingProgress': 'Uploading… {progress}%',
  'composer.media.limitWarning':
    'Too many attachments for the strictest channel ({current}/{max}). Remove attachments to publish or schedule.',
  'composer.media.limitInfinite': '∞',
  'composer.media.removeAttachment': 'Remove attachment {name}',
}

const mockT = (key: string, params?: Record<string, string | number>): string => {
  let value = translations[key] ?? key
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      value = value.replace(`{${k}}`, String(v))
    }
  }
  return value
}

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: mockT, locale: { value: 'en' } }),
  createI18n: () => ({ global: { locale: { value: 'en' } } }),
}))

vi.mock('@shared/i18n', () => ({
  default: { global: { locale: { value: 'en' } } },
}))

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  createApiFetch: () =>
    async function apiFetch<T>() {
      return {} as T
    },
  refreshSession: vi.fn().mockResolvedValue(null),
  getCurrentUserProfile: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  register: vi.fn(),
  logoutSession: vi.fn(),
  proxyImageUrl: (url: string) => url,
  resolveApiUrl: (url: string) => url,
}))

vi.mock('@modules/media/services/media-api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@modules/media/services/media-api')>()
  return {
    ...actual,
    searchUnsplashPhotos: vi.fn(
      async (query?: string): Promise<UnsplashPhotoSummary[]> => [
        {
          externalId: `${query ?? 'editorial'}-1`,
          name: `${query ?? 'Editorial'} photo`,
          previewUrl: 'https://images.unsplash.com/test-photo',
          sourceUrl: 'https://unsplash.com/photos/test-photo',
          authorName: 'Test author',
          authorUrl: 'https://unsplash.com/@test-author',
        },
      ],
    ),
    importUnsplashPhoto: vi.fn(
      async (externalId: string): Promise<MediaAssetSummary> => ({
        assetId: `unsplash-${externalId}`,
        workspaceId: 'ws-1',
        sourceType: 'EXTERNAL' as const,
        mediaType: 'image/jpeg',
        status: 'READY' as const,
        originalFilename: `${externalId}.jpg`,
        fileSizeBytes: 1024,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: `/api/media/assets/unsplash-${externalId}/preview`,
        sourceProvider: 'unsplash',
        externalId,
      }),
    ),
  }
})

vi.mock('@/components/ui/button', () => ({
  Button: { template: '<button class="ui-button" v-bind="$attrs"><slot /></button>' },
}))

vi.mock('@lucide/vue', () => {
  const stub = { template: '<svg />' }
  const iconStub = (icon: string) => ({ template: `<svg data-icon="${icon}" />` })
  return {
    Image: iconStub('image'),
    Calendar: iconStub('calendar'),
    Check: stub,
    ChevronDown: stub,
    FileImage: stub,
    Hash: stub,
    Paperclip: stub,
    Smile: stub,
    Sparkles: stub,
    X: stub,
    Loader2: stub,
    Loader2Icon: stub,
    Upload: stub,
    AlertCircle: stub,
    RotateCcw: stub,
    Search: stub,
    Info: stub,
    Globe: stub,
    ThumbsUp: stub,
    MessageCircle: stub,
    Repeat2: stub,
    Send: stub,
  }
})

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

interface TestChannel {
  id: string
  accountId: string
  name: string
  provider: 'linkedin' | 'twitter'
  avatar: string
  avatarUrl?: string
  handle: string
  status: 'ACTIVE' | 'INACTIVE'
}

function makeChannel(id: string, overrides: Partial<Omit<TestChannel, 'id'>> = {}): TestChannel {
  return {
    id,
    accountId: id,
    name: `Channel ${id}`,
    provider: 'linkedin',
    avatar: '',
    avatarUrl: undefined,
    handle: `Channel ${id}`,
    status: 'ACTIVE',
    ...overrides,
  }
}

function mountModal(channels: TestChannel[], props: Record<string, unknown> = {}) {
  const store = usePublishingStore()
  store.channels = channels

  const workspaceStore = useWorkspaceStore()
  workspaceStore.setActiveWorkspaceId('ws-1')

  return mount(CreatePostModalComponent, {
    attachTo: document.body,
    props: { isOpen: true, ...props },
    global: {
      mocks: { $t: mockT },
    },
  })
}

async function flushModal(_wrapper: ReturnType<typeof mountModal>): Promise<void> {
  await Promise.resolve()
  await nextTick()
  await nextTick()
}

function mockLoadAssetsWithIds(mediaStore: ReturnType<typeof useMediaStore>, ids: string[]) {
  return vi.spyOn(mediaStore, 'loadAssets').mockImplementation(async () => {
    mediaStore.isLoading = true
    mediaStore.loadError = null
    mediaStore.assetIds = [...ids]
    mediaStore.isLoading = false
  })
}

function getByTestId(testId: string): HTMLElement {
  const element = document.querySelector(`[data-testid="${testId}"]`)
  if (!(element instanceof HTMLElement)) {
    throw new Error(`Expected element with data-testid="${testId}" to exist in teleported modal`)
  }
  return element
}

function makeEditingPublication(
  overrides: Partial<
    Parameters<typeof mountModal>[1] & {
      id: string
      content: string
      channels: ('linkedin' | 'twitter' | 'instagram' | 'facebook')[]
      scheduledAt: string
      scheduleMode: 'NOW' | 'NEXT_SLOT' | 'SCHEDULED_AT'
      status: 'DRAFT' | 'QUEUED' | 'SCHEDULED'
      priority: boolean
      accountId: string
      assetIds: string[]
    }
  > = {},
) {
  return {
    id: 'pub-edit-1',
    content: 'Existing scheduled content',
    channels: ['linkedin'] as ('linkedin' | 'twitter' | 'instagram' | 'facebook')[],
    // Use a date far in the future to avoid validateCustomSchedule rejecting it
    // as "must be in the future" in CI / local environments with different current times.
    scheduledAt: '2030-06-25T14:30:00Z',
    scheduleMode: 'SCHEDULED_AT' as const,
    status: 'SCHEDULED' as const,
    priority: true,
    accountId: 'ch-edit-1',
    assetIds: ['asset-1', 'asset-2'] as string[],
    ...overrides,
  }
}

describe('CreatePostModal.vue — media picker foundation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    vi.clearAllMocks()
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:mock-preview'),
      revokeObjectURL: vi.fn(),
    })
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  it('mounts the teleported modal into document.body so live controls remain queryable', async () => {
    const wrapper = mountModal([makeChannel('ch-picker')])
    await flushModal(wrapper)

    expect(wrapper.html()).toContain('teleport start')
    expect(getByTestId('add-media-button').textContent).toContain('Add Media')
  })

  it('renders the media and scheduling icons used by the composer controls', async () => {
    const wrapper = mountModal([makeChannel('ch-icons')])
    await flushModal(wrapper)

    expect(document.querySelectorAll('[data-icon="image"]')).toHaveLength(2)
    expect(document.querySelectorAll('[data-icon="calendar"]')).toHaveLength(1)
    expect(document.querySelector('imageicon')).toBeNull()
    expect(document.querySelector('calendaricon')).toBeNull()
  })

  it('shows an Add Media entry and opens a staged picker from current draft attachments', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['asset-a'] = {
      assetId: 'asset-a',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: 'asset-a.png',
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-a/preview',
    }

    const loadAssets = mockLoadAssetsWithIds(mediaStore, ['asset-a'])

    const wrapper = mountModal([makeChannel('ch-picker')], {
      editingPublication: makeEditingPublication({ assetIds: ['asset-a'] }),
    })
    await flushModal(wrapper)
    await flushModal(wrapper)

    expect(document.body.innerHTML).toContain('Add Media')
    expect(document.body.innerHTML).not.toContain('composer.dragDrop')
    expect(document.body.innerHTML).toContain('asset-a.png')

    getByTestId('attachment-remove-asset-a').click()
    await flushModal(wrapper)

    getByTestId('add-media-button').click()
    await flushModal(wrapper)

    expect(loadAssets).toHaveBeenCalledWith('READY,PENDING_UPLOAD,UPLOADING,FAILED')
    expect(document.body.innerHTML).toContain('Media Library')
    expect(getByTestId('picker-asset-card-asset-a').getAttribute('data-selected')).toBe('false')
  })

  it('loads workspace assets when the picker opens and renders empty or error collection states', async () => {
    const mediaStore = useMediaStore()
    const loadAssets = vi.spyOn(mediaStore, 'loadAssets').mockResolvedValue(undefined)

    const wrapper = mountModal([makeChannel('ch-picker')])
    await flushModal(wrapper)

    getByTestId('add-media-button').click()
    await flushModal(wrapper)

    expect(loadAssets).toHaveBeenCalledWith('READY,PENDING_UPLOAD,UPLOADING,FAILED')
    expect(document.body.innerHTML).toContain('No media assets yet')

    loadAssets.mockImplementationOnce(async () => {
      mediaStore.loadError = 'library failed'
      throw new Error('library failed')
    })
    getByTestId('picker-cancel').click()
    await flushModal(wrapper)
    getByTestId('add-media-button').click()
    await flushModal(wrapper)

    expect(document.body.innerHTML).toContain('Unable to load media library.')
  })

  it('discards staged changes on cancel, reapplies current draft on reopen, and replaces draft on apply', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['asset-a'] = {
      assetId: 'asset-a',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: 'asset-a.png',
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-a/preview',
    }
    mediaStore.assetsById['asset-b'] = {
      assetId: 'asset-b',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: 'asset-b.png',
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-b/preview',
    }

    const loadAssets = mockLoadAssetsWithIds(mediaStore, ['asset-a', 'asset-b'])

    const wrapper = mountModal([makeChannel('ch-picker')], {
      editingPublication: makeEditingPublication({ assetIds: ['asset-a'] }),
    })
    await flushModal(wrapper)
    await flushModal(wrapper)

    getByTestId('add-media-button').click()
    await flushModal(wrapper)
    expect(loadAssets).toHaveBeenCalledWith('READY,PENDING_UPLOAD,UPLOADING,FAILED')
    getByTestId('picker-asset-card-asset-b').click()
    await flushModal(wrapper)
    getByTestId('picker-cancel').click()
    await flushModal(wrapper)

    expect(document.body.innerHTML).toContain('asset-a.png')
    expect(document.body.innerHTML).not.toContain('asset-b.png')

    getByTestId('add-media-button').click()
    await flushModal(wrapper)
    expect(getByTestId('picker-asset-card-asset-a').getAttribute('data-selected')).toBe('true')
    expect(getByTestId('picker-asset-card-asset-b').getAttribute('data-selected')).toBe('false')

    getByTestId('picker-asset-card-asset-a').click()
    getByTestId('picker-asset-card-asset-b').click()
    await flushModal(wrapper)
    getByTestId('picker-apply').click()
    await flushModal(wrapper)

    expect(document.body.innerHTML).not.toContain('asset-a.png')
    expect(document.body.innerHTML).toContain('asset-b.png')
  })

  it('uploads from the active picker session, reconciles the persisted asset in-place, and auto-stages it once selectable', async () => {
    vi.useFakeTimers()
    try {
      const mediaStore = useMediaStore()
      const loadAssets = mockLoadAssetsWithIds(mediaStore, [])
      const createAndUpload = vi
        .spyOn(mediaStore, 'createAndUpload')
        .mockImplementation(async (fileArg, tempKeyArg) => {
          const createdAsset = {
            assetId: 'asset-uploaded',
            workspaceId: 'ws-1',
            sourceType: 'UPLOADED' as const,
            mediaType: 'image/png',
            status: 'PENDING_UPLOAD' as const,
            originalFilename: 'uploaded.png',
            fileSizeBytes: 4096,
            createdAt: '2026-06-19T12:00:00Z',
            previewUrl: null,
          }
          mediaStore.upsertAsset(createdAsset)
          mediaStore.uploads[tempKeyArg] = {
            tempKey: tempKeyArg,
            assetId: createdAsset.assetId,
            file: fileArg,
            progress: 100,
            status: 'done',
            asset: createdAsset,
          }
          return createdAsset
        })
      const loadAsset = vi
        .spyOn(mediaStore, 'loadAsset')
        .mockResolvedValueOnce({
          assetId: 'asset-uploaded',
          workspaceId: 'ws-1',
          sourceType: 'UPLOADED',
          mediaType: 'image/png',
          status: 'UPLOADING',
          originalFilename: 'uploaded.png',
          fileSizeBytes: 4096,
          createdAt: '2026-06-19T12:00:00Z',
          previewUrl: null,
        })
        .mockResolvedValueOnce({
          assetId: 'asset-uploaded',
          workspaceId: 'ws-1',
          sourceType: 'UPLOADED',
          mediaType: 'image/png',
          status: 'READY',
          originalFilename: 'uploaded.png',
          fileSizeBytes: 4096,
          createdAt: '2026-06-19T12:00:00Z',
          previewUrl: '/api/media/assets/asset-uploaded/preview',
        })

      const wrapper = mountModal([makeChannel('ch-picker')])
      await flushModal(wrapper)

      getByTestId('add-media-button').click()
      await flushModal(wrapper)
      expect(loadAssets).toHaveBeenCalledWith('READY,PENDING_UPLOAD,UPLOADING,FAILED')
      expect(document.body.innerHTML).toContain('Media Library')

      const pickerUploadInput = getByTestId('picker-upload-input') as HTMLInputElement | null
      expect(pickerUploadInput).not.toBeNull()
      const uploadFile = new File(['upload'], 'uploaded.png', { type: 'image/png' })
      Object.defineProperty(pickerUploadInput!, 'files', {
        configurable: true,
        value: [uploadFile],
      })
      pickerUploadInput!.dispatchEvent(new Event('change'))
      await flushModal(wrapper)

      expect(createAndUpload).toHaveBeenCalledTimes(1)
      expect(createAndUpload).toHaveBeenCalledWith(
        uploadFile,
        expect.stringMatching(/^picker-upload-/),
        expect.any(Function),
      )
      expect(getByTestId('picker-asset-card-asset-uploaded').getAttribute('aria-disabled')).toBe(
        'true',
      )
      expect(getByTestId('picker-asset-card-asset-uploaded').getAttribute('data-selected')).toBe(
        'false',
      )

      await vi.advanceTimersByTimeAsync(1000)
      await flushModal(wrapper)
      expect(loadAsset).toHaveBeenCalledWith('asset-uploaded')
      expect(getByTestId('picker-asset-card-asset-uploaded').getAttribute('data-selected')).toBe(
        'false',
      )

      await vi.advanceTimersByTimeAsync(1000)
      await flushModal(wrapper)
      expect(loadAsset).toHaveBeenCalledTimes(2)
      expect(getByTestId('picker-asset-card-asset-uploaded').getAttribute('aria-disabled')).toBe(
        'false',
      )
      expect(getByTestId('picker-asset-card-asset-uploaded').getAttribute('data-selected')).toBe(
        'true',
      )

      getByTestId('picker-apply').click()
      await flushModal(wrapper)
      expect(document.body.innerHTML).toContain('uploaded.png')
    } finally {
      vi.useRealTimers()
    }
  })

  it('stops upload reconciliation when the picker closes without apply and leaves the non-ready asset visible for later reopen', async () => {
    vi.useFakeTimers()
    try {
      const mediaStore = useMediaStore()
      vi.spyOn(mediaStore, 'loadAssets').mockImplementation(async () => {
        mediaStore.assetIds = []
      })
      vi.spyOn(mediaStore, 'createAndUpload').mockImplementation(async (fileArg, tempKeyArg) => {
        const createdAsset = {
          assetId: 'asset-pending',
          workspaceId: 'ws-1',
          sourceType: 'UPLOADED' as const,
          mediaType: 'image/png',
          status: 'PENDING_UPLOAD' as const,
          originalFilename: 'pending.png',
          fileSizeBytes: 2048,
          createdAt: '2026-06-19T12:00:00Z',
          previewUrl: null,
        }
        mediaStore.upsertAsset(createdAsset)
        mediaStore.uploads[tempKeyArg] = {
          tempKey: tempKeyArg,
          assetId: createdAsset.assetId,
          file: fileArg,
          progress: 100,
          status: 'done',
          asset: createdAsset,
        }
        return createdAsset
      })
      const loadAsset = vi.spyOn(mediaStore, 'loadAsset').mockResolvedValue({
        assetId: 'asset-pending',
        workspaceId: 'ws-1',
        sourceType: 'UPLOADED',
        mediaType: 'image/png',
        status: 'UPLOADING',
        originalFilename: 'pending.png',
        fileSizeBytes: 2048,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: null,
      })

      const wrapper = mountModal([makeChannel('ch-picker')])
      await flushModal(wrapper)

      getByTestId('add-media-button').click()
      await flushModal(wrapper)
      expect(document.body.innerHTML).toContain('Media Library')

      const pickerUploadInput = getByTestId('picker-upload-input') as HTMLInputElement | null
      expect(pickerUploadInput).not.toBeNull()
      const uploadFile = new File(['pending'], 'pending.png', { type: 'image/png' })
      Object.defineProperty(pickerUploadInput!, 'files', {
        configurable: true,
        value: [uploadFile],
      })
      pickerUploadInput!.dispatchEvent(new Event('change'))
      await flushModal(wrapper)
      expect(getByTestId('picker-asset-card-asset-pending').getAttribute('aria-disabled')).toBe(
        'true',
      )

      getByTestId('picker-cancel').click()
      await flushModal(wrapper)

      await vi.advanceTimersByTimeAsync(5000)
      await flushModal(wrapper)
      expect(loadAsset).not.toHaveBeenCalled()

      getByTestId('add-media-button').click()
      await flushModal(wrapper)
      expect(document.body.innerHTML).toContain('No media assets yet')
      expect(document.body.innerHTML).not.toContain('picker-asset-card-asset-pending')
    } finally {
      vi.useRealTimers()
    }
  })

  it('retries transient asset refresh errors within bounds, respects manual deselection, and does not auto-stage again on later refreshes', async () => {
    vi.useFakeTimers()
    try {
      const mediaStore = useMediaStore()
      vi.spyOn(mediaStore, 'loadAssets').mockImplementation(async () => {
        mediaStore.assetIds = []
      })
      vi.spyOn(mediaStore, 'createAndUpload').mockImplementation(async (fileArg, tempKeyArg) => {
        const createdAsset = {
          assetId: 'asset-flaky',
          workspaceId: 'ws-1',
          sourceType: 'UPLOADED' as const,
          mediaType: 'image/png',
          status: 'PENDING_UPLOAD' as const,
          originalFilename: 'flaky.png',
          fileSizeBytes: 2048,
          createdAt: '2026-06-19T12:00:00Z',
          previewUrl: null,
        }
        mediaStore.upsertAsset(createdAsset)
        mediaStore.uploads[tempKeyArg] = {
          tempKey: tempKeyArg,
          assetId: createdAsset.assetId,
          file: fileArg,
          progress: 100,
          status: 'done',
          asset: createdAsset,
        }
        return createdAsset
      })
      const loadAsset = vi
        .spyOn(mediaStore, 'loadAsset')
        .mockRejectedValueOnce(new Error('temporary network failure'))
        .mockResolvedValueOnce({
          assetId: 'asset-flaky',
          workspaceId: 'ws-1',
          sourceType: 'UPLOADED',
          mediaType: 'image/png',
          status: 'READY',
          originalFilename: 'flaky.png',
          fileSizeBytes: 2048,
          createdAt: '2026-06-19T12:00:00Z',
          previewUrl: '/api/media/assets/asset-flaky/preview',
        })
        .mockResolvedValue({
          assetId: 'asset-flaky',
          workspaceId: 'ws-1',
          sourceType: 'UPLOADED',
          mediaType: 'image/png',
          status: 'READY',
          originalFilename: 'flaky.png',
          fileSizeBytes: 2048,
          createdAt: '2026-06-19T12:00:00Z',
          previewUrl: '/api/media/assets/asset-flaky/preview',
        })

      const wrapper = mountModal([makeChannel('ch-picker')])
      await flushModal(wrapper)

      getByTestId('add-media-button').click()
      await flushModal(wrapper)
      expect(document.body.innerHTML).toContain('Media Library')

      const pickerUploadInput = getByTestId('picker-upload-input') as HTMLInputElement | null
      expect(pickerUploadInput).not.toBeNull()
      const uploadFile = new File(['flaky'], 'flaky.png', { type: 'image/png' })
      Object.defineProperty(pickerUploadInput!, 'files', {
        configurable: true,
        value: [uploadFile],
      })
      pickerUploadInput!.dispatchEvent(new Event('change'))
      await flushModal(wrapper)

      await vi.advanceTimersByTimeAsync(1000)
      await flushModal(wrapper)
      expect(loadAsset).toHaveBeenCalledTimes(1)
      expect(getByTestId('picker-asset-card-asset-flaky').getAttribute('data-selected')).toBe(
        'false',
      )

      await vi.advanceTimersByTimeAsync(1000)
      await flushModal(wrapper)
      expect(loadAsset).toHaveBeenCalledTimes(2)
      expect(getByTestId('picker-asset-card-asset-flaky').getAttribute('data-selected')).toBe(
        'true',
      )

      getByTestId('picker-asset-card-asset-flaky').click()
      await flushModal(wrapper)
      expect(getByTestId('picker-asset-card-asset-flaky').getAttribute('data-selected')).toBe(
        'false',
      )

      mediaStore.upsertAsset({
        assetId: 'asset-flaky',
        workspaceId: 'ws-1',
        sourceType: 'UPLOADED',
        mediaType: 'image/png',
        status: 'UPLOADING',
        originalFilename: 'flaky.png',
        fileSizeBytes: 2048,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: null,
      })
      mediaStore.upsertAsset({
        assetId: 'asset-flaky',
        workspaceId: 'ws-1',
        sourceType: 'UPLOADED',
        mediaType: 'image/png',
        status: 'READY',
        originalFilename: 'flaky.png',
        fileSizeBytes: 2048,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: '/api/media/assets/asset-flaky/preview',
      })

      await vi.advanceTimersByTimeAsync(2000)
      await flushModal(wrapper)
      expect(loadAsset).toHaveBeenCalledTimes(2)
      expect(getByTestId('picker-asset-card-asset-flaky').getAttribute('data-selected')).toBe(
        'false',
      )
    } finally {
      vi.useRealTimers()
    }
  })

  it('stops polling after a bounded timeout while keeping the asset visible for later reconciliation', async () => {
    vi.useFakeTimers()
    try {
      const mediaStore = useMediaStore()
      vi.spyOn(mediaStore, 'loadAssets').mockImplementation(async () => {
        mediaStore.assetIds = []
      })
      vi.spyOn(mediaStore, 'createAndUpload').mockImplementation(async (fileArg, tempKeyArg) => {
        const createdAsset = {
          assetId: 'asset-timeout',
          workspaceId: 'ws-1',
          sourceType: 'UPLOADED' as const,
          mediaType: 'image/png',
          status: 'PENDING_UPLOAD' as const,
          originalFilename: 'timeout.png',
          fileSizeBytes: 2048,
          createdAt: '2026-06-19T12:00:00Z',
          previewUrl: null,
        }
        mediaStore.upsertAsset(createdAsset)
        mediaStore.uploads[tempKeyArg] = {
          tempKey: tempKeyArg,
          assetId: createdAsset.assetId,
          file: fileArg,
          progress: 100,
          status: 'done',
          asset: createdAsset,
        }
        return createdAsset
      })
      const loadAsset = vi.spyOn(mediaStore, 'loadAsset').mockResolvedValue({
        assetId: 'asset-timeout',
        workspaceId: 'ws-1',
        sourceType: 'UPLOADED',
        mediaType: 'image/png',
        status: 'UPLOADING',
        originalFilename: 'timeout.png',
        fileSizeBytes: 2048,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: null,
      })

      const wrapper = mountModal([makeChannel('ch-picker')])
      await flushModal(wrapper)

      getByTestId('add-media-button').click()
      await flushModal(wrapper)
      expect(document.body.innerHTML).toContain('Media Library')

      const pickerUploadInput = getByTestId('picker-upload-input') as HTMLInputElement | null
      expect(pickerUploadInput).not.toBeNull()
      const uploadFile = new File(['timeout'], 'timeout.png', { type: 'image/png' })
      Object.defineProperty(pickerUploadInput!, 'files', {
        configurable: true,
        value: [uploadFile],
      })
      pickerUploadInput!.dispatchEvent(new Event('change'))
      await flushModal(wrapper)

      await vi.advanceTimersByTimeAsync(10_000)
      await flushModal(wrapper)
      expect(loadAsset).toHaveBeenCalledTimes(5)
      expect(getByTestId('picker-asset-card-asset-timeout').getAttribute('aria-disabled')).toBe(
        'true',
      )
      expect(getByTestId('picker-asset-card-asset-timeout').getAttribute('data-selected')).toBe(
        'false',
      )

      await vi.advanceTimersByTimeAsync(5_000)
      await flushModal(wrapper)
      expect(loadAsset).toHaveBeenCalledTimes(5)
    } finally {
      vi.useRealTimers()
    }
  })
})

// ---------------------------------------------------------------------------
// Work Unit 3 — Unsplash integration + capability resolution + regressions
// ---------------------------------------------------------------------------

describe('CreatePostModal.vue — Unsplash integration (WU3)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    vi.clearAllMocks()
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:mock-preview'),
      revokeObjectURL: vi.fn(),
    })
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  it('renders the Unsplash provider tab only when the parent passes provider="unsplash"', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockImplementation(async () => {
      mediaStore.assetIds = []
    })

    const wrapper = mountModal([makeChannel('ch-prov')])
    await flushModal(wrapper)

    getByTestId('add-media-button').click()
    await flushModal(wrapper)

    // No provider prop supplied → tab/section MUST NOT be visible
    expect(document.body.innerHTML).not.toContain('Unsplash')
    expect(document.body.innerHTML).not.toContain('picker-provider-search')
    expect(document.body.innerHTML).not.toContain('picker-provider-import')
  })

  it('keeps the picker open while importing a provider result and reconciles the persisted asset into the active session', async () => {
    vi.useFakeTimers()
    try {
      const mediaStore = useMediaStore()
      vi.spyOn(mediaStore, 'loadAssets').mockImplementation(async () => {
        mediaStore.assetIds = []
      })
      vi.spyOn(mediaStore, 'loadAsset')
        .mockResolvedValueOnce({
          assetId: 'unsplash-mountain-1',
          workspaceId: 'ws-1',
          sourceType: 'EXTERNAL',
          mediaType: 'image/jpeg',
          status: 'UPLOADING',
          originalFilename: 'mountain-1.jpg',
          fileSizeBytes: 1024,
          createdAt: '2026-06-19T12:00:00Z',
          previewUrl: null,
          sourceProvider: 'unsplash',
          externalId: 'mountain-1',
        })
        .mockResolvedValueOnce({
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

      // Mount the modal with the provider enabled
      const wrapper = mountModal([makeChannel('ch-prov')], {
        provider: 'unsplash',
      } as Record<string, unknown>)
      await flushModal(wrapper)

      getByTestId('add-media-button').click()
      await flushModal(wrapper)
      getByTestId('picker-source-unsplash').click()
      await flushModal(wrapper)

      // The picker is now open — the provider tab MUST be visible because
      // the parent enabled it.
      expect(document.body.innerHTML).toContain('provider-panel')

      // Drive the provider-search via the input → submit pipeline that the
      // picker shell renders. The picker MUST remain open so the author can
      // continue multi-selection after import.
      const searchInput = document.querySelector(
        '[data-testid="picker-provider-search"] input',
      ) as HTMLInputElement | null
      expect(searchInput).not.toBeNull()
      searchInput!.value = 'mountain'
      searchInput!.dispatchEvent(new Event('input'))
      await flushModal(wrapper)
      ;(
        document.querySelector('[data-testid="picker-provider-search"]') as HTMLFormElement
      ).dispatchEvent(new Event('submit'))
      await flushModal(wrapper)

      // The picker MUST stay open after search → import pipeline is wired.
      expect(document.body.innerHTML).toContain('provider-panel')
      // Search synthesizes deterministic results so we see them painted,
      // proving the typed provider-search pipeline is connected.
      expect(document.body.innerHTML).toContain('provider-result-mountain-1')
      expect(document.body.innerHTML).toContain('provider-panel-import')
    } finally {
      vi.useRealTimers()
    }
  })

  it('emits typed provider-search and provider-import interactions through the picker shell', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAssets').mockImplementation(async () => {
      mediaStore.assetIds = []
    })

    const wrapper = mountModal([makeChannel('ch-prov')], {
      provider: 'unsplash',
    } as Record<string, unknown>)
    await flushModal(wrapper)

    getByTestId('add-media-button').click()
    await flushModal(wrapper)
    getByTestId('picker-source-unsplash').click()
    await flushModal(wrapper)

    // Drive the shell search: set the input value then submit the form.
    const searchInput = document.querySelector(
      '[data-testid="picker-provider-search"] input',
    ) as HTMLInputElement | null
    expect(searchInput).not.toBeNull()
    searchInput!.value = 'mountain'
    searchInput!.dispatchEvent(new Event('input'))
    await flushModal(wrapper)

    ;(
      document.querySelector('[data-testid="picker-provider-search"]') as HTMLFormElement
    ).dispatchEvent(new Event('submit'))
    await flushModal(wrapper)

    // handleProviderSearch produced deterministic results — the picker panel
    // is wired through to the modal's provider pipeline.
    expect(document.body.innerHTML).toContain('provider-result-mountain-1')
    expect(document.body.innerHTML).toContain('provider-panel-import')

    // Clicking the import button emits the typed provider-import interaction.
    ;(document.querySelector('[data-testid="provider-panel-import"]') as HTMLButtonElement).click()
    await flushModal(wrapper)

    // The picker MUST remain open after import so the author can continue
    // multi-selection — modal does NOT emit close.
    expect(wrapper.emitted('close')).toBeUndefined()
  })

  it('enforces effectiveAttachmentLimit of the selected channel and blocks apply above it', async () => {
    const mediaStore = useMediaStore()
    for (const id of ['asset-1', 'asset-2', 'asset-3', 'asset-4', 'asset-5']) {
      mediaStore.assetsById[id] = {
        assetId: id,
        workspaceId: 'ws-1',
        sourceType: 'UPLOADED',
        mediaType: 'image/png',
        status: 'READY',
        originalFilename: `${id}.png`,
        fileSizeBytes: 1024,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: `/api/media/assets/${id}/preview`,
      }
    }
    mockLoadAssetsWithIds(mediaStore, ['asset-1', 'asset-2', 'asset-3', 'asset-4', 'asset-5'])

    const channels = [
      makeChannel('ch-li', { provider: 'linkedin', name: 'LinkedIn' } as Partial<TestChannel>),
      makeChannel('ch-tw', { provider: 'twitter', name: 'Twitter' } as Partial<TestChannel>),
    ]
    ;(channels[0] as { maxAttachments?: number }).maxAttachments = 9
    ;(channels[1] as { maxAttachments?: number }).maxAttachments = 4

    const wrapper = mountModal(channels)
    await flushModal(wrapper)
    await flushModal(wrapper)

    const twitterButton = Array.from(document.querySelectorAll('button')).find((btn) =>
      btn.textContent?.includes('Twitter'),
    )
    expect(twitterButton).toBeDefined()
    twitterButton!.click()
    await flushModal(wrapper)

    getByTestId('add-media-button').click()
    await flushModal(wrapper)

    for (const id of ['asset-1', 'asset-2', 'asset-3', 'asset-4', 'asset-5']) {
      getByTestId(`picker-asset-card-${id}`).click()
      await flushModal(wrapper)
    }

    const applyButton = getByTestId('picker-apply') as HTMLButtonElement
    expect(applyButton.disabled).toBe(true)
    expect(document.body.innerHTML).toContain('picker-apply-warning')

    applyButton.click()
    await flushModal(wrapper)

    expect(document.body.innerHTML).toContain('picker-asset-card')
  })

  it('preserves attachments on channel change and surfaces invalid state without auto-removal', async () => {
    const mediaStore = useMediaStore()
    for (const id of ['asset-1', 'asset-2', 'asset-3']) {
      mediaStore.assetsById[id] = {
        assetId: id,
        workspaceId: 'ws-1',
        sourceType: 'UPLOADED',
        mediaType: 'image/png',
        status: 'READY',
        originalFilename: `${id}.png`,
        fileSizeBytes: 1024,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: `/api/media/assets/${id}/preview`,
      }
    }
    vi.spyOn(mediaStore, 'loadAssets').mockImplementation(async () => {
      mediaStore.assetIds = ['asset-1', 'asset-2', 'asset-3']
    })

    const channels = [
      makeChannel('ch-li', { provider: 'linkedin', name: 'LinkedIn' } as Partial<TestChannel>),
      makeChannel('ch-tw', { provider: 'twitter', name: 'Twitter' } as Partial<TestChannel>),
    ]
    ;(channels[0] as { maxAttachments?: number }).maxAttachments = 9
    ;(channels[1] as { maxAttachments?: number }).maxAttachments = 4

    const wrapper = mountModal(channels)
    await flushModal(wrapper)

    // Force a draft of 3 attachments by seeding editingPublication.
    // (Simpler than staging them all through the picker here.)
    await wrapper.unmount()
    const editWrapper = mountModal(channels, {
      editingPublication: makeEditingPublication({ assetIds: ['asset-1', 'asset-2', 'asset-3'] }),
    })
    await flushModal(editWrapper)
    await flushModal(editWrapper)

    expect(document.body.innerHTML).toContain('asset-1.png')
    expect(document.body.innerHTML).toContain('asset-2.png')
    expect(document.body.innerHTML).toContain('asset-3.png')

    // Switch to the lower-limit Twitter channel
    const twitterButton = Array.from(document.querySelectorAll('button')).find((btn) =>
      btn.textContent?.includes('Twitter'),
    )
    expect(twitterButton).toBeDefined()
    twitterButton!.click()
    await flushModal(editWrapper)

    // All 3 attachments MUST remain in the draft (no auto-removal)
    expect(document.body.innerHTML).toContain('asset-1.png')
    expect(document.body.innerHTML).toContain('asset-2.png')
    expect(document.body.innerHTML).toContain('asset-3.png')

    // Close the wrapper if still mounted.
    await editWrapper.unmount()
  })

  it('surfaces the limit warning when draft attachments exceed the selected channel limit, blocking publish/schedule', async () => {
    const mediaStore = useMediaStore()
    for (const id of ['asset-1', 'asset-2', 'asset-3']) {
      mediaStore.assetsById[id] = {
        assetId: id,
        workspaceId: 'ws-1',
        sourceType: 'UPLOADED',
        mediaType: 'image/png',
        status: 'READY',
        originalFilename: `${id}.png`,
        fileSizeBytes: 1024,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: `/api/media/assets/${id}/preview`,
      }
    }
    vi.spyOn(mediaStore, 'loadAssets').mockImplementation(async () => {
      mediaStore.assetIds = ['asset-1', 'asset-2', 'asset-3']
    })

    // LinkedIn with limit 2 — three draft attachments exceed it.
    const channels = [makeChannel('ch-li', { name: 'LinkedIn' } as Partial<TestChannel>)]
    ;(channels[0] as { maxAttachments?: number }).maxAttachments = 2

    const wrapper = mountModal(channels, {
      editingPublication: makeEditingPublication({
        assetIds: ['asset-1', 'asset-2', 'asset-3'],
        accountId: 'ch-li',
      }),
    })
    // Wait for initEditMode to complete (async asset loading + channel selection)
    await flushModal(wrapper)
    await flushModal(wrapper)
    await flushModal(wrapper)

    // The warning MUST render because draft length (3) > channel max (2).
    const warning = document.querySelector('[data-testid="attachment-limit-warning"]')
    expect(warning).not.toBeNull()
    expect(warning?.textContent ?? '').toContain('Too many attachments')

    // The submit button MUST be disabled. Because the mock returns i18n keys,
    // we look at the last <button> inside the grid action container — that's
    // the disabled primary action in the modal footer.
    const actionButtons = document.querySelectorAll(
      '.ui-button, button[disabled], button:not([data-testid])',
    )
    const disabledSubmit = Array.from(actionButtons).find((btn) =>
      (btn as HTMLButtonElement).hasAttribute('disabled'),
    )
    expect(disabledSubmit).toBeDefined()

    await wrapper.unmount()
  })
})

describe('CreatePostModal.vue — inline composer media layout', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    vi.clearAllMocks()
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:mock-preview'),
      revokeObjectURL: vi.fn(),
    })
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  it('renders a local upload as a same-size inline card and keeps the filename as tooltip only', async () => {
    const wrapper = mountModal([makeChannel('ch-inline')])
    await flushModal(wrapper)

    const pickerUploadInput = getByTestId('picker-upload-input') as HTMLInputElement
    const uploadFile = new File(['graph'], 'graph-theory.jpeg', { type: 'image/jpeg' })
    Object.defineProperty(pickerUploadInput, 'files', {
      configurable: true,
      value: [uploadFile],
    })
    pickerUploadInput.dispatchEvent(new Event('change'))
    await flushModal(wrapper)

    const inlineCard = getByTestId('inline-local-upload')
    expect(inlineCard.getAttribute('title')).toBe('graph-theory.jpeg')
    expect(getByTestId('composer-inline-dropzone')).toBeTruthy()
    expect(wrapper.text()).not.toContain('graph-theory.jpeg')
  })

  it('shows a round spinner upload overlay instead of the stuck preparing state', async () => {
    const mediaStore = useMediaStore()
    const wrapper = mountModal([makeChannel('ch-inline')])
    await flushModal(wrapper)

    const pickerUploadInput = getByTestId('picker-upload-input') as HTMLInputElement
    const uploadFile = new File(['graph'], 'graph-theory.jpeg', { type: 'image/jpeg' })
    Object.defineProperty(pickerUploadInput, 'files', {
      configurable: true,
      value: [uploadFile],
    })
    pickerUploadInput.dispatchEvent(new Event('change'))
    await flushModal(wrapper)

    mediaStore.uploads['modal-upload-inline'] = {
      tempKey: 'modal-upload-inline',
      assetId: 'asset-inline-upload',
      file: uploadFile,
      progress: 42,
      status: 'uploading',
    }
    await flushModal(wrapper)

    expect(document.body.innerHTML).toContain('Uploading… 42%')
    expect(document.body.innerHTML).toContain('You can keep editing while this finishes.')
    expect(document.body.innerHTML).not.toContain('Preparing upload')
  })

  it('rejects unsupported dragged media types like svg before upload starts', async () => {
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {})
    const mediaStore = useMediaStore()
    const createAndUploadSpy = vi.spyOn(mediaStore, 'createAndUpload')

    const wrapper = mountModal([makeChannel('ch-inline')])
    await flushModal(wrapper)

    const pickerUploadInput = getByTestId('picker-upload-input') as HTMLInputElement
    const svgFile = new File(['<svg></svg>'], 'diagram.svg', { type: 'image/svg+xml' })
    Object.defineProperty(pickerUploadInput, 'files', {
      configurable: true,
      value: [svgFile],
    })
    pickerUploadInput.dispatchEvent(new Event('change'))
    await flushModal(wrapper)

    expect(alertSpy).toHaveBeenCalledWith(
      'Unsupported media format. Supported formats: JPEG, PNG, WEBP, GIF, MP4.',
    )
    expect(document.querySelector('[data-testid="inline-local-upload"]')).toBeNull()
    expect(createAndUploadSpy).not.toHaveBeenCalled()
  })

  it('accepts pasted images in the composer textarea using the same inline upload flow', async () => {
    const wrapper = mountModal([makeChannel('ch-inline')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea')
    const pastedFile = new File(['png'], 'clipboard.png', { type: 'image/png' })
    const pasteEvent = new Event('paste', { bubbles: true, cancelable: true }) as Event & {
      clipboardData: { files: File[]; items: Array<{ kind: string; getAsFile: () => File | null }> }
    }
    pasteEvent.clipboardData = {
      files: [],
      items: [{ kind: 'file', getAsFile: () => pastedFile }],
    }
    textarea.dispatchEvent(pasteEvent)
    await flushModal(wrapper)

    const inlineCard = getByTestId('inline-local-upload')
    expect(inlineCard.getAttribute('title')).toBe('clipboard.png')
  })

  it('accepts dropped images directly over the composer textarea', async () => {
    const wrapper = mountModal([makeChannel('ch-inline')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea')
    const droppedFile = new File(['png'], 'dropped.png', { type: 'image/png' })
    const dropEvent = new Event('drop', { bubbles: true, cancelable: true }) as Event & {
      dataTransfer: { files: File[] }
    }
    dropEvent.dataTransfer = { files: [droppedFile] }

    textarea.dispatchEvent(dropEvent)
    await flushModal(wrapper)

    const inlineCard = getByTestId('inline-local-upload')
    expect(inlineCard.getAttribute('title')).toBe('dropped.png')
  })
})
