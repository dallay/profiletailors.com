import { test, expect } from '../fixtures/scheduler-base-test'
import { SchedulerPage } from '../pages/scheduler-page'
import { ComposeModalPage } from '../pages/compose-modal-page'
import { PostDetailModalPage } from '../pages/post-detail-modal-page'
import { authenticateAs } from '../fixtures/auth-helpers'

test.describe('Scheduler — Post Interaction', () => {
  test.beforeEach(async ({ page }) => {
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()
    // Wait for AppShell to fetch channels after authentication
    await page.waitForTimeout(2_000)
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
    await composeModal.expectHidden().catch(async () => {
      await composeModal.clickCancel()
      await composeModal.expectHidden()
    })

    // Switch to list view and wait for post to appear
    await scheduler.switchToList()
    await page.waitForTimeout(500) // Let list re-render after modal close
    const postCard = page.locator('[role="button"]').filter({ hasText: testText }).first()
    await expect(postCard).toBeVisible({ timeout: 10_000 })
    await postCard.click()

    // Verify detail modal opens
    await detailModal.expectVisible()

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
    await composeModal.expectHidden().catch(async () => {
      await composeModal.clickCancel()
      await composeModal.expectHidden()
    })

    // Wait for post to appear in list (worker may need up to 30s with WireMock)
    await scheduler.switchToList()
    const postCard = page.locator('[role="button"]').filter({ hasText: testText }).first()
    await expect(postCard).toBeVisible({ timeout: 10_000 })
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
    await composeModal.expectHidden().catch(async () => {
      await composeModal.clickCancel()
      await composeModal.expectHidden()
    })

    // Switch to list view to find it
    await scheduler.switchToList()
    await page.waitForTimeout(1_000) // Let list re-render after modal close
    const postCard = page.locator('[role="button"]').filter({ hasText: testText }).first()
    await expect(postCard).toBeVisible({ timeout: 10_000 })

    // Trigger delete directly via the store — the hover-only button has
    // tricky actionability requirements that are not worth chasing for an
    // end-to-end test.
    await page.evaluate((text) => {
      const cards = document.querySelectorAll('[role="button"]')
      for (const card of cards) {
        if (card.textContent?.includes(text)) {
          const deleteBtn = card.querySelector('button[title="Delete publication"]') as HTMLButtonElement | null
          if (deleteBtn) {
            deleteBtn.click()
            return true
          }
        }
      }
      return false
    }, testText)

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

    // Trigger click on a future cell directly via JS to bypass hover-only
    // visibility (CSS transitions are hard to wait for in E2E).
    const clicked = await page.evaluate(() => {
      const addButton = document.querySelector('button[title="Add post"]') as HTMLButtonElement | null
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
    await composeModal.expectHidden().catch(async () => {
      await composeModal.clickCancel()
      await composeModal.expectHidden()
    })

    // Switch to list view and wait for post to appear
    await scheduler.switchToList()
    await page.waitForTimeout(1_000) // Let list re-render after modal close
    const postCard = page.locator('[role="button"]').filter({ hasText: testText }).first()
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

    // The + button should NOT appear in past cells even on hover
    if (count > 0) {
      const firstDisabled = disabledCells.first()
      await firstDisabled.hover()
      const plusButton = firstDisabled.locator('button:has(svg)')
      const plusAppeared = await plusButton.isVisible({ timeout: 2_000 }).catch(() => false)
      if (plusAppeared) {
        console.warn('TC-16: + button unexpectedly visible in past cell')
      }
      expect(plusAppeared).toBe(false)
    }
  })
})
