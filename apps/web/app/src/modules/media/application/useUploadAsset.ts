import { putAsset, type PutAssetResponse, type UploadAssetResponse } from '@modules/media/services/media-api'

type UploadResult = PutAssetResponse | UploadAssetResponse

export type UploadAssetOptions = {
  maxAttempts?: number
  initialDelayMs?: number
  maxDelayMs?: number
}

const DEFAULT_MAX_ATTEMPTS = 3
const DEFAULT_INITIAL_DELAY_MS = 2_000
const DEFAULT_MAX_DELAY_MS = 30_000

function sleep(ms: number): Promise<void> {
  return new Promise<void>((resolve) => setTimeout(resolve, ms))
}

function backoffDelay(attempt: number, initialDelayMs: number, maxDelayMs: number): number {
  const delay = initialDelayMs * 2 ** Math.max(0, attempt - 1)
  return Math.min(delay, maxDelayMs)
}

function isRetryable(err: unknown): boolean {
  const apiErr = err as { status?: number }
  return apiErr.status === undefined || apiErr.status >= 500
}

/**
 * Composable that runs the PUT-first media upload flow with bounded retry/backoff.
 */
export function useUploadAsset() {
  async function uploadAsset(
    file: File,
    workspaceId: string,
    assetId?: string,
    options: UploadAssetOptions = {},
  ): Promise<UploadResult> {
    const maxAttempts = options.maxAttempts ?? DEFAULT_MAX_ATTEMPTS
    const initialDelayMs = options.initialDelayMs ?? DEFAULT_INITIAL_DELAY_MS
    const maxDelayMs = options.maxDelayMs ?? DEFAULT_MAX_DELAY_MS

    let lastError: unknown

    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return await putAsset(file, workspaceId, assetId)
      } catch (err) {
        lastError = err
        if (!isRetryable(err) || attempt === maxAttempts) {
          throw err
        }
        await sleep(backoffDelay(attempt, initialDelayMs, maxDelayMs))
      }
    }

    throw lastError instanceof Error ? lastError : new Error('Upload failed')
  }

  return {
    uploadAsset,
  }
}
