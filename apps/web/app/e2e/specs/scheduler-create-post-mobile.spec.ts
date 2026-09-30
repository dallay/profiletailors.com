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

  test('mobile scheduler guides users to channel connection when none are active', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 })
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

    const connectChannelsButton = page.getByTestId('mobile-new-post')
    await expect(connectChannelsButton).toHaveText(/connect channels/i)
    await expect(connectChannelsButton).toBeEnabled()
    await connectChannelsButton.click()

    const mobileSidebar = page.locator('[data-slot="sidebar"][data-mobile="true"]')
    await expect(mobileSidebar).toBeVisible()
    await expect(mobileSidebar.getByTestId('connect-provider-linkedin')).toBeVisible()
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

      // Open modal
      await scheduler.clickNewPost()
      await composeModal.expectVisible()

      // Assert channel selector is visible
      const channelSelector = page.getByTestId('channel-selector')
      await expect(channelSelector).toBeVisible()

      // Assert textarea is visible, enabled, and accepts input
      await expect(composeModal.textarea).toBeVisible()
      await expect(composeModal.textarea).toBeEnabled()
      const testContent = `Mobile test content on ${vp.name} - ${Date.now()}`
      await composeModal.fillText(testContent)
      await expect(composeModal.textarea).toHaveValue(testContent)

      // Scroll into view & assert LinkedIn preview panel is visible
      const linkedInPreview = page.getByRole('region', { name: /linkedin preview/i })
      await linkedInPreview.scrollIntoViewIfNeeded()
      await expect(linkedInPreview).toBeVisible()

      // Switch to Pick Date mode
      await composeModal.pickDateTab.scrollIntoViewIfNeeded()
      await composeModal.switchToPickDate()
      await composeModal.expectPickDateActive()

      // Verify date trigger & time input are visible and interactive
      await composeModal.datePickerButton.scrollIntoViewIfNeeded()
      await expect(composeModal.datePickerButton).toBeVisible()
      await expect(composeModal.timeInput).toBeVisible()

      // Verify primary submit button is visible and reachable
      await composeModal.schedulePostButton.scrollIntoViewIfNeeded()
      await expect(composeModal.schedulePostButton).toBeVisible()
      await expect(composeModal.schedulePostButton).toBeEnabled()

      // Check priority queue and cancel buttons
      await expect(composeModal.priorityQueueCheckbox).toBeVisible()
      await expect(composeModal.cancelButton).toBeVisible()

      // Verify no horizontal document page overflow
      const overflow = await page.evaluate(() => {
        return document.documentElement.scrollWidth > document.documentElement.clientWidth
      })
      expect(overflow).toBe(false)

      // Close modal
      await composeModal.clickCancel()
      await composeModal.expectHidden()
    })
  }
})
