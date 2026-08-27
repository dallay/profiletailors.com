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
    // Clear any persisted consent exactly once, before the banner script runs.
    // Must NOT be an addInitScript: that would re-run on the banner's own
    // location.reload() and wipe the receipt right after it is saved.
    await page.goto('/')
    await page.evaluate(() => localStorage.removeItem('pt-consent'))
    await page.reload()
  })

  // TASK-014: Accept all scenario
  test('accept-all sets analytics true and loads Ahrefs', async ({ page }) => {
    const banner = page.locator('#consent-banner')
    await expect(banner).toBeVisible()
    await expect(banner).not.toHaveAttribute('aria-modal')
    await expect(page.locator('.consent-backdrop')).toHaveCount(0)

    await page.click('[data-consent-accept-all]')

    // Saving triggers a reload; wait for it before asserting
    await page.waitForLoadState('load')

    // Verify localStorage receipt
    const receipt = await page.evaluate(() => {
      const raw = localStorage.getItem('pt-consent')
      return raw ? JSON.parse(raw) : null
    })
    expect(receipt.categories.analytics).toBe(true)
    expect(receipt.categories.necessary).toBe(true)
    expect(receipt.source).toBe('banner')

    // Banner is hidden after reload
    await expect(banner).toBeHidden()

    // Verify analytics script loaded (window.__PT_CONSENT_ANALYTICS should be true)
    const analyticsFlag = await page.evaluate(() => window.__PT_CONSENT_ANALYTICS)
    expect(analyticsFlag).toBe(true)
  })

  test('keeps landing navigation available before consent is decided', async ({ page }) => {
    await expect(page.locator('#consent-banner')).toBeVisible()

    await page.locator('a[href="/es/"]').click()

    await expect(page).toHaveURL(/\/es\/$/)
  })

  // TASK-015: Reject all scenario
  test('reject-all sets analytics false and blocks Ahrefs', async ({ page }) => {
    const banner = page.locator('#consent-banner')
    await expect(banner).toBeVisible()

    await page.getByRole('button', { name: 'Reject all' }).click()

    // Saving triggers a reload; wait for it before asserting
    await page.waitForLoadState('load')

    // Verify localStorage receipt
    const receipt = await page.evaluate(() => {
      const raw = localStorage.getItem('pt-consent')
      return raw ? JSON.parse(raw) : null
    })
    expect(receipt.categories.analytics).toBe(false)
    expect(receipt.categories.necessary).toBe(true)

    // Banner is hidden after reload
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

    const banner = page.locator('#consent-banner')
    await expect(banner).toBeVisible()

    await page.getByRole('button', { name: 'Customize' }).click()
    const toggle = page.locator('[data-consent-analytics]')
    await expect(toggle).toHaveAttribute('aria-checked', 'false')

    await page.getByRole('button', { name: 'Accept all' }).click()

    // Saving triggers a reload; wait for it before asserting
    await page.waitForLoadState('load')

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

  test('save preferences persists the customize state', async ({ page }) => {
    const banner = page.locator('#consent-banner')
    await expect(banner).toBeVisible()

    await page.getByRole('button', { name: 'Customize' }).click()
    const toggle = page.locator('[data-consent-analytics]')
    await expect(toggle).toHaveAttribute('aria-checked', 'true')

    await page.click('[data-consent-save]')

    // Saving triggers a reload; wait for it before asserting
    await page.waitForLoadState('load')

    // Verify localStorage receipt
    const receipt = await page.evaluate(() => {
      const raw = localStorage.getItem('pt-consent')
      return raw ? JSON.parse(raw) : null
    })
    expect(receipt.categories.analytics).toBe(true)
    expect(receipt.source).toBe('banner')
  })

  test('cookie settings link reopens the non-modal customize panel', async ({ page }) => {
    const banner = page.locator('#consent-banner')
    await expect(banner).toBeVisible()

    await page.getByRole('button', { name: 'Reject all' }).click()
    await page.waitForLoadState('load')
    await expect(banner).toBeHidden()

    await page.locator('#cookie-settings-link').click()

    await expect(banner).toBeVisible()
    await expect(page.locator('[data-consent-customize-panel]')).toBeVisible()
    await expect(banner).not.toHaveAttribute('aria-modal')
  })

  // Test: Version upgrade requires re-consent
  test('outdated consentVersion shows banner', async ({ page }) => {
    await page.evaluate(() => {
      localStorage.setItem(
        'pt-consent',
        JSON.stringify({
          consentVersion: 0,
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

    await page.getByRole('button', { name: 'Customize' }).click()
    const toggle = page.locator('[data-consent-analytics]')
    await expect(toggle).toHaveAttribute('aria-checked', 'false')
  })
})
