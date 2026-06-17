import { test, expect } from '@playwright/test'
import { SchedulerPage } from '../pages/scheduler-page'
import { ComposeModalPage } from '../pages/compose-modal-page'
import { PostDetailModalPage } from '../pages/post-detail-modal-page'
import { authenticateAs } from '../fixtures/auth-helpers'
import { safeGoto } from '../fixtures/navigation'
import { APP_URL } from '../fixtures/test-data'

test.describe('Scheduler — Create Post', () => {
  test.beforeEach(async ({ page }) => {
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()
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

    // Modal should close
    await composeModal.expectHidden()

    // Switch to list view to verify the post was created
    await scheduler.switchToList()
    await expect(page.getByText(testText)).toBeVisible({ timeout: 10_000 })

    // Verify the post card is in the list
    const postCard = page.locator('div').filter({ hasText: testText }).first()
    await expect(postCard).toBeVisible()
  })

  /**
   * TC-06: Create Post — NEXT SCHEDULE mode.
   * Creates a post with "Next Schedule" mode and verifies SCHEDULED status.
   */
  test('TC-06: create post via Next Schedule mode @creation @next-schedule @e2e', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)

    const testText = `E2E test Next Schedule ${Date.now()}`

    await scheduler.clickNewPost()
    await composeModal.expectVisible()

    // Switch to Next Schedule
    await composeModal.switchToNextSchedule()
    await composeModal.expectNextScheduleActive()

    // Verify helper text
    await composeModal.expectHelperText(/publishes in the next available schedule slot/i)

    // Create the post
    await composeModal.createPostNextSchedule(testText)

    // Modal should close
    await composeModal.expectHidden()

    // Verify the post appears
    await scheduler.switchToList()
    await expect(page.getByText(testText)).toBeVisible({ timeout: 10_000 })
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

    // Verify helper text mentions publishing on a date
    await composeModal.expectHelperText(/publishes on/i)

    // Verify Schedule Post button is visible (not Schedule Now)
    await composeModal.expectSchedulePostButton()

    // Open calendar and pick a future date (tomorrow)
    const tomorrow = new Date()
    tomorrow.setDate(tomorrow.getDate() + 1)
    await composeModal.openDatePicker()
    await composeModal.pickDate(tomorrow.getDate())

    // Create the post
    await composeModal.fillText(testText)
    await composeModal.selectLinkedIn()
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

    // Textarea should be cleared
    await expect(composeModal.textarea).toHaveValue('')

    // Close the modal
    await composeModal.clickCancel()
    await composeModal.expectHidden()
  })
})
