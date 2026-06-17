import type { Page, Locator } from '@playwright/test'
import { expect } from '@playwright/test'

/**
 * Page Object Model for the Post Detail modal (PostDetailModal.vue).
 */
export class PostDetailModalPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  // ---- Locators ----

  get heading(): Locator {
    return this.page.getByRole('dialog').getByRole('heading', { name: /post detail/i })
  }

  get titleLabel(): Locator {
    return this.page.getByText(/title/i).first()
  }

  get bodyLabel(): Locator {
    return this.page.getByText(/content/i).first()
  }

  get scheduledForLabel(): Locator {
    return this.page.getByText(/scheduled for/i)
  }

  get publishedAtLabel(): Locator {
    return this.page.getByText(/published at/i)
  }

  get readOnlyBadge(): Locator {
    return this.page.getByText(/read only|solo lectura/i)
  }

  get viewPostButton(): Locator {
    return this.page.getByRole('link', { name: /view post|ver publicación/i })
  }

  get deleteButton(): Locator {
    return this.page.getByRole('button', { name: /delete|eliminar/i })
  }

  get closeButton(): Locator {
    return this.page.getByRole('button', { name: /close|cerrar/i })
  }

  // ---- Actions ----

  async expectVisible(): Promise<void> {
    await expect(this.heading).toBeVisible({ timeout: 5_000 })
  }

  async expectHidden(): Promise<void> {
    await expect(this.heading).toBeHidden({ timeout: 5_000 })
  }

  async clickClose(): Promise<void> {
    await this.closeButton.click()
    await this.expectHidden()
  }

  async clickDelete(): Promise<void> {
    await this.deleteButton.click()
  }

  async clickViewPost(): Promise<void> {
    // Click and wait for new tab to open
    const [newPage] = await Promise.all([
      this.page.context().waitForEvent('page'),
      this.viewPostButton.click(),
    ])
    await newPage.waitForLoadState()
    return // caller can use newPage
  }

  // ---- Assertions ----

  async expectTitle(text: string | RegExp): Promise<void> {
    const dialog = this.page.getByRole('dialog')
    await expect(dialog).toContainText(text)
  }

  async expectReadOnly(): Promise<void> {
    await expect(this.readOnlyBadge).toBeVisible()
  }

  async expectNotReadOnly(): Promise<void> {
    await expect(this.readOnlyBadge).toBeHidden()
  }

  async expectViewPostEnabled(): Promise<void> {
    await expect(this.viewPostButton).toBeEnabled()
  }

  async expectViewPostDisabled(): Promise<void> {
    await expect(this.viewPostButton).toBeDisabled()
  }

  async expectDeleteVisible(): Promise<void> {
    await expect(this.deleteButton).toBeVisible()
  }

  async expectDeleteHidden(): Promise<void> {
    await expect(this.deleteButton).toBeHidden()
  }
}
