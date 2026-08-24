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
  new Function('AHREFS_ANALYTICS_KEY', match[1])(ahrefsAnalyticsKey)
}

const AHREFS_URL = 'https://analytics.ahrefs.com/analytics.js'

describe('Analytics conditional loader', () => {
  beforeEach(() => {
    document.head.innerHTML = ''
    delete window.__PT_CONSENT_ANALYTICS
  })

  it('injects the Ahrefs script into <head> when analytics consent is granted and a key is configured', () => {
    window.__PT_CONSENT_ANALYTICS = true

    runAnalyticsScript('test-key-123')

    const injected = document.head.querySelector<HTMLScriptElement>(
      `script[src="${AHREFS_URL}"]`
    )
    expect(injected).not.toBeNull()
    expect(injected?.type).toBe('text/partytown')
    expect(injected?.getAttribute('data-key')).toBe('test-key-123')
    expect(injected?.async).toBe(true)
  })

  it('does not inject the Ahrefs script when analytics consent is not granted', () => {
    window.__PT_CONSENT_ANALYTICS = false

    runAnalyticsScript('test-key-123')

    expect(document.head.querySelector(`script[src="${AHREFS_URL}"]`)).toBeNull()
  })

  it('does not inject the Ahrefs script when the consent flag is unset', () => {
    runAnalyticsScript('test-key-123')

    expect(document.head.querySelector(`script[src="${AHREFS_URL}"]`)).toBeNull()
  })

  it('does not inject the Ahrefs script when no analytics key is configured, even with consent', () => {
    window.__PT_CONSENT_ANALYTICS = true

    runAnalyticsScript(undefined)

    expect(document.head.querySelector(`script[src="${AHREFS_URL}"]`)).toBeNull()
  })

  it('does not inject the Ahrefs script when the analytics key is an empty string', () => {
    window.__PT_CONSENT_ANALYTICS = true

    runAnalyticsScript('')

    expect(document.head.querySelector(`script[src="${AHREFS_URL}"]`)).toBeNull()
  })
})
