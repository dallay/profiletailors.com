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
  reportId: '68f1061d-8371-5dd2-91ec-672c02a62e4a',
  workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
  assetId: '3418af10-5c90-5f8e-b0e5-95cb97592c2d',
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
        assetId: '3418af10-5c90-5f8e-b0e5-95cb97592c2d',
        reason: 'Copyright infringement',
      })

      expect(mockApiFetch).toHaveBeenCalledWith(
        '/api/governance/takedown/reports',
        expect.objectContaining({ method: 'POST', workspaceScoped: true }),
      )
      expect(result).toEqual(defaultReport)
    })

    it('includes mediaReferenceUrl when provided', async () => {
      mockApiFetch.mockResolvedValue(defaultReport)

      await reportTakedown({
        assetId: '3418af10-5c90-5f8e-b0e5-95cb97592c2d',
        reason: 'Copyright infringement',
        mediaReferenceUrl: 'https://example.com/original',
      })

      expect(mockApiFetch).toHaveBeenCalledTimes(1)
      const body = JSON.parse(mockApiFetch.mock.calls[0]![1]!.body as string)
      expect(body).toMatchObject({
        assetId: '3418af10-5c90-5f8e-b0e5-95cb97592c2d',
        reason: 'Copyright infringement',
        mediaReferenceUrl: 'https://example.com/original',
      })
      // reporterEmail is derived from the authenticated principal on the server side
      expect(body).not.toHaveProperty('reporterEmail')
    })

    it('serializes empty mediaReferenceUrl as empty string (component responsibility to trim)', async () => {
      mockApiFetch.mockResolvedValue(defaultReport)

      await reportTakedown({
        assetId: '3418af10-5c90-5f8e-b0e5-95cb97592c2d',
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

      await expect(
        reportTakedown({ assetId: '3418af10-5c90-5f8e-b0e5-95cb97592c2d', reason: 'Whatever' }),
      ).rejects.toMatchObject({
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

      const result = await approveTakedown('68f1061d-8371-5dd2-91ec-672c02a62e4a')

      expect(mockApiFetch).toHaveBeenCalledWith(
        '/api/governance/takedown/reports/68f1061d-8371-5dd2-91ec-672c02a62e4a/approve',
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

      await expect(approveTakedown('68f1061d-8371-5dd2-91ec-672c02a62e4a')).rejects.toMatchObject({
        status: 401,
      })
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

      const result = await rejectTakedown('68f1061d-8371-5dd2-91ec-672c02a62e4a', {
        rejectionReason: 'Not valid',
      })

      expect(mockApiFetch).toHaveBeenCalledWith(
        '/api/governance/takedown/reports/68f1061d-8371-5dd2-91ec-672c02a62e4a/reject',
        expect.objectContaining({ method: 'POST', workspaceScoped: true }),
      )
      expect(mockApiFetch).toHaveBeenCalledTimes(1)
      const body = JSON.parse(mockApiFetch.mock.calls[0]![1]!.body as string)
      expect(body).toEqual({ rejectionReason: 'Not valid' })
      expect(result).toEqual(dismissed)
    })

    it('throws 401 error when not authenticated', async () => {
      mockAuthState.isAuthenticated = false

      await expect(
        rejectTakedown('68f1061d-8371-5dd2-91ec-672c02a62e4a', { rejectionReason: 'No' }),
      ).rejects.toMatchObject({
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
