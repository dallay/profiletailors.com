import { test, expect, type Request } from '@playwright/test'

// Window type extensions for consent flags
declare global {
  interface Window {
    __PT_CONSENT_ANALYTICS?: boolean
    __PT_DNT?: boolean
  }
}

test.describe('Consent Management', () => {
  test.beforeEach(async ({ page }) => {
    // Initialize consent state before any navigation
    await page.addInitScript(() => {
      localStorage.removeItem('pt-consent')
    })
    await page.goto('/')
  })

  // TASK-014: Accept all scenario
  test('accept-all sets analytics true and loads Ahrefs', async ({ page }) => {
    // Banner should be visible
    const banner = page.locator('#consent-banner')
    await expect(banner).toBeVisible()

    // Click accept all
    await page.click('[data-consent-accept]')

    // Verify localStorage receipt
    const receipt = await page.evaluate(() => {
      const raw = localStorage.getItem('pt-consent')
      return raw ? JSON.parse(raw) : null
    })
    expect(receipt.categories.analytics).toBe(true)
    expect(receipt.categories.necessary).toBe(true)
    expect(receipt.source).toBe('banner')

    // Reload and check banner is hidden
    await page.reload()
    await expect(banner).toBeHidden()

    // Verify analytics script loaded (window.__PT_CONSENT_ANALYTICS should be true)
    const analyticsFlag = await page.evaluate(() => window.__PT_CONSENT_ANALYTICS)
    expect(analyticsFlag).toBe(true)
  })

  // TASK-015: Reject all scenario
  test('reject-all sets analytics false and blocks Ahrefs', async ({ page }) => {
    // Banner should be visible
    const banner = page.locator('#consent-banner')
    await expect(banner).toBeVisible()

    // Click reject all using accessible selector
    await page.getByRole('button', { name: 'Reject all' }).click()

    // Verify localStorage receipt
    const receipt = await page.evaluate(() => {
      const raw = localStorage.getItem('pt-consent')
      return raw ? JSON.parse(raw) : null
    })
    expect(receipt.categories.analytics).toBe(false)
    expect(receipt.categories.necessary).toBe(true)

    // Reload and check banner is hidden
    await page.reload()
    await expect(banner).toBeHidden()

    // Verify analytics flag is false
    const analyticsFlag = await page.evaluate(() => window.__PT_CONSENT_ANALYTICS)
    expect(analyticsFlag).toBe(false)

    // Verify no Ahrefs request occurred
    const ahrefsRequests: Request[] = []
    await page.route(/analytics\.ahrefs\.com/, (route) => {
      ahrefsRequests.push(route.request())
      route.abort()
    })
    await page.goto('/')
    expect(ahrefsRequests).toHaveLength(0)
  })

  // TASK-016: DNT enabled scenario
  test('dnt-enabled sets analytics off by default', async ({ page }) => {
    // Mock DNT signal
    await page.addInitScript(() => {
      Object.defineProperty(navigator, 'doNotTrack', {
        value: '1',
        writable: true,
        configurable: true,
      })
    })

    await page.goto('/')

    // Banner should be visible
    const banner = page.locator('#consent-banner')
    await expect(banner).toBeVisible()

    // Toggle should be OFF by default when DNT is enabled (using accessible selector)
    const toggle = page.getByRole('switch', { name: 'Analytics cookies' })
    await expect(toggle).toHaveAttribute('aria-checked', 'false')

    // User can still override using accessible selector
    await page.getByRole('button', { name: 'Accept all' }).click()

    // Verify receipt with dnt flag
    const receipt = await page.evaluate(() => {
      const raw = localStorage.getItem('pt-consent')
      return raw ? JSON.parse(raw) : null
    })
    expect(receipt.dnt).toBe(true)
    expect(receipt.categories.analytics).toBe(true) // User override

    // Verify DNT signal was detected
    const dntFlag = await page.evaluate(() => window.__PT_DNT)
    expect(dntFlag).toBe(true)
  })

  // Additional test: Save preferences with analytics off
  test('save preferences allows granular control', async ({ page }) => {
    const banner = page.locator('#consent-banner')
    await expect(banner).toBeVisible()

    // Toggle analytics OFF (click the toggle)
    const toggle = page.locator('[data-consent-analytics]')
    await toggle.click()

    // Toggle should now be OFF
    await expect(toggle).toHaveAttribute('aria-checked', 'false')

    // Save preferences
    await page.click('[data-consent-save]')

    // Verify localStorage receipt
    const receipt = await page.evaluate(() => {
      const raw = localStorage.getItem('pt-consent')
      return raw ? JSON.parse(raw) : null
    })
    expect(receipt.categories.analytics).toBe(false)
    expect(receipt.source).toBe('banner')
  })

  // Test: Version upgrade requires re-consent
  test('outdated consentVersion shows banner', async ({ page }) => {
    // Set outdated consent
    await page.evaluate(() => {
      localStorage.setItem(
        'pt-consent',
        JSON.stringify({
          consentVersion: 0, // Outdated
          policyVersion: '2026-07-23',
          timestamp: '2026-07-01T10:00:00Z',
          region: 'EU',
          categories: { necessary: true, analytics: true },
          dnt: false,
          source: 'banner',
        })
      )
    })

    await page.goto('/')

    // Banner should be visible
    const banner = page.locator('#consent-banner')
    await expect(banner).toBeVisible()
  })

  // Test: GPC signal treated identically to DNT
  test('gpc-signal blocks analytics by default', async ({ page }) => {
    // Mock GPC signal
    await page.addInitScript(() => {
      Object.defineProperty(navigator, 'globalPrivacyControl', {
        value: true,
        writable: true,
        configurable: true,
      })
    })

    await page.goto('/')

    // Toggle should be OFF by default when GPC is enabled
    const toggle = page.locator('[data-consent-analytics]')
    await expect(toggle).toHaveAttribute('aria-checked', 'false')
  })
})
