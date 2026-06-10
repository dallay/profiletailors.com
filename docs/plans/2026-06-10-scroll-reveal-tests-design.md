# Test Coverage: scroll-reveal.ts

## Goal

Add unit tests for `scroll-reveal.ts` in `apps/web/marketing` to eliminate the 0% coverage gap in `src/scripts/`, raising overall frontend statement coverage from ~40% toward ~65-70%.

## Background

JaCoCo/Vitest coverage report shows `src/scripts/` at 0% coverage:

```text
scripts/animations.ts | 0% | 100% | 100% | 0% | 5-188
scripts/scroll-reveal.ts | 0% | 100% | 100% | 0% | 5-30
```

The file `scroll-reveal.ts` is a30-line utility that adds `.is-visible` to `[data-animate-scroll]` elements via `IntersectionObserver`. It is testable in isolation with DOM mocking.

`hero-animations.ts` (188 lines) is out of scope for this pass — it requires WAAPI mocking and is addressed separately.

## Approach

### Environment change

Switch Vitest from `environment: 'node'` to `environment: 'jsdom'` in `vitest.config.ts`. This enables real DOM APIs (`document.querySelectorAll`, `IntersectionObserver`) without needing to mock them manually.

If jsdom is not yet installed:

```sh
pnpm add -D jsdom @vitest/jsdom
```

Update `vitest.config.ts`:

```ts
import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    globals: true,
    environment: 'jsdom', // was: 'node'
    include: ['src/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: ['src/**/*.ts'],
      exclude: ['src/**/*.test.ts', 'src/**/*.d.ts'],
    },
  },
})
```

### Test scenarios for `initScrollReveal()`

| # | Scenario | Expected |
|---|----------|----------|
| 1 | `prefers-reduced-motion: reduce` is active | function returns early, no observer created |
| 2 | No `[data-animate-scroll]` elements in DOM | function returns early, no observer created |
| 3 | Single `[data-animate-scroll]` element, never intersects | observer created, element not visible |
| 4 | Single `[data-animate-scroll]` element, intersects viewport | `.is-visible` added, observer stops watching that element |
| 5 | Multiple `[data-animate-scroll]` elements, some intersect | only intersected elements get `.is-visible` |
| 6 | Element already has `.is-visible` before observe | not re-observed (one-shot, already visible) |

### Mocking strategy

- `globalThis.matchMedia` — mock via `vi.stubGlobal('matchMedia', ...)`
- `IntersectionObserver` — use Vitest's built-in fake for the `observe/unobserve` API, or mock manually
- `document.querySelectorAll` — handled automatically by jsdom

### File to create

`apps/web/marketing/src/scripts/scroll-reveal.test.ts`

## Risks & Mitigations

- **jsdom performance**: slight slowdown vs node env — acceptable for the small test surface
- **IntersectionObserver fake**: Vitest's fake may not fire `isIntersecting` correctly in all cases — mock manually if needed

## Success criteria

- `scroll-reveal.test.ts` exists and all 6 scenarios pass
- `src/scripts/` coverage moves from 0% to 100% in the Vitest coverage report
- Overall `apps/web/marketing` statement coverage increases by ~25pp (from ~40% to ~65%)
- No regressions in existing tests

## Out of scope

- `hero-animations.ts` tests
- Changes to `apps/web/app` (Vue app has no test setup)
- Backend coverage improvements
