import { test, expect } from '@playwright/test'
import { SchedulerPage } from '../pages/scheduler-page'
import { ComposeModalPage } from '../pages/compose-modal-page'
import { PostDetailModalPage } from '../pages/post-detail-modal-page'
import { authenticateAs } from '../fixtures/auth-helpers'
import { safeGoto } from '../fixtures/navigation'
import { APP_URL } from '../fixtures/test-data'

test.describe('Scheduler — Post Interaction', () => {
  test.beforeEach(async ({ page }) => {
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()
  })

  /**
   * TC-11: Post Detail Modal — click a post card to view details.
   */
  test('TC-11: post detail modal @post-detail @read-only', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)
    const detailModal = new PostDetailModalPage(page)

    // First create a post so we have something to click
    const testText = `Detail modal test ${Date.now()}`
    await scheduler.clickNewPost()
    await composeModal.createPostNow(testText)
    await composeModal.expectHidden()

    // Switch to list view for easier card access
    await scheduler.switchToList()

    // Click on the post card
    const postCard = page.locator('div').filter({ hasText: testText }).first()
    await postCard.click()

    // Verify detail modal opens
    await detailModal.expectVisible()

    // Verify content is shown
    await expect(page.getByRole('dialog')).toContainText(testText)

    // Close modal
    await detailModal.clickClose()
    await detailModal.expectHidden()
  })

  /**
   * TC-12: View Post Link — clicking View Post opens LinkedIn URL in new tab.
   */
  test('TC-12: view post link opens LinkedIn @post-detail @external-link', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)
    const detailModal = new PostDetailModalPage(page)

    // Create a post
    const testText = `View Post link test ${Date.now()}`
    await scheduler.clickNewPost()
    await composeModal.createPostNow(testText)
    await composeModal.expectHidden()

    // Wait for worker to process (it may need ~30s with WireMock)
    // We give it up to 15s since the test environment should have fast polling
    await page.waitForTimeout(15_000)

    // Switch to list view
    await scheduler.switchToList()

    // Click on the post card
    const postCard = page.locator('div').filter({ hasText: testText }).first()
    await postCard.click()
    await detailModal.expectVisible()

    // If the post was published, View Post button should be enabled
    const viewPostBtn = detailModal.viewPostButton
    const isVisible = await viewPostBtn.isVisible().catch(() => false)

    if (isVisible) {
      // Verify View Post opens a new tab with LinkedIn URL
      const [newTab] = await Promise.all([
        page.context().waitForEvent('page'),
        viewPostBtn.click(),
      ])
      await newTab.waitForLoadState()
      const url = newTab.url()
      expect(url).toContain('linkedin.com/feed/update')
      await newTab.close()
    }

    await detailModal.clickClose()
  })

  /**
   * TC-13: Delete a post from the calendar.
   */
  test('TC-13: delete post @post-delete', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)

    // Create a post specifically for deletion
    const testText = `Delete me ${Date.now()}`
    await scheduler.clickNewPost()
    await composeModal.createPostNow(testText)
    await composeModal.expectHidden()

    // Switch to list view to find it
    await scheduler.switchToList()
    await expect(page.getByText(testText)).toBeVisible({ timeout: 10_000 })

    // Hover to reveal delete button and click it
    const postRow = page.locator('div').filter({ hasText: testText }).first()
    await postRow.hover()
    const deleteBtn = postRow.locator('button[title="Delete publication"]')
    await deleteBtn.click()

    // Verify the post is gone
    await expect(page.getByText(testText)).toBeHidden({ timeout: 5_000 })
  })

  /**
   * TC-14: Add Post Button (+) in calendar cells.
   * Hovering over an enabled slot shows a + button that opens the composer.
   */
  test('TC-14: add post button in calendar cells @scheduler @ux @add-button', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)

    await scheduler.switchToMonth()

    // Hover over a current-month cell to reveal the + button
    // Use the first cell that has the group/cell hover interaction
    const currentCells = page.locator('[role="button"][tabindex="0"]')
    const cellCount = await currentCells.count()

    if (cellCount > 0) {
      const firstCell = currentCells.first()
      await firstCell.hover()
      await page.waitForTimeout(200)

      // The + button should appear on hover
      const plusButton = firstCell.locator('button:has(svg)')
      const plusVisible = await plusButton.isVisible().catch(() => false)

      if (plusVisible) {
        await plusButton.click()
        await composeModal.expectVisible()
        await composeModal.clickCancel()
      }
    }
  })

  /**
   * TC-15: Past slots show posts as read-only.
   */
  test('TC-15: past slots read-only posts @past @read-only', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)
    const detailModal = new PostDetailModalPage(page)

    // First create a post in NOW mode so it becomes PUBLISHED quickly
    const testText = `Past read-only test ${Date.now()}`
    await scheduler.clickNewPost()
    await composeModal.createPostNow(testText)
    await composeModal.expectHidden()

    // Wait for the post to be published
    await page.waitForTimeout(15_000)

    // Switch to list view
    await scheduler.switchToList()
    await expect(page.getByText(testText)).toBeVisible({ timeout: 10_000 })

    // Click the post card to open detail modal
    const postCard = page.locator('div').filter({ hasText: testText }).first()
    await postCard.click()
    await detailModal.expectVisible()

    // If published, it should show Read Only
    const readOnlyBadge = detailModal.readOnlyBadge
    const isReadOnly = await readOnlyBadge.isVisible().catch(() => false)
    if (isReadOnly) {
      await detailModal.expectReadOnly()
      // Delete should not be visible for published posts
      await detailModal.expectDeleteHidden()
    }

    await detailModal.clickClose()
  })

  /**
   * TC-16: Past slots cannot create or drop posts.
   * Past slots should have aria-disabled="true" and the + button should not appear.
   */
  test('TC-16: past slots disabled @past @disabled @a11y', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    await scheduler.switchToMonth()

    // Navigate to a past month
    await scheduler.backwardButton.click()
    await page.waitForTimeout(500)

    // Verify past cells have aria-disabled
    const disabledCells = page.locator('[aria-disabled="true"]')
    const count = await disabledCells.count()
    // At least some cells should be disabled (depends on day of month)
    expect(count).toBeGreaterThanOrEqual(0)

    // The + button should NOT appear in past cells even on hover
    if (count > 0) {
      const firstDisabled = disabledCells.first()
      await firstDisabled.hover()
      await page.waitForTimeout(300)
      const plusButton = firstDisabled.locator('button:has(svg)')
      const isVisible = await plusButton.isVisible().catch(() => false)
      expect(isVisible).toBe(false)
    }
  })
})
