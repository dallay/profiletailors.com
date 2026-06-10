# Test Coverage: hero-animations.ts

## Goal

Add unit + integration tests for `hero-animations.ts` to raise `src/scripts/` coverage from ~13% to ~100%, driving overall frontend statement coverage from ~48% toward ~70-75%.

## Background

After adding `scroll-reveal.ts` tests, `src/scripts/` sits at ~13% because `hero-animations.ts` (188 lines) is untested. This file is the largest in the directory and the single biggest gap toward the 80% coverage target.

The file has these exported functions:
- `splitToChars(el)` — pure function, easily unit-testable
- `snapVisible(el)` — pure function (sets inline styles)
- `animateLabel/Headline/Sub/Fade` — WAAPI animations, tested indirectly
- `initHeroAnimations()` — orchestration entry point, tested via integration

## Approach: Hybrid

### Tier 1: `splitToChars` unit tests (pure function)

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Empty text | returns `[]` |
| 2 | Text `"hello"` | 5 spans with `textContent` = each char |
| 3 | Text with `\n` | `\n` becomes `<br>` node, no span |
| 4 | Single char |1 span |

### Tier 2: `initHeroAnimations` integration tests

| # | Scenario | Expected |
|---|----------|----------|
| 1 | `prefers-reduced-motion: true` | `snapVisible` called on all found elements |
| 2 | No `[data-hero-label]` in DOM | returns early, no errors |
| 3 | Hero elements exist, motion OK | all `animate*` functions called |
| 4 | `icons`/`form` are null | graceful handling, no throw |

## Mocking Strategy

### `matchMedia`
```ts
vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({
  matches: false, // default: motion OK
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
}))
```

### `element.animate()`
jsdom does NOT support WAAPI (`element.animate()`). Mock it on each element:
```ts
const makeMockEl = (): HTMLElement => ({
  style: { opacity: '', transform: '', filter: '', willChange: '' },
  animate: vi.fn().mockReturnValue({ finished: Promise.resolve() }),
  textContent: 'Test',
 appendChild: vi.fn(),
 querySelectorAll: vi.fn().mockReturnValue([]),
} as unknown as HTMLElement)
```

### `setTimeout` / `setInterval`
Use `vi.useFakeTimers()` to control async timing in `animateSub` (typewriter uses `setTimeout` per character).

### `document.querySelector`
Stub per test:
```ts
beforeEach(() => {
  document.querySelector = vi.fn()
})
```

## File to Create

`apps/web/marketing/src/scripts/hero-animations.test.ts`

## Out of Scope

- Testing individual `animateLabel/Headline/Sub/Fade` functions in isolation (covered indirectly via `initHeroAnimations`)
- `commitAndCancel` internal function (covered via integration assertions on final element state)
- `hero-animations.ts` refactoring to expose more pure functions

## Success Criteria

- `hero-animations.test.ts` passes all scenarios
- `src/scripts/hero-animations.ts` reaches 80%+ line coverage
- Overall `apps/web/marketing` statement coverage ≥ 65%
- All existing tests still pass
