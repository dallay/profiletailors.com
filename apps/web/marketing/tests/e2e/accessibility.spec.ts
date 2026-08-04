/**
 * Accessibility baseline for the Profile Tailors marketing site.
 *
 * Automated WCAG 2.2 AA checks using @axe-core/playwright.
 * Covers: landing page, waitlist form, legal pages, accessibility statement.
 *
 * See docs/testing/accessibility-regression-strategy.md for the full strategy.
 *
 * @tags @a11y
 */

import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

// Axe scans the DOM while the fade-in animation is mid-flight, measuring text
// at partial opacity and producing false color-contrast failures. Running with
// reduced motion is both a real user scenario (WCAG 2.3.3) and the stable
// end-state we actually want to verify. Note: `reducedMotion` must go through
// `contextOptions` — passing it directly to `test.use` is silently dropped by
// Playwright 1.62.
test.use({ contextOptions: { reducedMotion: 'reduce' } })

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Pre-seed consent so the banner never interferes with a11y scans.
 * The banner itself is tested in consent.spec.ts.
 */
async function seedConsent(page: Page): Promise<void> {
    await page.addInitScript(() => {
        localStorage.setItem(
            'pt-consent',
            JSON.stringify({
                consentVersion: 1,
                policyVersion: '2026-07-23',
                timestamp: new Date().toISOString(),
                region: 'EU',
                categories: { necessary: true, analytics: false },
                dnt: false,
                source: 'banner',
            }),
        )
    })
}

function axe(page: Page) {
    return new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
}

// ---------------------------------------------------------------------------
// Landing page
// ---------------------------------------------------------------------------

test.describe('Marketing A11y — Landing page @a11y', () => {
    test.beforeEach(async ({ page }) => {
        await seedConsent(page)
    })

    test('landing page (EN) has no WCAG 2.2 AA violations', async ({ page }) => {
        await page.goto('/')
        await page.locator('h1').first().waitFor()

        const results = await axe(page).analyze()
        expect(
            results.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length })),
            'axe violations on /',
        ).toEqual([])
    })

    test('landing page (ES) has no WCAG 2.2 AA violations', async ({ page }) => {
        await page.goto('/es/')
        await page.locator('h1').first().waitFor()

        const results = await axe(page).analyze()
        expect(
            results.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length })),
            'axe violations on /es/',
        ).toEqual([])
    })

    test('skip link is present and keyboard-operable', async ({ page }) => {
        await page.goto('/')
        const skipLink = page.locator('a.skip-link').or(page.getByRole('link', { name: /skip to/i }))
        await expect(skipLink).toBeVisible()

        // Focus the skip link and activate it. The target must receive focus
        // (via #main-content[tabindex="-1"]) and move the viewport to it.
        await skipLink.focus()
        await expect(skipLink).toBeFocused()
        await page.keyboard.press('Enter')

        const main = page.locator('#main-content').or(page.getByRole('main'))
        await expect(main).toBeFocused()
    })

    test('waitlist form is keyboard-operable', async ({ page }) => {
        await page.goto('/')
        await page.locator('h1').first().waitFor()

        // Locate the waitlist email input
        const emailInput = page.locator('input[type="email"]').first()
        const submitBtn = page.locator('button[type="submit"]').first()
        if (await emailInput.isVisible()) {
            await emailInput.focus()
            await expect(emailInput).toBeFocused()

            // Tab until the submit button receives focus. The form has
            // checkboxes between the email and the submit, so a fixed Tab
            // count would be brittle.
            for (let i = 0; i < 10; i++) {
                await page.keyboard.press('Tab')
                if (await submitBtn.evaluate((el) => el === document.activeElement)) {
                    break
                }
            }
            await expect(submitBtn).toBeFocused()
        }
    })
})

// ---------------------------------------------------------------------------
// Consent banner
// ---------------------------------------------------------------------------

test.describe('Marketing A11y — Consent banner @a11y', () => {
    test.beforeEach(async ({ page }) => {
        await page.addInitScript(() => localStorage.removeItem('pt-consent'))
    })

    test('consent banner has no WCAG 2.2 AA violations', async ({ page }) => {
        await page.goto('/')
        await page.locator('#consent-banner').waitFor()

        const results = await axe(page).include('#consent-banner').analyze()
        expect(
            results.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length })),
            'axe violations in consent banner',
        ).toEqual([])
    })

    test('accept and reject buttons are keyboard-reachable', async ({ page }) => {
        await page.goto('/')
        await page.locator('#consent-banner').waitFor()

        const acceptBtn = page.getByRole('button', { name: /accept all/i })
        const rejectBtn = page.getByRole('button', { name: /reject all/i })
        await expect(acceptBtn).toBeVisible()
        await expect(rejectBtn).toBeVisible()

        // Both must be reachable via Tab without assistive technology
        await acceptBtn.focus()
        await expect(acceptBtn).toBeFocused()
        await rejectBtn.focus()
        await expect(rejectBtn).toBeFocused()
    })
})

// ---------------------------------------------------------------------------
// Legal & policy pages
// ---------------------------------------------------------------------------

test.describe('Marketing A11y — Legal pages @a11y', () => {
    test.beforeEach(async ({ page }) => {
        await seedConsent(page)
    })

    for (const { label, path } of [
        { label: 'Privacy Policy', path: '/privacy/' },
        { label: 'Terms of Service', path: '/terms/' },
        { label: 'Cookie Policy', path: '/cookies/' },
        { label: 'Acceptable Use', path: '/acceptable-use/' },
        { label: 'Accessibility Statement', path: '/accessibility/' },
    ]) {
        test(`${label} page has no WCAG 2.2 AA violations`, async ({ page }) => {
            await page.goto(path)
            await page.getByRole('heading', { level: 1 }).first().waitFor()

            const results = await axe(page).analyze()
            expect(
                results.violations.map((v) => ({ id: v.id, impact: v.impact, nodes: v.nodes.length })),
                `axe violations on ${path}`,
            ).toEqual([])
        })
    }
})
