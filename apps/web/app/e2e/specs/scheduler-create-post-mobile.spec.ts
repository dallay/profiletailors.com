import { test, expect } from '../fixtures/scheduler-base-test'
import { SchedulerPage } from '../pages/scheduler-page'
import { ComposeModalPage } from '../pages/compose-modal-page'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'
import { ensureChannelsLoaded } from '../fixtures/scheduler-mocks'

test.describe('Scheduler — Create Post Responsive Mobile & Layout', { tag: '@responsive' }, () => {
  test.beforeEach(async ({ page }) => {
    await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()
    await ensureChannelsLoaded(page)
  })

  const viewports = [
    { width: 320, height: 568, name: '320x568 small phone' },
    { width: 390, height: 844, name: '390x844 modern iPhone' },
    { width: 430, height: 932, name: '430x932 large phone' },
    { width: 1280, height: 800, name: '1280x800 desktop' },
  ]

  for (const vp of viewports) {
    test(`Create Post modal layout & usability on ${vp.name}`, async ({ page }) => {
      await page.setViewportSize({ width: vp.width, height: vp.height })

      const scheduler = new SchedulerPage(page)
      const composeModal = new ComposeModalPage(page)
      const dialog = page.getByRole('dialog', { name: 'Create Post', exact: true })

      await test.step('Open the composer', async () => {
        await scheduler.clickNewPost()
        await composeModal.expectVisible()

        const channelButton = dialog.getByRole('button', { name: /Dev User/ })
        await expect(channelButton).toBeVisible()
        await channelButton.click()
      })

      await test.step('Edit content', async () => {
        await expect(composeModal.textarea).toBeVisible()
        await expect(composeModal.textarea).toBeEnabled()
        const testContent = `Mobile test content on ${vp.name} - ${Date.now()}`
        await composeModal.fillText(testContent)
        await expect(composeModal.textarea).toHaveValue(testContent)
      })

      await test.step('Check the LinkedIn preview', async () => {
        const linkedInPreview = page.getByRole('region', { name: /linkedin preview/i })
        await linkedInPreview.scrollIntoViewIfNeeded()
        await expect(linkedInPreview).toBeVisible()
      })

      await test.step('Select a schedule mode and date', async () => {
        await composeModal.switchToPickDate()
        await composeModal.expectPickDateActive()

        await expect(composeModal.datePickerButton).toBeVisible()
        await expect(composeModal.timeInput).toBeVisible()
        await composeModal.timeInput.fill('23:59')
        await expect(composeModal.timeInput).toHaveValue('23:59')

        const tomorrow = new Date()
        tomorrow.setDate(tomorrow.getDate() + 1)
        await composeModal.openDatePicker()
        await composeModal.pickDate(tomorrow)

        await expect(composeModal.schedulePostButton).toBeVisible()
        await expect(composeModal.schedulePostButton).toBeEnabled()
        await expect(composeModal.priorityQueueCheckbox).toBeVisible()
      })

      await test.step('Check horizontal overflow', async () => {
        const overflow = await page.evaluate(() => {
          return document.documentElement.scrollWidth > document.documentElement.clientWidth
        })
        expect(overflow).toBe(false)

        if (vp.width < 1024) {
          await expect
            .poll(() => dialog.evaluate((element) => element.scrollWidth > element.clientWidth))
            .toBe(false)
        }
      })

      await test.step('Close the composer', async () => {
        await composeModal.cancelButton.scrollIntoViewIfNeeded()
        await expect(composeModal.cancelButton).toBeVisible()
        await expect(composeModal.cancelButton).toBeInViewport()
        await composeModal.cancelButton.click()
        await composeModal.expectHidden()
      })
    })
  }
})
