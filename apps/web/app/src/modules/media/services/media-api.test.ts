import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import {
  deleteAsset,
  getAsset,
  listAssets,
  importUnsplashPhoto,
  putAsset,
  searchUnsplashPhotos,
  type MediaAssetSummary,
} from './media-api'
import type { createApiFetch } from '@modules/auth/infrastructure/auth-api'

type ApiFetchMock = ReturnType<typeof createApiFetch>

// ---------------------------------------------------------------------------
// Mock auth-api (createApiFetch + refreshSession)
// ---------------------------------------------------------------------------

// Use vi.hoisted so the variable is available in the vi.mock factory,
// which vitest hoists above let/const declarations.
const mockMediaApiFetch = vi.fn()
const { mockAuthenticatedBox } = vi.hoisted(() => ({
  mockAuthenticatedBox: { current: true },
}))

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  createApiFetch: () => {
    // Return a function that delegates ALL calls to mockMediaApiFetch.
    // The raw property is required by the media-api code for token refresh.
    const fn = (async <T>(...args: Parameters<ApiFetchMock>) => {
      return mockMediaApiFetch(...args) as Promise<T>
    }) as ApiFetchMock
    fn.raw = mockMediaApiFetch as ApiFetchMock['raw']
    return fn
  },
  refreshSession: vi.fn().mockResolvedValue(null),
}))

// ---------------------------------------------------------------------------
// Auth store mock (controlled per test)
// ---------------------------------------------------------------------------
const mockApiFetch = vi.fn()
const mockApiFetchRaw = vi.fn()

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({
    get isAuthenticated() {
      return mockAuthenticatedBox.current
    },
    apiFetch: mockApiFetch,
    apiFetchRaw: mockApiFetchRaw,
    accessToken: { value: 'fake-token' },
    workspace: { activeWorkspaceId: 'a0c6b80d-0d0c-5221-8858-e507447063df' },
    $reset: vi.fn(),
  }),
}))

vi.mock('@modules/workspace/infrastructure/workspace.store', () => ({
  useWorkspaceStore: () => ({
    activeWorkspaceId: 'a0c6b80d-0d0c-5221-8858-e507447063df',
  }),
}))

// Stub useFileHash so putAsset tests don't touch crypto.subtle
// NOTE: plain async functions (not vi.fn()) so vi.clearAllMocks in other describe
// blocks does NOT clear the implementation. vitest 3.2.6 clearAllMocks calls
// mockClear on each tracked spy, which resets implementation too.
vi.mock('@modules/media/application/useFileHash', () => ({
  computeFileHash: async () => 'a'.repeat(64),
  sanitizeFilename: vi.fn((name: string) =>
    name.replace(/[/\\]/g, '_').replace(/\.\./g, '_').replace(/\0/g, '').slice(0, 255),
  ),
}))

// Stub crypto.randomUUID so putAsset uses a deterministic asset id
const FIXED_UUID = '11111111-1111-4111-8111-111111111111'

let randomUUIDSpy: ReturnType<typeof vi.spyOn>
beforeEach(() => {
  randomUUIDSpy = vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue(FIXED_UUID)
})
afterEach(() => {
  randomUUIDSpy.mockRestore()
})

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function emptyResponse(status: number): Response {
  return new Response(null, { status })
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('media asset external metadata contract', () => {
  it('accepts optional external metadata fields on media summaries', () => {
    const summary: MediaAssetSummary = {
      assetId: '097590a1-c080-53f3-b5c2-aa595c3a08ef',
      workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
      sourceType: 'EXTERNAL',
      mediaType: 'image/jpeg',
      status: 'READY',
      originalFilename: null,
      fileSizeBytes: 2048,
      createdAt: '2026-01-01T00:00:00.000Z',
      sourceProvider: 'unsplash',
      externalId: 'photo-123',
      sourceUrl: 'https://unsplash.com/photos/photo-123',
      authorName: 'Jane Creator',
      authorUrl: 'https://unsplash.com/@jane',
      metadata: { palette: ['#000000', '#ffffff'] },
    }

    expect(summary.sourceProvider).toBe('unsplash')
    expect(summary.metadata).toEqual({ palette: ['#000000', '#ffffff'] })
  })
})

describe('Unsplash provider API', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockApiFetch.mockReset()
    mockAuthenticatedBox.current = true
  })

  it('loads editorial examples without a query string', async () => {
    mockApiFetch.mockResolvedValueOnce({
      photos: [
        {
          externalId: 'photo-1',
          name: 'Editorial photo',
          previewUrl: 'https://images.unsplash.com/photo-1',
          sourceUrl: 'https://unsplash.com/photos/photo-1',
          authorName: 'Author',
          authorUrl: 'https://unsplash.com/@author',
        },
      ],
    })

    const photos = await searchUnsplashPhotos()

    expect(photos).toHaveLength(1)
    expect(mockApiFetch).toHaveBeenCalledWith('/api/media/providers/unsplash/photos', {
      method: 'GET',
      workspaceScoped: true,
    })
  })

  it('encodes a normalized search query', async () => {
    mockApiFetch.mockResolvedValueOnce({ photos: [] })

    await searchUnsplashPhotos('  remote work & teams  ')

    expect(mockApiFetch).toHaveBeenCalledWith(
      '/api/media/providers/unsplash/photos?query=remote+work+%26+teams',
      { method: 'GET', workspaceScoped: true },
    )
  })

  it('imports the selected photo with a workspace-scoped POST', async () => {
    mockApiFetch.mockResolvedValueOnce({ assetId: '8bdec504-2ed1-59e4-8a4a-c068789d03d2' })

    await importUnsplashPhoto('photo/with spaces')

    expect(mockApiFetch).toHaveBeenCalledWith(
      '/api/media/providers/unsplash/photos/photo%2Fwith%20spaces/import',
      { method: 'POST', workspaceScoped: true },
    )
  })

  it('delegates unauthenticated provider responses to the shared authenticated client', async () => {
    mockAuthenticatedBox.current = false
    const unauthorized = Object.assign(new Error('Not authenticated'), { status: 401 })
    mockApiFetch.mockRejectedValue(unauthorized)

    await expect(searchUnsplashPhotos('work')).rejects.toMatchObject({ status: 401 })
    await expect(importUnsplashPhoto('photo-1')).rejects.toMatchObject({ status: 401 })
    expect(mockApiFetch).toHaveBeenCalledTimes(2)
  })
})

describe('deleteAsset', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockApiFetch.mockReset()
    mockAuthenticatedBox.current = true
  })

  it('throws a 401 error when the user is not authenticated', async () => {
    mockAuthenticatedBox.current = false

    await expect(deleteAsset('df0fa098-1f32-5770-8065-9abe0487642b')).rejects.toMatchObject({
      title: 'Not authenticated',
      detail: 'You must be signed in.',
      status: 401,
    })

    expect(mockApiFetch).not.toHaveBeenCalled()
  })

  it('calls apiFetch with DELETE method and the correct asset path', async () => {
    mockApiFetch.mockResolvedValueOnce(undefined)

    await deleteAsset('635a80d8-ddac-5be0-bcd5-3f04c43df611')

    expect(mockApiFetch).toHaveBeenCalledOnce()
    expect(mockApiFetch).toHaveBeenCalledWith(
      '/api/media/assets/635a80d8-ddac-5be0-bcd5-3f04c43df611',
      {
        method: 'DELETE',
        workspaceScoped: true,
      },
    )
  })

  it('resolves without returning a value on success', async () => {
    mockApiFetch.mockResolvedValueOnce(undefined)

    const result = await deleteAsset('b6167bf0-8ae8-548f-bf18-ce66f0e43771')

    expect(result).toBeUndefined()
  })

  it('propagates API errors to the caller', async () => {
    mockApiFetch.mockRejectedValueOnce({
      title: 'Not Found',
      detail: 'The asset does not exist.',
      status: 404,
    })

    await expect(deleteAsset('missing-asset')).rejects.toMatchObject({
      title: 'Not Found',
      status: 404,
    })
  })

  it('uses the workspaceScoped flag so the X-Workspace-Id header is injected', async () => {
    mockApiFetch.mockResolvedValueOnce(undefined)

    await deleteAsset('d6064dd3-0a18-5ff3-b11d-57865b46c8ab')

    const [, options] = mockApiFetch.mock.calls[0] as [string, { workspaceScoped?: boolean }]
    expect(options.workspaceScoped).toBe(true)
  })

  it('constructs the correct path for an asset with a complex id', async () => {
    mockApiFetch.mockResolvedValueOnce(undefined)

    await deleteAsset('asset/with spaces?special=chars')

    expect(mockApiFetch.mock.calls[0]?.[0]).toBe(
      `/api/media/assets/${encodeURIComponent('asset/with spaces?special=chars')}`,
    )
  })
})

describe('getAsset', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockApiFetch.mockReset()
    mockAuthenticatedBox.current = true
  })

  it('throws 401 when not authenticated', async () => {
    mockAuthenticatedBox.current = false

    await expect(getAsset('65656b27-19fb-5b2c-9050-d8cc60847d2c')).rejects.toMatchObject({
      title: 'Not authenticated',
      status: 401,
    })

    expect(mockApiFetch).not.toHaveBeenCalled()
  })

  it('calls apiFetch GET on /api/media/assets/{assetId}', async () => {
    mockApiFetch.mockResolvedValueOnce({
      assetId: '65656b27-19fb-5b2c-9050-d8cc60847d2c',
      workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      status: 'READY',
      mediaType: 'image/jpeg',
    })

    await getAsset('65656b27-19fb-5b2c-9050-d8cc60847d2c')

    expect(mockApiFetch).toHaveBeenCalledOnce()
    expect(mockApiFetch).toHaveBeenCalledWith(
      '/api/media/assets/65656b27-19fb-5b2c-9050-d8cc60847d2c',
      {
        method: 'GET',
        workspaceScoped: true,
      },
    )
  })

  it('returns the apiFetch response payload', async () => {
    const payload = {
      assetId: '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
      workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      status: 'READY' as const,
      mediaType: 'image/png',
      detectedMediaType: 'image/png',
      originalFilename: 'photo.png',
      fileSizeBytes: 1024,
      createdAt: '2026-01-01T00:00:00.000Z',
    }
    mockApiFetch.mockResolvedValueOnce(payload)

    const result = await getAsset('8bdec504-2ed1-59e4-8a4a-c068789d03d2')

    expect(result).toEqual(payload)
  })
})

describe('listAssets', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockApiFetch.mockReset()
    mockAuthenticatedBox.current = true
  })

  it('returns an empty result when not authenticated, without calling apiFetch', async () => {
    mockAuthenticatedBox.current = false

    const result = await listAssets()

    expect(result).toEqual({ assets: [], nextCursor: null })
    expect(mockApiFetch).not.toHaveBeenCalled()
  })

  it('defaults the status filter to READY when no opts are provided', async () => {
    mockApiFetch.mockResolvedValueOnce({ assets: [], nextCursor: null })

    await listAssets()

    expect(mockApiFetch).toHaveBeenCalledWith('/api/media/assets?status=READY', {
      method: 'GET',
      workspaceScoped: true,
    })
  })

  it('uses the explicit status filter when provided', async () => {
    mockApiFetch.mockResolvedValueOnce({ assets: [], nextCursor: null })

    await listAssets({ status: 'READY,PENDING_UPLOAD,UPLOADING,FAILED' })

    expect(mockApiFetch.mock.calls[0]?.[0]).toBe(
      '/api/media/assets?status=READY%2CPENDING_UPLOAD%2CUPLOADING%2CFAILED',
    )
  })

  it('appends pageSize to the query string when provided', async () => {
    mockApiFetch.mockResolvedValueOnce({ assets: [], nextCursor: null })

    await listAssets({ pageSize: 25 })

    expect(mockApiFetch.mock.calls[0]?.[0]).toBe('/api/media/assets?status=READY&pageSize=25')
  })

  it('appends cursor to the query string when provided', async () => {
    mockApiFetch.mockResolvedValueOnce({ assets: [], nextCursor: 'next-cursor' })

    await listAssets({ cursor: 'abc123' })

    expect(mockApiFetch.mock.calls[0]?.[0]).toBe('/api/media/assets?status=READY&cursor=abc123')
  })

  it('returns the apiFetch response payload (assets + nextCursor)', async () => {
    const payload = {
      assets: [
        {
          assetId: '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
          status: 'READY' as const,
          mediaType: 'image/jpeg',
          detectedMediaType: 'image/jpeg',
          originalFilename: 'a.jpg',
          fileSizeBytes: 100,
          createdAt: '2026-01-01T00:00:00.000Z',
        },
      ],
      nextCursor: 'cursor-2',
    }
    mockApiFetch.mockResolvedValueOnce(payload)

    const result = await listAssets()

    expect(result).toEqual(payload)
  })
})

describe('putAsset', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockMediaApiFetch.mockReset()
    mockApiFetchRaw.mockReset()
    mockApiFetchRaw.mockImplementation((...args) => mockMediaApiFetch(...args))
    mockAuthenticatedBox.current = true
  })

  it('throws 401 when not authenticated', async () => {
    mockAuthenticatedBox.current = false

    const file = new File(['hello'], 'hello.txt')
    await expect(putAsset(file, '820c22d8-9ce7-5e76-867f-04bac4e53b59')).rejects.toMatchObject({
      title: 'Not authenticated',
      status: 401,
    })

    expect(mockMediaApiFetch).not.toHaveBeenCalled()
  })

  it('sends a PUT with the computed hash, file size, declared media type, and sanitized filename', async () => {
    mockMediaApiFetch.mockResolvedValueOnce(
      jsonResponse(200, {
        assetId: FIXED_UUID,
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        status: 'READY',
        mediaType: 'image/jpeg',
        detectedMediaType: 'image/jpeg',
        originalFilename: 'photo.jpg',
        fileSizeBytes: 5,
        createdAt: '2026-01-01T00:00:00.000Z',
      }),
    )

    const file = new File(['hello'], 'photo_with_spaces.jpg', { type: 'image/jpeg' })
    await putAsset(file, '820c22d8-9ce7-5e76-867f-04bac4e53b59')

    expect(mockMediaApiFetch).toHaveBeenCalledTimes(1)
    const [path, options] = mockMediaApiFetch.mock.calls[0] as [
      string,
      { method: string; body: string },
    ]
    expect(path).toBe(`/api/media/assets/${FIXED_UUID}`)
    expect(options.method).toBe('PUT')

    const body = JSON.parse(options.body)
    expect(body.fileHash).toBe('a'.repeat(64))
    expect(body.fileSizeBytes).toBe(5)
    expect(body.declaredMediaType).toBe('image/jpeg')
    expect(body.originalFilename).toBe('photo_with_spaces.jpg')
  })

  it('sanitizes the filename by stripping path separators and traversal sequences', async () => {
    mockMediaApiFetch.mockResolvedValueOnce(
      jsonResponse(200, {
        assetId: FIXED_UUID,
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        status: 'READY',
        mediaType: 'image/jpeg',
        detectedMediaType: 'image/jpeg',
        originalFilename: 'photo.jpg',
        fileSizeBytes: 1,
        createdAt: '2026-01-01T00:00:00.000Z',
      }),
    )

    const file = new File(['x'], '../../../etc/passwd\0.jpg', { type: 'image/jpeg' })
    await putAsset(file, '820c22d8-9ce7-5e76-867f-04bac4e53b59')

    const options = mockMediaApiFetch.mock.calls[0]?.[1] as { body: string }
    const body = JSON.parse(options.body)
    // /, \\, .. are sanitized away; null bytes are removed
    expect(body.originalFilename).not.toContain('/')
    expect(body.originalFilename).not.toContain('\\')
    expect(body.originalFilename).not.toContain('..')
    expect(body.originalFilename).not.toContain('\0')
  })

  it('uses "application/octet-stream" as declared media type when File.type is empty', async () => {
    mockMediaApiFetch.mockResolvedValueOnce(
      jsonResponse(200, {
        assetId: FIXED_UUID,
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        status: 'READY',
        mediaType: 'image/jpeg',
        detectedMediaType: 'image/jpeg',
        originalFilename: 'unknown.bin',
        fileSizeBytes: 0,
        createdAt: '2026-01-01T00:00:00.000Z',
      }),
    )

    const file = new File([], 'unknown.bin')
    await putAsset(file, '820c22d8-9ce7-5e76-867f-04bac4e53b59')

    const [, options] = mockMediaApiFetch.mock.calls[0] as [string, { body: string }]
    const body = JSON.parse(options.body)
    expect(body.declaredMediaType).toBe('application/octet-stream')
  })

  it('returns the body directly when PUT returns 200 with READY status (dedup hit)', async () => {
    const dedupBody = {
      assetId: FIXED_UUID,
      workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      status: 'READY',
      mediaType: 'image/jpeg',
      detectedMediaType: 'image/jpeg',
      originalFilename: 'existing.jpg',
      fileSizeBytes: 1024,
      createdAt: '2026-01-01T00:00:00.000Z',
    }
    mockMediaApiFetch.mockResolvedValueOnce(jsonResponse(200, dedupBody))

    const result = await putAsset(
      new File(['x'], 'photo.jpg'),
      '820c22d8-9ce7-5e76-867f-04bac4e53b59',
    )

    expect(result).toEqual(dedupBody)
    // No upload POST happened
    expect(mockMediaApiFetch).toHaveBeenCalledTimes(1)
  })

  it('throws RATE_LIMIT_EXCEEDED when PUT returns 429 with retryAfterSeconds', async () => {
    mockMediaApiFetch.mockResolvedValueOnce(
      jsonResponse(429, {
        errorCode: 'RATE_LIMIT_EXCEEDED',
        message: 'Hourly creation limit exceeded.',
        retryAfterSeconds: 120,
      }),
    )

    await expect(
      putAsset(new File(['x'], 'a.jpg'), '820c22d8-9ce7-5e76-867f-04bac4e53b59'),
    ).rejects.toMatchObject({
      title: 'Rate limit exceeded',
      detail: 'Hourly creation limit exceeded.',
      status: 429,
      errorCode: 'RATE_LIMIT_EXCEEDED',
      retryAfterSeconds: 120,
    })
  })

  it('throws ASSET_HASH_MISMATCH when PUT returns 409', async () => {
    mockMediaApiFetch.mockResolvedValueOnce(
      jsonResponse(409, {
        errorCode: 'ASSET_HASH_MISMATCH',
        message: 'Asset already exists with a different file hash.',
        existingFileHash: 'b'.repeat(64),
      }),
    )

    await expect(
      putAsset(new File(['x'], 'a.jpg'), '820c22d8-9ce7-5e76-867f-04bac4e53b59'),
    ).rejects.toMatchObject({
      title: 'Asset hash mismatch',
      detail: 'Asset already exists with a different file hash.',
      status: 409,
      errorCode: 'ASSET_HASH_MISMATCH',
      existingFileHash: 'b'.repeat(64),
    })
  })

  it('throws email verification required when PUT returns 403 email verification problem', async () => {
    mockMediaApiFetch.mockResolvedValueOnce(
      jsonResponse(403, {
        code: 'EMAIL_VERIFICATION_REQUIRED',
        detail: 'Please verify your email before uploading media.',
      }),
    )

    await expect(
      putAsset(new File(['x'], 'a.jpg'), '820c22d8-9ce7-5e76-867f-04bac4e53b59'),
    ).rejects.toMatchObject({
      title: 'Email verification required',
      detail: 'Please verify your email before uploading media.',
      status: 403,
      errorCode: 'EMAIL_VERIFICATION_REQUIRED',
    })
  })

  it('throws VALIDATION_ERROR when PUT returns 400', async () => {
    mockMediaApiFetch.mockResolvedValueOnce(
      jsonResponse(400, {
        errorCode: 'FILE_SIZE_INVALID',
        message: 'File size must be positive.',
      }),
    )

    await expect(
      putAsset(new File(['x'], 'a.jpg'), '820c22d8-9ce7-5e76-867f-04bac4e53b59'),
    ).rejects.toMatchObject({
      title: 'Validation error',
      status: 400,
      errorCode: 'FILE_SIZE_INVALID',
    })
  })

  it('throws with the errorCode as title for non-OK PUT responses', async () => {
    mockMediaApiFetch.mockResolvedValueOnce(
      jsonResponse(500, {
        errorCode: 'INTERNAL_SERVER_ERROR',
        message: 'Something went wrong.',
      }),
    )

    await expect(
      putAsset(new File(['x'], 'a.jpg'), '820c22d8-9ce7-5e76-867f-04bac4e53b59'),
    ).rejects.toMatchObject({
      title: 'INTERNAL_SERVER_ERROR',
      detail: 'Something went wrong.',
      status: 500,
    })
  })

  it('throws with status-only title when non-OK PUT has no JSON body', async () => {
    mockMediaApiFetch.mockResolvedValueOnce(emptyResponse(502))

    await expect(
      putAsset(new File(['x'], 'a.jpg'), '820c22d8-9ce7-5e76-867f-04bac4e53b59'),
    ).rejects.toMatchObject({
      title: 'PUT failed',
      detail: 'Server returned 502.',
      status: 502,
    })
  })

  it('uploads bytes to the upload URL on 201 PENDING_UPLOAD and returns the upload response', async () => {
    mockMediaApiFetch
      .mockResolvedValueOnce(
        jsonResponse(201, {
          assetId: FIXED_UUID,
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
          status: 'PENDING_UPLOAD',
          mediaType: 'image/jpeg',
          uploadUrl: '/api/media/assets/abc/upload',
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse(200, {
          assetId: FIXED_UUID,
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
          status: 'READY',
          mediaType: 'image/jpeg',
          detectedMediaType: 'image/jpeg',
          originalFilename: 'photo.jpg',
          fileSizeBytes: 5,
          createdAt: '2026-01-01T00:00:00.000Z',
        }),
      )

    const result = await putAsset(
      new File(['hello'], 'photo.jpg', { type: 'image/jpeg' }),
      '820c22d8-9ce7-5e76-867f-04bac4e53b59',
    )

    expect(mockMediaApiFetch).toHaveBeenCalledTimes(2)
    const [, uploadOptions] = mockMediaApiFetch.mock.calls[1] as [
      string,
      { method: string; body: File; headers: Record<string, string> },
    ]
    expect(uploadOptions.method).toBe('POST')
    expect(uploadOptions.body).toBeInstanceOf(File)
    expect(uploadOptions.headers['Content-Type']).toBe('application/octet-stream')

    expect(result).toMatchObject({
      assetId: FIXED_UUID,
      status: 'READY',
    })
  })

  it('throws email verification required when upload returns 403 email verification problem', async () => {
    mockMediaApiFetch
      .mockResolvedValueOnce(
        jsonResponse(201, {
          assetId: FIXED_UUID,
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
          status: 'PENDING_UPLOAD',
          mediaType: 'image/jpeg',
          uploadUrl: '/api/media/assets/abc/upload',
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse(403, {
          code: 'EMAIL_VERIFICATION_REQUIRED',
          detail: 'Please verify your email before uploading media.',
        }),
      )

    await expect(
      putAsset(
        new File(['hello'], 'photo.jpg', { type: 'image/jpeg' }),
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      ),
    ).rejects.toMatchObject({
      title: 'Email verification required',
      detail: 'Please verify your email before uploading media.',
      status: 403,
      errorCode: 'EMAIL_VERIFICATION_REQUIRED',
    })
  })

  it('throws verification error when upload returns 422', async () => {
    mockMediaApiFetch
      .mockResolvedValueOnce(
        jsonResponse(201, {
          assetId: FIXED_UUID,
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
          status: 'PENDING_UPLOAD',
          mediaType: 'image/jpeg',
          uploadUrl: '/api/media/assets/abc/upload',
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse(422, {
          errorCode: 'HASH_MISMATCH',
          message: 'Server-side hash check failed.',
        }),
      )

    await expect(
      putAsset(
        new File(['hello'], 'photo.jpg', { type: 'image/jpeg' }),
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      ),
    ).rejects.toMatchObject({
      title: 'HASH_MISMATCH',
      detail: 'Server-side hash check failed.',
      status: 422,
      errorCode: 'HASH_MISMATCH',
    })
  })

  it('throws with the errorCode as title for non-OK upload responses', async () => {
    mockMediaApiFetch
      .mockResolvedValueOnce(
        jsonResponse(201, {
          assetId: FIXED_UUID,
          workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
          status: 'PENDING_UPLOAD',
          mediaType: 'image/jpeg',
          uploadUrl: '/api/media/assets/abc/upload',
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse(500, {
          errorCode: 'STORAGE_UNAVAILABLE',
          message: 'Object store is unreachable.',
        }),
      )

    await expect(
      putAsset(
        new File(['hello'], 'photo.jpg', { type: 'image/jpeg' }),
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      ),
    ).rejects.toMatchObject({
      title: 'STORAGE_UNAVAILABLE',
      detail: 'Object store is unreachable.',
      status: 500,
    })
  })

  it('throws a structured error when PENDING_UPLOAD does not include an upload URL', async () => {
    mockMediaApiFetch.mockResolvedValueOnce(
      jsonResponse(201, {
        assetId: FIXED_UUID,
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        status: 'PENDING_UPLOAD',
        mediaType: 'image/jpeg',
      }),
    )

    await expect(
      putAsset(
        new File(['hello'], 'photo.jpg', { type: 'image/jpeg' }),
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      ),
    ).rejects.toMatchObject({
      title: 'Upload URL missing',
      detail: 'Server requested an upload but did not provide an upload URL.',
      status: 201,
      errorCode: 'UPLOAD_URL_MISSING',
    })
  })

  it('uses the auth store raw fetch for PUT-first media requests', async () => {
    mockApiFetchRaw.mockReset()
    mockApiFetchRaw.mockResolvedValueOnce(
      jsonResponse(200, {
        assetId: FIXED_UUID,
        workspaceId: '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        status: 'READY',
        mediaType: 'image/jpeg',
        detectedMediaType: 'image/jpeg',
        originalFilename: 'photo.jpg',
        fileSizeBytes: 5,
        createdAt: '2026-01-01T00:00:00.000Z',
      }),
    )

    await putAsset(
      new File(['hello'], 'photo.jpg', { type: 'image/jpeg' }),
      '820c22d8-9ce7-5e76-867f-04bac4e53b59',
    )

    expect(mockMediaApiFetch).not.toHaveBeenCalled()
    expect(mockApiFetchRaw).toHaveBeenCalledWith(`/api/media/assets/${FIXED_UUID}`, {
      method: 'PUT',
      headers: { 'X-Workspace-Id': '820c22d8-9ce7-5e76-867f-04bac4e53b59' },
      body: JSON.stringify({
        fileHash: 'a'.repeat(64),
        fileSizeBytes: 5,
        declaredMediaType: 'image/jpeg',
        originalFilename: 'photo.jpg',
      }),
    })
  })

  // -----------------------------------------------------------------------
  // pollUntilReady (via 202 from putAsset)
  // -----------------------------------------------------------------------

  describe('pollUntilReady (putAsset returns 202)', () => {
    beforeEach(() => {
      vi.useFakeTimers()
    })

    afterEach(() => {
      vi.useRealTimers()
    })

    it('polls once and returns when status becomes READY', async () => {
      mockMediaApiFetch
        .mockResolvedValueOnce(jsonResponse(202, { status: 'WAITING_FOR_BLOB' }))
        .mockResolvedValueOnce(jsonResponse(200, { status: 'READY' }))

      const promise = putAsset(
        new File(['hello'], 'photo.jpg', { type: 'image/jpeg' }),
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      )
      await vi.advanceTimersByTimeAsync(3_100)
      const result = await promise

      expect(result).toMatchObject({ status: 'READY' })
    })

    it('retries on 202 and returns when READY after multiple polls', async () => {
      mockMediaApiFetch
        .mockResolvedValueOnce(jsonResponse(202, { status: 'WAITING_FOR_BLOB' }))
        .mockResolvedValueOnce(jsonResponse(202, { status: 'WAITING_FOR_BLOB' }))
        .mockResolvedValueOnce(jsonResponse(202, { status: 'WAITING_FOR_BLOB' }))
        .mockResolvedValueOnce(jsonResponse(200, { status: 'READY' }))

      const promise = putAsset(
        new File(['hello'], 'photo.jpg', { type: 'image/jpeg' }),
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      )

      // Advance past each poll interval
      for (let i = 0; i < 3; i++) {
        await vi.advanceTimersByTimeAsync(3_100)
      }

      const result = await promise
      expect(result).toMatchObject({ status: 'READY' })
    })

    it('throws ASSET_HASH_MISMATCH when poll returns 409', async () => {
      mockMediaApiFetch
        .mockResolvedValueOnce(jsonResponse(202, { status: 'WAITING_FOR_BLOB' }))
        .mockResolvedValueOnce(jsonResponse(409, { status: 'HASH_MISMATCH' }))

      let error: unknown
      const promise = putAsset(
        new File(['hello'], 'photo.jpg', { type: 'image/jpeg' }),
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      )
      // Attach catch handler BEFORE advancing timers so the rejection is claimed
      promise.catch((e) => {
        error = e
      })

      await vi.advanceTimersByTimeAsync(3_100)
      // Wait an extra microtask for the catch callback to fire
      await vi.advanceTimersByTimeAsync(0)

      expect(error).toMatchObject({ title: 'Asset hash mismatch', status: 409 })
    })

    it('throws server error when poll returns non-OK, non-409 response', async () => {
      mockMediaApiFetch
        .mockResolvedValueOnce(jsonResponse(202, { status: 'WAITING_FOR_BLOB' }))
        .mockResolvedValueOnce(
          jsonResponse(500, { errorCode: 'STORAGE_ERROR', message: 'Cannot check blob status.' }),
        )

      let error: unknown
      const promise = putAsset(
        new File(['hello'], 'photo.jpg', { type: 'image/jpeg' }),
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      )
      promise.catch((e) => {
        error = e
      })

      await vi.advanceTimersByTimeAsync(3_100)
      await vi.advanceTimersByTimeAsync(0)

      expect(error).toMatchObject({
        title: 'STORAGE_ERROR',
        detail: 'Cannot check blob status.',
        status: 500,
      })
    })

    it('throws status-only error when poll returns non-OK with an empty body', async () => {
      mockMediaApiFetch
        .mockResolvedValueOnce(jsonResponse(202, { status: 'WAITING_FOR_BLOB' }))
        .mockResolvedValueOnce(emptyResponse(502))

      let error: unknown
      const promise = putAsset(
        new File(['hello'], 'photo.jpg', { type: 'image/jpeg' }),
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      )
      promise.catch((e) => {
        error = e
      })

      await vi.advanceTimersByTimeAsync(3_100)
      await vi.advanceTimersByTimeAsync(0)

      expect(error).toMatchObject({
        title: 'PUT failed',
        detail: 'Server returned 502.',
        status: 502,
      })
    })

    it('throws upload timeout when all poll attempts return 202', async () => {
      // 1 call from putAsset + 10 calls from pollUntilReady (default maxAttempts = 10)
      for (let i = 0; i < 11; i++) {
        mockMediaApiFetch.mockResolvedValueOnce(jsonResponse(202, { status: 'WAITING_FOR_BLOB' }))
      }

      let error: unknown
      const promise = putAsset(
        new File(['hello'], 'photo.jpg', { type: 'image/jpeg' }),
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      )
      promise.catch((e) => {
        error = e
      })

      // Advance past all 10 poll intervals (3s each = 30s)
      for (let i = 0; i < 10; i++) {
        await vi.advanceTimersByTimeAsync(3_100)
      }
      await vi.advanceTimersByTimeAsync(0)

      expect(error).toMatchObject({ title: 'Upload timeout', status: 408 })
    })
  })
})
