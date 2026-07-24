import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { hasPrivacySignal, isDNTEnabled, isGPCEnabled } from './privacy-signals'

describe('privacy-signals', () => {
  let originalNavigator: Navigator
  let originalDNT: PropertyDescriptor | undefined
  let originalGPC: PropertyDescriptor | undefined

  beforeEach(() => {
    originalNavigator = global.navigator
    if (global.navigator) {
      originalDNT = Object.getOwnPropertyDescriptor(global.navigator, 'doNotTrack')
      originalGPC = Object.getOwnPropertyDescriptor(global.navigator, 'globalPrivacyControl')
    }
  })

  afterEach(() => {
    // Restore navigator first if it was set to undefined
    if (!global.navigator && originalNavigator) {
      global.navigator = originalNavigator
    }
    
    // Then restore properties
    if (global.navigator) {
      if (originalDNT) {
        Object.defineProperty(global.navigator, 'doNotTrack', originalDNT)
      } else if (Object.getOwnPropertyDescriptor(global.navigator, 'doNotTrack')) {
        // @ts-expect-error Cleanup test stub
        delete global.navigator.doNotTrack
      }
      if (originalGPC) {
        Object.defineProperty(global.navigator, 'globalPrivacyControl', originalGPC)
      } else if (Object.getOwnPropertyDescriptor(global.navigator, 'globalPrivacyControl')) {
        // @ts-expect-error Cleanup test stub
        delete global.navigator.globalPrivacyControl
      }
    }
  })

  describe('isDNTEnabled', () => {
    it('returns true when doNotTrack is "1"', () => {
      Object.defineProperty(global.navigator, 'doNotTrack', {
        value: '1',
        writable: true,
        configurable: true,
      })
      expect(isDNTEnabled()).toBe(true)
    })

    it('returns true when doNotTrack is "yes"', () => {
      Object.defineProperty(global.navigator, 'doNotTrack', {
        value: 'yes',
        writable: true,
        configurable: true,
      })
      expect(isDNTEnabled()).toBe(true)
    })

    it('returns false when doNotTrack is null', () => {
      Object.defineProperty(global.navigator, 'doNotTrack', {
        value: null,
        writable: true,
        configurable: true,
      })
      expect(isDNTEnabled()).toBe(false)
    })

    it('returns false in SSR context (no navigator)', () => {
      const savedNavigator = global.navigator
      // @ts-expect-error Testing SSR scenario
      global.navigator = undefined
      expect(isDNTEnabled()).toBe(false)
      global.navigator = savedNavigator
    })
  })

  describe('isGPCEnabled', () => {
    it('returns true when globalPrivacyControl is true', () => {
      Object.defineProperty(global.navigator, 'globalPrivacyControl', {
        value: true,
        writable: true,
        configurable: true,
      })
      expect(isGPCEnabled()).toBe(true)
    })

    it('returns false when globalPrivacyControl is false', () => {
      Object.defineProperty(global.navigator, 'globalPrivacyControl', {
        value: false,
        writable: true,
        configurable: true,
      })
      expect(isGPCEnabled()).toBe(false)
    })

    it('returns false when globalPrivacyControl is undefined', () => {
      expect(isGPCEnabled()).toBe(false)
    })

    it('returns false in SSR context (no navigator)', () => {
      const savedNavigator = global.navigator
      // @ts-expect-error Testing SSR scenario
      global.navigator = undefined
      expect(isGPCEnabled()).toBe(false)
      global.navigator = savedNavigator
    })
  })

  describe('hasPrivacySignal', () => {
    it('returns true when DNT is enabled', () => {
      Object.defineProperty(global.navigator, 'doNotTrack', {
        value: '1',
        writable: true,
        configurable: true,
      })
      expect(hasPrivacySignal()).toBe(true)
    })

    it('returns true when GPC is enabled', () => {
      Object.defineProperty(global.navigator, 'globalPrivacyControl', {
        value: true,
        writable: true,
        configurable: true,
      })
      expect(hasPrivacySignal()).toBe(true)
    })

    it('returns true when both DNT and GPC are enabled', () => {
      Object.defineProperty(global.navigator, 'doNotTrack', {
        value: '1',
        writable: true,
        configurable: true,
      })
      Object.defineProperty(global.navigator, 'globalPrivacyControl', {
        value: true,
        writable: true,
        configurable: true,
      })
      expect(hasPrivacySignal()).toBe(true)
    })

    it('returns false when neither DNT nor GPC are enabled', () => {
      Object.defineProperty(global.navigator, 'doNotTrack', {
        value: null,
        writable: true,
        configurable: true,
      })
      Object.defineProperty(global.navigator, 'globalPrivacyControl', {
        value: false,
        writable: true,
        configurable: true,
      })
      expect(hasPrivacySignal()).toBe(false)
    })
  })
})
