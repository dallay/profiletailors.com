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
}
