import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import {
  CONSENT_STORAGE_KEY,
  CURRENT_CONSENT_VERSION,
  CURRENT_POLICY_VERSION,
  type ConsentReceipt,
} from '@profiletailors/shared-web'

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

const mockUser = {
  principalId: 'user-1',
  email: 'test@test.com',
  username: 'testuser',
  emailStatus: 'VERIFIED',
  displayIdentity: 'testuser',
}
const mockAccessToken = 'mock-token-1'

let mockApiFetch = vi.fn()
const mockToastError = vi.fn()

vi.mock('vue-sonner', () => ({
  toast: {
    error: mockToastError,
  },
}))

vi.mock('@modules/auth/infrastructure/auth.store', () => ({
  useAuthStore: () => ({
    apiFetch: mockApiFetch,
    isAuthenticated: true,
    accessToken: mockAccessToken,
    user: mockUser,
  }),
}))

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function validReceipt(overrides?: Partial<Record<string, unknown>>): ConsentReceipt {
  return {
    consentVersion: CURRENT_CONSENT_VERSION,
    policyVersion: CURRENT_POLICY_VERSION,
    timestamp: '2026-07-23T12:00:00.000Z',
    region: 'EU',
    categories: { necessary: true, analytics: true },
    dnt: false,
    source: 'banner',
    ...overrides,
  }
}

function seedLocalStorage(receipt: ConsentReceipt): void {
  localStorage.setItem(CONSENT_STORAGE_KEY, JSON.stringify(receipt))
}

function clearLocalStorage(): void {
  localStorage.removeItem(CONSENT_STORAGE_KEY)
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('consent store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockApiFetch = vi.fn()
    mockToastError.mockReset()
    clearLocalStorage()
    // Reset navigator privacy signals
    Object.defineProperty(navigator, 'doNotTrack', {
      value: undefined,
      configurable: true,
    })
    Object.defineProperty(navigator, 'globalPrivacyControl', {
      value: undefined,
      configurable: true,
    })
  })

  // ── loadFromStorage ───────────────────────────────────────────────────

  it('loads valid receipt from localStorage on init', async () => {
    const receipt = validReceipt()
    seedLocalStorage(receipt)

    const { useConsentStore } = await import('./consent.store')
    const store = useConsentStore()

    expect(store.receipt).toEqual(receipt)
    expect(store.hasValidConsent).toBe(true)
  })

  it('sets receipt to null when no localStorage key exists', async () => {
    const { useConsentStore } = await import('./consent.store')
    const store = useConsentStore()

    expect(store.receipt).toBeNull()
    expect(store.hasValidConsent).toBe(false)
  })

  it('sets receipt to null when stored JSON is malformed', async () => {
    localStorage.setItem(CONSENT_STORAGE_KEY, 'not-valid-json')

    const { useConsentStore } = await import('./consent.store')
    const store = useConsentStore()

    expect(store.receipt).toBeNull()
  })

  it('sets receipt to null when consentVersion is outdated', async () => {
    seedLocalStorage(validReceipt({ consentVersion: 0 }))

    const { useConsentStore } = await import('./consent.store')
    const store = useConsentStore()

    expect(store.receipt).toBeNull()
    expect(store.hasValidConsent).toBe(false)
  })

  // ── saveConsent ───────────────────────────────────────────────────────

  it('saveConsent writes receipt to localStorage and updates state', async () => {
    const { useConsentStore } = await import('./consent.store')
    const store = useConsentStore()

    store.saveConsent({ analytics: true, source: 'banner' })

    expect(store.receipt).not.toBeNull()
    expect(store.receipt!.categories.analytics).toBe(true)
    expect(store.receipt!.source).toBe('banner')
    expect(store.receipt!.consentVersion).toBe(CURRENT_CONSENT_VERSION)
    expect(store.receipt!.policyVersion).toBe(CURRENT_POLICY_VERSION)
    expect(store.receipt!.region).toBe('EU')
    expect(store.receipt!.categories.necessary).toBe(true)
    expect(store.hasValidConsent).toBe(true)

    // Verify it was persisted
    const raw = localStorage.getItem(CONSENT_STORAGE_KEY)
    expect(raw).not.toBeNull()
    const parsed = JSON.parse(raw!)
    expect(parsed.categories.analytics).toBe(true)
  })

  it('saveConsent with analytics false saves correctly', async () => {
    const { useConsentStore } = await import('./consent.store')
    const store = useConsentStore()

    store.saveConsent({ analytics: false, source: 'banner' })

    expect(store.receipt!.categories.analytics).toBe(false)
    expect(store.receipt!.source).toBe('banner')
  })

  it('saveConsent uses source settings-panel when called from settings', async () => {
    const { useConsentStore } = await import('./consent.store')
    const store = useConsentStore()

    store.saveConsent({ analytics: true, source: 'settings-panel' })

    expect(store.receipt!.source).toBe('settings-panel')
  })

  it('saveConsent captures DNT signal when active', async () => {
    Object.defineProperty(navigator, 'doNotTrack', {
      value: '1',
      configurable: true,
    })

    const { useConsentStore } = await import('./consent.store')
    const store = useConsentStore()

    store.saveConsent({ analytics: true, source: 'banner' })

    expect(store.receipt!.dnt).toBe(true)
  })

  it('saveConsent sets dnt false when no privacy signal', async () => {
    const { useConsentStore } = await import('./consent.store')
    const store = useConsentStore()

    store.saveConsent({ analytics: true, source: 'banner' })

    expect(store.receipt!.dnt).toBe(false)
  })

  // ── syncToBackend (authenticated) ─────────────────────────────────────

  it('saveConsent calls syncToBackend for authenticated user with analytics true (record)', async () => {
    mockApiFetch.mockResolvedValue({})

    const { useConsentStore } = await import('./consent.store')
    const store = useConsentStore()

    await store.saveConsent({ analytics: true, source: 'banner' })

    expect(mockApiFetch).toHaveBeenCalledWith('/api/governance/consent', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        subjectKind: 'USER',
        subjectValue: 'user-1',
        consentType: 'CONSENT',
        purpose: 'web.analytics',
        policyVersion: CURRENT_POLICY_VERSION,
        source: 'banner',
        locale: 'en',
      }),
      workspaceScoped: true,
    })
  })

  it('saveConsent calls syncToBackend for authenticated user with analytics false (withdraw)', async () => {
    mockApiFetch.mockResolvedValue({})

    const { useConsentStore } = await import('./consent.store')
    const store = useConsentStore()

    await store.saveConsent({ analytics: false, source: 'banner' })

    expect(mockApiFetch).toHaveBeenCalledWith('/api/governance/consent/withdraw', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        subjectKind: 'USER',
        subjectValue: 'user-1',
        purpose: 'web.analytics',
        policyVersion: CURRENT_POLICY_VERSION,
        reason: 'user_request',
      }),
      workspaceScoped: true,
    })
  })

  it('sets syncError when backend sync fails but keeps local consent', async () => {
    mockApiFetch.mockRejectedValue(new Error('Server error'))

    const { useConsentStore } = await import('./consent.store')
    const store = useConsentStore()

    store.saveConsent({ analytics: true, source: 'banner' })

    // Local consent should still be saved immediately
    expect(store.receipt).not.toBeNull()
    expect(store.receipt!.categories.analytics).toBe(true)

    // syncError is set asynchronously in the .catch() handler
    await vi.waitFor(() => {
      expect(store.syncError).toContain('sync failed')
    })
  })

  it('shows toast.error when backend sync fails, but localStorage is saved', async () => {
    mockApiFetch.mockRejectedValue(new Error('Server error'))

    const { useConsentStore } = await import('./consent.store')
    const store = useConsentStore()

    store.saveConsent({ analytics: true, source: 'banner' })

    // Local consent must be saved immediately (localStorage first)
    expect(store.receipt).not.toBeNull()
    expect(store.receipt!.categories.analytics).toBe(true)

    // toast.error should be called asynchronously
    await vi.waitFor(() => {
      expect(mockToastError).toHaveBeenCalledOnce()
    })
    expect(mockToastError).toHaveBeenCalledWith(expect.stringContaining('sync failed'))
  })

  // ── loadFromStorage (explicit) ────────────────────────────────────────

  it('loadFromStorage re-reads from localStorage after external change', async () => {
    const { useConsentStore } = await import('./consent.store')
    const store = useConsentStore()

    // Initially null
    expect(store.receipt).toBeNull()

    // External write
    const receipt = validReceipt({ categories: { necessary: true, analytics: false } })
    seedLocalStorage(receipt)

    store.loadFromStorage()

    expect(store.receipt).not.toBeNull()
    expect(store.receipt!.categories.analytics).toBe(false)
  })
})
