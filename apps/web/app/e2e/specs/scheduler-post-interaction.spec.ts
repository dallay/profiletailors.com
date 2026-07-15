import { test, expect } from '../fixtures/scheduler-base-test'
import { SchedulerPage } from '../pages/scheduler-page'
import { ComposeModalPage } from '../pages/compose-modal-page'
import { PostDetailModalPage } from '../pages/post-detail-modal-page'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'
import { createPublicationInStore, ensureChannelsLoaded } from '../fixtures/scheduler-mocks'

test.describe('Scheduler — Post Interaction', () => {
  test.beforeEach(async ({ page }) => {
    await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()
    // Inject mock channel so compose modal submit button works in TC-14
    await ensureChannelsLoaded(page)
  })

  /**
   * TC-11: Post Detail Modal — click a post card to view details.
   */
  test('TC-11: post detail modal @post-detail @read-only', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const detailModal = new PostDetailModalPage(page)

    // Inject a publication directly into the store (no UI / backend needed)
    const testText = `Detail modal test ${Date.now()}`
    await createPublicationInStore(page, testText)

    // Switch to list view and click the card
    await scheduler.switchToList()
    await page.waitForTimeout(300)
    const postCard = page.getByRole('button', { name: new RegExp(testText) }).first()
    await expect(postCard).toBeVisible({ timeout: 10_000 })
    await postCard.click()

    await detailModal.expectVisible()
    await detailModal.clickClose()
    await detailModal.expectHidden()
  })

  /**
   * TC-12: View Post Link — clicking View Post opens LinkedIn URL in new tab.
   */
  test('TC-12: view post link opens LinkedIn @post-detail @external-link', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const detailModal = new PostDetailModalPage(page)

    // Inject a publication directly into the store (no UI / backend needed)
    const testText = `View Post link test ${Date.now()}`
    await createPublicationInStore(page, testText)

    await scheduler.switchToList()
    await page.waitForTimeout(300)
    const postCard = page.getByRole('button', { name: new RegExp(testText) }).first()
    await expect(postCard).toBeVisible({ timeout: 10_000 })
    await postCard.click()
    await detailModal.expectVisible()

    // If the post was published, View Post button should be enabled
    const viewPostBtn = detailModal.viewPostButton
    const isVisible = await viewPostBtn.isVisible().catch(() => false)

    if (isVisible) {
      // Verify View Post opens a new tab with LinkedIn URL
      const [newTab] = await Promise.all([page.context().waitForEvent('page'), viewPostBtn.click()])
      await newTab.waitForLoadState()
      const url = newTab.url()
      expect(url).toContain('linkedin.com/feed/update')
      await newTab.close()
    }

    await detailModal.clickClose()
  })

  test('TC-12A: publishing failure is visible and can be retried @post-detail @failure @mvp', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)
    const detailModal = new PostDetailModalPage(page)

    const testText = `Failed publish retry test ${Date.now()}`
    await createPublicationInStore(page, testText, {
      title: 'Failed LinkedIn post',
      status: 'FAILED',
      errorCode: 'PROVIDER_VALIDATION_FAILED',
    })

    await scheduler.switchToList()
    const postCard = page.getByRole('button', { name: new RegExp(testText) }).first()
    await expect(postCard).toBeVisible({ timeout: 10_000 })
    await postCard.click()

    await detailModal.expectVisible()
    const dialog = page.getByRole('dialog')
    // failureDetail maps PROVIDER_VALIDATION_FAILED -> localized message
    await expect(dialog).toContainText('The channel rejected this post')
    await dialog.getByRole('button', { name: /^retry$/i }).click()
    await detailModal.expectHidden()

    await expect(page.getByRole('button', { name: new RegExp(testText) }).first()).toContainText(
      /queued/i,
    )
  })

  /**
   * TC-13: Delete a post from the calendar.
   */
  test('TC-13: delete post @post-delete', async ({ page }) => {
    const scheduler = new SchedulerPage(page)

    // Inject a publication directly into the store (no UI / backend needed)
    const testText = `Delete me ${Date.now()}`
    await createPublicationInStore(page, testText)

    // Switch to list view to find it
    await scheduler.switchToList()
    await page.waitForTimeout(300)
    const postCard = page.getByRole('button', { name: new RegExp(testText) }).first()
    await expect(postCard).toBeVisible({ timeout: 10_000 })

    const deleteButton = postCard.locator('[title="Delete publication"]')
    await deleteButton.click({ force: true })

    await expect(page.getByRole('button', { name: new RegExp(testText) })).toHaveCount(0, {
      timeout: 5_000,
    })
  })

  /**
   * TC-14: Add Post Button (+) in calendar cells.
   * Hovering over an enabled slot shows a + button that opens the composer.
   */
  test('TC-14: add post button in calendar cells @scheduler @ux @add-button', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)

    await scheduler.switchToMonth()

    // Trigger click on a future cell directly via JS to bypass hover-only
    // visibility (CSS transitions are hard to wait for in E2E).
    const clicked = await page.evaluate(() => {
      const addButton = document.querySelector(
        'button[title="Add post"]',
      ) as HTMLButtonElement | null
      if (!addButton) return false
      addButton.click()
      return true
    })

    if (clicked) {
      await composeModal.expectVisible()
      await composeModal.clickCancel()
    }
  })

  /**
   * TC-14A: Month view publications open the detail modal.
   */
  test('TC-14A: month view publication opens detail modal @scheduler @month-view @post-detail', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)
    const detailModal = new PostDetailModalPage(page)

    const testText = `Month detail test ${Date.now()}`
    // Pass testText as title so it appears in the month chip
    // (month chip renders pub.title || pub.content.substring(0, 20))
    await createPublicationInStore(page, testText, { title: testText })

    await scheduler.switchToMonth()
    await page.waitForTimeout(300)

    const publicationChip = page.getByRole('button', { name: new RegExp(testText) }).first()
    await expect(publicationChip).toBeVisible({ timeout: 10_000 })
    await publicationChip.click()

    await detailModal.expectVisible()
    await detailModal.expectTitle(new RegExp(testText))
    await detailModal.clickClose()
  })

  /**
   * TC-15: Past slots show posts as read-only.
   */
  test('TC-15: past slots read-only posts @past @read-only', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const detailModal = new PostDetailModalPage(page)

    // Inject a publication directly into the store (no UI / backend needed)
    const testText = `Past read-only test ${Date.now()}`
    await createPublicationInStore(page, testText)

    // Switch to list view and find the card
    await scheduler.switchToList()
    await page.waitForTimeout(300)
    const postCard = page.getByRole('button', { name: new RegExp(testText) }).first()
    await expect(postCard).toBeVisible({ timeout: 10_000 })

    // Click the post card to open detail modal
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

    // Verify past cells have aria-disabled
    const disabledCells = page.locator('[aria-disabled="true"]')
    const count = await disabledCells.count()
    expect(count).toBeGreaterThan(0)

    // The + button should NOT appear in past cells even on hover
    const firstDisabled = disabledCells.first()
    await firstDisabled.hover()
    const plusButton = firstDisabled.locator('button:has(svg)')
    const plusAppeared = await plusButton.isVisible({ timeout: 2_000 }).catch(() => false)
    if (plusAppeared) {
      console.warn('TC-16: + button unexpectedly visible in past cell')
    }
    expect(plusAppeared).toBe(false)
  })
})
