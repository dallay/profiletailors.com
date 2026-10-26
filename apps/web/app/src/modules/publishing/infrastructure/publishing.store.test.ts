import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import {
  isSocialProvider,
  usePublishingStore,
  type CalendarResponse,
  type ProviderCatalogItem,
} from './publishing.store'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'

// ---------------------------------------------------------------------------
// Mock auth-api
// ---------------------------------------------------------------------------
vi.mock('@modules/auth/infrastructure/auth-api', () => ({
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

describe('isSocialProvider', () => {
  it('accepts publishable providers and rejects unknown connected providers', () => {
    expect(isSocialProvider('linkedin')).toBe(true)
    expect(isSocialProvider('threads')).toBe(true)
    expect(isSocialProvider('mastodon')).toBe(false)
  })
})

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeChannelForStore(
  accountId: string,
  overrides: Partial<{
    provider: 'linkedin' | 'twitter' | 'instagram' | 'facebook' | 'threads'
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
      id: 'bcb7936f-dc82-5b11-beaf-0ea0d32c1f0f',
      workspaceId: '9f6f84fe-2465-5955-ae9f-801107241c33',
      socialAccountId: '8ec9e4ac-9ec5-5c3a-8933-7af5407a797c',
      provider: 'LINKEDIN',
      status: 'SCHEDULED',
      scheduleMode: 'SCHEDULED_AT',
      priority: false,
      title: 'First Post',
      bodyText: 'First post content',
      assetIds: [],
      scheduledFor: '2026-06-15T20:00:00Z',
      hasConflict: true,
      conflictingPublicationIds: ['56536a72-fc15-50ce-8787-c1ffbfcefdf3'],
    },
    {
      id: '56536a72-fc15-50ce-8787-c1ffbfcefdf3',
      workspaceId: '9f6f84fe-2465-5955-ae9f-801107241c33',
      socialAccountId: '8ec9e4ac-9ec5-5c3a-8933-7af5407a797c',
      provider: 'LINKEDIN',
      status: 'SCHEDULED',
      scheduleMode: 'SCHEDULED_AT',
      priority: false,
      title: 'Second Post',
      bodyText: 'Second post content',
      assetIds: [],
      scheduledFor: '2026-06-15T20:10:00Z',
      hasConflict: true,
      conflictingPublicationIds: ['bcb7936f-dc82-5b11-beaf-0ea0d32c1f0f'],
    },
  ],
  conflicts: [
    {
      publicationId: 'bcb7936f-dc82-5b11-beaf-0ea0d32c1f0f',
      conflictingPublicationIds: ['56536a72-fc15-50ce-8787-c1ffbfcefdf3'],
      reason: 'OVERLAPPING_SCHEDULE',
    },
    {
      publicationId: '56536a72-fc15-50ce-8787-c1ffbfcefdf3',
      conflictingPublicationIds: ['bcb7936f-dc82-5b11-beaf-0ea0d32c1f0f'],
      reason: 'OVERLAPPING_SCHEDULE',
    },
  ],
  activity: [{ date: '2026-06-15', density: 'MEDIUM', count: 2 }],
}

const availableLinkedInProvider: ProviderCatalogItem = {
  provider: 'linkedin',
  accountKinds: ['PERSONAL_PROFILE'],
  state: 'AVAILABLE',
  reason: null,
  channelLimit: null,
  connectedChannelCount: 0,
  canConnectMore: true,
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

  describe('recurring schedules', () => {
    it('lists schedules scoped to the active workspace', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      const workspace = useWorkspaceStore()
      workspace.setActiveWorkspaceId('a219727d-019f-544c-95f9-ac30f00cc8b2')
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        schedules: [],
      } as never)

      await store.fetchRecurringSchedules()

      expect(apiFetch).toHaveBeenCalledWith(
        '/api/v1/workspaces/a219727d-019f-544c-95f9-ac30f00cc8b2/recurring',
        {
          method: 'GET',
          workspaceScoped: true,
        },
      )
    })

    it('creates a recurring schedule and adds it to local state', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      const workspace = useWorkspaceStore()
      workspace.setActiveWorkspaceId('a219727d-019f-544c-95f9-ac30f00cc8b2')
      const schedule = {
        id: 'a1dc2894-0de7-5ae9-a658-8f25f9d4b041',
        workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
        createdBy: '4dae1d83-d090-58ea-ac4b-eb99348c0689',
        templatePostId: '48c5787c-4ddb-5cd5-b205-cd0f99e7fc07',
        frequency: 'daily' as const,
        interval: 1,
        daysOfWeek: [],
        dayOfMonth: null,
        endDate: null,
        maxOccurrences: null,
        timezone: 'UTC',
        nextScheduledAt: '2026-08-01T09:00:00Z',
        status: 'ACTIVE' as const,
        createdAt: '2026-07-31T08:00:00Z',
        updatedAt: '2026-07-31T08:00:00Z',
      }
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue(schedule as never)

      await store.createRecurringSchedule('48c5787c-4ddb-5cd5-b205-cd0f99e7fc07', {
        frequency: 'daily',
        interval: 1,
        startsAt: '2026-08-01T09:00:00Z',
        timezone: 'UTC',
      })

      expect(apiFetch).toHaveBeenCalledWith(
        '/api/v1/workspaces/a219727d-019f-544c-95f9-ac30f00cc8b2/recurring',
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('"templatePostId":"48c5787c-4ddb-5cd5-b205-cd0f99e7fc07"'),
        }),
      )
      expect(store.recurringSchedules).toEqual([schedule])
    })

    it('updates a schedule and replaces it in local state', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      const workspace = useWorkspaceStore()
      workspace.setActiveWorkspaceId('a219727d-019f-544c-95f9-ac30f00cc8b2')
      store.recurringSchedules = [
        {
          id: 'a1dc2894-0de7-5ae9-a658-8f25f9d4b041',
          workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
          createdBy: '4dae1d83-d090-58ea-ac4b-eb99348c0689',
          templatePostId: '48c5787c-4ddb-5cd5-b205-cd0f99e7fc07',
          frequency: 'daily',
          interval: 1,
          daysOfWeek: [],
          dayOfMonth: null,
          endDate: null,
          maxOccurrences: null,
          timezone: 'UTC',
          nextScheduledAt: '2026-08-01T09:00:00Z',
          status: 'ACTIVE',
          createdAt: '2026-07-31T08:00:00Z',
          updatedAt: '2026-07-31T08:00:00Z',
        },
      ]
      const updated = {
        ...store.recurringSchedules[0],
        status: 'PAUSED' as const,
        updatedAt: '2026-07-31T09:00:00Z',
      }
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue(updated as never)

      await store.updateRecurringSchedule('a1dc2894-0de7-5ae9-a658-8f25f9d4b041', {
        status: 'PAUSED',
      })

      expect(apiFetch).toHaveBeenCalledWith(
        '/api/v1/workspaces/a219727d-019f-544c-95f9-ac30f00cc8b2/recurring/a1dc2894-0de7-5ae9-a658-8f25f9d4b041',
        expect.objectContaining({
          method: 'PATCH',
          body: expect.stringContaining('"status":"PAUSED"'),
        }),
      )
      expect(store.recurringSchedules[0]).toEqual(updated)
    })

    it('cancels a schedule locally only after the API succeeds', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      const workspace = useWorkspaceStore()
      workspace.setActiveWorkspaceId('a219727d-019f-544c-95f9-ac30f00cc8b2')
      store.recurringSchedules = [
        {
          id: 'a1dc2894-0de7-5ae9-a658-8f25f9d4b041',
          workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
          createdBy: '4dae1d83-d090-58ea-ac4b-eb99348c0689',
          templatePostId: '48c5787c-4ddb-5cd5-b205-cd0f99e7fc07',
          frequency: 'daily',
          interval: 1,
          daysOfWeek: [],
          dayOfMonth: null,
          endDate: null,
          maxOccurrences: null,
          timezone: 'UTC',
          nextScheduledAt: null,
          status: 'ACTIVE',
          createdAt: null,
          updatedAt: null,
        },
      ]
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue(undefined as never)

      await store.cancelRecurringSchedule('a1dc2894-0de7-5ae9-a658-8f25f9d4b041')

      expect(apiFetch).toHaveBeenCalledWith(
        '/api/v1/workspaces/a219727d-019f-544c-95f9-ac30f00cc8b2/recurring/a1dc2894-0de7-5ae9-a658-8f25f9d4b041',
        {
          method: 'DELETE',
          workspaceScoped: true,
        },
      )
      expect(store.recurringSchedules[0]?.status).toBe('CANCELLED')
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
      store.filterSocialAccountId = '8ec9e4ac-9ec5-5c3a-8933-7af5407a797c'
      expect(store.calendarFilters.socialAccountId).toBe('8ec9e4ac-9ec5-5c3a-8933-7af5407a797c')
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
            socialAccountId: '223bd3b9-4c0a-57cd-826f-681e172f6d7d',
            connectionId: '7fb06252-ea7d-5a8f-b3ad-726b3222f6f2',
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
          id: '223bd3b9-4c0a-57cd-826f-681e172f6d7d',
          accountId: '223bd3b9-4c0a-57cd-826f-681e172f6d7d',
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

    it('preserves an unknown backend provider for neutral connected-channel presentation', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        channels: [
          {
            socialAccountId: '2216a2d2-3ffe-5659-b5a9-f1555d18ffe2',
            connectionId: '8efcf6a1-25e3-5d9a-8116-598550729ee5',
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

      expect(store.channels[0]?.provider).toBe('mastodon')
    })

    it('maps null avatarUrl to undefined when backend omits avatar', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        channels: [
          {
            socialAccountId: 'd63e734c-cf36-5999-b9dc-66aa76d9a2d2',
            connectionId: 'ee9210e6-21e2-536b-b170-b42edd43caae',
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

  describe('refreshChannelAvatars', () => {
    const oldChannel = {
      socialAccountId: '223bd3b9-4c0a-57cd-826f-681e172f6d7d',
      connectionId: '7fb06252-ea7d-5a8f-b3ad-726b3222f6f2',
      provider: 'LINKEDIN',
      accountKind: 'PERSONAL_PROFILE',
      displayName: 'Ada Lovelace',
      status: 'ACTIVE',
      avatarUrl: 'https://media.licdn.com/old.jpg',
      connectedAt: '2026-06-12T12:00:00Z',
      lastSyncedAt: null,
    }
    const freshChannel = { ...oldChannel, avatarUrl: 'https://media.licdn.com/fresh.jpg' }

    function mockChannelsFlow(getResponses: unknown[], refreshResponse: unknown) {
      const calls: string[] = []
      const apiFetch = vi
        .spyOn(useAuthStore(), 'apiFetch')
        .mockImplementation(async (path: string) => {
          calls.push(path)
          if (path === '/api/publishing/channels/refresh-avatars') return refreshResponse
          return getResponses.shift() ?? { channels: [] }
        })
      return { apiFetch, calls }
    }

    function authenticate() {
      Object.defineProperty(useAuthStore(), 'isAuthenticated', {
        value: true,
        configurable: true,
      })
    }

    it('reloads channels once when avatars were refreshed', async () => {
      const store = usePublishingStore()
      authenticate()
      const { apiFetch } = mockChannelsFlow(
        [{ channels: [oldChannel] }, { channels: [freshChannel] }],
        {
          refreshedAccountIds: ['223bd3b9-4c0a-57cd-826f-681e172f6d7d'],
          skippedAccountIds: [],
          failedAccountIds: [],
          refreshed: 1,
          skipped: 0,
          failed: 0,
        },
      )

      await store.fetchChannels()

      expect(apiFetch).toHaveBeenCalledWith('/api/publishing/channels/refresh-avatars', {
        method: 'POST',
        workspaceScoped: true,
      })
      expect(
        apiFetch.mock.calls.filter(([path]) => path === '/api/publishing/channels'),
      ).toHaveLength(2)
      expect(store.channels[0]?.avatarUrl).toBe('https://media.licdn.com/fresh.jpg')
    })

    it('does not reload channels when no avatar changed', async () => {
      const store = usePublishingStore()
      authenticate()
      const { apiFetch } = mockChannelsFlow([{ channels: [oldChannel] }], {
        refreshedAccountIds: [],
        skippedAccountIds: ['223bd3b9-4c0a-57cd-826f-681e172f6d7d'],
        failedAccountIds: [],
        refreshed: 0,
        skipped: 1,
        failed: 0,
      })

      await store.fetchChannels()

      expect(apiFetch).toHaveBeenCalledWith('/api/publishing/channels/refresh-avatars', {
        method: 'POST',
        workspaceScoped: true,
      })
      expect(
        apiFetch.mock.calls.filter(([path]) => path === '/api/publishing/channels'),
      ).toHaveLength(1)
      expect(store.channels[0]?.avatarUrl).toBe('https://media.licdn.com/old.jpg')
    })

    it('keeps loaded channels when the refresh request fails', async () => {
      const store = usePublishingStore()
      authenticate()
      vi.spyOn(useAuthStore(), 'apiFetch').mockImplementation(async (path: string) => {
        if (path === '/api/publishing/channels/refresh-avatars') throw new Error('refresh down')
        return { channels: [oldChannel] }
      })

      await store.fetchChannels()

      expect(store.channels[0]?.avatarUrl).toBe('https://media.licdn.com/old.jpg')
    })
  })

  describe('provider connection lifecycle', () => {
    it('initiates a Threads connection through the provider-aware endpoint', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      const assign = vi.fn()
      Object.defineProperty(window, 'location', {
        value: { origin: 'http://app.test', assign },
        configurable: true,
      })
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        authorizationUrl: 'https://threads.example/auth',
        state: 'state-threads',
        expiresAt: '2026-06-12T12:10:00Z',
      })

      await store.connectProviderPersonalProfile('threads')

      expect(apiFetch).toHaveBeenCalledWith('/api/publishing/threads/connections/initiate', {
        method: 'POST',
        body: JSON.stringify({ redirectUri: 'http://app.test/integrations/threads/callback' }),
        workspaceScoped: true,
      })
      expect(assign).toHaveBeenCalledWith('https://threads.example/auth')
    })

    it('completes a Threads connection and refreshes channels without exposing callback values', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      const apiFetch = vi
        .spyOn(auth, 'apiFetch')
        .mockResolvedValueOnce({
          connectionId: '30aa483b-cc38-5c63-a7cf-75c4bfe26665',
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
          provider: 'threads',
          status: 'connected',
          account: {
            accountId: 'd48b9de6-2379-5ef3-8987-13d1effb08e7',
            providerAccountId: 'remote-threads',
            displayName: 'Threads profile',
            kind: 'PROFILE',
            profileUrn: null,
          },
        })
        .mockResolvedValueOnce({ channels: [] })

      await store.completeProviderConnectionFromCallback({
        provider: 'threads',
        code: 'opaque-code',
        state: 'opaque-state',
        redirectUri: 'http://app.test/integrations/threads/callback',
      })

      expect(apiFetch).toHaveBeenNthCalledWith(1, '/api/publishing/threads/connections/complete', {
        method: 'POST',
        body: JSON.stringify({
          authorizationCode: 'opaque-code',
          redirectUri: 'http://app.test/integrations/threads/callback',
          state: 'opaque-state',
        }),
        workspaceScoped: true,
      })
      expect(apiFetch).toHaveBeenNthCalledWith(2, '/api/publishing/channels', {
        method: 'GET',
        workspaceScoped: true,
      })
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

  describe('refreshWorkspaceData', () => {
    it('reloads the channel list and typed provider catalog in parallel', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockImplementation(async (path: string) => {
        if (path === '/api/publishing/channels') return { channels: [] }
        return { providers: [availableLinkedInProvider] }
      })

      await store.refreshWorkspaceData()

      expect(apiFetch).toHaveBeenCalledWith('/api/publishing/channels', {
        method: 'GET',
        workspaceScoped: true,
      })
      expect(apiFetch).toHaveBeenCalledWith('/api/publishing/channels/providers', {
        method: 'GET',
        workspaceScoped: true,
      })
      expect(store.providerCatalog).toEqual([availableLinkedInProvider])
      expect(store.configuredProviders).toEqual(['linkedin'])
      expect(store.isLinkedInConfigured).toBe(true)
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

    it('clears provider catalog entries when their reload fails so they cannot remain actionable', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.providerCatalog = [availableLinkedInProvider]
      vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('provider fetch failed'))

      await expect(store.fetchProviderCatalog()).rejects.toThrow('provider fetch failed')

      expect(store.providerCatalog).toEqual([])
      expect(store.providersLoading).toBe(false)
    })

    it('does not let an older workspace response restore stale catalog entries', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      let resolveOlder: ((value: { providers: ProviderCatalogItem[] }) => void) | undefined
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockImplementation(async () => {
        if (!resolveOlder) {
          return new Promise<{ providers: ProviderCatalogItem[] }>((resolve) => {
            resolveOlder = resolve
          })
        }
        return { providers: [] }
      })

      const older = store.fetchProviderCatalog()
      const newer = store.fetchProviderCatalog()
      await newer
      resolveOlder?.({ providers: [availableLinkedInProvider] })
      await older

      expect(apiFetch).toHaveBeenCalledTimes(2)
      expect(store.providerCatalog).toEqual([])
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

  describe('subscribePublicationEvents', () => {
    function sseStream(frames: string[]): ReadableStream<Uint8Array> {
      return new ReadableStream<Uint8Array>({
        start(controller) {
          const encoder = new TextEncoder()
          for (const frame of frames) controller.enqueue(encoder.encode(frame))
          controller.close()
        },
      })
    }

    it('preserves replacement connection when an old request completes', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      useWorkspaceStore().setActiveWorkspaceId('a219727d-019f-544c-95f9-ac30f00cc8b2')
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      let finishOld = (_response: Response) => {}
      const apiFetchRaw = vi.spyOn(auth, 'apiFetchRaw').mockImplementationOnce(
        () =>
          new Promise<Response>((resolve) => {
            finishOld = resolve
          }),
      )
      const oldSubscription = store.subscribePublicationEvents()
      let closeNew = () => {}
      apiFetchRaw.mockResolvedValueOnce(
        new Response(
          new ReadableStream({
            start(controller) {
              closeNew = () => controller.close()
            },
          }),
        ),
      )
      const newSubscription = store.subscribePublicationEvents()
      await Promise.resolve()
      expect(store.publicationEventsConnected).toBe(true)
      finishOld(new Response(sseStream([])))
      await oldSubscription
      expect(store.publicationEventsConnected).toBe(true)
      closeNew()
      await newSubscription
    })

    it('ignores stale stream frames and cleanup after replacing a connected stream', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      useWorkspaceStore().setActiveWorkspaceId('a219727d-019f-544c-95f9-ac30f00cc8b2')
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      let finishOld = () => {}
      const onInvalidation = vi.fn()
      const apiFetchRaw = vi.spyOn(auth, 'apiFetchRaw').mockResolvedValueOnce(
        new Response(
          new ReadableStream<Uint8Array>({
            start(controller) {
              finishOld = () => {
                controller.enqueue(
                  new TextEncoder().encode(
                    'data: {"workspaceId":"a219727d-019f-544c-95f9-ac30f00cc8b2","publicationId":"48c5787c-4ddb-5cd5-b205-cd0f99e7fc07","changeType":"publication.status-changed","occurredAt":"2026-09-25T12:00:00Z"}\n\n',
                  ),
                )
                controller.close()
              }
            },
          }),
        ),
      )
      const oldSubscription = store.subscribePublicationEvents(onInvalidation)
      await Promise.resolve()
      let closeNew = () => {}
      apiFetchRaw.mockResolvedValueOnce(
        new Response(
          new ReadableStream({
            start(controller) {
              closeNew = () => controller.close()
            },
          }),
        ),
      )
      const newSubscription = store.subscribePublicationEvents()
      await Promise.resolve()
      const newController = store.publicationEventsAbortController
      finishOld()
      await oldSubscription
      expect(onInvalidation).not.toHaveBeenCalled()
      expect(store.publicationEventsConnected).toBe(true)
      expect(store.publicationEventsAbortController).toBe(newController)
      closeNew()
      await newSubscription
    })

    it.each([404, 405])('returns unavailable for HTTP %s while preserving REST', async (status) => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      useWorkspaceStore().setActiveWorkspaceId('a219727d-019f-544c-95f9-ac30f00cc8b2')
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetchRaw').mockRejectedValueOnce({ status })
      vi.spyOn(console, 'warn').mockImplementation(() => {})
      expect(await store.subscribePublicationEvents()).toBe('unavailable')
      expect(store.publicationEventsConnected).toBe(false)
      const apiFetch = vi
        .spyOn(auth, 'apiFetch')
        .mockResolvedValueOnce({ publications: [], activity: [], conflicts: [] })
      await store.fetchCalendar('2026-10-01', '2026-11-01')
      expect(apiFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/publishing/publications/calendar?'),
        { workspaceScoped: true },
      )
      expect(store.publications).toEqual([])
    })

    it('opens the publications events stream and forwards matching workspace invalidations', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      const workspace = useWorkspaceStore()
      workspace.setActiveWorkspaceId('a219727d-019f-544c-95f9-ac30f00cc8b2')
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      const apiFetchRaw = vi
        .spyOn(auth, 'apiFetchRaw')
        .mockResolvedValue(
          new Response(
            sseStream([
              'event: publication.status-changed\ndata: {"workspaceId":"a219727d-019f-544c-95f9-ac30f00cc8b2","publicationId":"48c5787c-4ddb-5cd5-b205-cd0f99e7fc07","socialAccountId":"788c8103-35f1-5899-b15a-6ca994253c99","changeType":"publication.status-changed","occurredAt":"2026-09-25T12:00:00.000Z"}\n\n',
            ]),
          ),
        )
      const onInvalidation = vi.fn()

      await store.subscribePublicationEvents(onInvalidation)

      expect(apiFetchRaw).toHaveBeenCalledWith('/api/publishing/publications/events', {
        method: 'GET',
        headers: { Accept: 'text/event-stream' },
        workspaceScoped: true,
        signal: expect.any(AbortSignal),
      })
      expect(onInvalidation).toHaveBeenCalledOnce()
      expect(onInvalidation).toHaveBeenCalledWith({
        workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
        publicationId: '48c5787c-4ddb-5cd5-b205-cd0f99e7fc07',
        socialAccountId: '788c8103-35f1-5899-b15a-6ca994253c99',
        changeType: 'publication.status-changed',
        occurredAt: '2026-09-25T12:00:00.000Z',
      })
      expect(store.publicationEventsConnected).toBe(false)
    })

    it('ignores foreign workspace frames, heartbeats, and malformed payloads', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      const workspace = useWorkspaceStore()
      workspace.setActiveWorkspaceId('a219727d-019f-544c-95f9-ac30f00cc8b2')
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetchRaw').mockResolvedValue(
        new Response(
          sseStream([
            'event: publication.updated\ndata: {"workspaceId":"c1d6493d-5117-5741-a405-50309e8bfc77","publicationId":"5907d0e4-0390-51bb-8711-85387d7562f0","changeType":"publication.updated","occurredAt":"2026-09-25T12:00:00.000Z"}\n\n',
            'event: heartbeat\ndata: {}\n\n',
            'event: publication.updated\ndata: {"workspaceId":"a219727d-019f-544c-95f9-ac30f00cc8b2","changeType":"bogus"}\n\n',
          ]),
        ),
      )
      const onInvalidation = vi.fn()

      await store.subscribePublicationEvents(onInvalidation)

      expect(onInvalidation).not.toHaveBeenCalled()
    })

    it('returns null without connecting when unauthenticated', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })
      const apiFetchRaw = vi.spyOn(auth, 'apiFetchRaw')

      const result = await store.subscribePublicationEvents(vi.fn())

      expect(result).toBeNull()
      expect(apiFetchRaw).not.toHaveBeenCalled()
      expect(store.publicationEventsConnected).toBe(false)
    })

    it('aborts the active publication event stream on unsubscribe', () => {
      const store = usePublishingStore()
      const abortController = new AbortController()
      const abort = vi.spyOn(abortController, 'abort')
      store.publicationEventsAbortController = abortController
      store.publicationEventsConnected = true

      store.unsubscribePublicationEvents()

      expect(abort).toHaveBeenCalledOnce()
      expect(store.publicationEventsAbortController).toBeNull()
      expect(store.publicationEventsConnected).toBe(false)
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
          id: '967ffe2a-bc25-520e-9b99-5ff1de10474f',
          accountId: '967ffe2a-bc25-520e-9b99-5ff1de10474f',
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
          id: 'a64c6bd6-8a2e-55c8-9f98-fa7b7105b0ae',
          accountId: 'a64c6bd6-8a2e-55c8-9f98-fa7b7105b0ae',
          name: 'Inactive 1',
          provider: 'linkedin',
          avatar: '',
          handle: 'Inactive 1',
          status: 'EXPIRED',
        },
        {
          id: '57b084d6-4aed-51ce-a930-2485e7475a37',
          accountId: '57b084d6-4aed-51ce-a930-2485e7475a37',
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
          id: '07e93669-ed44-5dbc-8517-fe337a8328be',
          accountId: '07e93669-ed44-5dbc-8517-fe337a8328be',
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
          id: '0c783f9a-304d-5a13-9c37-3ad293aa8116',
          accountId: '0c783f9a-304d-5a13-9c37-3ad293aa8116',
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
          id: '63c71adf-16fb-5a1f-b897-17d9a9d83ce0',
          accountId: '63c71adf-16fb-5a1f-b897-17d9a9d83ce0',
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

  describe('scheduleMultiChannel', () => {
    it('maps calendar group ids onto publications', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publications: [
          {
            id: '05ee3d72-bb3f-5c06-9522-546b7d41d294',
            workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
            socialAccountId: 'b0837209-7042-5a29-bddf-5020da6580db',
            provider: 'LINKEDIN',
            status: 'QUEUED',
            scheduleMode: 'SCHEDULED_AT',
            priority: false,
            title: null,
            bodyText: 'Calendar publication',
            assetIds: [],
            scheduledFor: '2026-06-20T14:00:00Z',
            hasConflict: false,
            conflictingPublicationIds: [],
            groupId: '01348fd0-7362-5e46-9926-6fb0022954d0',
          },
        ],
        conflicts: [],
        activity: [],
      })

      await store.fetchCalendar('2026-06-20', '2026-06-20')

      expect(
        store.publications.find((item) => item.id === '05ee3d72-bb3f-5c06-9522-546b7d41d294')
          ?.groupId,
      ).toBe('01348fd0-7362-5e46-9926-6fb0022954d0')
    })

    it('sends one batch request with account-specific content and inserts successful publications', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [
        makeChannelForStore('b0837209-7042-5a29-bddf-5020da6580db'),
        makeChannelForStore('792ec8df-cf55-5d48-8077-39eb3c7bb67c', { provider: 'threads' }),
      ]
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        status: 'ALL_CREATED',
        results: [
          {
            socialAccountId: 'b0837209-7042-5a29-bddf-5020da6580db',
            publicationId: 'fc18a1ef-cca8-5cf8-a32a-27629468a359',
            groupId: 'd7040dcb-a574-549e-b1ac-b45c786d5e10',
            status: 'CREATED',
          },
          {
            socialAccountId: '792ec8df-cf55-5d48-8077-39eb3c7bb67c',
            publicationId: '261ef19a-4b40-52ca-8850-86ae31fdc4c3',
            groupId: 'd7040dcb-a574-549e-b1ac-b45c786d5e10',
            status: 'CREATED',
          },
        ],
      })

      const result = await store.scheduleMultiChannel({
        title: 'Post from App',
        scheduleMode: 'NOW',
        priority: false,
        assetIds: ['8bdec504-2ed1-59e4-8a4a-c068789d03d2'],
        targets: [
          {
            socialAccountId: 'b0837209-7042-5a29-bddf-5020da6580db',
            provider: 'linkedin',
            content: 'LinkedIn copy',
          },
          {
            socialAccountId: '792ec8df-cf55-5d48-8077-39eb3c7bb67c',
            provider: 'threads',
            content: 'Threads copy',
          },
        ],
      })

      expect(apiFetch).toHaveBeenCalledTimes(1)
      expect(apiFetch).toHaveBeenCalledWith(
        '/api/publishing/publications/batch',
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('LinkedIn copy'),
        }),
      )
      expect(result.results).toHaveLength(2)
      expect(
        store.publications.filter((item) =>
          ['fc18a1ef-cca8-5cf8-a32a-27629468a359', '261ef19a-4b40-52ca-8850-86ae31fdc4c3'].includes(
            item.id,
          ),
        ),
      ).toHaveLength(2)
      expect(
        store.publications
          .filter((item) =>
            [
              'fc18a1ef-cca8-5cf8-a32a-27629468a359',
              '261ef19a-4b40-52ca-8850-86ae31fdc4c3',
            ].includes(item.id),
          )
          .map((item) => item.groupId),
      ).toEqual(['d7040dcb-a574-549e-b1ac-b45c786d5e10', 'd7040dcb-a574-549e-b1ac-b45c786d5e10'])
    })

    it('preserves per-target failures when every target fails with the endpoint 400 response', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [makeChannelForStore('b0837209-7042-5a29-bddf-5020da6580db')]
      vi.spyOn(auth, 'apiFetch').mockRejectedValue(
        Object.assign(new Error('Batch failed'), {
          status: 400,
          results: [
            {
              socialAccountId: 'b0837209-7042-5a29-bddf-5020da6580db',
              status: 'FAILED',
              errorCode: 'INVALID_CONTENT',
              errorMessage: 'Rejected',
            },
          ],
        }),
      )

      const result = await store.scheduleMultiChannel({
        scheduleMode: 'NOW',
        priority: false,
        targets: [
          {
            socialAccountId: 'b0837209-7042-5a29-bddf-5020da6580db',
            provider: 'linkedin',
            content: 'Rejected content',
          },
        ],
      })

      expect(result).toEqual({
        status: 'ALL_FAILED',
        results: [
          {
            socialAccountId: 'b0837209-7042-5a29-bddf-5020da6580db',
            status: 'FAILED',
            errorCode: 'INVALID_CONTENT',
            errorMessage: 'Rejected',
          },
        ],
      })
      expect(
        store.publications.some(
          (item) => item.accountId === 'b0837209-7042-5a29-bddf-5020da6580db',
        ),
      ).toBe(false)
    })

    it('inserts successful targets and preserves per-target failures on partial response', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [
        makeChannelForStore('b0837209-7042-5a29-bddf-5020da6580db'),
        makeChannelForStore('792ec8df-cf55-5d48-8077-39eb3c7bb67c', { provider: 'threads' }),
      ]
      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        status: 'PARTIAL',
        results: [
          {
            socialAccountId: 'b0837209-7042-5a29-bddf-5020da6580db',
            publicationId: 'fc18a1ef-cca8-5cf8-a32a-27629468a359',
            status: 'CREATED',
          },
          {
            socialAccountId: '792ec8df-cf55-5d48-8077-39eb3c7bb67c',
            status: 'BAD_REQUEST',
            errorCode: 'CAPABILITY_VIOLATION',
          },
        ],
      })

      const result = await store.scheduleMultiChannel({
        scheduleMode: 'NOW',
        priority: false,
        targets: [
          {
            socialAccountId: 'b0837209-7042-5a29-bddf-5020da6580db',
            provider: 'linkedin',
            content: 'LinkedIn copy',
          },
          {
            socialAccountId: '792ec8df-cf55-5d48-8077-39eb3c7bb67c',
            provider: 'threads',
            content: 'Threads copy',
          },
        ],
      })

      expect(result.status).toBe('PARTIAL')
      expect(result.results[1]?.errorCode).toBe('CAPABILITY_VIOLATION')
      expect(
        store.publications.some((item) => item.id === 'fc18a1ef-cca8-5cf8-a32a-27629468a359'),
      ).toBe(true)
      expect(
        store.publications.some(
          (item) => item.accountId === '792ec8df-cf55-5d48-8077-39eb3c7bb67c',
        ),
      ).toBe(false)
    })
  })

  describe('schedulePost', () => {
    it('preserves assetIds in the unauthenticated fallback', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })

      const result = await store.schedulePost({
        content: 'Local post with media',
        channels: ['linkedin'],
        scheduleMode: 'NOW',
        priority: false,
        assetIds: ['7bdd618f-709c-51db-bf4b-a83cff7d3917'],
      })

      expect(result.assetIds).toEqual(['7bdd618f-709c-51db-bf4b-a83cff7d3917'])
      expect(store.publications[0]?.assetIds).toEqual(['7bdd618f-709c-51db-bf4b-a83cff7d3917'])
    })

    it.each([
      ['NOW', '2026-06-20T14:30:00Z', null],
      ['NEXT_SLOT', null, '2026-06-20T15:00:00Z'],
      ['SCHEDULED_AT', '2026-06-20T16:00:00Z', null],
    ] as const)(
      'adopts authenticated %s create identity and normalized server fields',
      async (scheduleMode, scheduledFor, nextSlotAfter) => {
        const store = usePublishingStore()
        const auth = useAuthStore()
        Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
        store.channels = [makeChannelForStore('f2f58fae-2804-5c15-bacd-feb0c8b94592')]
        vi.spyOn(auth, 'apiFetch').mockResolvedValue({
          publicationId: `backend-${scheduleMode}`,
          workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
          socialAccountId: 'f2f58fae-2804-5c15-bacd-feb0c8b94592',
          status: scheduleMode === 'SCHEDULED_AT' ? 'SCHEDULED' : 'QUEUED',
          scheduleMode,
          priority: true,
          title: 'Server title',
          bodyText: 'Server body',
          assetIds: ['af778fd0-cff9-523f-87ee-2f4ef06f082a'],
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
          socialAccountId: 'f2f58fae-2804-5c15-bacd-feb0c8b94592',
        })

        expect(result).toMatchObject({
          id: `backend-${scheduleMode}`,
          content: 'Server body',
          accountId: 'f2f58fae-2804-5c15-bacd-feb0c8b94592',
          status: scheduleMode === 'SCHEDULED_AT' ? 'SCHEDULED' : 'QUEUED',
          scheduleMode,
          scheduledAt: scheduledFor ?? nextSlotAfter ?? '',
          assetIds: ['af778fd0-cff9-523f-87ee-2f4ef06f082a'],
          priority: true,
        })
        expect(store.publications[0]?.id).toBe(`backend-${scheduleMode}`)
      },
    )

    it('uses the authoritative NOW scheduledFor as a valid display timestamp', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [makeChannelForStore('46470503-e51f-5663-9254-ee26fb4cec70')]
      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: '8a25f709-40f6-4ab0-b5ae-f79bdcf4d395',
        workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
        socialAccountId: '46470503-e51f-5663-9254-ee26fb4cec70',
        status: 'QUEUED',
        scheduleMode: 'NOW',
        priority: false,
        title: 'Post from App',
        bodyText: 'Real NOW response',
        assetIds: ['cdb1a004-9ef3-5f17-a8fa-e83d0177d4ee'],
        scheduledFor: '2026-07-02T15:25:38.050321Z',
        nextSlotAfter: null,
      })

      const result = await store.schedulePost({
        content: 'Real NOW response',
        channels: ['linkedin'],
        scheduleMode: 'NOW',
        priority: false,
        socialAccountId: '46470503-e51f-5663-9254-ee26fb4cec70',
      })

      expect(result).toMatchObject({
        id: '8a25f709-40f6-4ab0-b5ae-f79bdcf4d395',
        scheduleMode: 'NOW',
        scheduledAt: '2026-07-02T15:25:38.050321Z',
        assetIds: ['cdb1a004-9ef3-5f17-a8fa-e83d0177d4ee'],
      })
      expect(Number.isNaN(new Date(result.scheduledAt).getTime())).toBe(false)
    })

    it('does not insert a placeholder when authenticated create fails', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [makeChannelForStore('f2f58fae-2804-5c15-bacd-feb0c8b94592')]
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
          id: '967ffe2a-bc25-520e-9b99-5ff1de10474f',
          accountId: '967ffe2a-bc25-520e-9b99-5ff1de10474f',
          name: 'Real Profile 1',
          provider: 'linkedin',
          avatar: '',
          handle: 'Real Profile 1',
          status: 'ACTIVE',
        },
        {
          id: '2def2d73-5e00-5cc3-8433-90151129e799',
          accountId: '2def2d73-5e00-5cc3-8433-90151129e799',
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
        socialAccountId: '2def2d73-5e00-5cc3-8433-90151129e799',
      })

      const body = JSON.parse(apiFetch.mock.calls[0]?.[1]?.body as string)
      expect(body.socialAccountId).toBe('2def2d73-5e00-5cc3-8433-90151129e799')
      expect(body.socialAccountId).not.toBe('account-linkedin-mock')
      expect(apiFetch.mock.calls[0]?.[1]).toMatchObject({ workspaceScoped: true })
    })

    it('falls back to the first active LinkedIn account when socialAccountId is omitted', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [
        {
          id: '6b422edb-a477-5029-a5d7-8dab871e08e9',
          accountId: '6b422edb-a477-5029-a5d7-8dab871e08e9',
          name: 'Fallback Profile',
          provider: 'linkedin',
          avatar: '',
          handle: 'Fallback Profile',
          status: 'ACTIVE',
        },
        {
          id: '55309ec2-3276-542e-bbe4-cd6e82daf38d',
          accountId: '55309ec2-3276-542e-bbe4-cd6e82daf38d',
          name: 'Other Profile',
          provider: 'linkedin',
          avatar: '',
          handle: 'Other Profile',
          status: 'ACTIVE',
        },
      ]
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: 'efbe853b-156c-561d-ac5a-8b5bbae18cb0',
        workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
        socialAccountId: '6b422edb-a477-5029-a5d7-8dab871e08e9',
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
      expect(body.socialAccountId).toBe('6b422edb-a477-5029-a5d7-8dab871e08e9')
      expect(result.accountId).toBe('6b422edb-a477-5029-a5d7-8dab871e08e9')
    })

    it('uses the selected connected Threads account id for authenticated scheduling', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [
        {
          id: 'a4460b80-fdad-5d78-9508-79dafefe6bee',
          accountId: 'a4460b80-fdad-5d78-9508-79dafefe6bee',
          name: 'LinkedIn Profile',
          provider: 'linkedin',
          avatar: '',
          handle: 'LinkedIn Profile',
          status: 'ACTIVE',
        },
        {
          id: '1a1c7887-6761-541b-8256-911451482b17',
          accountId: '1a1c7887-6761-541b-8256-911451482b17',
          name: 'Threads Profile',
          provider: 'threads',
          avatar: '',
          handle: 'Threads Profile',
          status: 'ACTIVE',
        },
      ]
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: 'f7d251cb-5faa-59e9-a0c0-8d47b6b28618',
        workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
        socialAccountId: '1a1c7887-6761-541b-8256-911451482b17',
        status: 'QUEUED',
        scheduleMode: 'NOW',
        priority: false,
        title: 'Title',
        bodyText: 'Threads post content',
        assetIds: [],
        scheduledFor: null,
        nextSlotAfter: null,
      })

      const result = await store.schedulePost({
        content: 'Threads post content',
        title: 'Title',
        channels: ['threads'],
        priority: false,
        socialAccountId: '1a1c7887-6761-541b-8256-911451482b17',
      })

      const body = JSON.parse(apiFetch.mock.calls[0]?.[1]?.body as string)
      expect(body.socialAccountId).toBe('1a1c7887-6761-541b-8256-911451482b17')
      expect(result.accountId).toBe('1a1c7887-6761-541b-8256-911451482b17')
    })

    it('throws for authenticated scheduling when the selected provider channel is not connected', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.channels = [
        {
          id: 'a4460b80-fdad-5d78-9508-79dafefe6bee',
          accountId: 'a4460b80-fdad-5d78-9508-79dafefe6bee',
          name: 'LinkedIn Profile',
          provider: 'linkedin',
          avatar: '',
          handle: 'LinkedIn Profile',
          status: 'ACTIVE',
        },
      ]
      const apiFetch = vi.spyOn(auth, 'apiFetch')

      await expect(
        store.schedulePost({
          content: 'Threads post content',
          title: 'Title',
          channels: ['threads'],
          priority: false,
          socialAccountId: '1a1c7887-6761-541b-8256-911451482b17',
        }),
      ).rejects.toThrow('Connect a social profile before scheduling authenticated posts.')
      expect(apiFetch).not.toHaveBeenCalled()
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
      ).rejects.toThrow('Connect a social profile before scheduling authenticated posts.')
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
        assetIds: ['offline-asset'],
      })

      expect(apiFetch).not.toHaveBeenCalled()
      expect(store.publications[0]?.content).toBe('Offline post')
      expect(result.content).toBe('Offline post')
      expect(result.assetIds).toEqual(['offline-asset'])
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
      expect(store.publications[0]?.conflictingPublicationIds).toEqual([
        '56536a72-fc15-50ce-8787-c1ffbfcefdf3',
      ])

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
            id: '438b5dc0-db64-5675-a885-aebc17c296c6',
            workspaceId: '9f6f84fe-2465-5955-ae9f-801107241c33',
            socialAccountId: '8ec9e4ac-9ec5-5c3a-8933-7af5407a797c',
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
            id: '74ea302d-b8c6-532e-bd64-83934685aa51',
            workspaceId: '9f6f84fe-2465-5955-ae9f-801107241c33',
            socialAccountId: '8ec9e4ac-9ec5-5c3a-8933-7af5407a797c',
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
            id: '04c496ee-833f-548d-8f57-6fdd510fe5ca',
            workspaceId: '9f6f84fe-2465-5955-ae9f-801107241c33',
            socialAccountId: '8ec9e4ac-9ec5-5c3a-8933-7af5407a797c',
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
        id: '74ea302d-b8c6-532e-bd64-83934685aa51',
        status: 'BLOCKED',
        blockedReason: 'LinkedIn account requires reconnect',
      })
      expect(store.publications[1]).toMatchObject({
        id: '04c496ee-833f-548d-8f57-6fdd510fe5ca',
        status: 'FAILED',
        errorCode: 'LINKEDIN_VALIDATION_ERROR',
      })
    })

    it('preserves failed and blocked diagnostics as opaque values without creating visible copy', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publications: [
          {
            id: 'ed000f79-ab3e-5c37-a483-4bb6b71b75af',
            workspaceId: '9f6f84fe-2465-5955-ae9f-801107241c33',
            socialAccountId: '8ec9e4ac-9ec5-5c3a-8933-7af5407a797c',
            provider: 'linkedin',
            status: 'BLOCKED',
            scheduleMode: 'SCHEDULED_AT',
            priority: false,
            title: 'Blocked raw',
            bodyText: 'Publishing paused.',
            assetIds: [],
            scheduledFor: '2026-06-10T12:00:00Z',
            hasConflict: false,
            conflictingPublicationIds: [],
            blockedReason: 'com.example.ReconnectRequiredException token=secret',
          },
          {
            id: '3fe2488b-1366-56f2-ad36-af6aa20578a8',
            workspaceId: '9f6f84fe-2465-5955-ae9f-801107241c33',
            socialAccountId: '8ec9e4ac-9ec5-5c3a-8933-7af5407a797c',
            provider: 'linkedin',
            status: 'FAILED',
            scheduleMode: 'SCHEDULED_AT',
            priority: false,
            title: 'Failed raw',
            bodyText: 'Provider rejected the payload.',
            assetIds: [],
            scheduledFor: '2026-06-10T13:00:00Z',
            hasConflict: false,
            conflictingPublicationIds: [],
            errorCode: 'StorageObjectNotFoundException',
          },
        ],
        conflicts: [],
        activity: [],
      })

      await store.fetchCalendar('2026-06-01T00:00:00Z', '2026-07-01T00:00:00Z')

      expect(store.publications[0]?.blockedReason).toBe(
        'com.example.ReconnectRequiredException token=secret',
      )
      expect(store.publications[1]?.errorCode).toBe('StorageObjectNotFoundException')
      expect(JSON.stringify(store.publications)).not.toContain('postDetail.failure')
    })

    it('retries a failed publication through the retry endpoint and updates local state', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.publications = [
        {
          id: '8d57c44a-0cc3-5837-9d3d-219ca18ed5d0',
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
        publicationId: '8d57c44a-0cc3-5837-9d3d-219ca18ed5d0',
        workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
        socialAccountId: '223bd3b9-4c0a-57cd-826f-681e172f6d7d',
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

      const result = await store.retryPublication('8d57c44a-0cc3-5837-9d3d-219ca18ed5d0')

      expect(apiFetch).toHaveBeenCalledWith(
        '/api/publishing/publications/8d57c44a-0cc3-5837-9d3d-219ca18ed5d0/retry',
        {
          method: 'POST',
          body: JSON.stringify({
            scheduleMode: 'NOW',
            priority: false,
          }),
          workspaceScoped: true,
        },
      )
      expect(result.status).toBe('QUEUED')
      expect(result.errorCode).toBeUndefined()
      expect(store.publications[0]?.status).toBe('QUEUED')
    })

    it('retryPublication falls back to QUEUED locally when unauthenticated', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })
      const apiFetch = vi.spyOn(auth, 'apiFetch')

      store.publications = [
        {
          id: '8d1eec5e-e1a3-5555-b77f-fa2700ef41cc',
          content: 'Offline retry test',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'FAILED',
          priority: false,
          errorCode: 'LINKEDIN_VALIDATION_ERROR',
        },
      ]

      try {
        const result = await store.retryPublication('8d1eec5e-e1a3-5555-b77f-fa2700ef41cc')

        // No API call should be made when unauthenticated
        expect(apiFetch).not.toHaveBeenCalled()
        expect(result.status).toBe('QUEUED')
        expect(result.errorCode).toBeUndefined()
        expect(store.publications[0]?.status).toBe('QUEUED')
      } finally {
        apiFetch.mockRestore()
      }
    })

    it('falls back to local data on API error', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('Network error'))

      // Set initial local publications
      store.publications = [
        {
          id: '494209e5-e5d9-5eee-b2fd-4848e29511de',
          content: 'Local post',
          channels: ['linkedin'],
          scheduledAt: '2026-06-10T12:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      await store.fetchCalendar('2026-06-01T00:00:00Z', '2026-07-01T00:00:00Z')

      // Should still have the local publication
      expect(store.publications.some((p) => p.id === '494209e5-e5d9-5eee-b2fd-4848e29511de')).toBe(
        true,
      )
    })

    it('applies local filters in fallback mode and clears api-only state', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })
      store.publications = [
        {
          id: 'f8023dcb-93ef-5736-ba0b-febc6bc0853d',
          content: 'Architecture notes',
          channels: ['linkedin'],
          accountId: '788c8103-35f1-5899-b15a-6ca994253c99',
          scheduledAt: '2026-06-10T12:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
        {
          id: '41e6b357-faa8-50c0-b061-ff9eaed5144f',
          content: 'Architecture shipped',
          channels: ['linkedin'],
          accountId: '788c8103-35f1-5899-b15a-6ca994253c99',
          scheduledAt: '2026-06-11T12:00:00Z',
          status: 'PUBLISHED',
          priority: false,
        },
        {
          id: 'dd61f582-9041-5094-8c8b-2ac7edbde956',
          content: 'Visual post',
          channels: ['instagram'],
          accountId: '28353a84-fcee-5f3a-93b6-4c1bd639921e',
          scheduledAt: '2026-06-12T12:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]
      store.activity = [{ date: '2026-06-10', density: 'HIGH', count: 3 }]
      store.conflicts = [
        {
          publicationId: 'f8023dcb-93ef-5736-ba0b-febc6bc0853d',
          conflictingPublicationIds: ['41e6b357-faa8-50c0-b061-ff9eaed5144f'],
          reason: 'OVERLAPPING_SCHEDULE',
        },
      ]
      store.filterChannel = 'linkedin'
      store.filterSocialAccountId = '788c8103-35f1-5899-b15a-6ca994253c99'
      store.filterTag = 'arch'
      store.filterPostType = 'queued'

      await store.fetchCalendar('2026-06-01T00:00:00Z', '2026-07-01T00:00:00Z')

      expect(store.publications.map((pub) => pub.id)).toEqual([
        'f8023dcb-93ef-5736-ba0b-febc6bc0853d',
      ])
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
            id: 'd3a963cc-0fe6-5e8c-9a59-e4827ecf9164',
            workspaceId: '9f6f84fe-2465-5955-ae9f-801107241c33',
            socialAccountId: '788c8103-35f1-5899-b15a-6ca994253c99',
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
            id: 'b8ad09d5-33f5-5c20-88bf-a10a6dd8f9ea',
            workspaceId: '9f6f84fe-2465-5955-ae9f-801107241c33',
            socialAccountId: '788c8103-35f1-5899-b15a-6ca994253c99',
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
        conflicts: [{ id: '2512c06c-161a-59fb-9d20-c420584ae194' }],
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
      expect(store.publications.map((p) => p.id)).toEqual(['d3a963cc-0fe6-5e8c-9a59-e4827ecf9164'])
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
        socialAccountId: '8ec9e4ac-9ec5-5c3a-8933-7af5407a797c',
        title: 'Quick',
        bodyText: 'Quick post content',
        scheduledFor: '2026-06-20T14:00:00Z',
      })

      expect(result.id).toMatch(
        /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
      )
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
        publicationId: '21dacb75-39e2-5365-b8b2-0e4d95d137b6',
        workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
        socialAccountId: 'cbc4a596-9ae8-5479-b6da-8bd52442012d',
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
        socialAccountId: 'cbc4a596-9ae8-5479-b6da-8bd52442012d',
        title: 'Quick Auth',
        bodyText: 'Authenticated quick create',
        scheduledFor: '2026-06-20T14:00:00Z',
        priority: true,
      })

      expect(apiFetch).toHaveBeenCalledWith('/api/publishing/publications/quick-create', {
        method: 'POST',
        body: JSON.stringify({
          socialAccountId: 'cbc4a596-9ae8-5479-b6da-8bd52442012d',
          title: 'Quick Auth',
          bodyText: 'Authenticated quick create',
          scheduledFor: '2026-06-20T14:00:00Z',
          priority: true,
        }),
        workspaceScoped: true,
      })
      expect(result).toMatchObject({
        id: '21dacb75-39e2-5365-b8b2-0e4d95d137b6',
        content: 'Authenticated quick create normalized',
        accountId: 'cbc4a596-9ae8-5479-b6da-8bd52442012d',
        status: 'SCHEDULED',
        scheduleMode: 'SCHEDULED_AT',
        scheduledAt: '2026-06-20T14:05:00Z',
        assetIds: ['quick-asset'],
      })
      expect(store.publications[0]?.id).toBe('21dacb75-39e2-5365-b8b2-0e4d95d137b6')
    })

    it('propagates authenticated quick-create failure without local insertion', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      const before = store.publications.map((publication) => publication.id)
      vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('quick create failed'))

      await expect(
        store.quickCreatePost({
          socialAccountId: 'cbc4a596-9ae8-5479-b6da-8bd52442012d',
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
          id: '48c5787c-4ddb-5cd5-b205-cd0f99e7fc07',
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
      await expect(
        store.reschedulePublication('48c5787c-4ddb-5cd5-b205-cd0f99e7fc07', '2026-06-16T10:00:00Z'),
      ).rejects.toThrow()

      // State should be rolled back
      expect(store.publications[0]?.scheduledAt).toBe(originalDate)
    })

    it('keeps optimistic update on success', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()

      store.publications = [
        {
          id: '48c5787c-4ddb-5cd5-b205-cd0f99e7fc07',
          content: 'Test',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      vi.spyOn(auth, 'apiFetch').mockResolvedValue({})

      await store.reschedulePublication(
        '48c5787c-4ddb-5cd5-b205-cd0f99e7fc07',
        '2026-06-16T10:00:00Z',
      )

      expect(store.publications[0]?.scheduledAt).toBe('2026-06-16T10:00:00Z')
    })

    it('saves locally when unauthenticated', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })
      store.publications = [
        {
          id: 'dd2a566b-c458-5fbf-8f9d-7d4d6841a2e6',
          content: 'Test',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      const result = await store.reschedulePublication(
        'dd2a566b-c458-5fbf-8f9d-7d4d6841a2e6',
        '2026-06-16T10:00:00Z',
      )

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
          id: '590dd085-25e2-553e-9b7d-6618a6488c91',
          content: 'Original body',
          title: 'Original title',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          scheduleMode: 'SCHEDULED_AT',
          status: 'QUEUED',
          priority: false,
          accountId: '223bd3b9-4c0a-57cd-826f-681e172f6d7d',
          assetIds: [
            'f464d618-0f38-5b38-adda-9cf4a0b674eb',
            '13001fc9-a24f-54d3-bde1-cfa79074593c',
          ],
        },
      ]
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: '590dd085-25e2-553e-9b7d-6618a6488c91',
        workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
        socialAccountId: '223bd3b9-4c0a-57cd-826f-681e172f6d7d',
        status: 'SCHEDULED',
        scheduleMode: 'SCHEDULED_AT',
        priority: false,
        title: 'Original title',
        bodyText: 'Changed body',
        assetIds: ['f464d618-0f38-5b38-adda-9cf4a0b674eb', '13001fc9-a24f-54d3-bde1-cfa79074593c'],
        scheduledFor: '2026-06-15T20:00:00Z',
        nextSlotAfter: null,
      })

      await store.updatePost('590dd085-25e2-553e-9b7d-6618a6488c91', { content: 'Changed body' })

      const body = JSON.parse(apiFetch.mock.calls[0]?.[1]?.body as string)
      expect(body).not.toHaveProperty('assetIds')
    })

    it('serializes empty and replacement assetIds in PATCH when provided', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.publications = [
        {
          id: 'faed9dc9-1fab-5189-8be3-a824de11ffe3',
          content: 'Original body',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
          accountId: '223bd3b9-4c0a-57cd-826f-681e172f6d7d',
          assetIds: [
            'f464d618-0f38-5b38-adda-9cf4a0b674eb',
            '13001fc9-a24f-54d3-bde1-cfa79074593c',
          ],
        },
      ]
      const apiFetch = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: 'faed9dc9-1fab-5189-8be3-a824de11ffe3',
        workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
        socialAccountId: '223bd3b9-4c0a-57cd-826f-681e172f6d7d',
        status: 'SCHEDULED',
        scheduleMode: 'SCHEDULED_AT',
        priority: false,
        title: null,
        bodyText: 'Original body',
        assetIds: [],
        scheduledFor: '2026-06-15T20:00:00Z',
        nextSlotAfter: null,
      })

      await store.updatePost('faed9dc9-1fab-5189-8be3-a824de11ffe3', { assetIds: [] })
      let body = JSON.parse(apiFetch.mock.calls[0]?.[1]?.body as string)
      expect(body.assetIds).toEqual([])

      apiFetch.mockResolvedValueOnce({
        publicationId: 'faed9dc9-1fab-5189-8be3-a824de11ffe3',
        workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
        socialAccountId: '223bd3b9-4c0a-57cd-826f-681e172f6d7d',
        status: 'SCHEDULED',
        scheduleMode: 'SCHEDULED_AT',
        priority: false,
        title: null,
        bodyText: 'Original body',
        assetIds: ['fc7156dc-b74a-5e88-9bbd-5c29635a9da8', '9c57b8a2-6b56-5681-8dd9-b77e89dc1518'],
        scheduledFor: '2026-06-15T20:00:00Z',
        nextSlotAfter: null,
      })

      await store.updatePost('faed9dc9-1fab-5189-8be3-a824de11ffe3', {
        assetIds: ['fc7156dc-b74a-5e88-9bbd-5c29635a9da8', '9c57b8a2-6b56-5681-8dd9-b77e89dc1518'],
      })
      body = JSON.parse(apiFetch.mock.calls[1]?.[1]?.body as string)
      expect(body.assetIds).toEqual([
        'fc7156dc-b74a-5e88-9bbd-5c29635a9da8',
        '9c57b8a2-6b56-5681-8dd9-b77e89dc1518',
      ])
    })

    it('cancels a post in place', () => {
      const store = usePublishingStore()
      store.publications = [
        {
          id: 'b07bc02b-895e-53f8-8961-a062d353e8c1',
          content: 'Queued post',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      store.cancelPost('b07bc02b-895e-53f8-8961-a062d353e8c1')

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
          id: '2d814948-3da7-5042-8248-9a34f8b8fb7d',
          content: 'Queued post',
          title: 'Original title',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
          thumbnail: 'blob:old-thumb',
          accountId: '223bd3b9-4c0a-57cd-826f-681e172f6d7d',
        },
      ]

      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: '2d814948-3da7-5042-8248-9a34f8b8fb7d',
        workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
        socialAccountId: '223bd3b9-4c0a-57cd-826f-681e172f6d7d',
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

      await store.updatePost('2d814948-3da7-5042-8248-9a34f8b8fb7d', {
        thumbnail: 'blob:new-thumb',
        title: 'Updated again',
      })

      expect(store.publications[0]).toMatchObject({ title: 'Updated again', status: 'SCHEDULED' })
      stubUrl.revokeObjectURL.mockClear()
      await store.updatePost('2d814948-3da7-5042-8248-9a34f8b8fb7d', { title: 'Title only' })
      expect(stubUrl.revokeObjectURL).not.toHaveBeenCalled()
    })

    it('rolls back local publication when update fails', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.publications = [
        {
          id: '5e18a713-c93e-5353-9d71-fea5c480b47b',
          content: 'Original body',
          title: 'Original title',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
          accountId: '223bd3b9-4c0a-57cd-826f-681e172f6d7d',
        },
      ]
      vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('PATCH failed'))

      await expect(
        store.updatePost('5e18a713-c93e-5353-9d71-fea5c480b47b', { content: 'Changed body' }),
      ).rejects.toThrow('PATCH failed')
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
          id: '3719ce22-bedf-5f86-94ba-abc17bc82bad',
          content: 'Queued post',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
          thumbnail: 'blob:to-delete',
        },
      ]

      vi.spyOn(auth, 'apiFetch').mockResolvedValue({
        publicationId: '3719ce22-bedf-5f86-94ba-abc17bc82bad',
        workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
        socialAccountId: '223bd3b9-4c0a-57cd-826f-681e172f6d7d',
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

      await store.updatePost('3719ce22-bedf-5f86-94ba-abc17bc82bad', {
        thumbnail: 'blob:to-delete',
      })
      await store.deletePost('3719ce22-bedf-5f86-94ba-abc17bc82bad')

      expect(store.publications).toEqual([])
      expect(stubUrl.revokeObjectURL).toHaveBeenCalledWith('blob:to-delete')
    })

    it('retains local publication when delete fails', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
      store.publications = [
        {
          id: '6ed577bc-f136-5608-93e8-9f4269649f8b',
          content: 'Queued post',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]
      vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('DELETE failed'))

      await expect(store.deletePost('6ed577bc-f136-5608-93e8-9f4269649f8b')).rejects.toThrow(
        'DELETE failed',
      )
      expect(store.publications).toHaveLength(1)
      expect(store.publications[0]?.id).toBe('6ed577bc-f136-5608-93e8-9f4269649f8b')
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
          id: 'e4d7bcf1-bd92-5d1b-a2c8-0bc8624ca9e6',
          content: 'Queued post',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      await store.deletePost('e4d7bcf1-bd92-5d1b-a2c8-0bc8624ca9e6')

      expect(store.publications).toEqual([])
    })

    it('updates a post locally when unauthenticated', async () => {
      const store = usePublishingStore()
      const auth = useAuthStore()
      Object.defineProperty(auth, 'isAuthenticated', { value: false, configurable: true })
      store.publications = [
        {
          id: '675cbd8e-d47c-5731-bf69-811e027e45d6',
          content: 'Original body',
          title: 'Original title',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]

      await store.updatePost('675cbd8e-d47c-5731-bf69-811e027e45d6', {
        content: 'Updated body',
        title: 'Updated title',
      })

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
          id: '80bb873b-cfd3-5ef0-84f6-427e09272650',
          content: 'Body',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
          thumbnail: 'blob:old-thumb',
        },
      ]

      // First update to track the blob
      await store.updatePost('80bb873b-cfd3-5ef0-84f6-427e09272650', {
        thumbnail: 'blob:tracked-thumb',
      })

      // Second update replaces the blob — old one should be revoked
      await store.updatePost('80bb873b-cfd3-5ef0-84f6-427e09272650', {
        thumbnail: 'blob:new-thumb',
      })

      expect(stubUrl.revokeObjectURL).toHaveBeenCalledWith('blob:tracked-thumb')

      // Verify blob:new-thumb is tracked — a third replacement revokes it
      await store.updatePost('80bb873b-cfd3-5ef0-84f6-427e09272650', {
        thumbnail: 'blob:final-thumb',
      })
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
      store.filterSocialAccountId = '8ec9e4ac-9ec5-5c3a-8933-7af5407a797c'
      expect(store.calendarFilters.socialAccountId).toBe('8ec9e4ac-9ec5-5c3a-8933-7af5407a797c')
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
          id: '93070516-4676-52b4-8e4a-9ea4017ec68d',
          content: 'LinkedIn post',
          channels: ['linkedin'],
          scheduledAt: '2026-06-15T20:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
        {
          id: '28b71ec3-64b1-5db7-8d0a-6b4a19f15a95',
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
      expect(store.publications[0]?.id).toBe('93070516-4676-52b4-8e4a-9ea4017ec68d')
    })
  })
})
