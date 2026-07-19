import { expect, type Page, type Locator } from '@playwright/test'

/**
 * Page Object Model for the Privacy section of the Settings page.
 *
 * Handles DSAR request forms (ACCESS, EXPORT, CORRECTION, DELETION),
 * the request history list, status badges, and error/success states.
 *
 * All components carry stable `data-testid` attributes.
 *
 * @see PrivacySection.vue — parent component
 * @see DsarRequestForm.vue — form with type select, notes, correction fields, deletion dialog
 * @see DsarRequestList.vue — request history table
 * @see DsarStatusBadge.vue — status indicator badges
 */
export class PrivacySettingsPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  // ---------------------------------------------------------------------------
  // Locators — Settings shell
  // ---------------------------------------------------------------------------

  get settingsShell(): Locator {
    return this.page.getByTestId('settings-shell')
  }

  // ---------------------------------------------------------------------------
  // Locators — Privacy panel
  // ---------------------------------------------------------------------------

  get privacyPanel(): Locator {
    return this.page.getByTestId('settings-privacy-panel')
  }

  get successMessage(): Locator {
    return this.page.getByTestId('dsar-submit-success')
  }

  get errorMessage(): Locator {
    return this.page.getByTestId('dsar-error')
  }

  // ---------------------------------------------------------------------------
  // Locators — Request form
  // ---------------------------------------------------------------------------

  /**
   * The shadcn-vue SelectTrigger for the request type dropdown.
   * Click to open the select portal.
   */
  get typeSelectTrigger(): Locator {
    return this.page.getByTestId('dsar-type-select')
  }

  /** Returns a locator for a specific type option in the select portal. */
  typeOption(type: string): Locator {
    return this.page.getByTestId(`dsar-type-${type}`)
  }

  get notesInput(): Locator {
    return this.page.getByTestId('dsar-notes')
  }

  get correctionEmailInput(): Locator {
    return this.page.getByTestId('dsar-correction-email')
  }

  get correctionUsernameInput(): Locator {
    return this.page.getByTestId('dsar-correction-username')
  }

  /** Submit button for non-deletion types. */
  get submitButton(): Locator {
    return this.page.getByTestId('dsar-submit')
  }

  /** Submit button when DELETION type is selected. */
  get deletionTriggerButton(): Locator {
    return this.page.getByTestId('dsar-submit-deletion')
  }

  // ---------------------------------------------------------------------------
  // Locators — Deletion confirmation dialog
  // ---------------------------------------------------------------------------

  get deletionConfirmDialog(): Locator {
    return this.page.getByTestId('dsar-deletion-confirm-dialog')
  }

  get deletionConfirmButton(): Locator {
    return this.page.getByTestId('dsar-deletion-confirm')
  }

  get deletionCancelButton(): Locator {
    return this.page.getByTestId('dsar-deletion-cancel')
  }

  // ---------------------------------------------------------------------------
  // Locators — Request history list
  // ---------------------------------------------------------------------------

  get requestRows(): Locator {
    return this.page.getByTestId('dsar-request-row')
  }

  requestRow(index: number): Locator {
    return this.requestRows.nth(index)
  }

  statusBadge(row: Locator): Locator {
    return row.getByTestId('dsar-status-badge')
  }

  /**
   * Download button — rendered as an <a> tag via shadcn-vue's `as-child` prop.
   * The data-testid lands directly on the <a> element.
   */
  get downloadLink(): Locator {
    return this.page.getByTestId('dsar-download-btn')
  }

  // ---------------------------------------------------------------------------
  // Actions — Navigation
  // ---------------------------------------------------------------------------

  async goto(): Promise<void> {
    await this.page.goto('/settings', { waitUntil: 'domcontentloaded' })
    // The settings shell is rendered after auth hydration and data fetches
    await expect(this.settingsShell).toBeVisible({ timeout: 15_000 })
    await expect(this.privacyPanel).toBeVisible({ timeout: 10_000 })
  }

  // ---------------------------------------------------------------------------
  // Actions — Form interaction
  // ---------------------------------------------------------------------------

  /**
   * Select a DSAR type from the dropdown.
   * Clicks the trigger to open the portal, then clicks the matching option.
   */
  async selectType(type: string): Promise<void> {
    await this.typeSelectTrigger.click()
    const option = this.typeOption(type)
    await expect(option).toBeVisible({ timeout: 5_000 })
    await option.click()
    // Wait for the select portal to finish closing before continuing
    await expect(option).not.toBeVisible({ timeout: 3_000 })
  }

  async fillNotes(notes: string): Promise<void> {
    await this.notesInput.fill(notes)
  }

  async fillCorrectionEmail(email: string): Promise<void> {
    await this.correctionEmailInput.fill(email)
  }

  async fillCorrectionUsername(username: string): Promise<void> {
    await this.correctionUsernameInput.fill(username)
  }

  async clickSubmit(): Promise<void> {
    await this.submitButton.click()
  }

  async clickDeletionTrigger(): Promise<void> {
    await this.deletionTriggerButton.click()
  }

  async confirmDeletion(): Promise<void> {
    await this.deletionConfirmButton.click()
  }

  async cancelDeletion(): Promise<void> {
    await this.deletionCancelButton.click()
  }

  // ---------------------------------------------------------------------------
  // Actions — Compound flows
  // ---------------------------------------------------------------------------

  /** Full flow: select ACCESS, fill notes, submit. */
  async submitAccessRequest(notes?: string): Promise<void> {
    await this.selectType('ACCESS')
    if (notes) await this.fillNotes(notes)
    await this.clickSubmit()
  }

  /** Full flow: select EXPORT, fill notes, submit. */
  async submitExportRequest(notes?: string): Promise<void> {
    await this.selectType('EXPORT')
    if (notes) await this.fillNotes(notes)
    await this.clickSubmit()
  }

  /** Full flow: select CORRECTION, fill email/username, submit. */
  async submitCorrectionRequest(email?: string, username?: string): Promise<void> {
    await this.selectType('CORRECTION')
    if (email) await this.fillCorrectionEmail(email)
    if (username) await this.fillCorrectionUsername(username)
    await this.clickSubmit()
  }

  /** Full flow: select DELETION, trigger confirmation dialog, confirm. */
  async submitDeletionRequest(): Promise<void> {
    await this.selectType('DELETION')
    await this.clickDeletionTrigger()
    await expect(this.deletionConfirmDialog).toBeVisible({ timeout: 5_000 })
    await this.confirmDeletion()
  }

  /** Full flow: select DELETION, trigger confirmation dialog, cancel. */
  async cancelDeletionRequest(): Promise<void> {
    await this.selectType('DELETION')
    await this.clickDeletionTrigger()
    await expect(this.deletionConfirmDialog).toBeVisible({ timeout: 5_000 })
    await this.cancelDeletion()
  }

  // ---------------------------------------------------------------------------
  // Assertions
  // ---------------------------------------------------------------------------

  async expectSuccessVisible(): Promise<void> {
    await expect(this.successMessage).toBeVisible({ timeout: 5_000 })
  }

  async expectErrorVisible(expectedText?: string | RegExp): Promise<void> {
    await expect(this.errorMessage).toBeVisible({ timeout: 5_000 })
    if (expectedText) {
      await expect(this.errorMessage).toContainText(expectedText)
    }
  }

  async expectRequestCount(count: number): Promise<void> {
    await expect(this.requestRows).toHaveCount(count)
  }

  async expectRequestStatus(rowIndex: number, expectedStatus: string): Promise<void> {
    const row = this.requestRow(rowIndex)
    const badge = this.statusBadge(row)
    await expect(badge).toContainText(new RegExp(expectedStatus, 'i'))
  }

  async expectDownloadLinkVisible(): Promise<void> {
    await expect(this.downloadLink).toBeVisible({ timeout: 5_000 })
  }

  async expectDownloadLinkHasHref(pattern: RegExp): Promise<void> {
    await expect(this.downloadLink).toHaveAttribute('href', pattern)
  }
}
