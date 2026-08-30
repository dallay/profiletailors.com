import { ref } from 'vue'
import { defineStore } from 'pinia'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'

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

type PrivacyRequestDto = Record<string, unknown> & {
  id?: unknown
  workspaceId?: unknown
  type?: unknown
  status?: unknown
  notes?: unknown
  correctionData?: unknown
  result?: unknown
  resultRef?: unknown
  createdAt?: unknown
  updatedAt?: unknown
}

type PrivacyListDto = {
  requests?: PrivacyRequestDto[]
}

type PrivacySubmitResponseDto = {
  id: string
  status: string
  downloadUrl?: string | null
}

function mapStatusDtoToRequest(dto: PrivacyRequestDto): DsarRequest {
  const result = dto.result as Record<string, unknown> | null | undefined
  const resultRef =
    (result?.ref as string | undefined) ?? (dto.resultRef as string | undefined) ?? null
  return {
    id: dto.id as string,
    workspaceId: (dto.workspaceId as string) || '',
    type: dto.type as DsarRequestType,
    status: dto.status as DsarRequestStatus,
    notes: (dto.notes as string | null) || null,
    correctionData: (dto.correctionData as CorrectionData | null) || null,
    resultRef,
    createdAt: dto.createdAt as string,
    updatedAt: dto.updatedAt as string,
  }
}

export const usePrivacyStore = defineStore('privacy', () => {
  const auth = useAuthStore()

  const requests = ref<DsarRequest[]>([])
  const currentRequest = ref<DsarRequest | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

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

      const response = await auth.apiFetch<PrivacySubmitResponseDto>('/api/v1/privacy/requests', {
        method: 'POST',
        body: JSON.stringify(body),
        workspaceScoped: true,
      })

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

  async function fetchRequests(): Promise<DsarRequest[]> {
    loading.value = true
    error.value = null

    try {
      const data = await auth.apiFetch<PrivacyListDto>('/api/v1/privacy/requests', {
        workspaceScoped: true,
      })
      const mapped = (data.requests || []).map((req) =>
        mapStatusDtoToRequest(req as PrivacyRequestDto),
      )
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

  async function fetchRequest(id: string): Promise<DsarRequest> {
    loading.value = true
    error.value = null

    try {
      const result = await auth.apiFetch<PrivacyRequestDto>(`/api/v1/privacy/requests/${id}`, {
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

  return {
    requests,
    currentRequest,
    loading,
    error,
    submitRequest,
    fetchRequests,
    fetchRequest,
  }
})
