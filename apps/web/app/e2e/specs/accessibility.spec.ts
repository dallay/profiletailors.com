import { test, expect } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { APP_URL, I18N_TEXT } from '../fixtures/test-data'
import { SchedulerPage } from '../pages/scheduler-page'
import { authenticateAs } from '../fixtures/auth-helpers'

test.describe('Accessibility & i18n Alignment', () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('Register form has proper labels and for attributes', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.register)

    const emailLabel = page.locator('label[for="email"]')
    const passwordLabel = page.locator('label[for="password"]')

    await expect(emailLabel).toBeVisible()
    await expect(passwordLabel).toBeVisible()
  })

  test('Keyboard navigation follows logical order on register', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.register)

    await page.keyboard.press('Tab')
    await expect(loginPage.emailInput).toBeFocused()

    await page.keyboard.press('Tab')
    await expect(loginPage.passwordInput).toBeFocused()

    await page.keyboard.press('Tab')
    await expect(loginPage.submitButton).toBeFocused()
  })

  test('Focus is visible and styled (ring)', async ({ page }) => {
    const loginPage = new LoginPage(page)
    await loginPage.goto(APP_URL.register)

    await loginPage.emailInput.focus()
    // Verify user-visible focus indicator via computed styles
    const outline = await loginPage.emailInput.evaluate((el) => {
      const styles = window.getComputedStyle(el)
      return styles.outline || styles.boxShadow
    })
    expect(outline).not.toBe('none')
    expect(outline).not.toBe('')
  })

  test('Login page displays in Spanish', async ({ page }) => {
    // In this app, we might need to set the locale via a cookie or settings store mock
    // Assuming the app checks a 'locale' preference.
    // For E2E tests, we often use URL or localStorage.
    // Let's assume a mock approach if no easy URL switch exists.
    // For now, let's use the I18N_TEXT for verification if we can force it.

    const loginPage = new LoginPage(page)
    // Mock the settings store to 'es'
    await page.addInitScript(() => {
      window.localStorage.setItem('pt_settings_v1', JSON.stringify({ locale: 'es', theme: 'dark' }))
    })

    await loginPage.goto(APP_URL.login)

    const es = I18N_TEXT.es
    await expect(page.getByRole('heading', { name: es.titleLogin, level: 2 })).toBeVisible()
    await expect(loginPage.submitButton).toHaveText(es.submitLogin)
  })
})

test.describe('Scheduler Accessibility', () => {
  test.beforeEach(async ({ resetSession }) => {
    await resetSession()
  })

  test('Past calendar cells have correct aria-disabled attribute', async ({ page }) => {
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)

    // Mock the current date to a non-first day to ensure past cells exist
    await page.addInitScript(() => {
      const mockDate = new Date('2026-07-15T12:00:00Z')
      const OriginalDate = Date
      // @ts-ignore
      globalThis.Date = class extends OriginalDate {
        constructor(...args: any[]) {
          if (args.length === 0) {
            super(mockDate.getTime())
          } else {
            super(...args)
          }
        }
        static now() {
          return mockDate.getTime()
        }
      }
    })

    await scheduler.goto()
    await scheduler.switchToMonth()

    // Check if there are any past cells
    const pastCells = page.getByRole('gridcell', { disabled: true })
    const count = await pastCells.count()
    expect(count).toBeGreaterThan(0)
  })
})
