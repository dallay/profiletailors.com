import { expect, type Locator, type Page } from '@playwright/test'

export class PasswordRecoveryPage {
  constructor(readonly page: Page) {}

  get email(): Locator {
    return this.page.locator('#recovery-email')
  }
  get newPassword(): Locator {
    return this.page.locator('#new-password')
  }
  get confirmation(): Locator {
    return this.page.locator('#confirm-new-password')
  }
  get submit(): Locator {
    return this.page.locator('button[type="submit"]')
  }
  get alert(): Locator {
    return this.page.getByRole('alert')
  }
  get status(): Locator {
    return this.page.locator('output[aria-live="polite"]')
  }

  async requestReset(email: string): Promise<void> {
    await this.email.fill(email)
    await this.submit.click()
  }

  async resetPassword(password: string): Promise<void> {
    await this.newPassword.fill(password)
    await this.confirmation.fill(password)
    await this.submit.click()
  }

  async expectNoHorizontalOverflow(): Promise<void> {
    expect(
      await this.page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true)
  }

  async tabTo(id: string, direction: 'forward' | 'backward' = 'forward'): Promise<void> {
    const key = direction === 'forward' ? 'Tab' : 'Shift+Tab'
    for (let attempt = 0; attempt < 12; attempt += 1) {
      await this.page.keyboard.press(key)
      if ((await this.page.evaluate(() => document.activeElement?.id)) === id) return
    }
    throw new Error(`Keyboard focus did not reach #${id}`)
  }

  async tabUntil(locator: Locator, direction: 'forward' | 'backward' = 'forward'): Promise<void> {
    const key = direction === 'forward' ? 'Tab' : 'Shift+Tab'
    for (let attempt = 0; attempt < 12; attempt += 1) {
      await this.page.keyboard.press(key)
      if (await locator.evaluate((element) => element === document.activeElement)) return
    }
    throw new Error('Keyboard focus did not reach the expected control')
  }

  async expectFocused(locator: Locator): Promise<void> {
    await expect(locator).toBeFocused()
  }

  async expectVisibleFocus(locator: Locator): Promise<void> {
    await expect(locator).toBeFocused()
    await expect
      .poll(() =>
        locator.evaluate((element) => {
          const styles = getComputedStyle(element)
          return styles.outlineStyle !== 'none' || styles.boxShadow !== 'none'
        }),
      )
      .toBe(true)
  }

  /**
   * Measure the bounding box height and width of an element and assert
   * each dimension is >= minPx CSS pixels (for touch target size).
   */
  async expectTouchTarget(locator: Locator, minPx: number): Promise<void> {
    const box = await locator.boundingBox()
    expect(box, `bounding box must exist for touch target assertion`).not.toBeNull()
    if (!box) return
    expect(box.width, `touch target width must be >= ${minPx}px`).toBeGreaterThanOrEqual(minPx)
    expect(box.height, `touch target height must be >= ${minPx}px`).toBeGreaterThanOrEqual(minPx)
  }
}
