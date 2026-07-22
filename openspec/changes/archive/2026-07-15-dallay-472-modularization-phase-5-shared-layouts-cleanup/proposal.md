# Proposal: DALLAY-472 Modularization Phase 5 Shared Layouts Cleanup

## Problem / Why Now

Phases 0–4 moved feature code into modules, but `apps/web/app/src` still has root-level layout,
sidebar, composable, utility, i18n, and one auth view leftover. Now is the right cleanup point
because module aliases already exist and this is the final architecture pass before new feature work
builds on unstable roots.

## Goals

- Move shell/layout ownership to `@layouts`.
- Move generic cross-module utilities, composables, and i18n to `@shared`.
- Move domain-owned leftovers into auth, media, or publishing modules.
- Preserve runtime behavior, routes, tests, and shadcn-vue conventions.

## Non-goals

- No visual, UX, route, state, API, or design-system behavior changes.
- No migration of shadcn-vue primitives out of `components/ui`.
- No store splitting, public barrel redesign, or new abstractions beyond relocation.

## Scope

### In Scope

- Relocate `components/layout/*` and sidebar shell parts into `src/layouts/`.
- Relocate reusable app components/utilities/composables/i18n into `src/shared/`.
- Relocate `VerifyEmailView.vue` and domain-specific composables into owning modules.
- Update imports, tests, mocks, and relocation guards.

### Out of Scope

- Removing `src/components/ui/**`.
- Changing `components.json` or shadcn generation paths.
- Broad feature refactors discovered during moves.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `frontend-modularization`: extend relocation rules for shared/layout cleanup while preserving
  behavior.
- `app-shell`: update ownership paths from `components/layout` and `components/sidebar` to `layouts`
  where applicable.

## Proposed Approach

Use compatibility-first domain cleanup: keep shadcn-vue under `@/components/ui/*`; keep or shim
`@/lib/utils` only if needed for generated UI compatibility; move non-shadcn leftovers to
`@layouts`, `@shared`, or owning modules. AppShell owns authenticated layout composition; reusable
atoms like `WorkspaceAvatar` may move to shared only when consumed outside layouts.

## Alternatives / Tradeoffs

| Option                                             | Tradeoff                                                                   |
|----------------------------------------------------|----------------------------------------------------------------------------|
| Keep `components/ui` + `@/lib/utils` compatibility | Lowest risk; root `components` remains because shadcn owns it.             |
| Move all UI to shared                              | Cleaner tree; high churn and fights shadcn-vue CLI defaults.               |
| Layout owns all shell pieces                       | Clear AppShell boundary; shared reuse requires deliberate promotion later. |

## Risks

| Risk                                     | Mitigation                                                    |
|------------------------------------------|---------------------------------------------------------------|
| Import/mock churn breaks tests           | Update app-specific tests and mocks with moved paths.         |
| `@/lib/utils` move breaks shadcn imports | Prefer compatibility boundary or one deliberate import sweep. |
| Layout depends on publishing route state | Keep dependency explicit through stable module exports.       |

## Rollback Plan

Revert the relocation commit(s), restore previous import paths, and rerun app-focused Vitest/Biome
checks to confirm pre-change behavior resolves.

## Acceptance Criteria / Linear DoD

- [ ] Root leftovers are moved or explicitly justified.
- [ ] `components/ui` and shadcn conventions remain compatible.
- [ ] No behavior changes to shell, routes, auth, media, or publishing.
- [ ] App-specific tests/lint/type checks pass or unrelated failures are documented.
- [ ] Relocation guards prevent reintroducing cleaned legacy paths.
