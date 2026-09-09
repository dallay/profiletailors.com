# Dependency Maintenance Gatekeeper Report

## Purpose

Audit and maintain dependency versions, licenses, and scores across the monorepo.

## Execution Result

Execution completed with outcome `CHANGES_APPLIED`. Updated `@biomejs/biome` patch version from `2.5.11` to `2.5.12` in root `package.json` and `pnpm-lock.yaml`. All audited dependency manifests, version catalog, lockfiles, and frontend dependency licence checks pass cleanly.

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

- Upgraded `@biomejs/biome` devDependency from `2.5.11` to `2.5.12` in `package.json` and updated `pnpm-lock.yaml`.

## Evidence Table

| Source Manifest / File | Audited Component | Finding / Status | Evidence |
| :--- | :--- | :--- | :--- |
| `package.json` | devDependencies | Safe patch upgrade applied | `@biomejs/biome` upgraded to `2.5.12`. |
| `apps/web/*/package.json` | Node / Vue / Vite / Astro | Up to date / Locked | All dependencies match workspace policy; major version bumps are restricted by maintenance rules. |
| `gradle/libs.versions.toml` | Spring Boot & Kotlin catalog | Aligned & Compliant | Spring Boot `4.0.8`, Kotlin `2.4.10`, Coroutines `1.10.2`, Jackson `3.2.2`. |
| Licence Audits | Frontend licences | 100% Compliant | `pnpm licenses list` passed with zero licence policy violations. |

## Validation Table

| Check Name | Target | Status | Notes |
| :--- | :--- | :--- | :--- |
| `pnpm licenses list` | Monorepo frontend dependencies | Passed | All frontend dependency licences AGPL-3.0 compliant. |
| `pnpm lint` | Apps & Shared Packages | Passed | Biome 2.5.12 check passed across workspace projects. |
| `pnpm --recursive test:run` | All JS/TS packages | Passed | Unit tests passed across workspace projects. |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

- **Last Execution:** `2026-09-09T17:30:00Z`
- **Schema Version:** `1`
- **Task Identity:** `dependency-maintenance`
- **Execution Outcome:** `CHANGES_APPLIED`

## Risk Assessment

- **Overall Risk:** LOW (Safe patch release update to Biome linter; verified via `pnpm lint` and unit test suites).

## Human Review Notes

Automated dependency maintenance check performed. Upgraded `@biomejs/biome` patch version to 2.5.12. Major upgrades (e.g., Vite 8, Vitest 5, TypeScript 7) are intentionally withheld per framework decision rules.
