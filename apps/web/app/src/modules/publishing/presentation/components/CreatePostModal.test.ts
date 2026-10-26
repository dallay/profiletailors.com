import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { Component } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import {
  usePublishingStore,
  type Publication,
} from '@modules/publishing/infrastructure/publishing.store'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import {
  generateAiPostContent,
  optimizeAiPostContent,
  regenerateAiPostContent,
} from '@modules/publishing/services/ai-content-api'
import { useMediaStore } from '@modules/media'
import type { MediaAssetSummary, UnsplashPhotoSummary } from '@modules/media/services/media-api'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'
import { toast } from 'vue-sonner'
import CreatePostModalComponent from './CreatePostModal.vue'

const { createShortlink } = vi.hoisted(() => ({ createShortlink: vi.fn() }))

vi.mock('vue-sonner', () => ({ toast: { warning: vi.fn() } }))

vi.mock('@modules/shortlinks/infrastructure/shortlinks-api', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('@modules/shortlinks/infrastructure/shortlinks-api')>()
  return { ...actual, createShortlink }
})

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
  'composer.media.unsupportedFormat':
    'Unsupported media format. Supported formats: JPEG, PNG, WEBP, GIF, MP4.',
  'composer.media.fileSizeExceeded': 'File size exceeds 10MB limit.',
  'shortlinks.createFailed': 'Some links could not be shortened and remain unchanged.',
  'composer.scheduleBtn': 'Schedule Post',
  'composer.scheduleNowBtn': 'Schedule Now',
  'composer.nextScheduleBtn': 'Next Schedule',
  'composer.saveChanges': 'Save Changes',
  'composer.cancelBtn': 'Cancel',
  'composer.threadsPreview': 'Threads Preview',
  'composer.linkedinAuthorFallback': 'LinkedIn Member',
  'composer.threadsAuthorFallback': 'Threads user',
  'composer.customizePerNetwork': 'Customize per network',
  'composer.customizeBase': 'Base',
  'composer.customizeReset': 'Reset to base',
  'composer.customizeUsingBase':
    'Uses base content. First edit creates a version only for this account.',
  'composer.customizeCustomized': 'Customized. Base edits no longer change this account.',
  'composer.customizeBaseStatus': 'Base content feeds {count} account(s) using base content.',
  'composer.channelLimitExceeded': '{name} exceeds {limit} characters',
  'composer.channelEmptyText': '{name} text is empty',
  'composer.markdown.bold': 'Bold',
  'composer.markdown.italic': 'Italic',
  'composer.markdown.strikethrough': 'Strikethrough',
  'composer.markdown.heading': 'Heading',
  'composer.markdown.unorderedList': 'Bullet list',
  'composer.markdown.orderedList': 'Numbered list',
  'composer.markdown.blockquote': 'Quote',
  'composer.markdown.link': 'Link',
  'composer.markdown.code': 'Inline code',
  'composer.markdown.toolbarLabel': 'Formatting',
  'composer.ai.button': 'AI Assistant',
  'composer.ai.title': 'AI content generator',
  'composer.ai.description':
    'Generate, refine and compare LinkedIn-ready post drafts from a prompt.',
  'composer.ai.prompt': 'Prompt',
  'composer.ai.promptPlaceholder': 'e.g. importance of clean code in software projects',
  'composer.ai.industry': 'Industry',
  'composer.ai.industryPlaceholder': 'e.g. SaaS, healthcare, education',
  'composer.ai.targetAudience': 'Target audience',
  'composer.ai.targetAudiencePlaceholder': 'e.g. engineering leaders',
  'composer.ai.format': 'Format',
  'composer.ai.formats.standard': 'Standard',
  'composer.ai.formats.thread': 'Thread',
  'composer.ai.formats.tips': 'Tips',
  'composer.ai.formats.question': 'Question',
  'composer.ai.formats.story': 'Story',
  'composer.ai.tone': 'Tone',
  'composer.ai.tones.professional': 'Professional',
  'composer.ai.tones.casual': 'Casual',
  'composer.ai.tones.inspirational': 'Inspirational',
  'composer.ai.tones.educational': 'Educational',
  'composer.ai.length': 'Length',
  'composer.ai.lengths.short': 'Short',
  'composer.ai.lengths.medium': 'Medium',
  'composer.ai.lengths.long': 'Long',
  'composer.ai.keywords': 'Keywords',
  'composer.ai.keywordsPlaceholder': 'Kotlin, microservices, cloud-native',
  'composer.ai.generate': 'Generate',
  'composer.ai.optimize': 'Optimize draft',
  'composer.ai.regenerate': 'Regenerate alternative',
  'composer.ai.generating': 'Generating…',
  'composer.ai.reviewTitle': 'Generated draft',
  'composer.ai.reviewHint': 'Pick the version you want to insert into the editor.',
  'composer.ai.comparisonTitle': 'Compare versions',
  'composer.ai.accept': 'Use selected version',
  'composer.ai.close': 'Close',
  'composer.ai.version': 'Version',
  'composer.ai.errors.promptRequired': 'Enter a prompt to generate content.',
  'composer.ai.errors.currentDraftRequired': 'Write a draft to optimize.',
  'composer.ai.errors.previousVersionRequired': 'Generate content before regenerating.',
  'composer.ai.errors.monthlyLimitReached': 'Monthly limit reached',
  'composer.ai.errors.emptyResponse': 'AI service returned empty content.',
  'composer.ai.errors.generic': 'Unable to generate content right now.',
  'composer.ai.resetDate': 'Resets on {date}',
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

vi.mock('@modules/publishing/services/ai-content-api', () => ({
  generateAiPostContent: vi.fn(),
  optimizeAiPostContent: vi.fn(),
  regenerateAiPostContent: vi.fn(),
  fetchAiSuggestions: vi.fn(),
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
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
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

vi.mock('@profiletailors/vue-ui/shell/button', () => ({
  Button: { template: '<button class="ui-button" v-bind="$attrs"><slot /></button>' },
}))

vi.mock('@lucide/vue', () => {
  const stub = { template: '<svg />' }
  return {
    Image: stub,
    ImageIcon: stub,
    Calendar: stub,
    CalendarIcon: stub,
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
    Heart: stub,
    ThumbsUp: stub,
    MessageCircle: stub,
    Repeat2: stub,
    Send: stub,
    Bold: stub,
    Italic: stub,
    Strikethrough: stub,
    Heading: stub,
    List: stub,
    ListOrdered: stub,
    Quote: stub,
    Link: stub,
    Code: stub,
  }
})

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

interface TestChannel {
  id: string
  accountId: string
  name: string
  provider: 'linkedin' | 'twitter' | 'threads'
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

function mountModal(
  channels: TestChannel[],
  props: Record<string, unknown> = {},
  stubs: Record<string, Component> = {},
) {
  const store = usePublishingStore()
  store.channels = channels

  const workspaceStore = useWorkspaceStore()
  workspaceStore.setActiveWorkspaceId('820c22d8-9ce7-5e76-867f-04bac4e53b59')

  const { isOpen = true, ...restProps } = props
  const wrapper = mount(CreatePostModalComponent, {
    attachTo: document.body,
    props: { isOpen: false, ...restProps },
    global: {
      mocks: { $t: mockT },
      stubs,
    },
  })
  mountedWrappers.push(wrapper)
  void wrapper.setProps({ isOpen })
  return wrapper
}

const mountedWrappers: Array<{ unmount: () => void }> = []

async function cleanupMountedWrappers() {
  for (const wrapper of mountedWrappers.splice(0)) {
    try {
      wrapper.unmount()
    } catch {}
  }
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
    id: '7fc06d85-71cb-5e22-bab4-c881fe923beb',
    content: 'Existing scheduled content',
    channels: ['linkedin'] as ('linkedin' | 'twitter' | 'instagram' | 'facebook')[],
    // Use a date far in the future to avoid validateCustomSchedule rejecting it
    // as "must be in the future" in CI / local environments with different current times.
    scheduledAt: '2030-06-25T14:30:00Z',
    scheduleMode: 'SCHEDULED_AT' as const,
    status: 'SCHEDULED' as const,
    priority: true,
    accountId: '73a4665b-8dcc-5f27-8cba-bca328b27bde',
    assetIds: [
      '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
      'b909d06f-92ea-5671-9f13-73475a89080e',
    ] as string[],
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

  afterEach(async () => {
    await cleanupMountedWrappers()
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  it('mounts the teleported modal into document.body so live controls remain queryable', async () => {
    const wrapper = mountModal([makeChannel('ch-picker')])
    await flushModal(wrapper)

    expect(wrapper.html()).toContain('teleport start')
    expect(getByTestId('add-media-button').textContent).toContain('Add Media')
  })

  it('shows an Add Media entry and opens a staged picker from current draft attachments', async () => {
    const mediaStore = useMediaStore()
    mediaStore.assetsById['f464d618-0f38-5b38-adda-9cf4a0b674eb'] = {
      assetId: 'f464d618-0f38-5b38-adda-9cf4a0b674eb',
      workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: 'f464d618-0f38-5b38-adda-9cf4a0b674eb.png',
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/f464d618-0f38-5b38-adda-9cf4a0b674eb/preview',
    }

    const loadAssets = mockLoadAssetsWithIds(mediaStore, ['f464d618-0f38-5b38-adda-9cf4a0b674eb'])

    const wrapper = mountModal([makeChannel('ch-picker')], {
      editingPublication: makeEditingPublication({
        assetIds: ['f464d618-0f38-5b38-adda-9cf4a0b674eb'],
      }),
    })
    await flushModal(wrapper)
    await flushModal(wrapper)

    expect(document.body.innerHTML).toContain('Add Media')
    expect(document.body.innerHTML).not.toContain('composer.dragDrop')
    expect(document.body.innerHTML).toContain('f464d618-0f38-5b38-adda-9cf4a0b674eb.png')

    getByTestId('attachment-remove-f464d618-0f38-5b38-adda-9cf4a0b674eb').click()
    await flushModal(wrapper)

    getByTestId('add-media-button').click()
    await flushModal(wrapper)

    expect(loadAssets).toHaveBeenCalledWith('READY,PENDING_UPLOAD,UPLOADING,FAILED')
    expect(document.body.innerHTML).toContain('Media Library')
    expect(
      getByTestId('picker-asset-card-f464d618-0f38-5b38-adda-9cf4a0b674eb').getAttribute(
        'data-selected',
      ),
    ).toBe('false')
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
    mediaStore.assetsById['f464d618-0f38-5b38-adda-9cf4a0b674eb'] = {
      assetId: 'f464d618-0f38-5b38-adda-9cf4a0b674eb',
      workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: 'f464d618-0f38-5b38-adda-9cf4a0b674eb.png',
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/f464d618-0f38-5b38-adda-9cf4a0b674eb/preview',
    }
    mediaStore.assetsById['13001fc9-a24f-54d3-bde1-cfa79074593c'] = {
      assetId: '13001fc9-a24f-54d3-bde1-cfa79074593c',
      workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      sourceType: 'UPLOADED',
      mediaType: 'image/png',
      status: 'READY',
      originalFilename: '13001fc9-a24f-54d3-bde1-cfa79074593c.png',
      fileSizeBytes: 1024,
      createdAt: '2026-06-19T12:00:00Z',
      previewUrl: '/api/media/assets/13001fc9-a24f-54d3-bde1-cfa79074593c/preview',
    }

    const loadAssets = mockLoadAssetsWithIds(mediaStore, [
      'f464d618-0f38-5b38-adda-9cf4a0b674eb',
      '13001fc9-a24f-54d3-bde1-cfa79074593c',
    ])

    const wrapper = mountModal([makeChannel('ch-picker')], {
      editingPublication: makeEditingPublication({
        assetIds: ['f464d618-0f38-5b38-adda-9cf4a0b674eb'],
      }),
    })
    await flushModal(wrapper)
    await flushModal(wrapper)

    getByTestId('add-media-button').click()
    await flushModal(wrapper)
    expect(loadAssets).toHaveBeenCalledWith('READY,PENDING_UPLOAD,UPLOADING,FAILED')
    getByTestId('picker-asset-card-13001fc9-a24f-54d3-bde1-cfa79074593c').click()
    await flushModal(wrapper)
    getByTestId('picker-cancel').click()
    await flushModal(wrapper)

    expect(document.body.innerHTML).toContain('f464d618-0f38-5b38-adda-9cf4a0b674eb.png')
    expect(document.body.innerHTML).not.toContain('13001fc9-a24f-54d3-bde1-cfa79074593c.png')

    getByTestId('add-media-button').click()
    await flushModal(wrapper)
    expect(
      getByTestId('picker-asset-card-f464d618-0f38-5b38-adda-9cf4a0b674eb').getAttribute(
        'data-selected',
      ),
    ).toBe('true')
    expect(
      getByTestId('picker-asset-card-13001fc9-a24f-54d3-bde1-cfa79074593c').getAttribute(
        'data-selected',
      ),
    ).toBe('false')

    getByTestId('picker-asset-card-f464d618-0f38-5b38-adda-9cf4a0b674eb').click()
    getByTestId('picker-asset-card-13001fc9-a24f-54d3-bde1-cfa79074593c').click()
    await flushModal(wrapper)
    getByTestId('picker-apply').click()
    await flushModal(wrapper)

    expect(document.body.innerHTML).not.toContain('f464d618-0f38-5b38-adda-9cf4a0b674eb.png')
    expect(document.body.innerHTML).toContain('13001fc9-a24f-54d3-bde1-cfa79074593c.png')
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
            assetId: '482fa2cb-b1d5-5d75-88fb-908c80a3b162',
            workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
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
          assetId: '482fa2cb-b1d5-5d75-88fb-908c80a3b162',
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
          sourceType: 'UPLOADED',
          mediaType: 'image/png',
          status: 'UPLOADING',
          originalFilename: 'uploaded.png',
          fileSizeBytes: 4096,
          createdAt: '2026-06-19T12:00:00Z',
          previewUrl: null,
        })
        .mockResolvedValueOnce({
          assetId: '482fa2cb-b1d5-5d75-88fb-908c80a3b162',
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
          sourceType: 'UPLOADED',
          mediaType: 'image/png',
          status: 'READY',
          originalFilename: 'uploaded.png',
          fileSizeBytes: 4096,
          createdAt: '2026-06-19T12:00:00Z',
          previewUrl: '/api/media/assets/482fa2cb-b1d5-5d75-88fb-908c80a3b162/preview',
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
      expect(
        getByTestId('picker-asset-card-482fa2cb-b1d5-5d75-88fb-908c80a3b162').getAttribute(
          'aria-disabled',
        ),
      ).toBe('true')
      expect(
        getByTestId('picker-asset-card-482fa2cb-b1d5-5d75-88fb-908c80a3b162').getAttribute(
          'data-selected',
        ),
      ).toBe('false')

      await vi.advanceTimersByTimeAsync(1000)
      await flushModal(wrapper)
      expect(loadAsset).toHaveBeenCalledWith('482fa2cb-b1d5-5d75-88fb-908c80a3b162')
      expect(
        getByTestId('picker-asset-card-482fa2cb-b1d5-5d75-88fb-908c80a3b162').getAttribute(
          'data-selected',
        ),
      ).toBe('false')

      await vi.advanceTimersByTimeAsync(1000)
      await flushModal(wrapper)
      expect(loadAsset).toHaveBeenCalledTimes(2)
      expect(
        getByTestId('picker-asset-card-482fa2cb-b1d5-5d75-88fb-908c80a3b162').getAttribute(
          'aria-disabled',
        ),
      ).toBe('false')
      expect(
        getByTestId('picker-asset-card-482fa2cb-b1d5-5d75-88fb-908c80a3b162').getAttribute(
          'data-selected',
        ),
      ).toBe('true')

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
          assetId: '1d24d2ca-6f0c-5951-a32f-41e4d79a02d2',
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
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
        assetId: '1d24d2ca-6f0c-5951-a32f-41e4d79a02d2',
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
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
      expect(
        getByTestId('picker-asset-card-1d24d2ca-6f0c-5951-a32f-41e4d79a02d2').getAttribute(
          'aria-disabled',
        ),
      ).toBe('true')

      getByTestId('picker-cancel').click()
      await flushModal(wrapper)

      await vi.advanceTimersByTimeAsync(5000)
      await flushModal(wrapper)
      expect(loadAsset).not.toHaveBeenCalled()

      getByTestId('add-media-button').click()
      await flushModal(wrapper)
      expect(document.body.innerHTML).toContain('No media assets yet')
      expect(document.body.innerHTML).not.toContain(
        'picker-asset-card-1d24d2ca-6f0c-5951-a32f-41e4d79a02d2',
      )
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
          assetId: 'e1fd64a2-d2b7-5e4d-8847-934585748a0b',
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
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
          assetId: 'e1fd64a2-d2b7-5e4d-8847-934585748a0b',
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
          sourceType: 'UPLOADED',
          mediaType: 'image/png',
          status: 'READY',
          originalFilename: 'flaky.png',
          fileSizeBytes: 2048,
          createdAt: '2026-06-19T12:00:00Z',
          previewUrl: '/api/media/assets/e1fd64a2-d2b7-5e4d-8847-934585748a0b/preview',
        })
        .mockResolvedValue({
          assetId: 'e1fd64a2-d2b7-5e4d-8847-934585748a0b',
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
          sourceType: 'UPLOADED',
          mediaType: 'image/png',
          status: 'READY',
          originalFilename: 'flaky.png',
          fileSizeBytes: 2048,
          createdAt: '2026-06-19T12:00:00Z',
          previewUrl: '/api/media/assets/e1fd64a2-d2b7-5e4d-8847-934585748a0b/preview',
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
      expect(
        getByTestId('picker-asset-card-e1fd64a2-d2b7-5e4d-8847-934585748a0b').getAttribute(
          'data-selected',
        ),
      ).toBe('false')

      await vi.advanceTimersByTimeAsync(1000)
      await flushModal(wrapper)
      expect(loadAsset).toHaveBeenCalledTimes(2)
      expect(
        getByTestId('picker-asset-card-e1fd64a2-d2b7-5e4d-8847-934585748a0b').getAttribute(
          'data-selected',
        ),
      ).toBe('true')

      getByTestId('picker-asset-card-e1fd64a2-d2b7-5e4d-8847-934585748a0b').click()
      await flushModal(wrapper)
      expect(
        getByTestId('picker-asset-card-e1fd64a2-d2b7-5e4d-8847-934585748a0b').getAttribute(
          'data-selected',
        ),
      ).toBe('false')

      mediaStore.upsertAsset({
        assetId: 'e1fd64a2-d2b7-5e4d-8847-934585748a0b',
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        sourceType: 'UPLOADED',
        mediaType: 'image/png',
        status: 'UPLOADING',
        originalFilename: 'flaky.png',
        fileSizeBytes: 2048,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: null,
      })
      mediaStore.upsertAsset({
        assetId: 'e1fd64a2-d2b7-5e4d-8847-934585748a0b',
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        sourceType: 'UPLOADED',
        mediaType: 'image/png',
        status: 'READY',
        originalFilename: 'flaky.png',
        fileSizeBytes: 2048,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: '/api/media/assets/e1fd64a2-d2b7-5e4d-8847-934585748a0b/preview',
      })

      await vi.advanceTimersByTimeAsync(2000)
      await flushModal(wrapper)
      expect(loadAsset).toHaveBeenCalledTimes(2)
      expect(
        getByTestId('picker-asset-card-e1fd64a2-d2b7-5e4d-8847-934585748a0b').getAttribute(
          'data-selected',
        ),
      ).toBe('false')
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
          assetId: 'd3fe7e7c-495c-5ff2-9263-4a19f48bdabe',
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
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
        assetId: 'd3fe7e7c-495c-5ff2-9263-4a19f48bdabe',
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
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
      expect(
        getByTestId('picker-asset-card-d3fe7e7c-495c-5ff2-9263-4a19f48bdabe').getAttribute(
          'aria-disabled',
        ),
      ).toBe('true')
      expect(
        getByTestId('picker-asset-card-d3fe7e7c-495c-5ff2-9263-4a19f48bdabe').getAttribute(
          'data-selected',
        ),
      ).toBe('false')

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

  afterEach(async () => {
    await cleanupMountedWrappers()
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
          assetId: '5d41f601-f8a7-5ca7-86a3-6c7ea6d8f479',
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
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
          assetId: '5d41f601-f8a7-5ca7-86a3-6c7ea6d8f479',
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
          sourceType: 'EXTERNAL',
          mediaType: 'image/jpeg',
          status: 'READY',
          originalFilename: 'mountain-1.jpg',
          fileSizeBytes: 1024,
          createdAt: '2026-06-19T12:00:00Z',
          previewUrl: '/api/media/assets/5d41f601-f8a7-5ca7-86a3-6c7ea6d8f479/preview',
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

  it('enforces the strictest attachment limit across selected channels and blocks apply above it', async () => {
    const mediaStore = useMediaStore()
    for (const id of [
      '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
      'b909d06f-92ea-5671-9f13-73475a89080e',
      '24add7a3-5877-5ff8-a4cb-11ff965d127f',
      'f0465146-355a-5da1-b916-5770deff3ff2',
      'd9c82a9b-8776-5d39-b0a5-bbc47895211f',
    ]) {
      mediaStore.assetsById[id] = {
        assetId: id,
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        sourceType: 'UPLOADED',
        mediaType: 'image/png',
        status: 'READY',
        originalFilename: `${id}.png`,
        fileSizeBytes: 1024,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: `/api/media/assets/${id}/preview`,
      }
    }
    mockLoadAssetsWithIds(mediaStore, [
      '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
      'b909d06f-92ea-5671-9f13-73475a89080e',
      '24add7a3-5877-5ff8-a4cb-11ff965d127f',
      'f0465146-355a-5da1-b916-5770deff3ff2',
      'd9c82a9b-8776-5d39-b0a5-bbc47895211f',
    ])

    const channels = [
      makeChannel('b431194a-5205-577d-b212-98398966a944', {
        provider: 'linkedin',
        name: 'LinkedIn',
      } as Partial<TestChannel>),
      makeChannel('ch-tw', { provider: 'twitter', name: 'Twitter' } as Partial<TestChannel>),
    ]
    ;(channels[0] as { maxAttachments?: number }).maxAttachments = 9
    ;(channels[1] as { maxAttachments?: number }).maxAttachments = 4

    const wrapper = mountModal(channels)
    await flushModal(wrapper)
    await flushModal(wrapper)

    getByTestId('add-media-button').click()
    await flushModal(wrapper)

    for (const id of [
      '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
      'b909d06f-92ea-5671-9f13-73475a89080e',
      '24add7a3-5877-5ff8-a4cb-11ff965d127f',
      'f0465146-355a-5da1-b916-5770deff3ff2',
      'd9c82a9b-8776-5d39-b0a5-bbc47895211f',
    ]) {
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
    for (const id of [
      '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
      'b909d06f-92ea-5671-9f13-73475a89080e',
      '24add7a3-5877-5ff8-a4cb-11ff965d127f',
    ]) {
      mediaStore.assetsById[id] = {
        assetId: id,
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
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
      mediaStore.assetIds = [
        '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
        'b909d06f-92ea-5671-9f13-73475a89080e',
        '24add7a3-5877-5ff8-a4cb-11ff965d127f',
      ]
    })

    const channels = [
      makeChannel('b431194a-5205-577d-b212-98398966a944', {
        provider: 'linkedin',
        name: 'LinkedIn',
      } as Partial<TestChannel>),
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
      editingPublication: makeEditingPublication({
        assetIds: [
          '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
          'b909d06f-92ea-5671-9f13-73475a89080e',
          '24add7a3-5877-5ff8-a4cb-11ff965d127f',
        ],
      }),
    })
    await flushModal(editWrapper)
    await flushModal(editWrapper)

    expect(document.body.innerHTML).toContain('8bdec504-2ed1-59e4-8a4a-c068789d03d2.png')
    expect(document.body.innerHTML).toContain('b909d06f-92ea-5671-9f13-73475a89080e.png')
    expect(document.body.innerHTML).toContain('24add7a3-5877-5ff8-a4cb-11ff965d127f.png')

    // Switch to the lower-limit Twitter channel
    const twitterButton = Array.from(
      document.querySelectorAll('[data-channel-provider="twitter"]'),
    )[0]
    expect(twitterButton).toBeDefined()
    twitterButton!.click()
    await flushModal(editWrapper)

    // All 3 attachments MUST remain in the draft (no auto-removal)
    expect(document.body.innerHTML).toContain('8bdec504-2ed1-59e4-8a4a-c068789d03d2.png')
    expect(document.body.innerHTML).toContain('b909d06f-92ea-5671-9f13-73475a89080e.png')
    expect(document.body.innerHTML).toContain('24add7a3-5877-5ff8-a4cb-11ff965d127f.png')

    // Close the wrapper if still mounted.
    await editWrapper.unmount()
  })

  it('surfaces the limit warning when draft attachments exceed the selected channel limit, blocking publish/schedule', async () => {
    const mediaStore = useMediaStore()
    for (const id of [
      '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
      'b909d06f-92ea-5671-9f13-73475a89080e',
      '24add7a3-5877-5ff8-a4cb-11ff965d127f',
    ]) {
      mediaStore.assetsById[id] = {
        assetId: id,
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
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
      mediaStore.assetIds = [
        '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
        'b909d06f-92ea-5671-9f13-73475a89080e',
        '24add7a3-5877-5ff8-a4cb-11ff965d127f',
      ]
    })

    // LinkedIn with limit 2 — three draft attachments exceed it.
    const channels = [
      makeChannel('b431194a-5205-577d-b212-98398966a944', {
        name: 'LinkedIn',
      } as Partial<TestChannel>),
    ]
    ;(channels[0] as { maxAttachments?: number }).maxAttachments = 2

    const wrapper = mountModal(channels, {
      editingPublication: makeEditingPublication({
        assetIds: [
          '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
          'b909d06f-92ea-5671-9f13-73475a89080e',
          '24add7a3-5877-5ff8-a4cb-11ff965d127f',
        ],
        accountId: 'b431194a-5205-577d-b212-98398966a944',
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

    const anyEnabledSubmit = Array.from(document.querySelectorAll('.ui-button')).find(
      (btn) => !(btn as HTMLButtonElement).hasAttribute('disabled'),
    )
    expect(anyEnabledSubmit).toBeUndefined()

    await wrapper.unmount()
  })
})

describe('CreatePostModal.vue — AI assistant', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    vi.clearAllMocks()
    vi.spyOn(usePublishingStore(), 'scheduleMultiChannel').mockResolvedValue({
      status: 'ALL_CREATED',
      results: [
        {
          socialAccountId: 'c98c3b43-390a-556e-8fe3-622a2b97918a',
          publicationId: '9cdff3ca-0dd3-53b3-8cb9-b223e3e93037',
          status: 'CREATED',
        },
      ],
    })
    vi.mocked(createShortlink).mockReset()
    vi.mocked(toast.warning).mockReset()
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:mock-preview'),
      revokeObjectURL: vi.fn(),
    })
  })

  afterEach(async () => {
    await cleanupMountedWrappers()
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  it('generates content, compares alternatives, and inserts the selected version into the editor', async () => {
    vi.mocked(generateAiPostContent).mockResolvedValueOnce({
      content: 'Clean code keeps software projects readable, testable, and easier to evolve.',
    })
    vi.mocked(regenerateAiPostContent).mockResolvedValueOnce({
      content:
        'Clean code turns software projects into systems teams can trust, extend, and ship faster.',
    })

    const wrapper = mountModal([makeChannel('ac36c5e0-c4a6-5de8-abfe-1f3c50f83491')])
    await flushModal(wrapper)

    getByTestId('composer-ai-assist').click()
    await flushModal(wrapper)

    const promptInput = getByTestId('composer-ai-prompt') as HTMLTextAreaElement
    promptInput.value = 'importance of clean code in software projects'
    promptInput.dispatchEvent(new Event('input'))
    await flushModal(wrapper)

    getByTestId('composer-ai-generate').click()
    await flushModal(wrapper)

    expect(generateAiPostContent).toHaveBeenCalledTimes(1)
    expect(generateAiPostContent).toHaveBeenCalledWith(
      expect.objectContaining({
        prompt: 'importance of clean code in software projects',
      }),
    )
    expect(document.body.innerHTML).toContain('Clean code keeps software projects readable')

    getByTestId('composer-ai-regenerate').click()
    await flushModal(wrapper)

    expect(regenerateAiPostContent).toHaveBeenCalledTimes(1)
    expect(document.body.innerHTML).toContain('Compare versions')
    expect(document.body.innerHTML).toContain(
      'Clean code turns software projects into systems teams can trust',
    )

    getByTestId('composer-ai-version-1').click()
    await flushModal(wrapper)

    getByTestId('composer-ai-accept').click()
    await flushModal(wrapper)

    const composerTextarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    expect(composerTextarea.value).toBe(
      'Clean code keeps software projects readable, testable, and easier to evolve.',
    )
  })

  it('maps assistant fields into the generation request and renders the mapped response', async () => {
    vi.mocked(generateAiPostContent).mockResolvedValueOnce({
      content: 'Mapped response content',
      title: 'Mapped response title',
      suggestions: [{ text: 'Use a measurable result.' }],
    })
    const auth = useAuthStore()
    auth.user = {
      principalId: '18f3c8ec-b5fd-5d86-ac83-465965479890',
      email: 'author@example.com',
      username: 'author',
      emailStatus: 'VERIFIED',
      displayIdentity: 'Author Name',
    }

    const wrapper = mountModal([makeChannel('ac36c5e0-c4a6-5de8-abfe-1f3c50f83491')])
    await flushModal(wrapper)
    getByTestId('composer-ai-assist').click()
    await flushModal(wrapper)

    const setInput = (selector: string, value: string) => {
      const input = document.querySelector<HTMLInputElement | HTMLSelectElement>(selector)
      expect(input).not.toBeNull()
      input!.value = value
      input!.dispatchEvent(new Event(input instanceof HTMLSelectElement ? 'change' : 'input'))
    }
    setInput('[data-testid="composer-ai-prompt"]', '  launch a developer tool  ')
    setInput('#ai-industry-input', ' SaaS ')
    setInput('#ai-audience-input', ' engineering leaders ')
    setInput('#ai-format-select', 'tips')
    setInput('#ai-tone-select', 'educational')
    setInput('#ai-length-select', 'long')
    setInput('#ai-keywords-input', ' Kotlin, , cloud ')
    await flushModal(wrapper)

    getByTestId('composer-ai-generate').click()
    await flushModal(wrapper)

    expect(generateAiPostContent).toHaveBeenCalledWith({
      prompt: 'launch a developer tool',
      context: {
        user_profile: {
          display_name: 'Author Name',
          email: 'author@example.com',
          username: 'author',
        },
        industry: 'SaaS',
        target_audience: 'engineering leaders',
      },
      options: {
        format: 'tips',
        tone: 'educational',
        length: 'long',
        keywords: ['Kotlin', 'cloud'],
      },
    })
    expect(document.body.textContent).toContain('Mapped response content')
  })

  it('rejects a blank prompt before making an AI request', async () => {
    const wrapper = mountModal([makeChannel('ac36c5e0-c4a6-5de8-abfe-1f3c50f83491')])
    await flushModal(wrapper)

    getByTestId('composer-ai-assist').click()
    await flushModal(wrapper)
    const promptInput = getByTestId('composer-ai-prompt') as HTMLTextAreaElement
    promptInput.value = '   '
    promptInput.dispatchEvent(new Event('input'))
    await flushModal(wrapper)

    getByTestId('composer-ai-generate').click()
    await flushModal(wrapper)

    expect(generateAiPostContent).not.toHaveBeenCalled()
    expect(document.querySelector('[role="alert"]')?.textContent).toContain(
      'Enter a prompt to generate content.',
    )
  })

  it('shows the empty-response message when the AI response has no content', async () => {
    vi.mocked(generateAiPostContent).mockResolvedValueOnce({ content: undefined } as never)

    const wrapper = mountModal([makeChannel('ac36c5e0-c4a6-5de8-abfe-1f3c50f83491')])
    await flushModal(wrapper)
    getByTestId('composer-ai-assist').click()
    await flushModal(wrapper)

    const promptInput = getByTestId('composer-ai-prompt') as HTMLTextAreaElement
    promptInput.value = 'a useful prompt'
    promptInput.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    getByTestId('composer-ai-generate').click()
    await flushModal(wrapper)

    expect(document.querySelector('[role="alert"]')?.textContent).toContain(
      'AI service returned empty content.',
    )
  })

  it('falls back to the generic error when a failed AI request has no message', async () => {
    vi.mocked(generateAiPostContent).mockRejectedValueOnce(new Error(''))

    const wrapper = mountModal([makeChannel('ac36c5e0-c4a6-5de8-abfe-1f3c50f83491')])
    await flushModal(wrapper)
    getByTestId('composer-ai-assist').click()
    await flushModal(wrapper)

    const promptInput = getByTestId('composer-ai-prompt') as HTMLTextAreaElement
    promptInput.value = 'a useful prompt'
    promptInput.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    getByTestId('composer-ai-generate').click()
    await flushModal(wrapper)

    expect(document.querySelector('[role="alert"]')?.textContent).toContain(
      'Unable to generate content right now.',
    )
  })

  it('uses the next-month reset date when a rate-limit error omits resetAt', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-03T12:00:00.000Z'))
    try {
      vi.mocked(generateAiPostContent).mockRejectedValueOnce(
        Object.assign(new Error('Rate limited'), { status: 429 }),
      )

      const wrapper = mountModal([makeChannel('ac36c5e0-c4a6-5de8-abfe-1f3c50f83491')])
      await flushModal(wrapper)
      getByTestId('composer-ai-assist').click()
      await flushModal(wrapper)

      const promptInput = getByTestId('composer-ai-prompt') as HTMLTextAreaElement
      promptInput.value = 'a useful prompt'
      promptInput.dispatchEvent(new Event('input'))
      await flushModal(wrapper)
      getByTestId('composer-ai-generate').click()
      await flushModal(wrapper)

      expect(document.body.textContent).toContain('Resets on')
      expect(document.body.textContent).not.toContain('Invalid Date')
    } finally {
      vi.useRealTimers()
    }
  })

  it('shows the monthly limit message, reset date, and upgrade options when generation is rate limited', async () => {
    vi.mocked(generateAiPostContent).mockRejectedValueOnce(
      Object.assign(new Error('Rate limited'), {
        status: 429,
        resetAt: '2026-08-31T00:00:00.000Z',
      }),
    )

    const wrapper = mountModal([makeChannel('ac36c5e0-c4a6-5de8-abfe-1f3c50f83491')])
    await flushModal(wrapper)

    getByTestId('composer-ai-assist').click()
    await flushModal(wrapper)

    const promptInput = getByTestId('composer-ai-prompt') as HTMLTextAreaElement
    promptInput.value = 'best programming languages to learn in 2026'
    promptInput.dispatchEvent(new Event('input'))
    await flushModal(wrapper)

    getByTestId('composer-ai-generate').click()
    await flushModal(wrapper)

    expect(document.body.innerHTML).toContain('Monthly limit reached')
    expect(document.body.innerHTML).toContain('Resets on')
    expect(document.body.innerHTML).toContain('Free 10/mo')
    expect(document.body.innerHTML).toContain('Pro 100/mo')
    expect(document.body.innerHTML).toContain('Team 500/mo')

    await wrapper.unmount()
  })

  it('optimizes the current draft and sends the draft content to the AI service', async () => {
    vi.mocked(optimizeAiPostContent).mockResolvedValueOnce({ content: 'Optimized draft' })

    const wrapper = mountModal([makeChannel('ac36c5e0-c4a6-5de8-abfe-1f3c50f83491')])
    await flushModal(wrapper)
    getByTestId('composer-ai-assist').click()
    await flushModal(wrapper)

    const composerTextarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    composerTextarea.value = 'A draft that needs optimization'
    composerTextarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)

    getByTestId('composer-ai-optimize').click()
    await flushModal(wrapper)

    expect(optimizeAiPostContent).toHaveBeenCalledWith(
      expect.objectContaining({
        content: 'A draft that needs optimization',
      }),
    )
    expect(document.body.innerHTML).toContain('Optimized draft')
  })

  it('keeps optimize disabled and reports the draft guard when no current content exists', async () => {
    const wrapper = mountModal([makeChannel('ac36c5e0-c4a6-5de8-abfe-1f3c50f83491')])
    await flushModal(wrapper)
    getByTestId('composer-ai-assist').click()
    await flushModal(wrapper)

    const optimizeButton = getByTestId('composer-ai-optimize') as HTMLButtonElement
    expect(optimizeButton.disabled).toBe(true)
    expect(optimizeAiPostContent).not.toHaveBeenCalled()
  })

  it('keeps regenerate disabled until a generated version exists', async () => {
    const wrapper = mountModal([makeChannel('ac36c5e0-c4a6-5de8-abfe-1f3c50f83491')])
    await flushModal(wrapper)
    getByTestId('composer-ai-assist').click()
    await flushModal(wrapper)

    const regenerateButton = getByTestId('composer-ai-regenerate') as HTMLButtonElement
    expect(regenerateButton.disabled).toBe(true)
    expect(regenerateAiPostContent).not.toHaveBeenCalled()
  })

  it('emits create-another, clears the draft, and unchecks create-another after creation', async () => {
    const store = usePublishingStore()
    const scheduleSpy = vi.spyOn(store, 'scheduleMultiChannel').mockResolvedValue({
      status: 'ALL_CREATED',
      results: [
        {
          socialAccountId: 'ac36c5e0-c4a6-5de8-abfe-1f3c50f83491',
          publicationId: 'cc0ced20-56b1-58d7-8072-17242d4c9d68',
          status: 'CREATED',
        },
      ],
    })
    const wrapper = mountModal([makeChannel('ac36c5e0-c4a6-5de8-abfe-1f3c50f83491')])
    await flushModal(wrapper)

    const composerTextarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    composerTextarea.value = 'First post content'
    composerTextarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)

    const createAnotherInput = Array.from(
      document.querySelectorAll<HTMLInputElement>('input[type="checkbox"]'),
    ).find((input) => input.parentElement?.textContent?.includes('Create Another'))
    expect(createAnotherInput).toBeDefined()
    createAnotherInput!.click()
    await flushModal(wrapper)

    const scheduleButton = Array.from(
      document.querySelectorAll<HTMLButtonElement>('.ui-button'),
    ).find((button) => button.textContent?.includes('Schedule Now'))
    expect(scheduleButton).toBeDefined()
    scheduleButton!.click()
    await vi.waitFor(() => expect(scheduleSpy).toHaveBeenCalledTimes(1))
    await flushModal(wrapper)

    expect(scheduleSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        targets: [expect.objectContaining({ content: 'First post content' })],
      }),
    )
    expect(wrapper.emitted('created')).toEqual([
      [{ keepOpen: true, publicationId: 'cc0ced20-56b1-58d7-8072-17242d4c9d68' }],
    ])
    expect(composerTextarea.value).toBe('')
    expect(createAnotherInput!.checked).toBe(false)
  })

  it('uses the translated submit label for each schedule mode and edit mode', async () => {
    const wrapper = mountModal([makeChannel('ac36c5e0-c4a6-5de8-abfe-1f3c50f83491')])
    await flushModal(wrapper)

    const submitButton = () => document.querySelector<HTMLButtonElement>('.ui-button')
    expect(submitButton()?.textContent).toContain('Schedule Now')

    const nextMode = getByTestId('schedule-mode-next').querySelector<HTMLInputElement>('input')
    expect(nextMode).not.toBeNull()
    nextMode!.click()
    await flushModal(wrapper)
    expect(submitButton()?.textContent).toContain('Next Schedule')

    const customMode = getByTestId('schedule-mode-custom').querySelector<HTMLInputElement>('input')
    expect(customMode).not.toBeNull()
    customMode!.click()
    await flushModal(wrapper)
    expect(submitButton()?.textContent).toContain('Schedule Post')

    await wrapper.unmount()
    const editWrapper = mountModal([makeChannel('ac36c5e0-c4a6-5de8-abfe-1f3c50f83491')], {
      editingPublication: makeEditingPublication(),
    })
    await flushModal(editWrapper)
    await flushModal(editWrapper)
    expect(submitButton()?.textContent).toContain('Save Changes')
    await editWrapper.setProps({ isOpen: false })
    await flushModal(editWrapper)
    await editWrapper.unmount()
  })

  it('closes through Escape and restores focus to the trigger', async () => {
    const trigger = document.createElement('button')
    trigger.type = 'button'
    trigger.textContent = 'Open composer'
    document.body.appendChild(trigger)
    trigger.focus()
    const wrapper = mountModal(
      [makeChannel('ch-focus')],
      {},
      {
        ComposerMediaPickerShell: { template: '<div data-testid="media-picker-stub" />' },
      },
    )
    await flushModal(wrapper)

    const overlay = document.body.querySelector('.fixed.inset-0')
    expect(overlay).not.toBeNull()
    overlay!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(wrapper.emitted('close')).toHaveLength(1)

    await wrapper.setProps({ isOpen: false })
    await flushModal(wrapper)
    expect(document.activeElement).toBe(trigger)
    await wrapper.unmount()
    document.body.removeChild(trigger)
  })

  it('closes when the backdrop is clicked', async () => {
    const wrapper = mountModal([makeChannel('ch-backdrop')])
    await flushModal(wrapper)
    const overlay = document.body.querySelector('.fixed.inset-0')
    expect(overlay).not.toBeNull()
    overlay!.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    expect(wrapper.emitted('close')).toHaveLength(1)

    await wrapper.setProps({ isOpen: false })
    await flushModal(wrapper)
    await wrapper.unmount()
  })

  it('closes when the cancel action is clicked', async () => {
    const wrapper = mountModal([makeChannel('ch-cancel')])
    await flushModal(wrapper)
    const cancelButton = Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find(
      (button) => button.textContent?.includes('Cancel'),
    )
    expect(cancelButton).toBeDefined()
    cancelButton!.click()
    expect(wrapper.emitted('close')).toHaveLength(1)
    await wrapper.setProps({ isOpen: false })
    await flushModal(wrapper)
    await wrapper.unmount()
  })

  it('prunes inactive channels and defaults to the remaining active channel', async () => {
    const channels = [makeChannel('ch-first'), makeChannel('ch-second')]
    const wrapper = mountModal(channels)
    await flushModal(wrapper)

    const publishingStore = usePublishingStore()
    publishingStore.channels = [
      makeChannel('ch-inactive', { status: 'INACTIVE' }),
      makeChannel('ch-third'),
    ]
    await flushModal(wrapper)

    const buttons = Array.from(
      document.querySelectorAll<HTMLButtonElement>('[data-testid="channel-button"]'),
    )
    expect(buttons).toHaveLength(1)
    expect(buttons[0]?.getAttribute('aria-pressed')).toBe('true')
  })

  it('updates an edited publication without assetIds until attachments are changed', async () => {
    const publishingStore = usePublishingStore()
    const updatePost = vi.spyOn(publishingStore, 'updatePost').mockResolvedValue({} as Publication)
    const wrapper = mountModal([makeChannel('73a4665b-8dcc-5f27-8cba-bca328b27bde')], {
      editingPublication: makeEditingPublication({
        accountId: '73a4665b-8dcc-5f27-8cba-bca328b27bde',
        assetIds: [],
      }),
    })
    await flushModal(wrapper)
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'Edited content'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    const saveButton = Array.from(document.querySelectorAll<HTMLButtonElement>('.ui-button')).find(
      (button) => button.textContent?.includes('Save Changes'),
    )
    expect(saveButton).toBeDefined()
    saveButton!.click()
    await vi.waitFor(() => expect(updatePost).toHaveBeenCalledTimes(1))

    expect(updatePost).toHaveBeenCalledWith(
      '7fc06d85-71cb-5e22-bab4-c881fe923beb',
      expect.not.objectContaining({ assetIds: expect.anything() }),
    )
    expect(wrapper.emitted('updated')).toHaveLength(1)
  })

  it('initializes a new post from initialDate when the modal is opened', async () => {
    vi.useFakeTimers()
    const current = new Date(2026, 7, 3, 12, 0, 0)
    const sameDay = new Date(2026, 7, 3, 13, 30, 0)
    const futureDay = new Date(2026, 7, 4, 9, 15, 0)
    vi.setSystemTime(current)
    try {
      const wrapper = mountModal([makeChannel('ch-create')], {
        isOpen: false,
        initialDate: sameDay.toISOString(),
      })
      await flushModal(wrapper)

      await wrapper.setProps({ isOpen: true })
      await flushModal(wrapper)
      expect(
        (getByTestId('schedule-mode-custom').querySelector('input') as HTMLInputElement).checked,
      ).toBe(true)
      expect((getByTestId('schedule-time-input') as HTMLInputElement).value).toBe('13:30')
      expect((getByTestId('schedule-time-input') as HTMLInputElement).min).toBe('12:05')

      await wrapper.setProps({ isOpen: false, initialDate: futureDay.toISOString() })
      await flushModal(wrapper)
      await wrapper.setProps({ isOpen: true })
      await flushModal(wrapper)
      expect((getByTestId('schedule-time-input') as HTMLInputElement).min).toBe('00:00')
    } finally {
      vi.useRealTimers()
    }
  })

  it('shortens each distinct URL once and replaces every occurrence before submitting', async () => {
    const schedulePost = vi.spyOn(usePublishingStore(), 'scheduleMultiChannel').mockResolvedValue({
      status: 'ALL_CREATED',
      results: [
        {
          socialAccountId: 'f28eed72-9176-538b-8927-d851062a2970',
          publicationId: '347b54cf-b463-5c93-9fd4-817cbaa2a62a',
          status: 'CREATED',
        },
      ],
    })
    vi.mocked(createShortlink)
      .mockResolvedValueOnce({
        id: 'ce43975e-96af-59af-bea3-7d369916d24d',
        shortUrl: 'https://pt.link/a',
        destinationUrl: 'https://example.com/article',
      })
      .mockResolvedValueOnce({
        id: 'd4e70d18-926a-59f7-85ec-d90b1cff371f',
        shortUrl: 'https://pt.link/b',
        destinationUrl: 'https://other.example/page',
      })
    const wrapper = mountModal([makeChannel('f28eed72-9176-538b-8927-d851062a2970')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value =
      'Read https://example.com/article and https://other.example/page, then https://example.com/article.'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    Array.from(document.querySelectorAll<HTMLButtonElement>('.ui-button'))
      .find((button) => button.textContent?.includes('Schedule Now'))
      ?.click()
    await flushPromises()
    await flushModal(wrapper)

    expect(createShortlink).toHaveBeenCalledTimes(2)
    expect(createShortlink).toHaveBeenNthCalledWith(
      1,
      'https://example.com/article',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
    expect(createShortlink).toHaveBeenNthCalledWith(
      2,
      'https://other.example/page',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
    expect(schedulePost).toHaveBeenCalledWith(
      expect.objectContaining({
        targets: [
          expect.objectContaining({
            content: 'Read https://pt.link/a and https://pt.link/b, then https://pt.link/a.',
          }),
        ],
      }),
    )
    expect(document.querySelector('[data-testid="shortlink-opt-in"]')).toBeNull()
    expect(document.querySelector('[data-testid="shortlink-use"]')).toBeNull()
    expect(document.querySelector('[data-testid="shortlink-keep-original"]')).toBeNull()
  })

  it('keeps publishing when an individual shortlink request fails and shows a non-blocking warning', async () => {
    const schedulePost = vi.spyOn(usePublishingStore(), 'scheduleMultiChannel').mockResolvedValue({
      status: 'ALL_CREATED',
      results: [
        {
          socialAccountId: '324d00a2-87e7-5cc5-a803-a31fe9c45163',
          publicationId: '347b54cf-b463-5c93-9fd4-817cbaa2a62a',
          status: 'CREATED',
        },
      ],
    })
    vi.mocked(createShortlink)
      .mockRejectedValueOnce(new Error('shortlink unavailable'))
      .mockResolvedValueOnce({
        id: '5aaeca83-fc27-5e02-8a0d-98f7aeea6a01',
        shortUrl: 'https://pt.link/b',
        destinationUrl: 'https://other.example/page',
      })
    const wrapper = mountModal([makeChannel('324d00a2-87e7-5cc5-a803-a31fe9c45163')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'https://example.com/article and https://other.example/page'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    Array.from(document.querySelectorAll<HTMLButtonElement>('.ui-button'))
      .find((button) => button.textContent?.includes('Schedule Now'))
      ?.click()
    await flushPromises()
    await flushModal(wrapper)

    expect(createShortlink).toHaveBeenCalledTimes(2)
    expect(schedulePost).toHaveBeenCalledWith(
      expect.objectContaining({
        targets: [
          expect.objectContaining({ content: 'https://example.com/article and https://pt.link/b' }),
        ],
      }),
    )
    expect(toast.warning).toHaveBeenCalledWith(
      'Some links could not be shortened and remain unchanged. https://example.com/article',
    )
  })

  it('keeps the modal open and preserves the draft when some targets fail', async () => {
    vi.spyOn(usePublishingStore(), 'scheduleMultiChannel').mockResolvedValue({
      status: 'PARTIAL',
      results: [
        {
          socialAccountId: '6eeca0c2-97ec-588e-966c-bd4bbacdb5d9',
          publicationId: 'fc18a1ef-cca8-5cf8-a32a-27629468a359',
          status: 'CREATED',
        },
        {
          socialAccountId: 'c15e6f35-c95f-5cca-b943-da6f94483679',
          errorMessage: 'Threads failed',
          status: 'FAILED',
        },
      ],
    })
    const wrapper = mountModal([
      makeChannel('ch-partial-li', {
        accountId: '6eeca0c2-97ec-588e-966c-bd4bbacdb5d9',
        name: 'LinkedIn',
      }),
      makeChannel('ch-partial-th', {
        accountId: 'c15e6f35-c95f-5cca-b943-da6f94483679',
        provider: 'threads',
        name: 'Threads',
      }),
    ])
    await flushModal(wrapper)
    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'Retryable draft'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)

    getByTestId('customize-toggle').click()
    await flushModal(wrapper)
    Array.from(document.querySelectorAll<HTMLButtonElement>('[data-testid="customize-tab"]'))
      .find((tab) => tab.textContent?.includes('LinkedIn'))
      ?.click()
    await flushModal(wrapper)
    const scheduleButton = Array.from(
      document.querySelectorAll<HTMLButtonElement>('.ui-button'),
    ).find((button) => button.textContent?.includes('Schedule Now'))
    scheduleButton?.click()
    await flushPromises()
    await flushModal(wrapper)

    expect(wrapper.emitted('created')).toEqual([
      [{ keepOpen: true, publicationId: 'fc18a1ef-cca8-5cf8-a32a-27629468a359' }],
    ])
    expect((getByTestId('composer-textarea') as HTMLTextAreaElement).value).toBe('Retryable draft')
    expect(document.body.textContent).toContain('Threads: Threads failed')
    const channelButtons = Array.from(
      document.querySelectorAll<HTMLButtonElement>('[data-testid="channel-button"]'),
    )
    expect(
      channelButtons
        .find((button) => button.getAttribute('data-channel-provider') === 'threads')
        ?.getAttribute('aria-pressed'),
    ).toBe('true')
    expect(
      channelButtons
        .find((button) => button.getAttribute('data-channel-provider') === 'linkedin')
        ?.getAttribute('aria-pressed'),
    ).toBe('false')
    const customizeTabs = Array.from(
      document.querySelectorAll<HTMLButtonElement>('[data-testid="customize-tab"]'),
    )
    expect(
      customizeTabs.find((tab) => tab.textContent?.includes('Base'))?.getAttribute('aria-selected'),
    ).toBe('true')
    expect(customizeTabs.some((tab) => tab.textContent?.includes('LinkedIn'))).toBe(false)
    expect(
      customizeTabs
        .find((tab) => tab.textContent?.includes('Threads'))
        ?.getAttribute('aria-selected'),
    ).toBe('false')

    scheduleButton?.click()
    await flushPromises()
    await flushModal(wrapper)

    const retryPayload = vi.mocked(usePublishingStore().scheduleMultiChannel).mock.calls.at(-1)?.[0]
    expect(retryPayload?.targets.map((target) => target.socialAccountId)).toEqual([
      'c15e6f35-c95f-5cca-b943-da6f94483679',
    ])
  })

  it('submits content with no URL without calling the shortlink service', async () => {
    const schedulePost = vi.spyOn(usePublishingStore(), 'scheduleMultiChannel').mockResolvedValue({
      status: 'ALL_CREATED',
      results: [
        {
          socialAccountId: 'ef753858-7a8d-5043-ba04-5fe0a748b415',
          publicationId: '33cd4fc0-a8e8-5acc-bf53-4f047e169715',
          status: 'CREATED',
        },
      ],
    })
    const wrapper = mountModal([makeChannel('ef753858-7a8d-5043-ba04-5fe0a748b415')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'A post without a URL'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    Array.from(document.querySelectorAll<HTMLButtonElement>('.ui-button'))
      .find((button) => button.textContent?.includes('Schedule Now'))
      ?.click()
    await flushPromises()
    await flushModal(wrapper)

    expect(createShortlink).not.toHaveBeenCalled()
    expect(schedulePost).toHaveBeenCalledWith(
      expect.objectContaining({
        targets: [expect.objectContaining({ content: 'A post without a URL' })],
      }),
    )
  })

  it('does not create or render shortlink controls while editing a publication', async () => {
    const updatePost = vi
      .spyOn(usePublishingStore(), 'updatePost')
      .mockResolvedValue({} as Publication)
    const wrapper = mountModal([makeChannel('9aa02deb-2ff8-5b1a-9176-f9c9a00944a4')], {
      editingPublication: makeEditingPublication({
        content: 'Read https://example.com/article',
        accountId: '9aa02deb-2ff8-5b1a-9176-f9c9a00944a4',
        assetIds: [],
      }),
    })
    await flushModal(wrapper)

    expect(document.querySelector('[data-testid="shortlink-opt-in"]')).toBeNull()
    expect(document.querySelector('[data-testid="shortlink-create"]')).toBeNull()
    expect(createShortlink).not.toHaveBeenCalled()

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'Updated https://example.com/article'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    Array.from(document.querySelectorAll<HTMLButtonElement>('.ui-button'))
      .find((button) => button.textContent?.includes('Save Changes'))
      ?.click()
    await flushModal(wrapper)

    expect(updatePost).toHaveBeenCalledWith(
      '7fc06d85-71cb-5e22-bab4-c881fe923beb',
      expect.objectContaining({ content: 'Updated https://example.com/article' }),
    )
    expect(createShortlink).not.toHaveBeenCalled()
  })
})

describe('CreatePostModal.vue — inline composer media layout', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    vi.clearAllMocks()
    vi.spyOn(usePublishingStore(), 'scheduleMultiChannel').mockResolvedValue({
      status: 'ALL_CREATED',
      results: [
        {
          socialAccountId: 'c98c3b43-390a-556e-8fe3-622a2b97918a',
          publicationId: '9cdff3ca-0dd3-53b3-8cb9-b223e3e93037',
          status: 'CREATED',
        },
      ],
    })
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
    const wrapper = mountModal([makeChannel('8bd79d9b-a3f2-51e7-9943-dd649728e5ce')])
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
    const wrapper = mountModal([makeChannel('8bd79d9b-a3f2-51e7-9943-dd649728e5ce')])
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
      assetId: '793f573e-1865-5bd5-976c-5afaa7db95f6',
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
    const mediaStore = useMediaStore()
    const createAndUploadSpy = vi.spyOn(mediaStore, 'createAndUpload')

    const wrapper = mountModal([makeChannel('8bd79d9b-a3f2-51e7-9943-dd649728e5ce')])
    await flushModal(wrapper)

    const pickerUploadInput = getByTestId('picker-upload-input') as HTMLInputElement
    const svgFile = new File(['<svg></svg>'], 'diagram.svg', { type: 'image/svg+xml' })
    Object.defineProperty(pickerUploadInput, 'files', {
      configurable: true,
      value: [svgFile],
    })
    pickerUploadInput.dispatchEvent(new Event('change'))
    await flushModal(wrapper)

    const errorEl = document.querySelector('[role="alert"]')
    expect(errorEl).toBeTruthy()
    expect(errorEl?.textContent).toContain('Unsupported media format')
    expect(document.querySelector('[data-testid="inline-local-upload"]')).toBeNull()
    expect(createAndUploadSpy).not.toHaveBeenCalled()
  })

  it('accepts pasted images in the composer textarea using the same inline upload flow', async () => {
    const wrapper = mountModal([makeChannel('8bd79d9b-a3f2-51e7-9943-dd649728e5ce')])
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
    const wrapper = mountModal([makeChannel('8bd79d9b-a3f2-51e7-9943-dd649728e5ce')])
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

  it('rejects an unsupported dropped file over the composer surface via addFiles path', async () => {
    const wrapper = mountModal([makeChannel('8bd79d9b-a3f2-51e7-9943-dd649728e5ce')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea')
    const svgFile = new File(['<svg></svg>'], 'diagram.svg', { type: 'image/svg+xml' })
    const dropEvent = new Event('drop', { bubbles: true, cancelable: true }) as Event & {
      dataTransfer: { files: File[] }
    }
    dropEvent.dataTransfer = { files: [svgFile] }
    textarea.dispatchEvent(dropEvent)
    await flushModal(wrapper)

    const errorEl = document.querySelector('[role="alert"]')
    expect(errorEl).toBeTruthy()
    expect(errorEl?.textContent).toContain('Unsupported media format')
  })

  it('accepts a valid file when dropped alongside an unsupported one', async () => {
    const wrapper = mountModal([makeChannel('8bd79d9b-a3f2-51e7-9943-dd649728e5ce')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea')
    const svgFile = new File(['<svg></svg>'], 'diagram.svg', { type: 'image/svg+xml' })
    const pngFile = new File(['png'], 'photo.png', { type: 'image/png' })
    const dropEvent = new Event('drop', { bubbles: true, cancelable: true }) as Event & {
      dataTransfer: { files: File[] }
    }
    dropEvent.dataTransfer = { files: [svgFile, pngFile] }
    textarea.dispatchEvent(dropEvent)
    await flushModal(wrapper)

    const inlineCard = getByTestId('inline-local-upload')
    expect(inlineCard.getAttribute('title')).toBe('photo.png')
    expect(document.querySelector('[role="alert"]')?.textContent).toContain(
      'Unsupported media format',
    )
  })

  it('keeps the draft when every destination rejects the publication', async () => {
    const wrapper = mountModal([makeChannel('8ce3aa09-4ce4-5c20-82ef-b819fadd38d9')])
    await flushModal(wrapper)
    const input = getByTestId('composer-textarea') as HTMLTextAreaElement
    input.value = 'Keep this draft'
    input.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    const createAnotherLabel = Array.from(document.querySelectorAll('label')).find((label) =>
      label.textContent?.includes('Create Another'),
    )
    const createAnotherInput =
      createAnotherLabel?.querySelector<HTMLInputElement>('input[type="checkbox"]')
    expect(createAnotherInput).toBeTruthy()
    createAnotherInput!.checked = true
    createAnotherInput!.dispatchEvent(new Event('change', { bubbles: true }))
    await flushModal(wrapper)
    const scheduleSpy = vi.spyOn(usePublishingStore(), 'scheduleMultiChannel').mockResolvedValue({
      status: 'ALL_FAILED',
      results: [
        {
          socialAccountId: '8ce3aa09-4ce4-5c20-82ef-b819fadd38d9',
          status: 'BAD_REQUEST',
          errorMessage: 'Rejected by destination',
        },
      ],
    })

    const scheduleButton = Array.from(
      document.querySelectorAll<HTMLButtonElement>('.ui-button'),
    ).find((button) => button.textContent?.includes('Schedule Now'))
    expect(scheduleButton).toBeDefined()
    scheduleButton!.click()
    await vi.waitFor(() => expect(scheduleSpy).toHaveBeenCalledOnce())
    await flushModal(wrapper)

    expect(input.value).toBe('Keep this draft')
    expect(wrapper.emitted('created')).toBeUndefined()
    expect(createAnotherInput.checked).toBe(true)
  })

  it('clears a prior file-rejection alert when publishing with createAnother', async () => {
    const store = usePublishingStore()
    const scheduleSpy = vi.spyOn(store, 'scheduleMultiChannel').mockResolvedValue({
      status: 'ALL_CREATED',
      results: [
        {
          socialAccountId: '8bd79d9b-a3f2-51e7-9943-dd649728e5ce',
          publicationId: '61e201fe-e865-5cff-860a-68237a7f44ca',
          status: 'CREATED',
        },
      ],
    })
    const wrapper = mountModal([makeChannel('8bd79d9b-a3f2-51e7-9943-dd649728e5ce')])
    await flushModal(wrapper)

    // Reject an unsupported drop → role=alert appears
    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    const svgFile = new File(['<svg></svg>'], 'diagram.svg', { type: 'image/svg+xml' })
    const dropEvent = new Event('drop', { bubbles: true, cancelable: true }) as Event & {
      dataTransfer: { files: File[] }
    }
    dropEvent.dataTransfer = { files: [svgFile] }
    textarea.dispatchEvent(dropEvent)
    await flushModal(wrapper)
    expect(document.querySelector('[role="alert"]')?.textContent).toContain(
      'Unsupported media format',
    )

    // Type content and enable Create Another
    textarea.value = 'Next post'
    textarea.dispatchEvent(new Event('input'))
    const createAnotherLabel = Array.from(document.querySelectorAll('label')).find((label) =>
      label.textContent?.includes('Create Another'),
    )
    const createAnotherInput =
      createAnotherLabel?.querySelector<HTMLInputElement>('input[type="checkbox"]')
    expect(createAnotherInput).toBeTruthy()
    createAnotherInput!.checked = true
    createAnotherInput!.dispatchEvent(new Event('change', { bubbles: true }))
    await flushModal(wrapper)

    // Publish → createAnother resets the form; the stale rejection alert must NOT survive
    const scheduleButton = Array.from(
      document.querySelectorAll<HTMLButtonElement>('.ui-button'),
    ).find((button) => button.textContent?.includes('Schedule Now'))
    expect(scheduleButton).toBeTruthy()
    scheduleButton!.dispatchEvent(new MouseEvent('click', { bubbles: true }))

    await vi.waitFor(() => {
      expect(scheduleSpy).toHaveBeenCalled()
      expect(document.querySelector('[role="alert"]')).toBeNull()
      expect((getByTestId('composer-textarea') as HTMLTextAreaElement).value).toBe('')
    })
  })
})

describe('CreatePostModal.vue — markdown toolbar', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    vi.clearAllMocks()
    vi.spyOn(usePublishingStore(), 'scheduleMultiChannel').mockResolvedValue({
      status: 'ALL_CREATED',
      results: [
        {
          socialAccountId: 'c98c3b43-390a-556e-8fe3-622a2b97918a',
          publicationId: '9cdff3ca-0dd3-53b3-8cb9-b223e3e93037',
          status: 'CREATED',
        },
      ],
    })
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:mock-preview'),
      revokeObjectURL: vi.fn(),
    })
  })

  afterEach(async () => {
    await cleanupMountedWrappers()
    await nextTick()
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
  })

  it('applies link formatting when the link button is clicked', async () => {
    const wrapper = mountModal([makeChannel('45d24f46-f69c-5335-b77c-18d4e5796879')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'Click here'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    textarea.selectionStart = 0
    textarea.selectionEnd = 5

    getByTestId('md-link').click()
    await flushModal(wrapper)

    expect(textarea.value).toBe('[Click](https://) here')
  })

  it('submits plain text (stripped of markdown) to the store on schedule', async () => {
    const store = usePublishingStore()
    const scheduleSpy = vi.spyOn(store, 'scheduleMultiChannel').mockResolvedValue({
      status: 'ALL_CREATED',
      results: [
        {
          socialAccountId: '45d24f46-f69c-5335-b77c-18d4e5796879',
          publicationId: '42b7279b-bccb-5737-bdab-b3eca31b3f91',
          status: 'CREATED',
        },
      ],
    })
    const wrapper = mountModal([makeChannel('45d24f46-f69c-5335-b77c-18d4e5796879')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = '**Bold** and *italic* post'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)

    const scheduleButton = Array.from(
      document.querySelectorAll<HTMLButtonElement>('.ui-button'),
    ).find((button) => button.textContent?.includes('Schedule Now'))
    expect(scheduleButton).toBeTruthy()
    scheduleButton!.click()

    await vi.waitFor(() => {
      expect(scheduleSpy).toHaveBeenCalledTimes(1)
    })

    const submittedContent = scheduleSpy.mock.calls[0]?.[0]?.targets[0]?.content
    expect(submittedContent).toBe('Bold and italic post')
  })

  it('preserves existing plain-text content in edit mode', async () => {
    const wrapper = mountModal([makeChannel('ch-edit')], {
      editingPublication: makeEditingPublication({
        content: 'Existing plain text content',
      }),
    })
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    expect(textarea.value).toBe('Existing plain text content')
  })

  it('disables toolbar buttons while submitting', async () => {
    const store = usePublishingStore()
    vi.spyOn(store, 'schedulePost').mockImplementation(() => new Promise(() => {}))
    const wrapper = mountModal([makeChannel('45d24f46-f69c-5335-b77c-18d4e5796879')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'Test content'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)

    const boldButton = getByTestId('md-bold') as HTMLButtonElement
    expect(boldButton.disabled).toBe(false)

    const scheduleButton = Array.from(
      document.querySelectorAll<HTMLButtonElement>('.ui-button'),
    ).find((button) => button.textContent?.includes('Schedule Now'))
    scheduleButton!.click()
    await flushModal(wrapper)

    expect(boldButton.disabled).toBe(true)
  })

  it('applies bold formatting when the bold button is clicked', async () => {
    const wrapper = mountModal([makeChannel('45d24f46-f69c-5335-b77c-18d4e5796879')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'Hello world'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    textarea.setSelectionRange(0, 5)

    getByTestId('md-bold').click()
    await flushModal(wrapper)

    expect(textarea.value).toBe('**Hello** world')
  })

  it('applies italic formatting when the italic button is clicked', async () => {
    const wrapper = mountModal([makeChannel('45d24f46-f69c-5335-b77c-18d4e5796879')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'Hello'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    textarea.setSelectionRange(0, 5)

    getByTestId('md-italic').click()
    await flushModal(wrapper)

    expect(textarea.value).toBe('*Hello*')
  })

  it('applies heading formatting when the heading button is clicked', async () => {
    const wrapper = mountModal([makeChannel('45d24f46-f69c-5335-b77c-18d4e5796879')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'My heading'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    textarea.setSelectionRange(0, 9)

    getByTestId('md-heading').click()
    await flushModal(wrapper)

    expect(textarea.value).toBe('# My heading')
  })

  it('applies unordered list formatting when the list button is clicked', async () => {
    const wrapper = mountModal([makeChannel('45d24f46-f69c-5335-b77c-18d4e5796879')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'Item one'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    textarea.setSelectionRange(0, 8)

    getByTestId('md-unordered-list').click()
    await flushModal(wrapper)

    expect(textarea.value).toBe('- Item one')
  })

  it('applies ordered list formatting when the numbered list button is clicked', async () => {
    const wrapper = mountModal([makeChannel('45d24f46-f69c-5335-b77c-18d4e5796879')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'Item one'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    textarea.setSelectionRange(0, 8)

    getByTestId('md-ordered-list').click()
    await flushModal(wrapper)

    expect(textarea.value).toBe('1. Item one')
  })

  it('applies blockquote formatting when the quote button is clicked', async () => {
    const wrapper = mountModal([makeChannel('45d24f46-f69c-5335-b77c-18d4e5796879')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'Quoted text'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    textarea.setSelectionRange(0, 11)

    getByTestId('md-blockquote').click()
    await flushModal(wrapper)

    expect(textarea.value).toBe('> Quoted text')
  })

  it('applies inline code formatting when the code button is clicked', async () => {
    const wrapper = mountModal([makeChannel('45d24f46-f69c-5335-b77c-18d4e5796879')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'npm install'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    textarea.setSelectionRange(0, 11)

    getByTestId('md-code').click()
    await flushModal(wrapper)

    expect(textarea.value).toBe('`npm install`')
  })

  it('applies strikethrough formatting when the strikethrough button is clicked', async () => {
    const wrapper = mountModal([makeChannel('45d24f46-f69c-5335-b77c-18d4e5796879')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'deleted'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)
    textarea.setSelectionRange(0, 7)

    getByTestId('md-strikethrough').click()
    await flushModal(wrapper)

    expect(textarea.value).toBe('~~deleted~~')
  })

  it('normalizes hashtags in submitted content', async () => {
    const store = usePublishingStore()
    const scheduleSpy = vi.spyOn(store, 'scheduleMultiChannel').mockResolvedValue({
      status: 'ALL_CREATED',
      results: [
        {
          socialAccountId: '45d24f46-f69c-5335-b77c-18d4e5796879',
          publicationId: '42b7279b-bccb-5737-bdab-b3eca31b3f91',
          status: 'CREATED',
        },
      ],
    })
    const wrapper = mountModal([makeChannel('45d24f46-f69c-5335-b77c-18d4e5796879')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = 'Check this out #AWESOME #COOL-STUFF'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)

    const scheduleButton = Array.from(
      document.querySelectorAll<HTMLButtonElement>('.ui-button'),
    ).find((button) => button.textContent?.includes('Schedule Now'))
    scheduleButton!.click()

    await vi.waitFor(() => {
      expect(scheduleSpy).toHaveBeenCalledTimes(1)
    })

    const submittedContent = scheduleSpy.mock.calls[0]?.[0]?.targets[0]?.content
    expect(submittedContent).toBe('Check this out #awesome #coolstuff')
  })

  it('renders the markdown toolbar with accessible labels', async () => {
    const wrapper = mountModal([makeChannel('45d24f46-f69c-5335-b77c-18d4e5796879')])
    await flushModal(wrapper)

    const toolbar = getByTestId('markdown-toolbar')
    expect(toolbar.getAttribute('role')).toBe('toolbar')
    expect(toolbar.getAttribute('aria-label')).toBe('Formatting')
  })

  it('shows the preview panel text without markdown markers', async () => {
    const wrapper = mountModal([makeChannel('45d24f46-f69c-5335-b77c-18d4e5796879')])
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = '**Bold** and *italic* text'
    textarea.dispatchEvent(new Event('input'))
    await flushModal(wrapper)

    const previewText = getByTestId('linkedin-preview-text')
    expect(previewText.textContent).toContain('Bold and italic text')
    expect(previewText.textContent).not.toContain('**')
    expect(previewText.textContent).not.toContain('*')
  })

  it('preserves markdown-formatted content in edit mode and strips on resubmit', async () => {
    const store = usePublishingStore()
    const updateSpy = vi.spyOn(store, 'updatePost').mockResolvedValue({} as Publication)
    const wrapper = mountModal([makeChannel('73a4665b-8dcc-5f27-8cba-bca328b27bde')], {
      editingPublication: makeEditingPublication({
        content: '**Bold** content',
        accountId: '73a4665b-8dcc-5f27-8cba-bca328b27bde',
        assetIds: [],
      }),
    })
    await flushModal(wrapper)
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    expect(textarea.value).toBe('**Bold** content')

    const saveButton = Array.from(document.querySelectorAll<HTMLButtonElement>('.ui-button')).find(
      (button) => button.textContent?.includes('Save Changes'),
    )
    expect(saveButton).toBeDefined()
    saveButton!.click()

    await vi.waitFor(() => {
      expect(updateSpy).toHaveBeenCalledTimes(1)
    })

    const submittedContent = updateSpy.mock.calls[0]?.[1]?.content
    expect(submittedContent).toBe('Bold content')
  })
})

describe('CreatePostModal.vue — multi-channel (Buffer-style)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  afterEach(async () => {
    await cleanupMountedWrappers()
    document.body.innerHTML = ''
  })

  function twoChannels() {
    return [
      makeChannel('b431194a-5205-577d-b212-98398966a944', {
        accountId: '6eeca0c2-97ec-588e-966c-bd4bbacdb5d9',
        provider: 'linkedin',
        name: 'LinkedIn',
      }),
      makeChannel('ch-th', {
        accountId: 'c15e6f35-c95f-5cca-b943-da6f94483679',
        provider: 'threads',
        name: 'Threads',
      }),
    ]
  }

  function fillText(text: string) {
    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = text
    textarea.dispatchEvent(new Event('input'))
  }

  function scheduleNowButton(): HTMLButtonElement {
    const button = Array.from(document.querySelectorAll<HTMLButtonElement>('.ui-button')).find(
      (entry) => entry.textContent?.includes('Schedule Now'),
    )
    if (!button) {
      throw new Error('Expected Schedule Now button to exist')
    }
    return button
  }

  it('selects every active channel by default on open', async () => {
    const wrapper = mountModal(twoChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    const selected = document.querySelectorAll(
      '[data-testid="channel-button"][aria-pressed="true"]',
    )
    expect(selected).toHaveLength(2)
  })

  it('deselects a channel when its chip is clicked again', async () => {
    const wrapper = mountModal(twoChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    const linkedInChip = Array.from(
      document.querySelectorAll<HTMLButtonElement>('[data-testid="channel-button"]'),
    ).find((button) => button.getAttribute('data-channel-provider') === 'linkedin')
    expect(linkedInChip).toBeDefined()
    linkedInChip!.click()
    await flushModal(wrapper)

    expect(
      document.querySelectorAll('[data-testid="channel-button"][aria-pressed="true"]'),
    ).toHaveLength(1)
    expect(document.querySelectorAll('[data-channel-provider="threads"]')).toHaveLength(1)
  })

  it('shows over-limit errors live and blocks submit without calling the API', async () => {
    const scheduleSpy = vi.spyOn(usePublishingStore(), 'scheduleMultiChannel')
    const wrapper = mountModal(twoChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    fillText('x'.repeat(600))
    await flushModal(wrapper)

    expect(scheduleNowButton().disabled).toBe(true)
    const errors = getByTestId('composer-channel-limit-errors')
    expect(errors.textContent).toContain('Threads')
    expect(errors.textContent).toContain('500')

    scheduleNowButton().click()
    await flushModal(wrapper)
    expect(scheduleSpy).not.toHaveBeenCalled()
  })

  it('hides channel errors on open until the composer is attempted or touched', async () => {
    const wrapper = mountModal(twoChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    expect(document.querySelector('[data-testid="composer-channel-limit-errors"]')).toBeNull()
  })

  it('reveals empty-text errors only after a submit attempt, without calling the API', async () => {
    const scheduleSpy = vi.spyOn(usePublishingStore(), 'scheduleMultiChannel')
    const wrapper = mountModal(twoChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    expect(scheduleNowButton().disabled).toBe(true)
    scheduleNowButton().click()
    await flushModal(wrapper)
    expect(scheduleSpy).not.toHaveBeenCalled()

    const vm = wrapper.vm as Record<string, unknown>
    ;(vm as Record<string, boolean>).submitAttempted = true
    await flushModal(wrapper)

    const errors = getByTestId('composer-channel-limit-errors')
    expect(errors.textContent).toContain('LinkedIn')
    expect(errors.textContent).toContain('Threads')
    expect(scheduleSpy).not.toHaveBeenCalled()
  })

  it('does not reveal empty-text errors when the composer is blurred without editing', async () => {
    const wrapper = mountModal(twoChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    expect(document.querySelector('[data-testid="composer-channel-limit-errors"]')).toBeNull()

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.focus()
    textarea.blur()
    await flushModal(wrapper)

    expect(document.querySelector('[data-testid="composer-channel-limit-errors"]')).toBeNull()
  })

  it('submits one batch request with every selected account', async () => {
    const store = usePublishingStore()
    const scheduleSpy = vi.spyOn(store, 'scheduleMultiChannel').mockResolvedValue({
      status: 'ALL_CREATED',
      results: [
        {
          socialAccountId: '6eeca0c2-97ec-588e-966c-bd4bbacdb5d9',
          publicationId: 'fc18a1ef-cca8-5cf8-a32a-27629468a359',
          status: 'CREATED',
        },
        {
          socialAccountId: 'c15e6f35-c95f-5cca-b943-da6f94483679',
          publicationId: '261ef19a-4b40-52ca-8850-86ae31fdc4c3',
          status: 'CREATED',
        },
      ],
    })
    const wrapper = mountModal(twoChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    fillText('Hello every network')
    await flushModal(wrapper)

    scheduleNowButton().click()
    await vi.waitFor(() => {
      expect(scheduleSpy).toHaveBeenCalledTimes(1)
    })

    const submitted = scheduleSpy.mock.calls[0]?.[0]
    expect(submitted?.targets.map((target) => target.socialAccountId).sort()).toEqual([
      '6eeca0c2-97ec-588e-966c-bd4bbacdb5d9',
      'c15e6f35-c95f-5cca-b943-da6f94483679',
    ])
  })
})

describe('CreatePostModal.vue — per-network customization', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    document.body.innerHTML = ''
    vi.clearAllMocks()
    vi.spyOn(usePublishingStore(), 'scheduleMultiChannel').mockResolvedValue({
      status: 'ALL_CREATED',
      results: [
        {
          socialAccountId: 'c98c3b43-390a-556e-8fe3-622a2b97918a',
          publicationId: '9cdff3ca-0dd3-53b3-8cb9-b223e3e93037',
          status: 'CREATED',
        },
      ],
    })
  })

  afterEach(async () => {
    await cleanupMountedWrappers()
    document.body.innerHTML = ''
  })

  function baseChannels() {
    return [
      makeChannel('b431194a-5205-577d-b212-98398966a944', {
        accountId: '6eeca0c2-97ec-588e-966c-bd4bbacdb5d9',
        provider: 'linkedin',
        name: 'LinkedIn',
      }),
      makeChannel('ch-th', {
        accountId: 'c15e6f35-c95f-5cca-b943-da6f94483679',
        provider: 'threads',
        name: 'Threads',
      }),
    ]
  }

  function fillComposer(text: string) {
    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.value = text
    textarea.dispatchEvent(new Event('input'))
  }

  function customizeTabs(): HTMLButtonElement[] {
    return Array.from(document.querySelectorAll<HTMLButtonElement>('[data-testid="customize-tab"]'))
  }

  function openCustomizing() {
    getByTestId('customize-toggle').click()
  }

  it('adds emoji to the active channel override without changing the base draft', async () => {
    const wrapper = mountModal(baseChannels())
    await flushModal(wrapper)
    fillComposer('Base draft')
    await flushModal(wrapper)
    openCustomizing()
    await flushModal(wrapper)
    const threadsTab = customizeTabs().find((tab) => tab.textContent?.includes('Threads'))
    expect(threadsTab).toBeDefined()
    threadsTab?.click()
    await flushModal(wrapper)

    document.querySelector<HTMLButtonElement>('[aria-label="Open emoji picker"]')?.click()
    await flushModal(wrapper)
    expect((getByTestId('composer-textarea') as HTMLTextAreaElement).value).toBe('Base draft 🙂')

    customizeTabs()
      .find((tab) => tab.textContent?.includes('Base'))!
      .click()
    await flushModal(wrapper)
    expect((getByTestId('composer-textarea') as HTMLTextAreaElement).value).toBe('Base draft')
  })

  it('hides channel tabs until customize mode is enabled', async () => {
    const wrapper = mountModal(baseChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    expect(getByTestId('customize-toggle')).toBeDefined()
    expect(document.querySelector('[data-testid="customize-tabs"]')).toBeNull()
  })

  it('reveals one tab per selected channel with per-network counters', async () => {
    const wrapper = mountModal(baseChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    openCustomizing()
    await flushModal(wrapper)

    const tabs = customizeTabs()
    expect(tabs.map((tab) => tab.textContent)).toEqual(
      expect.arrayContaining([
        expect.stringContaining('Base'),
        expect.stringContaining('LinkedIn'),
        expect.stringContaining('Threads'),
      ]),
    )
    const threadsTab = tabs.find((tab) => tab.textContent?.includes('Threads'))
    expect(threadsTab?.textContent).toContain('500')
  })

  it('states the inheritance status of the active tab in words', async () => {
    const wrapper = mountModal(baseChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    fillComposer('Hello every network')
    await flushModal(wrapper)
    openCustomizing()
    await flushModal(wrapper)

    expect(document.querySelector('[data-testid="customize-status"]')).not.toBeNull()
    expect(getByTestId('customize-status').textContent).toContain('feeds 2 account(s)')

    customizeTabs()
      .find((tab) => tab.textContent?.includes('Threads'))!
      .click()
    await flushModal(wrapper)
    expect(getByTestId('customize-status').textContent).toContain('Uses base content')

    fillComposer('Short for threads')
    await flushModal(wrapper)
    expect(getByTestId('customize-status').textContent).toContain('Customized')
  })

  it('submits distinct bodyText per account in one batch request', async () => {
    const store = usePublishingStore()
    const scheduleSpy = vi.spyOn(store, 'scheduleMultiChannel').mockResolvedValue({
      status: 'ALL_CREATED',
      results: [
        {
          socialAccountId: '6eeca0c2-97ec-588e-966c-bd4bbacdb5d9',
          publicationId: 'fc18a1ef-cca8-5cf8-a32a-27629468a359',
          status: 'CREATED',
        },
        {
          socialAccountId: 'c15e6f35-c95f-5cca-b943-da6f94483679',
          publicationId: '261ef19a-4b40-52ca-8850-86ae31fdc4c3',
          status: 'CREATED',
        },
      ],
    })
    const wrapper = mountModal(baseChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    fillComposer('Hello every network')
    await flushModal(wrapper)

    openCustomizing()
    await flushModal(wrapper)
    customizeTabs()
      .find((tab) => tab.textContent?.includes('Threads'))!
      .click()
    await flushModal(wrapper)
    fillComposer('Short for threads')
    await flushModal(wrapper)

    Array.from(document.querySelectorAll<HTMLButtonElement>('.ui-button'))
      .find((button) => button.textContent?.includes('Schedule Now'))!
      .click()
    await vi.waitFor(() => {
      expect(scheduleSpy).toHaveBeenCalledTimes(1)
    })

    const submittedTargets = scheduleSpy.mock.calls[0]?.[0].targets
    const byAccount = Object.fromEntries(
      submittedTargets.map((target) => [target.socialAccountId, target.content]),
    )
    expect(byAccount['6eeca0c2-97ec-588e-966c-bd4bbacdb5d9']).toBe('Hello every network')
    expect(byAccount['c15e6f35-c95f-5cca-b943-da6f94483679']).toBe('Short for threads')
  })

  it('removes the override mark when tab text matches base again', async () => {
    const wrapper = mountModal(baseChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    fillComposer('Hello every network')
    await flushModal(wrapper)
    openCustomizing()
    await flushModal(wrapper)
    customizeTabs()
      .find((tab) => tab.textContent?.includes('Threads'))!
      .click()
    await flushModal(wrapper)
    fillComposer('Short for threads')
    await flushModal(wrapper)
    expect(document.querySelector('[data-testid="customize-dot"]')).not.toBeNull()

    fillComposer('Hello every network')
    await flushModal(wrapper)
    expect(document.querySelector('[data-testid="customize-dot"]')).toBeNull()
  })

  it('unblocks submit when a short override replaces an over-limit base for Threads', async () => {
    const wrapper = mountModal(baseChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    fillComposer('x'.repeat(600))
    await flushModal(wrapper)
    const scheduleButton = Array.from(
      document.querySelectorAll<HTMLButtonElement>('.ui-button'),
    ).find((button) => button.textContent?.includes('Schedule Now'))!
    expect(scheduleButton.disabled).toBe(true)

    openCustomizing()
    await flushModal(wrapper)
    customizeTabs()
      .find((tab) => tab.textContent?.includes('Threads'))!
      .click()
    await flushModal(wrapper)
    fillComposer('y'.repeat(100))
    await flushModal(wrapper)
    await flushModal(wrapper)

    expect(scheduleButton.disabled).toBe(false)
    expect(document.querySelector('[data-testid="composer-channel-limit-errors"]')).toBeNull()
  })

  it('reset button clears the override back to base text', async () => {
    const wrapper = mountModal(baseChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    fillComposer('Hello every network')
    await flushModal(wrapper)
    openCustomizing()
    await flushModal(wrapper)
    customizeTabs()
      .find((tab) => tab.textContent?.includes('Threads'))!
      .click()
    await flushModal(wrapper)
    fillComposer('Short for threads')
    await flushModal(wrapper)

    getByTestId('customize-reset').click()
    await flushModal(wrapper)

    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    expect(textarea.value).toBe('Hello every network')
    expect(document.querySelector('[data-testid="customize-dot"]')).toBeNull()
  })

  it('uses provider-specific author fallbacks while preserving a channel handle', async () => {
    const wrapper = mountModal([
      makeChannel('b431194a-5205-577d-b212-98398966a944', {
        accountId: '6eeca0c2-97ec-588e-966c-bd4bbacdb5d9',
        provider: 'linkedin',
        name: 'LinkedIn',
        handle: '',
      }),
      makeChannel('ch-th', {
        accountId: 'c15e6f35-c95f-5cca-b943-da6f94483679',
        provider: 'threads',
        name: 'Threads',
        handle: '',
      }),
    ])
    await flushModal(wrapper)
    await flushModal(wrapper)
    openCustomizing()
    await flushModal(wrapper)

    const handleText = (value: string) =>
      Array.from(document.querySelectorAll('p')).find(
        (paragraph) => paragraph.textContent?.trim() === value,
      )

    expect(handleText('LinkedIn Member')).toBeDefined()
    customizeTabs()
      .find((tab) => tab.textContent?.includes('Threads'))!
      .click()
    await flushModal(wrapper)
    expect(handleText('Threads user')).toBeDefined()

    const linkedinWithHandle = mountModal([
      makeChannel('ch-li-handle', {
        accountId: 'dfa6b191-56f3-5216-ad74-9fee37368d0c',
        provider: 'linkedin',
        handle: '@linkedin',
      }),
    ])
    await flushModal(linkedinWithHandle)
    expect(handleText('@linkedin')).toBeDefined()
  })

  it('renders the Threads preview with the override text for the active tab', async () => {
    const wrapper = mountModal(baseChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    fillComposer('Hello every network')
    await flushModal(wrapper)
    openCustomizing()
    await flushModal(wrapper)
    customizeTabs()
      .find((tab) => tab.textContent?.includes('Threads'))!
      .click()
    await flushModal(wrapper)
    fillComposer('Short for threads')
    await flushModal(wrapper)

    expect(document.querySelector('[data-testid="threads-preview"]')).not.toBeNull()
    expect(document.querySelector('[data-testid="threads-preview-text"]')?.textContent).toBe(
      'Short for threads',
    )
  })

  it('does not reveal errors when merely tabbing through without editing', async () => {
    const wrapper = mountModal(baseChannels())
    await flushModal(wrapper)
    await flushModal(wrapper)

    expect(document.querySelector('[data-testid="composer-channel-limit-errors"]')).toBeNull()

    openCustomizing()
    await flushModal(wrapper)
    const tabs = customizeTabs()
    const threadsTab = tabs.find((tab) => tab.textContent?.includes('Threads'))
    threadsTab?.click()
    await flushModal(wrapper)
    const textarea = getByTestId('composer-textarea') as HTMLTextAreaElement
    textarea.focus()
    textarea.blur()
    await flushModal(wrapper)

    expect(document.querySelector('[data-testid="composer-channel-limit-errors"]')).toBeNull()
  })
})
