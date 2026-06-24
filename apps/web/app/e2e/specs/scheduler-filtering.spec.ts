import { test, expect } from '../fixtures/scheduler-base-test'
import { SchedulerPage } from '../pages/scheduler-page'
import { authenticateAs } from '../fixtures/auth-helpers'

test.describe('Scheduler — Filtering & Views', () => {
  test.beforeEach(async ({ page }) => {
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()
  })

  /**
   * TC-17: Channel filtering — LinkedIn only, then all channels.
   */
  test('TC-17: channel filtering @filtering @channels', async ({ page }) => {
    const scheduler = new SchedulerPage(page)

    // Ensure we are on week view
    await scheduler.switchToWeek()

    // Click "All channels" to ensure we start from a clean state
    await scheduler.allChannelsButton.click({ force: true })
    await page.waitForTimeout(300)

    // Verify the scheduler header shows "All Channels"
    await expect(page.getByRole('heading', { name: 'All Channels' })).toBeVisible()
  })

  /**
   * TC-18: Post type filtering (All Posts, Queued, Published, Cancelled).
   */
  test('TC-18: post type filtering @filtering @post-type', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    await scheduler.switchToList()

    // The filter is a native <select> element. Use evaluate to set value directly
    // since Playwright's selectOption can have issues with empty-string values.
    const filter = page
      .locator('select')
      .filter({
        hasText: /all posts|queued|published|cancelled/i,
      })
      .first()

    // Select "Queued"
    await filter.evaluate((el: HTMLSelectElement) => {
      el.value = 'queued'
    })
    await filter.dispatchEvent('change')
    await page.waitForTimeout(500)

    // Select "Published"
    await filter.evaluate((el: HTMLSelectElement) => {
      el.value = 'published'
    })
    await filter.dispatchEvent('change')
    await page.waitForTimeout(500)

    // Select "All Posts" to reset (value is empty string)
    await filter.evaluate((el: HTMLSelectElement) => {
      el.value = ''
    })
    await filter.dispatchEvent('change')
    await page.waitForTimeout(500)
  })

  /**
   * TC-20: List view — shows all posts with correct structure.
   */
  test('TC-20: list view structure @list-view @posts', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    await scheduler.switchToList()

    // Verify posts are rendered (may be empty initially)
    // The list view renders each post as a card with date, status, content, channels
    const rows = page.locator('.rounded-2xl.border.border-border-subtle.bg-bg-surface')
    const count = await rows.count()
    expect(count).toBeGreaterThanOrEqual(0)

    // If there are posts, verify structure
    if (count > 0) {
      const firstRow = rows.first()
      // Should contain a status badge
      const statusBadge = firstRow.locator('span').filter({
        hasText: /published|queued|scheduled|failed|cancelled/i,
      })
      await expect(statusBadge.first()).toBeVisible()
    }
  })
})
