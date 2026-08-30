import { test, expect } from '../fixtures/media-mocked-test'
import { SchedulerPage } from '../pages/scheduler-page'
import { ensureChannelsLoaded } from '../fixtures/scheduler-mocks'

test.describe('Bulk import @bulk @e2e', () => {
  test.beforeEach(async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()
    await ensureChannelsLoaded(page)
  })

  test('upload csv preview validate schedule poll', async ({ page }) => {
    await test.step('mock bulk endpoints', async () => {
      await page.route('**/api/v1/workspaces/**/bulk/validate', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/vnd.api.v1+json',
          body: JSON.stringify({
            rows: [
              {
                rowIndex: 0,
                status: 'VALID',
                errors: [],
                bodyText: 'Hello',
                scheduledFor: '2099-06-15T10:00:00Z',
              },
              {
                rowIndex: 1,
                status: 'INVALID',
                errors: [{ code: 'INVALID_DATE', message: 'bad date' }],
              },
            ],
          }),
        })
      })
      await page.route('**/api/v1/workspaces/**/bulk/schedule', async (route) => {
        await route.fulfill({
          status: 207,
          contentType: 'application/vnd.api.v1+json',
          body: JSON.stringify({
            jobId: 'job-1',
            totalRows: 2,
            scheduledCount: 1,
            failedCount: 1,
            rows: [],
          }),
        })
      })
      await page.route('**/api/v1/workspaces/**/bulk/jobs/**', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/vnd.api.v1+json',
          body: JSON.stringify({
            jobId: 'job-1',
            status: 'PARTIAL',
            totalRows: 2,
            scheduledCount: 1,
            failedCount: 1,
            rows: [],
          }),
        })
      })
      await page.route('**/api/v1/workspaces/**/bulk/templates', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/vnd.api.v1+json',
          body: JSON.stringify({
            templates: [
              {
                id: 'linkedin-calendar',
                name: 'LinkedIn Calendar',
                description: 'default',
                header: 'bodyText,scheduledFor,timezone,media_urls,hashtags',
              },
            ],
          }),
        })
      })
      await page.route('**/api/v1/workspaces/**/bulk/templates/**/csv', async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'text/csv',
          body: 'bodyText,scheduledFor,timezone,media_urls,hashtags\n',
        })
      })
    })

    await test.step('open bulk modal and upload CSV', async () => {
      await page.getByTestId('open-bulk-import').click()
      await expect(page.getByTestId('bulk-import-modal')).toBeVisible()
      const csvContent =
        'bodyText,scheduledFor,timezone,media_urls,hashtags\nHello,2099-06-15T10:00:00Z,UTC,,\n,not-a-date,UTC,,'
      await page.getByTestId('bulk-file-input').setInputFiles({
        name: 'bulk.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from(csvContent),
      })
      await expect(page.getByTestId('bulk-csv-textarea')).toHaveValue(/Hello/)
    })

    await test.step('validate and edit invalid row', async () => {
      await page.getByRole('button', { name: 'Validate' }).click()
      await expect(page.getByTestId('bulk-preview-table')).toBeVisible()
      await expect(page.getByTestId('bulk-row-1')).toBeVisible()
      await expect(page.getByTestId('bulk-error-1-INVALID_DATE')).toBeVisible()
      await page.getByTestId('bulk-row-body-1').fill('Fixed body')
      await page.getByTestId('bulk-row-scheduled-1').fill('2099-06-16T10:00:00Z')
      await expect(page.getByTestId('bulk-row-body-1')).toHaveValue('Fixed body')
    })

    await test.step('schedule and verify result', async () => {
      await page.getByRole('button', { name: 'Schedule' }).click()
      await expect(page.getByTestId('bulk-schedule-result')).toBeVisible()
      await expect(page.getByTestId('bulk-schedule-result')).toContainText('job-1')
    })
  })

  test('CSV header validation shows error via real parser', async ({ page }) => {
    await page.route('**/api/v1/workspaces/**/bulk/templates', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.api.v1+json',
        body: JSON.stringify({
          templates: [
            {
              id: 'linkedin-calendar',
              name: 'LinkedIn Calendar',
              description: 'default',
              header: 'bodyText,scheduledFor,timezone,media_urls,hashtags',
            },
          ],
        }),
      })
    })
    await page.getByTestId('open-bulk-import').click()
    await expect(page.getByTestId('bulk-import-modal')).toBeVisible()
    await page.getByTestId('bulk-csv-textarea').fill('bad,header\nvalue1,value2')
    await expect(page.getByTestId('bulk-header-error')).toBeVisible()
    await expect(page.getByTestId('bulk-header-error')).toContainText(
      'bodyText,scheduledFor,timezone,media_urls,hashtags',
    )
  })

  test('template picker downloads CSV into textarea', async ({ page }) => {
    await page.route('**/api/v1/workspaces/**/bulk/templates', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.api.v1+json',
        body: JSON.stringify({
          templates: [
            {
              id: 'linkedin-calendar',
              name: 'LinkedIn Calendar',
              description: 'default',
              header: 'bodyText,scheduledFor,timezone,media_urls,hashtags',
            },
          ],
        }),
      })
    })
    await page.route('**/api/v1/workspaces/**/bulk/templates/**/csv', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'text/csv',
        body: 'bodyText,scheduledFor,timezone,media_urls,hashtags\nTemplate row,2099-06-20T10:00:00Z,UTC,,',
      })
    })
    await page.getByTestId('open-bulk-import').click()
    await expect(page.getByTestId('bulk-import-modal')).toBeVisible()
    const downloadBtn = page.getByTestId('bulk-template-linkedin-calendar')
    await expect(downloadBtn).toBeVisible()
    await downloadBtn.click()
    await expect(page.getByTestId('bulk-csv-textarea')).toHaveValue(/Template row/)
  })
})
