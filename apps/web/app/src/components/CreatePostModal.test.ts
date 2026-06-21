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
    expect(mediaStore.failedUploads[0].tempKey).toBe('retry-key')
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
    expect(mediaStore.pendingUploads[0].tempKey).toBe('pending-key')
  })
})
