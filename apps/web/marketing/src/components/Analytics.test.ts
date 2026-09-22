import { describe, it, expect, beforeEach } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

declare global {
  interface Window {
    __PT_CONSENT_ANALYTICS?: boolean
  }
}

const __dirname = dirname(fileURLToPath(import.meta.url))

/**
 * Analytics.astro ships its conditional-load logic as an inline script that
 * receives `AHREFS_ANALYTICS_KEY` via Astro's `define:vars`. This helper
 * extracts that exact source and executes it in the jsdom test environment,
 * passing the key in as a function parameter to emulate `define:vars`
 * injection, so the tests exercise the real shipped code.
 */
function runAnalyticsScript(ahrefsAnalyticsKey: string | undefined): void {
  const filePath = resolve(__dirname, './Analytics.astro')
  const source = readFileSync(filePath, 'utf-8')
  const match = source.match(/<script[^>]*>([\s\S]*?)<\/script>/)
  if (!match) {
    throw new Error('Could not find the <script> block in Analytics.astro')
  }
  new Function(
    'AHREFS_ANALYTICS_KEY',
    'CONSENT_KEY',
    'CURRENT_POLICY_VERSION',
    match[1]
  )(ahrefsAnalyticsKey, 'pt-consent', '2026-07-23')
}

const AHREFS_URL = 'https://analytics.ahrefs.com/analytics.js'
const CONSENT_KEY = 'pt-consent'

function storeAnalyticsReceipt(analytics: boolean): void {
  localStorage.setItem(
    CONSENT_KEY,
    JSON.stringify({
      consentVersion: 1,
      policyVersion: '2026-07-23',
      timestamp: '2026-07-23T10:00:00.000Z',
      region: 'EU',
      categories: { necessary: true, analytics },
      dnt: false,
      source: 'banner',
  it('does not inject the Ahrefs script when analytics consent is denied', () => {
    storeAnalyticsReceipt(false)

    runAnalyticsScript('test-key-123')

    const injected = document.head.querySelector<HTMLScriptElement>(
      `script[src="${AHREFS_URL}"]`
    )
    expect(injected).toBeNull()
  })

  it('does not inject the Ahrefs script when consent is granted but key is missing', () => {
    storeAnalyticsReceipt(true)

    runAnalyticsScript(undefined)

    const injected = document.head.querySelector<HTMLScriptElement>(
      `script[src="${AHREFS_URL}"]`
    )
    expect(injected).toBeNull()
  })

  it('does not inject the Ahrefs script when analytics consent is absent', () => {
    runAnalyticsScript('test-key-123')

    const injected = document.head.querySelector<HTMLScriptElement>(
      `script[src="${AHREFS_URL}"]`
    )
    expect(injected).toBeNull()
  })

  it('does not inject the Ahrefs script when consent receipt has wrong policy version', () => {
    localStorage.setItem(
      CONSENT_KEY,
      JSON.stringify({
        consentVersion: 1,
        policyVersion: '2025-01-01',
        timestamp: '2025-01-01T10:00:00.000Z',
        region: 'EU',
        categories: { necessary: true, analytics: true },
        dnt: false,
        source: 'banner',
      })
    )

    runAnalyticsScript('test-key-123')

    const injected = document.head.querySelector<HTMLScriptElement>(
      `script[src="${AHREFS_URL}"]`
    )
    expect(injected).toBeNull()
  })

  it('prevents stale receipts with wrong policy version from enabling analytics', () => {
    localStorage.setItem(
      CONSENT_KEY,
      JSON.stringify({
        consentVersion: 1,
        policyVersion: '2024-01-01',
        timestamp: '2024-01-01T10:00:00.000Z',
        region: 'EU',
        categories: { necessary: true, analytics: true },
        dnt: false,
        source: 'banner',
      })
    )

    runAnalyticsScript('test-key-123')

    const injected = document.head.querySelector<HTMLScriptElement>(
      `script[src="${AHREFS_URL}"]`
    )
    expect(injected).toBeNull()
  })

  it('does not inject the Ahrefs script when DNT is enabled without consent', () => {
    Object.defineProperty(global.navigator, 'doNotTrack', {
      value: '1',
      writable: true,
      configurable: true,
    })

    runAnalyticsScript('test-key-123')

    const injected = document.head.querySelector<HTMLScriptElement>(
      `script[src="${AHREFS_URL}"]`
    )
    expect(injected).toBeNull()
  })
})
  )
}

describe('Analytics conditional loader', () => {
  beforeEach(() => {
    document.head.innerHTML = ''
    localStorage.removeItem(CONSENT_KEY)
  })

  it('injects the Ahrefs script into <head> when analytics consent is granted via localStorage and a key is configured', () => {
    storeAnalyticsReceipt(true)

    runAnalyticsScript('test-key-123')

    const injected = document.head.querySelector<HTMLScriptElement>(
      `script[src="${AHREFS_URL}"]`
    )
    expect(injected).not.toBeNull()
    expect(injected?.type).toBe('text/partytown')
    expect(injected?.getAttribute('data-key')).toBe('test-key-123')
    expect(injected?.async).toBe(true)
  })

  it('does not inject the Ahrefs script when no analytics consent exists in localStorage', () => {
    runAnalyticsScript('test-key-123')

    expect(document.head.querySelector(`script[src="${AHREFS_URL}"]`)).toBeNull()
  })

  it('does not inject the Ahrefs script when localStorage has a receipt without analytics category', () => {
    storeAnalyticsReceipt(false)

    runAnalyticsScript('test-key-123')

    expect(document.head.querySelector(`script[src="${AHREFS_URL}"]`)).toBeNull()
  })

  it('does not inject the Ahrefs script when no analytics key is configured, even with consent', () => {
    storeAnalyticsReceipt(true)

    runAnalyticsScript(undefined)

    expect(document.head.querySelector(`script[src="${AHREFS_URL}"]`)).toBeNull()
  })

  it('does not inject the Ahrefs script when the analytics key is an empty string', () => {
    storeAnalyticsReceipt(true)

    runAnalyticsScript('')

    expect(document.head.querySelector(`script[src="${AHREFS_URL}"]`)).toBeNull()
  })

  it('refuses to grant analytics consent if no receipt exists in localStorage', () => {
    localStorage.removeItem(CONSENT_KEY)

    runAnalyticsScript('test-key-123')

    expect(document.head.querySelector(`script[src="${AHREFS_URL}"]`)).toBeNull()
  })

  it('grants analytics consent only when localStorage holds a valid analytics receipt', () => {
    storeAnalyticsReceipt(true)

    runAnalyticsScript('test-key-123')

    expect(document.head.querySelector(`script[src="${AHREFS_URL}"]`)).not.toBeNull()
  })

  it('denies analytics consent when localStorage holds a receipt without analytics category', () => {
    storeAnalyticsReceipt(false)

    runAnalyticsScript('test-key-123')

    expect(document.head.querySelector(`script[src="${AHREFS_URL}"]`)).toBeNull()
  })
})
