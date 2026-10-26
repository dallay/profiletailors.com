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
    mockPutAsset.mockRejectedValueOnce({ status: 503 }).mockResolvedValueOnce({
      assetId: 'bd526c7b-ec12-5f9f-a8ba-f27b4d769b3e',
      status: 'READY',
      deduped: true,
    })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    const result = await uploadAsset(
      file,
      '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      '8bdec504-2ed1-59e4-8a4a-c068789d03d2',
      {
        maxAttempts: 2,
        initialDelayMs: 1,
        maxDelayMs: 1,
      },
    )

    expect(result).toMatchObject({
      assetId: 'bd526c7b-ec12-5f9f-a8ba-f27b4d769b3e',
      status: 'READY',
    })
    expect(mockPutAsset).toHaveBeenCalledTimes(2)

    const firstCall = mockPutAsset.mock.calls[0]!
    const secondCall = mockPutAsset.mock.calls[1]!
    expect(firstCall[0]).toBe(file)
    expect(firstCall[1]).toBe('820c22d8-9ce7-5e76-867f-04bac4e53b59')
    expect(firstCall[2]).toBe('8bdec504-2ed1-59e4-8a4a-c068789d03d2')
    expect(secondCall[0]).toBe(file)
    expect(secondCall[1]).toBe('820c22d8-9ce7-5e76-867f-04bac4e53b59')
    expect(secondCall[2]).toBe('8bdec504-2ed1-59e4-8a4a-c068789d03d2')
  })

  it('does not retry non-retryable client errors', async (): Promise<void> => {
    mockPutAsset.mockRejectedValueOnce({ status: 409 })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    await expect(
      uploadAsset(
        file,
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        'b909d06f-92ea-5671-9f13-73475a89080e',
        {
          maxAttempts: 3,
          initialDelayMs: 1,
          maxDelayMs: 1,
        },
      ),
    ).rejects.toMatchObject({ status: 409 })

    expect(mockPutAsset).toHaveBeenCalledTimes(1)
  })

  it('throws on invalid maxAttempts', async (): Promise<void> => {
    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    await expect(
      uploadAsset(
        file,
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        '24add7a3-5877-5ff8-a4cb-11ff965d127f',
        { maxAttempts: 0, initialDelayMs: 1, maxDelayMs: 1 },
      ),
    ).rejects.toThrow('maxAttempts must be a positive finite integer')

    expect(mockPutAsset).not.toHaveBeenCalled()
  })

  it('throws on invalid initialDelayMs', async (): Promise<void> => {
    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    await expect(
      uploadAsset(
        file,
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        'f0465146-355a-5da1-b916-5770deff3ff2',
        { maxAttempts: 2, initialDelayMs: -1, maxDelayMs: 1 },
      ),
    ).rejects.toThrow('initialDelayMs must be a finite non-negative number')

    expect(mockPutAsset).not.toHaveBeenCalled()
  })

  it('retries on nullish failures', async (): Promise<void> => {
    mockPutAsset
      .mockRejectedValueOnce(null)
      .mockRejectedValueOnce(undefined)
      .mockResolvedValueOnce({
        assetId: 'bd526c7b-ec12-5f9f-a8ba-f27b4d769b3e',
        status: 'READY',
        deduped: true,
      })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    const result = await uploadAsset(
      file,
      '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      'd9c82a9b-8776-5d39-b0a5-bbc47895211f',
      {
        maxAttempts: 3,
        initialDelayMs: 1,
        maxDelayMs: 1,
      },
    )

    expect(result).toMatchObject({
      assetId: 'bd526c7b-ec12-5f9f-a8ba-f27b4d769b3e',
      status: 'READY',
    })
    expect(mockPutAsset).toHaveBeenCalledTimes(3)
  })

  it('retries on failures without a status field', async (): Promise<void> => {
    mockPutAsset.mockRejectedValueOnce(new Error('network error')).mockResolvedValueOnce({
      assetId: 'bd526c7b-ec12-5f9f-a8ba-f27b4d769b3e',
      status: 'READY',
      deduped: true,
    })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    const result = await uploadAsset(
      file,
      '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      '3e3a6ec9-33c3-5e6b-bf72-e7d6a7a7c2f6',
      {
        maxAttempts: 2,
        initialDelayMs: 1,
        maxDelayMs: 1,
      },
    )

    expect(result).toMatchObject({
      assetId: 'bd526c7b-ec12-5f9f-a8ba-f27b4d769b3e',
      status: 'READY',
    })
    expect(mockPutAsset).toHaveBeenCalledTimes(2)
  })

  it('does not retry on non-finite status values', async (): Promise<void> => {
    mockPutAsset.mockRejectedValueOnce({ status: NaN })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    await expect(
      uploadAsset(
        file,
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        '4dcb3fbc-d959-5c03-acd2-357a1a49a27f',
        {
          maxAttempts: 3,
          initialDelayMs: 1,
          maxDelayMs: 1,
        },
      ),
    ).rejects.toMatchObject({ status: NaN })

    expect(mockPutAsset).toHaveBeenCalledTimes(1)
  })

  it('throws on invalid maxDelayMs', async (): Promise<void> => {
    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    await expect(
      uploadAsset(
        file,
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        'f19ac77d-4842-5744-8fa0-24017d1ed5aa',
        { maxAttempts: 2, initialDelayMs: 1, maxDelayMs: -1 },
      ),
    ).rejects.toThrow('maxDelayMs must be a finite non-negative number')

    expect(mockPutAsset).not.toHaveBeenCalled()
  })

  it('retries on non-object (primitive) failures', async (): Promise<void> => {
    mockPutAsset.mockRejectedValueOnce('something went wrong').mockResolvedValueOnce({
      assetId: 'bd526c7b-ec12-5f9f-a8ba-f27b4d769b3e',
      status: 'READY',
      deduped: true,
    })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    const result = await uploadAsset(
      file,
      '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      '81bde866-3395-5cdf-b9be-88dd528d4323',
      {
        maxAttempts: 2,
        initialDelayMs: 1,
        maxDelayMs: 1,
      },
    )

    expect(result).toMatchObject({
      assetId: 'bd526c7b-ec12-5f9f-a8ba-f27b4d769b3e',
      status: 'READY',
    })
    expect(mockPutAsset).toHaveBeenCalledTimes(2)
  })

  it('retries on non-number status values', async (): Promise<void> => {
    mockPutAsset.mockRejectedValueOnce({ status: 'error' }).mockResolvedValueOnce({
      assetId: 'bd526c7b-ec12-5f9f-a8ba-f27b4d769b3e',
      status: 'READY',
      deduped: true,
    })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    const result = await uploadAsset(
      file,
      '820c22d8-9ce7-5e76-867f-04bac4e53b59',
      '43fa03b7-c3d5-5aad-97bf-b966670b2c9b',
      {
        maxAttempts: 2,
        initialDelayMs: 1,
        maxDelayMs: 1,
      },
    )

    expect(result).toMatchObject({
      assetId: 'bd526c7b-ec12-5f9f-a8ba-f27b4d769b3e',
      status: 'READY',
    })
    expect(mockPutAsset).toHaveBeenCalledTimes(2)
  })

  it('exhausts all retry attempts and throws', async (): Promise<void> => {
    mockPutAsset.mockRejectedValue({ status: 503 })

    const { uploadAsset } = useUploadAsset()
    const file = new File(['hello'], 'a.jpg', { type: 'image/jpeg' })

    await expect(
      uploadAsset(
        file,
        '820c22d8-9ce7-5e76-867f-04bac4e53b59',
        '18af3602-42d8-5840-bef4-cc07f933300e',
        {
          maxAttempts: 2,
          initialDelayMs: 1,
          maxDelayMs: 1,
        },
      ),
    ).rejects.toMatchObject({ status: 503 })

    expect(mockPutAsset).toHaveBeenCalledTimes(2)
  })
})
