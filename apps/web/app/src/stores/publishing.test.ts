import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePublishingStore, type CalendarResponse } from './publishing'
import { useAuthStore } from './auth'

// ---------------------------------------------------------------------------
// Mock auth-api
// ---------------------------------------------------------------------------
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
  resolveApiUrl: vi.fn((path: string) => `https://api.test${path}`),
  refreshSession: vi.fn().mockResolvedValue(null),
  getCurrentUserProfile: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  register: vi.fn(),
  logoutSession: vi.fn(),
}))

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeChannelForStore(
  accountId: string,
  overrides: Partial<{
    provider: 'linkedin' | 'twitter' | 'instagram' | 'facebook'
    maxAttachments: number
  }> = {},
) {
  return {
    id: accountId,
    accountId,
    name: 'Test Profile',
    provider: 'linkedin' as const,
    avatar: '',
    handle: 'Test Profile',
    status: 'ACTIVE' as const,
    maxAttachments: 9,
    ...overrides,
  }
}

const mockConflictCalendarResponse: CalendarResponse = {
  publications: [
    {
      id: 'api-pub-1',
      workspaceId: 'ws_1',
      socialAccountId: 'acc-li-1',
      provider: 'LINKEDIN',
      status: 'SCHEDULED',
      scheduleMode: 'SCHEDULED_AT',
      priority: false,
      title: 'First Post',
      bodyText: 'First post content',
      assetIds: [],
      scheduledFor: '2026-06-15T20:00:00Z',
      hasConflict: true,
      conflictingPublicationIds: ['api-pub-2'],
    },
    {
      id: 'api-pub-2',
      workspaceId: 'ws_1',
      socialAccountId: 'acc-li-1',
      provider: 'LINKEDIN',
      status: 'SCHEDULED',
      scheduleMode: 'SCHEDULED_AT',
      priority: false,
      title: 'Second Post',
      bodyText: 'Second post content',
      assetIds: [],
      scheduledFor: '2026-06-15T20:10:00Z',
      hasConflict: true,
      conflictingPublicationIds: ['api-pub-1'],
    },
  ],
  conflicts: [
    {
      publicationId: 'api-pub-1',
      conflictingPublicationIds: ['api-pub-2'],
      reason: 'OVERLAPPING_SCHEDULE',
    },
    {
      publicationId: 'api-pub-2',
      conflictingPublicationIds: ['api-pub-1'],
      reason: 'OVERLAPPING_SCHEDULE',
    },
  ],
  activity: [{ date: '2026-06-15', density: 'MEDIUM', count: 2 }],
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('publishing store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    globalThis.localStorage?.clear()
    vi.restoreAllMocks()
  })

  describe('initial state', () => {
    it('loads initial publications from seed data when localStorage is empty', () => {
      const store = usePublishingStore()
      expect(store.publications.length).toBeGreaterThanOrEqual(2)
      expect(store.publications[0]?.status).toBe('QUEUED')
      expect(store.channels).toEqual([])
    })

    it('derives user timezone from Intl API', () => {
      const store = usePublishingStore()
      expect(store.userTimezone).toBeTruthy()
      expect(typeof store.userTimezone).toBe('string')
    })

    it('has empty activity and conflicts initially', () => {
      const store = usePublishingStore()
      expect(store.activity).toEqual([])
      expect(store.conflicts).toEqual([])
    })
  })

  describe('calendarFilters', () => {
    it('returns empty filters by default', () => {
      const store = usePublishingStore()
      const filters = store.calendarFilters
      expect(filters.status).toBeUndefined()
      expect(filters.socialAccountId).toBeUndefined()
    })

    it('includes status when filterPostType is set', () => {
      const store = usePublishingStore()
      store.filterPostType = 'queued'
      expect(store.calendarFilters.status).toBe('QUEUED')
    })

    it('includes socialAccountId when filterSocialAccountId is set', () => {
      const store = usePublishingStore()
      store.filterSocialAccountId = 'acc-li-1'
      expect(store.calendarFilters.socialAccountId).toBe('acc-li-1')
    })
  })

  describe('fetchChannels', () => {
    it('maps backend channels to frontend channels', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        channels: [
          {
            socialAccountId: 'soc-1',
            connectionId: 'conn-1',
            provider: 'LINKEDIN',
            accountKind: 'PERSONAL_PROFILE',
            displayName: 'Ada Lovelace',
            status: 'ACTIVE',
            avatarUrl: 'https://media.licdn.com/photo.jpg',
            connectedAt: '2026-06-12T12:00:00Z',
            lastSyncedAt: null,
          },
        ],
      })

      await store.fetchChannels()

      expect(apiFetch).toHaveBeenCalledWith('/api/publishing/channels', {
        method: 'GET',
        workspaceScoped: true,
      })
      expect(store.channels).toEqual([
        {
          id: 'soc-1',
          accountId: 'soc-1',
          name: 'Ada Lovelace',
          provider: 'linkedin',
          avatar: '',
          avatarUrl: 'https://media.licdn.com/photo.jpg',
          handle: 'Ada Lovelace',
          status: 'ACTIVE',
          maxAttachments: 9,
        },
      ])
    })

    it('falls back to linkedin when backend provider is unknown', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        channels: [
          {
            socialAccountId: 'soc-unknown',
            connectionId: 'conn-unknown',
            provider: 'MASTODON',
            accountKind: 'PERSONAL_PROFILE',
            displayName: 'Unknown Network',
            status: 'ACTIVE',
            avatarUrl: null,
            connectedAt: '2026-06-12T12:00:00Z',
            lastSyncedAt: null,
          },
        ],
      })

      await store.fetchChannels()

      expect(store.channels[0]?.provider).toBe('linkedin')
    })

    it('maps null avatarUrl to undefined when backend omits avatar', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        channels: [
          {
            socialAccountId: 'soc-2',
            connectionId: 'conn-2',
            provider: 'LINKEDIN',
            accountKind: 'PERSONAL_PROFILE',
            displayName: 'Grace Hopper',
            status: 'ACTIVE',
            avatarUrl: null,
            connectedAt: '2026-06-12T12:00:00Z',
            lastSyncedAt: null,
          },
        ],
      })

      await store.fetchChannels()

      expect(store.channels[0]?.avatarUrl).toBeUndefined()
    })

    it('stores error message and rethrows when fetch fails', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('boom'))

      await expect(store.fetchChannels()).rejects.toThrow('boom')
      expect(store.channelsError).toBe('boom')
      expect(store.channelsLoading).toBe(false)
    })

    it('does not fetch channels when unauthenticated', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })
      const apiFetch = vi.spyOn(auth, 'apiFetch')

      await store.fetchChannels()

      expect(apiFetch).not.toHaveBeenCalled()
      expect(store.channels).toEqual([])
    })
  })

  describe('connectLinkedInPersonalProfile', () => {
    it('calls initiate endpoint and redirects to authorization URL', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      const assign = vi.fn()
      Object.defineProperty(window, 'location', {
        value: { origin: 'http://app.test', assign },
        configurable: true,
      })
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        authorizationUrl: 'https://linkedin.example/auth',
        state: 'state-1',
        expiresAt: '2026-06-12T12:10:00Z',
      })

      await store.connectLinkedInPersonalProfile()

      expect(apiFetch).toHaveBeenCalledWith('/api/publishing/linkedin/connections/initiate', {
        method: 'POST',
        body: JSON.stringify({ redirectUri: 'http://app.test/integrations/linkedin/callback' }),
        workspaceScoped: true,
      })
      expect(assign).toHaveBeenCalledWith('https://linkedin.example/auth')
    })
  })

  describe('completeLinkedInConnectionFromCallback', () => {
    it('posts code/state and refreshes channels', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      const apiFetch = vi
        .spyOn(auth, 'apiFetch')
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ channels: [] })

      await store.completeLinkedInConnectionFromCallback({
        code: 'code-1',
        state: 'state-1',
        redirectUri: 'http://app.test/integrations/linkedin/callback',
      })

      expect(apiFetch).toHaveBeenNthCalledWith(1, '/api/publishing/linkedin/connections/complete', {
        method: 'POST',
        body: JSON.stringify({
          authorizationCode: 'code-1',
          redirectUri: 'http://app.test/integrations/linkedin/callback',
          state: 'state-1',
        }),
        workspaceScoped: true,
      })
      expect(apiFetch).toHaveBeenNthCalledWith(2, '/api/publishing/channels', {
        method: 'GET',
        workspaceScoped: true,
      })
    })
  })

  describe('fetchConfiguredProviders', () => {
    it('keeps only configured provider names', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        providers: [
          { name: 'linkedin', configured: true },
          { name: 'twitter', configured: false },
          { name: 'instagram', configured: true },
        ],
      })

      await store.fetchConfiguredProviders()

      expect(store.configuredProviders).toEqual(['linkedin', 'instagram'])
      expect(store.isLinkedInConfigured).toBe(true)
      expect(store.providersLoading).toBe(false)
    })

    it('maps per-provider attachment limits onto channels', () => {
      const store = usePublishingStore()

      store.channels = [
        makeChannelForStore('li-account', { provider: 'linkedin', maxAttachments: 9 }),
        makeChannelForStore('tw-account', { provider: 'twitter', maxAttachments: 4 }),
        makeChannelForStore('ig-account', { provider: 'instagram', maxAttachments: 10 }),
        makeChannelForStore('fb-account', { provider: 'facebook', maxAttachments: 10 }),
      ]

      expect(store.channels.map((channel) => channel.maxAttachments)).toEqual([9, 4, 10, 10])
    })

    it('preserves existing providers when request fails', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.configuredProviders = ['linkedin']
      vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('provider fetch failed'))

      await store.fetchConfiguredProviders()

      expect(store.configuredProviders).toEqual(['linkedin'])
      expect(store.providersLoading).toBe(false)
    })

    it('clears configured providers when unauthenticated', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      store.configuredProviders = ['linkedin']
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })

      await store.fetchConfiguredProviders()

      expect(store.configuredProviders).toEqual([])
    })
  })

  describe('subscribeChannelEvents', () => {
    it('opens fetch-streaming events endpoint and refreshes channels on channel events', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      const stream = new ReadableStream<Uint8Array>({
        start(controller) {
          const encoder = new TextEncoder()
          controller.enqueue(
            encoder.encode(
              'event: connected-channel.updated\ndata: {"type":"connected-channel.updated"}\n\n',
            ),
          )
          controller.close()
        },
      })
      const apiFetchRaw = vi.spyOn(auth, 'apiFetchRaw').mockResolvedValue(new Response(stream))
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({ channels: [] })

      await store.subscribeChannelEvents()

      expect(apiFetchRaw).toHaveBeenCalledWith('/api/publishing/channels/events', {
        method: 'GET',
        headers: { Accept: 'text/event-stream' },
        workspaceScoped: true,
        signal: expect.any(AbortSignal),
      })
      expect(apiFetch).toHaveBeenCalledWith('/api/publishing/channels', {
        method: 'GET',
        workspaceScoped: true,
      })
      expect(store.channelEventsConnected).toBe(false)
    })

    it('ignores non-refresh events from the SSE stream', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      const stream = new ReadableStream<Uint8Array>({
        start(controller) {
          const encoder = new TextEncoder()
          controller.enqueue(encoder.encode('event: heartbeat\ndata: {}\n\n'))
          controller.close()
        },
      })
      vi.spyOn(auth, 'apiFetchRaw').mockResolvedValue(new Response(stream))
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({ channels: [] })

      await store.subscribeChannelEvents()

      expect(apiFetch).not.toHaveBeenCalled()
    })

    it('returns null without connecting when unauthenticated', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })
      const apiFetchRaw = vi.spyOn(auth, 'apiFetchRaw')

      const result = await store.subscribeChannelEvents()

      expect(result).toBeNull()
      expect(apiFetchRaw).not.toHaveBeenCalled()
      expect(store.channelEventsConnected).toBe(false)
    })

    it('aborts active channel event stream on unsubscribe', () => {
      const store = usePublishingStore()
      const abortController = new AbortController()
      const abort = vi.spyOn(abortController, 'abort')
      store.channelEventsAbortController = abortController
      store.channelEventsConnected = true

      store.unsubscribeChannelEvents()

      expect(abort).toHaveBeenCalledOnce()
      expect(store.channelEventsAbortController).toBeNull()
      expect(store.channelEventsConnected).toBe(false)
    })
  })

  describe('hasNoChannels', () => {
    it('returns true when there are no connected channels', () => {
      const store = usePublishingStore()

      store.channels = []

      expect(store.hasNoChannels).toBe(true)
    })

    it('returns false when there is at least one ACTIVE channel', () => {
      const store = usePublishingStore()

      store.channels = [
        {
          id: 'soc-real-1',
          accountId: 'soc-real-1',
          name: 'Real Profile 1',
          provider: 'linkedin',
          avatar: '',
          handle: 'Real Profile 1',
          status: 'ACTIVE',
        },
      ]

      expect(store.hasNoChannels).toBe(false)
    })

    it('returns true when channels exist but none are ACTIVE', () => {
      const store = usePublishingStore()

      store.channels = [
        {
          id: 'soc-inactive-1',
          accountId: 'soc-inactive-1',
          name: 'Inactive 1',
          provider: 'linkedin',
          avatar: '',
          handle: 'Inactive 1',
          status: 'EXPIRED',
        },
        {
          id: 'soc-inactive-2',
          accountId: 'soc-inactive-2',
          name: 'Inactive 2',
          provider: 'linkedin',
          avatar: '',
          handle: 'Inactive 2',
          status: 'REVOKED',
        },
      ]

      expect(store.hasNoChannels).toBe(true)
    })
  })

  describe('isPublicationEditable / isPublicationDeletable', () => {
    it.each([
      ['DRAFT', true],
      ['QUEUED', true],
      ['SCHEDULED', true],
      ['PROCESSING', false],
      ['PUBLISHED', false],
      ['BLOCKED', false],
      ['FAILED', false],
      ['CANCELLED', false],
    ] as const)('isPublicationEditable returns %s for status %s', (status, expected) => {
      const store = usePublishingStore()
      expect(store.isPublicationEditable(status)).toBe(expected)
    })

    it.each([
      ['DRAFT', true],
      ['QUEUED', true],
      ['SCHEDULED', true],
      ['PROCESSING', false],
      ['PUBLISHED', false],
      ['BLOCKED', false],
      ['FAILED', false],
      ['CANCELLED', false],
    ] as const)('isPublicationDeletable returns %s for status %s', (status, expected) => {
      const store = usePublishingStore()
      expect(store.isPublicationDeletable(status)).toBe(expected)
    })
  })

  describe('content normalization', () => {
    it('trims leading and trailing whitespace from post content', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [
        {
          id: 'ch-trim-1',
          accountId: 'ch-trim-1',
          name: 'Trim Test',
          provider: 'linkedin',
          avatar: '',
          handle: 'Trim Test',
          status: 'ACTIVE',
        },
      ]
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({})

      await store.schedulePost({
        content: '  Hello World  ',
        title: 'Title',
        channels: ['linkedin'],
        scheduledAt: '2026-06-20T14:00:00Z',
        priority: false,
      })

      const body = JSON.parse(apiFetch.mock.calls[0]?.[1]?.body as string)
      expect(body.bodyText).toBe('Hello World')
    })

    it('trims content with only whitespace', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [
        {
          id: 'ch-trim-2',
          accountId: 'ch-trim-2',
          name: 'Trim Test 2',
          provider: 'linkedin',
          avatar: '',
          handle: 'Trim Test 2',
          status: 'ACTIVE',
        },
      ]
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({})

      await store.schedulePost({
        content: '   \n\t  ',
        title: 'Title',
        channels: ['linkedin'],
        scheduledAt: '2026-06-20T14:00:00Z',
        priority: false,
      })

      const body = JSON.parse(apiFetch.mock.calls[0]?.[1]?.body as string)
      expect(body.bodyText).toBe('')
    })

    it('preserves internal whitespace and intentional newlines while trimming edges', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [
        {
          id: 'ch-trim-3',
          accountId: 'ch-trim-3',
          name: 'Trim Test 3',
          provider: 'linkedin',
          avatar: '',
          handle: 'Trim Test 3',
          status: 'ACTIVE',
        },
      ]
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({})

      await store.schedulePost({
        content: '  Line one\nLine two  ',
        title: 'Title',
        channels: ['linkedin'],
        scheduledAt: '2026-06-20T14:00:00Z',
        priority: false,
      })

      const body = JSON.parse(apiFetch.mock.calls[0]?.[1]?.body as string)
      expect(body.bodyText).toBe('Line one\nLine two')
    })
  })

  describe('schedulePost', () => {
    it.each([
      ['NOW', '2026-06-20T14:30:00Z', null],
      ['NEXT_SLOT', null, '2026-06-20T15:00:00Z'],
      ['SCHEDULED_AT', '2026-06-20T16:00:00Z', null],
    ] as const)('adopts authenticated %s create identity and normalized server fields', async (scheduleMode, scheduledFor, nextSlotAfter) => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [makeChannelForStore('soc-create')]
      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: `backend-${scheduleMode}`,
        workspaceId: 'workspace-1',
        socialAccountId: 'soc-create',
        status: scheduleMode === 'SCHEDULED_AT' ? 'SCHEDULED' : 'QUEUED',
        scheduleMode,
        priority: true,
        title: 'Server title',
        bodyText: 'Server body',
        assetIds: ['asset-server'],
        scheduledFor,
        nextSlotAfter,
      })

      const result = await store.schedulePost({
        content: 'Client body',
        channels: ['linkedin'],
        scheduledAt: '2026-06-20T16:00:00Z',
        nextSlotAfter: '2026-06-20T14:00:00Z',
        scheduleMode,
        priority: false,
        socialAccountId: 'soc-create',
      })

      expect(result).toMatchObject({
        id: `backend-${scheduleMode}`,
        content: 'Server body',
        accountId: 'soc-create',
        status: scheduleMode === 'SCHEDULED_AT' ? 'SCHEDULED' : 'QUEUED',
        scheduleMode,
        scheduledAt: scheduledFor ?? nextSlotAfter ?? '',
        assetIds: ['asset-server'],
        priority: true,
      })
      expect(store.publications[0]?.id).toBe(`backend-${scheduleMode}`)
    })

    it('uses the authoritative NOW scheduledFor as a valid display timestamp', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [makeChannelForStore('9f06a3c8-account')]
      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: '8a25f709-40f6-4ab0-b5ae-f79bdcf4d395',
        workspaceId: 'workspace-1',
        socialAccountId: '9f06a3c8-account',
        status: 'QUEUED',
        scheduleMode: 'NOW',
        priority: false,
        title: 'Post from App',
        bodyText: 'Real NOW response',
        assetIds: ['real-media-asset-id'],
        scheduledFor: '2026-07-02T15:25:38.050321Z',
        nextSlotAfter: null,
      })

      const result = await store.schedulePost({
        content: 'Real NOW response',
        channels: ['linkedin'],
        scheduleMode: 'NOW',
        priority: false,
        socialAccountId: '9f06a3c8-account',
      })

      expect(result).toMatchObject({
        id: '8a25f709-40f6-4ab0-b5ae-f79bdcf4d395',
        scheduleMode: 'NOW',
        scheduledAt: '2026-07-02T15:25:38.050321Z',
        assetIds: ['real-media-asset-id'],
      })
      expect(Number.isNaN(new Date(result.scheduledAt).getTime())).toBe(false)
    })

    it('does not insert a placeholder when authenticated create fails', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [makeChannelForStore('soc-create')]
      const before = store.publications.map((publication) => publication.id)
      vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('create failed'))

      await expect(
        store.schedulePost({
          content: 'Never inserted',
          channels: ['linkedin'],
          scheduleMode: 'NOW',
          priority: false,
        }),
      ).rejects.toThrow('create failed')

      expect(store.publications.map((publication) => publication.id)).toEqual(before)
    })
    it('uses the selected connected LinkedIn account id for authenticated scheduling', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [
        {
          id: 'soc-real-1',
          accountId: 'soc-real-1',
          name: 'Real Profile 1',
          provider: 'linkedin',
          avatar: '',
          handle: 'Real Profile 1',
          status: 'ACTIVE',
        },
        {
          id: 'soc-real-2',
          accountId: 'soc-real-2',
          name: 'Real Profile 2',
          provider: 'linkedin',
          avatar: '',
          handle: 'Real Profile 2',
          status: 'ACTIVE',
        },
      ]
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({})

      await store.schedulePost({
        content: 'Post content',
        title: 'Title',
        channels: ['linkedin'],
        scheduledAt: '2026-06-20T14:00:00Z',
        priority: false,
        socialAccountId: 'soc-real-2',
      })

      const body = JSON.parse(apiFetch.mock.calls[0]?.[1]?.body as string)
      expect(body.socialAccountId).toBe('soc-real-2')
      expect(body.socialAccountId).not.toBe('account-linkedin-mock')
      expect(apiFetch.mock.calls[0]?.[1]).toMatchObject({ workspaceScoped: true })
    })

    it('falls back to the first active LinkedIn account when socialAccountId is omitted', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [
        {
          id: 'soc-fallback-1',
          accountId: 'soc-fallback-1',
          name: 'Fallback Profile',
          provider: 'linkedin',
          avatar: '',
          handle: 'Fallback Profile',
          status: 'ACTIVE',
        },
        {
          id: 'soc-fallback-2',
          accountId: 'soc-fallback-2',
          name: 'Other Profile',
          provider: 'linkedin',
          avatar: '',
          handle: 'Other Profile',
          status: 'ACTIVE',
        },
      ]
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: 'backend-fallback',
        workspaceId: 'workspace-1',
        socialAccountId: 'soc-fallback-1',
        status: 'SCHEDULED',
        scheduleMode: 'SCHEDULED_AT',
        priority: false,
        title: 'Title',
        bodyText: 'Post content',
        assetIds: [],
        scheduledFor: '2026-06-20T14:00:00Z',
        nextSlotAfter: null,
      })

      const result = await store.schedulePost({
        content: 'Post content',
        title: 'Title',
        channels: ['linkedin'],
        scheduledAt: '2026-06-20T14:00:00Z',
        priority: false,
      })

      const body = JSON.parse(apiFetch.mock.calls[0]?.[1]?.body as string)
      expect(body.socialAccountId).toBe('soc-fallback-1')
      expect(result.accountId).toBe('soc-fallback-1')
    })

    it('throws for authenticated LinkedIn scheduling when no connected channel exists', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = []
      const apiFetch = vi.spyOn(auth, 'apiFetch')

      await expect(
        store.schedulePost({
          content: 'Post content',
          title: 'Title',
          channels: ['linkedin'],
          scheduledAt: '2026-06-20T14:00:00Z',
          priority: false,
        }),
      ).rejects.toThrow('Connect a LinkedIn profile before scheduling authenticated posts.')
      expect(apiFetch).not.toHaveBeenCalled()
    })

    it('saves locally when user is not authenticated', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })
      const apiFetch = vi.spyOn(auth, 'apiFetch')

      const result = await store.schedulePost({
        content: 'Offline post',
        title: 'Title',
        channels: ['linkedin'],
        scheduledAt: '2026-06-20T14:00:00Z',
        priority: false,
      })

      expect(apiFetch).not.toHaveBeenCalled()
      expect(store.publications[0]?.content).toBe('Offline post')
      expect(result.content).toBe('Offline post')
    })
  })

  describe('fetchCalendar', () => {
    it('maps API response to local publications, activity, and conflicts', async () => {
      const store = usePublishingStore()

      // Mock apiFetch on the auth store
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockResolvedValue(mockConflictCalendarResponse)

      await store.fetchCalendar('2026-06-01T00:00:00Z', '2026-07-01T00:00:00Z')

      expect(store.publications).toHaveLength(2)
      expect(store.publications[0]?.content).toBe('First post content')
      expect(store.publications[0]?.hasConflict).toBe(true)
      expect(store.publications[0]?.conflictingPublicationIds).toEqual(['api-pub-2'])

      expect(store.conflicts).toHaveLength(2)
      expect(store.activity).toHaveLength(1)
      expect(store.activity[0]?.density).toBe('MEDIUM')
    })

    it('maps unknown backend status to DRAFT and preview urls through resolveApiUrl', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publications: [
          {
            id: 'api-pub-draft',
            workspaceId: 'ws_1',
            socialAccountId: 'acc-li-1',
            provider: 'UNKNOWN',
            status: 'SOMETHING_NEW',
            scheduleMode: 'SCHEDULED_AT',
            priority: false,
            title: null,
            bodyText: null,
            scheduledFor: null,
            hasConflict: false,
            conflictingPublicationIds: [],
            previewUrl: '/media/preview.png',
          },
        ],
        conflicts: [],
        activity: [],
      })

      await store.fetchCalendar('2026-06-01T00:00:00Z', '2026-07-01T00:00:00Z')

      expect(store.publications[0]).toMatchObject({
        status: 'DRAFT',
        channels: ['linkedin'],
        thumbnail: 'https://api.test/media/preview.png',
      })
    })

    it('maps blocked and failed publication diagnostics from the calendar API', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publications: [
          {
            id: 'api-pub-blocked',
            workspaceId: 'ws_1',
            socialAccountId: 'acc-li-1',
            provider: 'linkedin',
            status: 'BLOCKED',
            scheduleMode: 'SCHEDULED_AT',
            priority: false,
            title: 'Reconnect required',
            bodyText: 'Publishing paused until LinkedIn reconnects.',
            scheduledFor: '2026-06-10T12:00:00Z',
            hasConflict: false,
            conflictingPublicationIds: [],
            blockedReason: 'LinkedIn account requires reconnect',
          },
          {
            id: 'api-pub-failed',
            workspaceId: 'ws_1',
            socialAccountId: 'acc-li-1',
            provider: 'linkedin',
            status: 'FAILED',
            scheduleMode: 'SCHEDULED_AT',
            priority: false,
            title: 'Publish failed',
            bodyText: 'LinkedIn rejected the payload.',
            scheduledFor: '2026-06-10T13:00:00Z',
            hasConflict: false,
            conflictingPublicationIds: [],
            errorCode: 'LINKEDIN_VALIDATION_ERROR',
          },
        ],
        conflicts: [],
        activity: [],
      })

      await store.fetchCalendar('2026-06-01T00:00:00Z', '2026-07-01T00:00:00Z')

      expect(store.publications[0]).toMatchObject({
        id: 'api-pub-blocked',
        status: 'BLOCKED',
        blockedReason: 'LinkedIn account requires reconnect',
      })
      expect(store.publications[1]).toMatchObject({
        id: 'api-pub-failed',
        status: 'FAILED',
        errorCode: 'LINKEDIN_VALIDATION_ERROR',
      })
    })

    it('retries a failed publication through the retry endpoint and updates local state', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.publications = [
        {
          id: 'failed-pub',
          content: 'Retry this post',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          scheduleMode: 'SCHEDULED_AT',
          status: 'FAILED',
          priority: false,
          errorCode: 'LINKEDIN_VALIDATION_ERROR',
        },
      ]
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: 'failed-pub',
        workspaceId: 'workspace-1',
        socialAccountId: 'soc-1',
        status: 'QUEUED',
        scheduleMode: 'NOW',
        priority: false,
        title: null,
        bodyText: 'Retry this post',
        assetIds: [],
        scheduledFor: null,
        nextSlotAfter: null,
        externalPublicationId: null,
        publicUrl: null,
        publishedAt: null,
      })

      const result = await store.retryPublication('failed-pub')

      expect(apiFetch).toHaveBeenCalledWith('/api/publishing/publications/failed-pub/retry', {
        method: 'POST',
        body: JSON.stringify({
          scheduleMode: 'NOW',
          priority: false,
        }),
        workspaceScoped: true,
      })
      expect(result.status).toBe('QUEUED')
      expect(result.errorCode).toBeUndefined()
      expect(store.publications[0]?.status).toBe('QUEUED')
    })

    it('falls back to local data on API error', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('Network error'))

      // Set initial local publications
      store.publications = [
        {
          id: 'local-1',
          content: 'Local post',
          channels: ['linkedin'],
          scheduledAt: '2026-06-10T12:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      await store.fetchCalendar('2026-06-01T00:00:00Z', '2026-07-01T00:00:00Z')

      // Should still have the local publication
      expect(store.publications.some((p) => p.id === 'local-1')).toBe(true)
    })

    it('applies local filters in fallback mode and clears api-only state', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })
      store.publications = [
        {
          id: 'queued-linkedin',
          content: 'Architecture notes',
          channels: ['linkedin'],
          accountId: 'acc-1',
          scheduledAt: '2026-06-10T12:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
        {
          id: 'published-linkedin',
          content: 'Architecture shipped',
          channels: ['linkedin'],
          accountId: 'acc-1',
          scheduledAt: '2026-06-11T12:00:00Z',
          status: 'PUBLISHED',
          priority: false,
        },
        {
          id: 'queued-instagram',
          content: 'Visual post',
          channels: ['instagram'],
          accountId: 'acc-2',
          scheduledAt: '2026-06-12T12:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]
      store.activity = [{ date: '2026-06-10', density: 'HIGH', count: 3 }]
      store.conflicts = [
        {
          publicationId: 'queued-linkedin',
          conflictingPublicationIds: ['published-linkedin'],
          reason: 'OVERLAPPING_SCHEDULE',
        },
      ]
      store.filterChannel = 'linkedin'
      store.filterSocialAccountId = 'acc-1'
      store.filterTag = 'arch'
      store.filterPostType = 'queued'

      await store.fetchCalendar('2026-06-01T00:00:00Z', '2026-07-01T00:00:00Z')

      expect(store.publications.map((pub) => pub.id)).toEqual(['queued-linkedin'])
      expect(store.activity).toEqual([])
      expect(store.conflicts).toEqual([])
    })

    it('drops stale responses when a newer fetchCalendar has started', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })

      const freshResponse = {
        publications: [
          {
            id: 'api-fresh',
            workspaceId: 'ws_1',
            socialAccountId: 'acc-1',
            provider: 'LINKEDIN',
            status: 'QUEUED',
            scheduleMode: 'SCHEDULED_AT',
            priority: false,
            title: null,
            bodyText: 'fresh',
            scheduledFor: '2026-06-10T12:00:00Z',
            hasConflict: false,
            conflictingPublicationIds: [],
          },
        ],
        conflicts: [],
        activity: [{ date: '2026-06-10', density: 'LOW' }],
      }
      const staleResponse = {
        publications: [
          {
            id: 'api-stale',
            workspaceId: 'ws_1',
            socialAccountId: 'acc-1',
            provider: 'LINKEDIN',
            status: 'QUEUED',
            scheduleMode: 'SCHEDULED_AT',
            priority: false,
            title: null,
            bodyText: 'stale',
            scheduledFor: '2026-06-01T12:00:00Z',
            hasConflict: false,
            conflictingPublicationIds: [],
          },
        ],
        conflicts: [{ id: 'stale-conflict' }],
        activity: [{ date: '2026-06-01', density: 'HIGH' }],
      }

      // The older (chronologically first) fetchCalendar call will resolve LAST,
      // so without the overlapping-request guard it would clobber the fresh data.
      let resolveOlder: (() => void) | undefined
      let callIndex = 0
      const spy = vi.spyOn(auth, 'apiFetch').mockImplementation(async () => {
        callIndex += 1
        if (callIndex === 1) {
          await new Promise<void>((resolve) => {
            resolveOlder = resolve
          })
          return staleResponse
        }
        return freshResponse
      })

      // Kick off the OLDER call first; it parks on resolveOlder.
      const older = store.fetchCalendar('2026-06-01T00:00:00Z', '2026-06-07T00:00:00Z')
      // Now kick off the NEWER call; it resolves immediately with fresh data.
      const newer = store.fetchCalendar('2026-06-08T00:00:00Z', '2026-06-14T00:00:00Z')
      await newer
      // Release the older call — its response must be dropped by the guard.
      resolveOlder?.()
      await older

      // The store should reflect the NEWER call's payload; the older response
      // must NOT have overwritten it.
      expect(store.publications.map((p) => p.id)).toEqual(['api-fresh'])
      expect(store.conflicts).toEqual([])
      expect(store.activity).toEqual([{ date: '2026-06-10', density: 'LOW' }])
      // Sanity: apiFetch was invoked twice.
      expect(spy).toHaveBeenCalledTimes(2)
    })
  })

  describe('quickCreatePost', () => {
    it('creates a publication in local state', async () => {
      const store = usePublishingStore()
      const result = await store.quickCreatePost({
        socialAccountId: 'acc-li-1',
        title: 'Quick',
        bodyText: 'Quick post content',
        scheduledFor: '2026-06-20T14:00:00Z',
      })

      expect(result.id).toBeTruthy()
      expect(result.content).toBe('Quick post content')
      expect(result.channels).toContain('linkedin')
      // Should be added to the beginning of the list
      expect(store.publications[0]?.id).toBe(result.id)
    })

    it('adopts authenticated quick-create server identity and normalized fields', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: 'backend-quick-1',
        workspaceId: 'workspace-1',
        socialAccountId: 'acc-li-99',
        status: 'SCHEDULED',
        scheduleMode: 'SCHEDULED_AT',
        priority: true,
        title: 'Quick Auth normalized',
        bodyText: 'Authenticated quick create normalized',
        assetIds: ['quick-asset'],
        scheduledFor: '2026-06-20T14:05:00Z',
        nextSlotAfter: null,
      })

      const result = await store.quickCreatePost({
        socialAccountId: 'acc-li-99',
        title: 'Quick Auth',
        bodyText: 'Authenticated quick create',
        scheduledFor: '2026-06-20T14:00:00Z',
        priority: true,
      })

      expect(apiFetch).toHaveBeenCalledWith('/api/publishing/publications/quick-create', {
        method: 'POST',
        body: JSON.stringify({
          socialAccountId: 'acc-li-99',
          title: 'Quick Auth',
          bodyText: 'Authenticated quick create',
          scheduledFor: '2026-06-20T14:00:00Z',
          priority: true,
        }),
        workspaceScoped: true,
      })
      expect(result).toMatchObject({
        id: 'backend-quick-1',
        content: 'Authenticated quick create normalized',
        accountId: 'acc-li-99',
        status: 'SCHEDULED',
        scheduleMode: 'SCHEDULED_AT',
        scheduledAt: '2026-06-20T14:05:00Z',
        assetIds: ['quick-asset'],
      })
      expect(store.publications[0]?.id).toBe('backend-quick-1')
    })

    it('propagates authenticated quick-create failure without local insertion', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      const before = store.publications.map((publication) => publication.id)
      vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('quick create failed'))

      await expect(
        store.quickCreatePost({
          socialAccountId: 'acc-li-99',
          bodyText: 'Never inserted',
          scheduledFor: '2026-06-20T14:00:00Z',
        }),
      ).rejects.toThrow('quick create failed')
      expect(store.publications.map((publication) => publication.id)).toEqual(before)
    })
  })

  describe('reschedulePublication', () => {
    it('optimistically updates then reverts on API failure', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()

      // Add a publication
      store.publications = [
        {
          id: 'pub-1',
          content: 'Test',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      // Mock API to fail
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('Server error'))

      const originalDate = store.publications[0]?.scheduledAt

      // Attempt reschedule — should throw but NOT mutate state
      await expect(store.reschedulePublication('pub-1', '2026-06-16T10:00:00Z')).rejects.toThrow()

      // State should be rolled back
      expect(store.publications[0]?.scheduledAt).toBe(originalDate)
    })

    it('keeps optimistic update on success', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()

      store.publications = [
        {
          id: 'pub-1',
          content: 'Test',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockResolvedValue({})

      await store.reschedulePublication('pub-1', '2026-06-16T10:00:00Z')

      expect(store.publications[0]?.scheduledAt).toBe('2026-06-16T10:00:00Z')
    })

    it('saves locally when unauthenticated', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })
      store.publications = [
        {
          id: 'pub-local',
          content: 'Test',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      const result = await store.reschedulePublication('pub-local', '2026-06-16T10:00:00Z')

      expect(result?.scheduledAt).toBe('2026-06-16T10:00:00Z')
      expect(store.publications[0]?.scheduledAt).toBe('2026-06-16T10:00:00Z')
    })

    it('throws when publication is not found', async () => {
      const store = usePublishingStore()
      await expect(
        store.reschedulePublication('nonexistent', '2026-07-01T00:00:00Z'),
      ).rejects.toThrow('Publication nonexistent not found')
    })
  })

  describe('local mutations', () => {
    it('omits assetIds from PATCH when update does not include assetIds', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.publications = [
        {
          id: 'patch-preserve-assets',
          content: 'Original body',
          title: 'Original title',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          scheduleMode: 'SCHEDULED_AT',
          status: 'QUEUED',
          priority: false,
          accountId: 'soc-1',
          assetIds: ['asset-a', 'asset-b'],
        },
      ]
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: 'patch-preserve-assets',
        workspaceId: 'workspace-1',
        socialAccountId: 'soc-1',
        status: 'SCHEDULED',
        scheduleMode: 'SCHEDULED_AT',
        priority: false,
        title: 'Original title',
        bodyText: 'Changed body',
        assetIds: ['asset-a', 'asset-b'],
        scheduledFor: '2026-06-15T20:00:00Z',
        nextSlotAfter: null,
      })

      await store.updatePost('patch-preserve-assets', { content: 'Changed body' })

      const body = JSON.parse(apiFetch.mock.calls[0]?.[1]?.body as string)
      expect(body).not.toHaveProperty('assetIds')
    })

    it('serializes empty and replacement assetIds in PATCH when provided', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.publications = [
        {
          id: 'patch-replace-assets',
          content: 'Original body',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
          accountId: 'soc-1',
          assetIds: ['asset-a', 'asset-b'],
        },
      ]
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: 'patch-replace-assets',
        workspaceId: 'workspace-1',
        socialAccountId: 'soc-1',
        status: 'SCHEDULED',
        scheduleMode: 'SCHEDULED_AT',
        priority: false,
        title: null,
        bodyText: 'Original body',
        assetIds: [],
        scheduledFor: '2026-06-15T20:00:00Z',
        nextSlotAfter: null,
      })

      await store.updatePost('patch-replace-assets', { assetIds: [] })
      let body = JSON.parse(apiFetch.mock.calls[0]?.[1]?.body as string)
      expect(body.assetIds).toEqual([])

      apiFetch.mockResolvedValueOnce({
        publicationId: 'patch-replace-assets',
        workspaceId: 'workspace-1',
        socialAccountId: 'soc-1',
        status: 'SCHEDULED',
        scheduleMode: 'SCHEDULED_AT',
        priority: false,
        title: null,
        bodyText: 'Original body',
        assetIds: ['asset-c', 'asset-d'],
        scheduledFor: '2026-06-15T20:00:00Z',
        nextSlotAfter: null,
      })

      await store.updatePost('patch-replace-assets', { assetIds: ['asset-c', 'asset-d'] })
      body = JSON.parse(apiFetch.mock.calls[1]?.[1]?.body as string)
      expect(body.assetIds).toEqual(['asset-c', 'asset-d'])
    })

    it('cancels a post in place', () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: 'cancel-me',
          content: 'Queued post',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      store.cancelPost('cancel-me')

      expect(store.publications[0]?.status).toBe('CANCELLED')
    })

    it('updates post fields and manages blob thumbnail lifecycle', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      // Stub URL globally since jsdom does not expose it
      const stubUrl = { revokeObjectURL: vi.fn(), createObjectURL: vi.fn() }
      vi.stubGlobal('URL', stubUrl)

      store.publications = [
        {
          id: 'update-me',
          content: 'Queued post',
          title: 'Original title',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
          thumbnail: 'blob:old-thumb',
          accountId: 'soc-1',
        },
      ]

      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: 'update-me',
        workspaceId: 'workspace-1',
        socialAccountId: 'soc-1',
        status: 'SCHEDULED',
        scheduleMode: 'SCHEDULED_AT',
        priority: false,
        title: 'Updated again',
        bodyText: 'Queued post',
        assetIds: [],
        scheduledFor: '2026-06-15T20:00:00Z',
        nextSlotAfter: null,
        externalPublicationId: null,
        publicUrl: null,
        publishedAt: null,
      })

      await store.updatePost('update-me', { thumbnail: 'blob:new-thumb', title: 'Updated again' })

      expect(store.publications[0]).toMatchObject({ title: 'Updated again', status: 'SCHEDULED' })
    })

    it('rolls back local publication when update fails', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.publications = [
        {
          id: 'update-fail',
          content: 'Original body',
          title: 'Original title',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
          accountId: 'soc-1',
        },
      ]
      vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('PATCH failed'))

      await expect(store.updatePost('update-fail', { content: 'Changed body' })).rejects.toThrow(
        'PATCH failed',
      )
      expect(store.publications[0]).toMatchObject({
        content: 'Original body',
        title: 'Original title',
      })
    })

    it('deletes a post through the backend and revokes tracked blob urls', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      const stubUrl = { revokeObjectURL: vi.fn(), createObjectURL: vi.fn() }
      vi.stubGlobal('URL', stubUrl)

      store.publications = [
        {
          id: 'delete-me',
          content: 'Queued post',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
          thumbnail: 'blob:to-delete',
        },
      ]

      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: 'delete-me',
        workspaceId: 'workspace-1',
        socialAccountId: 'soc-1',
        status: 'QUEUED',
        scheduleMode: 'SCHEDULED_AT',
        priority: false,
        title: null,
        bodyText: 'Queued post',
        assetIds: [],
        scheduledFor: '2026-06-15T20:00:00Z',
        nextSlotAfter: null,
        externalPublicationId: null,
        publicUrl: null,
        publishedAt: null,
      })

      await store.updatePost('delete-me', { thumbnail: 'blob:to-delete' })
      await store.deletePost('delete-me')

      expect(store.publications).toEqual([])
      expect(stubUrl.revokeObjectURL).toHaveBeenCalledWith('blob:to-delete')
    })

    it('retains local publication when delete fails', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.publications = [
        {
          id: 'delete-fail',
          content: 'Queued post',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]
      vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('DELETE failed'))

      await expect(store.deletePost('delete-fail')).rejects.toThrow('DELETE failed')
      expect(store.publications).toHaveLength(1)
      expect(store.publications[0]?.id).toBe('delete-fail')
    })

    it('throws when publication is not found in deletePost', async () => {
      const store = usePublishingStore()
      store.publications = []

      await expect(store.deletePost('non-existent')).rejects.toThrow(
        'Publication non-existent not found',
      )
    })

    it('deletes a post locally when unauthenticated', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })
      store.publications = [
        {
          id: 'delete-local',
          content: 'Queued post',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      await store.deletePost('delete-local')

      expect(store.publications).toEqual([])
    })

    it('updates a post locally when unauthenticated', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })
      store.publications = [
        {
          id: 'update-local',
          content: 'Original body',
          title: 'Original title',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      await store.updatePost('update-local', { content: 'Updated body', title: 'Updated title' })

      expect(store.publications[0]).toMatchObject({
        content: 'Updated body',
        title: 'Updated title',
      })
    })

    it('revokes old blob thumbnail and tracks new one on unauthenticated update', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })
      const stubUrl = { revokeObjectURL: vi.fn(), createObjectURL: vi.fn() }
      vi.stubGlobal('URL', stubUrl)

      store.publications = [
        {
          id: 'blob-update',
          content: 'Body',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
          thumbnail: 'blob:old-thumb',
        },
      ]

      // First update to track the blob
      await store.updatePost('blob-update', { thumbnail: 'blob:tracked-thumb' })

      // Second update replaces the blob — old one should be revoked
      await store.updatePost('blob-update', { thumbnail: 'blob:new-thumb' })

      expect(stubUrl.revokeObjectURL).toHaveBeenCalledWith('blob:tracked-thumb')

      // Verify blob:new-thumb is tracked — a third replacement revokes it
      await store.updatePost('blob-update', { thumbnail: 'blob:final-thumb' })
      expect(stubUrl.revokeObjectURL).toHaveBeenCalledWith('blob:new-thumb')
    })
  })

  describe('filterSocialAccountId', () => {
    it('is initially empty', () => {
      const store = usePublishingStore()
      expect(store.filterSocialAccountId).toBe('')
    })

    it('can be set and appears in calendarFilters', () => {
      const store = usePublishingStore()
      store.filterSocialAccountId = 'acc-li-1'
      expect(store.calendarFilters.socialAccountId).toBe('acc-li-1')
    })
  })

  describe('channel filters', () => {
    it('keeps local provider filtering independent from backend social account ids', async () => {
      // This test exercises the local fallback-only path (no API call).
      // fetchCalendar >> isAuthenticated === false >> applyLocalFilters
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })
      store.publications = [
        {
          id: 'local-linkedin',
          content: 'LinkedIn post',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
        {
          id: 'local-instagram',
          content: 'Instagram post',
          channels: ['instagram'],
          scheduledAt: '2026-06-15T21:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      store.filterChannel = 'linkedin'
      store.filterSocialAccountId = ''

      await store.fetchCalendar('2026-06-01T00:00:00Z', '2026-07-01T00:00:00Z')

      expect(store.publications).toHaveLength(1)
      expect(store.publications[0]?.id).toBe('local-linkedin')
    })
  })
})
