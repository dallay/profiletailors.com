import { test as base } from './base-test'
import AxeBuilder from '@axe-core/playwright'

export type A11yFixtures = {
  makeAxeBuilder: () => AxeBuilder
}

/**
 * Extends the base test fixture with an axe builder pre-configured for WCAG 2.2 AA.
 *
 * Usage:
 *   import { test, expect } from '../fixtures/a11y-fixture'
 *
 *   test('page has no violations', async ({ page, makeAxeBuilder }) => {
 *     await page.goto('/some-route')
 *     const results = await makeAxeBuilder().analyze()
 *     expect(results.violations).toEqual([])
 *   })
 */
export const test = base.extend<A11yFixtures>({
  makeAxeBuilder: async ({ page }, use) => {
    await use(() =>
      new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
        // reka-ui / shadcn-vue components occasionally produce known aria-hidden
        // focus warnings during animation frames; those are tracked separately.
        .disableRules(['aria-hidden-focus']),
    )
  },
})

export { expect } from './base-test'
