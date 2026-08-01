# Feature Flag Consistency Audit Report

## Purpose

The Feature Flag Auditor Agent has audited the feature flags, platform hooks, and environment toggles across both the frontend and backend of the Profile Tailors repository to verify structural integrity, configuration alignment, and compliance with the verified data inventory.

## Execution Result

The audit concluded with **CHANGES_APPLIED**. All state and report files have been successfully updated to record the status of the repository's feature flags. The stale `pt-dashboard-new` feature flag and toggle were successfully removed from the codebase.

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

- Reconciled and removed stale local storage feature flag and dead toggle UI for `pt-dashboard-new` in `apps/web/app/src/modules/dashboard/presentation/views/HomeView.vue`.
- Updated the compact machine-readable audit state file: `.agents/automation/state/feature-flag-audit.yaml`
- Created the comprehensive audit report: `.agents/automation/reports/feature-flag-audit.md`

## Evidence Table

| Feature Flag ID | Flag Name / Key | Scope | Expected Behavior / Intent | Observed Behavior | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **FF-DASHBOARD-STALE** | `pt-dashboard-new` | Frontend (Vue) | Toggle to restore old legacy dashboard layout during development | Legacy dashboard has been completely deleted. UI toggle is dead/stale control rendering a static text block. Resolved by removing dead flag and UI code. | **RESOLVED** |
| **FF-WAITLIST-CONSISTENT** | `WAITLIST_ENABLED` | Frontend (Astro) | Controls waitlist capture form; must be `false` until compliance is approved | Compliant. Defaults to `false` in `.env.example` and `astro.config.mjs` and successfully verified against the marketing site. | **CONSISTENT** |
| **FF-RATE-LIMIT-DRIFT** | `SMP_PLATFORM_RATE_LIMIT_ENABLED` | Backend (Spring) | Toggle platform-wide rate-limiting hook | Defaults to `true` in `application.yaml` if omitted, but explicitly set to `false` in `.env.example`. | **INCONSISTENT** |
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

## Unresolved Findings

- **FF-RATE-LIMIT-DRIFT**: Configuration-default drift detected. `SMP_PLATFORM_RATE_LIMIT_ENABLED` defaults to `true` in `application.yaml`, but is explicitly defined as `false` in `.env.example`. This should be unified to prevent unintended behavior in non-standard run environments.

## Blockers

None.

## Automation State

- **schemaVersion**: `1`
- **Task**: `feature-flag-auditor`
- **lastExecution**: `2026-08-01T18:15:00Z`
- **Result Status**: `CHANGES_APPLIED`

## Risk Assessment

- **Overall Risk**: **LOW** (Changes are strictly limited to the state, report, and stale toggle code deletion, preserving existing codebase behavior and rollout intent).

## Human Review Notes

All major feature flags (such as the waitlist compliance gate and Unsplash media provider hooks) are highly consistent with documentation. The stale frontend toggle (`pt-dashboard-new`) was successfully resolved and removed. The backend rate-limiting default mismatch (`SMP_PLATFORM_RATE_LIMIT_ENABLED`) remains as an unresolved finding to be addressed in subsequent maintenance.
