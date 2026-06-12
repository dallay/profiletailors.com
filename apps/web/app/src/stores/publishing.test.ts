import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePublishingStore, type CalendarResponse } from './publishing'
import { useAuthStore } from './auth'

// ---------------------------------------------------------------------------
// Mock auth-api
// ---------------------------------------------------------------------------
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
  })

  describe('initial state', () => {
    it('loads initial publications from seed data when localStorage is empty', () => {
      const store = usePublishingStore()
      expect(store.publications.length).toBeGreaterThanOrEqual(2)
      expect(store.publications[0]?.status).toBe('QUEUED')
      expect(store.channels.length).toBe(3)
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
      const store = usePublishingStore()
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
