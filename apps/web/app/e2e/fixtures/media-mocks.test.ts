import { describe, it, expect, beforeEach } from 'vitest'
import {
  MediaRouteState,
  DeferredUploadController,
  TransitionQueue,
  MockChannelsProvider,
  MockProviderFlag,
} from './media-mocks'

describe('DeferredUploadController (PR 1)', () => {
  let state: MediaRouteState
  let controller: DeferredUploadController

  beforeEach(() => {
    state = new MediaRouteState()
    controller = new DeferredUploadController(state)
  })

  it('returns a PENDING asset id from enqueueDeferred', () => {
    const assetId = controller.enqueueDeferred({ mediaType: 'image/png' })
    expect(assetId).toMatch(/^deferred-/)
    expect(state.deferredUploads.has(assetId)).toBe(true)
    expect(state.deferredUploads.get(assetId)?.status).toBe('PENDING')
  })

  it('marks an in-flight upload via startUpload', () => {
    const assetId = controller.enqueueDeferred({ mediaType: 'image/png' })
    controller.startUpload(assetId)
    expect(state.deferredUploads.get(assetId)?.status).toBe('UPLOADING')
  })

  it('completes a held upload and clears it from held', async () => {
    const assetId = controller.enqueueDeferred({ mediaType: 'image/png' })
    controller.startUpload(assetId)
    const pending = controller.complete(assetId, { progress: 100, status: 'READY' })
    await pending
    expect(controller.heldCount()).toBe(0)
    expect(state.deferredUploads.has(assetId)).toBe(false)
  })

  it('fails the next held upload and returns the rejection reason', async () => {
    const assetId = controller.enqueueDeferred({ mediaType: 'image/png' })
    controller.startUpload(assetId)
    const failed = controller.failNext(assetId, { status: 503, code: 'MEDIA_SERVICE_UNAVAILABLE' })
    await expect(failed).resolves.toEqual({
      status: 503,
      code: 'MEDIA_SERVICE_UNAVAILABLE',
    })
    expect(controller.heldCount()).toBe(0)
  })

  it('rejects advance() calls for unknown asset ids', () => {
    expect(() => controller.advance('not-enqueued', { progress: 50 })).toThrow(/not enqueued/)
  })

  it('records intermediate progress transitions via advance()', () => {
    const assetId = controller.enqueueDeferred({ mediaType: 'image/png' })
    controller.startUpload(assetId)
    controller.advance(assetId, { progress: 25 })
    controller.advance(assetId, { progress: 75, status: 'UPLOADING' })
    const transitions = state.deferredUploads.get(assetId)?.transitions ?? []
    expect(transitions.length).toBe(2)
    expect(transitions[0]?.progress).toBe(25)
    expect(transitions[1]?.progress).toBe(75)
  })

  it('clear() empties every held upload without resolving them', () => {
    controller.enqueueDeferred({ mediaType: 'image/png' })
    controller.enqueueDeferred({ mediaType: 'image/png' })
    expect(controller.heldCount()).toBe(2)
    controller.clear()
    expect(controller.heldCount()).toBe(0)
  })
})

describe('TransitionQueue (PR 1)', () => {
  it('returns queued responses FIFO keyed by assetId', () => {
    const queue = new TransitionQueue<{ progress: number }>()
    queue.enqueue('a', { progress: 10 })
    queue.enqueue('a', { progress: 30 })
    queue.enqueue('b', { progress: 5 })
    expect(queue.take('a')).toEqual({ progress: 10 })
    expect(queue.take('a')).toEqual({ progress: 30 })
    expect(queue.take('a')).toBeNull()
    expect(queue.take('b')).toEqual({ progress: 5 })
  })

  it('reports size per assetId', () => {
    const queue = new TransitionQueue<number>()
    queue.enqueue('a', 1)
    queue.enqueue('a', 2)
    queue.enqueue('b', 3)
    expect(queue.size('a')).toBe(2)
    expect(queue.size('b')).toBe(1)
    expect(queue.size('c')).toBe(0)
  })

  it('reset() clears every queued transition', () => {
    const queue = new TransitionQueue<number>()
    queue.enqueue('a', 1)
    queue.enqueue('b', 2)
    queue.reset()
    expect(queue.size('a')).toBe(0)
    expect(queue.size('b')).toBe(0)
  })
})

describe('MockChannelsProvider (PR 1)', () => {
  it('starts in disabled (null) state and resets to it', () => {
    const state = new MediaRouteState()
    const provider = new MockChannelsProvider(state)
    expect(provider.getMaxAttachments()).toBeNull()
    provider.setMaxAttachments(4)
    expect(provider.getMaxAttachments()).toBe(4)
    provider.reset()
    expect(provider.getMaxAttachments()).toBeNull()
  })

  it('setMaxAttachments persists into MediaRouteState for mock consumption', () => {
    const state = new MediaRouteState()
    const provider = new MockChannelsProvider(state)
    provider.setMaxAttachments(2)
    expect(state.channelsMaxAttachments).toBe(2)
  })
})

describe('MockProviderFlag (PR 1)', () => {
  it('starts disabled and resets to disabled', () => {
    const state = new MediaRouteState()
    const flag = new MockProviderFlag(state)
    expect(flag.isEnabled()).toBe(false)
    flag.setEnabled(true)
    expect(flag.isEnabled()).toBe(true)
    flag.reset()
    expect(flag.isEnabled()).toBe(false)
  })

  it('setEnabled persists into MediaRouteState for mock consumption', () => {
    const state = new MediaRouteState()
    const flag = new MockProviderFlag(state)
    flag.setEnabled(true)
    expect(state.unsplashProviderEnabled).toBe(true)
  })
})

describe('MediaRouteState.reset() (PR 1)', () => {
  it('clears deferred upload, channels, and provider flag state', () => {
    const state = new MediaRouteState()
    const controller = new DeferredUploadController(state)
    const provider = new MockChannelsProvider(state)
    const flag = new MockProviderFlag(state)
    controller.enqueueDeferred({ mediaType: 'image/png' })
    provider.setMaxAttachments(3)
    flag.setEnabled(true)
    expect(controller.heldCount()).toBe(1)
    expect(state.channelsMaxAttachments).toBe(3)
    expect(state.unsplashProviderEnabled).toBe(true)

    state.reset()

    expect(controller.heldCount()).toBe(0)
    expect(state.channelsMaxAttachments).toBeNull()
    expect(state.unsplashProviderEnabled).toBe(false)
  })
})
