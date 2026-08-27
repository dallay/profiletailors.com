import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { CURRENT_CONSENT_VERSION, CURRENT_POLICY_VERSION, PT_CONSENT_KEY } from '../../constants/consent'

declare global {
  interface Window {
    __PT_DNT?: boolean
    __consentReload: () => void
  }
}

const __dirname = dirname(fileURLToPath(import.meta.url))

function readConsentBannerSource(): string {
  return readFileSync(resolve(__dirname, './ConsentBanner.astro'), 'utf-8')
}

/**
 * ConsentBanner.astro's client `<script>` block uses a TypeScript value
 * import (for the constants module) and a type-only import (for
 * `ConsentReceipt`), plus a handful of `as`/`:` type annotations. None of
 * these have any runtime effect, so this helper extracts the exact shipped
 * source, strips the TypeScript-only syntax, inlines the real constant
 * values, and executes the result in the jsdom test environment. This way
 * the tests exercise the real shipped logic rather than a re-implementation
 * of it.
 */
function extractConsentBannerScript(): string {
  const source = readConsentBannerSource()
  const scriptBlocks = [...source.matchAll(/<script>([\s\S]*?)<\/script>/g)]
  const clientScript = scriptBlocks.find(([, body]) => body.includes('initConsentBanner'))
  if (!clientScript) {
    throw new Error('Could not find the client <script> block in ConsentBanner.astro')
  }

  let code = clientScript[1]

  // Type-only import has no runtime effect — drop it entirely.
  code = code.replace(/^\s*import type[^\n]*\n/m, '')

  // Replace the value import from constants/consent with inline constant values.
  code = code.replace(
    /^\s*import\s*\{[^}]*\}\s*from\s*['"]\.\.\/\.\.\/constants\/consent['"];?\s*\n/m,
    `const CURRENT_CONSENT_VERSION = ${JSON.stringify(CURRENT_CONSENT_VERSION)}\n` +
      `const CURRENT_POLICY_VERSION = ${JSON.stringify(CURRENT_POLICY_VERSION)}\n` +
      `const PT_CONSENT_KEY = ${JSON.stringify(PT_CONSENT_KEY)}\n`
  )

  // Replace the runtime import from @profiletailors/shared-web with a minimal
  // mock that passes through valid (parseable) objects, just like the real
  // validateConsentReceipt does.
  code = code.replace(
    /^\s*import\s*\{[^}]*\}\s*from\s*['"]@profiletailors\/shared-web['"];?\s*\n/m,
    `const validateConsentReceipt = (receipt) => receipt\n`
  )

  // Strip TypeScript annotations from the browser script.
  code = code
    .replaceAll(' as HTMLButtonElement', '')
    .replaceAll(' as HTMLElement', '')
    .replace('function setAnalyticsToggle(enabled: boolean)', 'function setAnalyticsToggle(enabled)')
    .replace('function setCustomizeOpen(open: boolean)', 'function setCustomizeOpen(open)')
    .replace('function saveConsentChoice(analytics: boolean) {', 'function saveConsentChoice(analytics) {')
    .replace('function loadConsent(): ConsentReceipt | null {', 'function loadConsent() {')
    .replace('const receipt: ConsentReceipt = {', 'const receipt = {')

  // jsdom's window.location.reload is non-configurable — replace the call
  // with an indirection so the test can mock it without fighting the host
  // object.
  code = code.replace('window.location.reload()', 'window.__consentReload()')

  if (/:\s*(HTMLButtonElement|HTMLElement|ConsentReceipt|boolean|KeyboardEvent)\b/.test(code) || /\bimport\b/.test(code)) {
    throw new Error(
      'ConsentBanner.astro script extraction left unstripped TypeScript syntax — update the stripping rules in ConsentBanner.test.ts to match the current source.'
    )
  }

  return code
}

function runConsentBannerScript(): void {
  new Function(extractConsentBannerScript())()
}

/** Renders the real markup structure the script queries against. */
function renderBannerFixture(): void {
  document.body.innerHTML = `
    <aside id="consent-banner" aria-labelledby="consent-heading" aria-describedby="consent-description" hidden>
      <button type="button" data-consent-reject-all class="consent-button">Reject all</button>
      <button type="button" data-consent-customize aria-expanded="false" class="consent-button">Customize</button>
      <button type="button" data-consent-accept-all class="consent-button">Accept all</button>
      <div id="consent-customize-panel" data-consent-customize-panel hidden>
        <button type="button" role="switch" aria-checked="false" data-consent-analytics class="consent-toggle"></button>
        <button type="button" data-consent-back class="consent-button">Back</button>
        <button type="button" data-consent-save class="consent-button">Save preferences</button>
      </div>
    </aside>
  `
}

describe('ConsentBanner client script', () => {
  let reloadSpy: { called: boolean; callCount: number }

  beforeEach(() => {
    localStorage.clear()
    delete window.__PT_DNT
    // The extracted script calls window.__consentReload() instead of
    // window.location.reload() because jsdom's location.reload is
    // non-configurable and can't be mocked.
    reloadSpy = { called: false, callCount: 0 }
    window.__consentReload = (): void => {
      reloadSpy.called = true
      reloadSpy.callCount++
    }
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders a visible non-modal banner without a blocking overlay or focus trap', () => {
    const source = readConsentBannerSource()

    expect(source).toContain('<aside')
    expect(source).not.toContain('aria-modal')
    expect(source).not.toContain('consent-backdrop')
    expect(source).not.toContain('backdrop-filter')
    expect(source).not.toContain('box-shadow')
    expect(source).not.toContain('activateFocusTrap')
    expect(source).toContain('bottom: 0;')
  })

  it('shows the banner without forcing focus when there is no stored consent', () => {
    renderBannerFixture()
    window.__PT_DNT = false

    runConsentBannerScript()

    const banner = document.getElementById('consent-banner') as HTMLElement

    expect(banner.hasAttribute('hidden')).toBe(false)
    expect(document.activeElement).not.toBe(banner)
    expect(banner.querySelector('[data-consent-customize-panel]')?.hasAttribute('hidden')).toBe(true)
  })

  it('defaults analytics OFF in the customize panel when a DNT/GPC signal is present', () => {
    renderBannerFixture()
    window.__PT_DNT = true

    runConsentBannerScript()

    const banner = document.getElementById('consent-banner') as HTMLElement
    banner.querySelector('[data-consent-customize]')?.dispatchEvent(new MouseEvent('click'))
    const toggle = banner.querySelector('[data-consent-analytics]') as HTMLElement

    expect(banner.hasAttribute('hidden')).toBe(false)
    expect(toggle.getAttribute('aria-checked')).toBe('false')
    expect(toggle.classList.contains('consent-toggle--on')).toBe(false)
  })

  it('keeps the banner hidden when valid consent already exists in localStorage', () => {
    renderBannerFixture()
    localStorage.setItem(
      PT_CONSENT_KEY,
      JSON.stringify({
        consentVersion: CURRENT_CONSENT_VERSION,
        policyVersion: CURRENT_POLICY_VERSION,
        timestamp: '2026-07-23T10:00:00.000Z',
        region: 'EU',
        categories: { necessary: true, analytics: false },
        dnt: false,
        source: 'banner',
      })
    )

    runConsentBannerScript()

    const banner = document.getElementById('consent-banner') as HTMLElement
    expect(banner.hasAttribute('hidden')).toBe(true)
  })

  it('toggles aria-checked and the "on" class when the analytics switch is clicked', () => {
    renderBannerFixture()
    window.__PT_DNT = false

    runConsentBannerScript()

    document.querySelector('[data-consent-customize]')?.dispatchEvent(new MouseEvent('click'))
    const toggle = document.querySelector('[data-consent-analytics]') as HTMLElement
    expect(toggle.getAttribute('aria-checked')).toBe('true')

    toggle.dispatchEvent(new MouseEvent('click'))
    expect(toggle.getAttribute('aria-checked')).toBe('false')
    expect(toggle.classList.contains('consent-toggle--on')).toBe(false)

    toggle.dispatchEvent(new MouseEvent('click'))
    expect(toggle.getAttribute('aria-checked')).toBe('true')
    expect(toggle.classList.contains('consent-toggle--on')).toBe(true)
  })

  it('saves a receipt reflecting the current toggle state, reloads, and hides the banner when "Save preferences" is clicked', () => {
    renderBannerFixture()
    window.__PT_DNT = false

    runConsentBannerScript()

    document.querySelector('[data-consent-customize]')?.dispatchEvent(new MouseEvent('click'))
    const toggle = document.querySelector('[data-consent-analytics]') as HTMLElement
    toggle.dispatchEvent(new MouseEvent('click'))
    expect(toggle.getAttribute('aria-checked')).toBe('false')

    document.querySelector('[data-consent-save]')?.dispatchEvent(new MouseEvent('click'))

    const stored = JSON.parse(localStorage.getItem(PT_CONSENT_KEY) as string)
    expect(stored).toMatchObject({
      consentVersion: CURRENT_CONSENT_VERSION,
      policyVersion: CURRENT_POLICY_VERSION,
      region: 'EU',
      categories: { necessary: true, analytics: false },
      dnt: false,
      source: 'banner',
    })
    expect(typeof stored.timestamp).toBe('string')

    expect(reloadSpy.callCount).toBe(1)

    const banner = document.getElementById('consent-banner') as HTMLElement
    expect(banner.hasAttribute('hidden')).toBe(true)
  })

  it('opens the customize panel when the cookie settings event is dispatched', () => {
    renderBannerFixture()
    window.__PT_DNT = false

    runConsentBannerScript()

    const banner = document.getElementById('consent-banner') as HTMLElement
    banner.dispatchEvent(new CustomEvent('consent-open-settings'))

    expect(banner.hasAttribute('hidden')).toBe(false)
    expect(banner.querySelector('[data-consent-customize-panel]')?.hasAttribute('hidden')).toBe(false)
    expect(banner.querySelector('[data-consent-customize]')?.getAttribute('aria-expanded')).toBe('true')
  })

  it('records dnt=true on the saved receipt when a privacy signal was detected', () => {
    renderBannerFixture()
    window.__PT_DNT = true

    runConsentBannerScript()

    document.querySelector('[data-consent-save]')?.dispatchEvent(new MouseEvent('click'))

    const stored = JSON.parse(localStorage.getItem(PT_CONSENT_KEY) as string)
    expect(stored.dnt).toBe(true)
  })

  it('saves consent when clicking "Accept all" and "Reject all" buttons', () => {
    // The template renders `data-consent-accept-all` / `data-consent-reject-all`
    // and the script queries those exact same selectors.
    renderBannerFixture()
    window.__PT_DNT = false

    runConsentBannerScript()

    // Click "Accept all" — analytics should be true
    document.querySelector('[data-consent-accept-all]')?.dispatchEvent(new MouseEvent('click'))
    let stored = JSON.parse(localStorage.getItem(PT_CONSENT_KEY) as string)
    expect(stored.categories.analytics).toBe(true)
    expect(stored.source).toBe('banner')
    expect(reloadSpy.called).toBe(true)

    localStorage.clear()
    reloadSpy.called = false
    reloadSpy.callCount = 0

    // Click "Reject all" — analytics should be false
    document.querySelector('[data-consent-reject-all]')?.dispatchEvent(new MouseEvent('click'))
    stored = JSON.parse(localStorage.getItem(PT_CONSENT_KEY) as string)
    expect(stored.categories.analytics).toBe(false)
    expect(stored.source).toBe('banner')
    expect(reloadSpy.called).toBe(true)
  })

  it('does not throw when the #consent-banner element is missing from the DOM', () => {
    document.body.innerHTML = ''

    expect(() => runConsentBannerScript()).not.toThrow()
  })

  it('does not throw when localStorage contains invalid JSON', () => {
    renderBannerFixture()
    localStorage.setItem(PT_CONSENT_KEY, 'not-json{')

    expect(() => runConsentBannerScript()).not.toThrow()

    // Invalid JSON is treated as "no consent", so the banner should show.
    const banner = document.getElementById('consent-banner') as HTMLElement
    expect(banner.hasAttribute('hidden')).toBe(false)
  })
})
