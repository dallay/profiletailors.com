import { test, expect } from '@playwright/test'

test.describe('PWA offline shell @frontend', () => {
  test('renders /offline standalone without auth', async ({ page }) => {
    await page.goto('/offline')
    await expect(page.getByRole('heading', { name: /offline|sin conexión/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /retry|reintentar/i })).toBeVisible()
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
