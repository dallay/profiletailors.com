# Linter Suppression Auditor Report

## Purpose

Audit linter suppressions, ignore directives, and bypass comments for staleness and unjustified use across the repository.

## Execution Result

`NO_DRIFT_DETECTED` — Audit completed successfully. All scanned `biome-ignore`, `@Suppress`, and `@file:Suppress` directives were inspected and verified as active and justified by structural/framework constraints or active lint rules.

## Scope Inspected

- `apps/web/` (JS/TS/Vue files, Biome ignore directives)
- `server/smp/`, `shared/` (Kotlin files, Detekt / Kotlinc `@Suppress` annotations)
- No `eslint-disable` directives were present in active codebase source files.

## Changes Applied

None. No obsolete or unjustified suppressions were found; all existing directives are required.

## Evidence Table

| Suppression Directives | Location / Scope | Inspection Finding | Status |
| :--- | :--- | :--- | :--- |
| `biome-ignore lint/correctness/noEmptyPattern` | `apps/web/app/e2e/fixtures/media-mocked-test.ts` | Required for Playwright fixture destructuring contract. | Active / Justified |
| `biome-ignore lint/suspicious/noExplicitAny` | `apps/web/app/e2e/fixtures/scheduler-mocks.ts` | Required for Pinia dynamic state and Vue app internal access. | Active / Justified |
| `biome-ignore lint/correctness/noUnusedVariables` | `apps/web/app/src/components/ui/carousel/CarouselContent.vue` | Required for Vue template ref bound composable destructuring. | Active / Justified |
| `biome-ignore lint/a11y/*` | `apps/web/app/src/...` (various Vue views/components) | Required for chart interactions, drop targets, and backdrop overlays. | Active / Justified |
| `@Suppress` / `@file:Suppress` | `server/smp/`, `shared/` (Kotlin codebase) | Required for generic types, unchecked casts, and Detekt metric bounds. | Active / Justified |

## Validation Table

| Check Name | Target | Status | Notes |
| :--- | :--- | :--- | :--- |
| Biome Check | `apps/web/` | Passed | `pnpm exec biome check` executed; all ignore rules valid. |
| Detekt Check | `server/smp/`, `shared/` | Passed | Gradle Detekt tasks executed successfully without errors. |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

- **Last Execution:** `2026-03-30T00:00:00Z`
- **Schema Version:** `1`
- **Task Identity:** `suppression-auditor`
- **Outcome:** `NO_DRIFT_DETECTED`

## Risk Assessment

- **Overall Risk:** LOW
- **Details:** Inspection-only maintenance run; zero code changes applied, task state and report updated cleanly.

## Human Review Notes

No obsolete linter suppressions were detected. All suppressions remain valid for their stated framework/architectural justifications.
