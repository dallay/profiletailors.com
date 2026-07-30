/**
 * Phase 3: E2E Verification — Consent Management (DALLAY-494)
 *
 * Covers TASK-026 through TASK-028:
 *   TASK-026  Accept all via banner — source='banner', analytics=true
 *   TASK-027  Withdrawal via CookieSettings — source='settings-panel', analytics=false
 *   TASK-028  Version upgrade re-consent — consentVersion: 0 → banner shows → re-accept
 *
 * All tests run against mocked API responses (no backend required).
 * HAR replay handles auth; consent API is intercepted via page.route()
 * for the sync endpoint.
 *
 * @see ConsentBanner.vue — Dialog-based consent banner with data-testid selectors
 * @see CookieSettings.vue — Standalone settings panel with Switch controls
 * @see consent.store.ts — Pinia store under test
 */

import { test, expect } from '../fixtures/base-test'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'
import { clearConsent, setConsentReceipt, mockConsentSync } from '../fixtures/consent-helpers'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Read the full consent receipt from localStorage, or null. */
async function readReceipt(page: import('@playwright/test').Page) {
  return page.evaluate(() => {
    const raw = localStorage.getItem('pt-consent')
    return raw ? JSON.parse(raw) : null
  })
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test.describe('Consent Management — App', () => {
  test.beforeEach(async ({ page }) => {
    // Bootstrap an authenticated session so AppShell renders
    await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })

    // Mock the consent sync endpoint so the store doesn't throw
    await mockConsentSync(page)

    // Start clean — no prior consent
    await clearConsent(page)
  })

  // -----------------------------------------------------------------------
  // TASK-026: Accept all via banner
  // -----------------------------------------------------------------------

  test('TASK-026: accept all sets analytics true with source banner @consent @frontend', async ({
    page,
  }) => {
    await page.goto('/')

    // Banner should be visible (no valid consent stored)
    const banner = page.getByTestId('consent-banner')
    await expect(banner).toBeVisible()

    // Click Accept All button
    await page.getByTestId('accept-all-btn').click()

    // Verify the dialog closes
    await expect(banner).not.toBeVisible()

    // Verify localStorage receipt
    const receipt = await readReceipt(page)
    expect(receipt).not.toBeNull()
    expect(receipt.categories.analytics).toBe(true)
    expect(receipt.categories.necessary).toBe(true)
    expect(receipt.source).toBe('banner')
    expect(receipt.consentVersion).toBe(1)
  })

  // -----------------------------------------------------------------------
  // TASK-027: Withdrawal via CookieSettings
  // -----------------------------------------------------------------------

  test('TASK-027: withdraw consent via cookie settings — source settings-panel @consent @frontend', async ({
    page,
  }) => {
    // Go to domain first to establish correct storage origin
    await page.goto('/')

    // Step 1: Start with valid consent (analytics=true)
    await setConsentReceipt(page, {
      consentVersion: 1,
      categories: { necessary: true, analytics: true },
      source: 'banner',
    })

    // Reload so the seeded storage is picked up by the app store
    await page.reload()

    // Wait for the app to fully load and hydrate
    await expect(page.getByRole('heading', { name: /welcome/i })).toBeVisible()

    // Banner should be hidden (valid consent exists)
    const banner = page.getByTestId('consent-banner')
    await expect(banner).not.toBeVisible()

    // Step 2: Open CookieSettings from the footer link (programmatic click to bypass viewport issues)
    const cookieSettingsLink = page.getByTestId('cookie-settings-link')
    await expect(cookieSettingsLink).toBeVisible()
    await page.evaluate(() => {
      const btn = document.querySelector(
        '[data-testid="cookie-settings-link"]',
      ) as HTMLButtonElement | null
      btn?.click()
    })

    // Step 3: CookieSettings dialog should open
    // The dialog uses v-model:open, so it renders as a Dialog with the Switch controls
    // Toggle analytics OFF — find the Switch inside the CookieSettings dialog
    // CookieSettings has `v-model="analyticsEnabled"` on its Switch
    // We click the Save button after toggling
    const saveButton = page.getByRole('button', { name: /save preferences|save/i })
    await expect(saveButton).toBeVisible()

    // Toggle analytics OFF — click the Switch component
    // CookieSettings.vue renders Switch v-model="analyticsEnabled",
    // which creates a button[role="switch"]. The dialog shows two switches:
    // one disabled (necessary) and one toggleable (analytics).
    // The second switch in the dialog is the analytics toggle.
    const switches = page.getByRole('switch')
    const analyticsSwitch = switches.nth(1) // 0=necessary(disabled), 1=analytics
    await analyticsSwitch.click()

    // Verify the switch is now off
    await expect(analyticsSwitch).toHaveAttribute('data-state', 'unchecked')

    // Step 4: Click Save
    await saveButton.click()

    // Step 5: Verify localStorage
    const receipt = await readReceipt(page)
    expect(receipt).not.toBeNull()
    expect(receipt.categories.analytics).toBe(false)
    expect(receipt.source).toBe('settings-panel')
    expect(receipt.consentVersion).toBe(1)
  })

  // -----------------------------------------------------------------------
  // TASK-028: Version upgrade re-consent
  // -----------------------------------------------------------------------

  test('TASK-028: version upgrade — outdated consentVersion shows banner, re-consent upgrades to v1 @consent @frontend', async ({
    page,
  }) => {
    // Go to domain first to establish correct storage origin
    await page.goto('/')

    // Step 1: Seed localStorage with an outdated consent (consentVersion=0)
    await setConsentReceipt(page, {
      consentVersion: 0,
      categories: { necessary: true, analytics: true },
      source: 'banner',
    })

    // Reload so the seeded storage is picked up by the app store
    await page.reload()

    // Step 2: Banner should be visible (version mismatch → no valid consent)
    const banner = page.getByTestId('consent-banner')
    await expect(banner).toBeVisible()

    // Step 3: Accept with the current version
    await page.getByTestId('accept-all-btn').click()

    // Step 4: Verify the receipt now has consentVersion=1
    const receipt = await readReceipt(page)
    expect(receipt).not.toBeNull()
    expect(receipt.consentVersion).toBe(1)
    expect(receipt.categories.analytics).toBe(true)

    // Step 5: Reload — banner should be hidden (valid consent now)
    await page.reload()
    await expect(banner).not.toBeVisible()
  })
})
