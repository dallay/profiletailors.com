import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

/**
 * ConsentScript.astro ships its logic as a single inline, self-contained IIFE
 * (no imports, no TypeScript syntax) so that it can run synchronously in the
 * document `<head>` before any other script. This helper extracts that exact
 * source and executes it in the jsdom test environment so the tests exercise
 * the real shipped code rather than a re-implementation of it.
 */
function runConsentScript(): void {
  const filePath = resolve(__dirname, './ConsentScript.astro')
  const source = readFileSync(filePath, 'utf-8')
  const match = source.match(/<script is:inline>([\s\S]*?)<\/script>/)
  if (!match) {
    throw new Error('Could not find the inline <script> block in ConsentScript.astro')
  }
  // eslint-disable-next-line no-new-func
  new Function(match[1])()
}

const CONSENT_KEY = 'pt-consent'
const ANALYTICS_FLAG = '__PT_CONSENT_ANALYTICS'
const testWindow = window as Window & Record<string, unknown>

describe('ConsentScript inline consent check', () => {
  let originalNavigator: Navigator

  beforeEach(() => {
    originalNavigator = global.navigator
    localStorage.clear()
    delete testWindow[ANALYTICS_FLAG]
    delete testWindow.__PT_DNT
    delete testWindow.doNotTrack

    Object.defineProperty(global.navigator, 'doNotTrack', {
      value: null,
      writable: true,
      configurable: true,
    })
    Object.defineProperty(global.navigator, 'globalPrivacyControl', {
      value: undefined,
      writable: true,
      configurable: true,
    })
  })

  afterEach(() => {
    global.navigator = originalNavigator
    vi.restoreAllMocks()
  })

  it('blocks analytics by default when no consent receipt is stored', () => {
    runConsentScript()

    expect(testWindow[ANALYTICS_FLAG]).toBe(false)
    expect(testWindow.__PT_DNT).toBe(false)
  })

  it('allows analytics when a valid receipt with analytics=true is stored', () => {
    localStorage.setItem(
      CONSENT_KEY,
      JSON.stringify({
        consentVersion: 1,
        policyVersion: '2026-07-23',
        timestamp: '2026-07-23T10:00:00.000Z',
        region: 'EU',
        categories: { necessary: true, analytics: true },
        dnt: false,
        source: 'banner',
      })
    )

    runConsentScript()

    expect(testWindow[ANALYTICS_FLAG]).toBe(true)
  })

  it('blocks analytics when a valid receipt has analytics=false', () => {
    localStorage.setItem(
      CONSENT_KEY,
      JSON.stringify({
        consentVersion: 1,
        policyVersion: '2026-07-23',
        timestamp: '2026-07-23T10:00:00.000Z',
        region: 'EU',
        categories: { necessary: true, analytics: false },
        dnt: false,
        source: 'settings',
      })
    )

    runConsentScript()

    expect(testWindow[ANALYTICS_FLAG]).toBe(false)
  })

  it('blocks analytics when the stored receipt has an outdated consentVersion', () => {
    localStorage.setItem(
      CONSENT_KEY,
      JSON.stringify({
        consentVersion: 0,
        policyVersion: '2026-07-23',
        timestamp: '2026-07-23T10:00:00.000Z',
        region: 'EU',
        categories: { necessary: true, analytics: true },
        dnt: false,
        source: 'banner',
      })
    )

    runConsentScript()

    expect(testWindow[ANALYTICS_FLAG]).toBe(false)
  })

  it('blocks analytics and does not throw when the stored value is invalid JSON', () => {
    localStorage.setItem(CONSENT_KEY, 'not-json{')
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined)

    expect(() => runConsentScript()).not.toThrow()
    expect(testWindow[ANALYTICS_FLAG]).toBe(false)
    expect(warnSpy).toHaveBeenCalled()
  })

  it('blocks analytics when categories.necessary is not true', () => {
    localStorage.setItem(
      CONSENT_KEY,
      JSON.stringify({
        consentVersion: 1,
        policyVersion: '2026-07-23',
        timestamp: '2026-07-23T10:00:00.000Z',
        region: 'EU',
        categories: { necessary: false, analytics: true },
        dnt: false,
        source: 'banner',
      })
    )

    runConsentScript()

    expect(testWindow[ANALYTICS_FLAG]).toBe(false)
  })

  it('blocks analytics when region is not a 2-character code', () => {
    localStorage.setItem(
      CONSENT_KEY,
      JSON.stringify({
        consentVersion: 1,
        policyVersion: '2026-07-23',
        timestamp: '2026-07-23T10:00:00.000Z',
        region: 'USA',
        categories: { necessary: true, analytics: true },
        dnt: false,
        source: 'banner',
      })
    )

    runConsentScript()

    expect(testWindow[ANALYTICS_FLAG]).toBe(false)
  })

  it('blocks analytics when policyVersion is not in YYYY-MM-DD format', () => {
    localStorage.setItem(
      CONSENT_KEY,
      JSON.stringify({
        consentVersion: 1,
        policyVersion: 'July 23, 2026',
        timestamp: '2026-07-23T10:00:00.000Z',
        region: 'EU',
        categories: { necessary: true, analytics: true },
        dnt: false,
        source: 'banner',
      })
    )

    runConsentScript()

    expect(testWindow[ANALYTICS_FLAG]).toBe(false)
  })

  it('blocks analytics when source is not "banner" or "settings"', () => {
    localStorage.setItem(
      CONSENT_KEY,
      JSON.stringify({
        consentVersion: 1,
        policyVersion: '2026-07-23',
        timestamp: '2026-07-23T10:00:00.000Z',
        region: 'EU',
        categories: { necessary: true, analytics: true },
        dnt: false,
        source: 'invalid-source',
      })
    )

    runConsentScript()

    expect(testWindow[ANALYTICS_FLAG]).toBe(false)
  })

  it('detects Do Not Track via navigator.doNotTrack === "1"', () => {
    Object.defineProperty(navigator, 'doNotTrack', {
      value: '1',
      writable: true,
      configurable: true,
    })

    runConsentScript()

    expect(testWindow.__PT_DNT).toBe(true)
  })

  it('detects Do Not Track via navigator.doNotTrack === "yes"', () => {
    Object.defineProperty(navigator, 'doNotTrack', {
      value: 'yes',
      writable: true,
      configurable: true,
    })

    runConsentScript()

    expect(testWindow.__PT_DNT).toBe(true)
  })

  it('detects Do Not Track via the legacy window.doNotTrack fallback', () => {
    ;testWindow.doNotTrack = '1'

    runConsentScript()

    expect(testWindow.__PT_DNT).toBe(true)
  })

  it('detects Global Privacy Control via navigator.globalPrivacyControl', () => {
    Object.defineProperty(navigator, 'globalPrivacyControl', {
      value: true,
      writable: true,
      configurable: true,
    })

    runConsentScript()

    expect(testWindow.__PT_DNT).toBe(true)
  })

  it('a privacy signal does not override an explicit stored consent choice', () => {
    Object.defineProperty(navigator, 'doNotTrack', {
      value: '1',
      writable: true,
      configurable: true,
    })
    localStorage.setItem(
      CONSENT_KEY,
      JSON.stringify({
        consentVersion: 1,
        policyVersion: '2026-07-23',
        timestamp: '2026-07-23T10:00:00.000Z',
        region: 'EU',
        categories: { necessary: true, analytics: true },
        dnt: true,
        source: 'banner',
      })
    )

    runConsentScript()

    expect(testWindow.__PT_DNT).toBe(true)
    expect(testWindow[ANALYTICS_FLAG]).toBe(true)
  })

  it('dispatches a "consentReady" event with the resolved analytics and dnt state', () => {
    localStorage.setItem(
      CONSENT_KEY,
      JSON.stringify({
        consentVersion: 1,
        policyVersion: '2026-07-23',
        timestamp: '2026-07-23T10:00:00.000Z',
        region: 'EU',
        categories: { necessary: true, analytics: true },
        dnt: false,
        source: 'banner',
      })
    )

    const listener = vi.fn()
    window.addEventListener('consentReady', listener)

    runConsentScript()

    expect(listener).toHaveBeenCalledTimes(1)
    const event = listener.mock.calls[0][0] as CustomEvent
    expect(event.detail).toEqual({ analytics: true, dnt: false })

    window.removeEventListener('consentReady', listener)
  })
})