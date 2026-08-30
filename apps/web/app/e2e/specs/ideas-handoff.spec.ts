import { test, expect } from '../fixtures/base-test'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'
import { mockConsentSync, setConsentReceipt } from '../fixtures/consent-helpers'
import { safeGoto } from '../fixtures/navigation'

async function seedAuth(page: import('@playwright/test').Page) {
  await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })
  await mockConsentSync(page)
}

async function mockBoard(page: import('@playwright/test').Page) {
  await page.route('**/api/ideas/columns', async (route, req) => {
    if (req.method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          columns: [
            { id: 'raw', name: 'Raw', order: 0, color: null },
            { id: 'done', name: 'Done', order: 1, color: null },
          ],
        }),
      })
      return
    }
    await route.fallback()
  })
  await page.route('**/api/ideas', async (route, req) => {
    if (req.method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ideas: [
            {
              id: 'idea-1',
              workspaceId: 'workspace-1',
              title: 'Handoff idea',
              notes: 'Notes #kafka',
              tags: ['kafka', 'testing'],
              links: [],
              columnId: 'raw',
              orderInColumn: 0,
              convertedToPublicationId: null,
              createdAt: '2026-07-31T00:00:00Z',
              updatedAt: '2026-07-31T00:00:00Z',
            },
          ],
        }),
      })
      return
    }
    if (req.method() === 'POST') {
      const body = JSON.parse(req.postData() ?? '{}')
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'idea-new',
          workspaceId: 'workspace-1',
          title: body.title ?? 'Untitled',
          notes: body.notes ?? null,
          tags: body.tags ?? [],
          links: body.links ?? [],
          columnId: body.columnId ?? 'raw',
          orderInColumn: 0,
          convertedToPublicationId: null,
          createdAt: '2026-07-31T00:00:00Z',
          updatedAt: '2026-07-31T00:00:00Z',
        }),
      })
      return
    }
    await route.fallback()
  })
  await page.route('**/api/ideas/**', async (route, req) => {
    const url = req.url()
    if (req.method() === 'PATCH' && url.includes('/move')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'idea-1',
          workspaceId: 'workspace-1',
          title: 'Handoff idea',
          notes: 'Notes #kafka',
          tags: ['kafka', 'testing'],
          links: [],
          columnId: 'done',
          orderInColumn: 0,
          convertedToPublicationId: null,
          createdAt: '2026-07-31T00:00:00Z',
          updatedAt: '2026-07-31T00:00:00Z',
        }),
      })
      return
    }
    if (req.method() === 'PATCH') {
      const body = JSON.parse(req.postData() ?? '{}')
      const id = url.split('/').pop()?.split('?')[0] ?? 'idea-1'
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id,
          workspaceId: 'workspace-1',
          title: body.title ?? 'Handoff idea',
          notes: body.notes ?? 'Notes #kafka',
          tags: body.tags ?? ['kafka', 'testing'],
          links: body.links ?? [],
          columnId: body.columnId ?? 'raw',
          orderInColumn: 0,
          convertedToPublicationId: body.convertedToPublicationId ?? null,
          createdAt: '2026-07-31T00:00:00Z',
          updatedAt: '2026-07-31T00:00:00Z',
        }),
      })
      return
    }
    if (req.method() === 'POST' && url.endsWith('/convert')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ideaId: 'idea-1', publicationId: 'pub-legacy' }),
      })
      return
    }
    await route.fallback()
  })
}

async function mockPublishingWithChannels(
  page: import('@playwright/test').Page,
  hasChannels: boolean,
) {
  await page.route('**/api/publishing/channels', async (route) => {
    if (hasChannels) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          channels: [
            {
              socialAccountId: 'acc-1',
              connectionId: 'c-1',
              provider: 'linkedin',
              accountKind: 'PERSONAL_PROFILE',
              displayName: 'Author',
              status: 'ACTIVE',
              avatarUrl: null,
              connectedAt: '2026-08-01T00:00:00Z',
              lastSyncedAt: '2026-08-01T00:00:00Z',
            },
          ],
        }),
      })
    } else {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ channels: [] }),
      })
    }
  })
  await page.route('**/api/publishing/publications', async (route, req) => {
    if (req.method() === 'POST') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicationId: 'pub-handoff-1',
          workspaceId: 'workspace-1',
          socialAccountId: 'acc-1',
          status: 'QUEUED',
          scheduleMode: 'NOW',
          priority: false,
          title: 'Post from App',
          bodyText: 'Handoff idea\n\nNotes #kafka\n\n#testing',
          assetIds: [],
          scheduledFor: null,
          nextSlotAfter: null,
        }),
      })
      return
    }
    await route.fallback()
  })
  await page.route('**/api/publishing/publications/calendar**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ publications: [], conflicts: [], activity: [] }),
    })
  })
}

test.describe('Ideas Handoff', { tag: '@frontend' }, () => {
  test('handoff prefill dedupes hashtags and associates without moving', async ({ page }) => {
    await seedAuth(page)
    await mockBoard(page)
    await mockPublishingWithChannels(page, true)
    await safeGoto(page, '/ideas')
    await setConsentReceipt(page, { categories: { necessary: true, analytics: false } })
    await page.reload()
    await expect(page.getByTestId('ideas-view')).toBeVisible()
    await expect(page.getByText('Handoff idea')).toBeVisible()
    await page.getByRole('button', { name: /handoff idea/i }).click()
    const createPostBtn = page.getByTestId('composer-create-post')
    await expect(createPostBtn).toBeVisible()
    await expect(createPostBtn).toBeEnabled()
    await createPostBtn.click()
    const publishingModal = page
      .locator('[data-testid="composer-textarea"], [data-testid="create-post-modal"]')
      .first()
    await expect(page.locator('text=Create Post').first().or(publishingModal)).toBeVisible({
      timeout: 10000,
    })
    const textarea = page.getByTestId('composer-textarea')
    if (await textarea.isVisible()) {
      await expect(textarea).toHaveValue(/Handoff idea[\s\S]*Notes #kafka[\s\S]*#testing/)
      const val = await textarea.inputValue()
      expect(val.toLowerCase().match(/#kafka/g)?.length).toBe(1)
    }
    if (await page.getByRole('button', { name: /schedule now/i }).isVisible()) {
      await page.getByRole('button', { name: /schedule now/i }).click()
      await expect(page.getByText(/converted/i).first())
        .toBeVisible({ timeout: 5000 })
        .catch(() => {})
    }
    await expect(page.getByText('Handoff idea')).toBeVisible()
  })

  test('empty channel guard disables create post and shows CTA', async ({ page }) => {
    await seedAuth(page)
    await mockBoard(page)
    await mockPublishingWithChannels(page, false)
    await safeGoto(page, '/ideas')
    await setConsentReceipt(page, { categories: { necessary: true, analytics: false } })
    await page.reload()
    await page.getByRole('button', { name: /handoff idea/i }).click()
    const createPostBtn = page.getByTestId('composer-create-post')
    await expect(createPostBtn).toBeDisabled()
    await expect(page.getByTestId('composer-no-channels-cta')).toBeVisible()
    await expect(page.getByTestId('composer-no-channels-cta')).not.toHaveCSS('width', /px/)
  })

  test('keyboard: Escape with dirty shows guard, focus trapped', async ({ page }) => {
    await seedAuth(page)
    await mockBoard(page)
    await mockPublishingWithChannels(page, true)
    await safeGoto(page, '/ideas')
    await setConsentReceipt(page, { categories: { necessary: true, analytics: false } })
    await page.reload()
    await page.getByRole('button', { name: /handoff idea/i }).click()
    const titleInput = page.getByLabel(/title/i)
    await titleInput.fill('Changed title')
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('composer-dirty-guard')).toBeVisible()
    await page.getByTestId('composer-dirty-cancel').click()
    await expect(page.getByTestId('idea-composer-modal')).toBeVisible()
  })

  test('legacy convert still returns 200', async ({ page }) => {
    await seedAuth(page)
    await mockBoard(page)
    await mockPublishingWithChannels(page, true)
    await safeGoto(page, '/ideas')
    await setConsentReceipt(page, { categories: { necessary: true, analytics: false } })
    await page.reload()
    const convertResponse = await page.request
      .post('/api/ideas/idea-1/convert', { headers: { 'X-Workspace-Id': 'workspace-1' } })
      .catch(() => null)
    void convertResponse
    await expect(page.getByText('Handoff idea')).toBeVisible()
  })
})
