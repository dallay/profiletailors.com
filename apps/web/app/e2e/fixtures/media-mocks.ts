import type { BrowserContext, Route } from '@playwright/test'

export type MockMediaStatus =
  | 'PENDING_UPLOAD'
  | 'UPLOADING'
  | 'READY'
  | 'FAILED'
  | 'DELETED'
  | 'PROCESSING'

export interface MockMediaAsset {
  assetId: string
  workspaceId: string
  sourceType: 'UPLOADED'
  mediaType: string
  status: MockMediaStatus
  originalFilename: string | null
  fileSizeBytes: number | null
  createdAt: string
  previewUrl?: string | null
  downloadUrl?: string | null
  fileHash?: string
}

export interface MockListResponse {
  status?: number
  body?: unknown
  headers?: Record<string, string>
}

export interface MockPutResponse {
  status: 200 | 201 | 202 | 409 | 429 | 500 | 503
  retryAfterSeconds?: number
  body?: Partial<{
    assetId: string
    workspaceId: string
    status: 'PENDING_UPLOAD' | 'READY' | 'WAITING_FOR_BLOB'
    mediaType: string
    deduped: boolean
    uploadUrl: string
    createdAt: string
    errorCode: string
    message: string
    existingFileHash: string
  }>
}

interface PutRequestBody {
  fileHash?: string
  fileSizeBytes?: number
  declaredMediaType?: string
  originalFilename?: string
}

const MOCK_WORKSPACE_ID = 'workspace-001'

function contentType(headers: Record<string, string> = {}): Record<string, string> {
  return { 'content-type': 'application/vnd.api.v1+json', ...headers }
}

function json(status: number, body: unknown, headers?: Record<string, string>) {
  return {
    status,
    headers: contentType(headers),
    body: JSON.stringify(body),
  }
}

function parseAssetId(url: string): string | null {
  const match = /\/api\/media\/assets\/([^/?]+)/.exec(url)
  return match?.[1] ? decodeURIComponent(match[1]) : null
}

function parsePutBody(route: Route): PutRequestBody {
  const data = route.request().postData()
  if (!data) return {}
  try {
    return JSON.parse(data) as PutRequestBody
  } catch {
    return {}
  }
}

function defaultErrorBody(status: number) {
  return {
    errorCode: status >= 500 ? 'MEDIA_SERVICE_UNAVAILABLE' : 'MEDIA_REQUEST_FAILED',
    message: status >= 500 ? 'Media service unavailable.' : 'Media request failed.',
  }
}

export class MediaRouteState {
  assets: MockMediaAsset[] = []
  pendingUploads: Record<string, PutRequestBody> = {}
  listOverride: MockListResponse | null = null
  putQueue: MockPutResponse[] = []
  putCount = 0
  uploadPostCount = 0
  deleteCount = 0
  getCount = 0

  reset(): void {
    this.assets = []
    this.pendingUploads = {}
    this.listOverride = null
    this.putQueue = []
    this.putCount = 0
    this.uploadPostCount = 0
    this.deleteCount = 0
    this.getCount = 0
  }

  enqueuePut(response: MockPutResponse): void {
    this.putQueue.push(response)
  }

  setListResponse(response: MockListResponse): void {
    this.listOverride = response
  }

  seedAsset(input: Partial<MockMediaAsset> = {}): MockMediaAsset {
    const assetId = input.assetId ?? `asset-${this.assets.length + 1}-${Date.now()}`
    const asset: MockMediaAsset = {
      assetId,
      workspaceId: input.workspaceId ?? MOCK_WORKSPACE_ID,
      sourceType: 'UPLOADED',
      mediaType: input.mediaType ?? 'image/png',
      status: input.status ?? 'READY',
      originalFilename: input.originalFilename ?? `${assetId}.png`,
      fileSizeBytes: input.fileSizeBytes ?? 68,
      createdAt: input.createdAt ?? new Date(Date.now() - this.assets.length * 1_000).toISOString(),
      previewUrl: input.previewUrl ?? `/api/media/assets/${assetId}/preview`,
      downloadUrl: input.downloadUrl ?? `/api/media/assets/${assetId}/preview`,
      fileHash: input.fileHash,
    }
    this.upsertAsset(asset)
    return asset
  }

  upsertAsset(asset: MockMediaAsset): void {
    const index = this.assets.findIndex((candidate) => candidate.assetId === asset.assetId)
    if (index >= 0) {
      this.assets[index] = asset
      return
    }
    this.assets.unshift(asset)
  }

  getAsset(assetId: string): MockMediaAsset | undefined {
    return this.assets.find((asset) => asset.assetId === assetId)
  }

  assetsByStatus(status: string): MockMediaAsset[] {
    const wanted = status.split(',').filter(Boolean)
    if (wanted.length === 0) return this.assets
    return this.assets.filter((asset) => wanted.includes(asset.status))
  }
}

export function resetMediaMocks(): void {
  // Media mocks are per-context/per-test. This no-op function exists to mirror
  // scheduler mocks and make explicit resets readable in test setup.
}

function listAssets(route: Route, state: MediaRouteState): void {
  if (state.listOverride) {
    const status = state.listOverride.status ?? 200
    route.fulfill(
      json(
        status,
        state.listOverride.body ?? { assets: [], nextCursor: null },
        state.listOverride.headers,
      ),
    )
    return
  }

  const url = new URL(route.request().url())
  const statuses = url.searchParams.get('status') ?? ''
  const pageSize = Number(url.searchParams.get('pageSize') ?? '50')
  const cursor = Number(url.searchParams.get('cursor') ?? '0')
  const filtered = state.assetsByStatus(statuses)
  const page = filtered.slice(cursor, cursor + pageSize)
  const nextCursor = cursor + pageSize < filtered.length ? String(cursor + pageSize) : null

  route.fulfill(json(200, { assets: page, nextCursor }))
}

function putAsset(route: Route, state: MediaRouteState, assetId: string): void {
  state.putCount += 1
  const body = parsePutBody(route)
  const queued = state.putQueue.shift()
  const status = queued?.status ?? 201
  const responseAssetId = queued?.body?.assetId ?? assetId
  const mediaType = queued?.body?.mediaType ?? body.declaredMediaType ?? 'image/png'
  const createdAt = queued?.body?.createdAt ?? new Date().toISOString()

  if (status === 201) {
    state.pendingUploads[responseAssetId] = body
    const pendingBody = {
      assetId: responseAssetId,
      workspaceId: MOCK_WORKSPACE_ID,
      status: 'PENDING_UPLOAD',
      mediaType,
      deduped: false,
      uploadUrl: `/api/media/assets/${responseAssetId}/upload`,
      createdAt,
      ...queued?.body,
    }
    route.fulfill(json(201, pendingBody))
    return
  }

  if (status === 200) {
    const existing = state.getAsset(responseAssetId)
    if (!existing) {
      state.seedAsset({
        assetId: responseAssetId,
        mediaType,
        originalFilename: body.originalFilename,
        fileSizeBytes: body.fileSizeBytes,
        fileHash: body.fileHash,
      })
    }
    route.fulfill(
      json(200, {
        assetId: responseAssetId,
        workspaceId: MOCK_WORKSPACE_ID,
        status: 'READY',
        mediaType,
        deduped: true,
        createdAt,
        ...queued?.body,
      }),
    )
    return
  }

  if (status === 202) {
    route.fulfill(
      json(
        202,
        {
          assetId: responseAssetId,
          workspaceId: MOCK_WORKSPACE_ID,
          status: 'WAITING_FOR_BLOB',
          mediaType,
          deduped: false,
          createdAt,
          ...queued?.body,
        },
        { 'Retry-After': String(queued?.retryAfterSeconds ?? 1) },
      ),
    )
    return
  }

  if (status === 409) {
    route.fulfill(
      json(409, {
        errorCode: 'ASSET_HASH_MISMATCH',
        message: 'Asset already exists with a different file hash.',
        ...queued?.body,
      }),
    )
    return
  }

  if (status === 429) {
    const retryAfter = queued?.retryAfterSeconds ?? 60
    route.fulfill(
      json(
        429,
        {
          errorCode: 'RATE_LIMIT_EXCEEDED',
          message: 'Hourly media creation limit exceeded.',
          retryAfterSeconds: retryAfter,
          ...queued?.body,
        },
        { 'Retry-After': String(retryAfter) },
      ),
    )
    return
  }

  route.fulfill(json(status, { ...defaultErrorBody(status), ...queued?.body }))
}

async function uploadAsset(route: Route, state: MediaRouteState, assetId: string): Promise<void> {
  state.uploadPostCount += 1
  const postData = route.request().postDataBuffer()
  if (!postData || postData.byteLength === 0) {
    await route.fulfill(
      json(422, { errorCode: 'EMPTY_UPLOAD', message: 'Upload bytes are empty.' }),
    )
    return
  }

  const pending = state.pendingUploads[assetId]
  const asset = state.getAsset(assetId)
  const readyAsset =
    asset ??
    state.seedAsset({
      assetId,
      mediaType: pending?.declaredMediaType ?? 'image/png',
      originalFilename: pending?.originalFilename ?? `${assetId}.png`,
      status: 'READY',
      fileSizeBytes: postData.byteLength,
      fileHash: pending?.fileHash,
    })

  readyAsset.status = 'READY'
  readyAsset.mediaType = pending?.declaredMediaType ?? readyAsset.mediaType
  readyAsset.originalFilename = pending?.originalFilename ?? readyAsset.originalFilename
  readyAsset.fileSizeBytes = postData.byteLength
  state.upsertAsset(readyAsset)

  await route.fulfill(
    json(200, {
      assetId: readyAsset.assetId,
      workspaceId: readyAsset.workspaceId,
      status: 'READY',
      mediaType: readyAsset.mediaType,
      detectedMediaType: readyAsset.mediaType,
      deduped: false,
      fileSizeBytes: readyAsset.fileSizeBytes,
      createdAt: readyAsset.createdAt,
    }),
  )
}

function getAsset(route: Route, state: MediaRouteState, assetId: string): void {
  state.getCount += 1
  const asset = state.getAsset(assetId)
  if (!asset) {
    route.fulfill(json(404, { title: 'Asset not found', detail: 'Media asset not found.' }))
    return
  }
  route.fulfill(json(200, asset))
}

function deleteAsset(route: Route, state: MediaRouteState, assetId: string): void {
  state.deleteCount += 1
  state.assets = state.assets.filter((asset) => asset.assetId !== assetId)
  route.fulfill(json(200, { deleted: true, blobScheduledForGC: false }))
}

function previewAsset(route: Route): void {
  route.fulfill({
    status: 200,
    contentType: 'image/png',
    body: Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=',
      'base64',
    ),
  })
}

export async function registerMediaMocks(
  context: BrowserContext,
  state: MediaRouteState,
): Promise<void> {
  await context.route('**/api/media/assets**', async (route) => {
    const request = route.request()
    const method = request.method()
    const url = request.url()
    const assetId = parseAssetId(url)

    if (method === 'GET' && url.includes('/preview')) {
      previewAsset(route)
      return
    }

    if (method === 'GET' && !assetId) {
      listAssets(route, state)
      return
    }

    if (!assetId) {
      await route.fulfill(json(400, { title: 'Bad request', detail: 'Missing asset id.' }))
      return
    }

    if (method === 'PUT') {
      putAsset(route, state, assetId)
      return
    }

    if (method === 'POST' && url.endsWith('/upload')) {
      await uploadAsset(route, state, assetId)
      return
    }

    if (method === 'GET') {
      getAsset(route, state, assetId)
      return
    }

    if (method === 'DELETE') {
      deleteAsset(route, state, assetId)
      return
    }

    await route.fulfill(
      json(405, { title: 'Method not allowed', detail: `Unsupported method ${method}.` }),
    )
  })
}
