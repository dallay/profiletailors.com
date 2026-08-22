# Linter Suppression Auditor Report

## Purpose

Audit linter suppression drift across the repository and remove obsolete or unjustified suppressions.

## Execution Result

`CHANGES_APPLIED`

## Scope Inspected

- `apps/web/marketing/src/components/**/*.test.ts`
- `apps/web/app/src/**/*`
- `server/smp/src/**/*`
- `shared/**/*`

## Evidence Table

| File | Suppression Type | Classification | Action Taken |
| --- | --- | --- | --- |
| `apps/web/marketing/src/components/Analytics.test.ts` | `eslint-disable-next-line no-new-func` | Obsolete | Removed |
| `apps/web/marketing/src/components/consent/ConsentScript.test.ts` | `eslint-disable-next-line no-new-func` | Obsolete | Removed |
| `apps/web/marketing/src/components/consent/CookieSettingsLink.test.ts` | `eslint-disable-next-line no-new-func` | Obsolete | Removed |
| `apps/web/marketing/src/components/consent/ConsentBanner.test.ts` | `eslint-disable-next-line no-new-func` | Obsolete | Removed |
| `apps/web/app/src/components/ui/carousel/CarouselContent.vue` (+9 more, see SUP-002 evidence in suppression-audit.yaml) | `biome-ignore lint/correctness/noUnusedVariables` | Required | Retained |
| `server/smp` and `shared` Kotlin modules (see SUP-003 evidence in suppression-audit.yaml) | Detekt `@Suppress` | Required | Retained |

## Validation

| Check | Scope | Result |
| --- | --- | --- |
| Marketing Biome Linter | `apps/web/marketing` | Passed |
| Marketing Vitest Unit Tests | `apps/web/marketing` | Passed |
| App SPA Biome Linter | `apps/web/app` | Passed |
| Backend Detekt Static Analysis | `:server:smp:detekt` | Passed |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

Updated `.agents/automation/state/suppression-audit.yaml` with state schema version 1.

## Risk Assessment

`LOW` — Removed only stale/obsolete ESLint comments in test files where ESLint is no longer part of the toolchain.

## Human Review Notes

No action required. All checks pass cleanly.
