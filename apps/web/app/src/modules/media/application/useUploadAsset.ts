import {
  putAsset,
  type PutAssetResponse,
  type UploadAssetResponse,
} from '@modules/media/services/media-api'

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
  if (err === null || err === undefined) {
    return true
  }
  if (typeof err !== 'object') {
    return true
  }
  const apiErr = err as { status?: unknown }
  if (!('status' in apiErr)) {
    return true
  }
  const status = apiErr.status
  if (typeof status !== 'number') {
    return true
  }
  if (!Number.isFinite(status)) {
    return false
  }
  return status >= 500 && status < 600
}

export type UseUploadAssetReturn = {
  uploadAsset: (
    file: File,
    workspaceId: string,
    assetId?: string,
    options?: UploadAssetOptions,
  ) => Promise<UploadResult>
}

/**
 * Composable that runs the PUT-first media upload flow with bounded retry/backoff.
 */
export function useUploadAsset(): UseUploadAssetReturn {
  async function uploadAsset(
    file: File,
    workspaceId: string,
    assetId?: string,
    options: UploadAssetOptions = {},
  ): Promise<UploadResult> {
    const maxAttempts = options.maxAttempts ?? DEFAULT_MAX_ATTEMPTS
    const initialDelayMs = options.initialDelayMs ?? DEFAULT_INITIAL_DELAY_MS
    const maxDelayMs = options.maxDelayMs ?? DEFAULT_MAX_DELAY_MS

    if (!Number.isInteger(maxAttempts) || maxAttempts <= 0 || !Number.isFinite(maxAttempts)) {
      throw new Error('maxAttempts must be a positive finite integer')
    }
    if (!Number.isFinite(initialDelayMs) || initialDelayMs < 0) {
      throw new Error('initialDelayMs must be a finite non-negative number')
    }
    if (!Number.isFinite(maxDelayMs) || maxDelayMs < 0) {
      throw new Error('maxDelayMs must be a finite non-negative number')
    }

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
