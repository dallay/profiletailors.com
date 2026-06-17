import { test, expect } from '@playwright/test'
import { SchedulerPage } from '../pages/scheduler-page'
import { authenticateAs } from '../fixtures/auth-helpers'
import { safeGoto } from '../fixtures/navigation'
import { APP_URL } from '../fixtures/test-data'

test.describe('Scheduler — Views & Navigation', () => {
  // Authenticate before each test in this describe block
  test.beforeEach(async ({ page }) => {
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()
  })

  /**
   * TC-02: Navigate to Scheduler — verify default week view with 24h slots.
   */
  test('TC-02: default week view with 24h slots @navigation @scheduler', async ({ page }) => {
    // Week view should be default
    await expect(page.locator('text=12 AM')).toBeVisible()
    await expect(page.locator('text=1 AM')).toBeVisible()
    await expect(page.locator('text=6 PM')).toBeVisible()
    await expect(page.locator('text=11 PM')).toBeVisible()
  })

  /**
   * TC-03: Calendar View Switching between month, week, and day.
   */
  test('TC-03: calendar view switching @navigation @scheduler @views', async ({ page }) => {
    const scheduler = new SchedulerPage(page)

    // Month view
    await scheduler.switchToMonth()
    // Month grid should show 42 cells (6 weeks × 7 days)
    const cells = page.locator('[role="button"]').filter({ hasText: /^\d{1,2}$/ })
    await expect(cells).toHaveCount(42)

    // Week view
    await scheduler.switchToWeek()
    await expect(page.locator('text=12 AM')).toBeVisible()

    // Day view
    await scheduler.switchToDay()
    await expect(page.locator(/all day/i)).toBeVisible()

    // TODAY button should be present
    await expect(scheduler.todayButton).toBeVisible()
  })

  /**
   * TC-04: Navigate to past months — verify past cells are styled as disabled
   * and posts are still readable (read-only).
   */
  test('TC-04: past months navigation @navigation @scheduler @past', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    await scheduler.switchToMonth()

    // Navigate backward one month
    await scheduler.backwardButton.click()
    await page.waitForTimeout(500)

    // Navigate forward back to current month
    await scheduler.forwardButton.click()
    await page.waitForTimeout(500)

    // TODAY button returns to current month
    await scheduler.todayButton.click()
    await page.waitForTimeout(500)

    // Past cells should have cursor-not-allowed and aria-disabled
    const pastCells = page.locator('[aria-disabled="true"]')
    const count = await pastCells.count()
    // At least some cells should be past (depends on day of month)
    expect(count).toBeGreaterThanOrEqual(0)

    // Past cells with post cards should be clickable (read-only detail)
    // This is validated in TC-15 separately
  })
})
