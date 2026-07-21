// ---------------------------------------------------------------------------
// Types — Governance API
// ---------------------------------------------------------------------------

import { useAuthStore } from '@modules/auth/infrastructure/auth.store'

export type TakedownReportStatus = 'REPORTED' | 'APPROVED' | 'DISMISSED'

export interface TakedownReportResponse {
  reportId: string
  workspaceId: string
  assetId: string
  reporterEmail: string
  reason: string
  mediaReferenceUrl?: string
  status: TakedownReportStatus
  rejectionReason?: string
  createdAt: string
  reviewedAt?: string
  reviewedBy?: string
}

export interface ReportTakedownRequest {
  assetId: string
  reason: string
  reporterEmail?: string
  mediaReferenceUrl?: string
}

export interface ReviewTakedownRequest {
  rejectionReason: string
}

// ---------------------------------------------------------------------------
// API functions
// ---------------------------------------------------------------------------

type GovernanceApiErrorShape = Error & {
  title: string
  detail: string
  status: number
  errorCode?: string
}

function governanceApiError(
  title: string,
  detail: string,
  status: number,
  errorCode?: string,
): GovernanceApiErrorShape {
  return Object.assign(new Error(title), {
    title,
    detail,
    status,
    errorCode,
  }) as GovernanceApiErrorShape
}

/**
 * Reports a media asset for copyright/DMCA takedown.
 * POST /api/governance/takedown/reports
 */
export async function reportTakedown(
  request: ReportTakedownRequest,
): Promise<TakedownReportResponse> {
  const auth = useAuthStore()

  if (!auth.isAuthenticated) {
    throw governanceApiError('Not authenticated', 'You must be signed in.', 401)
  }

  return auth.apiFetch<TakedownReportResponse>('/api/governance/takedown/reports', {
    method: 'POST',
    workspaceScoped: true,
    body: JSON.stringify(request),
  })
}

/**
 * Approves a pending takedown report.
 * POST /api/governance/takedown/reports/{reportId}/approve
 */
export async function approveTakedown(reportId: string): Promise<TakedownReportResponse> {
  const auth = useAuthStore()

  if (!auth.isAuthenticated) {
    throw governanceApiError('Not authenticated', 'You must be signed in.', 401)
  }

  return auth.apiFetch<TakedownReportResponse>(
    `/api/governance/takedown/reports/${encodeURIComponent(reportId)}/approve`,
    {
      method: 'POST',
      workspaceScoped: true,
    },
  )
}

/**
 * Rejects/dismisses a pending takedown report.
 * POST /api/governance/takedown/reports/{reportId}/reject
 */
export async function rejectTakedown(
  reportId: string,
  request: ReviewTakedownRequest,
): Promise<TakedownReportResponse> {
  const auth = useAuthStore()

  if (!auth.isAuthenticated) {
    throw governanceApiError('Not authenticated', 'You must be signed in.', 401)
  }

  return auth.apiFetch<TakedownReportResponse>(
    `/api/governance/takedown/reports/${encodeURIComponent(reportId)}/reject`,
    {
      method: 'POST',
      workspaceScoped: true,
      body: JSON.stringify(request),
    },
  )
}

/**
 * Lists takedown reports for the current workspace.
 * GET /api/governance/takedown/reports?status={status}
 */
export async function listTakedownReports(
  status?: TakedownReportStatus,
): Promise<TakedownReportResponse[]> {
  const auth = useAuthStore()

  if (!auth.isAuthenticated) {
    throw governanceApiError('Not authenticated', 'You must be signed in.', 401)
  }

  const url = status
    ? `/api/governance/takedown/reports?status=${encodeURIComponent(status)}`
    : '/api/governance/takedown/reports'

  return auth.apiFetch<TakedownReportResponse[]>(url, {
    method: 'GET',
    workspaceScoped: true,
  })
}
