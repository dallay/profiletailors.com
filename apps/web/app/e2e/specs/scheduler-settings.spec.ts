import { test, expect } from '../fixtures/scheduler-base-test'
import { authenticateAs, keepSessionAlive } from '../fixtures/auth-helpers'
import { safeGoto } from '../fixtures/navigation'
import { APP_URL } from '../fixtures/test-data'

test.describe('Scheduler — Settings Persistence', () => {
  test.beforeEach(async ({ page }) => {
    await authenticateAs(page)
    await keepSessionAlive(page)
  })

  /**
   * TC-21: Theme persistence across page reload.
   */
  test('TC-21: theme persistence @settings @theme @persistence', async ({ page }) => {
    // Theme controls live in the sidebar account menu, not the Settings page.
    const accountMenuTrigger = page.getByRole('button', { name: /dev user|profile tailors/i }).first()
    await accountMenuTrigger.click()

    const initialSettings = await page.evaluate(() => JSON.parse(localStorage.getItem('pt_settings_v1') || '{}'))
    const initialTheme = initialSettings.theme || 'dark'
    const nextTheme = initialTheme === 'dark' ? 'light' : 'dark'

    const themeRadio = page.getByRole('radio', { name: new RegExp(nextTheme, 'i') })
    await themeRadio.click({ force: true })
    await page.waitForTimeout(300)

    const updatedSettings = await page.evaluate(() => JSON.parse(localStorage.getItem('pt_settings_v1') || '{}'))
    expect(updatedSettings.theme).toBe(nextTheme)

    await page.reload()
    await page.waitForLoadState('networkidle')

    const afterReloadSettings = await page.evaluate(() => JSON.parse(localStorage.getItem('pt_settings_v1') || '{}'))
    expect(afterReloadSettings.theme).toBe(nextTheme)

    // No need to toggle back in the same test — persistence already verified.
  })

  /**
   * TC-22: Locale persistence across page reload.
   */
  test('TC-22: locale persistence @settings @locale @persistence', async ({ page }) => {
    await safeGoto(page, APP_URL.settings)
    await page.waitForTimeout(500)

    const initialSettings = await page.evaluate(() => JSON.parse(localStorage.getItem('pt_settings_v1') || '{}'))
    const initialLocale = initialSettings.locale || 'en'
    const nextLocale = initialLocale === 'en' ? 'es' : 'en'

    await page.getByTestId(`settings-language-${nextLocale}`).click({ force: true })
    await page.waitForTimeout(300)

    const updatedSettings = await page.evaluate(() => JSON.parse(localStorage.getItem('pt_settings_v1') || '{}'))
    expect(updatedSettings.locale).toBe(nextLocale)

    await page.reload()
    await page.waitForLoadState('networkidle')

    const afterReloadSettings = await page.evaluate(() => JSON.parse(localStorage.getItem('pt_settings_v1') || '{}'))
    expect(afterReloadSettings.locale).toBe(nextLocale)

    // Verify we're still on settings page after reload (data-testid is language-agnostic)
    await expect(page.getByTestId('settings-shell')).toBeVisible({ timeout: 10_000 })

    // Persistence already verified — no need to toggle back.
  })
})
