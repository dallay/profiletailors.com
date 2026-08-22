import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

/**
 * CookieSettingsLink.astro ships its logic as a plain, import-free <script>
 * block. This helper extracts that exact source and executes it in the
 * jsdom test environment so the tests exercise the real shipped code rather
 * than a re-implementation of it.
 */
function runCookieSettingsLinkScript(): void {
  const filePath = resolve(__dirname, './CookieSettingsLink.astro')
  const source = readFileSync(filePath, 'utf-8')
  const match = source.match(/<script>([\s\S]*?)<\/script>/)
  if (!match) {
    throw new Error('Could not find the <script> block in CookieSettingsLink.astro')
  }
  new Function(match[1])()
}

describe('CookieSettingsLink script', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  afterEach(() => {
    // Remove any shadowing own property added via defineProperty in a test
    // so the next test falls back to the real Document.prototype getter.
    delete (document as { readyState?: unknown }).readyState
  })

  it('reveals the hidden consent banner when the link is clicked', () => {
    document.body.innerHTML = `
      <button type="button" id="cookie-settings-link">Cookie settings</button>
      <div id="consent-banner" hidden></div>
    `

    runCookieSettingsLinkScript()

    const banner = document.getElementById('consent-banner') as HTMLElement
    expect(banner.hasAttribute('hidden')).toBe(true)

    document.getElementById('cookie-settings-link')?.dispatchEvent(new MouseEvent('click'))

    expect(banner.hasAttribute('hidden')).toBe(false)
  })

  it('does not throw when the consent banner is missing from the DOM', () => {
    document.body.innerHTML = `
      <button type="button" id="cookie-settings-link">Cookie settings</button>
    `

    runCookieSettingsLinkScript()

    expect(() =>
      document.getElementById('cookie-settings-link')?.dispatchEvent(new MouseEvent('click'))
    ).not.toThrow()
  })

  it('does not throw when the link itself is missing from the DOM', () => {
    document.body.innerHTML = ''

    expect(() => runCookieSettingsLinkScript()).not.toThrow()
  })

  it('waits for DOMContentLoaded before wiring the click handler when the document is still loading', () => {
    document.body.innerHTML = `
      <button type="button" id="cookie-settings-link">Cookie settings</button>
      <div id="consent-banner" hidden></div>
    `

    Object.defineProperty(document, 'readyState', {
      value: 'loading',
      configurable: true,
    })

    runCookieSettingsLinkScript()

    const banner = document.getElementById('consent-banner') as HTMLElement
    // Clicking before DOMContentLoaded fires must not wire up the handler yet.
    document.getElementById('cookie-settings-link')?.dispatchEvent(new MouseEvent('click'))
    expect(banner.hasAttribute('hidden')).toBe(true)

    document.dispatchEvent(new Event('DOMContentLoaded'))
    document.getElementById('cookie-settings-link')?.dispatchEvent(new MouseEvent('click'))
    expect(banner.hasAttribute('hidden')).toBe(false)
  })
})