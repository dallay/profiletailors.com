import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { usePublishingStore } from '@/stores/publishing'
import { useMediaStore } from '@/stores/media'
import CreatePostModalComponent from './CreatePostModal.vue'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const mockT = (key: string) => key

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: mockT, locale: { value: 'en' } }),
  createI18n: () => ({ global: { locale: { value: 'en' } } }),
}))

vi.mock('@/i18n', () => ({
  default: { global: { locale: { value: 'en' } } },
}))

vi.mock('@/lib/auth-api', () => ({
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

vi.mock('@/components/ui/button', () => ({
  Button: { template: '<button class="ui-button"><slot /></button>' },
}))

vi.mock('@lucide/vue', () => {
  const stub = { template: '<svg />' }
  return {
    Image: stub,
    Calendar: stub,
    Check: stub,
    ChevronDown: stub,
    FileImage: stub,
    Hash: stub,
    Paperclip: stub,
    Smile: stub,
    Sparkles: stub,
    X: stub,
    Loader2: stub,
    Upload: stub,
    AlertCircle: stub,
    RotateCcw: stub,
  }
})

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

interface TestChannel {
  id: string
  accountId: string
  name: string
  provider: 'linkedin'
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

function mountModal(channels: TestChannel[]) {
  const store = usePublishingStore()
  store.channels = channels

  return mount(CreatePostModalComponent, {
    props: { isOpen: true },
    global: {
      mocks: { $t: mockT },
    },
  })
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('CreatePostModal.vue — avatar rendering', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders <img> when channel has a valid avatarUrl', () => {
    const wrapper = mountModal([
      makeChannel('ch-1', { avatarUrl: 'https://example.com/avatar.jpg' }),
    ])

    // Teleported content is rendered into document.body
    const body = document.body.innerHTML
    expect(body).toContain('https://example.com/avatar.jpg')

    // Also check via wrapper.html() which includes teleported content
    const allHtml = wrapper.html() + body
    expect(allHtml).toContain('Channel ch-1 avatar')
  })

  it('renders fallback badge when avatarUrl is null/undefined', () => {
    mountModal([makeChannel('ch-2', { avatarUrl: undefined })])

    const body = document.body.innerHTML
    // For a linkedin channel, the fallback badge shows "in"
    expect(body).toContain('in')

    // No img with a src attribute in teleported content
    expect(body).not.toContain('src="undefined"')
  })

  it('shows fallback badge when avatar image fails to load', async () => {
    const wrapper = mountModal([
      makeChannel('ch-3', { avatarUrl: 'https://example.com/broken.jpg' }),
    ])

    const body = document.body.innerHTML
    expect(body).toContain('https://example.com/broken.jpg')

    // Find the img in teleported body content and trigger error
    const img = document.body.querySelector('img[src="https://example.com/broken.jpg"]')
    expect(img).toBeTruthy()

    // Dispatch error event
    img?.dispatchEvent(new Event('error'))

    await wrapper.vm.$nextTick()

    const bodyAfterError = document.body.innerHTML
    // After error, fallback badge "in" should be shown
    expect(bodyAfterError).toContain('in')
  })
})

describe('CreatePostModal.vue — media asset integration', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  it('shows a transient blob preview immediately after file selection without uploading yet', async () => {
    const mediaStore = useMediaStore()
    const createAndUpload = vi.spyOn(mediaStore, 'createAndUpload')
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:test-preview'),
    })
    const createObjectUrl = URL.createObjectURL as unknown as ReturnType<typeof vi.fn>

    const wrapper = mountModal([makeChannel('ch-preview')])
    await wrapper.vm.$nextTick()

    const input = document.body.querySelector('input[type="file"]') as HTMLInputElement | null
    expect(input).not.toBeNull()

    const file = new File(['fake'], 'preview.png', { type: 'image/png' })
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [file],
    })
    input?.dispatchEvent(new Event('change', { bubbles: true }))

    await wrapper.vm.$nextTick()

    const preview = document.body.querySelector(
      'img[alt="Media preview"]',
    ) as HTMLImageElement | null
    expect(preview).not.toBeNull()
    expect(preview?.getAttribute('src')).toBe('blob:test-preview')
    expect(createAndUpload).not.toHaveBeenCalled()
    expect(createObjectUrl).toHaveBeenCalledWith(file)
    expect(input?.value).toBe('')
  })

  it('opens the hidden file input when the media drop zone is clicked', async () => {
    const wrapper = mountModal([makeChannel('ch-picker')])
    await wrapper.vm.$nextTick()

    const input = document.body.querySelector('input[type="file"]') as HTMLInputElement | null
    expect(input).not.toBeNull()

    const clickSpy = vi.spyOn(input as HTMLInputElement, 'click')
    const dropZone = Array.from(document.body.querySelectorAll('button')).find(
      (button) => button.getAttribute('aria-label') === 'composer.dragDrop',
    ) as HTMLButtonElement | undefined

    expect(dropZone).toBeDefined()
    dropZone?.click()

    expect(clickSpy).toHaveBeenCalled()

    wrapper.unmount()
  })

  it('shows the selected asset preview instead of the completed upload card', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['preview-asset'] = {
      assetId: 'preview-asset',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: 'preview.png',
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/preview-asset/preview',
    }
    mediaStore.selectedAssetIds.push('preview-asset')
    mediaStore.uploads['done-key'] = {
      tempKey: 'done-key',
      assetId: 'preview-asset',
      file: new File(['fake'], 'preview.png', { type: 'image/png' }),
      progress: 100,
      status: 'done',
      asset: mediaStore.assetsById['preview-asset'],
    }

    mountModal([makeChannel('ch-preview')])

    await new Promise((resolve) => setTimeout(resolve, 0))

    const body = document.body.innerHTML
    expect(body).toContain('Selected media preview')
    expect(body).not.toContain('Uploading preview.png')
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('media store exposes persisted selected asset ids', () => {
    const mediaStore = useMediaStore()
    // Seed an asset in the store
    mediaStore.assetsById['media-asset-1'] = {
      assetId: 'media-asset-1',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'hero.jpg',
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
    }

    mediaStore.selectedAssetIds.push('media-asset-1')

    expect(mediaStore.selectedAssets).toHaveLength(1)
    expect(mediaStore.selectedAssets[0]?.assetId).toBe('media-asset-1')
  })

  it('selected assets include asset metadata for display', () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['display-asset'] = {
      assetId: 'display-asset',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'display-photo.jpg',
      fileSizeBytes: 2048,
      createdAt: '2026-06-19T12:00:00Z',
    }
    mediaStore.selectedAssetIds.push('display-asset')

    const selected = mediaStore.selectedAssets[0]
    expect(selected?.mediaType).toBe('image/jpeg')
    expect(selected?.originalFilename).toBe('display-photo.jpg')
    expect(selected?.fileSizeBytes).toBe(2048)
  })

  it('adds selected media asset to selection', () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['selectable-asset'] = {
      assetId: 'selectable-asset',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: 'chart.png',
      fileSizeBytes: 512,
      createdAt: '2026-06-19T12:00:00Z',
    }

    mediaStore.addToSelection('selectable-asset')

    expect(mediaStore.selectedAssetIds).toContain('selectable-asset')
  })

  it('prevents duplicate asset ids in selection', () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['dup-asset'] = {
      assetId: 'dup-asset',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: null,
      fileSizeBytes: null,
      createdAt: '2026-06-19T12:00:00Z',
    }

    mediaStore.addToSelection('dup-asset')
    mediaStore.addToSelection('dup-asset')

    expect(mediaStore.selectedAssetIds.filter((id) => id === 'dup-asset')).toHaveLength(1)
  })

  it('removes asset from selection', () => {
    const mediaStore = useMediaStore()
    mediaStore.selectedAssetIds.push('remove-me')

    mediaStore.removeFromSelection('remove-me')

    expect(mediaStore.selectedAssetIds).not.toContain('remove-me')
  })

  it('clears all selected assets', () => {
    const mediaStore = useMediaStore()
    mediaStore.selectedAssetIds.push('asset-a', 'asset-b')

    mediaStore.clearSelection()

    expect(mediaStore.selectedAssetIds).toEqual([])
  })
})

describe('CreatePostModal.vue — preview composition', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders the shared preview shell with the LinkedIn child preview', async () => {
    const wrapper = mountModal([
      makeChannel('preview-shell', { name: 'Acme Corp', handle: 'acme-corp' }),
    ])
    await wrapper.vm.$nextTick()

    expect(document.body.innerHTML).toContain('composer.linkedinPreview')
    expect(document.body.innerHTML).toContain('Acme Corp')
    expect(document.body.innerHTML).toContain('composer.seePreviewHere')
  })

  it('shows the more affordance for very long preview text without mutating the textarea value', async () => {
    const wrapper = mountModal([makeChannel('preview-long')])
    await wrapper.vm.$nextTick()

    const textarea = document.body.querySelector('textarea') as HTMLTextAreaElement | null
    expect(textarea).not.toBeNull()

    const longText = `${'Long LinkedIn preview text '.repeat(20)}\n\n${'Extra paragraph '.repeat(14)}`
    textarea!.value = longText
    textarea?.dispatchEvent(new Event('input', { bubbles: true }))

    await wrapper.vm.$nextTick()

    const previewText = document.body.querySelector('[data-testid="linkedin-preview-text"]')
    const previewMore = document.body.querySelector('[data-testid="linkedin-preview-more"]')

    expect(previewText).not.toBeNull()
    expect(previewMore?.textContent?.trim()).toBe('...more')
    expect((textarea as HTMLTextAreaElement).value).toBe(longText)
  })

  it('keeps the preview media visible when text is truncated', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['preview-media'] = {
      assetId: 'preview-media',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: 'preview.png',
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/preview-media/preview',
    }
    mediaStore.selectedAssetIds.push('preview-media')

    const wrapper = mountModal([makeChannel('preview-with-media')])
    await wrapper.vm.$nextTick()

    const textarea = document.body.querySelector('textarea') as HTMLTextAreaElement | null
    textarea!.value = 'A'.repeat(320)
    textarea?.dispatchEvent(new Event('input', { bubbles: true }))

    await wrapper.vm.$nextTick()

    const previewImage = document.body.querySelector('[data-testid="linkedin-preview-media"] img')
    const previewMore = document.body.querySelector('[data-testid="linkedin-preview-more"]')

    expect(previewImage).not.toBeNull()
    expect((previewImage as HTMLImageElement).getAttribute('src')).toBe(
      '/api/media/assets/preview-media/preview',
    )
    expect(previewMore?.textContent?.trim()).toBe('...more')
  })
})

describe('CreatePostModal.vue — submit normalization', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('submits trimmed post content to the publishing store', async () => {
    const channel = makeChannel('submit-ch-1')
    const store = usePublishingStore()
    store.channels = [channel]

    const wrapper = mount(CreatePostModalComponent, {
      props: { isOpen: true },
      global: { mocks: { $t: mockT } },
    })
    await wrapper.vm.$nextTick()

    const schedulePost = vi.spyOn(store, 'schedulePost').mockResolvedValue({
      id: 'pub-1',
      content: 'Hello world',
      channels: ['linkedin'],
      scheduledAt: '2026-06-20T14:00:00Z',
      status: 'QUEUED',
      priority: false,
    })

    // The textarea is teleported to body — query it directly
    const textarea = document.body.querySelector('textarea')
    expect(textarea).not.toBeNull()

    // Simulate typing with leading/trailing whitespace
    ;(textarea as HTMLTextAreaElement).value = '  Hello world  '
    textarea?.dispatchEvent(new Event('input', { bubbles: true }))

    await wrapper.vm.$nextTick()

    // Click the schedule button (the ui-button rendered in the teleported content)
    const button = document.body.querySelector('.ui-button')
    expect(button).not.toBeNull()
    ;(button as HTMLButtonElement).click()

    await wrapper.vm.$nextTick()

    expect(schedulePost).toHaveBeenCalledTimes(1)
    expect(schedulePost).toHaveBeenCalledWith(
      expect.objectContaining({
        content: 'Hello world',
      }),
    )
  })
})

describe('CreatePostModal.vue — deferred media upload on submit', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('uploads the selected file only when Schedule Post is clicked', async () => {
    const mediaStore = useMediaStore()
    const publishingStore = usePublishingStore()
    publishingStore.channels = [makeChannel('submit-with-media')]

    const schedulePost = vi.spyOn(publishingStore, 'schedulePost').mockResolvedValue({
      id: 'pub-1',
      content: 'Hello world',
      channels: ['linkedin'],
      scheduledAt: '2026-06-22T10:00:00Z',
      status: 'QUEUED',
      priority: false,
    })
    const createAndUpload = vi.spyOn(mediaStore, 'createAndUpload').mockResolvedValue({
      assetId: 'uploaded-asset',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: 'preview.png',
      fileSizeBytes: 1234,
      createdAt: '2026-06-22T10:00:00Z',
      previewUrl: '/api/media/assets/uploaded-asset/preview',
    })

    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:test-preview'),
    })

    const wrapper = mount(CreatePostModalComponent, {
      props: { isOpen: true },
      global: { mocks: { $t: mockT } },
    })
    await wrapper.vm.$nextTick()

    const input = document.body.querySelector('input[type="file"]') as HTMLInputElement | null
    expect(input).not.toBeNull()

    const file = new File(['fake'], 'preview.png', { type: 'image/png' })
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [file],
    })
    input?.dispatchEvent(new Event('change', { bubbles: true }))

    const textarea = document.body.querySelector('textarea')
    expect(textarea).not.toBeNull()
    ;(textarea as HTMLTextAreaElement).value = 'Hello world'
    textarea?.dispatchEvent(new Event('input', { bubbles: true }))

    await wrapper.vm.$nextTick()
    expect(createAndUpload).not.toHaveBeenCalled()

    const button = document.body.querySelector('.ui-button')
    expect(button).not.toBeNull()
    ;(button as HTMLButtonElement).click()

    await wrapper.vm.$nextTick()
    await Promise.resolve()

    expect(createAndUpload).toHaveBeenCalledTimes(1)
    expect(createAndUpload).toHaveBeenCalledWith(
      file,
      expect.stringMatching(/^modal-upload-/),
      expect.any(Function),
    )
    expect(mediaStore.selectedAssetIds).toContain('uploaded-asset')
    expect(schedulePost).toHaveBeenCalledWith(
      expect.objectContaining({
        content: 'Hello world',
      }),
    )
  })

  it('blocks schedulePost when the deferred upload fails', async () => {
    const mediaStore = useMediaStore()
    const publishingStore = usePublishingStore()
    publishingStore.channels = [makeChannel('submit-media-fail')]

    const schedulePost = vi.spyOn(publishingStore, 'schedulePost').mockResolvedValue({
      id: 'pub-should-not-exist',
      content: 'Hello world',
      channels: ['linkedin'],
      scheduledAt: '2026-06-22T10:00:00Z',
      status: 'QUEUED',
      priority: false,
    })
    const createAndUpload = vi
      .spyOn(mediaStore, 'createAndUpload')
      .mockRejectedValue(new Error('Network error: upload failed'))

    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:test-preview'),
    })

    const wrapper = mount(CreatePostModalComponent, {
      props: { isOpen: true },
      global: { mocks: { $t: mockT } },
    })
    await wrapper.vm.$nextTick()

    const input = document.body.querySelector('input[type="file"]') as HTMLInputElement | null
    const file = new File(['fake'], 'fail.png', { type: 'image/png' })
    Object.defineProperty(input!, 'files', { configurable: true, value: [file] })
    input?.dispatchEvent(new Event('change', { bubbles: true }))

    const textarea = document.body.querySelector('textarea')!
    textarea.value = 'Hello world'
    textarea.dispatchEvent(new Event('input', { bubbles: true }))
    await wrapper.vm.$nextTick()

    const button = document.body.querySelector('.ui-button') as HTMLButtonElement | null
    expect(button).not.toBeNull()
    button?.click()
    await wrapper.vm.$nextTick()
    await Promise.resolve()

    expect(createAndUpload).toHaveBeenCalledTimes(1)
    expect(schedulePost).not.toHaveBeenCalled()
  })

  it('passes the uploaded assetId to schedulePost when deferred upload succeeds', async () => {
    const mediaStore = useMediaStore()
    const publishingStore = usePublishingStore()
    publishingStore.channels = [makeChannel('submit-media-success')]

    const schedulePost = vi.spyOn(publishingStore, 'schedulePost').mockResolvedValue({
      id: 'pub-with-asset',
      content: 'Post with image',
      channels: ['linkedin'],
      scheduledAt: '2026-06-22T10:00:00Z',
      status: 'QUEUED',
      priority: false,
    })
    const createAndUpload = vi.spyOn(mediaStore, 'createAndUpload').mockResolvedValue({
      assetId: 'persistent-asset-id',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: 'hero.jpg',
      fileSizeBytes: 5678,
      createdAt: '2026-06-22T10:00:00Z',
      previewUrl: '/api/media/assets/persistent-asset-id/preview',
    })

    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:test-preview'),
    })

    const wrapper = mount(CreatePostModalComponent, {
      props: { isOpen: true },
      global: { mocks: { $t: mockT } },
    })
    await wrapper.vm.$nextTick()

    const input = document.body.querySelector('input[type="file"]') as HTMLInputElement | null
    const file = new File(['fake'], 'hero.jpg', { type: 'image/jpeg' })
    Object.defineProperty(input!, 'files', { configurable: true, value: [file] })
    input?.dispatchEvent(new Event('change', { bubbles: true }))

    const textarea = document.body.querySelector('textarea')!
    textarea.value = 'Post with image'
    textarea.dispatchEvent(new Event('input', { bubbles: true }))
    await wrapper.vm.$nextTick()

    const button = document.body.querySelector('.ui-button') as HTMLButtonElement | null
    expect(button).not.toBeNull()
    button?.click()
    await wrapper.vm.$nextTick()
    await Promise.resolve()

    expect(createAndUpload).toHaveBeenCalledTimes(1)
    expect(schedulePost).toHaveBeenCalledTimes(1)
    expect(schedulePost).toHaveBeenCalledWith(
      expect.objectContaining({
        assetIds: ['persistent-asset-id'],
      }),
    )
  })
})

describe('CreatePostModal.vue — dangling upload recovery', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('media store tracks failed uploads for retry', () => {
    const mediaStore = useMediaStore()
    mediaStore.uploads['retry-key'] = {
      tempKey: 'retry-key',
      assetId: 'dangling-asset-id',
      file: new File(['fake'], 'dangling.jpg', { type: 'image/jpeg' }),
      progress: 0,
      status: 'failed',
      errorTitle: 'Upload failed',
      errorDetail: 'Server error',
    }

    expect(mediaStore.failedUploads).toHaveLength(1)
    expect(mediaStore.failedUploads[0]?.tempKey).toBe('retry-key')
  })

  it('failed uploads are separate from completed uploads', () => {
    const mediaStore = useMediaStore()
    mediaStore.uploads['done-key'] = {
      tempKey: 'done-key',
      assetId: 'done-asset',
      file: new File(['fake'], 'done.jpg', { type: 'image/jpeg' }),
      progress: 100,
      status: 'done',
    }
    mediaStore.uploads['fail-key'] = {
      tempKey: 'fail-key',
      assetId: 'fail-asset',
      file: new File(['fake'], 'fail.jpg', { type: 'image/jpeg' }),
      progress: 0,
      status: 'failed',
      errorTitle: 'Failed',
      errorDetail: 'Error',
    }

    expect(mediaStore.completedUploads).toHaveLength(1)
    expect(mediaStore.failedUploads).toHaveLength(1)
    expect(mediaStore.failedUploads).not.toContainEqual(
      expect.objectContaining({ tempKey: 'done-key' }),
    )
  })

  it('dismissing upload removes it from tracking', () => {
    const mediaStore = useMediaStore()
    mediaStore.uploads['dismiss-key'] = {
      tempKey: 'dismiss-key',
      assetId: 'dismiss-asset',
      file: new File(['fake'], 'dismiss.jpg', { type: 'image/jpeg' }),
      progress: 0,
      status: 'failed',
      errorTitle: 'Error',
      errorDetail: 'Detail',
    }

    mediaStore.dismissUpload('dismiss-key')

    expect(mediaStore.uploads['dismiss-key']).toBeUndefined()
  })

  it('pending uploads are separate from completed and failed', () => {
    const mediaStore = useMediaStore()
    mediaStore.uploads['pending-key'] = {
      tempKey: 'pending-key',
      assetId: 'pending-asset',
      file: new File(['fake'], 'pending.jpg', { type: 'image/jpeg' }),
      progress: 50,
      status: 'uploading',
    }

    expect(mediaStore.pendingUploads).toHaveLength(1)
    expect(mediaStore.pendingUploads[0]?.tempKey).toBe('pending-key')
  })
})
