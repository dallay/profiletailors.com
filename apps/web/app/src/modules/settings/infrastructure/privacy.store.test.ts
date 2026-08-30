import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

const mockUser = {
  principalId: 'user-1',
  email: 'test@test.com',
  username: 'testuser',
  emailStatus: 'VERIFIED',
  displayIdentity: 'testuser',
}
const mockAccessToken = 'mock-token-1'
const mockWorkspaceId = 'ws-1'

vi.mock('@shared/i18n', () => ({
  default: { global: { locale: { value: 'en' } } },
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

let mockApiFetch = vi.fn()

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({
    apiFetch: mockApiFetch,
    isAuthenticated: true,
    accessToken: mockAccessToken,
    user: mockUser,
  }),
}))

vi.mock('@modules/workspace/infrastructure/workspace.store', () => ({
  useWorkspaceStore: () => ({
    activeWorkspaceId: mockWorkspaceId,
  }),
}))

describe('privacy store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockApiFetch = vi.fn()
  })

  it('submitRequest calls apiFetch POST and adds the returned request', async () => {
    const mockResponse = {
      id: 'req-1',
      status: 'PENDING',
      message: 'Request submitted successfully',
      oldValues: null,
      downloadUrl: null,
    }
    mockApiFetch.mockResolvedValue(mockResponse)

    const { usePrivacyStore } = await import('./privacy.store')
    const store = usePrivacyStore()

    const result = await store.submitRequest({ type: 'ACCESS' })

    expect(mockApiFetch).toHaveBeenCalledWith('/api/v1/privacy/requests', {
      method: 'POST',
      body: JSON.stringify({ type: 'ACCESS' }),
      workspaceScoped: true,
    })
    expect(result.id).toBe('req-1')
    expect(result.status).toBe('PENDING')
    expect(result.type).toBe('ACCESS')
    expect(store.requests).toHaveLength(1)
    expect(store.requests[0]).toEqual(result)
  })

  it('submitRequest with CORRECTION includes correctionData', async () => {
    const mockResponse = {
      id: 'req-2',
      status: 'PENDING',
      message: 'Request submitted successfully',
      oldValues: null,
      downloadUrl: null,
    }
    mockApiFetch.mockResolvedValue(mockResponse)

    const { usePrivacyStore } = await import('./privacy.store')
    const store = usePrivacyStore()

    await store.submitRequest({
      type: 'CORRECTION',
      notes: 'Please update my email',
      correctionData: { newEmail: 'new@test.com', newUsername: null },
    })

    expect(mockApiFetch).toHaveBeenCalledWith('/api/v1/privacy/requests', {
      method: 'POST',
      body: JSON.stringify({
        type: 'CORRECTION',
        notes: 'Please update my email',
        newEmail: 'new@test.com',
        newUsername: null,
      }),
      workspaceScoped: true,
    })
    expect(store.requests).toHaveLength(1)
  })

  it('fetchRequests calls apiFetch GET and populates requests list', async () => {
    const mockResponse = {
      requests: [
        {
          id: 'req-1',
          type: 'ACCESS',
          status: 'COMPLETED',
          result: { ref: 'export-file-1.zip' },
          createdAt: '2026-07-18T10:00:00Z',
          updatedAt: '2026-07-19T10:00:00Z',
        },
        {
          id: 'req-2',
          type: 'DELETION',
          status: 'REJECTED',
          result: null,
          createdAt: '2026-07-17T10:00:00Z',
          updatedAt: '2026-07-18T10:00:00Z',
        },
      ],
      total: 2,
      page: 1,
      perPage: 2,
    }
    mockApiFetch.mockResolvedValue(mockResponse)

    const { usePrivacyStore } = await import('./privacy.store')
    const store = usePrivacyStore()

    await store.fetchRequests()

    expect(mockApiFetch).toHaveBeenCalledWith('/api/v1/privacy/requests', { workspaceScoped: true })
    expect(store.requests).toHaveLength(2)
    expect(store.requests[0]!.type).toBe('ACCESS')
    expect(store.requests[0]!.resultRef).toBe('export-file-1.zip')
    expect(store.requests[1]!.status).toBe('REJECTED')
  })

  it('fetchRequest calls apiFetch GET with id and sets currentRequest', async () => {
    const mockResponse = {
      id: 'req-1',
      type: 'EXPORT',
      status: 'PROCESSING',
      result: null,
      createdAt: '2026-07-19T10:00:00Z',
      updatedAt: '2026-07-19T10:00:00Z',
    }
    mockApiFetch.mockResolvedValue(mockResponse)

    const { usePrivacyStore } = await import('./privacy.store')
    const store = usePrivacyStore()

    const result = await store.fetchRequest('req-1')

    expect(mockApiFetch).toHaveBeenCalledWith('/api/v1/privacy/requests/req-1', {
      workspaceScoped: true,
    })
    expect(result.id).toBe('req-1')
    expect(result.type).toBe('EXPORT')
    expect(result.status).toBe('PROCESSING')
    expect(store.currentRequest).toEqual(result)
  })

  it('handles API error in submitRequest gracefully', async () => {
    mockApiFetch.mockRejectedValue(new Error('Network error'))

    const { usePrivacyStore } = await import('./privacy.store')
    const store = usePrivacyStore()

    await expect(store.submitRequest({ type: 'ACCESS' })).rejects.toThrow('Network error')
    expect(store.error).toBe('Network error')
  })

  it('clears error after successful request', async () => {
    mockApiFetch.mockRejectedValueOnce(new Error('Network error'))
    mockApiFetch.mockResolvedValueOnce({
      id: 'req-1',
      workspaceId: mockWorkspaceId,
      type: 'ACCESS',
      status: 'PENDING',
      notes: null,
      correctionData: null,
      resultRef: null,
      createdAt: '2026-07-19T10:00:00Z',
      updatedAt: '2026-07-19T10:00:00Z',
    })

    const { usePrivacyStore } = await import('./privacy.store')
    const store = usePrivacyStore()

    await expect(store.submitRequest({ type: 'ACCESS' })).rejects.toThrow('Network error')
    expect(store.error).toBe('Network error')

    await store.submitRequest({ type: 'ACCESS' })
    expect(store.error).toBeNull()
  })

  it('initial state has empty requests, null error, null currentRequest', async () => {
    const { usePrivacyStore } = await import('./privacy.store')
    const store = usePrivacyStore()

    expect(store.requests).toEqual([])
    expect(store.error).toBeNull()
    expect(store.currentRequest).toBeNull()
    expect(store.loading).toBe(false)
  })
})
