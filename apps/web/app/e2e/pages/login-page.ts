import { expect, type Locator, type Page } from '@playwright/test'

export class LoginPage {
  constructor(readonly page: Page) {}

  get emailInput(): Locator {
    return this.page.locator('input[type="email"]').first()
  }

  get passwordInput(): Locator {
    return this.page.locator('input[autocomplete$="password"]').first()
  }

  get confirmPasswordInput(): Locator {
    return this.page.locator('#confirm-password')
  }

  get submitButton(): Locator {
    return this.page.locator('button[type="submit"]')
  }

  get errorBanner(): Locator {
    return this.page.getByRole('alert')
  }

  get heading(): Locator {
    return this.page.getByRole('heading').first()
  }

  get brandName(): Locator {
    return this.page.getByTestId('brand-name')
  }

  get alternateLink(): Locator {
    return this.page.getByRole('link', { name: /register|sign in|crear cuenta|iniciar sesión/i })
  }

  async goto(path = '/login'): Promise<void> {
    await this.page.goto(path)
  }

  async fillEmail(email: string): Promise<void> {
    await this.emailInput.fill(email)
  }

  async fillPassword(password: string): Promise<void> {
    await this.passwordInput.fill(password)
    if (await this.confirmPasswordInput.isVisible().catch(() => false)) {
      await this.confirmPasswordInput.fill(password)
    }
  }

  async acceptRegistrationRequirements(): Promise<void> {
    await this.page.locator('#ageEligibility').check()
    await this.page.locator('#terms').check()
  }

  async submit(): Promise<void> {
    await this.submitButton.click()
  }

  async login(email: string, password: string): Promise<void> {
    await this.fillEmail(email)
    await this.fillPassword(password)
    await this.submit()
  }

  async expectErrorVisible(message?: string | RegExp): Promise<void> {
    await expect(this.errorBanner).toBeVisible()
    if (message) await expect(this.errorBanner).toContainText(message)
  }

  async expectErrorHidden(): Promise<void> {
    await expect(this.errorBanner).toHaveCount(0)
  }

  async expectOnLoginPage(): Promise<void> {
    await expect(this.page).toHaveURL(/\/login/)
    await expect(this.page.getByRole('heading', { name: /welcome back|bienvenido/i })).toBeVisible()
  }

  async expectOnRegisterPage(): Promise<void> {
    await expect(this.page).toHaveURL(/\/register/)
    await expect(
      this.page.getByRole('heading', { name: /create account|crear cuenta/i }).first(),
    ).toBeVisible()
  }

  async expectRedirectedTo(path: string): Promise<void> {
    await this.page.waitForURL(path)
  }
}
