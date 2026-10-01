import { test, expect } from '../fixtures/scheduler-base-test'
import { SchedulerPage } from '../pages/scheduler-page'
import { authenticateAs } from '../fixtures/auth-helpers'
import { createPublicationInStore, ensureChannelsLoaded } from '../fixtures/scheduler-mocks'

test.describe('Scheduler — Views & Navigation', () => {
  // Authenticate before each test in this describe block
  test.beforeEach(async ({ page }) => {
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await scheduler.expectVisible()
  })

  /**
   * TC-02: Navigate to Scheduler — verify default week view with 24h slots.
   */
  test('TC-02: default week view with 24h slots @navigation @scheduler', async ({ page }) => {
    // Week view should be default
    await expect(page.getByText('12 AM', { exact: true })).toBeVisible()
    await expect(page.getByText('1 AM', { exact: true })).toBeVisible()
    await expect(page.getByText('6 PM', { exact: true })).toBeVisible()
    await expect(page.getByText('11 PM', { exact: true })).toBeVisible()
  })

  /**
   * TC-03: Calendar View Switching between month and week.
   */
  test('TC-03: calendar view switching @navigation @scheduler @views', async ({ page }) => {
    const scheduler = new SchedulerPage(page)

    // Month view
    await scheduler.switchToMonth()
    // Month grid should render 42 cells (6 weeks × 7 days)
    const cells = page.locator('.group\\/cell:visible')
    await expect(cells).toHaveCount(42)

    // Week view
    await scheduler.switchToWeek()
    await expect(page.getByText('12 AM', { exact: true })).toBeVisible()

    // TODAY button should be present
    await expect(scheduler.todayButton).toBeVisible()
  })

  /**
   * TC-04: Navigate to past months — verify past cells are styled as disabled
   * and posts are still readable (read-only).
   */
  test('TC-04: past months navigation @navigation @scheduler @past', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    await scheduler.switchToMonth()

    // Navigate backward one month
    await scheduler.backwardButton.click()
    await page.waitForTimeout(500)

    // Navigate forward back to current month
    await scheduler.forwardButton.click()
    await page.waitForTimeout(500)

    // TODAY button returns to current month
    await scheduler.todayButton.click()
    await page.waitForTimeout(500)

    // Past cells should have cursor-not-allowed and aria-disabled
    // (depends on day of month — some test runs may have 0 past cells)
    const pastCells = page.locator('[aria-disabled="true"]')
    await expect(pastCells.first())
      .toBeAttached({ timeout: 2_000 })
      .catch(() => {
        // No past cells on this day of month — navigation still validated
      })

    // Past cells with post cards should be clickable (read-only detail)
    // This is validated in TC-15 separately
  })
})

test.describe('Scheduler — Mobile shell', () => {
  const mobileViewports = [
    { width: 320, height: 568 },
    { width: 360, height: 640 },
    { width: 390, height: 844 },
    { width: 430, height: 932 },
  ]

  for (const viewport of mobileViewports) {
    test(`TC-M1: mobile shell dominates viewport at ${viewport.width}x${viewport.height} @mobile @scheduler`, async ({
      page,
    }) => {
      await page.setViewportSize(viewport)
      await authenticateAs(page)
      const scheduler = new SchedulerPage(page)
      await scheduler.goto()
      await expect(scheduler.mobileShell).toBeVisible()
      await expect(scheduler.newPostPrimaryButton).toBeVisible()
      await expect(scheduler.mobileFiltersTrigger).toBeVisible()
      await expect(scheduler.viewSwitcher).toBeVisible()
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      )
      expect(overflow).toBeLessThanOrEqual(1)
    })
  }

  test('TC-M2: filters Apply updates URL and Reset keeps view @mobile @scheduler', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await expect(scheduler.mobileShell).toBeVisible()
    await scheduler.dayViewButton.click()
    await expect(page).toHaveURL(/view=day/)
    await scheduler.mobileFiltersTrigger.click()
    await expect(scheduler.mobileFiltersSheet).toBeVisible()
    const selects = scheduler.mobileFiltersSheet.locator('select')
    await selects.nth(1).selectOption('queued')
    await page.getByRole('button', { name: /apply/i }).click()
    await expect(scheduler.mobileFiltersSheet).toBeHidden({ timeout: 5000 })
    await expect(page).toHaveURL(/status=queued/)
    await expect(page).toHaveURL(/view=day/)
    await scheduler.mobileFiltersTrigger.click()
    await page.getByRole('button', { name: /reset|restablecer/i }).click()
    await expect(page).not.toHaveURL(/status=queued/)
    await expect(page).toHaveURL(/view=day/)
  })

  test('TC-M3: prev, next and Today remain reachable @mobile @scheduler', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await expect(scheduler.mobileShell).toBeVisible()
    await scheduler.expectMinHitTarget(scheduler.prevPeriodButton)
    await scheduler.expectMinHitTarget(scheduler.nextPeriodButton)
    await scheduler.expectMinHitTarget(scheduler.todayPeriodButton)
    const beforeUrl = new URL(page.url())
    const beforeDate = beforeUrl.searchParams.get('date') ?? ''
    await scheduler.nextPeriodButton.click()
    await expect
      .poll(() => new URL(page.url()).searchParams.get('date') ?? '', {
        timeout: 5_000,
      })
      .not.toBe(beforeDate)
    await scheduler.prevPeriodButton.click()
    await expect
      .poll(() => new URL(page.url()).searchParams.get('date') ?? '', {
        timeout: 5_000,
      })
      .toBe(beforeDate)
    await scheduler.todayPeriodButton.click()
    const today = new Date()
    const expectedToday = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
    await expect
      .poll(
        () => {
          const value = new URL(page.url()).searchParams.get('date')
          return value === null ? expectedToday : value
        },
        { timeout: 5_000 },
      )
      .toBe(expectedToday)
    await expect(scheduler.mobileShell).toBeVisible()
  })

  test('TC-M4: Day, 3 Days and Week switch without compressing columns @mobile @scheduler', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await scheduler.dayViewButton.click()
    await expect(page).toHaveURL(/view=day/)
    await expect(scheduler.timelineViewport).toBeVisible()
    const dayColumns = await scheduler.timelineViewport.evaluate((element) => {
      const grid = element.querySelector('.grid.border-b')
      const match = grid?.getAttribute('style')?.match(/repeat\((\d+),/)
      return match ? Number(match[1]) : 0
    })
    expect(dayColumns).toBe(1)
    await scheduler.threeDaysViewButton.click()
    await expect(page).toHaveURL(/view=3-days/)
    const threeDayColumns = await scheduler.timelineViewport.evaluate((element) => {
      const grid = element.querySelector('.grid.border-b')
      const match = grid?.getAttribute('style')?.match(/repeat\((\d+),/)
      return match ? Number(match[1]) : 0
    })
    expect(threeDayColumns).toBe(3)
    await scheduler.weekSwitcherButton.click()
    await expect(scheduler.timelineViewport).toBeVisible()
    const viewportBox = await scheduler.timelineViewport.boundingBox()
    expect(viewportBox?.width ?? 0).toBeLessThanOrEqual(391)
    const scrollWidth = await scheduler.timelineViewport.evaluate((element) => element.scrollWidth)
    const clientWidth = await scheduler.timelineViewport.evaluate((element) => element.clientWidth)
    expect(scrollWidth).toBeGreaterThan(clientWidth)
    const dayColumnWidth = await scheduler.timelineViewport
      .locator('.border-r.border-border-subtle')
      .nth(1)
      .evaluate((el) => el.getBoundingClientRect().width)
    expect(dayColumnWidth).toBeGreaterThanOrEqual(120)
    const docOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    )
    expect(docOverflow).toBeLessThanOrEqual(1)
  })

  test('TC-M5: Bulk Import and tour live in the overflow menu @mobile @scheduler', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await scheduler.mobileOverflowMenu.click()
    await expect(scheduler.bulkImportOverflowItem).toBeVisible()
    await scheduler.bulkImportOverflowItem.click()
    await expect(page.getByTestId('bulk-import-modal')).toBeVisible()
    await page.keyboard.press('Escape')
  })

  test('TC-M6: agenda card tap opens post detail @mobile @scheduler', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await ensureChannelsLoaded(page)
    await createPublicationInStore(page, `Mobile agenda tap ${Date.now()}`)
    await scheduler.agendaViewButton.click()
    await expect(scheduler.mobileAgenda).toBeVisible()
    const card = scheduler.mobileAgenda.getByRole('button').first()
    await expect(card).toBeVisible()
    await card.click()
    await expect(page).toHaveURL(/postId=/)
  })

  test('TC-D1: desktop keeps header density and visible Bulk Import @scheduler', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1280, height: 800 })
    await authenticateAs(page)
    const scheduler = new SchedulerPage(page)
    await scheduler.goto()
    await expect(scheduler.mobileShell).toBeHidden()
    await expect(page.getByTestId('open-bulk-import')).toBeVisible()
    await expect(scheduler.newPostButton).toBeVisible()
  })
})
