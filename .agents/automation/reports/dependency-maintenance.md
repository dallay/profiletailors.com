# Dependency Maintenance Gatekeeper Report

## Purpose

Conservative dependency maintenance audit and updates to resolve version-drift and patch-drift among workspace dependencies.

## Execution Result

CHANGES_APPLIED

## Scope Inspected

- Root lockfile (`pnpm-lock.yaml`)
- Dashboard SPA app package manifest (`apps/web/app/package.json`)
- Platform admin SPA package manifest (`apps/web/admin/package.json`)
- Marketing Astro package manifest (`apps/web/marketing/package.json`)
- Gradle dependencies config (`gradle/libs.versions.toml`)

## Changes Applied

- Upgraded `@iconify-json/lucide` from `1.2.121` to `1.2.123` and `astro` from `7.2.0` to `7.2.2` in `apps/web/marketing/package.json`.
- Upgraded `@tsconfig/node24` from `24.0.4` to `24.0.5`, `pinia` from `4.0.2` to `4.0.3`, `reka-ui` from `2.10.1` to `2.10.3`, and `vue-tsc` from `3.3.9` to `3.3.10` in `apps/web/app/package.json` and `apps/web/admin/package.json`.
- Upgraded `shadcn-vue` from `2.7.4` to `2.8.0` and `@lucide/vue` from `1.25.0` to `1.27.0` in `apps/web/admin/package.json` to align with `apps/web/app/package.json`.
- Added `@types/node` `^24.0.0` to `apps/web/admin/package.json` matching Node 24 target to resolve node type definition resolution during `vue-tsc` build checks.
- Re-installed workspace dependencies with `pnpm install` to update `pnpm-lock.yaml`.

## Evidence Table

| Finding ID | Dependency | Type | Version Drift | Resolved Version | Status | Evidence |
|------------|------------|------|---------------|------------------|--------|----------|
| iconify-lucide-patch-drift | `@iconify-json/lucide` | patch-drift | `1.2.121` | `1.2.123` | resolved | Upgraded in marketing package manifest. |
| astro-patch-drift | `astro` | patch-drift | `7.2.0` | `7.2.2` | resolved | Upgraded in marketing package manifest. |
| tsconfig-node24-patch-drift | `@tsconfig/node24` | patch-drift | `24.0.4` | `24.0.5` | resolved | Upgraded in app and admin package manifests. |
| pinia-patch-drift | `pinia` | patch-drift | `4.0.2` | `4.0.3` | resolved | Upgraded in app and admin package manifests. |
| reka-ui-patch-drift | `reka-ui` | patch-drift | `2.10.1` | `2.10.3` | resolved | Upgraded in app and admin package manifests. |
| shadcn-vue-alignment-drift | `shadcn-vue` | version-drift | `2.7.4` | `2.8.0` | resolved | Aligned admin package manifest with app version. |
| lucide-vue-alignment-drift | `@lucide/vue` | version-drift | `1.25.0` | `1.27.0` | resolved | Aligned admin package manifest with app version. |
| vue-tsc-patch-drift | `vue-tsc` | patch-drift | `3.3.9` | `3.3.10` | resolved | Upgraded in app and admin package manifests. |
| admin-types-node-drift | `@types/node` | missing-dev-dependency | `null` | `^24.0.0` | resolved | Added `@types/node` Node 24 declaration to admin devDependencies to satisfy `vue-tsc`. |

## Validation Table

| Check Name | Status | Description |
|------------|--------|-------------|
| `backend-build-check` | Passed | Executed backend Spring Boot unit tests via Gradle. |
| `frontend-biome-check` | Passed | Ran biome linter checks on marketing, app, and admin subprojects. |
| `frontend-type-check` | Passed | Ran type checks using `astro check` and `vue-tsc --build`. |
| `frontend-unit-test-check` | Passed | Ran unit test suites across marketing, app, and admin subprojects. |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

State is tracked in `.agents/automation/state/dependency-maintenance.yaml`.

## Risk Assessment

Low Risk. All upgrades are patch-level or alignment updates within safe ranges. Type checks, linter checks, and unit test suites across all projects pass cleanly.

## Human Review Notes

No manual intervention required. Workspace dependencies updated cleanly and all build/test validations succeeded.
