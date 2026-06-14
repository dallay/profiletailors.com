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
    localStorage.clear()
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
            profileUrn: 'urn:li:person:ada',
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
          handle: 'urn:li:person:ada',
          status: 'ACTIVE',
        },
      ])
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
            profileUrn: 'urn:li:person:grace',
            avatarUrl: null,
            connectedAt: '2026-06-12T12:00:00Z',
            lastSyncedAt: null,
          },
        ],
      })

      await store.fetchChannels()

      expect(store.channels[0]?.avatarUrl).toBeUndefined()
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

  describe('schedulePost', () => {
    it('uses the real connected LinkedIn account id for authenticated scheduling', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [
        {
          id: 'soc-real',
          accountId: 'soc-real',
          name: 'Real Profile',
          provider: 'linkedin',
          avatar: '',
          handle: 'Real Profile',
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
      })

      const body = JSON.parse(apiFetch.mock.calls[0]?.[1]?.body as string)
      expect(body.socialAccountId).toBe('soc-real')
      expect(body.socialAccountId).not.toBe('account-linkedin-mock')
      expect(apiFetch.mock.calls[0]?.[1]).toMatchObject({ workspaceScoped: true })
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
  })

  describe('fetchCalendar', () => {
    it('maps API response to local publications, activity, and conflicts', async () => {
      const store = usePublishingStore()

      // Mock apiFetch on the auth store
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockResolvedValue(mockConflictCalendarResponse)

      await store.fetchCalendar('2026-06-01T00:00:00Z', '2026-07-01T00:00:00Z')

      expect(store.publications.length).toBe(2)
      expect(store.publications[0]?.content).toBe('First post content')
      expect(store.publications[0]?.hasConflict).toBe(true)
      expect(store.publications[0]?.conflictingPublicationIds).toEqual(['api-pub-2'])

      expect(store.conflicts.length).toBe(2)
      expect(store.activity.length).toBe(1)
      expect(store.activity[0]?.density).toBe('MEDIUM')
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

    it('throws when publication is not found', async () => {
      const store = usePublishingStore()
      await expect(
        store.reschedulePublication('nonexistent', '2026-07-01T00:00:00Z'),
      ).rejects.toThrow('Publication nonexistent not found')
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
