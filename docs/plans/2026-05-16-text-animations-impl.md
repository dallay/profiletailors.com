# Text Animations Implementation Plan

> **For agentic workers:** Implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax
> for tracking. Tasks 1→2→3→4→5 are sequential — each builds on the previous.

**Goal:** Add WAAPI on-load hero animations and scroll-driven fade-in reveals across the landing page.

**Architecture:** Two vanilla TypeScript modules — `hero-animations.ts` (one-shot WAAPI sequence on
DOMContentLoaded) and `scroll-reveal.ts` (IntersectionObserver that adds `.is-visible` to elements
tagged `data-animate-scroll`). Components get `data-*` attributes and import scripts. CSS handles
scroll transitions. Zero new dependencies.

**Tech Stack:** WAAPI (`Element.animate`), IntersectionObserver, Astro 6 static, TypeScript, CSS custom properties.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `src/scripts/hero-animations.ts` | **Create** | WAAPI sequence: label → headline → sub → icons+form |
| `src/scripts/scroll-reveal.ts` | **Create** | IntersectionObserver wiring for `data-animate-scroll` elements |
| `src/components/Hero.astro` | **Modify** | Add `data-*` IDs, import both scripts via `<script>` |
| `src/components/Features.astro` | **Modify** | Add `data-animate-scroll` + inline stagger delays |
| `src/components/Footer.astro` | **Modify** | Add `data-animate-scroll` to tagline |
| `src/styles/global.css` | **Modify** | Add scroll-reveal CSS rules + reduced-motion overrides |

---

## Task 1: CSS — Scroll-reveal rules + reduced-motion

**Files:**
- Modify: `src/styles/global.css`

- [ ] **Step 1: Add scroll-reveal CSS after the existing Animations block**

Open `src/styles/global.css`. After the closing `}` of the `.fade-in` rule block (line ~78),
add the following:

```css
/* ─── Scroll reveal ─────────────────────────────────────────────────────────── */

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

- [ ] **Step 2: Verify the CSS file compiles**

Run from `apps/web/marketing/`:
```bash
pnpm dev
```
Expected: dev server starts at `localhost:4321` with no errors. The page looks identical to before (no elements have `data-animate-scroll` yet).

Stop the dev server (`Ctrl+C`).

- [ ] **Step 3: Commit**

```bash
git add apps/web/marketing/src/styles/global.css
git commit -m "feat(animations): add scroll-reveal CSS with reduced-motion support"
```

---

## Task 2: Create `scroll-reveal.ts`

**Files:**
- Create: `apps/web/marketing/src/scripts/scroll-reveal.ts`

- [ ] **Step 1: Create the file**

```typescript
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
```

- [ ] **Step 2: Commit**

```bash
git add apps/web/marketing/src/scripts/scroll-reveal.ts
git commit -m "feat(animations): add scroll-reveal IntersectionObserver module"
```

---

## Task 3: Create `hero-animations.ts`

**Files:**
- Create: `apps/web/marketing/src/scripts/hero-animations.ts`

The Hero sequence:
1. `[data-hero-label]` → micro-scale-fade (whole, 240ms)
2. `[data-hero-headline]` → soft-blur-in per-character (648ms duration, 18ms stagger)
3. `[data-hero-sub]` → typewriter per-character (173ms duration, 33ms stagger, steps(1,end))
4. `[data-hero-icons]` → plain fade (400ms, delay 80ms after sub starts)
5. `[data-hero-form]` → plain fade (400ms, delay 160ms after sub starts)

- [ ] **Step 1: Create the file**

```typescript
// src/scripts/hero-animations.ts
// One-shot WAAPI animation sequence for the Hero section on page load.
// Effects: micro-scale-fade (label), soft-blur-in (headline), typewriter (sub), plain fade (icons+form).

const EASING_SPRING = 'cubic-bezier(0.22, 1, 0.36, 1)'
const EASING_EASE   = 'ease'

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
        { opacity: 1, transform: 'translate3d(0, 0, 0)',              filter: 'blur(0px)'  },
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
    return span.animate(
      [{ opacity: 0 }, { opacity: 1 }],
      {
        delay: delayMs + rank * 33,
        duration: 173,
        easing: 'steps(1, end)',
        fill: 'forwards',
      }
    )
  })

  return Promise.all(animations.map((a) => a.finished)).then(() => {
    chars.forEach((span) => { span.style.opacity = '1' })
  })
}

// Animate a generic element: plain fade + translateY.
function animateFade(el: HTMLElement, delayMs: number): Promise<void> {
  el.style.opacity = '0'
  const anim = el.animate(
    [
      { opacity: 0, transform: 'translateY(8px)' },
      { opacity: 1, transform: 'translateY(0)'   },
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

  const label    = document.querySelector<HTMLElement>('[data-hero-label]')
  const headline = document.querySelector<HTMLElement>('[data-hero-headline]')
  const sub      = document.querySelector<HTMLElement>('[data-hero-sub]')
  const icons    = document.querySelector<HTMLElement>('[data-hero-icons]')
  const form     = document.querySelector<HTMLElement>('[data-hero-form]')

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
  if (form)  animateFade(form,  subDelay + 160)

  await subAnimPromise
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/web/marketing/src/scripts/hero-animations.ts
git commit -m "feat(animations): add WAAPI hero on-load animation sequence"
```

---

## Task 4: Wire `Hero.astro`

**Files:**
- Modify: `apps/web/marketing/src/components/Hero.astro`

- [ ] **Step 1: Replace `Hero.astro` with the wired version**

Replace the entire file content with:

```astro
---
import { Icon } from '@dallay/astro-icon/components'
import WaitlistForm from './WaitlistForm.astro'

interface Props {
  label: string
  headline: string
  sub: string
  inputPlaceholder: string
  cta: string
  waiting: string
  successSuffix: string
}

const { label, headline, sub, inputPlaceholder, cta, waiting, successSuffix } = Astro.props
---

<section class="px-6 pt-20 pb-24 max-w-4xl mx-auto">
  <p
    data-hero-label
    class="font-mono text-xs tracking-[0.2em] text-text-muted mb-8"
    style="opacity:0"
  >
    {label}
  </p>

  <div data-hero-icons class="flex items-center gap-5 mb-10" style="opacity:0">
    <Icon name="lucide:linkedin" />
    <Icon name="lucide:twitter" />
    <Icon name="lucide:instagram" />
    <Icon name="lucide:facebook" />
    <span class="font-mono text-xs text-text-muted ml-1 tracking-widest">+ more</span>
  </div>

  <h1
    data-hero-headline
    class="font-display text-5xl sm:text-6xl lg:text-7xl font-semibold text-text-display leading-[1.05] tracking-tight mb-6 whitespace-pre-line"
    style="opacity:0"
  >
    {headline}
  </h1>

  <p
    data-hero-sub
    class="font-display text-lg text-text-secondary max-w-xl leading-relaxed mb-12"
    style="opacity:0"
  >
    {sub}
  </p>

  <div data-hero-form style="opacity:0">
    <WaitlistForm
      inputPlaceholder={inputPlaceholder}
      cta={cta}
      waiting={waiting}
      successSuffix={successSuffix}
    />
  </div>
</section>

<script>
  import { initHeroAnimations } from '../scripts/hero-animations'
  import { initScrollReveal } from '../scripts/scroll-reveal'

  document.addEventListener('DOMContentLoaded', () => {
    initHeroAnimations()
    initScrollReveal()
  })
</script>
```

- [ ] **Step 2: Start dev server and visually verify**

```bash
pnpm dev
```

Open `localhost:4321`. Expected:
- Page loads with hero elements invisible
- Label fades in first (~0ms)
- Headline letters blur-in one by one left to right
- Sub paragraph types out character by character
- Icons row and form fade in while sub is still typing
- Everything completes within ~3 seconds
- No console errors

- [ ] **Step 3: Test reduced-motion**

In Chrome DevTools → Rendering tab → check "Emulate CSS media feature prefers-reduced-motion: reduce".  
Reload. Expected: all hero elements appear instantly with no animation.

- [ ] **Step 4: Commit**

```bash
git add apps/web/marketing/src/components/Hero.astro
git commit -m "feat(animations): wire hero data attributes and script imports"
```

---

## Task 5: Wire `Features.astro` and `Footer.astro`

**Files:**
- Modify: `apps/web/marketing/src/components/Features.astro`
- Modify: `apps/web/marketing/src/components/Footer.astro`

- [ ] **Step 1: Replace `Features.astro` with the wired version**

```astro
---
interface FeatureItem {
  tag: string
  title: string
  desc: string
}

interface Props {
  label: string
  items: readonly FeatureItem[]
}

const { label, items } = Astro.props
---

<section class="px-6 pt-20 pb-24 max-w-4xl mx-auto">
  <p
    data-animate-scroll
    class="font-mono text-xs tracking-[0.2em] text-text-muted mb-16"
  >
    {label}
  </p>

  <div class="flex flex-col gap-14">
    {items.map((feature, i) => (
      <div
        data-animate-scroll
        class="grid grid-cols-1 sm:grid-cols-[1fr_2fr] gap-4 sm:gap-12"
        style={`transition-delay: ${i * 80}ms`}
      >
        <p class="font-mono text-xs tracking-widest text-text-muted pt-1">
          {feature.tag}
        </p>
        <div>
          <h2 class="font-display text-xl font-semibold text-text-display mb-3">
            {feature.title}
          </h2>
          <p class="font-display text-base text-text-secondary leading-relaxed">
            {feature.desc}
          </p>
        </div>
      </div>
    ))}
  </div>
</section>
```

- [ ] **Step 2: Replace `Footer.astro` with the wired version**

```astro
---
interface Props {
  tagline: string
  copy: string
}

const { tagline, copy } = Astro.props
---

<footer class="px-6 py-10 max-w-4xl mx-auto flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
  <div data-animate-scroll>
    <p class="font-mono text-xs text-text-muted tracking-widest mb-1">PROFILE TAILORS</p>
    <p class="font-display text-sm text-text-muted">{tagline}</p>
  </div>
  <p class="font-mono text-xs text-text-muted">{copy}</p>
</footer>
```

- [ ] **Step 3: Visual verification**

With `pnpm dev` running, open `localhost:4321`. Scroll down. Expected:
- Features section label fades up when it enters viewport
- Feature items fade up one by one with 80ms stagger between them
- Footer tagline fades up when footer enters viewport
- All transitions are smooth (0.5s ease)

- [ ] **Step 4: Test reduced-motion**

Enable "prefers-reduced-motion: reduce" in DevTools. Reload and scroll.  
Expected: features and footer items appear immediately, no transition.

- [ ] **Step 5: Commit**

```bash
git add apps/web/marketing/src/components/Features.astro
git add apps/web/marketing/src/components/Footer.astro
git commit -m "feat(animations): add scroll-reveal to features and footer"
```

---

## Done ✓

After all tasks complete, run a final build to confirm no TypeScript or Astro errors:

```bash
pnpm build
```

Expected: build completes to `./dist/` with no errors or warnings.
