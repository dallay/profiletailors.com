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
import { authenticateAs, keepSessionAlive, mockLoginResponse } from '../fixtures/auth-helpers'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function setup(page: import('@playwright/test').Page): Promise<SchedulerPage> {
  // Drop any leftover session/state from a previous test in this worker.
  await page.context().clearCookies()
  await page.goto('about:blank').catch(() => {})
  await page
    .evaluate(() => {
      try {
        localStorage.clear()
        sessionStorage.clear()
      } catch {}
    })
    .catch(() => {})

  // While the SPA boots for the upcoming `/login` navigation, force
  // refresh to fail so we always land on the login form, never the dashboard.
  await page.route('**/api/auth/refresh', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      body: JSON.stringify({ title: 'Refresh session invalid', status: 401 }),
    })
  })

  await mockLoginResponse(page, {
    status: 200,
    body: {
      accessToken: 'scheduler-test-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      principalId: 'test-user',
      email: 'dev@profiletailors.com',
      username: 'dev',
      emailStatus: 'PENDING',
      workspaceId: 'workspace-001',
    },
  })

  await authenticateAs(page)

  // After login, swap the refresh mock to a successful one so later
  // `page.goto('/scheduler/...')` navigations can rehydrate the session.
  await keepSessionAlive(page)

  const scheduler = new SchedulerPage(page)
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
    const scheduler = new SchedulerPage(page)
    await authenticateAs(page)

    // Switch to month first
    await page.goto('/scheduler/calendar/month')
    await scheduler.expectVisible()
    await page.waitForLoadState('networkidle')

    // Use the page object locator for the calendar sub-view toggle
    await scheduler.switchToWeek()

    expect(page.url()).toContain('/scheduler/calendar/week')
  })

  test('TC-NAV-04: forward/backward buttons update date param @scheduler @navigation', async ({
    page,
  }) => {
    const scheduler = new SchedulerPage(page)
    const initialUrl = page.url()

    // Navigate forward one week
    await scheduler.forwardButton.click()
    await page.waitForTimeout(300)
    const afterForwardUrl = page.url()

    // URL should move away from the initial state and carry an explicit date
    expect(afterForwardUrl).not.toBe(initialUrl)
    expect(afterForwardUrl).toContain('date=')

    // Navigate backward and confirm URL changes again (history of date navigation works)
    await scheduler.backwardButton.click()
    await page.waitForTimeout(300)
    const afterBackwardUrl = page.url()
    expect(afterBackwardUrl).not.toBe(afterForwardUrl)

    // Navigate to today; canonicalization may remove or keep date depending on the derived visible date,
    // but it must produce a stable scheduler route.
    await scheduler.todayButton.click()
    await page.waitForTimeout(300)
    expect(page.url()).toContain('/scheduler/calendar/week')
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
    const allChannelsButton = page.getByRole('button', { name: /all channels/i })
    await allChannelsButton.click()
    await page.waitForTimeout(300)

    expect(page.url()).toContain('/scheduler/calendar/week')
    // No channels[] param when showing all
    expect(page.url()).not.toContain('channels')
  })

  test('TC-SIDE-02: clicking a channel adds channels[] query param @scheduler', async ({
    page,
  }) => {
    // The channel row lives inside the shadcn-vue Sidebar component
    // (renders as <div data-slot="sidebar">, not <aside>).
    // Scope the locator to the sidebar to avoid matching the account-menu button.
    const sidebar = page.locator('[data-slot="sidebar"]')
    const channelButton = sidebar.getByRole('button', { name: /dev user/i }).first()
    await channelButton.click()
    await page.waitForTimeout(300)

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

  test.fixme(
    'TC-HIST-02: refresh/share restores scheduler filters from canonical URL @scheduler',
    'Blocked in current backend-free scheduler harness: refresh lands on login instead of preserving authenticated scheduler state.',
    async ({ page }) => {
      await setup(page)
      await keepSessionAlive(page)
      await page.goto(
        '/scheduler/calendar/week?date=2026-06-20&status=queued&channels[]=sa-linkedin-001',
      )
      await page.waitForLoadState('networkidle')

      await page.reload()
      await page.waitForURL('**/scheduler/calendar/week**')
      await page.waitForLoadState('networkidle')

      await expect(page.locator('#calendar-post-status-select')).toHaveValue('queued')
      await expect(page.locator('#calendar-platform-select')).toHaveValue('sa-linkedin-001')
      await expect(page).toHaveURL(
        /\/scheduler\/calendar\/week\?date=2026-06-20&status=queued&channels%5B%5D=sa-linkedin-001$/,
      )
    },
  )

  test.fixme(
    'TC-HIST-03: legacy /scheduler/calendar/day canonicalizes to canonical week route @scheduler',
    'Blocked in current backend-free scheduler harness: direct navigation to legacy protected route redirects to login before scheduler app state hydrates.',
    async ({ page }) => {
      await setup(page)
      await page.goto('/scheduler/calendar/day?date=2026-06-20&channels=sa-linkedin-001')
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveURL(
        /\/scheduler\/calendar\/week\?date=2026-06-20&channels%5B%5D=sa-linkedin-001$/,
      )
      await expect(page.locator('#calendar-platform-select')).toHaveValue('sa-linkedin-001')
    },
  )

  test.fixme(
    'TC-HIST-04: clearing filters cleans query back to canonical scheduler route @scheduler',
    'Blocked in current backend-free scheduler harness: direct protected-route navigation resolves to login, so scheduler controls are unavailable.',
    async ({ page }) => {
      await setup(page)
      await page.goto('/scheduler/calendar/week?status=queued&channels[]=sa-linkedin-001')
      await page.waitForLoadState('networkidle')

      await page.locator('#calendar-post-status-select').selectOption('all')
      await page.locator('#calendar-platform-select').selectOption('')
      await page.waitForTimeout(300)

      await expect(page).toHaveURL(/\/scheduler\/calendar\/week$/)
    },
  )
})
