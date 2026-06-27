import type { Page, Locator } from '@playwright/test'
import { expect } from '@playwright/test'

/**
 * Page Object Model for the Create Post modal (CreatePostModal.vue).
 */
export class ComposeModalPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  // ---- Locators ----

  get heading(): Locator {
    return this.page.getByRole('heading', {
      name: /create post|crear publicación|edit post|editar publicación/i,
    })
  }

  get textarea(): Locator {
    return this.page.getByPlaceholder(/start writing|comienza a escribir/i)
  }

  get firstCommentInput(): Locator {
    return this.page.getByPlaceholder(/your comment|tu comentario/i)
  }

  // Schedule mode tabs — labels wrap the visually-hidden radio inputs,
  // so we target the <label> for both clicking (toggles the radio) and
  // class checks (bg-text-display is on the label, not the input).
  get nowTab(): Locator {
    return this.page.getByRole('radio', { name: /^now$/i }).locator('xpath=..')
  }

  get nextScheduleTab(): Locator {
    return this.page.getByRole('radio', { name: 'Next Schedule' }).locator('xpath=..')
  }

  get pickDateTab(): Locator {
    return this.page
      .getByRole('radio', { name: /pick date|seleccionar fecha/i })
      .locator('xpath=..')
  }

  // Date/time pickers (visible when Pick Date is active)
  get datePickerButton(): Locator {
    // Use the Popover trigger button which contains the formatted date
    return this.page
      .locator('button')
      .filter({ hasText: /\w+ \d+, \d{4}/ })
      .first()
  }

  get timeInput(): Locator {
    return this.page.locator('input[type="time"]')
  }

  // Channel chips
  get channelChips(): Locator {
    return this.page.locator('button').filter({ hasText: /linkedin|twitter|instagram|facebook/i })
  }

  // Action buttons
  get scheduleNowButton(): Locator {
    // The submit button has `data-slot="button"` (from the shadcn Button component).
    return this.page.locator('button[data-slot="button"]').filter({ hasText: /schedule now/i })
  }

  get schedulePostButton(): Locator {
    return this.page
      .locator('button[data-slot="button"]')
      .filter({ hasText: /schedule post|programar publicación/i })
  }

  get nextScheduleSubmitButton(): Locator {
    // The submit button has `data-slot="button"` (from the shadcn Button component).
    return this.page.locator('button[data-slot="button"]').filter({ hasText: /next schedule/i })
  }

  get cancelButton(): Locator {
    return this.page.getByRole('button', { name: /cancel|cancelar/i })
  }

  // Checkboxes
  get priorityQueueCheckbox(): Locator {
    return this.page.getByRole('checkbox', { name: /priority queue/i })
  }

  get createAnotherCheckbox(): Locator {
    return this.page.getByRole('checkbox', { name: /create another|crear otra/i })
  }

  // Helper text — the schedule mode description inside the compose modal
  get helperText(): Locator {
    return this.page.getByText(/publishes|publica/)
  }

  // Media upload area
  get mediaSection(): Locator {
    return this.page.getByText(/media attachment|adjunto/i)
  }

  get selectFileLink(): Locator {
    return this.page.getByText(/select a file|selecciona un archivo/i)
  }

  // ---- Actions ----

  async expectVisible(): Promise<void> {
    await expect(this.heading).toBeVisible({ timeout: 5_000 })
  }

  async expectHidden(): Promise<void> {
    await expect(this.heading).toBeHidden({ timeout: 5_000 })
  }

  async fillText(text: string): Promise<void> {
    await this.textarea.fill(text)
  }

  async selectChannel(index: number = 0): Promise<void> {
    const chip = this.channelChips.nth(index)
    await chip.click()
  }

  async selectLinkedIn(): Promise<void> {
    // The channel is auto-selected by the store when channels are loaded.
    // This is intentionally a no-op — clickScheduleNow waits for channels.
  }

  async switchToNow(): Promise<void> {
    await this.nowTab.click()
  }

  async switchToNextSchedule(): Promise<void> {
    // nextScheduleTab resolves the Next Schedule radio (exact name match).
    await this.nextScheduleTab.click()
  }

  async switchToPickDate(): Promise<void> {
    await this.pickDateTab.click()
  }

  async openDatePicker(): Promise<void> {
    await this.datePickerButton.click()
  }

  async pickDate(day: number): Promise<void> {
    // Click on a day number in the calendar popover
    const dayButton = this.page
      .locator('[data-slot="calendar-cell-trigger"]')
      .getByText(String(day), { exact: true })
    await dayButton.click()
  }

  async clickScheduleNow(): Promise<void> {
    await this.scheduleNowButton.click()
  }

  async clickSchedulePost(): Promise<void> {
    await this.schedulePostButton.click()
  }

  async clickNextSchedule(): Promise<void> {
    await this.nextScheduleSubmitButton.click()
  }

  async clickCancel(): Promise<void> {
    // Use force click to avoid detachment issues from Vue animations
    await this.cancelButton.click({ force: true })
  }

  async togglePriority(): Promise<void> {
    await this.priorityQueueCheckbox.setChecked(true)
  }

  async toggleCreateAnother(): Promise<void> {
    await this.createAnotherCheckbox.setChecked(true)
  }

  // ---- Assertions ----

  async expectNowActive(): Promise<void> {
    await expect(this.nowTab).toHaveClass(/bg-text-display/)
  }

  async expectNextScheduleActive(): Promise<void> {
    // nextScheduleTab resolves a single radio. Only it has the bg-text-display class when active.
    await expect(this.nextScheduleTab).toHaveClass(/bg-text-display/)
  }

  async expectPickDateActive(): Promise<void> {
    await expect(this.pickDateTab).toHaveClass(/bg-text-display/)
  }

  async expectScheduleNowButton(): Promise<void> {
    await expect(this.scheduleNowButton).toBeVisible()
  }

  async expectSchedulePostButton(): Promise<void> {
    await expect(this.schedulePostButton).toBeVisible()
  }

  async expectNextScheduleButton(): Promise<void> {
    await expect(this.nextScheduleSubmitButton).toBeVisible()
  }

  async expectHelperText(text: string | RegExp): Promise<void> {
    await expect(this.helperText).toContainText(text)
  }

  async expectScheduleNowDisabled(): Promise<void> {
    await expect(this.scheduleNowButton).toBeDisabled()
  }

  async expectSchedulePostDisabled(): Promise<void> {
    await expect(this.schedulePostButton).toBeDisabled()
  }

  // ---- Composite actions ----

  /**
   * Full flow: create a post in NOW mode with text and LinkedIn channel.
   */
  async createPostNow(text: string): Promise<void> {
    await this.switchToNow()
    await this.fillText(text)
    await this.selectLinkedIn()
    await this.clickScheduleNow()
  }

  /**
   * Full flow: create a post in NEXT SCHEDULE mode.
   */
  async createPostNextSchedule(text: string): Promise<void> {
    await this.switchToNextSchedule()
    await this.fillText(text)
    await this.selectLinkedIn()
    await this.clickNextSchedule()
  }

  /**
   * Full flow: create a post in PICK DATE mode.
   */
  async createPostPickDate(text: string, day?: number): Promise<void> {
    await this.switchToPickDate()
    if (day) {
      await this.openDatePicker()
      await this.pickDate(day)
    }
    await this.fillText(text)
    await this.selectLinkedIn()
    await this.clickSchedulePost()
  }
}
