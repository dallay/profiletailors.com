import type { Page, Locator } from '@playwright/test'

/**
 * Page Object Model for the authenticated dashboard (HomeView.vue).
 */
export class DashboardPage {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  get welcomeHeading(): Locator {
    return this.page.getByRole('heading', { name: /welcome back|bienvenido/i })
  }

  get userDisplayName(): Locator {
    return this.page
      .locator('button')
      .filter({ hasText: /DU|dev user|profile tailors/i })
      .first()
  }

  get newPostButton(): Locator {
    return this.page.getByRole('button', { name: /new post|nueva publicación/i })
  }

  async expectAuthenticated(): Promise<void> {
    await this.welcomeHeading.waitFor({ state: 'visible', timeout: 10_000 })
  }
}
