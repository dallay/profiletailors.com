import type { Locator, Page } from '@playwright/test'

export class WaitlistPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  get heading(): Locator {
    return this.page.getByRole('heading', { name: /waitlist/i })
  }

  get searchInput(): Locator {
    return this.page.getByLabel(/search by email/i)
  }

  get statusFilter(): Locator {
    return this.page.getByLabel(/filter by status/i)
  }

  get selectAllCheckbox(): Locator {
    return this.page.getByTestId('bulk-select-all')
  }

  get bulkInviteButton(): Locator {
    return this.page.getByTestId('bulk-invite')
  }

  get resultsPanel(): Locator {
    return this.page.getByTestId('bulk-results')
  }

  get alert(): Locator {
    return this.page.getByRole('alert')
  }

  rowByEmail(email: string): Locator {
    return this.page.locator('tbody tr', { hasText: email })
  }

  async goto(heading: RegExp = /waitlist/i): Promise<void> {
    await this.page.goto('/waitlist')
    await this.page.getByRole('heading', { name: heading }).waitFor()
  }

  async search(email: string): Promise<void> {
    await this.searchInput.fill(email)
  }

  async filterStatus(status: string): Promise<void> {
    await this.statusFilter.selectOption(status)
  }

  async selectRow(email: string): Promise<void> {
    await this.page.locator(`tr:has-text("${email}") input[type="checkbox"]`).check()
  }

  async selectAll(): Promise<void> {
    await this.selectAllCheckbox.check()
  }

  async submitBulkInvite(): Promise<void> {
    await this.bulkInviteButton.click()
  }

  async mainText(): Promise<string> {
    return this.page.locator('main').innerText()
  }
}
