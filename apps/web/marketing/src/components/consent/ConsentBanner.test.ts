import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { CURRENT_CONSENT_VERSION, CURRENT_POLICY_VERSION, PT_CONSENT_KEY } from '../../constants/consent'

const __dirname = dirname(fileURLToPath(import.meta.url))
const testWindow = window as Window & Record<string, unknown>

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
  const filePath = resolve(__dirname, './ConsentBanner.astro')
  const source = readFileSync(filePath, 'utf-8')
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
    /^\s*import\s*\{[^}]*\}\s*from\s*['"]\.\.\/\.\.\/constants\/consent['"]\s*\n/m,
    `const CURRENT_CONSENT_VERSION = ${JSON.stringify(CURRENT_CONSENT_VERSION)}\n` +
      `const CURRENT_POLICY_VERSION = ${JSON.stringify(CURRENT_POLICY_VERSION)}\n` +
      `const PT_CONSENT_KEY = ${JSON.stringify(PT_CONSENT_KEY)}\n`
  )

  // Replace the runtime import from @profiletailors/shared-web with a minimal
  // mock that passes through valid (parseable) objects, just like the real
  // validateConsentReceipt does.
  code = code.replace(
    /^\s*import\s*\{[^}]*\}\s*from\s*['"]@profiletailors\/shared-web['"]\s*\n/m,
    `const validateConsentReceipt = (receipt) => receipt\n`
  )

  // Strip the small set of TypeScript-only annotations used in this file.
  code = code
    .replaceAll(' as HTMLButtonElement', '')
    .replace('function saveConsentChoice(analytics: boolean) {', 'function saveConsentChoice(analytics) {')
    .replace('function loadConsent(): ConsentReceipt | null {', 'function loadConsent() {')
    .replace('const receipt: ConsentReceipt = {', 'const receipt = {')

  // jsdom's window.location.reload is non-configurable — replace the call
  // with an indirection so the test can mock it without fighting the host
  // object.
  code = code.replace('window.location.reload()', 'window.__consentReload()')

  if (/:\s*(HTMLButtonElement|ConsentReceipt|boolean)\b/.test(code) || /\bimport\b/.test(code)) {
    throw new Error(
      'ConsentBanner.astro script extraction left unstripped TypeScript syntax — update the stripping rules in ConsentBanner.test.ts to match the current source.'
    )
  }

  return code
}

function runConsentBannerScript(): void {
  // eslint-disable-next-line no-new-func
  new Function(extractConsentBannerScript())()
}

/** Renders the real markup structure the script queries against. */
function renderBannerFixture(): void {
  document.body.innerHTML = `
    <div id="consent-banner" role="dialog" aria-labelledby="consent-heading" aria-modal="true" hidden>
      <button type="button" role="switch" aria-checked="false" data-consent-analytics class="consent-toggle"></button>
      <button type="button" data-consent-accept-all class="consent-button consent-button--primary">Accept all</button>
      <button type="button" data-consent-reject-all class="consent-button consent-button--secondary">Reject all</button>
      <button type="button" data-consent-save class="consent-button consent-button--secondary">Save preferences</button>
    </div>
  `
}

describe('ConsentBanner client script', () => {
  let reloadSpy: { called: boolean; callCount: number }

  beforeEach(() => {
    localStorage.clear()
    delete testWindow.__PT_DNT
    // The extracted script calls window.__consentReload() instead of
    // window.location.reload() because jsdom's location.reload is
    // non-configurable and can't be mocked.
    reloadSpy = { called: false, callCount: 0 }
    testWindow.__consentReload = () => {
      reloadSpy.called = true
      reloadSpy.callCount++
    }
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the banner and defaults the analytics toggle ON when there is no stored consent and no DNT signal', () => {
    renderBannerFixture()
    ;testWindow.__PT_DNT = false

    runConsentBannerScript()

    const banner = document.getElementById('consent-banner') as HTMLElement
    const toggle = banner.querySelector('[data-consent-analytics]') as HTMLElement

    expect(banner.hasAttribute('hidden')).toBe(false)
    expect(toggle.getAttribute('aria-checked')).toBe('true')
    expect(toggle.classList.contains('consent-toggle--on')).toBe(true)
  })

  it('shows the banner and defaults the analytics toggle OFF when a DNT/GPC signal is present', () => {
    renderBannerFixture()
    ;testWindow.__PT_DNT = true

    runConsentBannerScript()

    const banner = document.getElementById('consent-banner') as HTMLElement
    const toggle = banner.querySelector('[data-consent-analytics]') as HTMLElement

    expect(banner.hasAttribute('hidden')).toBe(false)
    expect(toggle.getAttribute('aria-checked')).toBe('false')
    expect(toggle.classList.contains('consent-toggle--on')).toBe(false)
  })

  it('keeps the banner hidden when any parseable consent value already exists in localStorage', () => {
    renderBannerFixture()
    // loadConsent() only checks for parseable JSON — it does not validate the
    // schema or consentVersion, so even a minimal/outdated object is treated
    // as "existing consent" and the banner stays hidden.
    localStorage.setItem(PT_CONSENT_KEY, JSON.stringify({ consentVersion: 0 }))

    runConsentBannerScript()

    const banner = document.getElementById('consent-banner') as HTMLElement
    expect(banner.hasAttribute('hidden')).toBe(true)
  })

  it('toggles aria-checked and the "on" class when the analytics switch is clicked', () => {
    renderBannerFixture()
    ;testWindow.__PT_DNT = false

    runConsentBannerScript()

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
    ;testWindow.__PT_DNT = false

    runConsentBannerScript()

    const toggle = document.querySelector('[data-consent-analytics]') as HTMLElement
    // Turn analytics OFF before saving.
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

  it('records dnt=true on the saved receipt when a privacy signal was detected', () => {
    renderBannerFixture()
    ;testWindow.__PT_DNT = true

    runConsentBannerScript()

    document.querySelector('[data-consent-save]')?.dispatchEvent(new MouseEvent('click'))

    const stored = JSON.parse(localStorage.getItem(PT_CONSENT_KEY) as string)
    expect(stored.dnt).toBe(true)
  })

  it('saves consent when clicking "Accept all" and "Reject all" buttons', () => {
    // The template renders `data-consent-accept-all` / `data-consent-reject-all`
    // and the script queries those exact same selectors.
    renderBannerFixture()
    ;testWindow.__PT_DNT = false

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
