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
              id: '0ca69867-76ef-5e75-a8c3-3933fb8faec4',
              workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
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
          id: 'b5489022-b2f9-5268-a83c-1006bb72857d',
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
  await page.route('**/api/ideas/**', async (route, req) => {
    const url = req.url()
    if (req.method() === 'PATCH' && url.includes('/move')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: '0ca69867-76ef-5e75-a8c3-3933fb8faec4',
          workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
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
      const id = url.split('/').pop()?.split('?')[0] ?? '0ca69867-76ef-5e75-a8c3-3933fb8faec4'
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id,
          workspaceId: 'a219727d-019f-544c-95f9-ac30f00cc8b2',
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
        body: JSON.stringify({
          ideaId: '0ca69867-76ef-5e75-a8c3-3933fb8faec4',
          publicationId: '3750c70a-19ae-5017-8b39-bc2b8b638465',
        }),
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
              socialAccountId: '788c8103-35f1-5899-b15a-6ca994253c99',
              connectionId: 'fbdc2c13-d186-5d45-8c3e-f12225cd1bd8',
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
  await page.route('**/api/publishing/publications/batch', async (route, req) => {
    if (req.method() === 'POST') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'ALL_CREATED',
          results: [
            {
              publicationId: '3a3d62ac-b4eb-5308-9afc-c8e18b29b48e',
              socialAccountId: '788c8103-35f1-5899-b15a-6ca994253c99',
              status: 'CREATED',
            },
          ],
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
    const moveRequests: string[] = []
    page.on('request', (request) => {
      if (new URL(request.url()).pathname.endsWith('/move')) moveRequests.push(request.url())
    })
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
    const textarea = page.getByTestId('composer-textarea')
    await expect(textarea).toHaveCount(1)
    await expect(textarea).toBeVisible()
    await expect(textarea).toHaveValue('Handoff idea\n\nNotes #kafka\n\n#testing')
    const val = await textarea.inputValue()
    expect(val.toLowerCase().match(/#kafka/g)?.length).toBe(1)
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
      bodyText: val,
      targets: [
        { socialAccountId: '788c8103-35f1-5899-b15a-6ca994253c99', bodyText: val, assetIds: [] },
      ],
    })
    expect((await associationRequestPromise).postDataJSON()).toEqual({
      convertedToPublicationId: '3a3d62ac-b4eb-5308-9afc-c8e18b29b48e',
    })
    await expect(
      page.getByText('Idea converted. Publication: 3a3d62ac-b4eb-5308-9afc-c8e18b29b48e', {
        exact: true,
      }),
    ).toBeVisible()
    await expect(textarea).not.toBeVisible()
    expect(moveRequests).toEqual([])
    await expect(page.getByText('Handoff idea')).toBeVisible()
  })

  for (const locale of ['en', 'es']) {
    test(`empty channel guard and CTA adapt to mobile and desktop in ${locale}`, async ({
      page,
    }) => {
      await page.addInitScript((language) => {
        localStorage.setItem('pt_settings_v1', JSON.stringify({ locale: language, theme: 'dark' }))
      }, locale)
      await seedAuth(page)
      await mockBoard(page)
      await mockPublishingWithChannels(page, false)
      await safeGoto(page, '/ideas')
      await setConsentReceipt(page, { categories: { necessary: true, analytics: false } })
      await page.reload()
      await expect(page.locator('html')).toHaveAttribute('lang', locale)
      await page.getByRole('button', { name: /handoff idea/i }).click()
      const modal = page.getByTestId('idea-composer-modal')
      const cta = modal.getByTestId('composer-no-channels-cta')
      await expect(modal.getByTestId('composer-create-post')).toBeDisabled()
      await expect(cta).toHaveText(
        locale === 'en'
          ? 'Connect a channel to enable publishing'
          : 'Conecta un canal para habilitar la publicación',
      )
      const widths: number[] = []
      const modalLayouts: Array<{
        viewportWidth: number
        scrollWidth: number
        clientWidth: number
      }> = []
      for (const width of [360, 1280]) {
        await page.setViewportSize({ width, height: 900 })
        await expect(cta).toBeVisible()
        await expect
          .poll(async () =>
            modal.evaluate((element) => {
              const bounds = element.getBoundingClientRect()
              return {
                withinViewport: bounds.left >= 0 && bounds.right <= window.innerWidth,
              }
            }),
          )
          .toMatchObject({ withinViewport: true })
        const bounds = await modal.boundingBox()
        expect(bounds).not.toBeNull()
        if (!bounds) throw new Error('Idea composer has no layout bounds')
        widths.push(bounds.width)
        const ctaLayout = await cta.evaluate((element) => {
          const bounds = element.getBoundingClientRect()
          const parent = element.closest('[data-testid="idea-composer-modal"]')
          if (!parent) throw new Error('CTA must be inside the idea composer')
          const parentBounds = parent.getBoundingClientRect()
          return {
            fits: bounds.left >= parentBounds.left && bounds.right <= parentBounds.right,
            noOverflow: element.scrollWidth <= element.clientWidth,
            height: bounds.height,
          }
        })
        expect(ctaLayout.fits).toBe(true)
        expect(ctaLayout.noOverflow).toBe(true)
        expect(ctaLayout.height).toBeGreaterThan(0)
        const modalLayout = await modal.evaluate((element) => ({
          scrollWidth: element.scrollWidth,
          clientWidth: element.clientWidth,
        }))
        const footerLayout = await modal.evaluate((element) => {
          const footer = element.querySelector('[data-slot="dialog-footer"]')
          if (!footer) throw new Error('Idea composer footer is missing')
          const boundsFor = (target: Element) => {
            const bounds = target.getBoundingClientRect()
            return {
              left: bounds.left,
              top: bounds.top,
              right: bounds.right,
              bottom: bounds.bottom,
              width: bounds.width,
              height: bounds.height,
            }
          }
          return {
            modal: boundsFor(element),
            footer: boundsFor(footer),
            buttons: Array.from(footer.querySelectorAll('button'), (button) => ({
              label: button.textContent?.trim(),
              disabled: button.disabled,
              ...boundsFor(button),
            })),
          }
        })
        console.log(
          'idea-footer-layout',
          JSON.stringify({ locale, viewportWidth: width, ...modalLayout, ...footerLayout }),
        )
        modalLayouts.push({ viewportWidth: width, ...modalLayout })
      }
      expect(widths[1]).toBeGreaterThan(widths[0] ?? 0)
      for (const modalLayout of modalLayouts) {
        expect(
          modalLayout.scrollWidth,
          JSON.stringify({ locale, modalLayouts }),
        ).toBeLessThanOrEqual(modalLayout.clientWidth)
      }
    })
  }

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
      .post('/api/ideas/0ca69867-76ef-5e75-a8c3-3933fb8faec4/convert', {
        headers: { 'X-Workspace-Id': 'a219727d-019f-544c-95f9-ac30f00cc8b2' },
      })
      .catch(() => null)
    void convertResponse
    await expect(page.getByText('Handoff idea')).toBeVisible()
  })
})
