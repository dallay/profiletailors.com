export function initScrollReveal(): void {
  const prefersReduced = globalThis.matchMedia('(prefers-reduced-motion: reduce)').matches

  if (prefersReduced) {
    return
  }

  const targets = document.querySelectorAll<HTMLElement>('[data-animate-scroll]')

  if (targets.length === 0) return

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          observer.unobserve(entry.target)
        }
      })
    },
    { threshold: 0.15 }
  )

  targets.forEach((el) => observer.observe(el))
}
