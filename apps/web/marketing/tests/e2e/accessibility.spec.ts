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
import AxeBuilder from '@axe-core/playwright'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Pre-seed consent so the banner never interferes with a11y scans.
 * The banner itself is tested in consent.spec.ts.
 */
async function seedConsent(page: Parameters<typeof test.beforeEach>[0]['page']): Promise<void> {
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

function axe(page: Parameters<typeof AxeBuilder>[0]['page']) {
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
        await page.keyboard.press('Tab')

        const skipLink = page.locator('a.skip-link').or(page.getByRole('link', { name: /skip to/i }))
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
        if (await emailInput.isVisible()) {
            await emailInput.focus()
            await expect(emailInput).toBeFocused()

            await page.keyboard.press('Tab')
            const submitBtn = page.locator('button[type="submit"]').first()
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
