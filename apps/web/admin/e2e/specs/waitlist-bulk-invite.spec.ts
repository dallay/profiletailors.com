import { WaitlistPage } from '../pages/waitlist-page'
import { expect, setupOwnerSession, setupSupportSession, test } from '../fixtures/base-test'
import { makeEntry, resetEntrySequence } from '../fixtures/test-data'

test.describe('waitlist bulk invite (mocked lane)', () => {
  test('hides bulk actions without the invite permission', async ({ page }) => {
    await setupSupportSession(page)
    const waitlist = new WaitlistPage(page)
    await waitlist.goto()

    await expect(waitlist.heading).toBeVisible()
    await expect(page.getByTestId('bulk-select')).toHaveCount(0)
    await expect(waitlist.bulkInviteButton).toHaveCount(0)
  })

  test('renders seeded entries with statuses', async ({ page }) => {
    await setupOwnerSession(page)
    const waitlist = new WaitlistPage(page)
    await waitlist.goto()

    await expect(waitlist.rowByEmail('ava@example.com')).toContainText('Pending')
    await expect(waitlist.rowByEmail('dan@example.com')).toContainText('Invited')
    await expect(waitlist.rowByEmail('eli@example.com')).toContainText('Converted')
  })

  test('email search narrows the rows', async ({ page }) => {
    await setupOwnerSession(page)
    const waitlist = new WaitlistPage(page)
    await waitlist.goto()
    await waitlist.search('ben@example.com')

    await expect(page.locator('tbody tr')).toHaveCount(1)
    await expect(waitlist.rowByEmail('ben@example.com')).toBeVisible()
  })

  test('status filter narrows the rows', async ({ page }) => {
    await setupOwnerSession(page)
    const waitlist = new WaitlistPage(page)
    await waitlist.goto()
    await waitlist.filterStatus('PENDING')

    await expect(page.locator('tbody tr')).toHaveCount(3)
  })

  test('row selection updates the bulk count', async ({ page }) => {
    await setupOwnerSession(page)
    const waitlist = new WaitlistPage(page)
    await waitlist.goto()

    await waitlist.selectRow('ava@example.com')
    await waitlist.selectRow('ben@example.com')
    await expect(waitlist.bulkInviteButton).toContainText('Invite selected (2)')
  })

  test('select-all covers the visible page', async ({ page }) => {
    await setupOwnerSession(page)
    const waitlist = new WaitlistPage(page)
    await waitlist.goto()

    await waitlist.selectAll()
    await expect(waitlist.bulkInviteButton).toContainText('Invite selected (5)')
  })

  test('all-pending batch reports every invitation', async ({ page }) => {
    const { state } = await setupOwnerSession(page)
    const waitlist = new WaitlistPage(page)
    await waitlist.goto()

    await waitlist.selectRow('ava@example.com')
    await waitlist.selectRow('ben@example.com')
    await waitlist.selectRow('cid@example.com')
    await waitlist.submitBulkInvite()

    await expect(waitlist.resultsPanel).toContainText('3 invited')
    await expect(waitlist.resultsPanel).toContainText('0 skipped')
    await expect(waitlist.resultsPanel).toContainText('0 failed')
    expect(state.bulkCalls).toHaveLength(1)

    const chips = await waitlist.mainText()
    expect(chips).toContain('Pending: 0')
    expect(chips).toContain('Invited: 4')
  })

  test('mixed batch reports per-entry outcomes', async ({ page }) => {
    await setupOwnerSession(page)
    const waitlist = new WaitlistPage(page)
    await waitlist.goto()

    await waitlist.selectRow('ava@example.com')
    await waitlist.selectRow('ben@example.com')
    await waitlist.selectRow('dan@example.com')
    await waitlist.selectRow('eli@example.com')
    await waitlist.submitBulkInvite()

    await expect(waitlist.resultsPanel).toContainText('2 invited')
    await expect(waitlist.resultsPanel).toContainText('1 skipped')
    await expect(waitlist.resultsPanel).toContainText('1 failed')
    await expect(waitlist.resultsPanel).toContainText('ALREADY_INVITED')
    await expect(waitlist.resultsPanel).toContainText('ENTRY_ALREADY_CONVERTED')
  })

  test('retrying a partial batch yields skips without duplicates', async ({ page }) => {
    const { state } = await setupOwnerSession(page)
    const waitlist = new WaitlistPage(page)
    await waitlist.goto()

    await waitlist.selectRow('ava@example.com')
    await waitlist.selectRow('ben@example.com')
    await waitlist.submitBulkInvite()
    await expect(waitlist.resultsPanel).toContainText('2 invited')

    await waitlist.selectAll()
    await waitlist.submitBulkInvite()

    await expect(waitlist.resultsPanel).toContainText('1 invited')
    await expect(waitlist.resultsPanel).toContainText('3 skipped')
    await expect(waitlist.resultsPanel).toContainText('1 failed')
    await expect(waitlist.resultsPanel).toContainText('e2e-entry-3 — invited')
    expect(state.bulkCalls).toHaveLength(2)
  })

  test('empty selection keeps the button disabled and sends nothing', async ({ page }) => {
    const { state } = await setupOwnerSession(page)
    const waitlist = new WaitlistPage(page)
    await waitlist.goto()

    await expect(waitlist.bulkInviteButton).toBeDisabled()
    expect(state.bulkCalls).toHaveLength(0)
  })

  test('over-limit selection is blocked with a localized message', async ({ page }) => {
    resetEntrySequence()
    const many = Array.from({ length: 51 }, (_, index) => makeEntry(`bulk-${index}`))
    const { state } = await setupOwnerSession(page, { entries: many })
    const waitlist = new WaitlistPage(page)
    await waitlist.goto()

    await waitlist.selectAll()
    await expect(waitlist.bulkInviteButton).toContainText('Invite selected (51)')
    await waitlist.submitBulkInvite()

    expect(state.bulkCalls).toHaveLength(0)
    await expect(waitlist.alert).toContainText('50')
  })

  test('transport failure shows the generic error', async ({ page }) => {
    await setupOwnerSession(page)
    const waitlist = new WaitlistPage(page)
    await waitlist.goto()

    await page.route('**/api/admin/waitlist-entries/invitations:bulk', (route) =>
      route.abort('failed'),
    )
    await waitlist.selectRow('ava@example.com')
    await waitlist.submitBulkInvite()

    await expect(waitlist.alert).toContainText('An error occurred.')
  })

  test.describe('spanish locale', () => {
    test.use({ locale: 'es-ES' })

    test('renders bulk copy in spanish', async ({ page }) => {
      await setupOwnerSession(page, {}, /lista de espera/i)
      const waitlist = new WaitlistPage(page)
      await waitlist.goto(/lista de espera/i)

      await expect(waitlist.bulkInviteButton).toContainText('Invitar seleccionados')
    })
  })
})
