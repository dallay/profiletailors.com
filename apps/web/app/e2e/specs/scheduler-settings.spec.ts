import { test, expect } from '@playwright/test'
import { SchedulerPage } from '../pages/scheduler-page'
import { authenticateAs } from '../fixtures/auth-helpers'
import { safeGoto } from '../fixtures/navigation'
import { APP_URL } from '../fixtures/test-data'

test.describe('Scheduler — Settings Persistence', () => {
  test.beforeEach(async ({ page }) => {
    await authenticateAs(page)
  })

  /**
   * TC-21: Theme persistence across page reload.
   */
  test('TC-21: theme persistence @settings @theme @persistence', async ({ page }) => {
    // Navigate to settings
    await safeGoto(page, APP_URL.settings)
    await page.waitForTimeout(500)

    // Get the theme toggle button
    const themeToggle = page.locator('button').filter({ hasText: /light|dark|light|oscuro|claro/i }).first()

    // Check initial theme from localStorage
    const initialSettings = await page.evaluate(() => {
      return JSON.parse(localStorage.getItem('pt_settings_v1') || '{}')
    })
    const initialTheme = initialSettings.theme || 'dark'

    // Toggle to the other theme
    await themeToggle.click()
    await page.waitForTimeout(300)

    // Verify localStorage updated
    const updatedSettings = await page.evaluate(() => {
      return JSON.parse(localStorage.getItem('pt_settings_v1') || '{}')
    })
    expect(updatedSettings.theme).not.toBe(initialTheme)

    // Reload page and verify theme persists
    await page.reload()
    await page.waitForTimeout(500)

    const afterReloadSettings = await page.evaluate(() => {
      return JSON.parse(localStorage.getItem('pt_settings_v1') || '{}')
    })
    expect(afterReloadSettings.theme).toBe(updatedSettings.theme)

    // Toggle back
    await themeToggle.click()
    await page.waitForTimeout(300)

    const finalSettings = await page.evaluate(() => {
      return JSON.parse(localStorage.getItem('pt_settings_v1') || '{}')
    })
    expect(finalSettings.theme).toBe(initialTheme)
  })

  /**
   * TC-22: Locale persistence across page reload.
   */
  test('TC-22: locale persistence @settings @locale @persistence', async ({ page }) => {
    await safeGoto(page, APP_URL.settings)
    await page.waitForTimeout(500)

    // Get initial locale
    const initialSettings = await page.evaluate(() => {
      return JSON.parse(localStorage.getItem('pt_settings_v1') || '{}')
    })
    const initialLocale = initialSettings.locale || 'en'

    // Switch to the other locale
    const localeToggle = page.locator('button').filter({ hasText: /english|español|spanish/i }).first()
    await localeToggle.click()
    await page.waitForTimeout(300)

    // Verify localStorage updated
    const updatedSettings = await page.evaluate(() => {
      return JSON.parse(localStorage.getItem('pt_settings_v1') || '{}')
    })
    expect(updatedSettings.locale).not.toBe(initialLocale)

    // Reload and verify persistence
    await page.reload()
    await page.waitForTimeout(500)

    const afterReloadSettings = await page.evaluate(() => {
      return JSON.parse(localStorage.getItem('pt_settings_v1') || '{}')
    })
    expect(afterReloadSettings.locale).toBe(updatedSettings.locale)

    // Toggle back
    await localeToggle.click()
    await page.waitForTimeout(300)

    const finalSettings = await page.evaluate(() => {
      return JSON.parse(localStorage.getItem('pt_settings_v1') || '{}')
    })
    expect(finalSettings.locale).toBe(initialLocale)
  })
})
