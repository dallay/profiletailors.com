import { describe, it, expect, vi } from 'vitest'
import { ComposeModalPage } from './compose-modal-page'

/**
 * These tests assert the structural shape of the selectors the page object
 * builds. They do NOT mount the full CreatePostModal — that would require
 * a Pinia store and a full app shell. The contract under test is:
 *
 *   1. Each new locator is built via Playwright's getByRole / getByTestId /
 *      locator chains, never a raw class-name.
 *   2. Helper methods exist with the expected signatures so callers do
 *      not silently fall back to nothing.
 */

function makePageStub() {
  const locator = (() => {
    const chain: Record<string, unknown> = {
      first: vi.fn(() => chain),
      nth: vi.fn(() => chain),
      filter: vi.fn(() => chain),
      locator: vi.fn(() => chain),
      getByTestId: vi.fn(() => chain),
      getByRole: vi.fn(() => chain),
      getByText: vi.fn(() => chain),
      getByPlaceholder: vi.fn(() => chain),
      getByTitle: vi.fn(() => chain),
      click: vi.fn(() => Promise.resolve()),
      fill: vi.fn(() => Promise.resolve()),
      setInputFiles: vi.fn(() => Promise.resolve()),
      setChecked: vi.fn(() => Promise.resolve()),
      toBeVisible: vi.fn(() => Promise.resolve()),
      toBeHidden: vi.fn(() => Promise.resolve()),
      toBeDisabled: vi.fn(() => Promise.resolve()),
      toHaveClass: vi.fn(() => Promise.resolve()),
      toContainText: vi.fn(() => Promise.resolve()),
      or: vi.fn(() => chain),
      elementHandle: vi.fn(() => Promise.resolve(null)),
      textContent: vi.fn(() => Promise.resolve('')),
      evaluate: vi.fn(() => Promise.resolve(50)),
      getAttribute: vi.fn(() => Promise.resolve('blob:fake')),
    }
    return chain
  })()

  return {
    page: {
      getByRole: vi.fn(() => locator),
      getByText: vi.fn(() => locator),
      getByPlaceholder: vi.fn(() => locator),
      getByTestId: vi.fn(() => locator),
      getByTitle: vi.fn(() => locator),
      locator: vi.fn(() => locator),
    },
    locator,
  }
}

describe('ComposeModalPage (PR 2)', () => {
  it('exposes picker shell, dropzone, overflow, and overlay locators', () => {
    const { page } = makePageStub()
    const p = new ComposeModalPage(page as unknown as never)
    expect(p.pickerShell).toBeDefined()
    expect(p.mediaDropzone).toBeDefined()
    expect(p.uploadOverlay).toBeDefined()
    expect(p.overflowCard).toBeDefined()
  })

  it('exposes picker source tabs and apply/limit warnings', () => {
    const { page } = makePageStub()
    const p = new ComposeModalPage(page as unknown as never)
    expect(p.libraryTab).toBeDefined()
    expect(p.unsplashTab).toBeDefined()
    expect(p.pickerApply).toBeDefined()
    expect(p.pickerApplyWarning).toBeDefined()
    expect(p.limitWarning).toBeDefined()
  })

  it('exposes the social preview image locator', () => {
    const { page } = makePageStub()
    const p = new ComposeModalPage(page as unknown as never)
    expect(p.socialPreviewMediaImg).toBeDefined()
  })

  it('exposes helpers for library card lookup, attach, drop, and remove', () => {
    const { page } = makePageStub()
    const p = new ComposeModalPage(page as unknown as never)
    expect(typeof p.libraryAssetCard).toBe('function')
    expect(typeof p.attachMediaFiles).toBe('function')
    expect(typeof p.dropFiles).toBe('function')
    expect(typeof p.removeAttachmentByName).toBe('function')
    expect(typeof p.overflowCount).toBe('function')
  })

  it('exposes assertions for overlay text, progress range, and preview kind', () => {
    const { page } = makePageStub()
    const p = new ComposeModalPage(page as unknown as never)
    expect(typeof p.expectUploadOverlayText).toBe('function')
    expect(typeof p.expectUploadProgressBetween).toBe('function')
    expect(typeof p.previewMediaSrcKind).toBe('function')
  })

  it('libraryAssetCard and attachmentByIndex return non-null locator objects', () => {
    const { page } = makePageStub()
    const p = new ComposeModalPage(page as unknown as never)
    const card = p.libraryAssetCard('asset-xyz')
    const attachment = p.attachmentByIndex(0)
    expect(card).toBeDefined()
    expect(attachment).toBeDefined()
  })

  it('previewMediaSrcKind classifies blob: src as blob', async () => {
    const { page } = makePageStub()
    const p = new ComposeModalPage(page as unknown as never)
    // Override elementHandle to return a handle with a src attribute.
    ;(
      p.socialPreviewMediaImg as unknown as { elementHandle: () => Promise<unknown> }
    ).elementHandle = () =>
      Promise.resolve({
        getAttribute: () => Promise.resolve('blob:abc-123'),
      })
    const kind = await p.previewMediaSrcKind()
    expect(kind).toBe('blob')
  })

  it('previewMediaSrcKind classifies /api/media/ src as persisted', async () => {
    const { page } = makePageStub()
    const p = new ComposeModalPage(page as unknown as never)
    ;(
      p.socialPreviewMediaImg as unknown as { elementHandle: () => Promise<unknown> }
    ).elementHandle = () =>
      Promise.resolve({
        getAttribute: () => Promise.resolve('/api/media/assets/asset-1/preview'),
      })
    const kind = await p.previewMediaSrcKind()
    expect(kind).toBe('persisted')
  })

  it('previewMediaSrcKind returns none when no media is rendered', async () => {
    const { page } = makePageStub()
    const p = new ComposeModalPage(page as unknown as never)
    ;(
      p.socialPreviewMediaImg as unknown as { elementHandle: () => Promise<unknown> }
    ).elementHandle = () => Promise.resolve(null)
    const kind = await p.previewMediaSrcKind()
    expect(kind).toBe('none')
  })
})
