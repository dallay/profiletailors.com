import { test, expect } from '../fixtures/scheduler-base-test'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'

const mainRoutes = [
  { path: '/', section: /dashboard/i },
  { path: '/scheduler/calendar/week', section: /scheduler/i },
  { path: '/analytics', section: /analytics/i },
  { path: '/media', section: /media/i },
  { path: '/ideas', section: /ideas/i },
  { path: '/governance/takedown', section: /governance/i },
  { path: '/settings', section: /settings/i },
]

test.describe('Mobile app UX route sweep', { tag: '@mobile @responsive' }, () => {
  for (const width of [320, 390]) {
    for (const route of mainRoutes) {
      test(`${route.path} fits a ${width}px mobile viewport`, async ({ page }) => {
        await page.setViewportSize({ width, height: width === 320 ? 568 : 844 })
        await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })
        await page.goto(route.path, { waitUntil: 'domcontentloaded' })

        await expect(page.locator('main h1, main h2').first()).toBeVisible()
        await expect(
          page.getByRole('banner').getByRole('heading', { name: route.section }),
        ).toBeVisible()

        const layout = await page.evaluate(() => ({
          viewportWidth: document.documentElement.clientWidth,
          pageWidth: document.documentElement.scrollWidth,
          mainWidth: document.querySelector('main')?.getBoundingClientRect().width ?? 0,
        }))

        expect(layout.pageWidth - layout.viewportWidth).toBeLessThanOrEqual(1)
        expect(layout.mainWidth).toBeLessThanOrEqual(width)
      })
    }
  }

  test('mobile navigation opens and reaches the media library', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })
    await page.goto('/scheduler/calendar/week', { waitUntil: 'domcontentloaded' })

    await page.getByRole('button', { name: /toggle sidebar/i }).click()
    const sidebar = page.locator('[data-slot="sidebar"][data-mobile="true"]')
    await expect(sidebar).toBeVisible()
    await sidebar.getByRole('link', { name: /media library/i }).click()
    await expect(page).toHaveURL(/\/media$/)
    await expect(page.getByRole('heading', { level: 1, name: /media/i })).toBeVisible()
  })

  test('dashboard composer opens channel connection when no channel is active', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })
    await page.goto('/', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('main h2').first()).toBeVisible()
    await page.evaluate(() => {
      const appRoot = document.querySelector('#app') as
        | (HTMLElement & {
            __vue_app__?: { config: { globalProperties: Record<string, unknown> } }
          })
        | null
      const pinia = appRoot?.__vue_app__?.config.globalProperties.$pinia as
        | { state: { value: { publishing?: { channels?: unknown[] } } } }
        | undefined
      if (pinia?.state.value.publishing) pinia.state.value.publishing.channels = []
    })

    await page
      .getByRole('button', { name: /new post/i })
      .first()
      .click()
    await expect(page.getByTestId('composer-no-channels')).toBeVisible()
    await page.getByTestId('composer-connect-channels').click()

    await expect(page.getByTestId('composer-no-channels')).toBeHidden()
    const sidebar = page.locator('[data-slot="sidebar"][data-mobile="true"]')
    await expect(sidebar).toBeVisible()
    await expect(sidebar.getByTestId('connect-provider-linkedin')).toBeVisible()
  })
})
