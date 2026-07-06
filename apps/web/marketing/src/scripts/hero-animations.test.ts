import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { splitToChars, initHeroAnimations } from './hero-animations'

// ---------------------------------------------------------------------------
// Tier1: splitToChars — pure unit tests
// ---------------------------------------------------------------------------

describe('splitToChars', () => {
  it('returns empty array when textContent is empty', () => {
    const el = document.createElement('div')
    el.textContent = ''
    const result = splitToChars(el)
    expect(result).toHaveLength(0)
  })

  it('creates one span per character for simple text', () => {
    const el = document.createElement('div')
    el.textContent = 'hi'
    const result = splitToChars(el)
    expect(result).toHaveLength(2)
    expect(result[0].textContent).toBe('h')
    expect(result[1].textContent).toBe('i')
  })

  it('converts newline to br element instead of span', () => {
    const el = document.createElement('div')
    el.textContent = 'hello\nworld'
    const result = splitToChars(el)
    const brs = Array.from(el.childNodes).filter((n) => n.nodeName === 'BR')
    expect(brs).toHaveLength(1)
    expect(result).toHaveLength(10)
  })

  it('handles single character', () => {
    const el = document.createElement('div')
    el.textContent = 'x'
    const result = splitToChars(el)
    expect(result).toHaveLength(1)
    expect(result[0].textContent).toBe('x')
  })
})

// ---------------------------------------------------------------------------
// Tier 2: initHeroAnimations — integration tests
// ---------------------------------------------------------------------------

/** WAAPI mock shared across all element instances. */
const waapiMock = vi.fn().mockReturnValue({
  finished: Promise.resolve(),
  cancel: vi.fn(),
  commitStyles: vi.fn(),
})

/** Shared original querySelector for centralized cleanup. */
const origQuerySelector = document.querySelector.bind(document)

describe('initHeroAnimations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    document.body.innerHTML = ''

    // Stub WAAPI globally — jsdom has no native element.animate()
    // Using Object.defineProperty to avoid TypeScript error on non-existent property
    Object.defineProperty(Element.prototype, 'animate', {
      value: waapiMock,
      writable: true,
      configurable: true,
    })

    vi.stubGlobal(
      'matchMedia',
      vi.fn().mockReturnValue({
        matches: false,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      }),
    )
  })

  afterEach(() => {
    vi.useRealTimers()
    // Restore native animate if it existed (it doesn't in jsdom)
    // biome-ignore lint/suspicious/noExplicitAny: jsdom lacks Element.animate; we must delete the injected mock
    delete (Element.prototype as any).animate
    // Restore document.querySelector to guarantee cleanup
    document.querySelector = origQuerySelector
  })

  // -------------------------------------------------------------------------
  // Scenario 1: reduced motion — snapVisible called on all elements
  // -------------------------------------------------------------------------
  it('calls snapVisible on all hero elements when prefers-reduced-motion is active', () => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn().mockReturnValue({
        matches: true,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      }),
    )

    const label = document.createElement('div')
    const headline = document.createElement('div')
    const sub = document.createElement('div')
    const icons = document.createElement('div')
    const form = document.createElement('div')

    document.querySelector = vi.fn().mockImplementation((selector: string) => {
      switch (selector) {
        case '[data-hero-label]':
          return label
        case '[data-hero-headline]':
          return headline
        case '[data-hero-sub]':
          return sub
        case '[data-hero-icons]':
          return icons
        case '[data-hero-form]':
          return form
        default:
          return null
      }
    })

    initHeroAnimations()

    // snapVisible sets opacity/transform/filter to final values
    expect(label.style.opacity).toBe('1')
    expect(label.style.transform).toBe('none')
    expect(headline.style.opacity).toBe('1')
    expect(sub.style.opacity).toBe('1')
    expect(icons.style.opacity).toBe('1')
    expect(form.style.opacity).toBe('1')
  })

  // -------------------------------------------------------------------------
  // Scenario 2: no hero-label in DOM — returns early
  // -------------------------------------------------------------------------
  it('returns early when no [data-hero-label] exists', () => {
    document.body.innerHTML = '<div>no hero</div>'
    expect(() => initHeroAnimations()).not.toThrow()
  })

  // -------------------------------------------------------------------------
  // Scenario 3: hero elements exist, motion OK — animate called
  // -------------------------------------------------------------------------
  it('calls element.animate when hero elements are present and motion is allowed', async () => {
    const label = document.createElement('div')
    label.textContent = 'Label'
    const headline = document.createElement('div')
    headline.textContent = 'Headline'
    const sub = document.createElement('div')
    sub.textContent = 'Sub'

    document.querySelector = vi.fn().mockImplementation((selector: string) => {
      switch (selector) {
        case '[data-hero-label]':
          return label
        case '[data-hero-headline]':
          return headline
        case '[data-hero-sub]':
          return sub
        case '[data-hero-icons]':
          return null
        case '[data-hero-form]':
          return null
        default:
          return null
      }
    })

    const animPromise = initHeroAnimations()
    await vi.runAllTimersAsync()
    await animPromise

    // animate is called on label (animateLabel) and headline (animateHeadline)
    // The global waapiMock spy tracks all calls
    expect(waapiMock).toHaveBeenCalled()
  })

  // -------------------------------------------------------------------------
  // Scenario 4: icons/form are null — graceful handling
  // -------------------------------------------------------------------------
  it('does not throw when icons or form are null', async () => {
    const label = document.createElement('div')
    label.textContent = 'Label'
    const headline = document.createElement('div')
    headline.textContent = 'Headline'
    const sub = document.createElement('div')
    sub.textContent = 'Sub'

    document.querySelector = vi.fn().mockImplementation((selector: string) => {
      switch (selector) {
        case '[data-hero-label]':
          return label
        case '[data-hero-headline]':
          return headline
        case '[data-hero-sub]':
          return sub
        case '[data-hero-icons]':
          return null
        case '[data-hero-form]':
          return null
        default:
          return null
      }
    })

    const animPromise = initHeroAnimations()
    await vi.runAllTimersAsync()
    await animPromise

    expect(waapiMock).toHaveBeenCalled()
  })
})
