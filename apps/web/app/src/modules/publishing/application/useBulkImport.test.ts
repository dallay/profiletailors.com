import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useBulkImport } from './useBulkImport'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'
import { useWorkspaceStore } from '@modules/workspace/infrastructure/workspace.store'

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  createApiFetch: () =>
    Object.assign(async () => ({}), { raw: async () => new Response(null, { status: 204 }) }),
  resolveApiUrl: vi.fn((p: string) => `https://api.test${p}`),
  refreshSession: vi.fn().mockResolvedValue(null),
  getCurrentUserProfile: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  register: vi.fn(),
  logoutSession: vi.fn(),
}))

describe('useBulkImport', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('validate delegates to store and tracks state', async () => {
    const store = usePublishingStore()
    const composable = useBulkImport()
    const validated: import('@modules/publishing/domain/bulk').ValidateBulkResult = {
      rows: [{ rowIndex: 0, status: 'VALID', errors: [] }],
    }
    const mock = vi.spyOn(store, 'validateBulk').mockResolvedValue(validated)
    const result = await composable.validate('csv')
    expect(mock).toHaveBeenCalledWith('csv')
    expect(result.rows[0]?.status).toBe('VALID')
    expect(composable.validateResult.value?.rows).toHaveLength(1)
    expect(composable.isValidating.value).toBe(false)
  })

  it('schedule delegates with hash and tracks', async () => {
    const store = usePublishingStore()
    const composable = useBulkImport()
    const scheduled: import('@modules/publishing/domain/bulk').ScheduleBulkResult = {
      jobId: 'j1',
      totalRows: 1,
      scheduledCount: 1,
      failedCount: 0,
      rows: [],
    }
    vi.spyOn(store, 'scheduleBulk').mockResolvedValue(scheduled)
    const result = await composable.schedule('csv', 'hash123')
    expect(result.jobId).toBe('j1')
    expect(composable.scheduleResult.value?.jobId).toBe('j1')
  })

  it('hasValidationErrors computed', async () => {
    const store = usePublishingStore()
    const composable = useBulkImport()
    const invalid: import('@modules/publishing/domain/bulk').ValidateBulkResult = {
      rows: [
        { rowIndex: 0, status: 'INVALID', errors: [{ code: 'INVALID_DATE', message: 'bad' }] },
      ],
    }
    vi.spyOn(store, 'validateBulk').mockResolvedValue(invalid)
    await composable.validate('csv')
    expect(composable.hasValidationErrors.value).toBe(true)
    expect(composable.invalidRows.value).toHaveLength(1)
  })

  it('fetchJob delegates', async () => {
    const store = usePublishingStore()
    const composable = useBulkImport()
    const jobResult: import('@modules/publishing/domain/bulk').BulkJobResult = {
      jobId: 'j1',
      status: 'SCHEDULED',
      totalRows: 1,
      scheduledCount: 1,
      failedCount: 0,
      rows: [],
    }
    vi.spyOn(store, 'fetchBulkJob').mockResolvedValue(jobResult)
    const job = await composable.fetchJob('j1')
    expect(job.status).toBe('SCHEDULED')
  })

  it('workspaceIdOrThrow throws when no workspace', () => {
    const composable = useBulkImport()
    expect(() => composable.workspaceIdOrThrow()).toThrow('Select a workspace')
  })

  it('workspaceIdOrThrow returns id when set', () => {
    const ws = useWorkspaceStore()
    ws.setActiveWorkspaceId('ws-1')
    const composable = useBulkImport()
    expect(composable.workspaceIdOrThrow()).toBe('ws-1')
  })
})
