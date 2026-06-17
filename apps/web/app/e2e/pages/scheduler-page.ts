import type { Page, Locator } from '@playwright/test'
import { expect } from '@playwright/test'

/**
 * Page Object Model for the Scheduler view (SchedulerView.vue).
 */
export class SchedulerPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  // ---- Locators ----

  get heading(): Locator {
    return this.page.getByRole('heading', { level: 1, name: /scheduler/i })
  }

  get newPostButton(): Locator {
    return this.page.getByRole('button', { name: /new post/i }).first()
  }

  // View toggles
  get monthViewButton(): Locator {
    return this.page.getByRole('button', { name: /^calendar$/i }).first()
  }

  get weekViewButton(): Locator {
    return this.page.getByRole('button', { name: /week/i })
  }

  get dayViewButton(): Locator {
    return this.page.getByRole('button', { name: /day$/i })
  }

  get listViewButton(): Locator {
    return this.page.getByRole('button', { name: /^list$/i })
  }

  // Navigation
  get todayButton(): Locator {
    return this.page.getByRole('button', { name: /today/i })
  }

  get forwardButton(): Locator {
    // The forward arrow button is the second navigation button
    return this.page.locator('button').filter({ hasText: /^\s*$/ }).nth(1)
  }

  get backwardButton(): Locator {
    return this.page.locator('button').filter({ hasText: /^\s*$/ }).nth(0)
  }

  // Sidebar channel filters
  get allChannelsButton(): Locator {
    return this.page.getByRole('button', { name: /all channels/i })
  }

  get linkedInFilterButton(): Locator {
    return this.page.getByRole('button', { name: /linkedin/i }).first()
  }

  // Post type filter dropdown
  get postTypeFilter(): Locator {
    return this.page.locator('select, [role="combobox"]').filter({ hasText: /all posts|queued|published/i }).first()
  }

  // Post cards in the scheduler
  get postCards(): Locator {
    return this.page.locator('.group\\/card')
  }

  // Delete buttons on post cards
  get deleteButtons(): Locator {
    return this.page.locator('button[title="Delete publication"]')
  }

  // ---- Actions ----

  async goto(): Promise<void> {
    await this.page.goto('/scheduler')
    await this.heading.waitFor({ state: 'visible', timeout: 10_000 })
  }

  async switchToMonth(): Promise<void> {
    await this.monthViewButton.click()
    await this.page.waitForTimeout(300)
  }

  async switchToWeek(): Promise<void> {
    await this.weekViewButton.click()
    await this.page.waitForTimeout(300)
  }

  async switchToDay(): Promise<void> {
    await this.dayViewButton.click()
    await this.page.waitForTimeout(300)
  }

  async switchToList(): Promise<void> {
    await this.listViewButton.click()
    await this.page.waitForTimeout(300)
  }

  async clickNewPost(): Promise<void> {
    await this.newPostButton.click()
  }

  async hoverPostCard(index: number): Promise<void> {
    const card = this.postCards.nth(index)
    await card.hover()
  }

  async clickPostCard(index: number): Promise<void> {
    const card = this.postCards.nth(index)
    await card.click()
  }

  async clickDeleteButton(index: number): Promise<void> {
    const btn = this.deleteButtons.nth(index)
    await btn.click()
  }

  // ---- Assertions ----

  async expectVisible(): Promise<void> {
    await expect(this.heading).toBeVisible()
  }

  async expectWeekView(): Promise<void> {
    // Verify 24 hour slots are present (12 AM through 11 PM)
    await expect(this.page.locator('text=12 AM')).toBeVisible()
    await expect(this.page.locator('text=11 PM')).toBeVisible()
  }

  async expectMonthView(): Promise<void> {
    // Verify month grid is rendered (day numbers visible)
    await expect(this.page.locator('.group\\/cell')).toHaveCount(42) // 6 weeks * 7 days
  }

  async expectPostCount(count: number): Promise<void> {
    await expect(this.postCards).toHaveCount(count, { timeout: 10_000 })
  }

  async expectPostStatus(index: number, status: string): Promise<void> {
    const card = this.postCards.nth(index)
    await expect(card.getByText(new RegExp(status, 'i'))).toBeVisible()
  }

  // ---- Helpers ----

  /**
   * Find the slot grid cell for a specific day-of-week column and hour.
   * weekDays array is 0=Sun, 1=Mon, ... 6=Sat
   */
  getSlotCell(dayOfWeek: number, hour: number): Locator {
    // The week grid has 24 rows, each with 7 day columns
    const row = this.page.locator(`[data-slot-hour="${hour}"]`).or(
      // Fallback: nth slot row, then nth day column
      this.page.locator('.grid.grid-cols-\\[48px_repeat\\(7\\,1fr\\)\\]').nth(hour)
    )
    // If we can't target by data attribute, use a broader approach
    // Each slot row has time-axis label + 7 day columns
    return this.page.locator('div[class*="grid-cols"]').nth(24 + hour) // +1 for header, +1 for time-axis
  }

  /**
   * Click on an empty future slot to open the composer.
   * Uses the + button that appears on hover.
   */
  async clickAddPostInSlot(dayOfWeek: number, hour: number): Promise<void> {
    // Hover over the slot area, then click the + button
    const slotArea = this.page.locator('[role="button"][aria-disabled="false"]').nth(dayOfWeek)
    await slotArea.hover()
    await this.page.waitForTimeout(200)
    // Click the + button that appears on hover
    const addButton = slotArea.locator('button:has(svg)')
    await addButton.click()
  }
}
