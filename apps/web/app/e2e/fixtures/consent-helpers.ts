/**
 * Consent E2E test helpers.
 *
 * Provides utilities for seeding and clearing consent state, mocking
 * privacy signals, and intercepting the consent sync API.
 *
 * @see auth-helpers.ts — similar pattern for auth state management
 */

import type { Page, Route } from '@playwright/test'

// ---------------------------------------------------------------------------
// Constants — matching shared/web/types/consent.ts
// ---------------------------------------------------------------------------

const CONSENT_STORAGE_KEY = 'pt-consent'
const CURRENT_CONSENT_VERSION = 1
const CURRENT_POLICY_VERSION = '2026-07-23'

export type ConsentCategories = {
  necessary: true
  analytics: boolean
}

export type TestConsentReceipt = {
  consentVersion: number
  policyVersion: string
  timestamp: string
  region: string
  categories: ConsentCategories
  dnt: boolean
  source: 'banner' | 'settings-panel'
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Set a consent receipt directly in localStorage.
 *
 * Overrides merge with sensible defaults so tests only need to specify
 * the fields they care about.
 */
export async function setConsentReceipt(
  page: Page,
  overrides?: Partial<TestConsentReceipt>,
): Promise<void> {
  const receipt: TestConsentReceipt = {
    consentVersion: CURRENT_CONSENT_VERSION,
    policyVersion: CURRENT_POLICY_VERSION,
    timestamp: new Date().toISOString(),
    region: 'EU',
    categories: { necessary: true, analytics: false },
    dnt: false,
    source: 'banner',
    ...overrides,
  }
  await page.evaluate(
    ({ key, value }: { key: string; value: TestConsentReceipt }) =>
      localStorage.setItem(key, JSON.stringify(value)),
    {
      key: CONSENT_STORAGE_KEY,
      value: receipt,
    },
  )
}

/**
 * Clear consent state from localStorage.
 */
export async function clearConsent(page: Page): Promise<void> {
  try {
    await page.evaluate((key: string) => localStorage.removeItem(key), CONSENT_STORAGE_KEY)
  } catch {
    // Ignore gracefully if the page is still on about:blank or cross-origin
  }
}

/**
 * Mock DNT or GPC privacy signals via addInitScript.
 *
 * Call this BEFORE page.goto() so the signal is active during the SPA's
 * inline consent check.
 */
export async function mockPrivacySignals(
  page: Page,
  signals: { dnt?: boolean; gpc?: boolean } = {},
): Promise<void> {
  await page.addInitScript((opts: { dnt?: boolean; gpc?: boolean }) => {
    if (opts.dnt) {
      Object.defineProperty(navigator, 'doNotTrack', {
        value: '1',
        writable: true,
        configurable: true,
      })
    }
    if (opts.gpc) {
      Object.defineProperty(navigator, 'globalPrivacyControl', {
        value: true,
        writable: true,
        configurable: true,
      })
    }
  }, signals)
}

/**
 * Intercept POST /api/governance/consent and return a successful response.
 *
 * Prevents unhandled request errors when the consent store syncs to
 * backend for authenticated users.
 */
export async function mockConsentSync(page: Page): Promise<void> {
  await page.route('**/api/governance/consent', async (route: Route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true }),
      })
    } else {
      await route.fallback()
    }
  })
}

/** Assert that the consent experience has no blocking overlay. */
export async function expectNoOverlay(page: Page): Promise<void> {
  await page
    .locator('[data-slot="dialog-overlay"]')
    .count()
    .then((count) => {
      if (count !== 0) {
        throw new Error(`Expected no dialog overlay, found ${count}`)
      }
    })
  await page
    .locator('[data-testid="consent-overlay"], [data-testid="consent-backdrop"]')
    .count()
    .then((count) => {
      if (count !== 0) {
        throw new Error(`Expected no consent backdrop, found ${count}`)
      }
    })
}
