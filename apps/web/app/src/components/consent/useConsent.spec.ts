import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const mockSaveConsent = vi.fn()

let mockAnalyticsEnabled = false
let mockHasValidConsent = false
let mockReceipt: unknown = null

vi.mock('@modules/settings/infrastructure/consent.store', () => ({
  useConsentStore: () => ({
    analyticsEnabled: mockAnalyticsEnabled,
    hasValidConsent: mockHasValidConsent,
    receipt: mockReceipt,
    saveConsent: mockSaveConsent,
  }),
}))

describe('useConsent', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockSaveConsent.mockReset()
    mockAnalyticsEnabled = false
    mockHasValidConsent = false
    mockReceipt = null
  })

  it('returns the current analyticsEnabled value from the store', async () => {
    mockAnalyticsEnabled = true
    const { useConsent } = await import('./useConsent')
    const { analyticsEnabled } = useConsent()
    expect(analyticsEnabled.value).toBe(true)
  })

  it('returns hasValidConsent from the store', async () => {
    mockHasValidConsent = true
    const { useConsent } = await import('./useConsent')
    const { hasValidConsent } = useConsent()
    expect(hasValidConsent.value).toBe(true)
  })

  it('returns the receipt from the store', async () => {
    mockReceipt = { version: 1 }
    const { useConsent } = await import('./useConsent')
    const { receipt } = useConsent()
    expect(receipt.value).toEqual({ version: 1 })
  })

  it('acceptAll defaults to source banner', async () => {
    const { useConsent } = await import('./useConsent')
    const { acceptAll } = useConsent()
    acceptAll()
    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: true,
      source: 'banner',
    })
  })

  it('acceptAll uses the given source', async () => {
    const { useConsent } = await import('./useConsent')
    const { acceptAll } = useConsent('settings-panel')
    acceptAll()
    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: true,
      source: 'settings-panel',
    })
  })

  it('rejectAll defaults to source banner', async () => {
    const { useConsent } = await import('./useConsent')
    const { rejectAll } = useConsent()
    rejectAll()
    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: false,
      source: 'banner',
    })
  })

  it('rejectAll uses the given source', async () => {
    const { useConsent } = await import('./useConsent')
    const { rejectAll } = useConsent('settings-panel')
    rejectAll()
    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: false,
      source: 'settings-panel',
    })
  })

  it('save delegates to store.saveConsent with the given value and default source', async () => {
    const { useConsent } = await import('./useConsent')
    const { save } = useConsent()
    save(true)
    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: true,
      source: 'banner',
    })
  })

  it('save uses the given source', async () => {
    const { useConsent } = await import('./useConsent')
    const { save } = useConsent('settings-panel')
    save(false)
    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: false,
      source: 'settings-panel',
    })
  })
})
