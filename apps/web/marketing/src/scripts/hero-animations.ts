const EASING_SPRING = 'cubic-bezier(0.22, 1, 0.36, 1)'
const EASING_EASE = 'ease'

function commitAndCancel(anim: Animation, el: HTMLElement): void {
  try {
    anim.commitStyles()
  } catch {
  }
  anim.cancel()
  el.style.opacity = '1'
  el.style.transform = 'none'
  el.style.filter = 'none'
  el.style.willChange = 'auto'
}

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

function snapVisible(el: HTMLElement): void {
  el.style.opacity = '1'
  el.style.transform = 'none'
  el.style.filter = 'none'
}

async function animateLabel(el: HTMLElement, delayMs: number): Promise<void> {
  el.style.opacity = '0'
  const anim = el.animate(
    [
      { opacity: 0, transform: 'scale(0.96)' },
      { opacity: 1, transform: 'scale(1)' },
    ],
    { delay: delayMs, duration: 240, easing: EASING_SPRING, fill: 'forwards' }
  )
  await anim.finished
  commitAndCancel(anim, el)
}

function animateHeadline(el: HTMLElement, delayMs: number): Promise<void> {
  const chars = splitToChars(el)
  const Y_TRAVEL = 9

  el.style.opacity = '1'

  const pairs = chars.map((span, rank) => {
    span.style.opacity = '0'
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

  const maxDelay = delayMs + (chars.length - 1) * 18 + 648 + 200

  const raceTimeout = new Promise<void>((resolve) =>
    setTimeout(() => {
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

function animateSub(el: HTMLElement, delayMs: number): Promise<void> {
  const chars = splitToChars(el)

  el.style.opacity = '1'

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
    if (chars.length === 0) resolve()
  })
}

async function animateFade(el: HTMLElement, delayMs: number): Promise<void> {
  el.style.opacity = '0'
  const anim = el.animate(
    [
      { opacity: 0, transform: 'translateY(8px)' },
      { opacity: 1, transform: 'translateY(0)' },
    ],
    { delay: delayMs, duration: 400, easing: EASING_EASE, fill: 'forwards' }
  )
  await anim.finished
  commitAndCancel(anim, el)
}

export async function initHeroAnimations(): Promise<void> {
  const prefersReduced = globalThis.matchMedia('(prefers-reduced-motion: reduce)').matches

  const label = document.querySelector<HTMLElement>('[data-hero-label]')
  const headline = document.querySelector<HTMLElement>('[data-hero-headline]')
  const sub = document.querySelector<HTMLElement>('[data-hero-sub]')
  const form = document.querySelector<HTMLElement>('[data-hero-form]')

  if (!label || !headline || !sub) return

  if (prefersReduced) {
    ;[label, headline, sub, form].forEach((el) => el && snapVisible(el))
    return
  }

  await animateLabel(label, 0)

  await animateHeadline(headline, 0)

  const subDelay = 100
  const subAnimPromise = animateSub(sub, subDelay)

  if (form) animateFade(form, subDelay + 160)

  await subAnimPromise
}
