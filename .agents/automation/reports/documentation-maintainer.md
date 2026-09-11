# Documentation Maintenance Audit Report

## Purpose

Audit documentation for accuracy, freshness, and alignment with current code and runtime definitions.

## Execution Result

`CHANGES_APPLIED`

The documentation maintainer audit detected drift in Java runtime/JDK prerequisites declarations in `server/smp/README.md` and 10 `shared/*/README.md` files, as well as a stale Kotlin version link in `docs/technical-debt-remediation.md`. Safe, evidence-backed corrections were applied to align these files with `AppConfiguration.kt` (`KtJvmTarget.JVM_25`), `gradle/libs.versions.toml` (`jdk = "25"`, `kotlin = "2.4.10"`), and `docs/getting-started.md`.

## Scope Inspected

- Root files: `README.md`, `Justfile`, `package.json`, `.nvmrc`, `CONTRIBUTING.md`
- Core documentation: `docs/getting-started.md`, `docs/gradle-build-system.md`, `docs/technical-debt-remediation.md`, `docs/compliance/README.md`
- Backend subproject & shared component documentation: `server/smp/README.md`, `shared/common/README.md`, `shared/presentation/README.md`, `shared/notifications/README.md`, `shared/security/README.md`, `shared/shield/ratelimit/README.md`, `shared/storage/README.md`, `shared/lead-capture/common/README.md`, `shared/lead-capture/waitlist/README.md`, `shared/bus/README.md`, `shared/spring-boot-common/README.md`
- Frontend subproject definitions: `apps/web/app/package.json`, `apps/web/app/README.md`, `apps/web/marketing/README.md`, `apps/web/admin/README.md`

## Changes Applied

- `server/smp/README.md`: Updated `Tech stack` and `Prerequisites` Java references from `Java 21` / `JDK >= 21` to `Java 25` / `JDK >= 25`.
- `shared/*/README.md` (10 files): Updated `Tech stack` and `Prerequisites` Java references from `Java 21` / `JDK >= 21` to `Java 25` / `JDK >= 25`.
- `docs/technical-debt-remediation.md`: Updated reference link from Kotlin 2.3 to Kotlin 2.4 release notes (`https://kotlinlang.org/docs/whatsnew24.html`).

## Evidence Table

| Claim / Location | Documented Value | Source of Truth | Status | Action Taken |
| :--- | :--- | :--- | :--- | :--- |
| Java target in `server/smp/README.md` | `Java 21` / `JDK >= 21` | `AppConfiguration.kt` (`JVM_25`), `gradle/libs.versions.toml` (`jdk = "25"`), `docs/getting-started.md` | Outdated | Reconciled reference to `Java 25` / `JDK >= 25`. |
| Java target in 10 `shared/*/README.md` files | `Java 21` / `JDK >= 21` | `AppConfiguration.kt` (`JVM_25`), `gradle/libs.versions.toml` (`jdk = "25"`), `docs/getting-started.md` | Outdated | Reconciled reference to `Java 25` / `JDK >= 25`. |
| Kotlin release link in `docs/technical-debt-remediation.md` | `whatsnew23.html` | `gradle/libs.versions.toml` (`kotlin = "2.4.10"`) | Outdated | Reconciled link to `whatsnew24.html`. |

## Validation Table

| Check Name | Target | Status | Notes |
| :--- | :--- | :--- | :--- |
| `java-version-alignment` | `server/smp/README.md`, `shared/*/README.md`, `docs/getting-started.md` | Passed | Java target declarations reconciled with `AppConfiguration.kt` (`JVM_25`) and `gradle/libs.versions.toml` (`jdk = "25"`). |
| `kotlin-version-alignment` | `docs/gradle-build-system.md`, `docs/technical-debt-remediation.md` | Passed | Kotlin version targets and reference links reconciled with `gradle/libs.versions.toml` (`2.4.10`). |
| `node-version-alignment` | `apps/web/app/package.json`, `README.md`, `docs/getting-started.md` | Passed | Node.js version claims reconciled with `.nvmrc` (`24.19.0`) and root `package.json` (`>= 24.19.0`). |
| `relative-links-audit` | `docs/`, `README.md` | Passed | Relative links audited. Deployed route links validated as intentional. |
| `markdownlint` | Repository docs & READMEs | Passed | Executed `pnpm exec markdownlint-cli2` successfully with zero errors. |
| `doc-date-validation` | `docs/` | Passed | Executed `node scripts/check-doc-last-updated.mjs` successfully with zero date discrepancies. |
| `ci-local` / `just ci` | Repository | Passed | Repository CI simulation executed successfully. |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

- **Last Execution:** `2026-09-11T09:35:00Z`
- **Schema Version:** `1`
- **Task Identity:** `documentation-maintainer`
- **Run Identifier:** `documentation-maintainer-run-20260911-093500`
- **Execution Outcome:** `CHANGES_APPLIED`

## Risk Assessment

- **Overall Risk:** `LOW`
- All changes were evidence-backed documentation updates aligning Java and Kotlin references across module READMEs with project sources of truth (`AppConfiguration.kt`, `gradle/libs.versions.toml`, `docs/getting-started.md`).

## Human Review Notes

Changes are purely documentation updates aligning Java runtime/JDK requirements and Kotlin release notes across server, shared modules, and technical debt documentation.
