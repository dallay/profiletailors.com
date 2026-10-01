import { test, expect } from '../fixtures/scheduler-base-test'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'

// Reuse the backend-free workspace, channel, consent and HAR fixtures.
test.describe('App section states @frontend', () => {
  test.beforeEach(async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-06-15T10:00:00Z'))
    await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })
    await page.route('**/api/analytics/overview?*', (route) =>
      route.fulfill({
        json: {
          period: { startDate: '2026-05-17', endDate: '2026-06-15' },
          totalImpressions: 0,
          totalEngagements: 0,
          engagementRate: 0,
          totalClicks: 0,
          newFollowers: 0,
          clickThroughRate: 0,
          dailyMetrics: [],
        },
      }),
    )
    await page.route('**/api/analytics/posts?*', (route) =>
      route.fulfill({ json: { posts: [], total: 0, page: 0, size: 20 } }),
    )
    await page.route('**/api/analytics/best-times', (route) =>
      route.fulfill({ json: { slots: [] } }),
    )
  })

  test('dashboard identifies preview data while connected channels remain available', async ({
    page,
  }) => {
    await page.goto('/')
    await expect(page.getByText('Preview data', { exact: true })).toBeVisible()
    await expect(
      page.getByText(
        'Dashboard metrics and recommendations are illustrative while workspace analytics are being connected. Your connected accounts remain available in the sidebar.',
      ),
    ).toBeVisible()
    await expect(page.getByRole('button', { name: /LinkedIn/ }).first()).toBeVisible()
  })

  test('lazy navigation announces loading and clears the status after the section resolves', async ({
    page,
  }) => {
    const errors: string[] = []
    page.on('pageerror', (error) => errors.push(error.message))
    await page.goto('/')
    await expect(page.getByText('Preview data', { exact: true })).toBeVisible()
    let releaseImport!: () => void
    const importGate = new Promise<void>((resolve) => {
      releaseImport = resolve
    })
    await page.route('**/AnalyticsView.vue', async (route) => {
      await importGate
      await route.continue()
    })
    try {
      await page.getByRole('link', { name: /Analytics/ }).click()
      await expect(page.getByRole('status').filter({ hasText: 'Loading section' })).toBeVisible()
    } finally {
      releaseImport()
    }
    await expect(
      page.getByRole('heading', { name: 'Analytics', exact: true, level: 2 }),
    ).toBeVisible()
    await expect(page.getByRole('status').filter({ hasText: 'Loading section' })).toBeHidden()
    await expect(page.getByText('Preview data', { exact: true })).toBeHidden()
    expect(errors).toEqual([])
    await page.getByRole('link', { name: /Dashboard/ }).click()
    await expect(page.getByText('Preview data', { exact: true })).toBeVisible()
    await expect(page.getByRole('status').filter({ hasText: 'Loading section' })).toBeHidden()
  })

  test('analytics shows zero-data guidance and updates the reporting period', async ({ page }) => {
    await page.goto('/analytics')
    await expect(
      page.getByText('No analytics collected for this period', { exact: true }),
    ).toBeVisible()
    await expect(
      page.getByText(
        'Connect a channel and publish content to begin collecting analytics. New results may take time to appear after publishing.',
      ),
    ).toBeVisible()
    await expect(
      page.getByText('Reporting period: May 17, 2026 – Jun 15, 2026', { exact: true }),
    ).toBeVisible()
    await page.getByRole('combobox', { name: 'Date range' }).click()
    await page.getByRole('option', { name: 'Last 7 days' }).click()
    await expect(
      page.getByText('Reporting period: Jun 9, 2026 – Jun 15, 2026', { exact: true }),
    ).toBeVisible()
    await expect(
      page.getByText('No analytics collected for this period', { exact: true }),
    ).toBeVisible()
  })

  for (const status of [403, 500]) {
    test(`governance shows a ${status} failure and recovers through Try again`, async ({
      page,
    }) => {
      let failing = true
      await page.route('**/api/governance/takedown/reports', (route) =>
        route.fulfill(
          failing
            ? {
                status,
                json: {
                  title: 'Reports unavailable',
                  detail: 'Cannot load workspace reports',
                  status,
                },
              }
            : {
                json: [
                  {
                    reportId: 'report-1',
                    workspaceId: 'workspace-001',
                    assetId: 'asset-1',
                    reporterEmail: 'reporter@example.com',
                    reason: 'Copyright infringement',
                    status: 'REPORTED',
                    createdAt: '2026-06-15T10:00:00Z',
                  },
                ],
              },
        ),
      )
      await page.goto('/governance/takedown')
      const alert = page.getByRole('alert')
      await expect(alert).toBeVisible()
      await expect(alert).toContainText(
        'Check your access to this workspace, then try loading the reports again.',
      )
      await expect(page.getByText('No reports found.', { exact: true })).toBeHidden()
      failing = false
      await page.getByRole('button', { name: 'Try again', exact: true }).click()
      await expect(page.getByText('Copyright infringement', { exact: true })).toBeVisible()
      await expect(alert).toBeEmpty()
      await expect(page.getByRole('button', { name: 'Try again' })).toBeHidden()
    })
  }
})
