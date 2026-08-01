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

function mapStatusDtoToRequest(dto: any): DsarRequest {
  return {
    id: dto.id,
    workspaceId: dto.workspaceId || '',
    type: dto.type as DsarRequestType,
    status: dto.status as DsarRequestStatus,
    notes: dto.notes || null,
    correctionData: dto.correctionData || null,
    resultRef: dto.result?.ref || dto.resultRef || null,
    createdAt: dto.createdAt,
    updatedAt: dto.updatedAt,
  }
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

      const response = await auth.apiFetch<any>('/api/v1/privacy/requests', {
        method: 'POST',
        body: JSON.stringify(body),
        workspaceScoped: true,
      })

      // Map backend response (SubmitPrivacyResponseDto) + payload to DsarRequest
      const result: DsarRequest = {
        id: response.id,
        workspaceId: '',
        type: payload.type,
        status: response.status as DsarRequestStatus,
        notes: payload.notes || null,
        correctionData: payload.correctionData || null,
        resultRef: response.downloadUrl || null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }

      requests.value.unshift(result)
      return result
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
      const data = await auth.apiFetch<any>('/api/v1/privacy/requests', {
        workspaceScoped: true,
      })
      const mapped = (data.requests || []).map((req: any) => mapStatusDtoToRequest(req))
      requests.value = mapped
      return mapped
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
      const result = await auth.apiFetch<any>(`/api/v1/privacy/requests/${id}`, {
        workspaceScoped: true,
      })
      const mapped = mapStatusDtoToRequest(result)
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
