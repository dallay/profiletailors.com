# Feature Flag Consistency Audit Report

## Purpose

The Feature Flag Auditor Agent has audited the feature flags, platform hooks, and environment toggles across both the frontend and backend of the Profile Tailors repository to verify structural integrity, configuration alignment, and compliance with the verified data inventory.

## Execution Result

The audit concluded with **NO_DRIFT_DETECTED**. All state and report files have been successfully updated to record the status of the repository's feature flags. The stale `pt-dashboard-new` feature flag and toggle were previously resolved and removed. The default fallback configuration drift for `SMP_PLATFORM_RATE_LIMIT_ENABLED` was successfully resolved by aligning the default value in `application.yaml` to `false` (matching `.env.example`).

## Scope Inspected

- **Frontend Applications (`apps/web/`)**:
  - `apps/web/app/src/modules/dashboard/presentation/views/HomeView.vue` (checked and removed `pt-dashboard-new` localStorage flag)
  - `apps/web/marketing/src/components/Hero.astro` and `astro.config.mjs` (checked `WAITLIST_ENABLED` compile/runtime flag)
  - `apps/web/app/src/modules/publishing/presentation/components/CreatePostModal.vue` (checked `provider` prop for Unsplash media provider integration)
- **Backend Application (`server/smp/`)**:
  - `server/smp/src/main/resources/application.yaml` (checked `SMP_PLATFORM_RATE_LIMIT_ENABLED`, `SMP_PLATFORM_AUDIT_ENABLED`, `SMP_PLATFORM_METRICS_ENABLED`, `SMP_REGISTRATION_ENABLED`, `SMP_PUBLISHING_WORKER_ENABLED`, and `SMP_MEDIAPROVIDER_UNSPLASH_ENABLED`)
- **Environment & Compliance Documentation**:
  - `.env.example` (checked environment variable definitions and default assignments)
  - `docs/compliance/data-inventory.yaml` and `docs/compliance/data-inventory.md` (checked listed device storage keys and compliance requirements)

## Changes Applied

- Updated the compact machine-readable audit state file: `.agents/automation/state/feature-flag-auditor.yaml`
- Updated the comprehensive audit report: `.agents/automation/reports/feature-flag-auditor.md`

## Evidence Table

| Feature Flag ID | Flag Name / Key | Scope | Expected Behavior / Intent | Observed Behavior | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **FF-DASHBOARD-STALE** | `pt-dashboard-new` | Frontend (Vue) | Toggle to restore old legacy dashboard layout during development | Legacy dashboard was completely deleted, leaving the UI toggle as a dead/stale control rendering a static text block. Flag and toggle code have been removed; HomeView.vue unconditionally renders DashboardLayout. | **RESOLVED** |
| **FF-WAITLIST-CONSISTENT** | `WAITLIST_ENABLED` | Frontend (Astro) | Controls waitlist capture form; must be `false` until compliance is approved | Compliant. Defaults to `false` in `.env.example` and `astro.config.mjs` and successfully verified against the marketing site. | **CONSISTENT** |
| **FF-RATE-LIMIT-DRIFT** | `SMP_PLATFORM_RATE_LIMIT_ENABLED` | Backend (Spring) | Toggle platform-wide rate-limiting hook | Fallback default aligned to `false` in `application.yaml` matching `.env.example` to ensure local environments behave consistently. | **RESOLVED** |
| **FF-AUDIT-METRICS-CONSISTENT** | `SMP_PLATFORM_AUDIT_ENABLED`, `SMP_PLATFORM_METRICS_ENABLED` | Backend (Spring) | Control audit event logging and prometheus metrics exporter hooks | Consistent. Both default to `false` in `application.yaml` when not configured, and are explicitly `false` in `.env.example`. | **CONSISTENT** |
| **FF-UNSPLASH-CONSISTENT** | `SMP_MEDIAPROVIDER_UNSPLASH_ENABLED`, `provider` | Backend & Frontend | Gated Unsplash integration (data inventory PA-008) | Consistent. Disabled by default in both backend YAML and frontend post-composer configuration. | **CONSISTENT** |
| **FF-REGISTRATION-CONSISTENT** | `SMP_REGISTRATION_ENABLED` | Backend (Spring) | Controls public self-service registration endpoint | Consistent. Defaults to `false` in `application.yaml` (line 51) and explicitly set to `false` in `.env.example` (line 114). | **CONSISTENT** |
| **FF-PUBLISHING-WORKER-CONSISTENT** | `SMP_PUBLISHING_WORKER_ENABLED` | Backend (Spring) | Controls the background publishing worker process | Consistent. Defaults to `false` in `application.yaml` (line 112) and explicitly set to `false` in `.env.example` (line 117). | **CONSISTENT** |

## Validation Table

| Check Name | Target Bounded Context / Command | Outcome | Details |
| :--- | :--- | :--- | :--- |
| Feature Flag Frontend Inspection | `apps/web/app/` / Static Analysis | **Passed** | Inspected Vue components and Astro template files for flag usages. |
| Feature Flag Backend Inspection | `server/smp/` / Static Analysis | **Passed** | Inspected `application.yaml` and properties classes for environment placeholders. |
| Environment Template Alignment Check | `.env.example` / Comparative Review | **Passed** | Compared `.env.example` default values with backend defaults. |
| Data Inventory Compliance Verification | `docs/compliance/data-inventory.yaml` / Verification | **Passed** | Cross-referenced client-side cookie and local storage registers. |
| Frontend Unit Testing Check | `apps/web/app` / `pnpm --filter app run test:run` | **Passed** | Full Vitest suite executes and passes cleanly. |
| Backend Fast Testing Check | `server/smp` / `just backend-test-fast` | **Passed** | Backend fast test suite executes and passes cleanly with configuration aligned. |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

- **schemaVersion**: `1`
- **Task**: `feature-flag-auditor`
- **lastExecution**: `2026-08-22T17:59:37Z`
- **Result Status**: `NO_DRIFT_DETECTED`

## Risk Assessment

- **Overall Risk**: **LOW** (No feature flag drift detected; state and report files updated cleanly).

## Human Review Notes

All major feature flags (such as the waitlist compliance gate and Unsplash media provider hooks) are highly consistent with documentation and configuration defaults. No drift was detected during this execution pass.
