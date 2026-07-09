import { test as base, expect, type Page } from '@bgotink/playwright-coverage'
import { mockAuthenticatedSession } from './auth-helpers'
import { MediaLibraryPage } from '../pages/media-library-page'
import {
  type MockListResponse,
  type MockPutResponse,
  MediaRouteState,
  registerMediaMocks,
  resetMediaMocks,
} from './media-mocks'

interface MediaMockFixtures {
  mediaPage: MediaLibraryPage
  mockState: MediaRouteState
  mockListResponse: (response: MockListResponse) => void
  mockNextPut: (response: MockPutResponse) => void
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
    await mockAuthenticatedSession(page)
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
})

export { expect }
export type { Page }
