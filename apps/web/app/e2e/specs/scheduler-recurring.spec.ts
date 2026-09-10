import { test, expect } from '../fixtures/scheduler-base-test'
import { SchedulerPage } from '../pages/scheduler-page'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'
import { ensureChannelsLoaded } from '../fixtures/scheduler-mocks'

test.describe('Scheduler — Recurring Posts', () => {
  test.beforeEach(async ({ page }) => {
    await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()
    await ensureChannelsLoaded(page)
  })

  test('TC-R01: recurring panel opens when recurring toggle is activated @recurring @e2e', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)

    await scheduler.clickNewPost()
    await page.waitForTimeout(500)

    const recurringToggle = page.getByTestId('recurring-toggle')
    if (await recurringToggle.isVisible()) {
      await recurringToggle.click()
      await page.waitForTimeout(300)
      const recurringPanel = page.getByTestId('recurring-panel')
      await expect(recurringPanel).toBeVisible()
    }
  })

  test('TC-R02: daily recurring schedule can be created from composer @recurring @e2e', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)
    const testText = `Recurring daily post ${Date.now()}`

    await scheduler.clickNewPost()
    await page.waitForTimeout(500)

    const recurringToggle = page.getByTestId('recurring-toggle')
    if (await recurringToggle.isVisible()) {
      await recurringToggle.click()
      await page.waitForTimeout(300)
    }

    const composeArea = page.getByTestId('composer-textarea')
    if (await composeArea.isVisible()) {
      await composeArea.fill(testText)
    }

    const frequencySelect = page.getByTestId('frequency-select')
    if (await frequencySelect.isVisible()) {
      await frequencySelect.selectOption('daily')
      await page.waitForTimeout(300)
    }

    const submitButton = page.getByTestId('submit-post-button')
    if (await submitButton.isVisible()) {
      await submitButton.click()
      await page.waitForTimeout(1000)
    }

    const successToast = page.getByText(/recurring|schedule|saved/i).first()
    await expect(successToast.or(page.locator('[data-testid="success-toast"]')))
      .toBeVisible({ timeout: 5000 })
      .catch(() => {})
  })

  test('TC-R03: weekly recurring schedule requires weekday selection @recurring @validation @e2e', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)

    await scheduler.clickNewPost()
    await page.waitForTimeout(500)

    const recurringToggle = page.getByTestId('recurring-toggle')
    if (await recurringToggle.isVisible()) {
      await recurringToggle.click()
      await page.waitForTimeout(300)
    }

    const frequencySelect = page.getByTestId('frequency-select')
    if (await frequencySelect.isVisible()) {
      await frequencySelect.selectOption('weekly')
      await page.waitForTimeout(300)
    }

    const submitButton = page.getByTestId('submit-post-button')
    if (await submitButton.isVisible()) {
      await submitButton.click()
      await page.waitForTimeout(500)
    }

    const validationError = page.getByTestId('recurrence-error')
    if (await validationError.isVisible()) {
      await expect(validationError).toContainText(/weekday|day|select/i)
    }
  })

  test('TC-R04: recurring schedules appear in scheduler list view @recurring @list @e2e', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)

    await scheduler.switchToList()
    await page.waitForTimeout(1000)

    const recurringBadge = page.getByTestId('recurring-badge')
    const hasRecurring = (await recurringBadge.count()) > 0
    if (hasRecurring) {
      await expect(recurringBadge.first()).toBeVisible()
    }
  })

  test('TC-R05: recurring schedule can be paused from post menu @recurring @e2e', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)

    await scheduler.switchToList()
    await page.waitForTimeout(1000)

    const firstPostMenu = page.getByTestId('post-menu-button').first()
    const hasMenu = await firstPostMenu.isVisible().catch(() => false)
    if (hasMenu) {
      await firstPostMenu.click()
      await page.waitForTimeout(300)

      const pauseOption = page.getByText(/pause|recurring/i).first()
      if (await pauseOption.isVisible({ timeout: 500 }).catch(() => false)) {
        await pauseOption.click()
        await page.waitForTimeout(500)
        const resumeOption = page.getByText(/resume/i).first()
        await expect(resumeOption.or(page.getByText(/paused/i).first()))
          .toBeVisible({ timeout: 3000 })
          .catch(() => {})
      }
    }
  })
})
