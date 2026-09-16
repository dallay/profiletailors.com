# Dependency Maintenance Gatekeeper Report

## Purpose

Audit and maintain dependency versions, licenses, and scores across the monorepo.

## Execution Result

Execution completed with outcome `CHANGES_APPLIED`. Updated safe patch and minor versions for non-major frontend dependencies (`@biomejs/biome`, `@iconify-json/lucide`, `@internationalized/date`, `@types/node`, `vue-router`, `yaml`, `@lucide/vue`, `@playwright/test`, `@unovis/vue`, `tailwind-merge`) across workspace package manifests and `pnpm-lock.yaml`. All audited dependency manifests, version catalog, lockfiles, and frontend dependency licence checks pass cleanly.

## Scope Inspected

- `package.json` (root pnpm workspace configuration)
- `apps/web/marketing/package.json`
- `apps/web/app/package.json`
- `apps/web/admin/package.json`
- `tools/compliance/package.json`
- `shared/web/package.json`
- `pnpm-workspace.yaml` & `pnpm-lock.yaml`
- `gradle/libs.versions.toml` (Gradle Version Catalog)

## Changes Applied

- Upgraded `@biomejs/biome` from `2.5.12` to `2.5.13` in `package.json`.
- Upgraded `@iconify-json/lucide` from `1.2.128` to `1.2.132` in `apps/web/marketing/package.json`.
- Upgraded `@internationalized/date` from `3.12.3` to `3.12.4` in `apps/web/app/package.json`.
- Upgraded `@types/node` in `apps/web/admin/package.json` (`24.13.3` -> `24.13.4`) and `apps/web/app/package.json` (`25.9.5` -> `24.13.4`).
- Upgraded `vue-router` from `5.3.0` to `5.3.1` in `apps/web/admin/package.json` and `apps/web/app/package.json`.
- Upgraded `yaml` from `2.9.0` to `2.9.1` in `apps/web/marketing/package.json` and `tools/compliance/package.json`.
- Upgraded `@lucide/vue` from `1.38.0` to `1.45.0` in `apps/web/admin/package.json` and `apps/web/app/package.json`.
- Upgraded `@playwright/test` from `1.62.1` to `1.63.0` in `apps/web/app/package.json` and `apps/web/marketing/package.json`.
- Upgraded `@unovis/vue` from `1.6.7` to `1.7.0` in `apps/web/app/package.json`.
- Upgraded `tailwind-merge` from `3.6.0` to `3.7.0` in `apps/web/admin/package.json` and `apps/web/app/package.json`.
- Updated `pnpm-lock.yaml`.

## Evidence Table

| Source Manifest / File | Audited Component | Finding / Status | Evidence |
| :--- | :--- | :--- | :--- |
| `package.json` & workspace manifests | devDependencies & dependencies | Safe patch/minor upgrades applied | Updated 10 safe non-major dependencies. |
| `apps/web/*/package.json` | Node / Vue / Vite / Astro | Up to date / Locked | All dependencies match workspace policy; major version bumps (Vite 8, Vitest 5, TypeScript 7) are restricted by maintenance rules. |
| `gradle/libs.versions.toml` | Spring Boot & Kotlin catalog | Aligned & Compliant | Spring Boot `4.0.8`, Kotlin `2.4.10`, Coroutines `1.10.2`, Jackson `3.2.2`. |
| Licence Audits | Frontend licences | 100% Compliant | `pnpm licenses list` passed with zero licence policy violations. |

## Validation Table

| Check Name | Target | Status | Notes |
| :--- | :--- | :--- | :--- |
| `pnpm licenses list` | Monorepo frontend dependencies | Passed | All frontend dependency licences AGPL-3.0 compliant. |
| `pnpm lint` | Apps & Shared Packages | Passed | Biome check passed across workspace projects. |
| `pnpm --recursive test:run` | All JS/TS packages | Passed | Unit tests passed across workspace projects (149 test files, 1750 tests passed in app). |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

- **Last Execution:** `2026-09-16T17:55:02Z`
- **Schema Version:** `1`
- **Task Identity:** `dependency-maintenance`
- **Execution Outcome:** `CHANGES_APPLIED`

## Risk Assessment

- **Overall Risk:** LOW (Safe patch and minor release updates to frontend tooling and utilities; verified via `pnpm lint` and unit test suites).

## Human Review Notes

Automated dependency maintenance check performed. Upgraded non-major frontend dependencies. Major upgrades (e.g., Vite 8, Vitest 5, TypeScript 7) are intentionally withheld per framework decision rules.
