/**
 * E2E tests for URL-addressable scheduler calendar.
 *
 * Validates that deep links, navigation, sidebar filters, and browser
 * history correctly drive the scheduler state via canonical URLs.
 *
 * @see openspec/changes/scheduler-url-addressable/spec.md
 */

import { test, expect } from '../fixtures/scheduler-base-test'
import { SchedulerPage } from '../pages/scheduler-page'
import { authenticateAs } from '../fixtures/auth-helpers'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

import { ensureChannelsLoaded } from '../fixtures/scheduler-mocks'

async function setup(page: import('@playwright/test').Page): Promise<SchedulerPage> {
  await authenticateAs(page)
  const scheduler = new SchedulerPage(page)
  await ensureChannelsLoaded(page)
  return scheduler
}

// ---------------------------------------------------------------------------
// Deep links — direct navigation to canonical URLs
// ---------------------------------------------------------------------------

test.describe('URL-addressable scheduler — deep links', () => {
  test('TC-DL-01: deep link to /scheduler/calendar/week renders week view @scheduler', async ({
    page,
  }) => {
    const scheduler = await setup(page)
    await page.goto('/scheduler/calendar/week')
    await scheduler.expectVisible()
    await page.waitForLoadState('networkidle')

    // Week view renders 24 hour-slot labels
    await expect(page.getByText('12 AM', { exact: true })).toBeVisible()
    await expect(page.getByText('11 PM', { exact: true })).toBeVisible()
  })

  test('TC-DL-02: deep link to /scheduler/calendar/month renders month grid @scheduler', async ({
    page,
  }) => {
    const scheduler = await setup(page)
    await page.goto('/scheduler/calendar/month')
    await scheduler.expectVisible()
    await page.waitForLoadState('networkidle')

    // Month view renders 42 day cells (6 weeks × 7 days)
    const cells = page.locator('.group\\/cell')
    await expect(cells).toHaveCount(42)
  })

  test('TC-DL-03: deep link to /scheduler/list renders list view @scheduler', async ({ page }) => {
    const scheduler = await setup(page)
    await page.goto('/scheduler/list')
    await scheduler.expectVisible()
    await page.waitForLoadState('networkidle')

    // List view should not render the calendar day cells
    const cells = page.locator('.group\\/cell')
    await expect(cells).toHaveCount(0)
  })

  test('TC-DL-04: /scheduler redirects to /scheduler/calendar/week @scheduler', async ({
    page,
  }) => {
    const scheduler = await setup(page)
    await page.goto('/scheduler')
    await scheduler.expectVisible()
    await page.waitForLoadState('networkidle')

    // URL should have been redirected to canonical week path
    expect(page.url()).toContain('/scheduler/calendar/week')

    // Week view should render
    await expect(page.getByText('12 AM', { exact: true })).toBeVisible()
  })

  test('TC-DL-05: deep link with date param preserves the date @scheduler', async ({ page }) => {
    const scheduler = await setup(page)
    // Navigate to a specific date — use a known past month to test date param
    await page.goto('/scheduler/calendar/month?date=2026-01-15')
    await scheduler.expectVisible()
    await page.waitForLoadState('networkidle')

    // URL should keep the date param
    expect(page.url()).toContain('date=2026-01-15')
  })
})

// ---------------------------------------------------------------------------
// Navigation — header controls update URL
// ---------------------------------------------------------------------------

test.describe('URL-addressable scheduler — navigation', () => {
  test.beforeEach(async ({ page }) => {
    const scheduler = await setup(page)
    await page.goto('/scheduler/calendar/week')
    await scheduler.expectVisible()
    await page.waitForLoadState('networkidle')
  })

  test('TC-NAV-01: clicking List toggle updates URL surface param @scheduler', async ({ page }) => {
    // Click the "List" button in the view mode toggle
    await page.getByRole('button', { name: 'List', exact: true }).click()
    await page.waitForTimeout(300)

    expect(page.url()).toContain('/scheduler/list')
  })

  test('TC-NAV-02: clicking Calendar toggle from list returns to week view @scheduler', async ({
    page,
  }) => {
    // First switch to list
    await page.getByRole('button', { name: 'List', exact: true }).click()
    await page.waitForTimeout(200)

    // Then switch back to calendar
    await page.getByRole('button', { name: 'Calendar', exact: true }).click()
    await page.waitForTimeout(300)

    // Should be back on week view
    expect(page.url()).toContain('/scheduler/calendar/week')
    await expect(page.getByText('12 AM', { exact: true })).toBeVisible()
  })

  test('TC-NAV-03: clicking Week toggle from month changes URL @scheduler', async ({ page }) => {
    const scheduler = await setup(page)
    await page.goto('/scheduler/calendar/month')
    await scheduler.expectVisible()
    await page.waitForLoadState('networkidle')

    // Click "Week" button in the Month/Week toggle to go back to week
    await page.getByRole('button', { name: 'Week', exact: true }).click()
    await page.waitForTimeout(300)

    expect(page.url()).toContain('/scheduler/calendar/week')
    await expect(page.getByText('12 AM', { exact: true })).toBeVisible()
  })

  test('TC-NAV-04: forward/backward buttons update date param @scheduler @navigation', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)

    // Navigate forward one week
    await scheduler.forwardButton.click()
    await page.waitForTimeout(300)

    // URL should contain a date param
    expect(page.url()).toContain('date=')

    // Navigate to today
    await scheduler.todayButton.click()
    await page.waitForTimeout(300)

    // When navigating to today, the date parameter might be omitted as it is the default
    // Or it might be present if the URL was canonicalized.
    // Based on useCalendarUrl.ts: if (state.date !== resolveToday()) { query.date = state.date }
    // So it should be NOT present if it is today.
    expect(page.url()).not.toContain('date=')
  })
})

// ---------------------------------------------------------------------------
// Sidebar — channel filtering
// ---------------------------------------------------------------------------

test.describe('URL-addressable scheduler — sidebar channels', () => {
  test.beforeEach(async ({ page }) => {
    const scheduler = await setup(page)
    await page.goto('/scheduler/calendar/week')
    await scheduler.expectVisible()
    await page.waitForLoadState('networkidle')
  })

  test('TC-SIDE-01: clicking All Channels navigates to /scheduler/calendar/week @scheduler', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)
    await scheduler.selectPlatform('')
    await page.waitForTimeout(300)

    expect(page.url()).toContain('/scheduler/calendar/week')
    // No channels[] param when showing all
    expect(page.url()).not.toContain('channels')
  })

  test('TC-SIDE-02: selecting a channel adds channels query param @scheduler', async ({ page }) => {
    const scheduler = new SchedulerPage(page)
    // Select the LinkedIn channel from the dropdown
    // Mock channels use the accountId as option value, not the provider name
    await scheduler.selectPlatform('sa-linkedin-001')

    expect(page.url()).toContain('/scheduler/calendar/week')
    expect(page.url()).toContain('channels')
  })
})

// ---------------------------------------------------------------------------
// History — browser back/forward
// ---------------------------------------------------------------------------

test.describe('URL-addressable scheduler — browser history', () => {
  test('TC-HIST-01: browser back and forward restore scheduler state @scheduler', async ({
    page,
  }) => {
    const scheduler = await setup(page)
    await page.goto('/scheduler/calendar/week')
    await scheduler.expectVisible()
    await page.waitForLoadState('networkidle')

    // Switch to list view
    await page.getByRole('button', { name: 'List', exact: true }).click()
    await page.waitForTimeout(300)
    expect(page.url()).toContain('/scheduler/list')

    // Go back to week
    await page.goBack()
    await page.waitForTimeout(500)
    expect(page.url()).toContain('/scheduler/calendar/week')

    // Go forward back to list
    await page.goForward()
    await page.waitForTimeout(500)
    expect(page.url()).toContain('/scheduler/list')
  })
})
