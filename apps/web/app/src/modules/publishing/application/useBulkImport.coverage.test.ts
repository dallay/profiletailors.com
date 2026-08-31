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

describe('useBulkImport coverage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('validate sets error and rethrows on failure and resets isValidating', async () => {
    const store = usePublishingStore()
    const composable = useBulkImport()
    vi.spyOn(store, 'validateBulk').mockRejectedValue(new Error('validate boom'))
    await expect(composable.validate('csv')).rejects.toThrow('validate boom')
    expect(composable.error.value).toBe('validate boom')
    expect(composable.isValidating.value).toBe(false)
  })

  it('validate sets generic error when non-Error thrown', async () => {
    const store = usePublishingStore()
    const composable = useBulkImport()
    vi.spyOn(store, 'validateBulk').mockRejectedValue('string error' as never)
    await expect(composable.validate('csv')).rejects.toBe('string error')
    expect(composable.error.value).toBe('Validate failed')
  })

  it('schedule sets error and rethrows and resets isScheduling', async () => {
    const store = usePublishingStore()
    const composable = useBulkImport()
    vi.spyOn(store, 'scheduleBulk').mockRejectedValue(new Error('schedule boom'))
    await expect(composable.schedule('csv')).rejects.toThrow('schedule boom')
    expect(composable.error.value).toBe('schedule boom')
    expect(composable.isScheduling.value).toBe(false)
  })

  it('schedule generic error fallback', async () => {
    const store = usePublishingStore()
    const composable = useBulkImport()
    vi.spyOn(store, 'scheduleBulk').mockRejectedValue('oops' as never)
    await expect(composable.schedule('csv')).rejects.toBe('oops')
    expect(composable.error.value).toBe('Schedule failed')
  })

  it('hasValidationErrors false and invalidRows empty when no validation', () => {
    const composable = useBulkImport()
    expect(composable.hasValidationErrors.value).toBe(false)
    expect(composable.invalidRows.value).toEqual([])
  })

  it('hasValidationErrors false when all rows valid', async () => {
    const store = usePublishingStore()
    const composable = useBulkImport()
    vi.spyOn(store, 'validateBulk').mockResolvedValue({
      rows: [{ rowIndex: 0, status: 'VALID', errors: [] }],
    })
    await composable.validate('csv')
    expect(composable.hasValidationErrors.value).toBe(false)
    expect(composable.invalidRows.value).toEqual([])
  })

  it('fetchJob toggles isPolling and sets jobResult', async () => {
    const store = usePublishingStore()
    const composable = useBulkImport()
    const job = {
      jobId: 'j1',
      status: 'SCHEDULED' as const,
      totalRows: 1,
      scheduledCount: 1,
      failedCount: 0,
      rows: [],
    }
    vi.spyOn(store, 'fetchBulkJob').mockResolvedValue(job)
    const promise = composable.fetchJob('j1')
    expect(composable.isPolling.value).toBe(true)
    const result = await promise
    expect(result).toEqual(job)
    expect(composable.jobResult.value).toEqual(job)
    expect(composable.isPolling.value).toBe(false)
  })

  it('loadTemplates delegates and sets templates', async () => {
    const store = usePublishingStore()
    const composable = useBulkImport()
    const res = { templates: [{ id: 't1', name: 'Template', description: 'desc' }] }
    vi.spyOn(store, 'fetchBulkTemplates').mockResolvedValue(res as never)
    const result = await composable.loadTemplates()
    expect(result).toEqual(res)
    expect(composable.templates.value).toEqual(res)
  })

  it('downloadTemplateCsv delegates', async () => {
    const store = usePublishingStore()
    const composable = useBulkImport()
    vi.spyOn(store, 'fetchBulkTemplateCsv').mockResolvedValue('csv-data' as never)
    const result = await composable.downloadTemplateCsv('t1')
    expect(result).toBe('csv-data')
  })

  it('pollJob returns immediately when first fetch is terminal SCHEDULED', async () => {
    const composable = useBulkImport()
    const store = usePublishingStore()
    vi.spyOn(store, 'fetchBulkJob').mockResolvedValue({
      jobId: 'j1',
      status: 'SCHEDULED',
      totalRows: 1,
      scheduledCount: 1,
      failedCount: 0,
      rows: [],
    })
    const result = await composable.pollJob('j1', 10, 5)
    expect(result.status).toBe('SCHEDULED')
  })

  it('pollJob returns when status PARTIAL', async () => {
    const composable = useBulkImport()
    const store = usePublishingStore()
    vi.spyOn(store, 'fetchBulkJob').mockResolvedValue({
      jobId: 'j1',
      status: 'PARTIAL',
      totalRows: 2,
      scheduledCount: 1,
      failedCount: 1,
      rows: [],
    })
    const result = await composable.pollJob('j1', 10, 2)
    expect(result.status).toBe('PARTIAL')
  })

  it('pollJob returns when status FAILED', async () => {
    const composable = useBulkImport()
    const store = usePublishingStore()
    vi.spyOn(store, 'fetchBulkJob').mockResolvedValue({
      jobId: 'j1',
      status: 'FAILED',
      totalRows: 1,
      scheduledCount: 0,
      failedCount: 1,
      rows: [],
    })
    const result = await composable.pollJob('j1', 10, 2)
    expect(result.status).toBe('FAILED')
  })

  it('pollJob polls with interval until terminal and respects attempts', async () => {
    vi.useFakeTimers()
    try {
      const composable = useBulkImport()
      const store = usePublishingStore()
      const spy = vi
        .spyOn(store, 'fetchBulkJob')
        .mockResolvedValueOnce({
          jobId: 'j1',
          status: 'SCHEDULING',
          totalRows: 1,
          scheduledCount: 0,
          failedCount: 0,
          rows: [],
        })
        .mockResolvedValueOnce({
          jobId: 'j1',
          status: 'SCHEDULING',
          totalRows: 1,
          scheduledCount: 0,
          failedCount: 0,
          rows: [],
        })
        .mockResolvedValueOnce({
          jobId: 'j1',
          status: 'SCHEDULED',
          totalRows: 1,
          scheduledCount: 1,
          failedCount: 0,
          rows: [],
        })
      const promise = composable.pollJob('j1', 15, 3)
      await vi.advanceTimersByTimeAsync(15)
      await vi.advanceTimersByTimeAsync(15)
      const result = await promise
      expect(result.status).toBe('SCHEDULED')
      expect(spy).toHaveBeenCalledTimes(3)
    } finally {
      vi.useRealTimers()
    }
  })

  it('pollJob with maxAttempts 0 falls back to 1 and throws if no result', async () => {
    const composable = useBulkImport()
    const store = usePublishingStore()
    vi.spyOn(store, 'fetchBulkJob').mockResolvedValue({
      jobId: 'j1',
      status: 'SCHEDULING',
      totalRows: 1,
      scheduledCount: 0,
      failedCount: 0,
      rows: [],
    })
    await expect(composable.pollJob('j1', 10, 0)).resolves.toEqual(
      expect.objectContaining({ jobId: 'j1' }),
    )
  })

  it('pollJob with negative interval falls back to default', async () => {
    vi.useFakeTimers()
    try {
      const composable = useBulkImport()
      const store = usePublishingStore()
      vi.spyOn(store, 'fetchBulkJob')
        .mockResolvedValueOnce({
          jobId: 'j1',
          status: 'SCHEDULING',
          totalRows: 1,
          scheduledCount: 0,
          failedCount: 0,
          rows: [],
        })
        .mockResolvedValueOnce({
          jobId: 'j1',
          status: 'SCHEDULED',
          totalRows: 1,
          scheduledCount: 1,
          failedCount: 0,
          rows: [],
        })
      const promise = composable.pollJob('j1', -5, 2)
      await vi.advanceTimersByTimeAsync(1500)
      const result = await promise
      expect(result.status).toBe('SCHEDULED')
    } finally {
      vi.useRealTimers()
    }
  })

  it('pollJob returns last result when polling exhausts without terminal status', async () => {
    const composable = useBulkImport()
    const store = usePublishingStore()
    vi.spyOn(store, 'fetchBulkJob').mockResolvedValue({
      jobId: 'j1',
      status: 'SCHEDULING',
      totalRows: 1,
      scheduledCount: 0,
      failedCount: 0,
      rows: [],
    } as never)
    const result = await composable.pollJob('j1', 5, 1)
    expect(result.status).toBe('SCHEDULING')
    expect(composable.jobResult.value?.status).toBe('SCHEDULING')
  })

  it('validate clears error on success after previous failure', async () => {
    const store = usePublishingStore()
    const composable = useBulkImport()
    vi.spyOn(store, 'validateBulk').mockRejectedValueOnce(new Error('first fail'))
    await expect(composable.validate('csv')).rejects.toThrow()
    vi.spyOn(store, 'validateBulk').mockResolvedValueOnce({
      rows: [{ rowIndex: 0, status: 'VALID', errors: [] }],
    })
    const result = await composable.validate('csv')
    expect(result.rows[0]?.status).toBe('VALID')
    expect(composable.error.value).toBeNull()
  })

  it('workspaceIdOrThrow returns id when set via workspace store', () => {
    const ws = useWorkspaceStore()
    ws.setActiveWorkspaceId('ws-cover')
    const composable = useBulkImport()
    expect(composable.workspaceIdOrThrow()).toBe('ws-cover')
  })
})
