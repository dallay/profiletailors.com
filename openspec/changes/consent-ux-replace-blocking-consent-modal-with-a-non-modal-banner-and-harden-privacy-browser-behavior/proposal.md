# Proposal: Replace blocking consent modal with non-modal banner and harden privacy-browser behavior

## Intent

The app's first-run consent prompt is a blocking reka-ui Dialog: full-screen overlay, focus trap, and inherited close button that hides the prompt without writing a receipt — leaving an undecided state. Brave stacking failures were only mitigated via `z-[51]`. Replace the unsolicited prompt with a semantic fixed non-modal banner; keep `CookieSettings.vue` as the intentional settings modal; validate across browsers.

## Scope

### In Scope
- `ConsentBanner.vue` → fixed non-modal banner: bottom viewport, `~560–680px` max-width centered desktop, bottom-sheet mobile with safe-area padding; no overlay/portal, no focus trap, no close while undecided.
- Keyboard: **Escape ignored while undecided**; focus stays in document on mount; reachable in tab order. "Customize" stays inline in banner; `CookieSettings` remains the only consent modal, opened solely by `showCookieSettings` — no orphaned overlay.
- Preserve store contract: `source: 'banner'`, validation, `pt-consent`, DNT/GPC capture, async backend sync; `forceOpen` re-prompt retained.
- Rewrite `ConsentBanner.spec.ts`; update E2E `consent.spec.ts` (banner visible + app interactable); keep `consent-banner` testid.
- Browser matrix: Chrome/Chromium, Safari/WebKit, Brave Shields ON/OFF × states A–D (A no receipt→banner; B valid→hidden; C stale→re-prompt; D DNT/GPC→restrictive); verify no backdrop/overflow/orphaned overlay, themes, EN/ES.

### Out of Scope
Analytics provider, new categories, backend governance API/model, weakening script blocking, auth/session rework, marketing banner.

## Capabilities

### New Capabilities
- `consent-banner-presentation`: app banner modality (non-modal, fixed bottom, responsive), keyboard/focus semantics, no-orphaned-overlay guarantee, browser-resilience matrix.

### Modified Capabilities
None — `privacy-compliance` receipt/source/version contract unchanged; marketing banner untouched.

## Approach

Rework `ConsentBanner.vue` to an inline `section` fixed at viewport bottom (no dialog portal), scoped z-index, gated on `!hasValidConsent || forceOpen`, zero dismiss paths while undecided. `CookieSettings.vue` and dialog primitives unchanged. Update unit/E2E tests; verify states A–D across the matrix.

## Affected Areas

| Area | Impact |
|------|--------|
| `consent/ConsentBanner.vue` | Modified |
| `consent/ConsentBanner.spec.ts` | Modified |
| `e2e/specs/consent.spec.ts` | Modified |
| `infrastructure/consent.store.ts` | Verify |
| `layouts/AppShell.vue` | Verify |
| `components/ui/dialog/*` | Unchanged |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Banner covers controls / overflow | Med | Max-width, safe-area, scoped z, matrix |
| Escape/close leaks undecided state | Low | No dismiss affordance while undecided |
| Specs/E2E coupled to dialog stubs | High | Keep testid; re-stub; audit `consent-helpers` |
| `forceOpen` splits across surfaces | Med | Define ownership in spec |

## Rollback Plan

`git revert`: `ConsentBanner.vue` returns to dialog. No migration — receipts stay valid (`consentVersion: 1`), banner removal never deletes `pt-consent`, stale receipts re-prompt. Backend untouched.

## Dependencies

None external. Reka-ui `Dialog` retained only by `CookieSettings.vue`.

## Success Criteria

- [ ] Non-modal: no overlay, app interactable while banner visible
- [ ] No dismiss path leaves undecided; Escape does not close
- [ ] Matrix green: Chrome/Safari/Brave (ON+OFF), states A–D
- [ ] Vitest + app E2E consent suites pass; Biome + vue-tsc clean
- [ ] EN/ES copy and light/dark themes render correctly