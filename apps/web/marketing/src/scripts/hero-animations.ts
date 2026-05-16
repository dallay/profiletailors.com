// src/scripts/hero-animations.ts
// One-shot WAAPI animation sequence for the Hero section on page load.
// Effects: micro-scale-fade (label), soft-blur-in (headline), typewriter (sub), plain fade (icons+form).

const EASING_SPRING = 'cubic-bezier(0.22, 1, 0.36, 1)'
const EASING_EASE = 'ease'

// Split text into per-character spans. Spaces get a span but are skipped during animation.
function splitToChars(el: HTMLElement): HTMLSpanElement[] {
  const text = el.textContent ?? ''
  el.textContent = ''
  return Array.from(text).map((char) => {
    const span = document.createElement('span')
    span.textContent = char
    span.style.display = 'inline-block'
    span.style.whiteSpace = 'pre'
    span.style.backfaceVisibility = 'hidden'
    span.style.willChange = 'transform, opacity, filter'
    el.appendChild(span)
    return span
  })
}

// Snap an element to its final visible state without animation (for reduced-motion or direct reveal).
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
  return anim.finished.then(() => {
    el.style.opacity = '1'
    el.style.transform = 'none'
  })
}

// Animate headline: soft-blur-in per-character.
// Returns a Promise that resolves when all characters have finished entering.
function animateHeadline(el: HTMLElement, delayMs: number): Promise<void> {
  const chars = splitToChars(el)
  const animatedChars = chars.filter((s) => s.textContent !== ' ')
  const Y_TRAVEL = 16 * 0.58 // 9.28px

  const animations = animatedChars.map((span, rank) => {
    span.style.opacity = '0'
    return span.animate(
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
  })

  return Promise.all(animations.map((a) => a.finished)).then(() => {
    animatedChars.forEach((span) => {
      span.style.opacity = '1'
      span.style.transform = 'none'
      span.style.filter = 'none'
    })
  })
}

// Animate sub: typewriter per-character, steps(1, end).
function animateSub(el: HTMLElement, delayMs: number): Promise<void> {
  const chars = splitToChars(el)

  const animations = chars.map((span, rank) => {
    span.style.opacity = '0'
    return span.animate([{ opacity: 0 }, { opacity: 1 }], {
      delay: delayMs + rank * 33,
      duration: 173,
      easing: 'steps(1, end)',
      fill: 'forwards',
    })
  })

  return Promise.all(animations.map((a) => a.finished)).then(() => {
    chars.forEach((span) => {
      span.style.opacity = '1'
    })
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
  return anim.finished.then(() => {
    el.style.opacity = '1'
    el.style.transform = 'none'
  })
}

export async function initHeroAnimations(): Promise<void> {
  const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches

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

  // Hide all hero elements before sequencing
  ;[label, headline, sub, icons, form].forEach((el) => {
    if (el) el.style.opacity = '0'
  })

  // t=0: label
  await animateLabel(label, 0)

  // t=after label (~240ms): headline starts
  await animateHeadline(headline, 0)

  // t=after headline: sub starts (with 100ms gap)
  const subDelay = 100
  const subAnimPromise = animateSub(sub, subDelay)

  // icons and form fade in shortly after sub starts
  if (icons) animateFade(icons, subDelay + 80)
  if (form) animateFade(form, subDelay + 160)

  await subAnimPromise
}
