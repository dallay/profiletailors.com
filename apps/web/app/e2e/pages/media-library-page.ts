import type { Locator, Page } from '@playwright/test'
import { expect } from '@playwright/test'

export type MediaCounterKind = 'READY' | 'PROCESSING' | 'FAILED'

/**
 * Page object for `MediaLibraryView.vue`.
 *
 * Known defect: card action icon buttons currently lack accessible names. The
 * dedicated single-card action helpers use position-based locators inside a
 * filename-scoped card until ML-A11Y-004 is fixed in product code.
 */
export class MediaLibraryPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  get heading(): Locator {
    return this.page.getByRole('heading', { name: /media library/i, level: 2 })
  }

  get refreshButton(): Locator {
    return this.page.getByRole('button', { name: /refresh|actualizar/i })
  }

  get uploadButton(): Locator {
    return this.page.getByRole('button', { name: /upload files|subir archivos/i })
  }

  get fileInput(): Locator {
    return this.page.locator('#media-library-file-input')
  }

  get deleteSelectedButton(): Locator {
    return this.page.getByRole('button', { name: /delete selected|eliminar seleccionados/i })
  }

  async navigateTo(): Promise<void> {
    await this.page.goto('/media')
    await this.page.waitForLoadState('domcontentloaded')
    await this.heading.waitFor({ state: 'visible', timeout: 15_000 })
  }

  async refresh(): Promise<void> {
    await this.refreshButton.click()
  }

  async uploadFile(absolutePath: string): Promise<void> {
    await this.fileInput.setInputFiles(absolutePath)
  }

  async uploadFiles(paths: string[]): Promise<void> {
    await this.fileInput.setInputFiles(paths)
  }

  async waitForUploadComplete(name: string): Promise<void> {
    await expect.poll(async () => this.getCardByName(name).count()).toBeGreaterThan(0)
    await expect(this.getCardStatus(name)).toHaveText('READY')
  }

  cards(): Locator {
    return this.page.locator('article')
  }

  getCardByName(name: string): Locator {
    return this.cards().filter({ hasText: name })
  }

  getCardStatus(name: string): Locator {
    return this.getCardByName(name)
      .locator('span')
      .filter({ hasText: /READY|PROCESSING|FAILED/ })
      .first()
  }

  async selectCard(name: string): Promise<void> {
    await this.getCardByName(name).locator('input[type="checkbox"]').check({ force: true })
  }

  async deleteCard(name: string): Promise<void> {
    const buttons = this.getCardByName(name).locator('button')
    await buttons.nth(1).click({ force: true })
    await this.page.getByRole('button', { name: /delete asset|eliminar asset/i }).click()
  }

  bulkDeleteDialog(): Locator {
    return this.page.getByRole('alertdialog').filter({ hasText: 'Delete selected media assets?' })
  }

  async confirmBulkDelete(): Promise<void> {
    await expect(this.bulkDeleteDialog()).toBeVisible()
    await this.bulkDeleteDialog()
      .getByRole('button', { name: /delete selected/i })
      .dispatchEvent('click')
  }

  async cancelBulkDelete(): Promise<void> {
    await expect(this.bulkDeleteDialog()).toBeVisible()
    await this.page.keyboard.press('Escape')
    await expect(this.bulkDeleteDialog()).toBeHidden()
  }

  async search(text: string): Promise<void> {
    await this.page.getByLabel(/search|buscar/i).fill(text)
  }

  async setStatusFilter(value: string): Promise<void> {
    await this.page.getByTestId('filter-status').selectOption(value)
  }

  async setTypeFilter(value: string): Promise<void> {
    await this.page.getByTestId('filter-type').selectOption(value)
  }

  async setSort(value: string): Promise<void> {
    const optionValue =
      value === 'Filename A–Z' || value === 'Filename A-Z' || value === 'filename-asc'
        ? 'filename-asc'
        : value
    await this.page.getByTestId('filter-sort').selectOption(optionValue)
  }

  getCounter(kind: MediaCounterKind): Locator {
    const label =
      kind === 'READY' ? /ready assets/i : kind === 'PROCESSING' ? /processing/i : /failed/i
    return this.page
      .locator('div.inline-flex')
      .filter({ has: this.page.getByText(label, { exact: true }) })
      .locator('span')
      .last()
  }

  getCounterBadge(kind: MediaCounterKind): Locator {
    const label =
      kind === 'READY' ? /ready assets/i : kind === 'PROCESSING' ? /processing/i : /failed/i
    return this.page
      .locator('div.inline-flex')
      .filter({ has: this.page.getByText(label, { exact: true }) })
  }

  getEmptyState(): Locator {
    return this.page.getByText(/no media assets yet/i)
  }

  getLoadError(): Locator {
    return this.page.getByText(/failed to load/i, { exact: true })
  }

  getUploadFailure(): Locator {
    return this.getCounterBadge('FAILED')
  }

  async getVisibleCount(): Promise<number> {
    return this.cards().count()
  }

  visibleCountText(): Locator {
    return this.page
      .locator('.text-xs.text-text-secondary')
      .filter({ hasText: /\d+ \/ \d+/ })
      .first()
  }
}
