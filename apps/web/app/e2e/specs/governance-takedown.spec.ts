import { expect, test } from '@playwright/test'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'

test.describe('media copyright takedown', () => {
  test.beforeEach(async ({ page }) => {
    await mockAuthenticatedSession(page, {
      email: 'owner@example.com',
      emailStatus: 'VERIFIED',
      workspaceId: 'workspace-1',
    })
  })

  test('reviews and approves a reported takedown', async ({ page }) => {
    const report = {
      reportId: 'report-1',
      workspaceId: 'workspace-1',
      assetId: 'asset-1',
      reporterEmail: 'reporter@example.com',
      reason: 'Copyright infringement',
      status: 'REPORTED',
      createdAt: '2026-07-21T10:00:00Z',
    }

    await page.route('**/api/governance/takedown/reports', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([report]) })
        return
      }
      await route.fallback()
    })
    await page.route('**/api/governance/takedown/reports/report-1/approve', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ...report, status: 'APPROVED' }),
      })
    })

    await page.goto('/governance/takedown')

    await expect(page.getByText('Copyright infringement')).toBeVisible()
    await page.getByRole('button', { name: /approve/i }).click()
    await expect(page.locator('[data-slot="badge"]').getByText('APPROVED', { exact: true })).toBeVisible()
  })
})
