import { test, expect } from '../fixtures/media-real-test'
import { ComposeModalPage } from '../pages/compose-modal-page'
import { SchedulerPage } from '../pages/scheduler-page'

interface UnsplashSearchResponse {
  photos: Array<{ externalId: string }>
}

interface UnsplashImportResponse {
  assetId: string
}

interface PublicationResponse {
  publicationId: string
}

const workspaceHeaders = { 'X-Workspace-Id': 'dev-workspace-001' }

test.describe('Unsplash real provider smoke @real-unsplash @media @composer', () => {
  test('ML-SMOKE-UNSPLASH-001 loads examples, searches, imports, and creates a post', async ({
    page,
  }) => {
    const runId = `e2e-unsplash-${Date.now()}`
    const scheduler = new SchedulerPage(page)
    const composePage = new ComposeModalPage(page)
    let importedAssetId: string | undefined
    let publicationId: string | undefined
    const unsplashRequests: string[] = []
    const unsplashResponses: string[] = []
    page.on('request', (request) => {
      if (request.url().includes('/api/media/providers/unsplash/')) {
        unsplashRequests.push(`${request.method()} ${request.url()}`)
      }
    })
    page.on('response', (response) => {
      if (response.url().includes('/api/media/providers/unsplash/')) {
        unsplashResponses.push(`${response.status()} ${response.url()}`)
      }
    })

    try {
      await test.step('Open composer and media picker', async () => {
        await scheduler.goto()
        await scheduler.clickNewPost()
        await composePage.expectVisible()
        await composePage.fillText(runId)
        await composePage.openMediaPicker()
      })

      let selectedPhotoId: string | undefined
      await test.step('Browse editorial examples', async () => {
        const examplesResponsePromise = page.waitForResponse(
          (response) =>
            response.request().method() === 'GET' &&
            new URL(response.url()).pathname === '/api/media/providers/unsplash/photos' &&
            !new URL(response.url()).searchParams.has('query'),
          { timeout: 20_000 },
        )
        await composePage.unsplashTab.click()
        const examplesResponse = await examplesResponsePromise.catch((error: unknown) => {
          throw new Error(
            `Initial Unsplash response was not observed. Requests: ${unsplashRequests.join(', ')}. Responses: ${unsplashResponses.join(', ')}. Cause: ${String(error)}`,
          )
        })
        expect(examplesResponse.ok()).toBe(true)
        const examples = (await examplesResponse.json()) as UnsplashSearchResponse
        expect(examples.photos.length).toBeGreaterThan(0)
        await expect(composePage.unsplashResults).toBeVisible()
      })

      await test.step('Search for "remote work"', async () => {
        const searchResponsePromise = page.waitForResponse((response) => {
          const url = new URL(response.url())
          return (
            response.request().method() === 'GET' &&
            url.pathname === '/api/media/providers/unsplash/photos' &&
            url.searchParams.get('query') === 'remote work'
          )
        })
        await composePage.searchUnsplash('remote work')
        const searchResponse = await searchResponsePromise
        expect(searchResponse.ok()).toBe(true)
        const searchResult = (await searchResponse.json()) as UnsplashSearchResponse
        expect(searchResult.photos.length).toBeGreaterThan(0)
        selectedPhotoId = searchResult.photos[0]?.externalId
        expect(selectedPhotoId).toBeTruthy()
        if (!selectedPhotoId) throw new Error('Unsplash returned no selectable photo')
      })

      await test.step('Import selected Unsplash photo', async () => {
        const photoId = selectedPhotoId
        if (!photoId) throw new Error('No photo selected for import')
        const importResponsePromise = page.waitForResponse(
          (response) =>
            response.request().method() === 'POST' &&
            new URL(response.url()).pathname ===
              `/api/media/providers/unsplash/photos/${photoId}/import`,
        )
        await composePage.unsplashImportButton(photoId).click()
        const importResponse = await importResponsePromise
        if (!importResponse.ok()) {
          throw new Error(
            `Unsplash import failed with ${importResponse.status()}: ${await importResponse.text()}`,
          )
        }
        importedAssetId = ((await importResponse.json()) as UnsplashImportResponse).assetId
        expect(importedAssetId).toBeTruthy()
      })

      await test.step('Apply selection and attach to post', async () => {
        await composePage.libraryTab.click()
        await expect(composePage.libraryAssetCard(importedAssetId)).toBeVisible()
        await composePage.pickerApply.click()
        await expect(composePage.attachmentPreview).toBeVisible()
      })

      await test.step('Schedule post with attached Unsplash asset', async () => {
        await composePage.switchToPickDate()
        const tomorrow = new Date()
        tomorrow.setDate(tomorrow.getDate() + 1)
        await composePage.openDatePicker()
        await composePage.pickDate(tomorrow)

        const publicationResponsePromise = page.waitForResponse(
          (response) =>
            response.request().method() === 'POST' &&
            new URL(response.url()).pathname === '/api/publishing/publications',
        )
        await composePage.clickSchedulePost()
        const publicationResponse = await publicationResponsePromise
        expect(publicationResponse.ok()).toBe(true)
        expect(publicationResponse.request().postDataJSON()).toMatchObject({
          bodyText: runId,
          assetIds: [importedAssetId],
        })
        publicationId = ((await publicationResponse.json()) as PublicationResponse).publicationId
        expect(publicationId).toBeTruthy()
      })
    } finally {
      if (publicationId) {
        await page.request.delete(`/api/publishing/publications/${publicationId}`, {
          headers: workspaceHeaders,
        })
      }
      if (importedAssetId) {
        await page.request.delete(`/api/media/assets/${importedAssetId}`, {
          headers: workspaceHeaders,
        })
      }
    }
  })
})
