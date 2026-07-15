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
  sourceType: 'UPLOADED' | 'EXTERNAL'
  mediaType: string
  status: MockMediaStatus
  originalFilename: string | null
  fileSizeBytes: number | null
  createdAt: string
  previewUrl?: string | null
  downloadUrl?: string | null
  fileHash?: string
  sourceProvider?: string | null
  externalId?: string | null
  sourceUrl?: string | null
  authorName?: string | null
  authorUrl?: string | null
  metadata?: Record<string, unknown> | null
}

export interface MockUnsplashPhoto {
  externalId: string
  name: string
  previewUrl: string
  sourceUrl: string
  authorName: string
  authorUrl: string
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

// ---------------------------------------------------------------------------
// PR 1 — deferred upload, transition queue, channel/provider state
// ---------------------------------------------------------------------------

/** Record of a single in-flight deferred upload held by the mock layer. */
export interface DeferredUploadRecord {
  assetId: string
  mediaType: string
  status: 'PENDING' | 'UPLOADING' | 'FAILED'
  progress: number
  transitions: Array<{ progress: number; status?: 'PENDING' | 'UPLOADING' | 'FAILED' }>
  /** Resolver callback fired by complete() — fulfils the held route. */
  resolve: (response: { status: number; body: unknown }) => void
  /** Rejector callback fired by failNext() — rejects the held route. */
  reject: (failure: { status: number; code: string }) => void
}

export interface DeferredUploadOptions {
  mediaType?: string
}

export interface DeferredProgress {
  progress: number
  status?: 'PENDING' | 'UPLOADING' | 'FAILED'
}

export interface DeferredCompletion {
  progress: number
  status: 'READY'
}

export interface DeferredFailure {
  status: 500 | 502 | 503 | 504
  code: string
}

export interface IDeferredUploadController {
  enqueueDeferred(input?: DeferredUploadOptions): string
  startUpload(assetId: string): void
  advance(assetId: string, transition: DeferredProgress): void
  complete(assetId: string, completion: DeferredCompletion): Promise<void>
  failNext(assetId: string, failure: DeferredFailure): Promise<DeferredFailure>
  clear(): void
  heldCount(): number
}

/**
 * Holds a binary POST /upload route fulfillment until a test calls
 * `complete()` or `failNext()`. Deterministic: no setTimeout / sleep.
 */
export class DeferredUploadController implements IDeferredUploadController {
  private static counter = 0
  private readonly state: MediaRouteState

  constructor(state: MediaRouteState) {
    this.state = state
  }

  enqueueDeferred(input: DeferredUploadOptions = {}): string {
    const assetId = `deferred-${++DeferredUploadController.counter}-${Date.now()}`
    const record: DeferredUploadRecord = {
      assetId,
      mediaType: input.mediaType ?? 'image/png',
      status: 'PENDING',
      progress: 0,
      transitions: [],
      resolve: () => {
        // Default resolver: tests that don't await complete() still release
        // the route so the test doesn't hang. Replaced by `startUpload`.
      },
      reject: () => {
        // Default rejector. Replaced by `startUpload`.
      },
    }
    this.state.deferredUploads.set(assetId, record)
    return assetId
  }

  startUpload(assetId: string): void {
    const record = this.requireRecord(assetId)
    record.status = 'UPLOADING'
  }

  advance(assetId: string, transition: DeferredProgress): void {
    const record = this.requireRecord(assetId)
    record.progress = transition.progress
    if (transition.status) record.status = transition.status
    record.transitions.push({ progress: transition.progress, status: transition.status })
  }

  async complete(assetId: string, completion: DeferredCompletion): Promise<void> {
    const record = this.requireRecord(assetId)
    record.progress = completion.progress
    record.status = 'UPLOADING'
    record.resolve({
      status: 200,
      body: {
        assetId: record.assetId,
        workspaceId: MOCK_WORKSPACE_ID,
        status: 'READY',
        mediaType: record.mediaType,
        detectedMediaType: record.mediaType,
        deduped: false,
        fileSizeBytes: 0,
        createdAt: new Date().toISOString(),
      },
    })
    this.state.deferredUploads.delete(assetId)
  }

  async failNext(assetId: string, failure: DeferredFailure): Promise<DeferredFailure> {
    const record = this.requireRecord(assetId)
    record.status = 'FAILED'
    record.reject(failure)
    this.state.deferredUploads.delete(assetId)
    return failure
  }

  clear(): void {
    for (const record of this.state.deferredUploads.values()) {
      // Resolve with a 200 + cancelled so any awaiting routes release.
      record.resolve({ status: 200, body: { cancelled: true } })
    }
    this.state.deferredUploads.clear()
  }

  heldCount(): number {
    return this.state.deferredUploads.size
  }

  private requireRecord(assetId: string): DeferredUploadRecord {
    const record = this.state.deferredUploads.get(assetId)
    if (!record) {
      throw new Error(`DeferredUploadController: assetId ${assetId} is not enqueued`)
    }
    return record
  }
}

/** FIFO queue of arbitrary transitions keyed by assetId. */
export class TransitionQueue<T> {
  private readonly queues = new Map<string, T[]>()

  enqueue(assetId: string, value: T): void {
    let queue = this.queues.get(assetId)
    if (!queue) {
      queue = []
      this.queues.set(assetId, queue)
    }
    queue.push(value)
  }

  take(assetId: string): T | null {
    const queue = this.queues.get(assetId)
    if (!queue || queue.length === 0) return null
    return queue.shift() ?? null
  }

  size(assetId: string): number {
    return this.queues.get(assetId)?.length ?? 0
  }

  reset(): void {
    this.queues.clear()
  }
}

/**
 * Per-test override for the channel-limit returned by the mocked channels
 * endpoint. When set, the mocked route responds with channels whose
 * `maxAttachments` field equals this value.
 */
export interface IMockChannelsProvider {
  setMaxAttachments(limit: number | null): void
  getMaxAttachments(): number | null
  reset(): void
}

export class MockChannelsProvider implements IMockChannelsProvider {
  private readonly state: MediaRouteState

  constructor(state: MediaRouteState) {
    this.state = state
  }

  setMaxAttachments(limit: number | null): void {
    this.state.channelsMaxAttachments = limit
  }

  getMaxAttachments(): number | null {
    return this.state.channelsMaxAttachments
  }

  reset(): void {
    this.state.channelsMaxAttachments = null
  }
}

/**
 * Controls the mocked `POST /api/publishing/publications` response used by
 * the composer happy-path. The default behavior echoes the request body and
 * returns `201 Created`; tests can call `setFailure(...)` to simulate a
 * server-side rejection (e.g. validation, scheduling conflict).
 */
export interface IMockPublicationPostProvider {
  setFailure(status: number, body?: unknown): void
  clearFailure(): void
  reset(): void
}

export class MockPublicationPostProvider implements IMockPublicationPostProvider {
  private readonly state: MediaRouteState

  constructor(state: MediaRouteState) {
    this.state = state
  }

  setFailure(status: number, body: unknown = null): void {
    this.state.publicationPostStatus = status
    this.state.publicationPostFailureBody = body
  }

  clearFailure(): void {
    this.state.publicationPostStatus = 201
    this.state.publicationPostFailureBody = null
  }

  reset(): void {
    this.clearFailure()
    this.state.publicationPostCount = 0
  }
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

function parsePostData(request: import('@playwright/test').Request): Record<string, unknown> {
  const data = request.postData()
  if (!data) return {}
  try {
    return JSON.parse(data) as Record<string, unknown>
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
  // PR 1 — deferred upload and channel-limit state
  deferredUploads: Map<string, DeferredUploadRecord> = new Map()
  channelsMaxAttachments: number | null = null
  unsplashSearches: string[] = []
  unsplashImportCount = 0
  unsplashImportFailures = new Set<string>()
  // PR 2 — composer publication POST state.
  // Default behavior is "echo the request body as 201 Created". Tests that
  // need to exercise failure paths call `publicationPostProvider.setFailure(...)`
  // to enqueue a one-shot status + body before the request fires.
  publicationPostCount = 0
  publicationPostStatus = 201
  publicationPostFailureBody: unknown = null

  reset(): void {
    this.assets = []
    this.pendingUploads = {}
    this.listOverride = null
    this.putQueue = []
    this.putCount = 0
    this.uploadPostCount = 0
    this.deleteCount = 0
    this.getCount = 0
    this.deferredUploads = new Map()
    this.channelsMaxAttachments = null
    this.unsplashSearches = []
    this.unsplashImportCount = 0
    this.unsplashImportFailures = new Set()
    this.publicationPostCount = 0
    this.publicationPostStatus = 201
    this.publicationPostFailureBody = null
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
      sourceProvider: input.sourceProvider,
      externalId: input.externalId,
      sourceUrl: input.sourceUrl,
      authorName: input.authorName,
      authorUrl: input.authorUrl,
      metadata: input.metadata,
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

  // PR 1 — defer response if the test held this asset id via DeferredUploadController.
  const deferred = state.deferredUploads.get(assetId)
  if (deferred) {
    deferred.status = 'UPLOADING'
    deferred.progress = 0
    const { promise, resolve, reject } = deferredPromise<{ status: number; body: unknown }>()
    deferred.resolve = resolve
    deferred.reject = (failure) => reject({ status: failure.status, code: failure.code })
    const result = await promise
    if ('code' in (result as { code?: string })) {
      const failure = result as { status: number; code: string }
      await route.fulfill(
        json(failure.status, {
          errorCode: failure.code,
          message: 'Media service unavailable.',
        }),
      )
      return
    }
    const response = result as { status: number; body: unknown }
    const asset = state.getAsset(assetId)
    if (asset) {
      asset.status = 'READY'
      asset.fileSizeBytes = postData.byteLength
    }
    await route.fulfill(json(response.status, response.body))
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

function deferredPromise<T>(): {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (reason?: unknown) => void
} {
  let resolveFn: (value: T) => void = () => {}
  let rejectFn: (reason?: unknown) => void = () => {}
  const promise = new Promise<T>((resolve, reject) => {
    resolveFn = resolve
    rejectFn = reject
  })
  return { promise, resolve: resolveFn, reject: rejectFn }
}

/**
 * Creates the mocked channels response payload for the configured attachment limit.
 *
 * @param maxAttachments - The maximum number of attachments, or `null` to use 10.
 * @returns An object containing the mocked LinkedIn channel.
 */
export function defaultChannelsPayload(maxAttachments: number | null) {
  return {
    channels: [
      {
        id: 'sa-linkedin-001',
        accountId: 'sa-linkedin-001',
        workspaceId: MOCK_WORKSPACE_ID,
        name: 'Dev User',
        provider: 'linkedin',
        handle: 'Dev User',
        status: 'ACTIVE',
        maxAttachments: maxAttachments ?? 10,
      },
    ],
  }
}

/**
 * Registers mock routes for Unsplash photo operations and media asset endpoints.
 *
 * @param context - The browser context in which to register the routes
 * @param state - Shared mutable state used by the mocked routes
 */
export async function registerMediaMocks(
  context: BrowserContext,
  state: MediaRouteState,
): Promise<void> {
  const editorialPhotos: MockUnsplashPhoto[] = [
    {
      externalId: 'editorial-workspace',
      name: 'Creative workspace',
      previewUrl: 'https://images.unsplash.com/editorial-workspace',
      sourceUrl:
        'https://unsplash.com/photos/editorial-workspace?utm_source=profile_tailors&utm_medium=referral',
      authorName: 'Editorial Author',
      authorUrl:
        'https://unsplash.com/@editorial-author?utm_source=profile_tailors&utm_medium=referral',
    },
  ]
  const searchPhotos: MockUnsplashPhoto[] = [
    {
      externalId: 'remote-work',
      name: 'Remote work',
      previewUrl: 'https://images.unsplash.com/remote-work',
      sourceUrl:
        'https://unsplash.com/photos/remote-work?utm_source=profile_tailors&utm_medium=referral',
      authorName: 'Search Author',
      authorUrl:
        'https://unsplash.com/@search-author?utm_source=profile_tailors&utm_medium=referral',
    },
    {
      externalId: 'import-fails',
      name: 'Import failure example',
      previewUrl: 'https://images.unsplash.com/import-fails',
      sourceUrl:
        'https://unsplash.com/photos/import-fails?utm_source=profile_tailors&utm_medium=referral',
      authorName: 'Retry Author',
      authorUrl:
        'https://unsplash.com/@retry-author?utm_source=profile_tailors&utm_medium=referral',
    },
  ]

  await context.route('https://images.unsplash.com/**', (route) => {
    previewAsset(route)
  })

  await context.route('**/api/media/providers/unsplash/photos**', (route) => {
    const request = route.request()
    const url = new URL(request.url())

    if (request.method() === 'GET') {
      const query = url.searchParams.get('query')?.trim() ?? ''
      state.unsplashSearches.push(query)

      if (query === 'provider-error') {
        route.fulfill(json(502, { title: 'Unsplash unavailable', detail: 'Try again later.' }))
        return
      }

      route.fulfill(
        json(200, {
          photos: query === '' ? editorialPhotos : query === 'no-results' ? [] : searchPhotos,
        }),
      )
      return
    }

    if (request.method() === 'POST' && url.pathname.endsWith('/import')) {
      const externalId = url.pathname.split('/').at(-2) ?? ''
      state.unsplashImportCount += 1
      if (state.unsplashImportFailures.has(externalId)) {
        route.fulfill(json(502, { title: 'Unsplash import failed', detail: 'Try again later.' }))
        return
      }

      const photo = [...editorialPhotos, ...searchPhotos].find(
        (candidate) => candidate.externalId === externalId,
      )
      if (!photo) {
        route.fulfill(
          json(404, { title: 'Photo not found', detail: 'The photo is no longer available.' }),
        )
        return
      }

      const asset = state.seedAsset({
        assetId: `unsplash-${externalId}`,
        sourceType: 'EXTERNAL',
        mediaType: 'image/jpeg',
        originalFilename: `${externalId}.jpg`,
        sourceProvider: 'unsplash',
        externalId,
        sourceUrl: photo.sourceUrl,
        authorName: photo.authorName,
        authorUrl: photo.authorUrl,
        metadata: { attribution: `Photo by ${photo.authorName} on Unsplash` },
      })
      route.fulfill(json(201, asset))
      return
    }

    route.fulfill(json(405, { title: 'Method not allowed' }))
  })

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

  // PR 2 — composer publication POST. The composer lane submits here on
  // happy-path scheduling. Without a route the request falls through to the
  // Vite proxy and ECONNREFUSEs because no backend is running in this config.
  await context.route('**/api/publishing/publications', async (route) => {
    const request = route.request()
    if (request.method() !== 'POST') {
      await route.fallback()
      return
    }
    state.publicationPostCount += 1
    const status = state.publicationPostStatus
    if (status !== 201) {
      const failureBody = state.publicationPostFailureBody ?? {
        title: 'Publication rejected',
        detail: 'Mocked failure.',
      }
      await route.fulfill(json(status, failureBody))
      return
    }
    const body = parsePostData(request)
    const publication = {
      id: `mock-publication-${Date.now()}`,
      publicationId: `mock-publication-${Date.now()}`,
      workspaceId: body?.workspaceId ?? MOCK_WORKSPACE_ID,
      socialAccountId: body?.socialAccountId ?? 'sa-linkedin-001',
      provider: body?.provider ?? 'linkedin',
      status: 'QUEUED',
      scheduleMode: body?.scheduleMode ?? 'NOW',
      priority: body?.priority ?? false,
      title: body?.title ?? null,
      bodyText: body?.bodyText ?? null,
      scheduledFor: body?.scheduledFor ?? null,
      nextSlotAfter: body?.nextSlotAfter ?? null,
      assetIds: body?.assetIds ?? [],
      hasConflict: false,
      conflictingPublicationIds: [],
    }
    await route.fulfill(json(201, publication))
  })

  // PR 2 — composer channels GET. The composer lane needs at least one
  // ACTIVE LinkedIn channel for `canSubmit` to enable the submit button.
  // Without this handler the request 401s on the Vite proxy and the
  // composer never reaches the publication POST above.
  await context.route('**/api/publishing/channels**', async (route) => {
    const request = route.request()
    if (request.method() !== 'GET') {
      await route.fallback()
      return
    }
    await route.fulfill(json(200, defaultChannelsPayload(state.channelsMaxAttachments)))
  })
}

/**
 * PR 1 — register conditional routes for the channels provider and the
 * Unsplash provider flag. Callers should `use()` the returned cleanup
 * function in their fixture teardown if they want a finer-grained reset
 * than the default per-context `unrouteAll` in `media-mocked-test.ts`.
 */
export interface RegisteredComposerControls {
  unregister: () => Promise<void>
}

export async function registerComposerControls(
  context: BrowserContext,
  state: MediaRouteState,
): Promise<RegisteredComposerControls> {
  const channelsHandler = (route: Route): void => {
    if (state.channelsMaxAttachments === null) {
      // No override registered: let the request fall through to the next
      // route handler (typically the scheduler mock's /api/publishing/channels).
      route.fallback().catch(() => {
        route.fulfill(json(200, defaultChannelsPayload(null)))
      })
      return
    }
    route.fulfill(json(200, defaultChannelsPayload(state.channelsMaxAttachments)))
  }

  await context.route('**/api/publishing/channels**', channelsHandler)

  return {
    unregister: async () => {
      await context.unroute('**/api/publishing/channels**', channelsHandler)
    },
  }
}

/**
 * Apply seeded channels directly to the Pinia publishing store.
 * Used by composer tests that need channels loaded before the modal opens.
 */
type VueAppElement = Element & {
  __vue_app__?: {
    config?: {
      globalProperties?: {
        $pinia?: {
          _s?: Map<string, unknown>
        }
      }
    }
  }
}

type SeededPublishingChannel = {
  id: string
  accountId: string
  name: string
  provider: 'linkedin'
  avatar: string
  handle: string
  status: 'ACTIVE'
  maxAttachments: number
}

type PublishingStoreWithChannels = {
  channels: SeededPublishingChannel[]
}

export async function applySeededChannelsToStore(
  page: import('@playwright/test').Page,
  maxAttachments: number | null = null,
): Promise<void> {
  const channel: SeededPublishingChannel = {
    id: 'sa-linkedin-001',
    accountId: 'sa-linkedin-001',
    name: 'Dev User',
    provider: 'linkedin',
    avatar: '',
    handle: 'Dev User',
    status: 'ACTIVE',
    maxAttachments: maxAttachments ?? 10,
  }
  await page.evaluate((ch) => {
    const app = (document.querySelector('#app') as VueAppElement | null)?.__vue_app__
    const pinia = app?.config?.globalProperties?.$pinia
    const publishingStore = pinia?._s?.get('publishing') as PublishingStoreWithChannels | undefined
    if (!publishingStore) {
      throw new Error('Publishing store not available')
    }
    publishingStore.channels = [ch]
  }, channel)
}
