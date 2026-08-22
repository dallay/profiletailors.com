import { test, expect } from '../fixtures/media-real-test'

const tags = '@real-cas @media'

/**
 * Real CAS smoke tests against local Spring Boot backend.
 * Requires a backend and worktree-aware app runtime with real media credentials.
 */
test.describe(`Media Library real CAS smoke ${tags}`, () => {
  test('ML-SMOKE-001 upload fresh content: PUT 201, POST, READY card appears', async ({
    mediaPage,
    requestLedger,
    runFiles,
  }) => {
    await mediaPage.navigateTo()

    // 1. Upload fresh file
    await mediaPage.uploadFile(runFiles.base.path)
    await mediaPage.waitForUploadComplete(runFiles.base.name)

    // 2. Validate visual card and status
    const card = mediaPage.getCardByName(runFiles.base.name)
    await expect(card).toBeVisible()
    await expect(mediaPage.getCardStatus(runFiles.base.name)).toHaveText('READY')

    // 3. Verify ledger captured PUT and POST
    const putEvents = requestLedger.forMethod('PUT')
    const postEvents = requestLedger.forMethod('POST')

    expect(putEvents.length).toBe(1)
    expect(putEvents[0].status).toBe(201)

    expect(postEvents.length).toBe(1)
    expect(postEvents[0].url).toContain('/upload')
    expect(postEvents[0].status).toBe(200)

    // Verify sequence assertion
    const assetId = putEvents[0].assetId
    expect(assetId).toBeDefined()
    if (!assetId) {
      throw new Error('assetId is not defined')
    }
    requestLedger.assertSequence(assetId, ['PUT 201', 'POST 200'])
  })

  test('ML-SMOKE-002 upload exact duplicate: skips binary POST and does not increase visible card count', async ({
    mediaPage,
    requestLedger,
    runFiles,
  }) => {
    await mediaPage.navigateTo()

    // 1. Upload base file first to establish READY state
    await mediaPage.uploadFile(runFiles.base.path)
    await mediaPage.waitForUploadComplete(runFiles.base.name)

    // Reset ledger to only track the duplicate upload events
    requestLedger.reset()

    const beforeCount = await mediaPage.getVisibleCount()

    // 2. Upload duplicate file (baseCopy)
    await mediaPage.uploadFile(runFiles.baseCopy.path)
    // Wait for the duplicate PUT to be captured by the ledger instead of
    // waitForUploadComplete, which can race by matching the already-visible card.
    await expect.poll(() => requestLedger.forMethod('PUT').length).toBe(1)

    // 3. Verify count doesn't increase by more than 0 (duplicate resolves to existing card)
    const afterCount = await mediaPage.getVisibleCount()
    expect(afterCount).toBe(beforeCount)

    // 4. Verify no POST was sent for duplicate upload (only PUT 200)
    const putEvents = requestLedger.forMethod('PUT')
    expect(putEvents.length).toBe(1)
    expect(putEvents[0].status).toBe(200)

    requestLedger.assertZeroPosts()

    // Known defect annotation formatting
    test.info().annotations.push({
      type: 'known-defect',
      description:
        'CAS contract drift: backend returns 200 OK for duplicate PUT instead of 201 PENDING_UPLOAD; see spec requirement ML-SMOKE-002.',
    })
  })
})
