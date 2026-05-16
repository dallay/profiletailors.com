# Text Animations — Landing Page Design Spec

**Date:** 2026-05-16  
**App:** `apps/web/marketing` (Astro 6, static)  
**Stack:** WAAPI (Web Animations API) — native, zero dependencies  

---

## Overview

Add purposeful text animations to the landing page in two layers:

1. **Hero on-load** — staggered sequence when the page first loads
2. **Scroll-driven** — fade-in reveal as sections enter the viewport

Motion is minimal and precise. Respects `prefers-reduced-motion`. No bounce, blur ornaments, or looping text swaps.

---

## Layer 1 — Hero On-Load Sequence

All effects are **one-shot** (play once on page load, no loops). Elements animate in sequence with overlapping stagger.

### Timing sequence

```
t=0ms     label        → micro-scale-fade (whole)
t=150ms   headline     → soft-blur-in (per-character)
t=~800ms  sub          → typewriter (per-character, starts after headline finishes)
t=~1600ms icons + form → fade (opacity 0→1, translateY 8→0)
```

Exact `sub` start = `label_delay + headline_enter_total_ms + 100ms gap`.

### Effect specs (WAAPI, scaled)

#### `label` — micro-scale-fade
- Target: whole element
- Enter: `opacity 0→1`, `scale 0.96→1`, duration `240ms`, easing `cubic-bezier(0.22, 1, 0.36, 1)`
- One-shot, no exit

#### `headline` — soft-blur-in
- Target: per-character (`Array.from(text)`)
- Enter per unit: `opacity 0→1`, `translateY(9px→0)` (y_travel × 0.58), `blur(12px→0)`
- Duration: `648ms`, stagger: `18ms/char`, easing: `cubic-bezier(0.22, 1, 0.36, 1)`
- One-shot, no exit
- Space characters get a `span` but are NOT animated

#### `sub` — typewriter
- Target: per-character
- Enter per unit: `opacity 0→1`, no y motion (stepped reveal)
- Duration per char: `173ms`, stagger: `33ms/char`, easing: `steps(1, end)`
- One-shot, no exit

#### `icons + form` — plain fade
- Target: whole container elements
- Enter: `opacity 0→1`, `translateY(8px→0)`, duration `400ms`, easing `ease`
- Small stagger between icons block and form: `80ms`

---

## Layer 2 — Scroll-Driven Reveal

### Mechanism

`IntersectionObserver` — native, no deps. Each observed element starts invisible (`opacity: 0`, `translateY: 16px`). When 15% of the element enters the viewport, the CSS class `.is-visible` is added, triggering the transition.

### CSS transition (in `global.css`)

```css
[data-animate-scroll] {
  opacity: 0;
  transform: translateY(16px);
  transition: opacity 0.5s ease, transform 0.5s ease;
}

[data-animate-scroll].is-visible {
  opacity: 1;
  transform: translateY(0);
}

@media (prefers-reduced-motion: reduce) {
  [data-animate-scroll] {
    opacity: 1;
    transform: none;
    transition: none;
  }
}
```

### Elements that get `data-animate-scroll`

- `Features` section label (`<p class="font-mono ...">`)
- Each feature item `<div class="grid ...">` — with `transition-delay` stagger: `0ms`, `80ms`, `160ms`, `240ms`…
- `Footer` tagline

### Stagger delays for feature items

Applied inline via `style="transition-delay: {i * 80}ms"` in the Astro template. Capped at 5 items × 80ms = 400ms max delay.

---

## `prefers-reduced-motion` Contract

All animations respect the media query:

- **Hero WAAPI animations**: check `window.matchMedia('(prefers-reduced-motion: reduce)').matches` before running. If true, snap elements directly to their final visible state without animating.
- **Scroll CSS transitions**: `[data-animate-scroll]` resets to visible with `transition: none` via the media query rule above.

---

## File Plan

| File | Change |
|------|--------|
| `src/scripts/hero-animations.ts` | New — WAAPI logic for Hero on-load sequence |
| `src/scripts/scroll-reveal.ts` | New — IntersectionObserver setup |
| `src/components/Hero.astro` | Add `id`/`data-*` attributes, `<script>` import |
| `src/components/Features.astro` | Add `data-animate-scroll` + stagger delays |
| `src/components/Footer.astro` | Add `data-animate-scroll` to tagline |
| `src/styles/global.css` | Add scroll-reveal CSS rules |

---

## Constraints

- Zero new npm dependencies (WAAPI + IntersectionObserver are native)
- No changes to i18n content files
- No changes to existing CSS class names or Tailwind utilities
- Hero animations run only after `DOMContentLoaded`
- Scripts are `type="module"` (Astro default)

---

## Out of Scope

- Text rotation / looping swaps on headline
- Per-word effects on Features descriptions
- Animation on Nav
- Any backend or persistence changes
