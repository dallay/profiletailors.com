import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useMediaStore } from './media.store'

// ---------------------------------------------------------------------------
// Mock auth-api
// ---------------------------------------------------------------------------
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
  refreshSession: vi.fn().mockResolvedValue(null),
  getCurrentUserProfile: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  register: vi.fn(),
  logoutSession: vi.fn(),
}))

// ---------------------------------------------------------------------------
// Mock media-api
// ---------------------------------------------------------------------------
const mockPutAsset = vi.fn()
const mockListAssets = vi.fn()
const mockGetAsset = vi.fn()
const mockDeleteAsset = vi.fn()
const mockWorkspace = { activeWorkspaceId: '2b255986-a5f6-5b57-85d9-1f11eb5d96bc' as string | null }

vi.mock('@modules/media/services/media-api', () => ({
  putAsset: (...args: unknown[]) => mockPutAsset(...args),
  listAssets: (...args: unknown[]) => mockListAssets(...args),
  getAsset: (...args: unknown[]) => mockGetAsset(...args),
  deleteAsset: (...args: unknown[]) => mockDeleteAsset(...args),
}))

// ---------------------------------------------------------------------------
// Mock auth store
// ---------------------------------------------------------------------------
vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({
    isAuthenticated: true,
    accessToken: { value: 'fake-token' },
    workspace: { activeWorkspaceId: '2b255986-a5f6-5b57-85d9-1f11eb5d96bc' },
  }),
}))

// ---------------------------------------------------------------------------
// Mock workspace store
// ---------------------------------------------------------------------------
vi.mock('@modules/workspace/infrastructure/workspace.store', () => ({
  useWorkspaceStore: () => mockWorkspace,
}))

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
const readyAsset = (assetId: string) => ({
  assetId,
  workspaceId: '2b255986-a5f6-5b57-85d9-1f11eb5d96bc',
  sourceType: 'UPLOADED' as const,
  mediaType: 'image/jpeg',
  status: 'READY' as const,
  originalFilename: 'test.jpg',
  fileSizeBytes: 1024,
  createdAt: '2026-06-19T12:00:00Z',
})

const processingAsset = (assetId: string) => ({
  assetId,
  workspaceId: '2b255986-a5f6-5b57-85d9-1f11eb5d96bc',
  sourceType: 'UPLOADED' as const,
  mediaType: 'image/png',
  status: 'UPLOADING' as const,
  originalFilename: null,
  fileSizeBytes: null,
  createdAt: '2026-06-19T12:00:00Z',
})

const mockFile = (name = 'photo.jpg') => new File(['fake-image-data'], name, { type: 'image/jpeg' })

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe('media store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockWorkspace.activeWorkspaceId = '2b255986-a5f6-5b57-85d9-1f11eb5d96bc'
  })

  describe('initial state', () => {
    it('starts with empty asset list', () => {
      const store = useMediaStore()
      expect(store.assetIds).toEqual([])
      expect(store.assetsById).toEqual({})
      expect(store.nextCursor).toBeNull()
    })

    it('starts with empty selected asset ids', () => {
      const store = useMediaStore()
      expect(store.selectedAssetIds).toEqual([])
    })

    it('starts with no uploads', () => {
      const store = useMediaStore()
      expect(store.uploads).toEqual({})
      expect(store.pendingUploads).toEqual([])
      expect(store.completedUploads).toEqual([])
      expect(store.failedUploads).toEqual([])
    })

    it('starts not loading', () => {
      const store = useMediaStore()
      expect(store.isLoading).toBe(false)
      expect(store.loadError).toBeNull()
    })
  })

  describe('loadAssets', () => {
    it('loads READY assets by default', async () => {
      const store = useMediaStore()
      mockListAssets.mockResolvedValueOnce({
        assets: [readyAsset('8bdec504-2ed1-59e4-8a4a-c068789d03d2')],
        nextCursor: null,
      })

      await store.loadAssets()

      expect(mockListAssets).toHaveBeenCalledWith({
        status: 'READY',
        pageSize: 50,
      })
      expect(store.assetIds).toContain('8bdec504-2ed1-59e4-8a4a-c068789d03d2')
      expect(store.assetsById['8bdec504-2ed1-59e4-8a4a-c068789d03d2']?.status).toBe('READY')
    })

    it('loads UPLOADING assets when requested', async () => {
      const store = useMediaStore()
      mockListAssets.mockResolvedValueOnce({
        assets: [processingAsset('b909d06f-92ea-5671-9f13-73475a89080e')],
        nextCursor: null,
      })

      await store.loadAssets('UPLOADING')

      expect(mockListAssets).toHaveBeenCalledWith({
        status: 'UPLOADING',
        pageSize: 50,
      })
      expect(store.assetIds).toContain('b909d06f-92ea-5671-9f13-73475a89080e')
      expect(store.assetIds).not.toContain('8bdec504-2ed1-59e4-8a4a-c068789d03d2')
    })

    it('loads all statuses when querying dangling assets', async () => {
      const store = useMediaStore()
      mockListAssets.mockResolvedValueOnce({
        assets: [readyAsset('ready-1'), processingAsset('proc-1')],
        nextCursor: null,
      })

      await store.loadDanglingAssets()

      expect(mockListAssets).toHaveBeenCalledWith({
        status: 'PENDING_UPLOAD,UPLOADING,FAILED',
        pageSize: 50,
      })
    })

    it('prepends new assets maintaining newest-first order', async () => {
      const store = useMediaStore()
      // Simulate existing assets in store
      store.assetIds.push('existing-asset')
      store.assetsById['existing-asset'] = readyAsset('existing-asset')

      mockListAssets.mockResolvedValueOnce({
        assets: [readyAsset('new-asset')],
        nextCursor: null,
      })

      await store.loadAssets()

      // New asset should be at the front
      expect(store.assetIds[0]).toBe('new-asset')
    })

    it('clears previous assets on reload', async () => {
      const store = useMediaStore()
      store.assetIds.push('old-asset')
      store.assetsById['old-asset'] = readyAsset('old-asset')

      mockListAssets.mockResolvedValueOnce({
        assets: [readyAsset('new-asset')],
        nextCursor: null,
      })

      await store.loadAssets()

      expect(store.assetIds).not.toContain('old-asset')
      expect(store.assetIds).toContain('new-asset')
    })

    it('records load error on API failure', async () => {
      const store = useMediaStore()
      mockListAssets.mockRejectedValueOnce(
        Object.assign(new Error('Server error'), {
          status: 500,
          title: 'Server Error',
          detail: 'Internal server error',
        }),
      )

      await expect(store.loadAssets()).rejects.toThrow()
      expect(store.loadError).toBeTruthy()
    })
  })

  describe('loadNextPage', () => {
    it('appends new page IDs in API order after existing entries', async () => {
      const store = useMediaStore()
      store.assetIds.push('existing-asset')
      store.assetsById['existing-asset'] = readyAsset('existing-asset')
      store.nextCursor = 'next-page'
      mockListAssets.mockResolvedValueOnce({
        assets: [
          readyAsset('a4ed5f4b-1b79-5e4e-828c-27b7b68952f6'),
          readyAsset('b81b1dba-5135-58c4-832f-f9c687c1bf83'),
        ],
        nextCursor: null,
      })

      await store.loadNextPage()

      expect(store.assetIds).toEqual([
        'existing-asset',
        'a4ed5f4b-1b79-5e4e-828c-27b7b68952f6',
        'b81b1dba-5135-58c4-832f-f9c687c1bf83',
      ])
    })

    it('does nothing when no cursor available', async () => {
      const store = useMediaStore()
      store.nextCursor = null

      await store.loadNextPage()

      expect(mockListAssets).not.toHaveBeenCalled()
    })

    it('fetches next page with cursor', async () => {
      const store = useMediaStore()
      store.nextCursor = 'cursor-abc'

      mockListAssets.mockResolvedValueOnce({
        assets: [readyAsset('page-2-asset')],
        nextCursor: null,
      })

      await store.loadNextPage()

      expect(mockListAssets).toHaveBeenCalledWith({
        status: 'READY',
        pageSize: 50,
        cursor: 'cursor-abc',
      })
      expect(store.assetIds).toContain('page-2-asset')
      expect(store.nextCursor).toBeNull()
    })
  })

  describe('createAndUpload', () => {
    it('rejects deterministically without tracking or API work when workspace is absent', async () => {
      const store = useMediaStore()
      mockWorkspace.activeWorkspaceId = null

      await expect(store.createAndUpload(mockFile(), 'missing-workspace')).rejects.toThrow(
        'An active workspace is required to upload media.',
      )

      expect(mockPutAsset).not.toHaveBeenCalled()
      expect(store.uploads['missing-workspace']).toBeUndefined()
    })

    it('uploads the file and returns ready asset', async () => {
      const store = useMediaStore()
      mockPutAsset.mockResolvedValueOnce({
        assetId: '7a6b54b9-d3f6-5885-81c3-ca9107eccee1',
        workspaceId: '2b255986-a5f6-5b57-85d9-1f11eb5d96bc',
        status: 'PENDING_UPLOAD',
        mediaType: 'image/jpeg',
        deduped: false,
        createdAt: '2026-06-19T12:00:00Z',
      })
      mockGetAsset.mockResolvedValueOnce({
        ...readyAsset('7a6b54b9-d3f6-5885-81c3-ca9107eccee1'),
        previewUrl: '/api/media/assets/7a6b54b9-d3f6-5885-81c3-ca9107eccee1/preview',
        downloadUrl: '/api/media/assets/7a6b54b9-d3f6-5885-81c3-ca9107eccee1/content',
      })

      const file = mockFile()
      const result = await store.createAndUpload(file, 'temp-key-1')

      expect(mockPutAsset).toHaveBeenCalledWith(
        file,
        '2b255986-a5f6-5b57-85d9-1f11eb5d96bc',
        expect.any(String),
      )
      expect(result.assetId).toBe('7a6b54b9-d3f6-5885-81c3-ca9107eccee1')
      expect(result.status).toBe('READY')
    })

    it('hydrates previewUrl and downloadUrl after upload by re-fetching the asset', async () => {
      const store = useMediaStore()
      mockPutAsset.mockResolvedValueOnce({
        assetId: '5c08f33b-611f-536d-9dae-6bd03101dbcb',
        workspaceId: '2b255986-a5f6-5b57-85d9-1f11eb5d96bc',
        status: 'PENDING_UPLOAD',
        mediaType: 'image/jpeg',
        deduped: false,
        createdAt: '2026-06-19T12:00:00Z',
      })
      mockGetAsset.mockResolvedValueOnce({
        assetId: '5c08f33b-611f-536d-9dae-6bd03101dbcb',
        workspaceId: '2b255986-a5f6-5b57-85d9-1f11eb5d96bc',
        sourceType: 'UPLOADED',
        mediaType: 'image/jpeg',
        status: 'READY',
        originalFilename: 'photo.jpg',
        fileSizeBytes: 1024,
        createdAt: '2026-06-19T12:00:00Z',
        previewUrl: '/api/media/assets/5c08f33b-611f-536d-9dae-6bd03101dbcb/preview',
        downloadUrl: '/api/media/assets/5c08f33b-611f-536d-9dae-6bd03101dbcb/content',
      })

      const file = mockFile()
      const result = await store.createAndUpload(file, 'hydrated-key')

      // getAsset was called to fill in the URLs
      expect(mockGetAsset).toHaveBeenCalledWith('5c08f33b-611f-536d-9dae-6bd03101dbcb')
      // The returned asset now has previewUrl so the library grid renders correctly
      expect(result.previewUrl).toBe(
        '/api/media/assets/5c08f33b-611f-536d-9dae-6bd03101dbcb/preview',
      )
      // The store holds the full asset
      expect(store.assetsById['5c08f33b-611f-536d-9dae-6bd03101dbcb']?.previewUrl).toBe(
        '/api/media/assets/5c08f33b-611f-536d-9dae-6bd03101dbcb/preview',
      )
    })

    it('tracks upload item and marks done after successful upload', async () => {
      const store = useMediaStore()
      mockPutAsset.mockResolvedValueOnce({
        assetId: '1a120b9a-7541-5c48-998c-642eba217f3d',
        workspaceId: '2b255986-a5f6-5b57-85d9-1f11eb5d96bc',
        status: 'PENDING_UPLOAD',
        mediaType: 'image/jpeg',
        deduped: false,
        createdAt: '2026-06-19T12:00:00Z',
      })
      mockGetAsset.mockResolvedValueOnce({
        ...readyAsset('1a120b9a-7541-5c48-998c-642eba217f3d'),
        previewUrl: '/api/media/assets/1a120b9a-7541-5c48-998c-642eba217f3d/preview',
        downloadUrl: '/api/media/assets/1a120b9a-7541-5c48-998c-642eba217f3d/content',
      })

      const file = mockFile()
      await store.createAndUpload(file, 'temp-tracking-key')

      // After completion, the upload should be tracked as 'done'
      expect(store.uploads['temp-tracking-key']?.status).toBe('done')
      expect(store.uploads['temp-tracking-key']?.progress).toBe(100)
    })

    it('transitions to failed on upload error with retry policy', async () => {
      vi.useFakeTimers()
      try {
        const store = useMediaStore()
        // Fail on all retry attempts
        mockPutAsset.mockRejectedValue(
          Object.assign(new Error('Server error'), {
            status: 500,
            errorCode: 'INTERNAL_ERROR',
            detail: 'Server error',
          }),
        )

        const file = mockFile()
        const promise = store.createAndUpload(file, 'fail-temp-key')
        const rejection = expect(promise).rejects.toThrow()

        await Promise.resolve()
        await vi.runAllTimersAsync()

        await rejection
        expect(mockPutAsset).toHaveBeenCalledTimes(3)
        expect(store.failedUploads.length).toBeGreaterThan(0)
      } finally {
        vi.useRealTimers()
      }
    })

    it('records conflict status on HTTP 409', async () => {
      const store = useMediaStore()
      mockPutAsset.mockRejectedValue(
        Object.assign(new Error('Conflict'), {
          status: 409,
          errorCode: 'ASSET_UPLOAD_CONFLICT',
          detail: 'Asset already ready',
        }),
      )

      const file = mockFile()

      await expect(store.createAndUpload(file, 'conflict-key')).rejects.toThrow()
      expect(store.uploads['conflict-key']?.status).toBe('conflict')
    })

    it('records file-too-large error on HTTP 413', async () => {
      const store = useMediaStore()
      mockPutAsset.mockRejectedValue(
        Object.assign(new Error('Payload too large'), {
          status: 413,
          errorCode: 'FILE_TOO_LARGE',
          detail: 'File exceeds 500 MB',
        }),
      )

      const file = mockFile()

      await expect(store.createAndUpload(file, 'large-key')).rejects.toThrow()
      expect(store.uploads['large-key']?.status).toBe('failed')
      expect(store.uploads['large-key']?.errorTitle).toBe('File too large')
    })

    it('records email verification required using the backend code field', async () => {
      const store = useMediaStore()
      mockPutAsset.mockRejectedValue(
        Object.assign(new Error('Email verification required'), {
          status: 403,
          code: 'EMAIL_VERIFICATION_REQUIRED',
          detail: 'Please verify your email before uploading media.',
        }),
      )

      const file = mockFile()

      await expect(store.createAndUpload(file, 'verify-key')).rejects.toThrow()
      expect(store.uploads['verify-key']?.status).toBe('failed')
      expect(store.uploads['verify-key']?.errorTitle).toBe('Email verification required')
      expect(store.uploads['verify-key']?.errorDetail).toBe(
        'Please verify your email before uploading media.',
      )
    })
  })

  describe('targeted reconciliation helpers', () => {
    it('upserts a single asset into the newest-first cache without duplicating ids', () => {
      const store = useMediaStore()
      const asset = readyAsset('514795e5-71d8-567c-865c-ed8939cc7c6f')

      store.upsertAsset(asset)
      store.upsertAsset({ ...asset, originalFilename: 'updated-name.png' })

      expect(store.assetIds).toEqual(['514795e5-71d8-567c-865c-ed8939cc7c6f'])
      expect(store.assetsById['514795e5-71d8-567c-865c-ed8939cc7c6f']?.originalFilename).toBe(
        'updated-name.png',
      )
    })

    it('refreshes a single asset from the API and keeps it available in the library cache', async () => {
      const store = useMediaStore()
      mockGetAsset.mockResolvedValueOnce({
        ...readyAsset('bf45c0c4-fce8-58bf-90d1-a01947ac0dc3'),
        previewUrl: '/api/media/assets/bf45c0c4-fce8-58bf-90d1-a01947ac0dc3/preview',
      })

      const result = await store.refreshAsset('bf45c0c4-fce8-58bf-90d1-a01947ac0dc3')

      expect(mockGetAsset).toHaveBeenCalledWith('bf45c0c4-fce8-58bf-90d1-a01947ac0dc3')
      expect(result.assetId).toBe('bf45c0c4-fce8-58bf-90d1-a01947ac0dc3')
      expect(store.assetIds[0]).toBe('bf45c0c4-fce8-58bf-90d1-a01947ac0dc3')
      expect(store.assetsById['bf45c0c4-fce8-58bf-90d1-a01947ac0dc3']?.previewUrl).toBe(
        '/api/media/assets/bf45c0c4-fce8-58bf-90d1-a01947ac0dc3/preview',
      )
    })
  })

  describe('retryUpload', () => {
    it('re-uploads a failed upload item', async () => {
      const store = useMediaStore()
      // Pre-seed a failed upload
      store.uploads['retry-key'] = {
        tempKey: 'retry-key',
        assetId: '087fe0a1-8bc8-5a2b-bfb6-838695c90382',
        file: mockFile(),
        progress: 0,
        status: 'failed',
        errorTitle: 'Upload failed',
        errorDetail: 'Server error',
      }
      mockPutAsset.mockResolvedValueOnce({
        assetId: '087fe0a1-8bc8-5a2b-bfb6-838695c90382',
        workspaceId: '2b255986-a5f6-5b57-85d9-1f11eb5d96bc',
        status: 'PENDING_UPLOAD',
        mediaType: 'image/jpeg',
        deduped: false,
        createdAt: '2026-06-19T12:00:00Z',
      })
      mockGetAsset.mockResolvedValueOnce({
        ...readyAsset('087fe0a1-8bc8-5a2b-bfb6-838695c90382'),
        previewUrl: '/api/media/assets/087fe0a1-8bc8-5a2b-bfb6-838695c90382/preview',
        downloadUrl: '/api/media/assets/087fe0a1-8bc8-5a2b-bfb6-838695c90382/content',
      })

      const result = await store.retryUpload('retry-key')

      expect(mockPutAsset).toHaveBeenCalledWith(
        expect.any(File),
        '2b255986-a5f6-5b57-85d9-1f11eb5d96bc',
        expect.any(String),
      )
      expect(result.status).toBe('READY')
      expect(store.uploads['retry-key']?.status).toBe('done')
    })

    it('throws when no upload found for temp key', async () => {
      const store = useMediaStore()

      await expect(store.retryUpload('nonexistent-key')).rejects.toThrow('No upload found with key')
    })

    it('throws when upload is already in progress', async () => {
      const store = useMediaStore()
      store.uploads['in-progress-key'] = {
        tempKey: 'in-progress-key',
        assetId: '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
        file: mockFile(),
        progress: 50,
        status: 'uploading',
      }

      await expect(store.retryUpload('in-progress-key')).rejects.toThrow('already in progress')
    })
  })

  describe('addToSelection / removeFromSelection', () => {
    it('adds asset to selection', () => {
      const store = useMediaStore()
      store.assetsById['8bdec504-2ed1-59e4-8a4a-c068789d03d2'] = readyAsset(
        '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
      )

      store.addToSelection('8bdec504-2ed1-59e4-8a4a-c068789d03d2')

      expect(store.selectedAssetIds).toContain('8bdec504-2ed1-59e4-8a4a-c068789d03d2')
      expect(store.selectedAssets).toHaveLength(1)
    })

    it('addToSelection is idempotent — duplicate adds are ignored', () => {
      const store = useMediaStore()
      store.assetsById['8bdec504-2ed1-59e4-8a4a-c068789d03d2'] = readyAsset(
        '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
      )

      store.addToSelection('8bdec504-2ed1-59e4-8a4a-c068789d03d2')
      store.addToSelection('8bdec504-2ed1-59e4-8a4a-c068789d03d2')

      expect(
        store.selectedAssetIds.filter((id) => id === '8bdec504-2ed1-59e4-8a4a-c068789d03d2'),
      ).toHaveLength(1)
    })

    it('removes asset from selection', () => {
      const store = useMediaStore()
      store.selectedAssetIds.push('8bdec504-2ed1-59e4-8a4a-c068789d03d2')

      store.removeFromSelection('8bdec504-2ed1-59e4-8a4a-c068789d03d2')

      expect(store.selectedAssetIds).not.toContain('8bdec504-2ed1-59e4-8a4a-c068789d03d2')
    })

    it('clearSelection removes all assets', () => {
      const store = useMediaStore()
      store.selectedAssetIds.push(
        '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
        'b909d06f-92ea-5671-9f13-73475a89080e',
      )

      store.clearSelection()

      expect(store.selectedAssetIds).toEqual([])
    })
  })

  describe('selectedAssets computed', () => {
    it('returns selected assets in order', () => {
      const store = useMediaStore()
      store.assetsById['8bdec504-2ed1-59e4-8a4a-c068789d03d2'] = readyAsset(
        '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
      )
      store.assetsById['b909d06f-92ea-5671-9f13-73475a89080e'] = readyAsset(
        'b909d06f-92ea-5671-9f13-73475a89080e',
      )
      store.selectedAssetIds.push(
        '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
        'b909d06f-92ea-5671-9f13-73475a89080e',
      )

      const selected = store.selectedAssets

      expect(selected).toHaveLength(2)
      expect(selected[0]?.assetId).toBe('8bdec504-2ed1-59e4-8a4a-c068789d03d2')
      expect(selected[1]?.assetId).toBe('b909d06f-92ea-5671-9f13-73475a89080e')
    })

    it('excludes unknown asset ids from selectedAssets', () => {
      const store = useMediaStore()
      store.assetsById['known-asset'] = readyAsset('known-asset')
      store.selectedAssetIds.push('known-asset', 'unknown-asset')

      const selected = store.selectedAssets

      // Only the known asset should appear
      expect(selected).toHaveLength(1)
      expect(selected[0]?.assetId).toBe('known-asset')
    })
  })

  describe('dismissUpload / clearUploads', () => {
    it('removes a single failed upload tracking entry', () => {
      const store = useMediaStore()
      store.uploads['dismiss-key'] = {
        tempKey: 'dismiss-key',
        assetId: '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
        file: mockFile(),
        progress: 0,
        status: 'failed',
        errorTitle: 'Error',
        errorDetail: 'Detail',
      }

      store.dismissUpload('dismiss-key')

      expect(store.uploads['dismiss-key']).toBeUndefined()
    })

    it('clears all upload tracking entries', () => {
      const store = useMediaStore()
      store.uploads['key-1'] = {
        tempKey: 'key-1',
        assetId: '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
        file: mockFile(),
        progress: 0,
        status: 'failed',
      }
      store.uploads['key-2'] = {
        tempKey: 'key-2',
        assetId: 'b909d06f-92ea-5671-9f13-73475a89080e',
        file: mockFile(),
        progress: 0,
        status: 'done',
      }

      store.clearUploads()

      expect(Object.keys(store.uploads)).toHaveLength(0)
    })
  })

  describe('paging', () => {
    it('sets nextCursor from list response', async () => {
      const store = useMediaStore()
      mockListAssets.mockResolvedValueOnce({
        assets: [readyAsset('8bdec504-2ed1-59e4-8a4a-c068789d03d2')],
        nextCursor: 'cursor-page-2',
      })

      await store.loadAssets()

      expect(store.nextCursor).toBe('cursor-page-2')
    })
  })

  describe('computed lists', () => {
    it('pendingUploads includes only uploading items', () => {
      const store = useMediaStore()
      store.uploads.uploading = {
        tempKey: 'uploading',
        assetId: 'bd526c7b-ec12-5f9f-a8ba-f27b4d769b3e',
        file: mockFile(),
        progress: 50,
        status: 'uploading',
      }
      store.uploads.done = {
        tempKey: 'done',
        assetId: 'b2becc82-9edd-508c-afe3-9f9c67eb89f3',
        file: mockFile(),
        progress: 100,
        status: 'done',
      }

      expect(store.pendingUploads).toHaveLength(1)
      expect(store.pendingUploads[0]?.tempKey).toBe('uploading')
    })

    it('failedUploads includes failed and conflict items', () => {
      const store = useMediaStore()
      store.uploads.failed = {
        tempKey: 'failed',
        assetId: 'bd526c7b-ec12-5f9f-a8ba-f27b4d769b3e',
        file: mockFile(),
        progress: 0,
        status: 'failed',
        errorTitle: 'Error',
        errorDetail: 'Detail',
      }
      store.uploads.conflict = {
        tempKey: 'conflict',
        assetId: 'b2becc82-9edd-508c-afe3-9f9c67eb89f3',
        file: mockFile(),
        progress: 0,
        status: 'conflict',
        errorTitle: 'Conflict',
        errorDetail: 'Already ready',
      }

      expect(store.failedUploads).toHaveLength(2)
    })
  })

  describe('deletePersistedAsset', () => {
    it('calls deleteAsset API and removes the asset from local state', async () => {
      const store = useMediaStore()
      store.assetsById['08e791d4-38c0-53d5-881f-570f51f991c8'] = readyAsset(
        '08e791d4-38c0-53d5-881f-570f51f991c8',
      )
      store.assetIds.push('08e791d4-38c0-53d5-881f-570f51f991c8')
      mockDeleteAsset.mockResolvedValueOnce(undefined)

      await store.deletePersistedAsset('08e791d4-38c0-53d5-881f-570f51f991c8')

      expect(mockDeleteAsset).toHaveBeenCalledWith('08e791d4-38c0-53d5-881f-570f51f991c8')
      expect(store.assetIds).not.toContain('08e791d4-38c0-53d5-881f-570f51f991c8')
      expect(store.assetsById['08e791d4-38c0-53d5-881f-570f51f991c8']).toBeUndefined()
    })

    it('removes the deleted asset from selectedAssetIds', async () => {
      const store = useMediaStore()
      store.assetsById['bc9ecbd2-39ab-5bf6-b271-114032e2bf7f'] = readyAsset(
        'bc9ecbd2-39ab-5bf6-b271-114032e2bf7f',
      )
      store.assetIds.push('bc9ecbd2-39ab-5bf6-b271-114032e2bf7f')
      store.selectedAssetIds.push('bc9ecbd2-39ab-5bf6-b271-114032e2bf7f')
      mockDeleteAsset.mockResolvedValueOnce(undefined)

      await store.deletePersistedAsset('bc9ecbd2-39ab-5bf6-b271-114032e2bf7f')

      expect(store.selectedAssetIds).not.toContain('bc9ecbd2-39ab-5bf6-b271-114032e2bf7f')
    })

    it('does not remove other assets when deleting one', async () => {
      const store = useMediaStore()
      store.assetsById['keep-asset'] = readyAsset('keep-asset')
      store.assetIds.push('keep-asset')
      store.assetsById['b9c4a8fc-728d-5911-b8d0-5b86d25339bd'] = readyAsset(
        'b9c4a8fc-728d-5911-b8d0-5b86d25339bd',
      )
      store.assetIds.push('b9c4a8fc-728d-5911-b8d0-5b86d25339bd')
      mockDeleteAsset.mockResolvedValueOnce(undefined)

      await store.deletePersistedAsset('b9c4a8fc-728d-5911-b8d0-5b86d25339bd')

      expect(store.assetIds).toContain('keep-asset')
      expect(store.assetsById['keep-asset']).toBeDefined()
    })

    it('propagates API errors without modifying local state', async () => {
      const store = useMediaStore()
      store.assetsById['del-err-asset'] = readyAsset('del-err-asset')
      store.assetIds.push('del-err-asset')
      mockDeleteAsset.mockRejectedValueOnce(
        Object.assign(new Error('Not found'), {
          title: 'Not Found',
          detail: 'Asset does not exist',
          status: 404,
        }),
      )

      await expect(store.deletePersistedAsset('del-err-asset')).rejects.toThrow()

      // Local state should be unchanged after the API failure
      expect(store.assetIds).toContain('del-err-asset')
      expect(store.assetsById['del-err-asset']).toBeDefined()
      expect(store.deleteError).toBe('Asset does not exist')
    })

    it('removes the deleted asset from both assetIds array and assetsById map', async () => {
      const store = useMediaStore()
      store.assetsById['multi-check-asset'] = readyAsset('multi-check-asset')
      store.assetIds.push('multi-check-asset')
      mockDeleteAsset.mockResolvedValueOnce(undefined)

      await store.deletePersistedAsset('multi-check-asset')

      expect(store.assetIds.includes('multi-check-asset')).toBe(false)
      expect('multi-check-asset' in store.assetsById).toBe(false)
    })
  })

  // -------------------------------------------------------------------------
  // Content-aware dedup — simulates server-side CAS with real SHA-256 hashing
  // -------------------------------------------------------------------------
  describe('createAndUpload — content-aware dedup', () => {
    /** Tracks hash → assetId across uploads within a single test */
    let contentRegistry: Map<string, string>

    beforeEach(() => {
      // vi.clearAllMocks ran in the parent beforeEach — mockPutAsset and
      // mockGetAsset are clean. We now install content-aware implementations
      // so files with identical content automatically dedup.
      contentRegistry = new Map()

      mockPutAsset.mockImplementation(
        async (file: File, _workspaceId: string, stableAssetId?: string) => {
          const buffer = await file.arrayBuffer()
          const hashBytes = new Uint8Array(await crypto.subtle.digest('SHA-256', buffer))
          const hashHex = Array.from(hashBytes)
            .map((b) => b.toString(16).padStart(2, '0'))
            .join('')

          const existingAssetId = contentRegistry.get(hashHex)
          if (existingAssetId) {
            return {
              assetId: existingAssetId,
              workspaceId: '2b255986-a5f6-5b57-85d9-1f11eb5d96bc',
              status: 'READY',
              mediaType: file.type || 'application/octet-stream',
              deduped: true,
              createdAt: '2026-06-19T12:00:00Z',
            }
          }

          const assetId = stableAssetId ?? crypto.randomUUID()
          contentRegistry.set(hashHex, assetId)

          return {
            assetId,
            workspaceId: '2b255986-a5f6-5b57-85d9-1f11eb5d96bc',
            status: 'PENDING_UPLOAD',
            mediaType: file.type || 'application/octet-stream',
            deduped: false,
            createdAt: '2026-06-19T12:00:00Z',
          }
        },
      )

      mockGetAsset.mockImplementation(async (assetId: string) => ({
        assetId,
        workspaceId: '2b255986-a5f6-5b57-85d9-1f11eb5d96bc',
        sourceType: 'UPLOADED' as const,
        mediaType: 'image/svg+xml',
        status: 'READY' as const,
        originalFilename: 'asset.svg',
        fileSizeBytes: 100,
        createdAt: '2026-06-19T12:00:00Z',
      }))
    })

    it('returns the same assetId when uploading identical files (CAS dedup)', async () => {
      const store = useMediaStore()
      const content = '<svg><circle cx="10" cy="10" r="5"/></svg>'
      const file1 = new File([content], 'img1.svg', { type: 'image/svg+xml' })
      const file2 = new File([content], 'img2.svg', { type: 'image/svg+xml' })

      const result1 = await store.createAndUpload(file1, 'upload-1')
      const result2 = await store.createAndUpload(file2, 'upload-2')

      expect(result1.assetId).toBe(result2.assetId)
    })

    it('returns different assetIds for files with different content', async () => {
      const store = useMediaStore()
      const file1 = new File(['content-a'], 'a.svg', { type: 'image/svg+xml' })
      const file2 = new File(['content-b'], 'b.svg', { type: 'image/svg+xml' })

      const result1 = await store.createAndUpload(file1, 'upload-1')
      const result2 = await store.createAndUpload(file2, 'upload-2')

      expect(result1.assetId).not.toBe(result2.assetId)
    })

    it('treats a 1-bit mutated file as a different asset', async () => {
      const store = useMediaStore()
      const content = '<svg><circle cx="10" cy="10" r="5"/></svg>'
      const encoder = new TextEncoder()
      const originalBytes = encoder.encode(content)
      const mutatedBytes = new Uint8Array(originalBytes)
      // Flip the lowest bit of the last byte
      mutatedBytes[mutatedBytes.length - 1]! ^= 0x01

      const file1 = new File([originalBytes], 'original.svg', {
        type: 'image/svg+xml',
      })
      const file2 = new File([mutatedBytes], 'mutated.svg', {
        type: 'image/svg+xml',
      })

      const result1 = await store.createAndUpload(file1, 'upload-1')
      const result2 = await store.createAndUpload(file2, 'upload-2')

      expect(result1.assetId).not.toBe(result2.assetId)
    })

    it('tracks upload items with done status for both dedup and new uploads', async () => {
      const store = useMediaStore()
      const file = new File(['test'], 'test.svg', { type: 'image/svg+xml' })

      await store.createAndUpload(file, 'upload-key')

      expect(store.uploads['upload-key']?.status).toBe('done')
      expect(store.uploads['upload-key']?.progress).toBe(100)
    })

    it('keeps only one asset in the store when uploading the same file twice', async () => {
      const store = useMediaStore()
      const content = '<svg><circle cx="10" cy="10" r="5"/></svg>'
      const file1 = new File([content], 'img1.svg', { type: 'image/svg+xml' })
      const file2 = new File([content], 'img2.svg', { type: 'image/svg+xml' })

      const result1 = await store.createAndUpload(file1, 'upload-1')
      await store.createAndUpload(file2, 'upload-2')

      // Only one unique asset in the store since both files have the same content
      expect(Object.keys(store.assetsById)).toHaveLength(1)
      expect(store.assetIds).toHaveLength(1)
      expect(store.assetIds[0]).toBe(result1.assetId)
    })
  })
})
