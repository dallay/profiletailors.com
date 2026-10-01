import { test, expect } from '../fixtures/scheduler-base-test'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'

test.describe('Scheduler controls and empty states @frontend', () => {
  test.beforeEach(async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-06-15T10:00:00Z'))
    await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })
  })

  test('format and period controls agree with the URL and rendered range', async ({ page }) => {
    await page.goto('/scheduler/calendar/week?date=2026-06-22&timezone=Europe%2FMadrid')
    const format = page.getByRole('group', { name: 'View format' })
    const period = page.getByRole('group', { name: 'Calendar period' })
    const slots = page.getByRole('button', { name: /^Slot for/ })
    for (const [name, view, count] of [
      ['Day', 'day', 24],
      ['3 Days', '3-days', 72],
      ['Week', 'week', 168],
    ] as const) {
      await period.getByRole('button', { name, exact: true }).click()
      await expect(period.getByRole('button', { name, exact: true })).toHaveAttribute(
        'aria-pressed',
        'true',
      )
      await expect(page).toHaveURL((url) => (url.searchParams.get('view') ?? 'week') === view)
      await expect(slots).toHaveCount(count)
    }
    await period.getByRole('button', { name: 'Month', exact: true }).click()
    await expect(page).toHaveURL(/\/scheduler\/calendar\/month/)
    await expect(period.getByRole('button', { name: 'Month' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    await expect(slots).toHaveCount(0)
    await format.getByRole('button', { name: 'List', exact: true }).click()
    await expect(page).toHaveURL(/\/scheduler\/list/)
    await expect(format.getByRole('button', { name: 'List' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    await expect(period).toBeHidden()
    await expect(page.getByText('No posts scheduled yet', { exact: true })).toBeVisible()
    await format.getByRole('button', { name: 'Calendar', exact: true }).click()
    await expect(page).toHaveURL(/\/scheduler\/calendar\//)
    await expect(page.getByTestId('calendar-mode')).toBeVisible()
  })

  test('empty schedule offers a working create action', async ({ page }) => {
    await page.goto('/scheduler/list?date=2026-06-22')
    const workspace = page.getByTestId('scheduler-workspace')
    await expect(workspace.getByText('No posts scheduled yet', { exact: true })).toBeVisible()
    await expect(
      workspace.getByText('Create your first post to start building this publishing schedule.'),
    ).toBeVisible()
    await expect(page.getByRole('button', { name: 'Clear filters' })).toBeHidden()
    await workspace.getByRole('button', { name: 'New Post', exact: true }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
  })

  test('clear filters restores the unfiltered empty state and preserves the range', async ({
    page,
  }) => {
    await page.goto(
      '/scheduler/list?date=2026-06-22&timezone=UTC&status=queued&q=missing&channels%5B%5D=acc-linkedin',
    )
    await expect(page.getByText('No posts match these filters', { exact: true })).toBeVisible()
    await expect(
      page.getByText('Clear the filters to see all scheduled and published posts.'),
    ).toBeVisible()
    await page.getByRole('button', { name: 'Clear filters', exact: true }).click()
    await expect(page.getByText('No posts scheduled yet', { exact: true })).toBeVisible()
    await expect(page).toHaveURL(
      (url) =>
        url.pathname === '/scheduler/list' &&
        url.searchParams.get('date') === '2026-06-22' &&
        url.searchParams.get('timezone') === 'UTC' &&
        !url.searchParams.has('status') &&
        !url.searchParams.has('q') &&
        !url.searchParams.has('channels[]'),
    )
    await expect(page.getByRole('button', { name: 'Clear filters' })).toBeHidden()
  })

  test('calendar has one tab stop and arrow keys move focus without entering past slots', async ({
    page,
  }) => {
    await page.goto('/scheduler/calendar/week?date=2026-06-15&timezone=Europe%2FMadrid')
    const grid = page.getByRole('region', { name: /Publishing calendar/ })
    const tabStop = grid.locator('button[tabindex="0"]')
    const tuesday = grid.getByRole('button', { name: 'Slot for Tuesday at 12 AM', exact: true })
    await expect(tabStop).toHaveCount(1)
    await expect(tuesday).toHaveAttribute('tabindex', '0')
    // Shift+Tab then Tab verifies entry through the browser's tab order.
    await tuesday.focus()
    await page.keyboard.press('Shift+Tab')
    await page.keyboard.press('Tab')
    await expect(tuesday).toBeFocused()
    await page.keyboard.press('ArrowLeft')
    await expect(tuesday).toBeFocused()
    await expect(
      grid.getByRole('button', { name: 'Slot for Monday at 12 AM', exact: true }),
    ).toBeDisabled()
    await page.keyboard.press('ArrowUp')
    await expect(tuesday).toBeFocused()
    await page.keyboard.press('ArrowDown')
    await expect(
      grid.getByRole('button', { name: 'Slot for Tuesday at 1 AM', exact: true }),
    ).toBeFocused()
    await page.keyboard.press('ArrowRight')
    await expect(
      grid.getByRole('button', { name: 'Slot for Wednesday at 1 AM', exact: true }),
    ).toBeFocused()
    await page.keyboard.press('ArrowUp')
    await page.keyboard.press('ArrowLeft')
    await expect(tuesday).toBeFocused()
    await expect(tabStop).toHaveCount(1)
    await page.keyboard.press('Enter')
    await expect(page.getByRole('dialog')).toBeVisible()
  })
})
