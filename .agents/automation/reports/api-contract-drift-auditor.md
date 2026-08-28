# API Contract Drift Auditor Report

## Purpose

Audit HTTP contract interfaces between frontend application Pinia stores/API services and the Spring Boot backend REST controllers to detect drift.

## Execution Result

NO_DRIFT_DETECTED. Audited all REST controllers across Spring Boot backend modules and corresponding HTTP client calls/stores in Vue frontend apps (`apps/web/app`, `apps/web/admin`, `apps/web/marketing`). All HTTP methods, route paths, query parameters, DTO payloads, and status codes are aligned.

## Scope Inspected

- **Backend Controllers:**
  - `com.profiletailors.smp.identity.infrastructure.http.LocalAuthController` (`/api/auth/*`)
  - `com.profiletailors.smp.identity.infrastructure.http.CurrentUserProfileController` (`/api/auth/me`)
  - `com.profiletailors.smp.identity.infrastructure.http.PublicCapabilitiesController` (`/api/capabilities/public`)
  - `com.profiletailors.smp.identity.infrastructure.http.AccountLifecycleController` (`/api/v1/account/close`)
  - `com.profiletailors.smp.tenancy.infrastructure.http.WorkspaceController` (`/api/tenancy/workspaces`)
  - `com.profiletailors.smp.publishing.infrastructure.http.PublishingControllers` (`/api/publishing/*`, `/api/v1/workspaces/{workspaceId}/recurring`)
  - `com.profiletailors.smp.media.infrastructure.http.MediaAssetController` (`/api/media/assets/*`)
  - `com.profiletailors.smp.media.infrastructure.http.UnsplashMediaProviderController` (`/api/media/providers/unsplash/*`)
  - `com.profiletailors.smp.ideas.infrastructure.http.IdeasController` (`/api/ideas/*`)
  - `com.profiletailors.smp.privacy.infrastructure.http.PrivacyController` (`/api/v1/privacy/requests/*`)
  - `com.profiletailors.smp.governance.infrastructure.http.ConsentController` (`/api/governance/consent`)
  - `com.profiletailors.smp.platformadmin.infrastructure.http.*` (`/api/admin/*`, `/api/invitations/*`)
- **Frontend Stores & API Clients:**
  - `apps/web/app/src/modules/auth/infrastructure/auth-api.ts`
  - `apps/web/app/src/modules/auth/infrastructure/auth.store.ts`
  - `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts`
  - `apps/web/app/src/modules/publishing/services/ai-content-api.ts`
  - `apps/web/app/src/modules/publishing/services/hashtag-api.ts`
  - `apps/web/app/src/modules/media/services/media-api.ts`
  - `apps/web/app/src/modules/media/infrastructure/media.store.ts`
  - `apps/web/app/src/modules/ideas/infrastructure/ideas.store.ts`
  - `apps/web/app/src/modules/settings/infrastructure/privacy.store.ts`
  - `apps/web/app/src/modules/settings/infrastructure/consent.store.ts`
  - `apps/web/admin/src/stores/auth.store.ts` & Admin views
  - `apps/web/marketing/src/components/WaitlistForm.astro`

## Changes Applied

None.

## Evidence Table

| Area | Backend Endpoint | Frontend Endpoint / Client | Drift Status | Verification Notes |
| :--- | :--- | :--- | :--- | :--- |
| Auth | POST `/api/auth/register`, `/login`, `/refresh`, `/logout`, `/verify-email`, `/forgot-password`, `/reset-password` | `auth-api.ts` | Aligned | Payload schemas and path parameters match DTO definitions. |
| User Profile | GET `/api/auth/me` | `auth-api.ts` (`fetchCurrentUserProfile`) | Aligned | Profile response fields map to `CurrentUserProfile`. |
| Tenancy | GET `/api/tenancy/workspaces`, POST `/current/name`, `/current/icon` | `auth-api.ts` (`fetchWorkspaces`, etc.) | Aligned | DTO structure aligns with `WorkspaceSummary`. |
| Account Closure | POST `/api/v1/account/close` | `auth-api.ts` (`closeAccount`) | Aligned | Path and method match backend route. |
| Media Assets | GET/POST/PUT/DELETE `/api/media/assets/*`, `/providers/unsplash/*` | `media-api.ts`, `media.store.ts` | Aligned | Upload, import, preview, and listing endpoints align. |
| Ideas | GET/POST/PATCH/DELETE `/api/ideas/*` | `ideas.store.ts` | Aligned | Idea CRUD, move, convert, and column operations match controller mapping. |
| Publishing | GET/POST/PATCH/DELETE `/api/publishing/*`, `/api/v1/workspaces/{id}/recurring` | `publishing.store.ts` | Aligned | Quick-create, reschedule, retry, channel, and recurring schedule contracts match. |
| Privacy DSAR | POST/GET `/api/v1/privacy/requests/*` | `privacy.store.ts` | Aligned | Request DTO maps flat `newEmail`/`newUsername` fields as required by backend. |
| Consent | POST `/api/governance/consent`, `/withdraw` | `consent.store.ts` | Aligned | Event payloads map subject and policy parameters correctly. |
| Admin | GET/POST `/api/admin/*`, `/api/invitations/*` | `apps/web/admin/src/` | Aligned | Operator sessions, user lists, waitlist entries, and audit events match. |

## Validation Table

| Check Name | Target | Status | Notes |
| :--- | :--- | :--- | :--- |
| backend-fast-tests | server/smp | Passed | Fast unit tests for backend controllers passed cleanly. |
| frontend-unit-tests | apps/web/app | Passed | 125 test files / 1530 tests passed in frontend app unit tests. |
| frontend-lint-check | apps/web/app | Passed | Biome lint check completed across frontend web application files. |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

- **Last Execution:** `2026-03-30T19:00:00Z`
- **Schema Version:** `1`
- **Task Identity:** `api-contract-drift-auditor`

## Risk Assessment

- **Overall Risk:** LOW (No drift detected; audit verified complete contract alignment).

## Human Review Notes

Comprehensive audit completed across all backend HTTP endpoints and frontend client stores/services. No contract drift detected.
