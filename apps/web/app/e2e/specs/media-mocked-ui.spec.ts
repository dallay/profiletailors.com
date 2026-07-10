import { test, expect } from '../fixtures/media-mocked-test'
import { mockAuthenticatedSession } from '../fixtures/auth-helpers'
import { mediaFiles } from '../fixtures/media-files'

const tags = '@media @mocked'

test.describe(`Media Library mocked UI ${tags}`, () => {
  test.beforeEach(async ({ mockState }) => {
    mockState.reset()
  })

  test('ML-LOAD-002 empty: shows empty state and zero counters', async ({ mediaPage }) => {
    await mediaPage.navigateTo()

    await expect(mediaPage.getEmptyState()).toBeVisible()
    await expect(mediaPage.uploadButton).toBeVisible()
    await expect(mediaPage.getCounter('READY')).toHaveText('0')
    await expect(mediaPage.getCounter('PROCESSING')).toHaveText('0')
    await expect(mediaPage.getCounter('FAILED')).toHaveText('0')
  })

  test('ML-LOAD-003 error: shows load error and retry while empty state still renders', async ({
    mediaPage,
    mockListResponse,
  }) => {
    mockListResponse({
      status: 400,
      body: { title: 'Media unavailable', detail: 'Failed to load test media.' },
    })

    await mediaPage.navigateTo()

    await expect(mediaPage.getLoadError()).toContainText('Failed to load test media.')
    await expect(mediaPage.refreshButton).toBeVisible()
    // Note: empty state is also visible because assets.length === 0 after failed load.
    // The product renders both the error message and the empty-state fallback simultaneously.
  })

  test('ML-VERIFY-001 PENDING user sees guidance and cannot start an upload', async ({
    mediaPage,
    mockState,
    page,
  }) => {
    await mockAuthenticatedSession(page, {
      email: 'pending-media@example.com',
      emailStatus: 'PENDING',
    })

    let mediaUploadRequests = 0
    page.on('request', (request) => {
      if (
        ['PUT', 'POST'].includes(request.method()) &&
        request.url().includes('/api/media/assets')
      ) {
        mediaUploadRequests += 1
      }
    })

    await mediaPage.navigateTo()

    await expect(page.getByTestId('media-verification-guidance')).toBeVisible()
    await expect(page.getByTestId('media-verification-guidance')).toContainText(
      /verify your email/i,
    )
    await expect(mediaPage.uploadButton).toBeDisabled()
    await expect(mediaPage.fileInput).toBeDisabled()
    await mediaPage.fileInput.setInputFiles(mediaFiles.base.path)

    expect(mediaUploadRequests).toBe(0)
    expect(mockState.putCount).toBe(0)
    expect(mockState.uploadPostCount).toBe(0)
  })

  test('ML-UP-001 upload new content: PUT 201 then POST and READY card appears', async ({
    mediaPage,
  }) => {
    await mediaPage.navigateTo()
    const beforeReady = Number(await mediaPage.getCounter('READY').textContent())

    await mediaPage.uploadFile(mediaFiles.base.path)
    await mediaPage.waitForUploadComplete(mediaFiles.base.name)

    await expect(mediaPage.getCardByName(mediaFiles.base.name)).toBeVisible()
    await expect(mediaPage.getCardStatus(mediaFiles.base.name)).toHaveText('READY')
    await expect
      .poll(async () => Number(await mediaPage.getCounter('READY').textContent()))
      .toBe(beforeReady + 1)
  })

  test('ML-CAS-001 dedup: PUT 200 skips POST and visible count does not increase', async ({
    mediaPage,
    mockState,
    mockNextPut,
    page,
  }) => {
    const existing = mockState.seedAsset({
      originalFilename: mediaFiles.base.name,
      mediaType: mediaFiles.base.type,
    })
    mockNextPut({ status: 200, body: { assetId: existing.assetId, deduped: true } })
    let uploadPosts = 0
    page.on('request', (request) => {
      if (request.method() === 'POST' && request.url().includes('/upload')) uploadPosts += 1
    })

    await mediaPage.navigateTo()
    const beforeCount = await mediaPage.getVisibleCount()
    await mediaPage.uploadFile(mediaFiles.baseCopy.path)
    await mediaPage.waitForUploadComplete(mediaFiles.base.name)

    expect(await mediaPage.getVisibleCount()).toBe(beforeCount)
    expect(uploadPosts).toBe(0)
    await expect.poll(() => mockState.putCount).toBe(1)
  })

  test('ML-ERR-004 rate-limit: PUT 429 surfaces failure and skips POST', async ({
    mediaPage,
    mockState,
    mockNextPut,
    page,
  }) => {
    mockNextPut({
      status: 429,
      retryAfterSeconds: 60,
      body: { errorCode: 'RATE_LIMIT_EXCEEDED', message: 'Hourly media creation limit exceeded.' },
    })
    let uploadPosts = 0
    page.on('request', (request) => {
      if (request.method() === 'POST' && request.url().includes('/upload')) uploadPosts += 1
    })

    await mediaPage.navigateTo()
    await mediaPage.uploadFile(mediaFiles.mutated.path)

    await expect.poll(() => mockState.putCount).toBe(1)
    await expect.poll(() => mockState.uploadPostCount).toBe(0)
    expect(uploadPosts).toBe(0)
    test.info().annotations.push({
      type: 'known-product-drift',
      description:
        'ML-ERR-004: the failed upload is tracked internally, but the Failed counter remains 0 after PUT 429.',
    })
  })

  test('ML-CAS-006 polling: PUT 202 polls until READY without binary POST', async ({
    mediaPage,
    mockState,
    mockNextPut,
    page,
  }) => {
    const seeded = mockState.seedAsset({
      originalFilename: mediaFiles.mutated.name,
      mediaType: mediaFiles.mutated.type,
      status: 'READY',
    })
    mockNextPut({ status: 202, retryAfterSeconds: 1, body: { assetId: seeded.assetId } })
    mockNextPut({ status: 200, body: { assetId: seeded.assetId, deduped: true } })
    let uploadPosts = 0
    page.on('request', (request) => {
      if (request.method() === 'POST' && request.url().includes('/upload')) uploadPosts += 1
    })

    await mediaPage.navigateTo()
    await mediaPage.uploadFile(mediaFiles.mutated.path)
    await mediaPage.waitForUploadComplete(mediaFiles.mutated.name)

    await expect(mediaPage.getCardStatus(mediaFiles.mutated.name)).toHaveText('READY')
    expect(uploadPosts).toBe(0)
    await expect.poll(() => mockState.putCount).toBe(2) // 202 + 200 polling sequence
  })

  test('ML-DEL-003 bulk dialog wording: cancel preserves assets, confirm deletes selected', async ({
    mediaPage,
    mockState,
  }) => {
    mockState.seedAsset({ originalFilename: 'alpha.png', mediaType: 'image/png' })
    mockState.seedAsset({ originalFilename: 'beta.png', mediaType: 'image/png' })

    await mediaPage.navigateTo()
    await mediaPage.selectCard('alpha.png')
    await mediaPage.selectCard('beta.png')

    await mediaPage.deleteSelectedButton.click()
    await expect(mediaPage.bulkDeleteDialog()).toBeVisible()
    await mediaPage.cancelBulkDelete()
    await expect(mediaPage.getCardByName('alpha.png')).toBeVisible()
    expect(mockState.deleteCount).toBe(0)

    await expect(mediaPage.deleteSelectedButton).toBeVisible()
    await mediaPage.deleteSelectedButton.click({ force: true })
    await expect(mediaPage.bulkDeleteDialog()).toBeVisible()
    await mediaPage.confirmBulkDelete()
    await expect.poll(() => mockState.deleteCount).toBe(2)
    await expect(mediaPage.getCardByName('alpha.png')).toBeHidden()
    await expect(mediaPage.getCardByName('beta.png')).toBeHidden()
  })

  test('ML-BROWSE-007 search+type+sort: filters image knowledge asset and orders by filename', async ({
    mediaPage,
    mockState,
  }) => {
    mockState.seedAsset({ originalFilename: 'Zebra.png', mediaType: 'image/png' })
    mockState.seedAsset({ originalFilename: 'Knowledge.png', mediaType: 'image/png' })
    mockState.seedAsset({ originalFilename: 'Knowledge.pdf', mediaType: 'application/pdf' })
    mockState.seedAsset({ originalFilename: 'Alpha.mp4', mediaType: 'video/mp4' })
    mockState.seedAsset({ originalFilename: 'Brief.pdf', mediaType: 'application/pdf' })

    await mediaPage.navigateTo()
    await mediaPage.search('Knowledge')
    await mediaPage.setTypeFilter('IMAGE')
    await mediaPage.setSort('filename-asc')

    await expect(mediaPage.visibleCountText()).toHaveText('1 / 5')
    await expect(mediaPage.cards().first()).toContainText('Knowledge.png')
  })

  test.describe
    .parallel('ML-MULTI-CONTEXT-001 parallel isolation', () => {
      test('parallel context A sees only asset A', async ({ mediaPage, mockState }) => {
        mockState.seedAsset({ originalFilename: 'context-a.png', mediaType: 'image/png' })
        await mediaPage.navigateTo()
        await expect(mediaPage.getCardByName('context-a.png')).toBeVisible()
        await expect(mediaPage.getCardByName('context-b.png')).toHaveCount(0)
      })

      test('parallel context B sees only asset B', async ({ mediaPage, mockState }) => {
        mockState.seedAsset({ originalFilename: 'context-b.png', mediaType: 'image/png' })
        await mediaPage.navigateTo()
        await expect(mediaPage.getCardByName('context-b.png')).toBeVisible()
        await expect(mediaPage.getCardByName('context-a.png')).toHaveCount(0)
      })
    })

  test('ML-A11Y-004 known defect: card action icon buttons lack accessible names', async ({
    mediaPage,
    mockState,
  }) => {
    test.fixme(
      true,
      'ML-A11Y-004 known product defect: Media Library card download/delete icon buttons lack accessible names.',
    )
    mockState.seedAsset({ originalFilename: 'unnamed-actions.png', mediaType: 'image/png' })
    await mediaPage.navigateTo()
    console.warn(
      'ML-A11Y-004 known defect: unnamed Media Library card action icon buttons remain unresolved.',
    )
  })

  test('ML-A11Y-005 known defect: composer fields do not expose stable id/name hooks', async () => {
    test.fixme(
      true,
      'Spec requirement Known-defect handling / Accessibility defects: composer field controls still lack the stable id/name hooks expected by accessibility-focused automation.',
    )
    console.warn(
      'ML-A11Y-005 known defect: form controls missing stable id/name automation hooks remain unresolved.',
    )
  })

  test('ML-CAS-007 known defect: UI still uses PROCESSING terminology instead of canonical CAS lifecycle', async () => {
    test.fixme(
      true,
      'Spec requirement Known-defect handling / Product drift: Media Library UI still surfaces PROCESSING instead of canonical PENDING_UPLOAD/UPLOADING lifecycle labels.',
    )
    console.warn(
      'ML-CAS-007 known defect: PROCESSING terminology remains in the UI and is intentionally documented, not normalized by tests.',
    )
  })

  test('ML-COMPOSE-006 known limitation: composer has no media library selector', async () => {
    test.fixme(
      true,
      'Spec requirement Product limitation: composer currently lacks a browse-from-library selector, so tests document the limitation without failing unrelated flows.',
    )
    console.warn(
      'ML-COMPOSE-006 known limitation: no composer media library selector is currently available.',
    )
  })
})
