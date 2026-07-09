import {
  DeferredUploadController,
  MockChannelsProvider,
  MockProviderFlag,
  type MediaRouteState,
  TransitionQueue,
} from './media-mocks'

/**
 * PR 1 — composer test fixture factories. Owns the wiring between
 * MediaRouteState and the new composer-scoped controllers so the
 * Playwright fixture in `media-mocked-test.ts` can expose them as
 * scoped, isolated fixtures.
 */
export interface ComposerControls {
  deferredUpload: DeferredUploadController
  channelsProvider: MockChannelsProvider
  providerFlag: MockProviderFlag
  transitionQueue: TransitionQueue<{ progress: number }>
}

export function createComposerControls(state: MediaRouteState): ComposerControls {
  return {
    deferredUpload: new DeferredUploadController(state),
    channelsProvider: new MockChannelsProvider(state),
    providerFlag: new MockProviderFlag(state),
    transitionQueue: new TransitionQueue<{ progress: number }>(),
  }
}

/**
 * Resets all composer-scoped controllers. Used by the per-test fixture
 * teardown to keep tests isolated even when the route state survives
 * across `beforeEach` boundaries.
 */
export function resetComposerControls(controls: ComposerControls): void {
  controls.deferredUpload.clear()
  controls.channelsProvider.reset()
  controls.providerFlag.reset()
  controls.transitionQueue.reset()
}
