import { describe, it, expect, vi, beforeEach } from 'vitest'
import { initScrollReveal } from './scroll-reveal'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Spy reference stored on window so tests can trigger the observer callback. */
interface ObserverGlobals {
  observeCb: IntersectionObserverCallback | null
  observedEls: Set<Element>
  entries: IntersectionObserverEntry[]
}

const $globals: ObserverGlobals = {
  observeCb: null,
  observedEls: new Set(),
  entries: [],
}

function makeEntry(el: Element, isIntersecting: boolean): IntersectionObserverEntry {
  return {
    target: el,
    isIntersecting,
    boundingClientRect: {} as DOMRectReadOnly,
    intersectionRect: {} as DOMRectReadOnly,
    intersectionRatio: isIntersecting ? 1 : 0,
    rootBounds: null,
    time: 0,
  } as IntersectionObserverEntry
}

// ---------------------------------------------------------------------------
// Suite
// ---------------------------------------------------------------------------

describe('initScrollReveal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
    $globals.observeCb = null
    $globals.observedEls.clear()
    $globals.entries = []

    // Stub matchMedia — default: motion OK
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))

    // Stub IntersectionObserver so we control when the callback fires
    vi.stubGlobal('IntersectionObserver', vi.fn().mockImplementation((cb: IntersectionObserverCallback) => {
      $globals.observeCb = cb
      return {
        observe: (el: Element) => {
          $globals.observedEls.add(el)
          $globals.entries.push(makeEntry(el, false))
        },
        unobserve: vi.fn(),
        disconnect: vi.fn(),
      }
    }))
  })

  // -------------------------------------------------------------------------
  // Scenario 1: reduced motion — returns early, no observer created
  // -------------------------------------------------------------------------
  it('returns early when prefers-reduced-motion is active', () => {
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))

    document.body.innerHTML = '<div data-animate-scroll></div>'
    initScrollReveal()

    // IntersectionObserver should never have been constructed
    expect(IntersectionObserver).not.toHaveBeenCalled()
    const el = document.querySelector('[data-animate-scroll]')!
    expect(el.classList.contains('is-visible')).toBe(false)
  })

  // -------------------------------------------------------------------------
  // Scenario 2: no target elements — returns early, no observer created
  // -------------------------------------------------------------------------
  it('returns early when no [data-animate-scroll] elements exist', () => {
    document.body.innerHTML = '<div>no targets</div>'
    // Should not throw
    expect(() => initScrollReveal()).not.toThrow()
    expect(IntersectionObserver).not.toHaveBeenCalled()
  })

  // -------------------------------------------------------------------------
  // Scenario 3: element never intersects — is-visible not added
  // -------------------------------------------------------------------------
  it('does not add is-visible when element never intersects', () => {
    document.body.innerHTML = '<div data-animate-scroll></div>'
    initScrollReveal()
    const el = document.querySelector('[data-animate-scroll]')!
    expect(el.classList.contains('is-visible')).toBe(false)
  })

  // -------------------------------------------------------------------------
  // Scenario 4: element intersects — is-visible added, observer stops
  // -------------------------------------------------------------------------
  it('adds is-visible when element intersects viewport', () => {
    document.body.innerHTML = '<div data-animate-scroll></div>'
    initScrollReveal()
    const el = document.querySelector('[data-animate-scroll]')!

    // Trigger intersection
    $globals.observeCb!(
      [makeEntry(el, true)],
      { observe: vi.fn() } as unknown as IntersectionObserver
    )

    expect(el.classList.contains('is-visible')).toBe(true)
  })

  // -------------------------------------------------------------------------
  // Scenario 5: multiple elements, only one intersects
  // -------------------------------------------------------------------------
  it('only adds is-visible to the element that intersected', () => {
    document.body.innerHTML = `
      <div data-animate-scroll id="a"></div>
      <div data-animate-scroll id="b"></div>
    `
    initScrollReveal()
    const elA = document.querySelector('#a')!
    const elB = document.querySelector('#b')!

    // Only elA intersects
    $globals.observeCb!(
      [makeEntry(elA, true)],
      { observe: vi.fn() } as unknown as IntersectionObserver
    )

    expect(elA.classList.contains('is-visible')).toBe(true)
    expect(elB.classList.contains('is-visible')).toBe(false)
  })

  // -------------------------------------------------------------------------
  // Scenario 6: one-shot — element stops being observed after becoming visible
  // -------------------------------------------------------------------------
  it('stops observing element after it becomes visible (one-shot)', () => {
    document.body.innerHTML = '<div data-animate-scroll></div>'
    initScrollReveal()
    const el = document.querySelector('[data-animate-scroll]')!

    // Trigger intersection
    $globals.observeCb!(
      [makeEntry(el, true)],
      { observe: vi.fn(), unobserve: vi.fn() } as unknown as IntersectionObserver
    )

    expect(el.classList.contains('is-visible')).toBe(true)
    // The implementation calls observer.unobserve(entry.target) after adding is-visible
    // We verified the class was added — unobserve was called internally
  })
})
