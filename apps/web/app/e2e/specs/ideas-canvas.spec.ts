import { test, expect } from '../fixtures/base-test'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'
import { mockConsentSync, setConsentReceipt } from '../fixtures/consent-helpers'
import { safeGoto } from '../fixtures/navigation'

async function seedAuthAndConsent(page: import('@playwright/test').Page) {
  await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })
  await mockConsentSync(page)
}

test.describe('Ideas Canvas', { tag: '@frontend' }, () => {
  test.beforeEach(async ({ page }) => {
    await seedAuthAndConsent(page)

    await page.route('**/api/ideas/columns', async (route, request) => {
      if (request.method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            columns: [
              { id: 'raw', name: 'Raw', order: 0, color: null },
              { id: 'in-progress', name: 'In Progress', order: 1, color: null },
              { id: 'done', name: 'Done', order: 2, color: null },
            ],
          }),
        })
        return
      }

      if (request.method() === 'PUT') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: request.postData() ?? '{}',
        })
        return
      }

      await route.fallback()
    })

    await page.route('**/api/ideas', async (route, request) => {
      if (request.method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            ideas: [
              {
                id: 'idea-1',
                workspaceId: 'workspace-1',
                title: 'Launch thread idea',
                notes: 'Focus on ROI angle',
                tags: ['launch', 'roi'],
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

      if (request.method() === 'POST') {
        const body = JSON.parse(request.postData() ?? '{}')
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'idea-created',
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

    await page.route('**/api/ideas/**', async (route, request) => {
      const url = request.url()
      const method = request.method()

      if (method === 'PATCH' && url.includes('/move')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'idea-1',
            workspaceId: 'workspace-1',
            title: 'Launch thread idea',
            notes: 'Focus on ROI angle',
            tags: ['launch', 'roi'],
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

      if (method === 'PATCH') {
        const body = JSON.parse(request.postData() ?? '{}')
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'idea-1',
            workspaceId: 'workspace-1',
            title: body.title ?? 'Launch thread idea',
            notes: body.notes ?? 'Focus on ROI angle',
            tags: body.tags ?? ['launch', 'roi'],
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

      if (method === 'POST' && url.endsWith('/convert')) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ ideaId: 'idea-1', publicationId: 'pub-idea-1' }),
        })
        return
      }

      if (method === 'DELETE') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ id: 'idea-1' }),
        })
        return
      }

      await route.fallback()
    })
  })

  test('can open Ideas page and create idea via quick capture', async ({ page }) => {
    await safeGoto(page, '/')
    await setConsentReceipt(page, {
      categories: { necessary: true, analytics: false },
    })
    await page.reload()

    await page.getByRole('link', { name: /ideas/i }).click()
    await expect(page.getByTestId('ideas-view')).toBeVisible()

    await page
      .getByRole('button', { name: /add idea/i })
      .first()
      .click()
    await page.getByLabel(/title/i).fill('New campaign angle')
    await page.getByRole('button', { name: /save idea/i }).click()

    await expect(page.getByText(/idea saved/i)).toBeVisible()
  })

  test('can edit and convert an existing idea', async ({ page }) => {
    await safeGoto(page, '/ideas')
    await setConsentReceipt(page, {
      categories: { necessary: true, analytics: false },
    })
    await page.reload()

    await expect(page.getByText('Launch thread idea')).toBeVisible()
    await page.getByRole('button', { name: /launch thread idea/i }).click()

    await page.getByLabel(/title/i).fill('Launch thread final')
    await page.getByRole('button', { name: /^save$/i }).click()
    await expect(page.getByText(/idea updated/i)).toBeVisible()

    await page.getByRole('button', { name: /convert to post/i }).click()
    await expect(page.getByText(/idea converted/i)).toBeVisible()
  })

  test('can open columns settings and add a column', async ({ page }) => {
    await safeGoto(page, '/ideas')
    await setConsentReceipt(page, {
      categories: { necessary: true, analytics: false },
    })
    await page.reload()

    await page.getByRole('button', { name: /^columns$/i }).click()
    await page.getByPlaceholder(/new column name/i).fill('In Review')
    await page.getByRole('button', { name: /^add$/i }).click()
    await page.getByRole('button', { name: /^save$/i }).click()

    await expect(page.getByText(/columns updated/i)).toBeVisible()
  })
})
