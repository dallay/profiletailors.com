import { test, expect } from '@playwright/test'

test.describe('PWA offline shell @frontend', () => {
  test('renders /offline standalone without auth', async ({ page }) => {
    await page.goto('/offline')
    await expect(page.getByRole('heading', { name: /offline|sin conexión/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /retry|reintentar/i })).toBeVisible()
  })

  test('keeps /offline usable with a controlling service worker @production-preview', async ({
    page,
    context,
  }, testInfo) => {
    test.skip(testInfo.project.name !== 'pwa-preview', 'Requires a production preview build')

    await page.goto('/offline')
    await page.evaluate(() => navigator.serviceWorker.ready)
    await page.reload()
    await expect
      .poll(() => page.evaluate(() => Boolean(navigator.serviceWorker.controller)))
      .toBe(true)

    await context.setOffline(true)
    const response = await page.goto('/offline')
    expect(response?.ok()).toBe(true)
    await expect(page.getByRole('heading', { name: /offline|sin conexión/i })).toBeVisible()
    const retry = page.getByRole('button', { name: /retry|reintentar/i })
    await expect(retry).toBeEnabled()
    await retry.click()
    await expect(retry).toBeEnabled()
  })

  test('manifest is valid and installable metadata exists', async ({ page }) => {
    const response = await page.request.get('/manifest.webmanifest')
    expect(response.ok()).toBeTruthy()
    const manifest = await response.json()
    expect(manifest.id).toBe('/')
    expect(manifest.scope).toBe('/')
    expect(manifest.display).toBe('standalone')
    expect(manifest.short_name.length).toBeLessThanOrEqual(12)
    expect(manifest.icons.length).toBeGreaterThanOrEqual(2)
  })
})
