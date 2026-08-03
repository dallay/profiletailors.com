/**
 * Accessibility baseline — automated WCAG 2.2 AA checks for critical user journeys.
 *
 * These tests run axe-core via @axe-core/playwright and are intentionally
 * separated from functional E2E tests so failures appear as discrete findings.
 *
 * ## What this covers (automated layer)
 * - Login and registration forms
 * - Scheduler / calendar view
 * - Compose modal
 * - Media library
 * - Account settings
 * - Dashboard shell
 *
 * ## What this does NOT replace
 * Automated tools catch ~30-40 % of WCAG issues.  Manual review, screen-reader
 * walkthroughs, and keyboard-only testing are required alongside this suite.
 * See docs/testing/accessibility-regression-strategy.md for the full strategy.
 *
 * @tags @a11y @frontend @integration
 */

import { test, expect } from '../fixtures/a11y-fixture'
import { test as schedulerTest } from '../fixtures/scheduler-base-test'
import AxeBuilder from '@axe-core/playwright'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'
import { ensureChannelsLoaded } from '../fixtures/scheduler-mocks'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Produce a per-test axe builder scoped to WCAG 2.2 AA, excluding rules that
 * are tracked separately or known false-positives in our UI library.
 */
function axe(page: Parameters<typeof AxeBuilder>[0]['page']) {
  return new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .disableRules([
      // reka-ui animates out dialogs — focus briefly lands on aria-hidden elements.
      // Tracked in: https://github.com/radix-ui/primitives/issues/1820
      'aria-hidden-focus',
    ])
}

// ---------------------------------------------------------------------------
// Unauthenticated pages
// ---------------------------------------------------------------------------

test.describe('A11y — Unauthenticated pages @a11y @frontend', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => localStorage.removeItem('pt-consent'))
  })

  test('login page has no WCAG 2.2 AA violations', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('heading').first().waitFor()

    const results = await axe(page).analyze()
    expect(
      results.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length })),
      'axe violations on /login',
    ).toEqual([])
  })

  test('registration page has no WCAG 2.2 AA violations', async ({ page }) => {
    await page.goto('/register')
    await page.getByRole('heading').first().waitFor()

    const results = await axe(page).analyze()
    expect(
      results.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length })),
      'axe violations on /register',
    ).toEqual([])
  })

  test('password reset page has no WCAG 2.2 AA violations', async ({ page }) => {
    await page.goto('/password-reset')
    await page.getByRole('heading').first().waitFor()

    const results = await axe(page).analyze()
    expect(
      results.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length })),
      'axe violations on /password-reset',
    ).toEqual([])
  })

  // ── Keyboard navigation — login form ──────────────────────────────────────

  test('login form is fully operable by keyboard only', async ({ page }) => {
    await page.goto('/login')

    // Tab to email
    await page.keyboard.press('Tab')
    const email = page.locator('input[type="email"]').first()
    await expect(email).toBeFocused()

    // Tab to password
    await page.keyboard.press('Tab')
    const password = page.locator('input[autocomplete$="password"]').first()
    await expect(password).toBeFocused()

    // Tab to submit
    await page.keyboard.press('Tab')
    const submit = page.locator('button[type="submit"]')
    await expect(submit).toBeFocused()

    // Submitting with Enter triggers validation (no credentials entered)
    await page.keyboard.press('Enter')
    // Expect an error message or required-field indication to appear
    await expect(
      page.getByRole('alert').or(page.locator('[aria-invalid="true"]')).first(),
    ).toBeVisible()
  })
})

// ---------------------------------------------------------------------------
// Authenticated pages — scheduler-base-test seeds consent + mocks
// ---------------------------------------------------------------------------

schedulerTest.describe('A11y — Authenticated pages @a11y @integration', () => {
  schedulerTest.beforeEach(async ({ page }) => {
    await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })
  })

  schedulerTest('scheduler / calendar view has no WCAG 2.2 AA violations', async ({ page }) => {
    await page.goto('/dashboard/scheduler')
    // Wait for the calendar grid to be present
    await page
      .getByRole('grid')
      .or(page.locator('[data-scheduler]'))
      .first()
      .waitFor({ timeout: 10_000 })
      .catch(() => {})
    await page.waitForLoadState('networkidle')

    const results = await axe(page).analyze()
    expect(
      results.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length })),
      'axe violations on /dashboard/scheduler',
    ).toEqual([])
  })

  schedulerTest('compose modal has no WCAG 2.2 AA violations when open', async ({ page }) => {
    await ensureChannelsLoaded(page)
    await page.goto('/dashboard/scheduler')
    await page.waitForLoadState('networkidle')

    // Open the compose modal
    const newPostBtn = page
      .getByRole('button', { name: /new post|nueva publicación/i })
      .or(page.locator('[data-new-post]'))
      .first()
    await newPostBtn.click()

    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()

    const results = await axe(page).include('[role="dialog"]').analyze()
    expect(
      results.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length })),
      'axe violations in compose modal',
    ).toEqual([])
  })

  schedulerTest('media library has no WCAG 2.2 AA violations', async ({ page }) => {
    await page.goto('/dashboard/media')
    await page.waitForLoadState('networkidle')

    const results = await axe(page).analyze()
    expect(
      results.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length })),
      'axe violations on /dashboard/media',
    ).toEqual([])
  })

  schedulerTest('account settings page has no WCAG 2.2 AA violations', async ({ page }) => {
    await page.goto('/dashboard/settings')
    await page.waitForLoadState('networkidle')

    const results = await axe(page).analyze()
    expect(
      results.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length })),
      'axe violations on /dashboard/settings',
    ).toEqual([])
  })

  // ── Keyboard navigation — modal focus management ───────────────────────────

  schedulerTest('compose modal traps focus and returns it on close', async ({ page }) => {
    await ensureChannelsLoaded(page)
    await page.goto('/dashboard/scheduler')
    await page.waitForLoadState('networkidle')

    const newPostBtn = page
      .getByRole('button', { name: /new post|nueva publicación/i })
      .or(page.locator('[data-new-post]'))
      .first()
    await newPostBtn.click()

    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()

    // First focusable element inside the dialog should receive focus
    const firstFocusable = dialog
      .locator('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])')
      .first()
    await expect(firstFocusable).toBeFocused()

    // Escape should close the dialog
    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()

    // Focus should return to the trigger button
    await expect(newPostBtn).toBeFocused()
  })

  schedulerTest('calendar does not trap keyboard focus when navigating dates', async ({ page }) => {
    await page.goto('/dashboard/scheduler')
    await page.waitForLoadState('networkidle')

    // Tab through the page — focus should move forward and eventually leave
    // the calendar region without getting stuck.
    const maxTabs = 40
    let focusEscapedCalendar = false

    for (let i = 0; i < maxTabs; i++) {
      await page.keyboard.press('Tab')
      const focused = page.locator(':focus')
      const isInCalendar = await focused
        .evaluate(
          (el) =>
            !!el.closest(
              '[data-scheduler],[role="grid"],[aria-label*="calendar" i],[aria-label*="calendario" i]',
            ),
        )
        .catch(() => false)

      if (!isInCalendar) {
        focusEscapedCalendar = true
        break
      }
    }

    expect(focusEscapedCalendar, 'keyboard focus escaped the calendar region').toBe(true)
  })

  // ── Skip-to-content ────────────────────────────────────────────────────────

  schedulerTest('skip link is present and leads to main content', async ({ page }) => {
    await page.goto('/dashboard/scheduler')
    await page.waitForLoadState('networkidle')

    await page.keyboard.press('Tab')
    const skipLink = page.getByRole('link', { name: /skip to (main )?content/i })
    await expect(skipLink.or(page.locator('.skip-link'))).toBeFocused()

    await page.keyboard.press('Enter')
    const main = page.getByRole('main')
    await expect(main).toBeFocused()
  })
})
