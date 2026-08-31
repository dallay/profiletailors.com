import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePublishingStore } from './publishing.store'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'

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

async function computeExpectedHash(csvText: string): Promise<string> {
  const bytes = new TextEncoder().encode(csvText)
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

describe('publishing store bulk', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    globalThis.localStorage?.clear()
    vi.restoreAllMocks()
  })

  it('validateBulk throws when no workspace selected', async () => {
    const store = usePublishingStore()
    const ws = useWorkspaceStore()
    ws.setActiveWorkspaceId(null)
    await expect(store.validateBulk('a,b')).rejects.toThrow(
      'Select a workspace before bulk import.',
    )
  })

  it('validateBulk calls apiFetch with correct path and body', async () => {
    const store = usePublishingStore()
    const auth = useAuthStore()
    const ws = useWorkspaceStore()
    ws.setActiveWorkspaceId('ws-bulk')
    Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
    const spy = vi.spyOn(auth, 'apiFetch').mockResolvedValue({ rows: [] } as never)
    const result = await store.validateBulk('hello,2026-09-01T10:00:00Z')
    expect(spy).toHaveBeenCalledWith('/api/v1/workspaces/ws-bulk/bulk/validate', {
      method: 'POST',
      body: JSON.stringify({ csvText: 'hello,2026-09-01T10:00:00Z' }),
      workspaceScoped: true,
    })
    expect(result).toEqual({ rows: [] })
  })

  it('scheduleBulk with explicit hash uses provided hash', async () => {
    const store = usePublishingStore()
    const auth = useAuthStore()
    const ws = useWorkspaceStore()
    ws.setActiveWorkspaceId('ws-bulk')
    const spy = vi.spyOn(auth, 'apiFetch').mockResolvedValue({ jobId: 'j1' } as never)
    const result = await store.scheduleBulk('csv-content', 'abc123hash')
    expect(spy).toHaveBeenCalledWith('/api/v1/workspaces/ws-bulk/bulk/schedule', {
      method: 'POST',
      body: JSON.stringify({ csvText: 'csv-content', csvHash: 'abc123hash' }),
      workspaceScoped: true,
    })
    expect(result).toEqual({ jobId: 'j1' })
  })

  it('scheduleBulk without hash computes SHA-256 via crypto.subtle', async () => {
    const store = usePublishingStore()
    const auth = useAuthStore()
    const ws = useWorkspaceStore()
    ws.setActiveWorkspaceId('ws-bulk')
    const spy = vi.spyOn(auth, 'apiFetch').mockResolvedValue({ jobId: 'j2' } as never)
    const csv = 'hello world'
    await store.scheduleBulk(csv)
    const expectedHash = await computeExpectedHash(csv)
    expect(spy).toHaveBeenCalledWith('/api/v1/workspaces/ws-bulk/bulk/schedule', {
      method: 'POST',
      body: JSON.stringify({ csvText: csv, csvHash: expectedHash }),
      workspaceScoped: true,
    })
  })

  it('scheduleBulk without hash falls back to csvText when crypto.subtle missing', async () => {
    const store = usePublishingStore()
    const auth = useAuthStore()
    const ws = useWorkspaceStore()
    ws.setActiveWorkspaceId('ws-bulk')
    const spy = vi.spyOn(auth, 'apiFetch').mockResolvedValue({ jobId: 'j3' } as never)
    const originalSubtle = globalThis.crypto.subtle
    Object.defineProperty(globalThis.crypto, 'subtle', { value: undefined, configurable: true })
    try {
      await store.scheduleBulk('fallback-csv')
      expect(spy).toHaveBeenCalledWith('/api/v1/workspaces/ws-bulk/bulk/schedule', {
        method: 'POST',
        body: JSON.stringify({ csvText: 'fallback-csv', csvHash: 'fallback-csv' }),
        workspaceScoped: true,
      })
    } finally {
      Object.defineProperty(globalThis.crypto, 'subtle', {
        value: originalSubtle,
        configurable: true,
      })
    }
  })

  it('fetchBulkJob encodes jobId and calls apiFetch', async () => {
    const store = usePublishingStore()
    const auth = useAuthStore()
    const ws = useWorkspaceStore()
    ws.setActiveWorkspaceId('ws-123')
    const spy = vi.spyOn(auth, 'apiFetch').mockResolvedValue({ jobId: 'job/1' } as never)
    await store.fetchBulkJob('job/1')
    expect(spy).toHaveBeenCalledWith('/api/v1/workspaces/ws-123/bulk/jobs/job%2F1', {
      method: 'GET',
      workspaceScoped: true,
    })
  })

  it('fetchBulkTemplates calls apiFetch with templates path', async () => {
    const store = usePublishingStore()
    const auth = useAuthStore()
    const ws = useWorkspaceStore()
    ws.setActiveWorkspaceId('ws-t')
    const spy = vi.spyOn(auth, 'apiFetch').mockResolvedValue({ templates: [] } as never)
    await store.fetchBulkTemplates()
    expect(spy).toHaveBeenCalledWith('/api/v1/workspaces/ws-t/bulk/templates', {
      method: 'GET',
      workspaceScoped: true,
    })
  })

  it('fetchBulkTemplateCsv encodes templateId and returns text', async () => {
    const store = usePublishingStore()
    const auth = useAuthStore()
    const ws = useWorkspaceStore()
    ws.setActiveWorkspaceId('ws-t')
    const spy = vi
      .spyOn(auth, 'apiFetchRaw')
      .mockResolvedValue(new Response('a,b,c', { status: 200 }))
    const result = await store.fetchBulkTemplateCsv('tpl/1')
    expect(spy).toHaveBeenCalledWith('/api/v1/workspaces/ws-t/bulk/templates/tpl%2F1/csv', {
      method: 'GET',
      workspaceScoped: true,
    })
    expect(result).toBe('a,b,c')
  })

  it('fetchBulkJob throws when no workspace', async () => {
    const store = usePublishingStore()
    const ws = useWorkspaceStore()
    ws.setActiveWorkspaceId(null)
    await expect(store.fetchBulkJob('j1')).rejects.toThrow('Select a workspace before bulk import.')
  })

  it('fetchBulkTemplates throws when no workspace', async () => {
    const store = usePublishingStore()
    const ws = useWorkspaceStore()
    ws.setActiveWorkspaceId(null)
    await expect(store.fetchBulkTemplates()).rejects.toThrow(
      'Select a workspace before bulk import.',
    )
  })

  it('fetchBulkTemplateCsv throws when no workspace', async () => {
    const store = usePublishingStore()
    const ws = useWorkspaceStore()
    ws.setActiveWorkspaceId(null)
    await expect(store.fetchBulkTemplateCsv('tpl-1')).rejects.toThrow(
      'Select a workspace before bulk import.',
    )
  })

  it('fetchCalendar builds query with timezone and filters', async () => {
    const store = usePublishingStore()
    const auth = useAuthStore()
    const ws = useWorkspaceStore()
    ws.setActiveWorkspaceId('ws-1')
    Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
    const spy = vi.spyOn(auth, 'apiFetch').mockResolvedValue({
      publications: [],
      conflicts: [],
      activity: [],
    })
    store.userTimezone = 'Europe/Madrid'
    await store.fetchCalendar('2026-06-01T00:00:00Z', '2026-07-01T00:00:00Z', {
      status: 'QUEUED',
      socialAccountId: 'acc-1',
      timezone: 'UTC',
    })
    expect(spy).toHaveBeenCalledWith(
      expect.stringContaining('/api/publishing/publications/calendar?'),
      expect.objectContaining({ workspaceScoped: true }),
    )
    const url = spy.mock.calls[0]?.[0] as string
    expect(url).toContain('from=2026-06-01T00%3A00%3A00Z')
    expect(url).toContain('to=2026-07-01T00%3A00%3A00Z')
    expect(url).toContain('timezone=UTC')
    expect(url).toContain('status=QUEUED')
    expect(url).toContain('socialAccountId=acc-1')
  })

  it('fetchCalendar falls back to local filtered data when remote fails', async () => {
    const store = usePublishingStore()
    const auth = useAuthStore()
    Object.defineProperty(auth, 'isAuthenticated', { value: true, configurable: true })
    vi.spyOn(auth, 'apiFetch').mockRejectedValue(new Error('network'))
    store.publications = [
      {
        id: 'local-1',
        content: 'Architecture notes',
        channels: ['linkedin'],
        accountId: 'acc-1',
        scheduledAt: '2026-06-10T12:00:00Z',
        status: 'QUEUED',
        priority: false,
      },
    ]
    store.activity = [{ date: '2026-06-10', density: 'HIGH', count: 3 }] as never
    store.conflicts = [
      { publicationId: 'local-1', conflictingPublicationIds: [], reason: 'x' },
    ] as never
    await store.fetchCalendar('2026-06-01T00:00:00Z', '2026-07-01T00:00:00Z')
    expect(store.activity).toEqual([])
    expect(store.conflicts).toEqual([])
  })

  it('initial publications loads from localStorage when valid JSON present', () => {
    localStorage.setItem(
      'pt_publications',
      JSON.stringify([
        {
          id: 'stored-1',
          content: 'stored',
          channels: ['linkedin'],
          scheduledAt: '2026-06-10T12:00:00Z',
          status: 'QUEUED',
          priority: false,
        },
      ]),
    )
    setActivePinia(createPinia())
    const store = usePublishingStore()
    expect(store.publications.some((p) => p.id === 'stored-1')).toBe(true)
  })

  it('initial publications falls back to seed when localStorage contains invalid JSON', () => {
    localStorage.setItem('pt_publications', 'not-json')
    setActivePinia(createPinia())
    const store = usePublishingStore()
    expect(store.publications.length).toBeGreaterThanOrEqual(2)
  })

  it('deriveTimezone fallback returns UTC when Intl throws', async () => {
    const original = Intl.DateTimeFormat
    // @ts-expect-error mock
    Intl.DateTimeFormat = (() => ({
      resolvedOptions: () => {
        throw new Error('fail')
      },
    })) as never
    try {
      setActivePinia(createPinia())
      const store = usePublishingStore()
      expect(store.userTimezone).toBe('UTC')
    } finally {
      Intl.DateTimeFormat = original
    }
  })
})
