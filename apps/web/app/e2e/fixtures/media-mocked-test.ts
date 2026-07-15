import { test as base, expect, type Page } from '@bgotink/playwright-coverage'
import { mockAuthenticatedSession } from './auth-helpers'
import { MediaLibraryPage } from '../pages/media-library-page'
import {
  type MockListResponse,
  type MockPutResponse,
  MediaRouteState,
  registerMediaMocks,
  resetMediaMocks,
  DeferredUploadController,
  MockChannelsProvider,
  MockPublicationPostProvider,
  TransitionQueue,
} from './media-mocks'

interface MediaMockFixtures {
  mediaPage: MediaLibraryPage
  mockState: MediaRouteState
  mockListResponse: (response: MockListResponse) => void
  mockNextPut: (response: MockPutResponse) => void
  // PR 1 — composer-scoped controllers
  deferredUpload: DeferredUploadController
  channelsProvider: MockChannelsProvider
  transitionQueue: TransitionQueue<{ progress: number }>
  // PR 2 — composer publication POST controller.
  // Default behavior echoes the request body as 201; tests override via
  // `setFailure(status, body?)` for failure-path coverage.
  publicationPostProvider: MockPublicationPostProvider
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

  page: async ({ page, context, mockState }, use) => {
    // Media Library gates uploads behind email verification; the default
    // mockAuthenticatedSession emailStatus is PENDING, which disables the
    // "Upload files" button and blocks every upload-flow test in this spec.
    await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })
    await registerMediaMocks(context, mockState)
    await use(page)
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

  // biome-ignore lint/correctness/noEmptyPattern: Playwright fixtures require object destructuring.
  transitionQueue: async ({}, use) => {
    const queue = new TransitionQueue<{ progress: number }>()
    await use(queue)
    queue.reset()
  },

  publicationPostProvider: async ({ mockState }, use) => {
    const provider = new MockPublicationPostProvider(mockState)
    await use(provider)
    provider.reset()
  },
})

export { expect }
export type { Page }
