/**
 * E2E — Product tour (Driver.js)
 *
 * Covers the guided tour started from the header "Product tour" button:
 * opening the popover, advancing through the steps, and closing.
 *
 * Driver.js skips steps whose target element is not visible, so the exact
 * walk-through runs on a forced desktop viewport where the shell chrome
 * (sidebar, header, footer) is fully rendered. A separate smoke test
 * verifies the tour opens and closes from any viewport.
 *
 * Runs against mocked API responses (no backend required).
 * HAR replay handles auth; consent is pre-seeded so the consent banner
 * does not overlay the tour popover.
 *
 * @see app-tour.ts — tour steps and driver configuration
 * @see AppHeader.vue — start button (data-testid="start-tour-btn")
 */

import { test, expect } from '../fixtures/base-test'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'
import { setConsentReceipt, mockConsentSync } from '../fixtures/consent-helpers'

async function seedAuthAndConsent(page: import('@playwright/test').Page) {
  await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })
  await mockConsentSync(page)
}

async function seedConsentAndReload(page: import('@playwright/test').Page) {
  await setConsentReceipt(page, {
    categories: { necessary: true, analytics: false },
  })
  await page.reload()
  await expect(page.getByRole('heading', { name: /welcome/i })).toBeVisible()
}

test.describe('Product tour', () => {
  // Desktop viewport so every step target is visible and the walk-through is deterministic
  test.use({ viewport: { width: 1280, height: 720 } })

  test.beforeEach(async ({ page }) => {
    await seedAuthAndConsent(page)
  })

  test('walks through all steps from the header button @frontend', async ({ page }) => {
    await page.goto('/')
    await seedConsentAndReload(page)

    // Start the tour from the header button
    await page.getByTestId('start-tour-btn').click()

    const popover = page.locator('.driver-popover')
    await expect(popover).toBeVisible()
    await expect(popover.locator('.driver-popover-title')).toHaveText('Workspace selector')

    // Advance through the remaining steps
    await popover.locator('.driver-popover-next-btn').click()
    await expect(popover.locator('.driver-popover-title')).toHaveText('Primary navigation')

    await popover.locator('.driver-popover-next-btn').click()
    await expect(popover.locator('.driver-popover-title')).toHaveText('Channel filters')

    await popover.locator('.driver-popover-next-btn').click()
    await expect(popover.locator('.driver-popover-title')).toHaveText('Current section')

    await popover.locator('.driver-popover-next-btn').click()
    await expect(popover.locator('.driver-popover-title')).toHaveText('Main workspace')

    await popover.locator('.driver-popover-next-btn').click()
    await expect(popover.locator('.driver-popover-title')).toHaveText('Cookie settings')

    // Close the tour from the final step
    await popover.locator('.driver-popover-close-btn').click()
    await expect(popover).not.toBeVisible()
  })
})

test.describe('Product tour — any viewport', () => {
  test.beforeEach(async ({ page }) => {
    await seedAuthAndConsent(page)
  })

  test('opens and closes the tour @frontend', async ({ page }) => {
    await page.goto('/')
    await seedConsentAndReload(page)

    await page.getByTestId('start-tour-btn').click()

    const popover = page.locator('.driver-popover')
    await expect(popover).toBeVisible()
    await expect(popover.locator('.driver-popover-title')).not.toBeEmpty()

    await popover.locator('.driver-popover-close-btn').click()
    await expect(popover).not.toBeVisible()
  })
})
