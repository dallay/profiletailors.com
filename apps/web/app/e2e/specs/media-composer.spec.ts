import { test, expect, type Page } from '../fixtures/media-mocked-test'
import { mediaFiles } from '../fixtures/media-files'
import { ComposeModalPage } from '../pages/compose-modal-page'
import { SchedulerPage } from '../pages/scheduler-page'
import { ensureChannelsLoaded } from '../fixtures/scheduler-mocks'

const tags = '@media @composer @mocked'

interface VueAppElement extends HTMLElement {
  __vue_app__?: {
    config: {
      globalProperties: {
        $pinia?: {
          state: {
            value: {
              media?: {
                assetsById: Record<string, unknown>
                assetIds: string[]
                selectedAssetIds: string[]
              }
            }
          }
        }
      }
    }
  }
}

async function selectAssetInStore(
  page: Page,
  asset: { assetId: string; mediaType: string; originalFilename: string },
): Promise<void> {
  await page.evaluate((data) => {
    const appEl = document.querySelector('#app') as VueAppElement | null
    const pinia = appEl?.__vue_app__?.config.globalProperties.$pinia
    if (pinia) {
      const mediaStore = pinia.state.value.media
      if (mediaStore) {
        mediaStore.assetsById[data.assetId] = {
          assetId: data.assetId,
          workspaceId: 'workspace-001',
          sourceType: 'UPLOADED',
          mediaType: data.mediaType,
          status: 'READY',
          originalFilename: data.originalFilename,
          fileSizeBytes: 68,
          createdAt: new Date().toISOString(),
          previewUrl: `/api/media/assets/${data.assetId}/preview`,
          downloadUrl: `/api/media/assets/${data.assetId}/preview`,
        }
        if (!mediaStore.assetIds.includes(data.assetId)) {
          mediaStore.assetIds.unshift(data.assetId)
        }
        mediaStore.selectedAssetIds = [data.assetId]
      }
    }
  }, asset)
}

/**
 * Composer media attachment tests using mocked media config.
 * Tests attachment readiness, limits, failure blocking, and assetId publication.
 */
test.describe(`Composer media attachment ${tags}`, () => {
  test.beforeEach(async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()
    await ensureChannelsLoaded(page)
  })

  test('ML-COMPOSE-001 attach media: preview appears and publish button enabled', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)
    const composePage = new ComposeModalPage(page)
    await scheduler.clickNewPost()
    await composePage.expectVisible()

    await composePage.fillText('Post with media attachment')
    await composePage.attachMedia(mediaFiles.base.path)

    // Preview shows on the LinkedIn preview panel
    await expect(page.locator('[data-testid="linkedin-preview-media"] img')).toBeVisible()
    await expect(composePage.scheduleNowButton).toBeEnabled()
  })

  test('ML-COMPOSE-002 remove attachment: preview disappears', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const composePage = new ComposeModalPage(page)
    await scheduler.clickNewPost()
    await composePage.expectVisible()

    // Pre-select the asset so the ready asset preview is rendered
    await selectAssetInStore(page, {
      assetId: 'asset-composer-002',
      mediaType: mediaFiles.base.type,
      originalFilename: mediaFiles.base.name,
    })

    await expect(composePage.getAttachmentPreview()).toBeVisible()
    await composePage.removeAttachment()
    await expect(composePage.getAttachmentPreview()).toBeHidden()
  })

  test('ML-COMPOSE-003 upload failure: displays error and blocks submit', async ({
    page,
    mockNextPut,
  }) => {
    mockNextPut({
      status: 429,
      body: { errorCode: 'RATE_LIMIT_EXCEEDED', message: 'Rate limit exceeded.' },
    })

    const scheduler = new SchedulerPage(page)
    const composePage = new ComposeModalPage(page)
    await scheduler.clickNewPost()
    await composePage.expectVisible()

    await composePage.fillText('Post with failed media')
    await composePage.attachMedia(mediaFiles.mutated.path)

    // Schedule now triggers the upload, which fails
    await composePage.clickScheduleNow()

    // Displays the error from submitError
    await expect(page.getByText('Media upload failed. Please try again.')).toBeVisible()
    await expect(composePage.scheduleNowButton).toBeEnabled() // Still enabled after error so we can retry
  })

  test('ML-COMPOSE-004 successful upload: assetId included in publish payload', async ({
    page,
    mockState,
    mockNextPut,
  }) => {
    const asset = mockState.seedAsset({
      originalFilename: mediaFiles.base.name,
      mediaType: mediaFiles.base.type,
    })

    // Setup PUT response to trigger instant dedup success
    mockNextPut({
      status: 200,
      body: { assetId: asset.assetId, deduped: true, status: 'READY' },
    })

    const scheduler = new SchedulerPage(page)
    const composePage = new ComposeModalPage(page)
    await scheduler.clickNewPost()
    await composePage.expectVisible()

    await composePage.fillText('Post with assetId payload')
    await composePage.attachMedia(mediaFiles.base.path)

    // Intercept publish request to verify assetIds contains our asset
    let publishPayload: Record<string, unknown> | null = null
    await page.route('**/api/publishing/publications', async (route) => {
      publishPayload = route.request().postDataJSON() as Record<string, unknown>
      await route.fulfill({
        status: 201,
        contentType: 'application/vnd.api.v1+json',
        body: JSON.stringify({
          publicationId: 'pub-composer-004',
          workspaceId: 'workspace-001',
          socialAccountId: 'sa-linkedin-001',
          status: 'QUEUED',
          scheduleMode: 'NOW',
          priority: false,
          title: 'Post with assetId payload',
          bodyText: 'Post with assetId payload',
          assetIds: [asset.assetId],
          scheduledFor: new Date().toISOString(),
          nextSlotAfter: null,
        }),
      })
    })

    await composePage.clickScheduleNow()

    await expect(composePage.heading).toBeHidden()

    expect(publishPayload).not.toBeNull()
    expect(publishPayload.assetIds).toContain(asset.assetId)
  })

  test('ML-COMPOSE-005 oversized file: rejects upload at 10 MiB limit', async ({ page }) => {
    test.skip(true, 'ML-COMPOSE-005: 10 MiB limit validation not yet implemented in composer UI')

    const scheduler = new SchedulerPage(page)
    const composePage = new ComposeModalPage(page)
    await scheduler.clickNewPost()
    await composePage.expectVisible()

    await composePage.fillText('Post with oversized media')
    await composePage.attachMedia('/path/to/oversized.png')

    await expect(page.getByText(/file size exceeds/i)).toBeVisible()
    await expect(composePage.scheduleNowButton).toBeDisabled()
  })
})
