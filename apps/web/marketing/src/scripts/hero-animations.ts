// src/scripts/hero-animations.ts
// One-shot WAAPI animation sequence for the Hero section on page load.
// Effects: micro-scale-fade (label), soft-blur-in (headline), typewriter (sub), plain fade (icons+form).

const EASING_SPRING = 'cubic-bezier(0.22, 1, 0.36, 1)'
const EASING_EASE = 'ease'

// Commit the animation's final state to inline styles, then cancel the effect.
// Without this, fill:'forwards' keeps the WAAPI effect in the cascade at a higher layer
// than inline styles, so setting el.style.opacity = '1' after finished has no effect.
// After cancel we always force the final state explicitly — Safari does not always flush
// commitStyles() reliably, leaving blur residue or clipped characters.
function commitAndCancel(anim: Animation, el: HTMLElement): void {
  try {
    anim.commitStyles()
  } catch {
    // intentionally ignored — we force state below
  }
  anim.cancel()
  // Force final visible state regardless of what commitStyles() did.
  // This is the Safari-safe fallback: explicit inline styles always win.
  el.style.opacity = '1'
  el.style.transform = 'none'
  el.style.filter = 'none'
  el.style.willChange = 'auto'
}

// Split text into per-character spans, turning \n into <br> elements.
// Returns only the animatable spans (not <br> nodes).
export function splitToChars(el: HTMLElement): HTMLSpanElement[] {
  const text = el.textContent ?? ''
  el.textContent = ''
  const spans: HTMLSpanElement[] = []

  Array.from(text).forEach((char) => {
    if (char === '\n') {
      el.appendChild(document.createElement('br'))
      return
    }
    const span = document.createElement('span')
    span.textContent = char
    span.style.display = 'inline-block'
    span.style.whiteSpace = 'pre'
    span.style.backfaceVisibility = 'hidden'
    el.appendChild(span)
    spans.push(span)
  })

  return spans
}

// Snap an element to its final visible state without animation (for reduced-motion).
function snapVisible(el: HTMLElement): void {
  el.style.opacity = '1'
  el.style.transform = 'none'
  el.style.filter = 'none'
}

// Animate label: whole element, micro-scale + fade, 240ms one-shot.
function animateLabel(el: HTMLElement, delayMs: number): Promise<void> {
  el.style.opacity = '0'
  const anim = el.animate(
    [
      { opacity: 0, transform: 'scale(0.96)' },
      { opacity: 1, transform: 'scale(1)' },
    ],
    { delay: delayMs, duration: 240, easing: EASING_SPRING, fill: 'forwards' }
  )
  return anim.finished.then(() => commitAndCancel(anim, el))
}

// Animate headline: soft-blur-in per-character.
function animateHeadline(el: HTMLElement, delayMs: number): Promise<void> {
  const chars = splitToChars(el)
  const Y_TRAVEL = 9 // px

  el.style.opacity = '1' // container visible; chars handle their own opacity

  const pairs = chars.map((span, rank) => {
    span.style.opacity = '0'
    // will-change hints Safari to create a compositing layer per char,
    // preventing blur residue and clipping at animation end.
    span.style.willChange = 'transform, opacity, filter'
    const anim = span.animate(
      [
        { opacity: 0, transform: `translate3d(0, ${Y_TRAVEL}px, 0)`, filter: 'blur(12px)' },
        { opacity: 1, transform: 'translate3d(0, 0, 0)', filter: 'blur(0px)' },
      ],
      {
        delay: delayMs + rank * 18,
        duration: 648,
        easing: EASING_SPRING,
        fill: 'forwards',
      }
    )
    return { anim, span }
  })

  const maxDelay = delayMs + (chars.length - 1) * 18 + 648 + 200 // generous safety margin

  const raceTimeout = new Promise<void>((resolve) =>
    setTimeout(() => {
      // Safety net: if Safari never resolves anim.finished, force final state and bail.
      pairs.forEach(({ anim, span }) => commitAndCancel(anim, span))
      resolve()
    }, maxDelay)
  )

  return Promise.race([
    Promise.all(pairs.map(({ anim }) => anim.finished)).then(() => {
      pairs.forEach(({ anim, span }) => commitAndCancel(anim, span))
    }),
    raceTimeout,
  ])
}

// Animate sub: typewriter per-character.
// Uses a simple setTimeout-based reveal instead of WAAPI steps() to avoid
// commitStyles() timing issues with step easing functions.
function animateSub(el: HTMLElement, delayMs: number): Promise<void> {
  const chars = splitToChars(el)

  el.style.opacity = '1' // container visible; chars handle opacity

  chars.forEach((span) => {
    span.style.opacity = '0'
  })

  return new Promise<void>((resolve) => {
    chars.forEach((span, rank) => {
      setTimeout(
        () => {
          span.style.opacity = '1'
          if (rank === chars.length - 1) resolve()
        },
        delayMs + rank * 33
      )
    })
    // Fallback resolve if chars is empty
    if (chars.length === 0) resolve()
  })
}

// Animate a generic element: plain fade + translateY.
function animateFade(el: HTMLElement, delayMs: number): Promise<void> {
  el.style.opacity = '0'
  const anim = el.animate(
    [
      { opacity: 0, transform: 'translateY(8px)' },
      { opacity: 1, transform: 'translateY(0)' },
    ],
    { delay: delayMs, duration: 400, easing: EASING_EASE, fill: 'forwards' }
  )
  return anim.finished.then(() => commitAndCancel(anim, el))
}

export async function initHeroAnimations(): Promise<void> {
  const prefersReduced = globalThis.matchMedia('(prefers-reduced-motion: reduce)').matches

  const label = document.querySelector<HTMLElement>('[data-hero-label]')
  const headline = document.querySelector<HTMLElement>('[data-hero-headline]')
  const sub = document.querySelector<HTMLElement>('[data-hero-sub]')
  const icons = document.querySelector<HTMLElement>('[data-hero-icons]')
  const form = document.querySelector<HTMLElement>('[data-hero-form]')

  // Guard: if Hero isn't on this page, exit early.
  if (!label || !headline || !sub) return

  if (prefersReduced) {
    ;[label, headline, sub, icons, form].forEach((el) => el && snapVisible(el))
    return
  }

  // t=0: label
  await animateLabel(label, 0)

  // t=after label (~240ms): headline
  await animateHeadline(headline, 0)

  // t=after headline: sub (with 100ms gap) + icons/form fire concurrently
  const subDelay = 100
  const subAnimPromise = animateSub(sub, subDelay)

  if (icons) animateFade(icons, subDelay + 80)
  if (form) animateFade(form, subDelay + 160)

  await subAnimPromise
}
