import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const mockSaveConsent = vi.fn()
const mockOpenSettings = vi.fn()

let mockAnalyticsEnabled = false
let mockHasValidConsent = false
let mockReceipt: unknown = null

vi.mock('@modules/settings/infrastructure/consent.store', () => ({
  useConsentStore: () => ({
    analyticsEnabled: mockAnalyticsEnabled,
    hasValidConsent: mockHasValidConsent,
    receipt: mockReceipt,
    saveConsent: mockSaveConsent,
    openSettings: mockOpenSettings,
  }),
}))

describe('useConsent', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockSaveConsent.mockReset()
    mockOpenSettings.mockReset()
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

  it('acceptAll calls store.saveConsent with analytics=true', async () => {
    const { useConsent } = await import('./useConsent')
    const { acceptAll } = useConsent()
    acceptAll()
    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: true,
      source: 'banner',
    })
  })

  it('rejectAll calls store.saveConsent with analytics=false', async () => {
    const { useConsent } = await import('./useConsent')
    const { rejectAll } = useConsent()
    rejectAll()
    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: false,
      source: 'banner',
    })
  })

  it('save delegates to store.saveConsent with the given value', async () => {
    const { useConsent } = await import('./useConsent')
    const { save } = useConsent()
    save(true)
    expect(mockSaveConsent).toHaveBeenCalledWith({
      analytics: true,
      source: 'banner',
    })
  })

  it('openSettings delegates to store.openSettings', async () => {
    const { useConsent } = await import('./useConsent')
    const { openSettings } = useConsent()
    openSettings()
    expect(mockOpenSettings).toHaveBeenCalledOnce()
  })
})
