import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { consumeSseStream } from '@/lib/sse'
import { resolveApiUrl } from '@/lib/auth-api'
import { useAuthStore } from './auth'

// ---------------------------------------------------------------------------
// Types — Channel & Publication (frontend model)
// ---------------------------------------------------------------------------

export type SocialProvider = 'twitter' | 'linkedin' | 'instagram' | 'facebook'

export interface SocialConnectionResult {
  connectionId: string
  workspaceId: string
  provider: string
  status: string
  account: SocialAccountSummary
}

export interface SocialAccountSummary {
  accountId: string
  providerAccountId: string
  displayName: string
  kind: string
  profileUrn: string | null
}

export interface Channel {
  id: string
  name: string
  provider: SocialProvider
  avatar: string
  avatarUrl?: string
  handle: string
  status:
    | 'ACTIVE'
    | 'INACTIVE'
    | 'PENDING'
    | 'DISABLED'
    | 'REQUIRES_RECONNECT'
    | 'DELETED'
    | 'ERROR'
    | 'REVOKED'
    | 'EXPIRED'
  accountId: string // Maps to backend socialAccountId if available
  maxAttachments?: number
}

export interface Publication {
  id: string
  content: string
  title?: string
  channels: SocialProvider[]
  scheduledAt: string // ISO string
  scheduleMode?: 'NOW' | 'NEXT_SLOT' | 'SCHEDULED_AT'
  status:
    | 'DRAFT'
    | 'QUEUED'
    | 'SCHEDULED'
    | 'PROCESSING'
    | 'PUBLISHED'
    | 'BLOCKED'
    | 'FAILED'
    | 'CANCELLED'
  priority: boolean
  thumbnail?: string
  assetIds?: string[]
  mediaFiles?: File[] // Local file list for previewing uploads
  hasConflict?: boolean
  conflictingPublicationIds?: string[]
  /** The originating social account ID; used for direct account-level filtering. */
  accountId?: string
  /** LinkedIn external publication id (e.g. urn:li:share:...) — only set after a successful publish. */
  externalPublicationId?: string
  /** Direct URL to the published post on the provider (LinkedIn share URL). */
  publicUrl?: string
  /** Wall-clock instant the provider confirmed publish. */
  publishedAt?: string
  /** Reason the publication was blocked (e.g., account DISABLED or REQUIRES_RECONNECT). */
  blockedReason?: string
}

export type PublicationUpdate = Partial<Publication> & {
  assetIds?: string[]
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
  assetIds: string[]
  scheduledFor: string | null
  hasConflict: boolean
  conflictingPublicationIds: string[]
  externalPublicationId?: string | null
  publicUrl?: string | null
  publishedAt?: string | null
  previewUrl?: string | null
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

interface PublicationMutationResult {
  publicationId: string
  workspaceId: string
  socialAccountId: string
  status: string
  scheduleMode: string
  priority: boolean
  title: string | null
  bodyText: string | null
  assetIds: string[]
  scheduledFor: string | null
  nextSlotAfter: string | null
  externalPublicationId?: string | null
  publicUrl?: string | null
  publishedAt?: string | null
}

export interface ConnectedSocialChannelSummary {
  socialAccountId: string
  connectionId: string
  provider: string
  accountKind: string
  displayName: string
  status: string
  avatarUrl?: string | null
  connectedAt: string | null
  lastSyncedAt: string | null
}

export interface ConnectedChannelsResponse {
  channels: ConnectedSocialChannelSummary[]
}

interface LinkedInConnectionInitiationResult {
  authorizationUrl: string
  state: string
  expiresAt: string
}

/** Filter params accepted by fetchCalendar */
export interface CalendarFilters {
  status?: string
  socialAccountId?: string
  timezone?: string
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Normalizes a text input by trimming leading/trailing whitespace.
 * Internal spacing and intentional newlines are preserved.
 */
function normalizeText(input: string): string {
  return input.trim()
}

function deriveTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone
  } catch {
    return 'UTC'
  }
}

function readStoredPublications(): string | null {
  if (typeof localStorage === 'undefined') return null
  return localStorage.getItem('pt_publications')
}

/** Maps a backend provider string to the frontend channel type. */
function toChannelProvider(backendProvider: string): Publication['channels'][number] {
  const lower = backendProvider.toLowerCase()
  const known = new Set(['twitter', 'linkedin', 'instagram', 'facebook'])
  return known.has(lower) ? (lower as Publication['channels'][number]) : 'linkedin'
}

const CHANNEL_ATTACHMENT_LIMITS: Record<SocialProvider, number> = {
  linkedin: 9,
  twitter: 4,
  instagram: 10,
  facebook: 10,
}

function resolveChannelMaxAttachments(provider: SocialProvider): number {
  return CHANNEL_ATTACHMENT_LIMITS[provider]
}

function apiChannelToChannel(api: ConnectedSocialChannelSummary): Channel {
  const provider = toChannelProvider(api.provider)

  return {
    id: api.socialAccountId,
    accountId: api.socialAccountId,
    name: api.displayName,
    provider,
    avatar: '',
    avatarUrl: api.avatarUrl ?? undefined,
    handle: api.displayName,
    status: api.status as Channel['status'],
    maxAttachments: resolveChannelMaxAttachments(provider),
  }
}

function findActiveLinkedInChannel(
  channels: Channel[],
  socialAccountId?: string,
): Channel | undefined {
  if (socialAccountId) {
    return channels.find(
      (c) => c.accountId === socialAccountId && c.provider === 'linkedin' && c.status === 'ACTIVE',
    )
  }

  return channels.find((c) => c.provider === 'linkedin' && c.status === 'ACTIVE')
}

function matchesPublicationFilters(
  pub: Publication,
  filters: {
    channel?: string
    socialAccountId?: string
    tag?: string
    postType?: string
  },
): boolean {
  if (filters.channel && !(pub.channels as string[]).includes(filters.channel)) {
    return false
  }
  if (filters.socialAccountId && pub.accountId !== filters.socialAccountId) {
    return false
  }
  if (filters.tag && !pub.content.toLowerCase().includes(filters.tag.toLowerCase())) {
    return false
  }

  switch (filters.postType) {
    case 'queued':
      return pub.status === 'QUEUED'
    case 'published':
      return pub.status === 'PUBLISHED'
    case 'cancelled':
      return pub.status === 'CANCELLED'
    default:
      return true
  }
}

/** Converts a single API result to a frontend Publication. */
function apiResultToPublication(api: CalendarPublicationResult): Publication {
  return {
    id: api.id,
    content: api.bodyText ?? api.title ?? '',
    title: api.title ?? undefined,
    channels: [toChannelProvider(api.provider)],
    scheduledAt: api.scheduledFor ?? new Date().toISOString(),
    scheduleMode: api.scheduleMode as Publication['scheduleMode'],
    status: mapApiStatus(api.status),
    priority: api.priority,
    assetIds: api.assetIds,
    hasConflict: api.hasConflict,
    conflictingPublicationIds: api.conflictingPublicationIds,
    accountId: api.socialAccountId,
    externalPublicationId: api.externalPublicationId ?? undefined,
    publicUrl: api.publicUrl ?? undefined,
    publishedAt: api.publishedAt ?? undefined,
    thumbnail: api.previewUrl ? resolveApiUrl(api.previewUrl) : undefined,
  }
}

function publicationMutationResultToPublication(
  result: PublicationMutationResult,
  current: Publication,
): Publication {
  const scheduleMode = result.scheduleMode as Publication['scheduleMode']
  const scheduledAt = scheduleMode === 'NEXT_SLOT' ? result.nextSlotAfter : result.scheduledFor

  return {
    ...current,
    id: result.publicationId,
    title: result.title ?? undefined,
    content: result.bodyText ?? result.title ?? '',
    scheduledAt: scheduledAt ?? '',
    scheduleMode,
    status: mapApiStatus(result.status),
    priority: result.priority,
    assetIds: result.assetIds,
    accountId: result.socialAccountId,
    externalPublicationId: result.externalPublicationId ?? undefined,
    publicUrl: result.publicUrl ?? undefined,
    publishedAt: result.publishedAt ?? undefined,
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
    'BLOCKED',
    'FAILED',
    'CANCELLED',
  ])
  return valid.has(s) ? (s as Publication['status']) : 'DRAFT'
}

const MUTABLE_PUBLICATION_STATUSES = new Set<Publication['status']>([
  'DRAFT',
  'QUEUED',
  'SCHEDULED',
])

function isPublicationEditable(status: Publication['status']): boolean {
  return MUTABLE_PUBLICATION_STATUSES.has(status)
}

function isPublicationDeletable(status: Publication['status']): boolean {
  return MUTABLE_PUBLICATION_STATUSES.has(status)
}

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------

export const usePublishingStore = defineStore('publishing', () => {
  const auth = useAuthStore()

  const channels = ref<Channel[]>([])
  const channelsLoading = ref(false)
  const channelsError = ref<string | null>(null)
  const channelEventsConnected = ref(false)
  const channelEventsAbortController = ref<AbortController | null>(null)

  // Configured providers (which channels are available to connect)
  const configuredProviders = ref<string[]>([])
  const providersLoading = ref(false)

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
  const stored = readStoredPublications()
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

  // Reconnect state
  const hasReconnectRequiredChannels = computed(() =>
    channels.value.some(
      (ch) =>
        ch.status === 'REQUIRES_RECONNECT' || ch.status === 'REVOKED' || ch.status === 'EXPIRED',
    ),
  )

  /**
   * True when no connected channels are available.
   * Used as a guard to block post creation UI when the workspace has no channels.
   */
  const hasNoChannels = computed(
    () => channels.value.filter((ch) => ch.status === 'ACTIVE').length === 0,
  )
  const reconnectRequiredChannels = computed(() =>
    channels.value.filter(
      (ch) =>
        ch.status === 'REQUIRES_RECONNECT' || ch.status === 'REVOKED' || ch.status === 'EXPIRED',
    ),
  )

  /** Filters for the calendar API — derived from reactive filter state. */
  const calendarFilters = computed<CalendarFilters>(() => ({
    ...(filterPostType.value === 'all' ? {} : { status: filterPostType.value.toUpperCase() }),
    ...(filterSocialAccountId.value ? { socialAccountId: filterSocialAccountId.value } : {}),
  }))

  // Save changes helper
  function saveToStorage() {
    if (typeof localStorage === 'undefined') return
    localStorage.setItem('pt_publications', JSON.stringify(publications.value))
  }

  // -----------------------------------------------------------------------
  // Actions — Channel API
  // -----------------------------------------------------------------------

  async function fetchChannels() {
    if (!auth.isAuthenticated) {
      channels.value = []
      channelsError.value = null
      return []
    }

    channelsLoading.value = true
    channelsError.value = null

    try {
      const data = await auth.apiFetch<ConnectedChannelsResponse>('/api/publishing/channels', {
        method: 'GET',
        workspaceScoped: true,
      })
      channels.value = data.channels.map(apiChannelToChannel)
      return channels.value
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unable to load connected channels.'
      channelsError.value = message
      throw err
    } finally {
      channelsLoading.value = false
    }
  }

  async function fetchConfiguredProviders() {
    if (!auth.isAuthenticated) {
      configuredProviders.value = []
      return
    }

    providersLoading.value = true
    try {
      const data = await auth.apiFetch<{ providers: { name: string; configured: boolean }[] }>(
        '/api/publishing/channels/providers',
        { method: 'GET', workspaceScoped: true },
      )
      configuredProviders.value = data.providers.filter((p) => p.configured).map((p) => p.name)
    } catch {
      // Silently fail — preserve existing provider state rather than collapsing
      // transient network errors into a "not configured" state
    } finally {
      providersLoading.value = false
    }
  }

  const isLinkedInConfigured = computed(() => configuredProviders.value.includes('linkedin'))

  async function connectLinkedInPersonalProfile(
    redirectUri = `${globalThis.location.origin}/integrations/linkedin/callback`,
  ) {
    const data = await auth.apiFetch<LinkedInConnectionInitiationResult>(
      '/api/publishing/linkedin/connections/initiate',
      {
        method: 'POST',
        body: JSON.stringify({ redirectUri }),
        workspaceScoped: true,
      },
    )
    globalThis.location.assign(data.authorizationUrl)
    return data
  }

  async function completeLinkedInConnectionFromCallback(opts: {
    code: string
    state: string
    redirectUri: string
  }) {
    let result: SocialConnectionResult

    try {
      result = await auth.apiFetch<SocialConnectionResult>(
        '/api/publishing/linkedin/connections/complete',
        {
          method: 'POST',
          body: JSON.stringify({
            authorizationCode: opts.code,
            redirectUri: opts.redirectUri,
            state: opts.state,
          }),
          workspaceScoped: true,
        },
      )
    } catch (e) {
      console.error('Failed to complete connection', e)
      throw e
    }

    await fetchChannels()
    return result
  }

  async function subscribeChannelEvents() {
    if (!auth.isAuthenticated) {
      channelEventsConnected.value = false
      return null
    }

    unsubscribeChannelEvents()
    const abortController = new AbortController()
    channelEventsAbortController.value = abortController

    try {
      const response = await auth.apiFetchRaw('/api/publishing/channels/events', {
        method: 'GET',
        headers: { Accept: 'text/event-stream' },
        workspaceScoped: true,
        signal: abortController.signal,
      })
      channelEventsConnected.value = true
      await consumeSseStream(response, async ({ event }) => {
        if (event === 'connected-channel.updated' || event === 'connected-channel.removed') {
          await fetchChannels()
        }
      })
    } catch (err) {
      if (!abortController.signal.aborted) {
        console.warn('Channel event stream unavailable; continuing with REST channel list.', err)
      }
    } finally {
      if (channelEventsAbortController.value === abortController) {
        channelEventsAbortController.value = null
      }
      channelEventsConnected.value = false
    }

    return null
  }

  function unsubscribeChannelEvents() {
    channelEventsAbortController.value?.abort()
    channelEventsAbortController.value = null
    channelEventsConnected.value = false
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
      timezone: filters?.timezone ?? userTimezone.value,
    })
    if (filters?.status) params.set('status', filters.status)
    if (filters?.socialAccountId) params.set('socialAccountId', filters.socialAccountId)

    if (auth.isAuthenticated) {
      try {
        const data = await auth.apiFetch<CalendarResponse>(
          `/api/publishing/publications/calendar?${params.toString()}`,
          { workspaceScoped: true },
        )
        publications.value = data.publications.map(apiResultToPublication)
        activity.value = data.activity
        conflicts.value = data.conflicts
        // Prune object URLs for publications no longer in the current set
        const activeIds = new Set(publications.value.map((p) => p.id))
        for (const id of objectUrls.keys()) {
          if (!activeIds.has(id)) {
            const url = objectUrls.get(id)
            if (url) URL.revokeObjectURL(url)
            objectUrls.delete(id)
          }
        }
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
    const localPub: Publication = {
      id: `pub-${Date.now()}`,
      content: opts.bodyText,
      title: opts.title,
      channels: [toChannelProvider('linkedin')],
      accountId: opts.socialAccountId,
      scheduledAt: opts.scheduledFor,
      scheduleMode: 'SCHEDULED_AT',
      status: 'QUEUED',
      priority: opts.priority ?? false,
    }

    const newPub = auth.isAuthenticated
      ? publicationMutationResultToPublication(
          await auth.apiFetch<PublicationMutationResult>(
            '/api/publishing/publications/quick-create',
            {
              method: 'POST',
              body: JSON.stringify({
                socialAccountId: opts.socialAccountId,
                title: opts.title ?? 'Quick post',
                bodyText: opts.bodyText,
                scheduledFor: opts.scheduledFor,
                priority: opts.priority ?? false,
              }),
              workspaceScoped: true,
            },
          ),
          localPub,
        )
      : localPub

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
          workspaceScoped: true,
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

  /**
   * Retry a FAILED publication with a new future schedule.
   * Uses the dedicated /retry endpoint (not /reschedule) — the backend
   * rejects reschedule attempts on FAILED publications with a 409
   * "Publication state conflict" because only DRAFT/QUEUED/SCHEDULED
   * publications are editable. FAILED publications must go through /retry.
   */
  async function retryPublication(id: string, newScheduledFor: string) {
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
        await auth.apiFetch<unknown>(`/api/publishing/publications/${id}/retry`, {
          method: 'POST',
          body: JSON.stringify({
            scheduleMode: 'SCHEDULED_AT',
            scheduledFor: newScheduledFor,
            priority: previous.priority,
          }),
          workspaceScoped: true,
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
  /**
   * Creates and queues a publication for the specified channels.
   *
   * @param post - The publication details
   * @param post.scheduleMode - The scheduling mode: 'NOW' executes immediately, 'SCHEDULED_AT' uses the `scheduledAt` field, 'NEXT_SLOT' uses the `nextSlotAfter` field. Defaults to 'SCHEDULED_AT'.
   * @param post.nextSlotAfter - When `scheduleMode` is 'NEXT_SLOT', the earliest time to schedule the publication.
   * @returns The created Publication object.
   * @throws Error if LinkedIn channels are requested but no active LinkedIn account is available.
   */

  async function schedulePost(post: {
    content: string
    title?: string
    channels: SocialProvider[]
    scheduledAt?: string
    nextSlotAfter?: string
    scheduleMode?: 'NOW' | 'SCHEDULED_AT' | 'NEXT_SLOT'
    priority: boolean
    thumbnail?: string
    assetIds?: string[]
    socialAccountId?: string
  }) {
    const publicationId = `pub-${Date.now()}`

    const effectiveMode = post.scheduleMode ?? 'SCHEDULED_AT'
    const effectiveScheduledAt =
      effectiveMode === 'SCHEDULED_AT'
        ? (post.scheduledAt ?? new Date().toISOString())
        : (post.nextSlotAfter ?? new Date().toISOString())

    const newPub: Publication = {
      id: publicationId,
      content: normalizeText(post.content),
      title: post.title || undefined,
      channels: post.channels,
      accountId: post.socialAccountId,
      scheduledAt: effectiveScheduledAt,
      status: 'QUEUED',
      priority: post.priority,
      thumbnail: post.thumbnail,
    }

    const persistedPub = auth.isAuthenticated
      ? publicationMutationResultToPublication(
          await syncPublicationWithApi(post, effectiveMode),
          newPub,
        )
      : newPub

    publications.value.unshift(persistedPub)
    if (typeof persistedPub.thumbnail === 'string' && persistedPub.thumbnail.startsWith('blob:')) {
      objectUrls.set(persistedPub.id, persistedPub.thumbnail)
    }
    saveToStorage()
    return persistedPub
  }

  /** Syncs an authenticated LinkedIn publication and returns backend server truth. */
  async function syncPublicationWithApi(
    post: Parameters<typeof schedulePost>[0],
    effectiveMode: string,
  ): Promise<PublicationMutationResult> {
    const hasLinkedIn = post.channels.includes('linkedin')
    if (!hasLinkedIn) {
      throw new Error('Authenticated publication sync currently requires a LinkedIn channel.')
    }

    const linkedInChannel = findActiveLinkedInChannel(channels.value, post.socialAccountId)
    if (!linkedInChannel?.accountId) {
      throw new Error('Connect a LinkedIn profile before scheduling authenticated posts.')
    }

    const resolvedAssetIds = post.assetIds ?? []

    return auth.apiFetch<PublicationMutationResult>('/api/publishing/publications', {
      method: 'POST',
      body: JSON.stringify({
        socialAccountId: linkedInChannel.accountId,
        title: post.title || 'Post via Web App',
        bodyText: normalizeText(post.content),
        assetIds: resolvedAssetIds,
        scheduleMode: effectiveMode,
        ...(effectiveMode === 'SCHEDULED_AT' ? { scheduledFor: post.scheduledAt } : {}),
        ...(effectiveMode === 'NEXT_SLOT' ? { nextSlotAfter: post.nextSlotAfter } : {}),
        priority: post.priority,
      }),
      workspaceScoped: true,
    })
  }

  async function deletePost(id: string) {
    if (!publications.value.some((p) => p.id === id)) {
      throw new Error(`Publication ${id} not found`)
    }

    if (auth.isAuthenticated) {
      await auth.apiFetch<PublicationMutationResult>(`/api/publishing/publications/${id}`, {
        method: 'DELETE',
        workspaceScoped: true,
      })
    }

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

  /**
   * Update tracked object URLs (blob revocations / assignments) for a publication.
   * Shared between the authenticated and unauthenticated paths of updatePost
   * to avoid duplicated cognitive complexity.
   */
  function updateTrackedObjectUrls(
    id: string,
    updates: { thumbnail?: string },
    current: { thumbnail?: string },
  ): void {
    if (updates.thumbnail && current.thumbnail && objectUrls.has(id)) {
      const trackedUrl = objectUrls.get(id)
      if (trackedUrl) URL.revokeObjectURL(trackedUrl)
      objectUrls.delete(id)
    }
    if (typeof updates.thumbnail === 'string' && updates.thumbnail.startsWith('blob:')) {
      objectUrls.set(id, updates.thumbnail)
    } else if (updates.thumbnail == null && objectUrls.has(id)) {
      objectUrls.delete(id)
    }
  }

  async function updatePost(id: string, updates: PublicationUpdate) {
    const current = publications.value.find((p) => p.id === id)
    if (!current) {
      throw new Error(`Publication ${id} not found`)
    }

    if (auth.isAuthenticated) {
      const result = await auth.apiFetch<PublicationMutationResult>(
        `/api/publishing/publications/${id}`,
        {
          method: 'PATCH',
          body: JSON.stringify({
            socialAccountId: current.accountId,
            title: updates.title ?? current.title ?? null,
            bodyText: updates.content ?? current.content,
            ...(Object.hasOwn(updates, 'assetIds') ? { assetIds: updates.assetIds ?? [] } : {}),
            scheduleMode: updates.scheduleMode ?? current.scheduleMode ?? 'SCHEDULED_AT',
            scheduledFor: updates.scheduledAt ?? current.scheduledAt,
            priority: updates.priority ?? current.priority,
          }),
          workspaceScoped: true,
        },
      )

      updateTrackedObjectUrls(id, updates, current)

      const merged = publicationMutationResultToPublication(result, {
        ...current,
        ...updates,
      })
      publications.value[publications.value.indexOf(current)] = merged
      saveToStorage()
      return merged
    }

    updateTrackedObjectUrls(id, updates, current)
    const updated = { ...current, ...updates }
    publications.value[publications.value.indexOf(current)] = updated
    saveToStorage()
    return updated
  }

  // -----------------------------------------------------------------------
  // Helpers — local filtering fallback
  // -----------------------------------------------------------------------

  function applyLocalFilters(list: Publication[]): Publication[] {
    return list.filter((pub) =>
      matchesPublicationFilters(pub, {
        channel: filterChannel.value || undefined,
        socialAccountId: filterSocialAccountId.value || undefined,
        tag: filterTag.value || undefined,
        postType: filterPostType.value === 'all' ? undefined : filterPostType.value,
      }),
    )
  }

  // -----------------------------------------------------------------------
  // Public surface
  // -----------------------------------------------------------------------

  return {
    // State
    channels,
    channelsLoading,
    channelsError,
    channelEventsConnected,
    channelEventsAbortController,
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
    configuredProviders,
    providersLoading,
    isLinkedInConfigured,
    hasReconnectRequiredChannels,
    reconnectRequiredChannels,
    hasNoChannels,
    isPublicationEditable,
    isPublicationDeletable,
    // Actions
    fetchChannels,
    fetchConfiguredProviders,
    connectLinkedInPersonalProfile,
    completeLinkedInConnectionFromCallback,
    subscribeChannelEvents,
    unsubscribeChannelEvents,
    fetchCalendar,
    quickCreatePost,
    reschedulePublication,
    retryPublication,
    schedulePost,
    deletePost,
    cancelPost,
    updatePost,
  }
})
