import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { hasPrivacySignal, isDNTEnabled, isGPCEnabled } from './privacy-signals'

function stubNavigator(value: Partial<Navigator> = {}): void {
  vi.stubGlobal(
    'navigator',
    Object.assign(
      {
        doNotTrack: null,
        globalPrivacyControl: undefined,
      },
      value,
    ),
  )
}

describe('privacy-signals', () => {
  beforeEach(() => {
    stubNavigator()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  describe('isDNTEnabled', () => {
    it('returns true when doNotTrack is "1"', () => {
      stubNavigator({ doNotTrack: '1' })
      expect(isDNTEnabled()).toBe(true)
    })

    it('returns true when doNotTrack is "yes"', () => {
      stubNavigator({ doNotTrack: 'yes' })
      expect(isDNTEnabled()).toBe(true)
    })

    it('returns false when doNotTrack is null', () => {
      stubNavigator({ doNotTrack: null })
      expect(isDNTEnabled()).toBe(false)
    })

    it('returns false in SSR context (no navigator)', () => {
      vi.stubGlobal('navigator', undefined)
      expect(isDNTEnabled()).toBe(false)
    })

    it('returns false without throwing when a navigator-like global exists without window', () => {
      stubNavigator({ doNotTrack: null })
      const savedWindow = global.window
      Reflect.deleteProperty(global, 'window')

      expect(() => isDNTEnabled()).not.toThrow()
      expect(isDNTEnabled()).toBe(false)

      global.window = savedWindow
    })

    it('returns true via the legacy window.doNotTrack fallback even when navigator.doNotTrack is unset', () => {
      stubNavigator({ doNotTrack: null })
      ;(window as Window & { doNotTrack?: string }).doNotTrack = '1'

      expect(isDNTEnabled()).toBe(true)

      delete (window as Window & { doNotTrack?: string }).doNotTrack
    })
  })

  describe('isGPCEnabled', () => {
    it('returns true when globalPrivacyControl is true', () => {
      stubNavigator({ globalPrivacyControl: true })
      expect(isGPCEnabled()).toBe(true)
    })

    it('returns false when globalPrivacyControl is false', () => {
      stubNavigator({ globalPrivacyControl: false })
      expect(isGPCEnabled()).toBe(false)
    })

    it('returns false when globalPrivacyControl is undefined', () => {
      expect(isGPCEnabled()).toBe(false)
    })

    it('returns false in SSR context (no navigator)', () => {
      vi.stubGlobal('navigator', undefined)
      expect(isGPCEnabled()).toBe(false)
    })
  })

  describe('hasPrivacySignal', () => {
    it('returns true when DNT is enabled', () => {
      stubNavigator({ doNotTrack: '1' })
      expect(hasPrivacySignal()).toBe(true)
    })

    it('returns true when GPC is enabled', () => {
      stubNavigator({ globalPrivacyControl: true })
      expect(hasPrivacySignal()).toBe(true)
    })

    it('returns true when both DNT and GPC are enabled', () => {
      stubNavigator({ doNotTrack: '1', globalPrivacyControl: true })
      expect(hasPrivacySignal()).toBe(true)
    })

    it('returns false when neither DNT nor GPC are enabled', () => {
      stubNavigator({ doNotTrack: null, globalPrivacyControl: false })
      expect(hasPrivacySignal()).toBe(false)
    })
  })
})
