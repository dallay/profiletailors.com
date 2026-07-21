import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { TakedownReportResponse } from './governance-api'

// ---------------------------------------------------------------------------
// Mocks — hoisted so vi.mock factories can reference them
// ---------------------------------------------------------------------------

const { mockApiFetch, mockAuthState } = vi.hoisted(() => ({
  mockApiFetch: vi.fn(),
  mockAuthState: { isAuthenticated: true },
}))

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({
    isAuthenticated: mockAuthState.isAuthenticated,
    apiFetch: mockApiFetch,
  }),
}))

// Must use vi.hoisted for dynamic import to work in mocks
const governanceApi = await import('./governance-api')
const { reportTakedown, approveTakedown, rejectTakedown, listTakedownReports } = governanceApi

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const defaultReport: TakedownReportResponse = {
  reportId: 'rpt-1',
  workspaceId: 'ws-1',
  assetId: 'ast-1',
  reporterEmail: 'reporter@example.com',
  reason: 'Copyright infringement',
  status: 'REPORTED',
  createdAt: '2026-07-21T10:00:00Z',
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('governance-api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockAuthState.isAuthenticated = true
  })

  // -----------------------------------------------------------------------
  // reportTakedown
  // -----------------------------------------------------------------------
  describe('reportTakedown', () => {
    it('sends POST with workspace scope and returns the response', async () => {
      mockApiFetch.mockResolvedValue(defaultReport)

      const result = await reportTakedown({
        assetId: 'ast-1',
        reason: 'Copyright infringement',
      })

      expect(mockApiFetch).toHaveBeenCalledWith(
        '/api/governance/takedown/reports',
        expect.objectContaining({ method: 'POST', workspaceScoped: true }),
      )
      expect(result).toEqual(defaultReport)
    })

    it('includes reporterEmail and mediaReferenceUrl when provided', async () => {
      mockApiFetch.mockResolvedValue(defaultReport)

      await reportTakedown({
        assetId: 'ast-1',
        reason: 'Copyright infringement',
        reporterEmail: 'author@example.com',
        mediaReferenceUrl: 'https://example.com/original',
      })

      expect(mockApiFetch).toHaveBeenCalledTimes(1)
      const body = JSON.parse(mockApiFetch.mock.calls[0]![1]!.body as string)
      expect(body).toMatchObject({
        assetId: 'ast-1',
        reason: 'Copyright infringement',
        reporterEmail: 'author@example.com',
        mediaReferenceUrl: 'https://example.com/original',
      })
    })

    it('serializes empty mediaReferenceUrl as empty string (component responsibility to trim)', async () => {
      mockApiFetch.mockResolvedValue(defaultReport)

      await reportTakedown({
        assetId: 'ast-1',
        reason: 'Copyright issue',
        mediaReferenceUrl: '',
      })

      expect(mockApiFetch).toHaveBeenCalledTimes(1)
      const body = JSON.parse(mockApiFetch.mock.calls[0]![1]!.body as string)
      // The API layer does not trim — that's the component's job
      expect(body).toHaveProperty('mediaReferenceUrl', '')
    })

    it('throws 401 error when not authenticated', async () => {
      mockAuthState.isAuthenticated = false

      await expect(reportTakedown({ assetId: 'ast-1', reason: 'Whatever' })).rejects.toMatchObject({
        status: 401,
      })

      expect(mockApiFetch).not.toHaveBeenCalled()
    })
  })

  // -----------------------------------------------------------------------
  // approveTakedown
  // -----------------------------------------------------------------------
  describe('approveTakedown', () => {
    it('sends POST with reportId in path', async () => {
      const approved = { ...defaultReport, status: 'APPROVED' as const }
      mockApiFetch.mockResolvedValue(approved)

      const result = await approveTakedown('rpt-1')

      expect(mockApiFetch).toHaveBeenCalledWith(
        '/api/governance/takedown/reports/rpt-1/approve',
        expect.objectContaining({ method: 'POST', workspaceScoped: true }),
      )
      expect(result).toEqual(approved)
    })

    it('encodes the reportId', async () => {
      mockApiFetch.mockResolvedValue(defaultReport)

      await approveTakedown('rpt with spaces')

      expect(mockApiFetch).toHaveBeenCalledWith(
        '/api/governance/takedown/reports/rpt%20with%20spaces/approve',
        expect.anything(),
      )
    })

    it('throws 401 error when not authenticated', async () => {
      mockAuthState.isAuthenticated = false

      await expect(approveTakedown('rpt-1')).rejects.toMatchObject({ status: 401 })
      expect(mockApiFetch).not.toHaveBeenCalled()
    })
  })

  // -----------------------------------------------------------------------
  // rejectTakedown
  // -----------------------------------------------------------------------
  describe('rejectTakedown', () => {
    it('sends POST with rejection reason', async () => {
      const dismissed = {
        ...defaultReport,
        status: 'DISMISSED' as const,
        rejectionReason: 'Not valid',
      }
      mockApiFetch.mockResolvedValue(dismissed)

      const result = await rejectTakedown('rpt-1', { rejectionReason: 'Not valid' })

      expect(mockApiFetch).toHaveBeenCalledWith(
        '/api/governance/takedown/reports/rpt-1/reject',
        expect.objectContaining({ method: 'POST', workspaceScoped: true }),
      )
      expect(mockApiFetch).toHaveBeenCalledTimes(1)
      const body = JSON.parse(mockApiFetch.mock.calls[0]![1]!.body as string)
      expect(body).toEqual({ rejectionReason: 'Not valid' })
      expect(result).toEqual(dismissed)
    })

    it('throws 401 error when not authenticated', async () => {
      mockAuthState.isAuthenticated = false

      await expect(rejectTakedown('rpt-1', { rejectionReason: 'No' })).rejects.toMatchObject({
        status: 401,
      })
      expect(mockApiFetch).not.toHaveBeenCalled()
    })
  })

  // -----------------------------------------------------------------------
  // listTakedownReports
  // -----------------------------------------------------------------------
  describe('listTakedownReports', () => {
    it('fetches without status filter', async () => {
      mockApiFetch.mockResolvedValue([defaultReport])

      const result = await listTakedownReports()

      expect(mockApiFetch).toHaveBeenCalledWith(
        '/api/governance/takedown/reports',
        expect.objectContaining({ method: 'GET', workspaceScoped: true }),
      )
      expect(result).toEqual([defaultReport])
    })

    it('appends status query parameter', async () => {
      mockApiFetch.mockResolvedValue([])

      await listTakedownReports('APPROVED')

      expect(mockApiFetch).toHaveBeenCalledWith(
        '/api/governance/takedown/reports?status=APPROVED',
        expect.anything(),
      )
    })

    it('throws 401 error when not authenticated', async () => {
      mockAuthState.isAuthenticated = false

      await expect(listTakedownReports()).rejects.toMatchObject({ status: 401 })
      expect(mockApiFetch).not.toHaveBeenCalled()
    })
  })
})
