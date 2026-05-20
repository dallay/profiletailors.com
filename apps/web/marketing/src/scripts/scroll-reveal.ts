// src/scripts/scroll-reveal.ts
// Adds .is-visible to elements tagged [data-animate-scroll] when they enter the viewport.
// CSS in global.css handles the actual transition.

export function initScrollReveal(): void {
  const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches

  if (prefersReduced) {
    // CSS already makes them visible via the media query — nothing to do.
    return
  }

  const targets = document.querySelectorAll<HTMLElement>('[data-animate-scroll]')

  if (targets.length === 0) return

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          observer.unobserve(entry.target) // one-shot: stop watching once visible
        }
      })
    },
    { threshold: 0.15 }
  )

  targets.forEach((el) => observer.observe(el))
}
