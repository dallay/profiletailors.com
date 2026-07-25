import { ref } from 'vue'
import { defineStore } from 'pinia'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'

// ---------------------------------------------------------------------------
// Types — DSAR Request (frontend model)
// ---------------------------------------------------------------------------

export type DsarRequestType = 'ACCESS' | 'EXPORT' | 'CORRECTION' | 'DELETION'
export type DsarRequestStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'REJECTED' | 'FAILED'

export interface CorrectionData {
  newEmail: string | null
  newUsername: string | null
}

export interface DsarRequest {
  id: string
  workspaceId: string
  type: DsarRequestType
  status: DsarRequestStatus
  notes: string | null
  correctionData: CorrectionData | null
  resultRef: string | null
  createdAt: string
  updatedAt: string
}

export interface SubmitDsarRequestPayload {
  type: DsarRequestType
  notes?: string
  correctionData?: CorrectionData
}

export interface DsarRequestListResponse {
  requests: DsarRequest[]
}

// ---------------------------------------------------------------------------
// Backend DTO Types
// ---------------------------------------------------------------------------

export interface BackendPrivacyRequest {
  id: string
  type: DsarRequestType
  status: DsarRequestStatus
  result: { ref: string | null } | null
  createdAt: string
  updatedAt: string
}

export interface BackendPrivacyRequestListResponse {
  requests: BackendPrivacyRequest[]
  total: number
  page: number
  perPage: number
}

export interface SubmitPrivacyResponse {
  id: string
  status: string
  message: string
  oldValues: Record<string, string> | null
  downloadUrl: string | null
}

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------

export const usePrivacyStore = defineStore('privacy', () => {
  const auth = useAuthStore()

  const requests = ref<DsarRequest[]>([])
  const currentRequest = ref<DsarRequest | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  // -------------------------------------------------------------------------
  // Actions
  // -------------------------------------------------------------------------

  /**
   * Submit a new DSAR request.
   * On success, prepends the returned request to the local list.
   *
   * @param payload - The request type, optional notes, and optional correction data
   * @returns The created DsarRequest
   */
  async function submitRequest(payload: SubmitDsarRequestPayload): Promise<DsarRequest> {
    loading.value = true
    error.value = null

    try {
      const body: Record<string, unknown> = { type: payload.type }
      if (payload.notes) {
        body.notes = payload.notes
      }
      if (payload.correctionData) {
        body.newEmail = payload.correctionData.newEmail
        body.newUsername = payload.correctionData.newUsername
      }

      const result = await auth.apiFetch<SubmitPrivacyResponse>('/api/v1/privacy/requests', {
        method: 'POST',
        body: JSON.stringify(body),
        workspaceScoped: true,
      })

      const newRequest: DsarRequest = {
        id: result.id,
        workspaceId: '',
        type: payload.type,
        status: result.status as DsarRequestStatus,
        notes: payload.notes || null,
        correctionData: payload.correctionData || null,
        resultRef: result.downloadUrl || null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }

      requests.value.unshift(newRequest)
      return newRequest
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to submit request.'
      error.value = message
      throw err
    } finally {
      loading.value = false
    }
  }

  /**
   * Fetch all DSAR requests for the current workspace.
   */
  async function fetchRequests(): Promise<DsarRequest[]> {
    loading.value = true
    error.value = null

    try {
      const data = await auth.apiFetch<BackendPrivacyRequestListResponse>('/api/v1/privacy/requests', {
        workspaceScoped: true,
      })
      requests.value = data.requests.map((r) => ({
        id: r.id,
        workspaceId: '',
        type: r.type,
        status: r.status,
        notes: null,
        correctionData: null,
        resultRef: r.result?.ref || null,
        createdAt: r.createdAt,
        updatedAt: r.updatedAt,
      }))
      return requests.value
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load requests.'
      error.value = message
      throw err
    } finally {
      loading.value = false
    }
  }

  /**
   * Fetch a single DSAR request by its ID.
   *
   * @param id - The request ID
   * @returns The DsarRequest
   */
  async function fetchRequest(id: string): Promise<DsarRequest> {
    loading.value = true
    error.value = null

    try {
      const result = await auth.apiFetch<BackendPrivacyRequest>(`/api/v1/privacy/requests/${id}`, {
        workspaceScoped: true,
      })
      const mapped: DsarRequest = {
        id: result.id,
        workspaceId: '',
        type: result.type,
        status: result.status,
        notes: null,
        correctionData: null,
        resultRef: result.result?.ref || null,
        createdAt: result.createdAt,
        updatedAt: result.updatedAt,
      }
      currentRequest.value = mapped
      return mapped
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load request.'
      error.value = message
      throw err
    } finally {
      loading.value = false
    }
  }

  // -------------------------------------------------------------------------
  // Public surface
  // -------------------------------------------------------------------------

  return {
    // State
    requests,
    currentRequest,
    loading,
    error,
    // Actions
    submitRequest,
    fetchRequests,
    fetchRequest,
  }
})
