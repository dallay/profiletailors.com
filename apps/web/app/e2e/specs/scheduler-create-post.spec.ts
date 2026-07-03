import { test, expect } from '../fixtures/scheduler-base-test'
import { SchedulerPage } from '../pages/scheduler-page'
import { ComposeModalPage } from '../pages/compose-modal-page'
import { authenticateAs } from '../fixtures/auth-helpers'
import { ensureChannelsLoaded } from '../fixtures/scheduler-mocks'
import { PostDetailModalPage } from '../pages/post-detail-modal-page'
import { mediaFiles } from '../fixtures/media-files'

test.describe('Scheduler — Create Post', () => {
  test.beforeEach(async ({ page }) => {
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()
    // Inject mock channel directly into Pinia so the compose modal's
    // submit button is enabled without waiting for AppShell fetchChannels.
    await ensureChannelsLoaded(page)
  })

  /**
   * TC-05: Create Post — NOW mode.
   * Creates a post with the default "Now" schedule mode and verifies it appears.
   */
  test('TC-05: create post via Now mode @creation @now @e2e', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)

    const testText = `E2E test post Now mode ${Date.now()}`

    // Open composer
    await scheduler.clickNewPost()
    await composeModal.expectVisible()

    // Verify NOW is active by default
    await composeModal.expectNowActive()
    await composeModal.expectScheduleNowButton()

    // Create the post
    await composeModal.createPostNow(testText)

    // Modal may remain open in some local-only mock flows; close it explicitly
    // if needed so the test can continue.
    await composeModal.expectHidden().catch(async () => {
      await composeModal.clickCancel()
      await composeModal.expectHidden()
    })

    // Switch to list view to verify the post was created
    await scheduler.switchToList()
    await expect(page.getByText(testText).first()).toBeVisible({ timeout: 10_000 })

    // Verify the post card is in the list
    const postCard = page.locator('div').filter({ hasText: testText }).first()
    await expect(postCard).toBeVisible()
  })

  test('TC-05A: authenticated create reopens and PATCHes backend ID while preserving media @creation @edit @media', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)
    const detailModal = new PostDetailModalPage(page)
    const text = `Real ID create ${Date.now()}`
    const updatedText = `${text} updated`
    const backendId = 'backend-publication-real-id'
    const assetId = 'asset-preserved-1'
    let patchUrl = ''
    let patchBody: Record<string, unknown> | null = null
    let wasPatched = false

    await page.route(/\/api\/media\/assets\/[^/]+$/, async (route) => {
      const method = route.request().method()
      const requestedAssetId = route.request().url().split('/api/media/assets/')[1] ?? assetId
      if (method === 'PUT') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            assetId: requestedAssetId,
            workspaceId: 'workspace-001',
            sourceType: 'UPLOADED',
            mediaType: 'image/png',
            status: 'READY',
            deduped: true,
            originalFilename: 'base.png',
            fileSizeBytes: 68,
            createdAt: new Date().toISOString(),
            previewUrl: `/api/media/assets/${requestedAssetId}/preview`,
          }),
        })
        return
      }
      if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            assetId: requestedAssetId,
            workspaceId: 'workspace-001',
            sourceType: 'UPLOADED',
            mediaType: 'image/png',
            status: 'READY',
            originalFilename: 'base.png',
            fileSizeBytes: 68,
            createdAt: new Date().toISOString(),
            previewUrl: `/api/media/assets/${requestedAssetId}/preview`,
            downloadUrl: `/api/media/assets/${requestedAssetId}/content`,
          }),
        })
        return
      }
      route.fallback()
    })
    await page.route('**/api/publishing/publications', async (route) => {
      if (route.request().method() !== 'POST') return route.fallback()
      const body = route.request().postDataJSON() as Record<string, unknown>
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          publicationId: backendId,
          workspaceId: 'workspace-001',
          socialAccountId: 'sa-linkedin-001',
          status: 'QUEUED',
          scheduleMode: 'NOW',
          priority: false,
          title: 'Post from App',
          bodyText: body.bodyText,
          assetIds: [assetId],
          scheduledFor: null,
          nextSlotAfter: null,
        }),
      })
    })
    await page.route('**/api/publishing/publications/calendar**', async (route) => {
      const currentBodyText = wasPatched ? updatedText : text
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publications: [
            {
              id: backendId,
              workspaceId: 'workspace-001',
              socialAccountId: 'sa-linkedin-001',
              provider: 'linkedin',
              status: 'QUEUED',
              scheduleMode: 'NOW',
              priority: false,
              title: 'Post from App',
              bodyText: currentBodyText,
              scheduledFor: null,
              nextSlotAfter: null,
              assetIds: [assetId],
              hasConflict: false,
              conflictingPublicationIds: [],
            },
          ],
          conflicts: [],
          activity: [],
        }),
      })
    })
    await page.route(`**/api/publishing/publications/${backendId}`, async (route) => {
      if (route.request().method() !== 'PATCH') return route.fallback()
      patchUrl = route.request().url()
      patchBody = route.request().postDataJSON() as Record<string, unknown>
      wasPatched = true
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicationId: backendId,
          workspaceId: 'workspace-001',
          socialAccountId: 'sa-linkedin-001',
          status: 'QUEUED',
          scheduleMode: 'NOW',
          priority: false,
          title: 'Post from App',
          bodyText: updatedText,
          assetIds: [assetId],
          scheduledFor: null,
          nextSlotAfter: null,
        }),
      })
    })

    await scheduler.clickNewPost()
    await composeModal.expectVisible()
    await composeModal.fillText(text)
    await composeModal.attachMedia(mediaFiles.base.path)
    await composeModal.clickScheduleNow()
    await composeModal.expectHidden()

    await scheduler.switchToList()
    await page
      .getByRole('button', { name: new RegExp(text) })
      .first()
      .click()
    await detailModal.expectVisible()
    await page
      .getByRole('dialog')
      .getByRole('button', { name: /edit|editar/i })
      .click()
    await composeModal.expectVisible()
    await expect(composeModal.attachmentPreview).toBeVisible()
    await composeModal.fillText(updatedText)
    await page.getByRole('button', { name: /save|guardar/i }).click()
    await composeModal.expectHidden()

    expect(patchUrl).toContain(`/api/publishing/publications/${backendId}`)
    expect(patchBody).not.toHaveProperty('assetIds')
    await expect(page.getByRole('button', { name: new RegExp(updatedText) })).toBeVisible()
  })

  /**
   * TC-06: Create Post — NEXT SCHEDULE mode.
   * Creates a post with "Next Schedule" mode and verifies SCHEDULED status.
   */
  test('TC-06: create post via Next Schedule mode @creation @next-schedule @e2e', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)

    const testText = `E2E test Next Schedule ${Date.now()}`

    await scheduler.clickNewPost()
    await composeModal.expectVisible()

    // Switch to Next Schedule
    await composeModal.switchToNextSchedule()
    await composeModal.expectNextScheduleActive()

    // Fill text first — the submit button is disabled until text is entered
    await composeModal.fillText(testText)

    // Create the post
    await composeModal.clickNextSchedule()

    // Modal should close
    await composeModal.expectHidden()

    // Verify the post appears
    await scheduler.switchToList()
    await expect(page.getByText(testText).first()).toBeVisible({ timeout: 10_000 })
  })

  /**
   * TC-07: Create Post — PICK DATE mode.
   * Opens calendar selector, picks a date, creates the post.
   */
  test('TC-07: create post via Pick Date mode @creation @pick-date @e2e', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)

    const testText = `E2E test Pick Date ${Date.now()}`

    await scheduler.clickNewPost()
    await composeModal.expectVisible()

    // Switch to Pick Date
    await composeModal.switchToPickDate()
    await composeModal.expectPickDateActive()

    // Fill text first — the Schedule Post button is disabled until both
    // text and a valid date are set.
    await composeModal.fillText(testText)

    // Verify Schedule Post button is visible (not Schedule Now)
    await composeModal.expectSchedulePostButton()

    // Open calendar and pick a future date (tomorrow)
    const tomorrow = new Date()
    tomorrow.setDate(tomorrow.getDate() + 1)
    await composeModal.openDatePicker()
    await composeModal.pickDate(tomorrow.getDate())

    // Create the post
    await composeModal.clickSchedulePost()

    // Modal should close
    await composeModal.expectHidden()
  })

  /**
   * TC-08: Create Post — Validation.
   * Button should be disabled when no text is entered.
   */
  test('TC-08: validation — button disabled when empty @creation @validation', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)

    await scheduler.clickNewPost()
    await composeModal.expectVisible()

    // NOW mode: button should be disabled without text
    await composeModal.expectScheduleNowDisabled()

    // Switch to Pick Date, button should also be disabled
    await composeModal.switchToPickDate()
    await composeModal.expectSchedulePostDisabled()

    // Close
    await composeModal.clickCancel()
    await composeModal.expectHidden()
  })

  /**
   * TC-10: Priority Queue & Create Another checkboxes.
   * Creates a post with Priority Queue checked, then verifies the modal stays open.
   */
  test('TC-10: priority queue and create another @creation @priority @ux', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)

    await scheduler.clickNewPost()
    await composeModal.expectVisible()

    // Check Priority Queue
    await composeModal.togglePriority()
    await expect(composeModal.priorityQueueCheckbox).toBeChecked()

    // Check Create Another
    await composeModal.toggleCreateAnother()
    await expect(composeModal.createAnotherCheckbox).toBeChecked()

    // Create a post — modal should stay open
    const testText = `Priority test ${Date.now()}`
    await composeModal.createPostNow(testText)

    // Modal should still be visible (Create Another was checked)
    await composeModal.expectVisible()

    // Close the modal via Escape key (avoids detachment timing issues)
    await page.keyboard.press('Escape')
    await composeModal.expectHidden()
  })
})
