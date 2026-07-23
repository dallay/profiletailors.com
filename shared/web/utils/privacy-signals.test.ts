import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { hasPrivacySignal, isDNTEnabled, isGPCEnabled } from './privacy-signals'

describe('privacy-signals', () => {
  let originalNavigator: Navigator

  beforeEach(() => {
    originalNavigator = global.navigator
  })

  afterEach(() => {
    global.navigator = originalNavigator
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
      // @ts-expect-error Testing SSR scenario
      global.navigator = undefined
      expect(isDNTEnabled()).toBe(false)
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
      // @ts-expect-error Testing SSR scenario
      global.navigator = undefined
      expect(isGPCEnabled()).toBe(false)
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
