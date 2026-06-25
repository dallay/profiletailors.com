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
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({})

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

    it('sends quick-create request when authenticated and preserves local insert', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({})

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
      expect(store.publications[0]?.id).toBe(result.id)
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
