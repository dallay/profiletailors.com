import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { hasPrivacySignal, isDNTEnabled, isGPCEnabled } from './privacy-signals'

describe('privacy-signals', () => {
  let originalNavigator: Navigator
  let originalDNT: PropertyDescriptor | undefined
  let originalGPC: PropertyDescriptor | undefined
  let originalWindow: Window & typeof globalThis

  beforeEach(() => {
    originalNavigator = global.navigator
    originalWindow = global.window
    if (global.navigator) {
      originalDNT = Object.getOwnPropertyDescriptor(global.navigator, 'doNotTrack')
      originalGPC = Object.getOwnPropertyDescriptor(global.navigator, 'globalPrivacyControl')
    }
  })

  afterEach(() => {
    if (!global.navigator && originalNavigator) {
      global.navigator = originalNavigator
    }
    if (!global.window && originalWindow) {
      global.window = originalWindow
    }

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

    it('returns false without throwing when a navigator-like global exists without window', () => {
      const savedWindow = global.window
      Object.defineProperty(global.navigator, 'doNotTrack', {
        value: null,
        writable: true,
        configurable: true,
      })
      Reflect.deleteProperty(global, 'window')

      expect(() => isDNTEnabled()).not.toThrow()
      expect(isDNTEnabled()).toBe(false)

      global.window = savedWindow
    })

    it('returns true via the legacy window.doNotTrack fallback even when navigator.doNotTrack is unset', () => {
      Object.defineProperty(global.navigator, 'doNotTrack', {
        value: null,
        writable: true,
        configurable: true,
      })
      ;(window as Window & { doNotTrack?: string }).doNotTrack = '1'

      expect(isDNTEnabled()).toBe(true)

      delete (window as Window & { doNotTrack?: string }).doNotTrack
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
