import { test, expect } from '../fixtures/scheduler-base-test'
import { SchedulerPage } from '../pages/scheduler-page'
import { authenticateAs } from '../fixtures/auth-helpers'
import { createPublicationInStore, ensureChannelsLoaded } from '../fixtures/scheduler-mocks'
import { PostDetailModalPage } from '../pages/post-detail-modal-page'

test.describe('Scheduler Gherkin Alignment', () => {
  test.beforeEach(async ({ page }) => {
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await ensureChannelsLoaded(page)
    await scheduler.expectVisible()
  })

  test('Initial view shows All Channels and no channels param in URL', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'All Channels' })).toBeVisible()
    expect(page.url()).not.toContain('channels')
  })

  test('Filtering by LinkedIn channel adds channels param to URL', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    await scheduler.linkedInFilterButton.click()
    await page.waitForTimeout(300)
    expect(page.url()).toContain('channels')
  })

  test('Clicking All Channels removes channel filter', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    // Activate filter
    await scheduler.linkedInFilterButton.click()
    await page.waitForTimeout(300)
    expect(page.url()).toContain('channels')

    // Click All Channels
    await scheduler.allChannelsButton.click()
    await page.waitForTimeout(300)
    expect(page.url()).not.toContain('channels')
    await expect(page.getByRole('heading', { name: 'All Channels' })).toBeVisible()
  })

  test('Published posts are read-only in detail modal', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const detailModal = new PostDetailModalPage(page)

    const text = 'Published post'
    await createPublicationInStore(page, text, { status: 'PUBLISHED' })

    await scheduler.switchToList()
    await page
      .getByRole('button', { name: new RegExp(text) })
      .first()
      .click()
    await detailModal.expectVisible()

    await expect(page.getByText(/read only|solo lectura/i)).toBeVisible()
    await expect(page.getByRole('button', { name: /edit|editar/i })).toHaveCount(0)
    await detailModal.expectDeleteHidden()
  })
})
