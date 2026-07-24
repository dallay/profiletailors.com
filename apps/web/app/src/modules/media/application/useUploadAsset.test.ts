import { describe, expect, it, vi, beforeEach } from 'vitest'
import { useUploadAsset } from './useUploadAsset'

const mockPutAsset: ReturnType<typeof vi.fn> = vi.hoisted(() => vi.fn())

vi.mock('@modules/media/services/media-api', () => ({
  putAsset: (...args: unknown[]): ReturnType<typeof mockPutAsset> => mockPutAsset(...args),
}))

describe('useUploadAsset', () => {
  beforeEach(() => {
    mockPutAsset.mockReset()
  })

  it('retries transient failures and succeeds', async (): Promise<void> => {
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

    const firstCall = mockPutAsset.mock.calls[0]!
    const secondCall = mockPutAsset.mock.calls[1]!
    expect(firstCall[0]).toBe(file)
    expect(firstCall[1]).toBe('ws-1')
    expect(firstCall[2]).toBe('asset-1')
    expect(secondCall[0]).toBe(file)
    expect(secondCall[1]).toBe('ws-1')
    expect(secondCall[2]).toBe('asset-1')
  })

  it('does not retry non-retryable client errors', async (): Promise<void> => {
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

  it('throws on invalid maxAttempts', async (): Promise<void> => {
    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    await expect(
      uploadAsset(file, 'ws-1', 'asset-3', { maxAttempts: 0, initialDelayMs: 1, maxDelayMs: 1 }),
    ).rejects.toThrow('maxAttempts must be a positive finite integer')

    expect(mockPutAsset).not.toHaveBeenCalled()
  })

  it('throws on invalid initialDelayMs', async (): Promise<void> => {
    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    await expect(
      uploadAsset(file, 'ws-1', 'asset-4', { maxAttempts: 2, initialDelayMs: -1, maxDelayMs: 1 }),
    ).rejects.toThrow('initialDelayMs must be a finite non-negative number')

    expect(mockPutAsset).not.toHaveBeenCalled()
  })

  it('retries on nullish failures', async (): Promise<void> => {
    mockPutAsset
      .mockRejectedValueOnce(null)
      .mockRejectedValueOnce(undefined)
      .mockResolvedValueOnce({ assetId: 'a1', status: 'READY', deduped: true })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    const result = await uploadAsset(file, 'ws-1', 'asset-5', {
      maxAttempts: 3,
      initialDelayMs: 1,
      maxDelayMs: 1,
    })

    expect(result).toMatchObject({ assetId: 'a1', status: 'READY' })
    expect(mockPutAsset).toHaveBeenCalledTimes(3)
  })

  it('retries on failures without a status field', async (): Promise<void> => {
    mockPutAsset
      .mockRejectedValueOnce(new Error('network error'))
      .mockResolvedValueOnce({ assetId: 'a1', status: 'READY', deduped: true })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    const result = await uploadAsset(file, 'ws-1', 'asset-6', {
      maxAttempts: 2,
      initialDelayMs: 1,
      maxDelayMs: 1,
    })

    expect(result).toMatchObject({ assetId: 'a1', status: 'READY' })
    expect(mockPutAsset).toHaveBeenCalledTimes(2)
  })

  it('does not retry on non-finite status values', async (): Promise<void> => {
    mockPutAsset.mockRejectedValueOnce({ status: NaN })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    await expect(
      uploadAsset(file, 'ws-1', 'asset-7', {
        maxAttempts: 3,
        initialDelayMs: 1,
        maxDelayMs: 1,
      }),
    ).rejects.toMatchObject({ status: NaN })

    expect(mockPutAsset).toHaveBeenCalledTimes(1)
  })

  it('throws on invalid maxDelayMs', async (): Promise<void> => {
    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    await expect(
      uploadAsset(file, 'ws-1', 'asset-8', { maxAttempts: 2, initialDelayMs: 1, maxDelayMs: -1 }),
    ).rejects.toThrow('maxDelayMs must be a finite non-negative number')

    expect(mockPutAsset).not.toHaveBeenCalled()
  })

  it('retries on non-object (primitive) failures', async (): Promise<void> => {
    mockPutAsset
      .mockRejectedValueOnce('something went wrong')
      .mockResolvedValueOnce({ assetId: 'a1', status: 'READY', deduped: true })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    const result = await uploadAsset(file, 'ws-1', 'asset-9', {
      maxAttempts: 2,
      initialDelayMs: 1,
      maxDelayMs: 1,
    })

    expect(result).toMatchObject({ assetId: 'a1', status: 'READY' })
    expect(mockPutAsset).toHaveBeenCalledTimes(2)
  })

  it('retries on non-number status values', async (): Promise<void> => {
    mockPutAsset
      .mockRejectedValueOnce({ status: 'error' })
      .mockResolvedValueOnce({ assetId: 'a1', status: 'READY', deduped: true })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    const result = await uploadAsset(file, 'ws-1', 'asset-10', {
      maxAttempts: 2,
      initialDelayMs: 1,
      maxDelayMs: 1,
    })

    expect(result).toMatchObject({ assetId: 'a1', status: 'READY' })
    expect(mockPutAsset).toHaveBeenCalledTimes(2)
  })

  it('exhausts all retry attempts and throws', async (): Promise<void> => {
    mockPutAsset.mockRejectedValue({ status: 503 })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    await expect(
      uploadAsset(file, 'ws-1', 'asset-11', {
        maxAttempts: 2,
        initialDelayMs: 1,
        maxDelayMs: 1,
      }),
    ).rejects.toMatchObject({ status: 503 })

    expect(mockPutAsset).toHaveBeenCalledTimes(2)
  })
})
