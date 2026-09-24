# Dependency Maintenance Gatekeeper Report

## Purpose

Audit and maintain dependency versions, licenses, and scores across the monorepo.

## Execution Result

Execution completed with outcome `CHANGES_APPLIED`. Updated eligible conservative patch dependencies across frontend workspace packages and root workspace (`vue` to `3.5.43`, `vue-i18n` to `11.4.12`, `@vue/test-utils` to `2.5.1`, `@lucide/vue` to `1.47.0`, `@iconify-json/lucide` to `1.2.135`, `markdownlint-cli2` to `0.23.3`) in `package.json` and `pnpm-lock.yaml`. The validation table records the checks reported as passed; their execution location was not recorded. No CI, remote, or deployed validation evidence is cited here.

## Scope Inspected

- `package.json` (root pnpm workspace configuration)
- `apps/web/marketing/package.json`
- `apps/web/app/package.json`
- `apps/web/admin/package.json`
- `shared/vue-ui/package.json`
- `tools/compliance/package.json`
- `shared/web/package.json`
- `pnpm-workspace.yaml` & `pnpm-lock.yaml`
- `gradle/libs.versions.toml` (Gradle Version Catalog)

## Changes Applied

- Upgraded `vue` patch version from `3.5.42` to `3.5.43` across workspace packages.
- Upgraded `vue-i18n` patch version from `11.4.10` to `11.4.12` in `apps/web/app` and `apps/web/admin`.
- Upgraded `@vue/test-utils` patch version from `2.5.0` to `2.5.1` in `apps/web/app`, `apps/web/admin`, and `shared/vue-ui`.
- Upgraded `@lucide/vue` minor version from `1.46.0` to `1.47.0` in `apps/web/app` and `apps/web/admin`.
- Upgraded `@iconify-json/lucide` patch version from `1.2.134` to `1.2.135` in `apps/web/marketing`.
- Upgraded `markdownlint-cli2` patch version from `0.23.2` to `0.23.3` in root `package.json`.
- Updated `pnpm-lock.yaml`.

## Evidence Table

| Source Manifest / File | Audited Component | Finding / Status | Evidence |
| :--- | :--- | :--- | :--- |
| `package.json` | devDependencies | Safe patch upgrade applied | `markdownlint-cli2` upgraded to `0.23.3`. |
| `apps/web/*/package.json`, `shared/vue-ui/package.json` | Node / Vue / Lucide / i18n | Safe patch/minor updates applied | `vue` 3.5.43, `vue-i18n` 11.4.12, `@vue/test-utils` 2.5.1, `@lucide/vue` 1.47.0, `@iconify-json/lucide` 1.2.135. |
| `gradle/libs.versions.toml` | Spring Boot & Kotlin catalog | Aligned & Compliant | Spring Boot `4.0.8`, Kotlin `2.4.10`, Coroutines `1.10.2`, Jackson `3.2.2`. |
| Licence Audits | Frontend licences | 100% Compliant | `pnpm licenses list` passed with zero licence policy violations. |

## Validation Table

| Check Name | Target | Reported Status | Execution Source | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `pnpm licenses list` | Monorepo frontend dependencies | Passed | Not recorded (local/CI/remote/deployed unknown) | Frontend dependency licences reported AGPL-3.0 compliant. |
| `pnpm lint` | Apps & Shared Packages | Passed | Not recorded (local/CI/remote/deployed unknown) | Biome check reported passed across workspace projects. |
| `pnpm --recursive test:run` | All JS/TS packages | Passed | Not recorded (local/CI/remote/deployed unknown) | 150 test files and 1,756 unit tests reported passed across workspace projects. |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

- **Last Execution:** `2026-09-09T18:00:00Z`
- **Schema Version:** `1`
- **Task Identity:** `dependency-maintenance`
- **Execution Outcome:** `CHANGES_APPLIED`

## Risk Assessment

- **Overall Risk:** LOW (Safe conservative patch/minor dependency updates; verified via `pnpm lint`, license checks, and unit test suites).

## Human Review Notes

Automated dependency maintenance check performed. Upgraded patch/minor versions for `vue`, `vue-i18n`, `@vue/test-utils`, `@lucide/vue`, `@iconify-json/lucide`, and `markdownlint-cli2`. Major upgrades (e.g., Vite 8, Vitest 5, TypeScript 7, Wrangler 4) are intentionally withheld per framework decision rules.
