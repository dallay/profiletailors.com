# Dependency Maintenance Gatekeeper Report

## Purpose
Conservative dependency maintenance audit and updates to resolve version-drift compile errors, type check failures, and package drift.

## Execution Result
CHANGES_APPLIED

## Scope Inspected
- Gradle dependencies (`gradle/libs.versions.toml`)
- Node.js dependencies (`package.json`, `pnpm-lock.yaml` across workspace packages `apps/web/marketing` and `apps/web/app`)
- Astro and TypeScript compiler alignment in the frontend

## Changes Applied
- Upgraded `@iconify-json/lucide` from `1.2.116` to `1.2.120`, `@tailwindcss/vite` from `4.3.0` to `4.3.3`, and `tailwindcss` from `4.3.0` to `4.3.3` in `apps/web/marketing/package.json`.
- Upgraded `@tailwindcss/vite` from `4.3.1` to `4.3.3`, `tailwindcss` from `4.3.1` to `4.3.3`, `vue-i18n` from `11.4.7` to `11.4.8`, and `vue-tsc` from `3.3.7` to `3.3.8` in `apps/web/app/package.json`.
- Ran `pnpm install` to update the local workspace package lockfile.
- Fixed the TypeScript compiler type mismatch in marketing's `ConsentBanner.astro` and `CookieSettingsLink.astro` components, where `Astro.currentLocale` (which is `string | undefined`) was being supplied to `useTranslations` (which requires `Locale`).

## Evidence Table
| Finding ID | Dependency | Type | Version Drift | Resolved Version | Status | Evidence |
|------------|------------|------|---------------|------------------|--------|----------|
| detekt-kotlin-mismatch | `dev.detekt` | version-drift | `2.0.0-alpha.5` | `2.0.0-alpha.3` | resolved | Detekt 2.0.0-alpha.5 compiled with Kotlin 2.4.0, but project Kotlin is 2.3.21. Downgrading to 2.0.0-alpha.3 resolved mismatch. |
| biome-patch-drift | `@biomejs/biome" | patch-drift | `2.5.4` | `2.5.5` | resolved | Safe minor patch upgrade. |
| marketing-deps-patch | `@iconify-json/lucide, @tailwindcss/vite, tailwindcss` | patch-drift | `1.2.116, 4.3.0, 4.3.0` | `1.2.120, 4.3.3, 4.3.3` | resolved | Upgraded marketing dependencies in package.json and verified lockfile integrity. |
| app-deps-patch | `@tailwindcss/vite, tailwindcss, vue-i18n, vue-tsc` | patch-drift | `4.3.1, 4.3.1, 11.4.7, 3.3.7` | `4.3.3, 4.3.3, 11.4.8, 3.3.8` | resolved | Upgraded app dependencies in package.json and verified lockfile integrity. |
| marketing-astro-locale-typecheck | `astro / typescript` | version-drift | `Astro.currentLocale as string` | `Guarded Locale cast` | resolved | Fixed type mismatch in useTranslations input where Astro.currentLocale was typed as string | undefined. |

## Validation Table
| Check Name | Status | Description |
|------------|--------|-------------|
| `just backend-check` | Passed | Compiled backend, ran unit and integration tests successfully. |
| `just backend-lint` | Passed | Detekt static analysis completed with zero errors or warnings. |
| `pnpm biome check` | Passed | Validated package.json formatting and biome lint checks. |
| `just frontend-check` | Passed | Ran astro type check across marketing and dashboard workspace. |
| `just frontend-lint` | Passed | Biome checked and formatted marketing workspace. |
| `just frontend-test` | Passed | Marketing unit tests run and completed successfully via vitest. |
| `pnpm --filter app test:run` | Passed | App unit and integration tests run and completed successfully via vitest. |

## Unresolved Findings
None.

## Blockers
None.

## Automation State
State is tracked in `.agents/automation/state/dependency-maintenance.yaml`.

## Risk Assessment
Low Risk. All upgrades are restricted to patch level or tooling updates, avoiding any major production dependency changes. The TypeScript casting fix in the marketing layout resolves a known type check blocker safely by verifying the locale is supported.

## Human Review Notes
No manual intervention required. Both Gradle compilation, frontend linting, frontend typechecking, and unit tests pass cleanly.
