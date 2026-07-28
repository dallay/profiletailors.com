import { describe, it, expect, beforeEach, vi } from 'vitest'
import { loadConsent, saveConsent, clearConsent } from './consent-storage'
import type { ConsentReceipt } from '../types/consent'

describe('consent-storage', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  describe('loadConsent', () => {
    it('returns null when no consent is stored', () => {
      expect(loadConsent()).toBeNull()
    })

    it('returns parsed receipt when valid consent exists', () => {
      const receipt: ConsentReceipt = {
        consentVersion: 1,
        policyVersion: '2026-07-23',
        timestamp: '2026-07-23T10:00:00Z',
        region: 'EU',
        categories: { necessary: true, analytics: true },
        dnt: false,
        source: 'banner',
      }
      localStorage.setItem('pt-consent', JSON.stringify(receipt))

      expect(loadConsent()).toEqual(receipt)
    })

    it('returns null when stored data is invalid JSON', () => {
      localStorage.setItem('pt-consent', 'invalid-json{')
      expect(loadConsent()).toBeNull()
    })

    it('returns null when stored data fails validation', () => {
      localStorage.setItem('pt-consent', JSON.stringify({ invalid: 'data' }))
      expect(loadConsent()).toBeNull()
    })

    it('returns null when stored data is a JSON array instead of an object', () => {
      localStorage.setItem('pt-consent', JSON.stringify(['not', 'an', 'object']))
      expect(loadConsent()).toBeNull()
    })

    it('returns null when localStorage is unavailable', () => {
      const originalGetItem = Storage.prototype.getItem
      Storage.prototype.getItem = vi.fn(() => {
        throw new Error('localStorage unavailable')
      })

      expect(loadConsent()).toBeNull()

      Storage.prototype.getItem = originalGetItem
    })
  })

  describe('saveConsent', () => {
    it('stores receipt in localStorage', () => {
      const receipt: ConsentReceipt = {
        consentVersion: 1,
        policyVersion: '2026-07-23',
        timestamp: '2026-07-23T10:00:00Z',
        region: 'EU',
        categories: { necessary: true, analytics: false },
        dnt: true,
        source: 'settings-panel',
      }

      saveConsent(receipt)

      const stored = localStorage.getItem('pt-consent')
      expect(stored).not.toBeNull()
      expect(JSON.parse(stored ?? 'null')).toEqual(receipt)
    })

    it('overwrites existing consent', () => {
      const oldReceipt: ConsentReceipt = {
        consentVersion: 1,
        policyVersion: '2026-07-23',
        timestamp: '2026-07-23T09:00:00Z',
        region: 'EU',
        categories: { necessary: true, analytics: false },
        dnt: false,
        source: 'banner',
      }
      saveConsent(oldReceipt)

      const newReceipt: ConsentReceipt = {
        ...oldReceipt,
        timestamp: '2026-07-23T10:00:00Z',
        categories: { necessary: true, analytics: true },
      }
      saveConsent(newReceipt)

      expect(loadConsent()).toEqual(newReceipt)
    })

    it('handles localStorage errors gracefully', () => {
      const originalSetItem = Storage.prototype.setItem
      Storage.prototype.setItem = vi.fn(() => {
        throw new Error('localStorage full')
      })

      const receipt: ConsentReceipt = {
        consentVersion: 1,
        policyVersion: '2026-07-23',
        timestamp: '2026-07-23T10:00:00Z',
        region: 'EU',
        categories: { necessary: true, analytics: true },
        dnt: false,
        source: 'banner',
      }

      // Should not throw
      expect(() => saveConsent(receipt)).not.toThrow()

      Storage.prototype.setItem = originalSetItem
    })
  })

  describe('clearConsent', () => {
    it('removes consent from localStorage', () => {
      const receipt: ConsentReceipt = {
        consentVersion: 1,
        policyVersion: '2026-07-23',
        timestamp: '2026-07-23T10:00:00Z',
        region: 'EU',
        categories: { necessary: true, analytics: true },
        dnt: false,
        source: 'banner',
      }
      saveConsent(receipt)

      clearConsent()

      expect(localStorage.getItem('pt-consent')).toBeNull()
      expect(loadConsent()).toBeNull()
    })

    it('handles localStorage errors gracefully', () => {
      const originalRemoveItem = Storage.prototype.removeItem
      Storage.prototype.removeItem = vi.fn(() => {
        throw new Error('localStorage unavailable')
      })

      // Should not throw
      expect(() => clearConsent()).not.toThrow()

      Storage.prototype.removeItem = originalRemoveItem
    })
  })
})
