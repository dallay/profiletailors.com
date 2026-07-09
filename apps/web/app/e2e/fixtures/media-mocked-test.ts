import { test as base, expect, type Page } from '@bgotink/playwright-coverage'
import { mockAuthenticatedSession } from './auth-helpers'
import { MediaLibraryPage } from '../pages/media-library-page'
import {
  type MockListResponse,
  type MockPutResponse,
  MediaRouteState,
  registerComposerControls,
  registerMediaMocks,
  resetMediaMocks,
  DeferredUploadController,
  MockChannelsProvider,
  MockProviderFlag,
  type TransitionQueue,
} from './media-mocks'
import {
  createComposerControls,
  type ComposerControls,
  resetComposerControls,
} from './media-mocked-test-fixtures'

interface MediaMockFixtures {
  mediaPage: MediaLibraryPage
  mockState: MediaRouteState
  mockListResponse: (response: MockListResponse) => void
  mockNextPut: (response: MockPutResponse) => void
  // PR 1 — composer-scoped controllers
  deferredUpload: DeferredUploadController
  channelsProvider: MockChannelsProvider
  providerFlag: MockProviderFlag
  transitionQueue: TransitionQueue<{ progress: number }>
}

export const test = base.extend<MediaMockFixtures>({
  mockState: [
    // biome-ignore lint/correctness/noEmptyPattern: Playwright fixtures require object destructuring.
    async ({}, use) => {
      const state = new MediaRouteState()
      resetMediaMocks()
      await use(state)
      state.reset()
    },
    { scope: 'test' },
  ],

  deferredUpload: async ({ mockState }, use) => {
    const controller = new DeferredUploadController(mockState)
    await use(controller)
    controller.clear()
  },

  channelsProvider: async ({ mockState }, use) => {
    const provider = new MockChannelsProvider(mockState)
    await use(provider)
    provider.reset()
  },

  providerFlag: async ({ mockState }, use) => {
    const flag = new MockProviderFlag(mockState)
    await use(flag)
    flag.reset()
  },

  transitionQueue: async (
    // biome-ignore lint/correctness/noEmptyPattern: Playwright fixtures require object destructuring.
    {},
    use,
  ) => {
    const queue = new TransitionQueue<{ progress: number }>()
    await use(queue)
    queue.reset()
  },

  page: async ({ page, context, mockState }, use) => {
    await mockAuthenticatedSession(page)
    await registerMediaMocks(context, mockState)
    // PR 1 — register the composer controls only when the test asks for them.
    // The fixture's per-context teardown still unroutes everything on `use` exit.
    const controls: ComposerControls = createComposerControls(mockState)
    const registration = await registerComposerControls(context, mockState)
    // Bind the same controls into the page-level closures so fixture teardown
    // can reach them. The page fixture itself does not need to call them.
    void controls
    await use(page)
    await registration.unregister()
    await context.unrouteAll({ behavior: 'wait' })
  },

  mediaPage: async ({ page }, use) => {
    await use(new MediaLibraryPage(page))
  },

  mockListResponse: async ({ mockState }, use) => {
    await use((response: MockListResponse) => {
      mockState.setListResponse(response)
    })
  },

  mockNextPut: async ({ mockState }, use) => {
    await use((response: MockPutResponse) => {
      mockState.enqueuePut(response)
    })
  },
})

export { expect, createComposerControls, resetComposerControls }
export type { Page }
