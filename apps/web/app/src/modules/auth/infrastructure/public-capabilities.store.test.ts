import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePublicCapabilitiesStore } from './public-capabilities.store'

const fetchPublicCapabilities = vi.hoisted(() => vi.fn())

vi.mock('@modules/auth/infrastructure/auth-api', () => ({
  fetchPublicCapabilities,
}))

function setupStore() {
  setActivePinia(createPinia())
  return usePublicCapabilitiesStore()
}

describe('usePublicCapabilitiesStore', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  describe('registrationEnabled', () => {
    it('is false by default (fail-closed) before any load call', () => {
      const store = setupStore()
      expect(store.registrationEnabled).toBe(false)
    })

    it('is true when API returns registrationEnabled true', async () => {
      fetchPublicCapabilities.mockResolvedValueOnce({ registrationEnabled: true })
      const store = setupStore()
      await store.load()
      expect(store.registrationEnabled).toBe(true)
    })

    it('is false when API returns registrationEnabled false', async () => {
      fetchPublicCapabilities.mockResolvedValueOnce({ registrationEnabled: false })
      const store = setupStore()
      await store.load()
      expect(store.registrationEnabled).toBe(false)
    })

    it('remains false when API call fails (fail-closed)', async () => {
      fetchPublicCapabilities.mockRejectedValueOnce(new Error('Network error'))
      const store = setupStore()
      await store.load()
      expect(store.registrationEnabled).toBe(false)
    })
  })

  describe('load()', () => {
    it('sets isLoading true while fetching', async () => {
      fetchPublicCapabilities.mockImplementation(
        () =>
          new Promise((resolve) => setTimeout(() => resolve({ registrationEnabled: true }), 10)),
      )
      const store = setupStore()
      const loadPromise = store.load()
      expect(store.isLoading).toBe(true)
      await loadPromise
      expect(store.isLoading).toBe(false)
    })

    it('sets isLoading false after successful load', async () => {
      fetchPublicCapabilities.mockResolvedValueOnce({ registrationEnabled: true })
      const store = setupStore()
      await store.load()
      expect(store.isLoading).toBe(false)
    })

    it('sets isLoading false after failed load (fail-closed)', async () => {
      fetchPublicCapabilities.mockRejectedValueOnce(new Error('Network error'))
      const store = setupStore()
      await store.load()
      expect(store.isLoading).toBe(false)
    })

    it('does not allow concurrent loads', async () => {
      let resolve!: (value: { registrationEnabled: boolean }) => void
      fetchPublicCapabilities.mockImplementation(
        () =>
          new Promise((r) => {
            resolve = r
          }),
      )
      const store = setupStore()
      const firstLoad = store.load()
      const secondLoad = store.load()
      expect(fetchPublicCapabilities).toHaveBeenCalledTimes(1)
      resolve({ registrationEnabled: true })
      await firstLoad
      await secondLoad
      expect(fetchPublicCapabilities).toHaveBeenCalledTimes(1)
    })

    it('caches result and does not refetch on subsequent calls', async () => {
      fetchPublicCapabilities.mockResolvedValueOnce({ registrationEnabled: true })
      const store = setupStore()
      await store.load()
      await store.load()
      await store.load()
      expect(fetchPublicCapabilities).toHaveBeenCalledTimes(1)
    })

    it('does not refetch after successful load (caches indefinitely)', async () => {
      fetchPublicCapabilities.mockResolvedValue({ registrationEnabled: true })
      const store = setupStore()
      await store.load()
      await store.load()
      await store.load()
      expect(fetchPublicCapabilities).toHaveBeenCalledTimes(1)
    })
  })

  describe('error', () => {
    it('is null after successful load', async () => {
      fetchPublicCapabilities.mockResolvedValueOnce({ registrationEnabled: true })
      const store = setupStore()
      await store.load()
      expect(store.error).toBeNull()
    })

    it('contains error message after failed load', async () => {
      fetchPublicCapabilities.mockRejectedValueOnce(new Error('Backend unavailable'))
      const store = setupStore()
      await store.load()
      expect(store.error).toBe('Backend unavailable')
    })
  })

  describe('capabilityChecked', () => {
    it('is false before any load', () => {
      const store = setupStore()
      expect(store.capabilityChecked).toBe(false)
    })

    it('is true after successful load', async () => {
      fetchPublicCapabilities.mockResolvedValueOnce({ registrationEnabled: true })
      const store = setupStore()
      await store.load()
      expect(store.capabilityChecked).toBe(true)
    })

    it('is true after failed load (fail-closed)', async () => {
      fetchPublicCapabilities.mockRejectedValueOnce(new Error('error'))
      const store = setupStore()
      await store.load()
      expect(store.capabilityChecked).toBe(true)
    })
  })
})
