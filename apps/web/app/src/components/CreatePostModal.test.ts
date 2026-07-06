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
  Button: {
    props: ['asChild'],
    template: '<slot v-if="asChild" /><button v-else class="ui-button"><slot /></button>',
  },
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
    XIcon: stub,
    Search: stub,
    Loader2: stub,
    Upload: stub,
    AlertCircle: stub,
    RotateCcw: stub,
  }
})

// Lightweight stub for the composer media picker shell. The shell's own
// behavior is covered in ComposerMediaPickerShell.test.ts; here we only verify
// that CreatePostModal wires open state, focus return, and emit observation.
vi.mock('@/components/composer/ComposerMediaPickerShell.vue', () => ({
  default: {
    name: 'ComposerMediaPickerShell',
    props: [
      'open',
      'state',
      'searchQuery',
      'selectedFilter',
      'filterOptions',
      'assets',
      'disabled',
      'errorMessage',
    ],
    emits: ['update:open', 'close', 'search-change', 'filter-change', 'provider-import'],
    template: `
      <div
        v-if="open"
        data-testid="dialog-content"
        @keydown.escape.stop="$emit('close'); $emit('update:open', false)"
      >
        <h2 data-testid="dialog-title">composer.mediaPicker.title</h2>
        <button
          data-testid="media-picker-close"
          type="button"
          @click="$emit('close'); $emit('update:open', false)"
        >close</button>
        <input
          data-testid="media-picker-search"
          @input="$emit('search-change', { query: $event.target.value })"
        />
        <select
          data-testid="media-picker-filter"
          @change="$emit('filter-change', { filter: $event.target.value })"
        >
          <option value="all">all</option>
          <option value="image">image</option>
          <option value="video">video</option>
          <option value="document">document</option>
        </select>
      </div>
    `,
  },
}))

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

function mountModal(channels: TestChannel[], props: Record<string, unknown> = {}) {
  const store = usePublishingStore()
  store.channels = channels

  return mount(CreatePostModalComponent, {
    props: { isOpen: true, ...props },
    global: {
      mocks: { $t: mockT },
    },
  })
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

  afterEach(() => {
    document.body.innerHTML = ''
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

    const button = Array.from(document.body.querySelectorAll('button')).find((candidate) =>
      candidate.textContent?.includes('Schedule Now'),
    ) as HTMLButtonElement | undefined
    expect(button).toBeDefined()
    button?.click()

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

    const button = Array.from(document.body.querySelectorAll('button')).find((candidate) =>
      candidate.textContent?.includes('Schedule Now'),
    ) as HTMLButtonElement | undefined
    expect(button).toBeDefined()
    button?.click()

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

    const button = Array.from(document.body.querySelectorAll('button')).find((candidate) =>
      candidate.textContent?.includes('Schedule Now'),
    ) as HTMLButtonElement | undefined
    expect(button).toBeDefined()
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

    const button = Array.from(document.body.querySelectorAll('button')).find((candidate) =>
      candidate.textContent?.includes('Schedule Now'),
    ) as HTMLButtonElement | undefined
    expect(button).toBeDefined()
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

describe('CreatePostModal.vue — edit mode', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('pre-fills content, schedule mode, date, time, priority, and media in edit mode', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAsset').mockImplementation(async (assetId: string) => ({
      assetId,
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: `${assetId}.png`,
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: `/api/media/assets/${assetId}/preview`,
    }))
    const wrapper = mountModal([makeChannel('ch-edit-1')], {
      editingPublication: makeEditingPublication(),
    })

    await wrapper.vm.$nextTick()
    await Promise.resolve()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await wrapper.vm.$nextTick()

    const textarea = document.body.querySelector('textarea') as HTMLTextAreaElement | null
    const timeInput = document.body.querySelector('input[type="time"]') as HTMLInputElement | null
    const checkedRadio = document.body.querySelector<HTMLInputElement>(
      'input[type="radio"]:checked',
    )
    const customModeButton = checkedRadio?.closest('label')
    const checkboxes = document.body.querySelectorAll('input[type="checkbox"]')

    expect(textarea?.value).toBe('Existing scheduled content')
    expect(customModeButton?.textContent).toContain('Pick Date')
    // time is parsed from scheduledAt UTC and rendered in local TZ — only verify format
    expect(timeInput?.value).toMatch(/^\d{2}:\d{2}$/)
    expect((checkboxes[0] as HTMLInputElement | undefined)?.checked).toBe(true)
    expect(mediaStore.selectedAssetIds).toEqual(['asset-1', 'asset-2'])
    expect(document.body.innerHTML).toContain('Jun 25, 2030')
  })

  it('initializes a real reconciled NOW response as NOW without stale custom controls', async () => {
    const wrapper = mountModal([makeChannel('9f06a3c8-account')], {
      editingPublication: makeEditingPublication({
        id: '8a25f709-40f6-4ab0-b5ae-f79bdcf4d395',
        accountId: '9f06a3c8-account',
        scheduleMode: 'NOW',
        scheduledAt: '2026-07-02T15:25:38.050321Z',
        assetIds: ['real-media-asset-id'],
      }),
    })
    await wrapper.vm.$nextTick()

    expect(
      document.body.querySelector<HTMLInputElement>('input[type="radio"]:checked')?.closest('label')
        ?.textContent,
    ).toContain('Now')
    expect(document.body.querySelector<HTMLInputElement>('input[type="time"]')).toBeNull()
    expect(document.body.innerHTML).not.toContain('Jul 2, 2026')
  })

  it('maps NOW and NEXT_SLOT without stale custom date or time values', async () => {
    const nowWrapper = mountModal([makeChannel('ch-edit-1')], {
      editingPublication: makeEditingPublication({ scheduleMode: 'NOW', scheduledAt: '' }),
    })
    await nowWrapper.vm.$nextTick()
    expect(
      document.body.querySelector<HTMLInputElement>('input[type="radio"]:checked')?.closest('label')
        ?.textContent,
    ).toContain('Now')
    expect(document.body.querySelector<HTMLInputElement>('input[type="time"]')).toBeNull()
    expect(document.body.innerHTML).not.toContain('Jun 25, 2030')
    nowWrapper.unmount()
    document.body.innerHTML = ''

    const nextWrapper = mountModal([makeChannel('ch-edit-1')], {
      editingPublication: makeEditingPublication({
        scheduleMode: 'NEXT_SLOT',
        scheduledAt: '2026-06-20T15:00:00Z',
      }),
    })
    await nextWrapper.vm.$nextTick()
    expect(
      document.body.querySelector<HTMLInputElement>('input[type="radio"]:checked')?.closest('label')
        ?.textContent,
    ).toContain('Next Schedule')
    expect(document.body.querySelector<HTMLInputElement>('input[type="time"]')).toBeNull()
    expect(document.body.innerHTML).not.toContain('Jun 20, 2026')
  })

  it('submits NOW mode edit through updatePost with NOW scheduleMode preserved', async () => {
    const store = usePublishingStore()
    const updatePost = vi.spyOn(store, 'updatePost').mockResolvedValue({
      ...makeEditingPublication({ scheduleMode: 'NOW' }),
      content: 'Updated NOW content',
    })

    const wrapper = mountModal([makeChannel('ch-edit-now')], {
      editingPublication: makeEditingPublication({ scheduleMode: 'NOW' }),
    })
    await wrapper.vm.$nextTick()

    const textarea = document.body.querySelector('textarea') as HTMLTextAreaElement | null
    textarea!.value = 'Updated NOW content'
    textarea?.dispatchEvent(new Event('input', { bubbles: true }))
    await wrapper.vm.$nextTick()

    const submitButton = Array.from(document.body.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('composer.saveChanges'),
    ) as HTMLButtonElement | undefined
    submitButton?.click()

    await wrapper.vm.$nextTick()
    await Promise.resolve()

    expect(updatePost).toHaveBeenCalledWith('pub-edit-1', {
      content: 'Updated NOW content',
      scheduledAt: undefined,
      priority: true,
      scheduleMode: 'NOW',
    })
    expect(wrapper.emitted('updated')).toHaveLength(1)
  })

  it('submits NEXT_SLOT mode edit through updatePost with NEXT_SLOT scheduleMode preserved', async () => {
    const store = usePublishingStore()
    const updatePost = vi.spyOn(store, 'updatePost').mockResolvedValue({
      ...makeEditingPublication({ scheduleMode: 'NEXT_SLOT' }),
      content: 'Updated NEXT_SLOT content',
    })

    const wrapper = mountModal([makeChannel('ch-edit-next')], {
      editingPublication: makeEditingPublication({ scheduleMode: 'NEXT_SLOT' }),
    })
    await wrapper.vm.$nextTick()

    const textarea = document.body.querySelector('textarea') as HTMLTextAreaElement | null
    textarea!.value = 'Updated NEXT_SLOT content'
    textarea?.dispatchEvent(new Event('input', { bubbles: true }))
    await wrapper.vm.$nextTick()

    const submitButton = Array.from(document.body.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('composer.saveChanges'),
    ) as HTMLButtonElement | undefined
    submitButton?.click()

    await wrapper.vm.$nextTick()
    await Promise.resolve()

    expect(updatePost).toHaveBeenCalledWith('pub-edit-1', {
      content: 'Updated NEXT_SLOT content',
      scheduledAt: undefined,
      priority: true,
      scheduleMode: 'NEXT_SLOT',
    })
    expect(wrapper.emitted('updated')).toHaveLength(1)
  })

  it('hydrates edit assets into visible previews and skips missing assets gracefully', async () => {
    const mediaStore = useMediaStore()
    const loadAsset = vi.spyOn(mediaStore, 'loadAsset')
    loadAsset.mockImplementation(async (assetId: string) => {
      if (assetId === 'missing-asset') {
        throw Object.assign(new Error('Not found'), { status: 404 })
      }
      const asset = {
        assetId,
        workspaceId: 'ws-1',
        sourceType: 'UPLOADED' as const,
        mediaType: 'image/png',
        status: 'READY' as const,
        originalFilename: `${assetId}.png`,
        fileSizeBytes: 1024,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: `/api/media/assets/${assetId}/preview`,
      }
      mediaStore.assetsById[assetId] = asset
      return asset
    })

    const wrapper = mountModal([makeChannel('ch-edit-1')], {
      editingPublication: makeEditingPublication({ assetIds: ['asset-a', 'missing-asset'] }),
    })
    await wrapper.vm.$nextTick()
    await Promise.resolve()
    await wrapper.vm.$nextTick()

    expect(loadAsset).toHaveBeenCalledWith('asset-a')
    expect(loadAsset).toHaveBeenCalledWith('missing-asset')
    expect(mediaStore.selectedAssetIds).toEqual(['asset-a'])
    expect(mediaStore.selectedAssets[0]?.previewUrl).toBe('/api/media/assets/asset-a/preview')
  })

  it('omits assetIds when saving edit without touching assets', async () => {
    const store = usePublishingStore()
    const updatePost = vi.spyOn(store, 'updatePost').mockResolvedValue({
      ...makeEditingPublication(),
      content: 'Updated untouched content',
    })
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAsset').mockImplementation(async (assetId: string) => ({
      assetId,
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: `${assetId}.png`,
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: `/api/media/assets/${assetId}/preview`,
    }))

    const wrapper = mountModal([makeChannel('ch-edit-1')], {
      editingPublication: makeEditingPublication(),
    })
    await wrapper.vm.$nextTick()
    await Promise.resolve()

    const textarea = document.body.querySelector('textarea') as HTMLTextAreaElement | null
    textarea!.value = 'Updated untouched content'
    textarea?.dispatchEvent(new Event('input', { bubbles: true }))
    await wrapper.vm.$nextTick()

    const submitButton = Array.from(document.body.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('composer.saveChanges'),
    ) as HTMLButtonElement | undefined
    submitButton?.click()

    await wrapper.vm.$nextTick()
    await Promise.resolve()

    expect(updatePost).toHaveBeenCalledWith('pub-edit-1', {
      content: 'Updated untouched content',
      scheduledAt: '2030-06-25T14:30:00.000Z',
      priority: true,
      scheduleMode: 'SCHEDULED_AT',
    })
  })

  it('sends empty assetIds after explicit edit clear', async () => {
    const store = usePublishingStore()
    const updatePost = vi
      .spyOn(store, 'updatePost')
      .mockResolvedValue(makeEditingPublication({ assetIds: [] }))
    const mediaStore = useMediaStore()
    mediaStore.assetsById['asset-1'] = {
      assetId: 'asset-1',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: 'asset-1.png',
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-1/preview',
    }

    const wrapper = mountModal([makeChannel('ch-edit-1')], {
      editingPublication: makeEditingPublication({ assetIds: ['asset-1'] }),
    })
    await wrapper.vm.$nextTick()

    const removeButton = document.body
      .querySelector('img[alt="Selected media preview"]')
      ?.parentElement?.querySelector('button') as HTMLButtonElement | null
    expect(removeButton).not.toBeNull()
    removeButton?.click()
    await wrapper.vm.$nextTick()

    const submitButton = Array.from(document.body.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('composer.saveChanges'),
    ) as HTMLButtonElement | undefined
    submitButton?.click()
    await wrapper.vm.$nextTick()
    await Promise.resolve()

    expect(updatePost).toHaveBeenCalledWith('pub-edit-1', expect.objectContaining({ assetIds: [] }))
  })

  it('sends replacement assetIds after selecting a new edit asset', async () => {
    const store = usePublishingStore()
    const updatePost = vi
      .spyOn(store, 'updatePost')
      .mockResolvedValue(makeEditingPublication({ assetIds: ['asset-c'] }))
    const mediaStore = useMediaStore()
    mediaStore.assetsById['asset-c'] = {
      assetId: 'asset-c',
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: 'asset-c.png',
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/asset-c/preview',
    }

    const wrapper = mountModal([makeChannel('ch-edit-1')], {
      editingPublication: makeEditingPublication({ assetIds: [] }),
    })
    await wrapper.vm.$nextTick()

    mediaStore.addToSelection('asset-c')
    await wrapper.vm.$nextTick()

    const submitButton = Array.from(document.body.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('composer.saveChanges'),
    ) as HTMLButtonElement | undefined
    submitButton?.click()
    await wrapper.vm.$nextTick()
    await Promise.resolve()

    expect(updatePost).toHaveBeenCalledWith(
      'pub-edit-1',
      expect.objectContaining({ assetIds: ['asset-c'] }),
    )
  })

  it('locks channel selection and hides create-another in edit mode', async () => {
    const wrapper = mountModal([makeChannel('ch-edit-1'), makeChannel('ch-edit-2')], {
      editingPublication: makeEditingPublication(),
    })
    await wrapper.vm.$nextTick()

    const channelButtons = Array.from(document.body.querySelectorAll('button')).filter((button) =>
      button.textContent?.includes('Channel ch-edit-'),
    ) as HTMLButtonElement[]

    expect(channelButtons.length).toBeGreaterThan(0)
    channelButtons.forEach((button) => expect(button.disabled).toBe(true))
    expect(document.body.innerHTML).toContain('composer.saveChanges')
    expect(document.body.innerHTML).not.toContain('Create Another')
  })

  it('submits through updatePost, emits updated, and does not call schedulePost in edit mode', async () => {
    const mediaStore = useMediaStore()
    vi.spyOn(mediaStore, 'loadAsset').mockImplementation(async (assetId: string) => ({
      assetId,
      workspaceId: 'ws-1',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: `${assetId}.png`,
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: `/api/media/assets/${assetId}/preview`,
    }))
    const store = usePublishingStore()
    const updatePost = vi.spyOn(store, 'updatePost').mockResolvedValue({
      ...makeEditingPublication(),
      content: 'Updated content',
    })
    const schedulePost = vi.spyOn(store, 'schedulePost')

    const wrapper = mountModal([makeChannel('ch-edit-1')], {
      editingPublication: makeEditingPublication(),
    })
    await wrapper.vm.$nextTick()

    const textarea = document.body.querySelector('textarea') as HTMLTextAreaElement | null
    textarea!.value = 'Updated content'
    textarea?.dispatchEvent(new Event('input', { bubbles: true }))
    await wrapper.vm.$nextTick()

    const submitButton = Array.from(document.body.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('composer.saveChanges'),
    ) as HTMLButtonElement | undefined
    submitButton?.click()

    await wrapper.vm.$nextTick()
    await Promise.resolve()

    expect(updatePost).toHaveBeenCalledWith('pub-edit-1', {
      content: 'Updated content',
      scheduledAt: '2030-06-25T14:30:00.000Z',
      priority: true,
      scheduleMode: 'SCHEDULED_AT',
    })
    expect(schedulePost).not.toHaveBeenCalled()
    expect(wrapper.emitted('updated')).toHaveLength(1)
  })

  it('surfaces update errors in edit mode', async () => {
    const store = usePublishingStore()
    vi.spyOn(store, 'updatePost').mockRejectedValue(new Error('Update failed'))

    const wrapper = mountModal([makeChannel('ch-edit-1')], {
      editingPublication: makeEditingPublication(),
    })
    // Flush all async initialization (loadDanglingAssets, nextTick, focus trap)
    await new Promise((resolve) => setTimeout(resolve, 10))
    await wrapper.vm.$nextTick()

    const submitButton = Array.from(document.body.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('composer.saveChanges'),
    ) as HTMLButtonElement | undefined
    submitButton?.click()

    await wrapper.vm.$nextTick()
    await new Promise((resolve) => setTimeout(resolve, 10))

    expect(document.body.innerHTML).toContain('Update failed')
    expect(wrapper.emitted('updated')).toBeUndefined()
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

describe('CreatePostModal.vue — media picker shell integration', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders a media picker trigger when the modal is open', async () => {
    const wrapper = mountModal([makeChannel('ch-picker-trigger')])
    await wrapper.vm.$nextTick()

    const trigger = document.body.querySelector(
      '[data-testid="media-picker-trigger"]',
    ) as HTMLButtonElement | null
    expect(trigger).not.toBeNull()
    expect(trigger?.textContent).toContain('composer.mediaPicker.open')
  })

  it('opens the media picker shell when the trigger is activated', async () => {
    const wrapper = mountModal([makeChannel('ch-picker-open')])
    await wrapper.vm.$nextTick()

    const trigger = document.body.querySelector(
      '[data-testid="media-picker-trigger"]',
    ) as HTMLButtonElement | null
    expect(trigger).not.toBeNull()
    trigger?.click()
    await wrapper.vm.$nextTick()

    // Shell should be rendered (dialog from ComposerMediaPickerShell)
    const pickerDialog = document.body.querySelector('[data-testid="dialog-content"]')
    expect(pickerDialog).not.toBeNull()
  })

  it('preserves composer text content when the picker opens and closes', async () => {
    const wrapper = mountModal([makeChannel('ch-picker-preserve')])
    await wrapper.vm.$nextTick()

    const textarea = document.body.querySelector('textarea') as HTMLTextAreaElement | null
    expect(textarea).not.toBeNull()
    textarea!.value = 'My draft post content'
    textarea?.dispatchEvent(new Event('input', { bubbles: true }))
    await wrapper.vm.$nextTick()

    const trigger = document.body.querySelector(
      '[data-testid="media-picker-trigger"]',
    ) as HTMLButtonElement | null
    expect(trigger).not.toBeNull()
    trigger?.click()
    await wrapper.vm.$nextTick()

    expect(document.body.querySelector('[data-testid="dialog-content"]')).not.toBeNull()
    expect(textarea?.value).toBe('My draft post content')

    const closeBtn = document.body.querySelector(
      '[data-testid="media-picker-close"]',
    ) as HTMLButtonElement | null
    expect(closeBtn).not.toBeNull()
    closeBtn?.click()
    await wrapper.vm.$nextTick()

    expect(textarea?.value).toBe('My draft post content')
  })

  it('does not close the parent composer when Escape is pressed inside the media picker shell', async () => {
    const wrapper = mountModal([makeChannel('ch-picker-escape')])
    await wrapper.vm.$nextTick()

    const trigger = document.body.querySelector(
      '[data-testid="media-picker-trigger"]',
    ) as HTMLButtonElement | null
    expect(trigger).not.toBeNull()
    trigger?.click()
    await wrapper.vm.$nextTick()

    const pickerDialog = document.body.querySelector('[data-testid="dialog-content"]')
    expect(pickerDialog).not.toBeNull()
    pickerDialog?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('close')).toBeUndefined()
    expect(document.body.querySelector('[data-testid="dialog-content"]')).toBeNull()
  })

  it('closes the media picker shell when the shell close button is clicked and returns focus to the trigger', async () => {
    const wrapper = mountModal([makeChannel('ch-picker-close')])
    await wrapper.vm.$nextTick()

    const trigger = document.body.querySelector(
      '[data-testid="media-picker-trigger"]',
    ) as HTMLButtonElement | null
    expect(trigger).not.toBeNull()

    trigger?.focus()
    trigger?.click()
    await wrapper.vm.$nextTick()

    expect(document.body.querySelector('[data-testid="dialog-content"]')).not.toBeNull()

    const closeBtn = document.body.querySelector(
      '[data-testid="media-picker-close"]',
    ) as HTMLButtonElement | null
    expect(closeBtn).not.toBeNull()
    closeBtn?.click()
    await wrapper.vm.$nextTick()
    await Promise.resolve()

    expect(document.body.querySelector('[data-testid="media-picker-close"]')).toBeNull()
    expect(document.activeElement).toBe(trigger)
  })

  it('observes shell search and filter emits in the parent-owned state', async () => {
    const wrapper = mountModal([makeChannel('ch-picker-observe')])
    await wrapper.vm.$nextTick()

    const trigger = document.body.querySelector(
      '[data-testid="media-picker-trigger"]',
    ) as HTMLButtonElement | null
    expect(trigger).not.toBeNull()
    trigger?.click()
    await wrapper.vm.$nextTick()

    const search = document.body.querySelector(
      '[data-testid="media-picker-search"]',
    ) as HTMLInputElement | null
    const filter = document.body.querySelector(
      '[data-testid="media-picker-filter"]',
    ) as HTMLSelectElement | null
    expect(search).not.toBeNull()
    expect(filter).not.toBeNull()

    search!.value = 'landscape'
    search?.dispatchEvent(new Event('input', { bubbles: true }))
    filter!.value = 'video'
    filter?.dispatchEvent(new Event('change', { bubbles: true }))
    await wrapper.vm.$nextTick()

    expect(
      (wrapper.vm as unknown as { mediaPickerSearchQuery?: string }).mediaPickerSearchQuery,
    ).toBe('landscape')
    expect(
      (wrapper.vm as unknown as { mediaPickerSelectedFilter?: string }).mediaPickerSelectedFilter,
    ).toBe('video')
  })

  it('selects the imported asset when provider-import is emitted by the shell', async () => {
    const wrapper = mountModal([makeChannel('ch-picker-import')])
    const mediaStore = useMediaStore()
    const loadAsset = vi
      .spyOn(mediaStore, 'loadAsset')
      .mockImplementation(async (assetId: string) => ({
        assetId,
        workspaceId: 'ws-1',
        sourceType: 'EXTERNAL',
        mediaType: 'image/jpeg',
        status: 'READY',
        originalFilename: 'imported.jpg',
        fileSizeBytes: 1234,
        createdAt: '2026-07-06T10:00:00Z',
        previewUrl: `/api/media/assets/${assetId}/preview`,
      }))

    await wrapper.vm.$nextTick()

    const trigger = document.body.querySelector(
      '[data-testid="media-picker-trigger"]',
    ) as HTMLButtonElement | null
    expect(trigger).not.toBeNull()
    trigger?.click()
    await wrapper.vm.$nextTick()

    wrapper.findComponent({ name: 'ComposerMediaPickerShell' }).vm.$emit('provider-import', {
      externalId: 'asset-provider-imported',
    })
    await wrapper.vm.$nextTick()
    await Promise.resolve()

    expect(loadAsset).toHaveBeenCalledWith('asset-provider-imported')
    expect(mediaStore.selectedAssetIds).toContain('asset-provider-imported')
    expect(document.body.querySelector('[data-testid="dialog-content"]')).toBeNull()
  })
})
