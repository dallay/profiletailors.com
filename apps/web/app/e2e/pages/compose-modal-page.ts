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

  get saveButton(): Locator {
    return this.page.getByRole('dialog').getByRole('button', { name: /save|guardar/i })
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

  get mediaFileInput(): Locator {
    return this.page.locator('[data-testid="picker-upload-input"]')
  }

  get attachmentPreview(): Locator {
    return this.mediaDropzone.locator('img')
  }

  get removeAttachmentButton(): Locator {
    return this.page
      .locator('div.relative.group')
      .filter({ has: this.attachmentPreview })
      .locator('button')
  }

  get uploadFailureMessage(): Locator {
    return this.page.getByText(/media upload failed/i)
  }

  // ---- PR 2 locators ----

  /** Dropzone button rendered as a 118x118 dashed tile next to the attachments. */
  get mediaDropzone(): Locator {
    return this.page.getByTestId('composer-inline-dropzone')
  }

  /**
   * Picker shell (full overlay modal). The role/name fallback uses the
   * `Media Library` heading text inside the shell.
   */
  get pickerShell(): Locator {
    return this.page.getByTestId('composer-media-picker-shell')
  }

  /** Library tab inside the picker shell. */
  get libraryTab(): Locator {
    return this.page.getByTestId('picker-source-library')
  }

  /** Unsplash tab inside the picker shell. */
  get unsplashTab(): Locator {
    return this.page.getByTestId('picker-source-unsplash')
  }

  get unsplashSearchInput(): Locator {
    return this.pickerShell.getByRole('searchbox')
  }

  get unsplashSearchButton(): Locator {
    return this.pickerShell.getByRole('button', { name: /search|buscar/i })
  }

  get unsplashResults(): Locator {
    return this.page.getByTestId('provider-panel-results')
  }

  /**
   * Named locator for Unsplash result cards.
   * Uses the named-image role (accessible) backed by data-testid for robustness.
   * Falls back to testid when the photo name matches.
   */
  unsplashResult(externalId: string): Locator {
    return this.page.getByTestId(`provider-result-${externalId}`)
  }

  /**
   * Named locator for Unsplash result images.
   * Uses the img role with accessible alt text for screen readers.
   * Falls back to testid when alt text is not reliably exposed.
   */
  unsplashResultImage(externalId: string): Locator {
    return this.unsplashResult(externalId).getByRole('img')
  }

  unsplashImportButton(externalId: string): Locator {
    return this.unsplashResult(externalId).getByTestId('provider-panel-import')
  }

  /**
   * Empty state locator when a search returns no results.
   * No distinguishing accessible role is present, so data-testid is used.
   */
  get unsplashEmptyState(): Locator {
    return this.page.getByTestId('provider-panel-empty')
  }

  /**
   * Error state locator when provider search fails.
   * No distinguishing accessible role is present, so data-testid is used.
   */
  get unsplashErrorState(): Locator {
    return this.page.getByTestId('provider-panel-search-error')
  }

  /** Button to open the media picker from the compose modal. */
  get addMediaButton(): Locator {
    return this.page.getByTestId('add-media-button')
  }

  /** Apply button inside the picker footer. */
  get pickerApply(): Locator {
    return this.page.getByTestId('picker-apply')
  }

  /** Apply-disabled warning inside the picker footer. */
  get pickerApplyWarning(): Locator {
    return this.page.getByTestId('picker-apply-warning')
  }

  /** Inline attachment-limit warning rendered beneath the textarea. */
  get limitWarning(): Locator {
    return this.page.getByTestId('attachment-limit-warning')
  }

  get uploadStatus(): Locator {
    return this.mediaDropzone.getByText(
      /keep editing while this finishes|keepEditingWhileUploading/i,
    )
  }

  get uploadOverlay(): Locator {
    return this.page.getByTestId('inline-upload-overlay')
  }

  /**
   * "+N more files" summary rendered when attachment previews are truncated.
   */
  get overflowCard(): Locator {
    return this.page.getByTestId('inline-attachment-overflow')
  }

  /**
   * Preview media `<img>` inside the social preview panel.
   * Structural fallback: any `<img>` inside a region labelled
   * "LinkedIn Preview".
   */
  get socialPreviewMediaImg(): Locator {
    return this.page
      .locator('[data-media-src-kind]')
      .or(this.page.locator('img[alt="Media preview"]'))
  }

  /**
   * Per-card remove button located by assetId / tempKey.
   * Accepts a stable identifier (assetId for drafts, "local-upload" for
   * the in-flight upload).
   */
  removeAttachmentById(id: string): Locator {
    return this.page.getByTestId(`attachment-remove-${id}`)
  }

  /**
   * Remove a card by its visible file name. Resolves the underlying testid
   * via DOM inspection so the page object does not need to know the assetId.
   */
  async removeAttachmentByName(name: string): Promise<void> {
    const card = this.page
      .locator('.group\\/attachment')
      .filter({ has: this.page.getByTitle(name) })
    await card.locator('button').first().click()
  }

  /**
   * Inline attachment card by visible index in the composer. Skips the
   * dropzone (last sibling) and the overflow card (sibling with "+N").
   */
  attachmentByIndex(index: number): Locator {
    return this.page.locator('.group\\/attachment').nth(index)
  }

  /** Resolves a library asset card by its assetId. */
  libraryAssetCard(assetId: string): Locator {
    return this.page.getByTestId(`picker-asset-card-${assetId}`)
  }

  /**
   * Attach one or more media files to the composer. Backed by the hidden
   * `<input type="file">` so the change event fires the same code path
   * as a user click.
   */
  async attachMediaFiles(paths: string[]): Promise<void> {
    await this.mediaFileInput.setInputFiles(paths)
  }

  /**
   * Drop one or more files onto the composer. Constructs a `DataTransfer`
   * and dispatches `drop` on the dropzone tile. Falls back to
   * `setInputFiles` if the browser does not surface a usable drop target.
   */
  async dropFiles(paths: string[]): Promise<void> {
    const files = paths.map((p) => {
      const name = p.split('/').pop() ?? p
      const extension = name.split('.').pop()?.toLowerCase()
      const typeByExtension: Record<string, string> = {
        gif: 'image/gif',
        jpeg: 'image/jpeg',
        jpg: 'image/jpeg',
        mp4: 'video/mp4',
        pdf: 'application/pdf',
        png: 'image/png',
        txt: 'text/plain',
        webp: 'image/webp',
      }
      return { name, type: extension ? (typeByExtension[extension] ?? '') : '' }
    })
    await this.page.evaluate(
      async ({ files }) => {
        const dt = new DataTransfer()
        for (const { name, type } of files) {
          const file = new File([new Uint8Array([0])], name, { type })
          dt.items.add(file)
        }
        const target = document.querySelector(
          '[data-testid="composer-inline-dropzone"]',
        ) as HTMLElement | null
        if (!target) throw new Error('dropzone not found')
        const events = ['dragover', 'drop']
        for (const type of events) {
          const event = new DragEvent(type, { bubbles: true, cancelable: true, dataTransfer: dt })
          target.dispatchEvent(event)
        }
      },
      { files },
    )
  }

  /**
   * Classify the social preview's media source URL into a stable enum.
   * `blob` — preview is rendering the transient upload blob.
   * `persisted` — preview is rendering the persisted `/api/media/...` URL.
   * `none` — preview is not rendering any media.
   */
  async previewMediaSrcKind(): Promise<'blob' | 'persisted' | 'none'> {
    const handle = await this.socialPreviewMediaImg
      .first()
      .elementHandle({ timeout: 1_000 })
      .catch(() => null)
    if (!handle) return 'none'
    const src = await handle.getAttribute('src')
    if (!src) return 'none'
    if (src.startsWith('blob:')) return 'blob'
    if (src.startsWith('/api/media/') || src.includes('/api/media/')) return 'persisted'
    return 'none'
  }

  async expectUploadStatusText(matcher: RegExp | string): Promise<void> {
    const pattern = typeof matcher === 'string' ? new RegExp(matcher, 'i') : matcher
    await expect(this.uploadStatus).toBeVisible({ timeout: 10_000 })
    await expect(this.uploadStatus).toContainText(pattern)
  }

  /**
   * Reads the visible "+N" overflow count. Returns 0 if no overflow card.
   */
  async overflowCount(): Promise<number> {
    const handle = await this.overflowCard
      .first()
      .elementHandle({ timeout: 500 })
      .catch(() => null)
    if (!handle) return 0
    const text = (await handle.textContent())?.trim() ?? ''
    const match = /^\+(\d+) more files?$/.exec(text)
    if (!match) return 0
    return Number(match[1])
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
    // Click an enabled day number in the calendar popover.
    // Month grids can render duplicate labels for outside-view days, so avoid disabled cells.
    const dayButton = this.page
      .locator('[data-slot="calendar-cell-trigger"]:not([data-disabled])')
      .getByText(String(day), { exact: true })
    await dayButton.click()
  }

  async clickScheduleNow(): Promise<void> {
    await this.scheduleNowButton.click()
  }

  async clickSchedulePost(): Promise<void> {
    await this.schedulePostButton.click()
  }

  async clickSave(): Promise<void> {
    await this.saveButton.click({ force: true })
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

  async openMediaPicker(): Promise<void> {
    await this.addMediaButton.click()
    await this.libraryTab.click()
  }

  async searchUnsplash(query: string): Promise<void> {
    await this.unsplashSearchInput.fill(query)
    await this.unsplashSearchButton.click()
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

  // ---- Media attachment actions ----

  /**
   * Attach a media file to the post by absolute path.
   */
  async attachMedia(absolutePath: string): Promise<void> {
    await this.mediaFileInput.setInputFiles(absolutePath)
  }

  /**
   * Remove the attached media file.
   */
  async removeAttachment(): Promise<void> {
    await this.removeAttachmentButton.click()
  }

  /**
   * Get the attachment preview element (for assertions).
   */
  getAttachmentPreview(): Locator {
    return this.attachmentPreview
  }

  /**
   * Check if the upload failure message is visible.
   */
  async expectUploadFailure(): Promise<void> {
    await expect(this.uploadFailureMessage).toBeVisible()
  }
}
