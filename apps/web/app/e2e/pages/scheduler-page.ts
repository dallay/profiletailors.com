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
    return this.page.getByRole('heading', { name: /all channels/i })
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
    return this.page.getByRole('button', { name: 'Day', exact: true })
  }

  get listViewButton(): Locator {
    return this.page.getByRole('button', { name: 'List', exact: true })
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
    return this.page
      .locator('select, [role="combobox"]')
      .filter({ hasText: /all posts|queued|published/i })
      .first()
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
    await this.page.waitForLoadState('networkidle')
    await this.heading.waitFor({ state: 'visible', timeout: 15_000 })
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
    await expect(this.page.getByText('12 AM', { exact: true })).toBeVisible()
    await expect(this.page.getByText('11 PM', { exact: true })).toBeVisible()
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
   * Uses the time-axis label text to locate the correct row, then indexes
   * into the day columns (0=Sun, 1=Mon, ... 6=Sat).
   */
  getSlotCell(dayOfWeek: number, hour: number): Locator {
    const ampm = hour >= 12 ? 'PM' : 'AM'
    const displayHour = hour % 12 === 0 ? 12 : hour % 12
    const _timeLabel = `${displayHour} ${ampm}`

    // Each slot row has a time-axis label followed by 7 day columns.
    // Find the row by its time label, then pick the day column by index.
    const timeAxisLabel = this.page
      .locator('span.font-mono')
      .filter({ hasText: new RegExp(`^${displayHour} ${ampm}$`) })
    // The day column is a sibling of the time-axis label within the same grid row
    return timeAxisLabel.locator('..').locator('div[aria-disabled]').nth(dayOfWeek)
  }

  /**
   * Click on an empty future slot to open the composer.
   * Uses the + button that appears on hover.
   */
  async clickAddPostInSlot(dayOfWeek: number, hour: number): Promise<void> {
    const cell = this.getSlotCell(dayOfWeek, hour)
    await cell.hover()
    await this.page.waitForTimeout(200)
    // Click the + button that appears on hover
    const addButton = cell.locator('button:has(svg)')
    await addButton.click()
  }
}
