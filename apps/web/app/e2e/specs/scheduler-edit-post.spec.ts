import { test, expect } from '../fixtures/scheduler-base-test'
import { SchedulerPage } from '../pages/scheduler-page'
import { ComposeModalPage } from '../pages/compose-modal-page'
import { PostDetailModalPage } from '../pages/post-detail-modal-page'
import { authenticateAs } from '../fixtures/auth-helpers'
import { createPublicationInStore, ensureChannelsLoaded } from '../fixtures/scheduler-mocks'

test.describe('Scheduler — Edit Post', () => {
  test.beforeEach(async ({ page }) => {
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()
    await ensureChannelsLoaded(page)
  })

  /**
   * TC-17: Edit a scheduled publication from the scheduler.
   * Verifies: detail modal → Edit → composer opens in edit mode,
   * content is pre-filled, channel is locked, edits are saved.
   */
  test('TC-17: edit scheduled post — opens composer, pre-fills, saves @edit @e2e', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)
    const detailModal = new PostDetailModalPage(page)
    const composeModal = new ComposeModalPage(page)

    // Inject an unpublished scheduled publication into the store
    const originalText = `Edit test original ${Date.now()}`
    const updatedText = `Edit test updated ${Date.now()}`
    await createPublicationInStore(page, originalText, {
      title: `Original scheduled post`,
    })

    // Open the publication detail
    await scheduler.switchToList()
    const postCard = page.getByRole('button', { name: new RegExp(originalText) }).first()
    await expect(postCard).toBeVisible({ timeout: 10_000 })
    await postCard.click()
    await detailModal.expectVisible()

    // Click Edit — this opens the composer in edit mode
    const editButton = page
      .getByRole('dialog')
      .getByRole('button', { name: /edit|editar/i })
      .first()
    await expect(editButton).toBeVisible()
    await editButton.click()

    // Composer should be in edit mode
    await composeModal.expectVisible()
    // Verify edit mode label on submit button (saveChanges, not Schedule Now)
    const saveButton = composeModal.page.getByRole('button', { name: /save|guardar/i })
    await expect(saveButton).toBeVisible()

    // Content should be pre-filled
    await expect(composeModal.textarea).toHaveValue(originalText)

    // Channel should be locked (disabled)
    const channelChips = composeModal.page.locator('button[data-edit-disabled="true"]')
    await expect(channelChips.first()).toBeVisible()

    // "Create Another" should NOT appear in edit mode
    await expect(composeModal.page.getByText(/create another|crear otra/i)).toHaveCount(0)

    // Edit the content and save
    await composeModal.fillText('')
    await composeModal.fillText(updatedText)
    await saveButton.click()

    // Modal should close
    await composeModal.expectHidden()

    // Verify updated text appears in scheduler
    await scheduler.switchToList()
    await expect(page.getByRole('button', { name: new RegExp(updatedText) })).toBeVisible({ timeout: 10_000 })
  })

  /**
   * TC-17A: Priority pre-fill and toggling in edit mode.
   * Verifies the priority toggle is on when the publication has priority.
   */
  test('TC-17A: priority pre-filled in edit mode @edit @priority @e2e', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const detailModal = new PostDetailModalPage(page)
    const composeModal = new ComposeModalPage(page)

    // Inject a priority publication
    await createPublicationInStore(page, `Priority edit test ${Date.now()}`, {
      title: `Priority scheduled post`,
      priority: true,
    })

    await scheduler.switchToList()
    const postCard = page.getByRole('button', { name: /Priority edit test/i }).first()
    await expect(postCard).toBeVisible({ timeout: 10_000 })
    await postCard.click()
    await detailModal.expectVisible()

    const editButton = page
      .getByRole('dialog')
      .getByRole('button', { name: /edit|editar/i })
      .first()
    await editButton.click()
    await composeModal.expectVisible()

    // Priority checkbox should be checked
    await expect(composeModal.priorityQueueCheckbox).toBeChecked()
  })

  /**
   * TC-17B: Channel lock — existing channel is selected and disabled in edit mode.
   */
  test('TC-17B: channel is selected and disabled in edit mode @edit @channels @e2e', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)
    const detailModal = new PostDetailModalPage(page)
    const composeModal = new ComposeModalPage(page)

    await createPublicationInStore(page, `Channel lock test ${Date.now()}`, {
      title: `Channel lock post`,
    })

    await scheduler.switchToList()
    const postCard = page.getByRole('button', { name: /Channel lock test/i }).first()
    await expect(postCard).toBeVisible({ timeout: 10_000 })
    await postCard.click()
    await detailModal.expectVisible()

    const editButton = page
      .getByRole('dialog')
      .getByRole('button', { name: /edit|editar/i })
      .first()
    await editButton.click()
    await composeModal.expectVisible()

    // At least one channel chip must be selected (has check mark) and disabled
    const selectedDisabledChannel = composeModal.page.locator(
      'button[data-edit-disabled="true"][class*="border-text-display"]',
    )
    await expect(selectedDisabledChannel).toBeVisible()
  })
})
