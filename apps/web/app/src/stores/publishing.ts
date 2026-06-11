import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { useAuthStore } from './auth'

// ---------------------------------------------------------------------------
// Types — Channel & Publication (frontend model)
// ---------------------------------------------------------------------------

export interface Channel {
  id: string
  name: string
  provider: 'twitter' | 'linkedin' | 'instagram' | 'facebook'
  avatar: string
  handle: string
  status: 'ACTIVE' | 'INACTIVE'
  accountId: string // Maps to backend socialAccountId if available
}

export interface Publication {
  id: string
  content: string
  title?: string
  channels: ('twitter' | 'linkedin' | 'instagram' | 'facebook')[]
  scheduledAt: string // ISO string
  status: 'DRAFT' | 'QUEUED' | 'SCHEDULED' | 'PROCESSING' | 'PUBLISHED' | 'FAILED' | 'CANCELLED'
  priority: boolean
  thumbnail?: string
  mediaFiles?: File[] // Local file list for previewing uploads
  hasConflict?: boolean
  conflictingPublicationIds?: string[]
}

// ---------------------------------------------------------------------------
// Types — Backend DTOs (calendar API response)
// ---------------------------------------------------------------------------

export type ActivityDensity = 'NONE' | 'LIGHT' | 'MEDIUM' | 'HIGH'

export interface CalendarPublicationResult {
  id: string
  workspaceId: string
  socialAccountId: string
  provider: string
  status: string
  scheduleMode: string
  priority: boolean
  title: string | null
  bodyText: string | null
  scheduledFor: string | null
  hasConflict: boolean
  conflictingPublicationIds: string[]
}

export interface ConflictEntry {
  publicationId: string
  conflictingPublicationIds: string[]
  reason: string
}

export interface ActivityEntry {
  date: string // ISO LocalDate
  density: ActivityDensity
  count: number
}

export interface CalendarResponse {
  publications: CalendarPublicationResult[]
  conflicts: ConflictEntry[]
  activity: ActivityEntry[]
}

/** Filter params accepted by fetchCalendar */
export interface CalendarFilters {
  status?: string
  socialAccountId?: string
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function deriveTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone
  } catch {
    return 'UTC'
  }
}

/** Maps a backend provider string to the frontend channel type. */
function toChannelProvider(backendProvider: string): Publication['channels'][number] {
  const lower = backendProvider.toLowerCase()
  const known = new Set(['twitter', 'linkedin', 'instagram', 'facebook'])
  return known.has(lower) ? (lower as Publication['channels'][number]) : 'linkedin'
}

/** Converts a single API result to a frontend Publication. */
function apiResultToPublication(api: CalendarPublicationResult): Publication {
  return {
    id: api.id,
    content: api.bodyText ?? api.title ?? '',
    title: api.title ?? undefined,
    channels: [toChannelProvider(api.provider)],
    scheduledAt: api.scheduledFor ?? new Date().toISOString(),
    status: mapApiStatus(api.status),
    priority: api.priority,
    hasConflict: api.hasConflict,
    conflictingPublicationIds: api.conflictingPublicationIds,
  }
}

/** Converts backend status strings to frontend union. */
function mapApiStatus(s: string): Publication['status'] {
  const valid: Set<string> = new Set([
    'DRAFT',
    'QUEUED',
    'SCHEDULED',
    'PROCESSING',
    'PUBLISHED',
    'FAILED',
    'CANCELLED',
  ])
  return valid.has(s) ? (s as Publication['status']) : 'DRAFT'
}

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------

export const usePublishingStore = defineStore('publishing', () => {
  const auth = useAuthStore()

  // Seeding initial mock channels
  const channels = ref<Channel[]>([
    {
      id: 'ch-twitter',
      name: 'yacosta738',
      provider: 'twitter',
      avatar:
        'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80',
      handle: '@yacosta738',
      status: 'ACTIVE',
      accountId: 'account-twitter-mock',
    },
    {
      id: 'ch-linkedin',
      name: 'Yuniel Acosta Pérez',
      provider: 'linkedin',
      avatar:
        'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=150&q=80',
      handle: 'Yuniel Acosta Pérez',
      status: 'ACTIVE',
      accountId: 'account-linkedin-mock',
    },
    {
      id: 'ch-instagram',
      name: 'yacosta738',
      provider: 'instagram',
      avatar:
        'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=150&q=80',
      handle: '@yacosta738',
      status: 'ACTIVE',
      accountId: 'account-instagram-mock',
    },
  ])

  // Seeding initial mock publications
  const initialPublications: Publication[] = [
    {
      id: 'pub-1',
      content:
        'El error más común con DDD: crear modelos anémicos y tratar la base de datos como el centro del diseño. ¡Concéntrate en el comportamiento primero!',
      title: 'Common DDD Mistake',
      channels: ['linkedin', 'twitter'],
      scheduledAt: '2026-06-09T22:00:00Z',
      status: 'QUEUED',
      priority: false,
      thumbnail:
        'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=200&q=80',
    },
    {
      id: 'pub-2',
      content:
        '¿Por qué tu Arquitectura debería seguir principios de diseño suizos? Minimalismo visual, tipografía clara y cero ruido ornamental.',
      title: 'Swiss Design Architecture',
      channels: ['linkedin'],
      scheduledAt: '2026-06-09T22:00:00Z',
      status: 'QUEUED',
      priority: false,
      thumbnail:
        'https://images.unsplash.com/photo-1507238691740-187a5b1d37b8?auto=format&fit=crop&w=200&q=80',
    },
  ]

  // Persisted publications list
  const publications = ref<Publication[]>([])
  // Track object URLs for memory cleanup
  const objectUrls = new Map<string, string>()

  // Load from localStorage or seed
  const stored = localStorage.getItem('pt_publications')
  if (stored) {
    try {
      publications.value = JSON.parse(stored)
    } catch {
      publications.value = initialPublications
    }
  } else {
    publications.value = initialPublications
  }

  // Activity & conflicts from calendar API
  const activity = ref<ActivityEntry[]>([])
  const conflicts = ref<ConflictEntry[]>([])

  // Active filters and settings
  const userTimezone = ref(deriveTimezone())
  const filterTag = ref('')
  const filterChannel = ref('')
  const filterPostType = ref('all')
  const filterSocialAccountId = ref('')
  const viewMode = ref<'calendar' | 'list'>('calendar')

  /** Filters for the calendar API — derived from reactive filter state. */
  const calendarFilters = computed<CalendarFilters>(() => ({
    ...(filterPostType.value !== 'all' ? { status: filterPostType.value.toUpperCase() } : {}),
    ...(filterSocialAccountId.value ? { socialAccountId: filterSocialAccountId.value } : {}),
  }))

  // Save changes helper
  function saveToStorage() {
    localStorage.setItem('pt_publications', JSON.stringify(publications.value))
  }

  // -----------------------------------------------------------------------
  // Actions — Calendar API
  // -----------------------------------------------------------------------

  /**
   * Fetch publications, conflicts, and activity for a date range.
   * Falls back to localStorage-filtered data when unauthenticated or on network error.
   */
  async function fetchCalendar(from: string, to: string, filters?: CalendarFilters) {
    const params = new URLSearchParams({
      from,
      to,
      timezone: userTimezone.value,
    })
    if (filters?.status) params.set('status', filters.status)
    if (filters?.socialAccountId) params.set('socialAccountId', filters.socialAccountId)

    if (auth.isAuthenticated) {
      try {
        const data = await auth.apiFetch<CalendarResponse>(
          `/api/publishing/publications/calendar?${params.toString()}`,
        )
        publications.value = data.publications.map(apiResultToPublication)
        activity.value = data.activity
        conflicts.value = data.conflicts
        saveToStorage()
        return
      } catch (err) {
        console.warn('Calendar API unavailable, falling back to local data', err)
      }
    }

    // Fallback: filter from localStorage
    const local = applyLocalFilters(publications.value)
    publications.value = local
    // Reset API-only state when falling back
    activity.value = []
    conflicts.value = []
    saveToStorage()
  }

  /** Quick-create a publication from calendar cells (no assets, no multi-channel). */
  async function quickCreatePost(opts: {
    socialAccountId: string
    title?: string
    bodyText: string
    scheduledFor: string
    priority?: boolean
  }) {
    const publicationId = `pub-${Date.now()}`

    const newPub: Publication = {
      id: publicationId,
      content: opts.bodyText,
      title: opts.title,
      channels: [toChannelProvider('linkedin')],
      scheduledAt: opts.scheduledFor,
      status: 'QUEUED',
      priority: opts.priority ?? false,
    }

    if (auth.isAuthenticated) {
      try {
        await auth.apiFetch<unknown>('/api/publishing/publications/quick-create', {
          method: 'POST',
          body: JSON.stringify({
            socialAccountId: opts.socialAccountId,
            title: opts.title ?? 'Quick post',
            bodyText: opts.bodyText,
            scheduledFor: opts.scheduledFor,
            priority: opts.priority ?? false,
          }),
        })
      } catch (err) {
        console.warn('Quick-create API unavailable, saving locally', err)
      }
    }

    publications.value.unshift(newPub)
    saveToStorage()
    return newPub
  }

  /**
   * Reschedule a publication with optimistic update + rollback on failure.
   * Returns the updated publication on success, or rolls back and throws.
   */
  async function reschedulePublication(id: string, newScheduledFor: string) {
    const idx = publications.value.findIndex((p) => p.id === id)
    if (idx === -1) throw new Error(`Publication ${id} not found`)

    const current = publications.value[idx]
    if (!current) throw new Error(`Publication ${id} not found`)

    const previous: Publication = { ...current }
    const rollbackValue = previous.scheduledAt

    // Optimistic update
    publications.value[idx] = { ...previous, scheduledAt: newScheduledFor }

    if (auth.isAuthenticated) {
      try {
        await auth.apiFetch<unknown>(`/api/publishing/publications/${id}/reschedule`, {
          method: 'PATCH',
          body: JSON.stringify({
            scheduleMode: 'SCHEDULED_AT',
            scheduledFor: newScheduledFor,
            priority: previous.priority,
          }),
        })
        saveToStorage()
        return publications.value[idx]
      } catch (err) {
        // Rollback
        publications.value[idx] = { ...previous, scheduledAt: rollbackValue }
        saveToStorage()
        throw err
      }
    }

    // Unauthenticated: just save locally
    saveToStorage()
    return publications.value[idx]
  }

  // -----------------------------------------------------------------------
  // Actions — Existing  (modified with fallback awareness)
  // -----------------------------------------------------------------------

  async function schedulePost(post: {
    content: string
    title?: string
    channels: ('twitter' | 'linkedin' | 'instagram' | 'facebook')[]
    scheduledAt: string
    priority: boolean
    mediaFiles?: File[]
  }) {
    const publicationId = `pub-${Date.now()}`

    // Create new publication object
    const newPub: Publication = {
      id: publicationId,
      content: post.content,
      title: post.title || undefined,
      channels: post.channels,
      scheduledAt: post.scheduledAt,
      status: 'QUEUED',
      priority: post.priority,
      mediaFiles: post.mediaFiles,
    }

    // Generate static image thumbnail for file preview if uploaded
    if (post.mediaFiles && post.mediaFiles.length > 0) {
      const file = post.mediaFiles[0]
      if (file?.type?.startsWith('image/')) {
        const objectUrl = URL.createObjectURL(file)
        newPub.thumbnail = objectUrl
        objectUrls.set(publicationId, objectUrl)
      }
    }

    // Try backend integration if authenticated
    if (auth.isAuthenticated) {
      try {
        // LinkedIn is the only active integration on the backend
        const hasLinkedIn = post.channels.includes('linkedin')
        if (hasLinkedIn) {
          // Find the active account ID. In production we map workspace connections
          const linkedInChannel = channels.value.find((c) => c.provider === 'linkedin')
          const accountId = linkedInChannel?.accountId || 'account-linkedin-mock'

          // Call the Spring Boot API
          await auth.apiFetch<unknown>('/api/publishing/publications', {
            method: 'POST',
            body: JSON.stringify({
              socialAccountId: accountId,
              title: post.title || 'Post via Web App',
              bodyText: post.content,
              assetIds: [],
              scheduleMode: 'SCHEDULED_AT',
              scheduledFor: post.scheduledAt,
              priority: post.priority,
            }),
          })
          console.log('Successfully synced publication with backend API!')
        }
      } catch (err) {
        console.warn('Backend API unavailable. Saving to local storage mock queue instead.', err)
      }
    }

    // Push local state
    publications.value.unshift(newPub)
    saveToStorage()
    return newPub
  }

  function deletePost(id: string) {
    // Revoke object URL if tracked
    const url = objectUrls.get(id)
    if (url) {
      URL.revokeObjectURL(url)
      objectUrls.delete(id)
    }
    publications.value = publications.value.filter((p) => p.id !== id)
    saveToStorage()
  }

  function cancelPost(id: string) {
    const post = publications.value.find((p) => p.id === id)
    if (post) {
      post.status = 'CANCELLED'
      saveToStorage()
    }
  }

  function updatePost(id: string, updates: Partial<Publication>) {
    const post = publications.value.find((p) => p.id === id)
    if (post) {
      // If thumbnail is being replaced, revoke old object URL
      if (updates.thumbnail && post.thumbnail && objectUrls.has(id)) {
        const trackedUrl = objectUrls.get(id)
        if (trackedUrl) URL.revokeObjectURL(trackedUrl)
        objectUrls.delete(id)
      }
      // Track new blob URL for later revocation
      if (typeof updates.thumbnail === 'string' && updates.thumbnail.startsWith('blob:')) {
        objectUrls.set(id, updates.thumbnail)
      } else if (updates.thumbnail == null && objectUrls.has(id)) {
        objectUrls.delete(id)
      }
      Object.assign(post, updates)
      saveToStorage()
    }
  }

  // -----------------------------------------------------------------------
  // Helpers — local filtering fallback
  // -----------------------------------------------------------------------

  function applyLocalFilters(list: Publication[]): Publication[] {
    return list.filter((pub) => {
      if (filterChannel.value && !(pub.channels as string[]).includes(filterChannel.value)) {
        return false
      }
      if (filterTag.value && !pub.content.toLowerCase().includes(filterTag.value.toLowerCase())) {
        return false
      }
      if (filterPostType.value !== 'all') {
        if (filterPostType.value === 'queued' && pub.status !== 'QUEUED') return false
        if (filterPostType.value === 'published' && pub.status !== 'PUBLISHED') return false
        if (filterPostType.value === 'cancelled' && pub.status !== 'CANCELLED') return false
      }
      return true
    })
  }

  // -----------------------------------------------------------------------
  // Public surface
  // -----------------------------------------------------------------------

  return {
    // State
    channels,
    publications,
    activity,
    conflicts,
    userTimezone,
    filterTag,
    filterChannel,
    filterPostType,
    filterSocialAccountId,
    viewMode,
    calendarFilters,
    // Actions
    fetchCalendar,
    quickCreatePost,
    reschedulePublication,
    schedulePost,
    deletePost,
    cancelPost,
    updatePost,
  }
})
