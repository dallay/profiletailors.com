import { describe, expect, it, vi, beforeEach } from 'vitest'
import { useUploadAsset } from './useUploadAsset'

const mockPutAsset = vi.hoisted(() => vi.fn())

vi.mock('@modules/media/services/media-api', () => ({
  putAsset: (...args: unknown[]): ReturnType<typeof mockPutAsset> => mockPutAsset(...args),
}))

describe('useUploadAsset', () => {
  beforeEach(() => {
    mockPutAsset.mockReset()
  })

  it('retries transient failures and succeeds', async () => {
    mockPutAsset
      .mockRejectedValueOnce({ status: 503 })
      .mockResolvedValueOnce({ assetId: 'a1', status: 'READY', deduped: true })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    const result = await uploadAsset(file, 'ws-1', 'asset-1', {
      maxAttempts: 2,
      initialDelayMs: 1,
      maxDelayMs: 1,
    })

    expect(result).toMatchObject({ assetId: 'a1', status: 'READY' })
    expect(mockPutAsset).toHaveBeenCalledTimes(2)

    // Assert both attempts received the same arguments
    const firstCall = mockPutAsset.mock.calls[0]
    const secondCall = mockPutAsset.mock.calls[1]
    expect(firstCall[0]).toBe(file)
    expect(firstCall[1]).toBe('ws-1')
    expect(firstCall[2]).toBe('asset-1')
    expect(secondCall[0]).toBe(file)
    expect(secondCall[1]).toBe('ws-1')
    expect(secondCall[2]).toBe('asset-1')
  })

  it('does not retry non-retryable client errors', async () => {
    mockPutAsset.mockRejectedValueOnce({ status: 409 })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    await expect(
      uploadAsset(file, 'ws-1', 'asset-2', {
        maxAttempts: 3,
        initialDelayMs: 1,
        maxDelayMs: 1,
      }),
    ).rejects.toMatchObject({ status: 409 })

    expect(mockPutAsset).toHaveBeenCalledTimes(1)
  })
})
