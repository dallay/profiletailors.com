import { test, expect } from '../fixtures/scheduler-base-test'
import { SchedulerPage } from '../pages/scheduler-page'
import { ComposeModalPage } from '../pages/compose-modal-page'
import { authenticateAs } from '../fixtures/auth-helpers'
import { ensureChannelsLoaded } from '../fixtures/scheduler-mocks'

test.describe('Composer Gherkin Alignment', () => {
  test.beforeEach(async ({ page }) => {
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await ensureChannelsLoaded(page)
    await scheduler.expectVisible()
  })

  test('LinkedIn preview shows truncated content and "...more"', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)

    await scheduler.clickNewPost()
    await composeModal.expectVisible()

    // Fill with very long text (> 240 chars to trigger truncation based on LinkedInPostPreview.vue)
    const longText = 'A'.repeat(300)
    await composeModal.fillText(longText)

    // Check preview
    const previewText = page.getByTestId('linkedin-preview-text')
    const moreLink = page.getByTestId('linkedin-preview-more')

    await expect(previewText).toBeVisible()
    await expect(moreLink).toBeVisible()
    await expect(moreLink).toHaveText('...more')
  })

  test('Cancel button closes modal without creating post', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    const composeModal = new ComposeModalPage(page)

    await scheduler.clickNewPost()
    await composeModal.expectVisible()
    await composeModal.fillText('Cancelled post')

    await composeModal.clickCancel()
    await composeModal.expectHidden()

    // Verify it's not in the list
    await scheduler.switchToList()
    await expect(page.getByText('Cancelled post')).toHaveCount(0)
  })
})
