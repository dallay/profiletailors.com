import { expect, type Page, type Locator } from '@playwright/test'

/**
 * Page Object Model for the Login / Register page (AuthView.vue).
 *
 * The same Vue component is used for both /login and /register routes.
 * Behaviour switches based on the current route name.
 *
 * NOTE: Language and theme controls are NOT available on the auth page.
 * They live in the dashboard sidebar and Settings page (requires auth).
 */
export class LoginPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  // ---- Locators ----

  get emailInput(): Locator {
    return this.page.getByLabel(/email/i)
  }

  get passwordInput(): Locator {
    return this.page.getByLabel(/password/i)
  }

  get submitButton(): Locator {
    return this.page.getByRole('button', { name: /sign in|iniciar sesión|create account|crear cuenta/i })
  }

  get errorBanner(): Locator {
    return this.page.locator('[class*="border-error"]')
  }

  get heading(): Locator {
    return this.page.getByRole('heading', { level: 2 })
  }

  get heroTitle(): Locator {
    return this.page.getByRole('heading', { level: 1 })
  }

  get alternateLink(): Locator {
    return this.page.locator('a').filter({ hasText: /register|sign in|crear cuenta|iniciar sesión/i })
  }

  get badge(): Locator {
    return this.page.getByText(/local access|acceso local/i)
  }

  // ---- Actions ----

  async goto(basePath = '/login'): Promise<void> {
    try {
      await this.page.goto(basePath)
    } catch {
      // WebKit: throws "Navigation to X is interrupted by another navigation to X"
      // when the SPA route guard triggers a redirect during page load.
      await this.page.waitForLoadState('domcontentloaded').catch(() => {})
    }
  }

  async fillEmail(email: string): Promise<void> {
    await this.emailInput.fill(email)
  }

  async fillPassword(password: string): Promise<void> {
    await this.passwordInput.fill(password)
  }

  async submit(): Promise<void> {
    await this.submitButton.click()
  }

  async login(email: string, password: string): Promise<void> {
    await this.fillEmail(email)
    await this.fillPassword(password)
    await this.submit()
  }

  // ---- Assertions ----

  async expectErrorVisible(message?: string | RegExp): Promise<void> {
    if (message) {
      await expect(this.errorBanner).toContainText(message)
    }
    await expect(this.errorBanner).toBeVisible()
  }

  async expectErrorHidden(): Promise<void> {
    await expect(this.errorBanner).toHaveCount(0)
  }

  async expectOnLoginPage(): Promise<void> {
    // Wait for the login form to render (SPA redirect happens during page load)
    await expect(this.heading).toBeVisible({ timeout: 15_000 })
    await expect(this.submitButton).toBeVisible()
    // Use toHaveURL to check the CURRENT URL (not waitForURL which waits for
    // a future navigation event — the SPA redirect already happened)
    await expect(this.page).toHaveURL(/\/login/, { timeout: 5_000 })
  }

  async expectOnRegisterPage(): Promise<void> {
    // Wait for Vue Router to navigate to /register (client-side)
    await expect(this.page).toHaveURL(/\/register/, { timeout: 10_000 })
    await expect(this.heading).toBeVisible({ timeout: 10_000 })
  }

  async expectRedirectedTo(path: string): Promise<void> {
    await this.page.waitForURL(path)
  }
}
