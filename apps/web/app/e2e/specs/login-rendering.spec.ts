import { test, expect, type Page } from '../fixtures/base-test'
import { LoginPage } from '../pages/login-page'
import { APP_URL } from '../fixtures/test-data'
import { mockRefreshFailure } from '../fixtures/auth-helpers'

async function mockCapabilities(
  page: Page,
  capabilities: { registrationEnabled: boolean; passwordRecoveryEnabled: boolean },
  delay = 0,
) {
  await page.route('**/api/capabilities/public', async (route) => {
    if (delay) await new Promise((resolve) => setTimeout(resolve, delay))
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(capabilities),
    })
  })
}

test.describe('Login redesign rendering', { tag: '@frontend' }, () => {
  test.beforeEach(async ({ page }) => {
    await mockRefreshFailure(page)
  })

  test('renders one focused column with shared branding, legal links, and no prohibited UI', async ({
    page,
  }) => {
    await mockCapabilities(page, { registrationEnabled: true, passwordRecoveryEnabled: true })
    const login = new LoginPage(page)
    await login.goto(APP_URL.login)

    await expect(login.brandName).toHaveText('Profile Tailors')
    await expect(page.locator('img[data-asset^="profiletailors-logotype"]:visible')).toHaveCount(1)
    await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible()
    await expect(login.emailInput).toHaveAttribute('autocomplete', 'username')
    await expect(login.passwordInput).toHaveAttribute('autocomplete', 'current-password')
    await expect(page.getByRole('link', { name: /terms of service/i })).toHaveAttribute(
      'href',
      '/terms',
    )
    await expect(page.getByRole('link', { name: /privacy policy/i })).toHaveAttribute(
      'href',
      '/privacy',
    )
    await expect(
      page.getByText(/continue with google|continue with apple|security|workflow/i),
    ).toHaveCount(0)
  })

  test('login stays usable while capabilities are loading and dependent links stay hidden', async ({
    page,
  }) => {
    await mockCapabilities(
      page,
      { registrationEnabled: true, passwordRecoveryEnabled: true },
      1_500,
    )
    const login = new LoginPage(page)
    await login.goto(APP_URL.login)

    await expect(login.emailInput).toBeEditable()
    await expect(login.passwordInput).toBeEditable()
    await expect(login.submitButton).toBeEnabled()
    await expect(page.getByRole('link', { name: /register|forgot password/i })).toHaveCount(0)
  })

  test('320px viewport has no horizontal overflow in light and dark themes', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 720 })
    await mockCapabilities(page, { registrationEnabled: true, passwordRecoveryEnabled: true })
    for (const theme of ['light', 'dark']) {
      await page.addInitScript((value) => {
        localStorage.setItem('pt_settings_v1', JSON.stringify({ locale: 'en', theme: value }))
      }, theme)
      await page.goto(APP_URL.login)
      await expect(new LoginPage(page).submitButton).toBeVisible()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
    }
  })

  test('shared symbol follows the explicit app theme even when the OS preference disagrees', async ({
    page,
  }) => {
    await page.emulateMedia({ colorScheme: 'dark' })
    await mockCapabilities(page, { registrationEnabled: true, passwordRecoveryEnabled: true })
    await page.addInitScript(() => {
      const theme = window.name === 'dark' ? 'dark' : 'light'
      localStorage.setItem('pt_settings_v1', JSON.stringify({ locale: 'en', theme }))
    })
    await page.goto(APP_URL.login)
    await expect(page.locator('html')).toHaveClass(/light/)
    await expect(page.locator('img[data-asset="profiletailors-logotype.svg"]')).toBeVisible()
    await expect(page.locator('img[data-asset="profiletailors-logotype-light.svg"]')).toBeHidden()

    await page.evaluate(() => {
      window.name = 'dark'
    })
    await page.reload()
    await expect(page.locator('html')).toHaveClass(/dark/)
    await expect(page.locator('img[data-asset="profiletailors-logotype.svg"]')).toBeHidden()
    await expect(page.locator('img[data-asset="profiletailors-logotype-light.svg"]')).toBeVisible()
  })

  test('auth text, errors, controls, and focus indicators meet WCAG AA in light and dark', async ({
    page,
  }) => {
    await mockCapabilities(page, { registrationEnabled: true, passwordRecoveryEnabled: true })
    await page.addInitScript(() => {
      const theme = window.name === 'dark' ? 'dark' : 'light'
      localStorage.setItem('pt_settings_v1', JSON.stringify({ locale: 'en', theme }))
    })

    for (const theme of ['light', 'dark']) {
      await page.goto(APP_URL.login)
      if (theme === 'dark') {
        await page.evaluate(() => {
          window.name = 'dark'
        })
        await page.reload()
      }
      await page.getByRole('button', { name: /sign in/i }).click()
      await page.locator('#login-email').focus()

      const ratios = await page.evaluate(() => {
        function parse(color: string): [number, number, number] {
          const values = color.match(/[\d.]+/g)?.map(Number) ?? []
          return [values[0] ?? 0, values[1] ?? 0, values[2] ?? 0]
        }
        function luminance(color: string): number {
          const [red, green, blue] = parse(color).map((value) => {
            const channel = value / 255
            return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
          })
          return 0.2126 * (red ?? 0) + 0.7152 * (green ?? 0) + 0.0722 * (blue ?? 0)
        }
        function contrast(foreground: string, background: string): number {
          const lighter = Math.max(luminance(foreground), luminance(background))
          const darker = Math.min(luminance(foreground), luminance(background))
          return (lighter + 0.05) / (darker + 0.05)
        }
        function requiredElement(selector: string): Element {
          const element = document.querySelector(selector)
          if (!element) throw new Error(`Expected ${selector} to be rendered`)
          return element
        }
        const selectors = ['label[for="login-email"]', '#login-email-error', '#login-email']
        const surface = getComputedStyle(requiredElement('section')).backgroundColor
        return selectors.map((selector) => {
          const style = getComputedStyle(requiredElement(selector))
          return {
            selector,
            ratio: contrast(
              style.color,
              style.backgroundColor === 'rgba(0, 0, 0, 0)' ? surface : style.backgroundColor,
            ),
          }
        })
      })

      expect(ratios).toHaveLength(3)
      for (const { selector, ratio } of ratios) {
        expect(ratio, `${theme} ${selector}`).toBeGreaterThanOrEqual(4.5)
      }
      await expect(page.locator('#login-email')).toHaveCSS('box-shadow', /rgb/)
    }
  })

  test('password toggle exposes pressed state and keyboard focus remains visible', async ({
    page,
  }) => {
    await mockCapabilities(page, { registrationEnabled: false, passwordRecoveryEnabled: false })
    await page.goto(APP_URL.login)
    const toggle = page.getByRole('button', { name: /show password/i })
    await expect(toggle).toHaveAttribute('aria-pressed', 'false')
    await toggle.focus()
    await page.keyboard.press('Enter')
    const hideToggle = page.getByRole('button', { name: /hide password/i })
    await expect(hideToggle).toHaveAttribute('aria-pressed', 'true')
    await expect(page.locator('input[type="text"][autocomplete="current-password"]')).toBeVisible()
    expect(
      await hideToggle.evaluate(
        (element) =>
          getComputedStyle(element).outlineStyle !== 'none' ||
          getComputedStyle(element).boxShadow !== 'none',
      ),
    ).toBe(true)
  })
})
