import { test, expect } from '../fixtures/base-test'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'
import { mockConsentSync, setConsentReceipt } from '../fixtures/consent-helpers'
import { safeGoto } from '../fixtures/navigation'

async function seedAuthAndConsent(page: import('@playwright/test').Page) {
  await mockAuthenticatedSession(page, { emailStatus: 'VERIFIED' })
  await mockConsentSync(page)
}

async function openIdeasNavigation(page: import('@playwright/test').Page) {
  const ideasLink = page.getByRole('link', { name: /^ideas(?:\s+ideas)?$/i })
  if (!(await ideasLink.isVisible())) {
    await page
      .locator('header')
      .getByRole('button', { name: 'Open navigation', exact: true })
      .click()
  }
  await expect(ideasLink).toBeVisible()
  await ideasLink.click()
}

test.describe('Ideas Canvas', { tag: '@frontend' }, () => {
  test.beforeEach(async ({ page }) => {
    await seedAuthAndConsent(page)
    let ideaTitle = 'Launch thread idea'
    let convertedToPublicationId: string | null = null

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
                id: '0ca69867-76ef-5e75-a8c3-3933fb8faec4',
                workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
                title: ideaTitle,
                notes: 'Focus on ROI angle',
                tags: ['launch', 'roi'],
                links: [],
                columnId: 'raw',
                orderInColumn: 0,
                convertedToPublicationId,
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
            id: '29e38a90-83cc-5bd6-b35d-fe36041817ea',
            workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
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
            id: '0ca69867-76ef-5e75-a8c3-3933fb8faec4',
            workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
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
        if (typeof body.title === 'string') ideaTitle = body.title
        if (typeof body.convertedToPublicationId === 'string') {
          convertedToPublicationId = body.convertedToPublicationId
        }
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: '0ca69867-76ef-5e75-a8c3-3933fb8faec4',
            workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
            title: ideaTitle,
            notes: body.notes ?? 'Focus on ROI angle',
            tags: body.tags ?? ['launch', 'roi'],
            links: body.links ?? [],
            columnId: body.columnId ?? 'raw',
            orderInColumn: 0,
            convertedToPublicationId,
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
          body: JSON.stringify({
            ideaId: '0ca69867-76ef-5e75-a8c3-3933fb8faec4',
            publicationId: '0e0ca66b-f01a-5684-af70-6bb5f89afdaf',
          }),
        })
        return
      }

      if (method === 'DELETE') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ id: '0ca69867-76ef-5e75-a8c3-3933fb8faec4' }),
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

    await openIdeasNavigation(page)
    await expect(page.getByTestId('ideas-view')).toBeVisible()

    await page
      .getByRole('button', { name: /add idea/i })
      .first()
      .click()
    await page.getByLabel(/title/i).fill('New campaign angle')
    const modal = page.getByTestId('idea-composer-modal')
    await modal.getByTestId('composer-save').click()

    await expect(page.getByText(/idea saved/i)).toBeVisible()
    await expect(modal).not.toBeVisible()
    await expect(page.getByRole('button', { name: /new campaign angle/i })).toBeVisible()
  })

  test('can edit an existing idea and associate a publication through Create Post', async ({
    page,
  }) => {
    const publicationId = '0e0ca66b-f01a-5684-af70-6bb5f89afdaf'
    await page.route('**/api/publishing/channels', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          channels: [
            {
              socialAccountId: '788c8103-35f1-5899-b15a-6ca994253c99',
              connectionId: 'fbdc2c13-d186-5d45-8c3e-f12225cd1bd8',
              provider: 'linkedin',
              accountKind: 'PERSONAL_PROFILE',
              displayName: 'Author',
              status: 'ACTIVE',
              avatarUrl: null,
            },
          ],
        }),
      })
    })
    await page.route('**/api/publishing/publications/batch', async (route, request) => {
      expect(request.method()).toBe('POST')
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'ALL_CREATED',
          results: [
            {
              publicationId,
              socialAccountId: '788c8103-35f1-5899-b15a-6ca994253c99',
              status: 'CREATED',
            },
          ],
        }),
      })
    })
    await safeGoto(page, '/ideas')
    await setConsentReceipt(page, {
      categories: { necessary: true, analytics: false },
    })
    await page.reload()

    await expect(page.getByText('Launch thread idea')).toBeVisible()
    await page.getByRole('button', { name: /launch thread idea/i }).click()

    await page.getByLabel(/title/i).fill('Launch thread final')
    const modal = page.getByTestId('idea-composer-modal')
    await modal.getByTestId('composer-save').click()
    await expect(page.getByText(/idea updated/i)).toBeVisible()

    await expect(modal).not.toBeVisible()
    await page.getByRole('button', { name: /launch thread final/i }).click()
    await expect(modal.getByTestId('composer-title-input')).toHaveValue('Launch thread final')
    await modal.getByTestId('composer-create-post').click()
    const textarea = page.getByTestId('composer-textarea')
    await expect(textarea).toBeVisible()
    await expect(textarea).toHaveValue('Launch thread final\n\nFocus on ROI angle\n\n#launch #roi')
    const channel = page.getByTestId('channel-button')
    await expect(channel).toHaveAttribute('aria-pressed', 'false')
    await channel.click()
    await expect(channel).toHaveAttribute('aria-pressed', 'true')
    const publicationRequestPromise = page.waitForRequest(
      (request) =>
        new URL(request.url()).pathname === '/api/publishing/publications/batch' &&
        request.method() === 'POST',
    )
    const associationRequestPromise = page.waitForRequest(
      (request) =>
        new URL(request.url()).pathname === '/api/ideas/0ca69867-76ef-5e75-a8c3-3933fb8faec4' &&
        request.method() === 'PATCH',
    )
    await page.getByRole('button', { name: /^schedule now$/i }).click()
    expect((await publicationRequestPromise).postDataJSON()).toMatchObject({
      bodyText: 'Launch thread final\n\nFocus on ROI angle\n\n#launch #roi',
      targets: [
        {
          socialAccountId: '788c8103-35f1-5899-b15a-6ca994253c99',
          bodyText: 'Launch thread final\n\nFocus on ROI angle\n\n#launch #roi',
          assetIds: [],
        },
      ],
    })
    expect((await associationRequestPromise).postDataJSON()).toEqual({
      convertedToPublicationId: publicationId,
    })
    await expect(
      page.getByText(`Idea converted. Publication: ${publicationId}`, { exact: true }),
    ).toBeVisible()
    await expect(textarea).not.toBeVisible()
    await expect(page.getByRole('button', { name: /launch thread final/i })).toBeVisible()
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
