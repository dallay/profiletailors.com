# Verification Report: Email Verification Reliability

## Change

- Change: `email-verification-reliability`
- Mode: OpenSpec
- Verified scope: Work Units 1–3 / tasks 1.1–4.3, plus Phase 5 focused integration commands where
  reasonable.
- Verdict: PASS WITH WARNINGS

## Completeness

| Area                                | Required                                                                                           | Evidence                                                                                                                                                                 | Status |
|-------------------------------------|----------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
| Event dispatch reliability          | Registration and resend use active `SendVerificationEmailConsumer` via shared `EventConfiguration` | `IdentityEventConfiguration` imports `EventConfiguration` and exposes a single `EventEmitter<DomainEvent>`; focused backend tests passed                                 | PASS   |
| `/api/auth/me` authoritative status | Profile includes persisted `emailStatus`                                                           | `CurrentUserProfile` / `GetCurrentUserProfileService` map persisted `PrincipalIdentityFacts.emailStatus`; controller/service tests passed                                | PASS   |
| SPA auth store authoritative status | Store uses profile status over token heuristics                                                    | `mapProfileToUser()` sets `emailStatus` from profile; auth store tests passed                                                                                            | PASS   |
| Global banner                       | Accessible localized banner with resend states; hidden for verified                                | `AppShell.vue` tests passed for `role="alert"`, resend loading/success/error, PENDING/BOUNCED visibility, VERIFIED hidden                                                | PASS   |
| Media upload backend gate           | Legacy + CAS upload paths deny unverified users before mutation                                    | Media command/CAS/controller tests passed; code gates `CreateUploadedAssetHandler`, `UploadAssetHandler`, `PutAssetHandler`, `CasUploadAssetHandler` with `UPLOAD_MEDIA` | PASS   |
| MediaLibrary frontend gate          | Upload disabled and guidance shown for unverified users                                            | `MediaLibraryView.test.ts` and `media-api.test.ts` passed                                                                                                                | PASS   |
| Existing publish/social gates       | Publishing and social-connect gates remain intact                                                  | Focused backend run included `*PublishingHandlersTest` and `*SocialConnection*`; backend fast suite passed                                                               | PASS   |

## Commands and Results

| Command                                                                                                                                                                                                                                                                                                                                                                                                      | Result                                        |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------|
| `./gradlew :server:smp:cleanTest :server:smp:test --tests '*LocalAuth*' --tests '*EventConfiguration*' --tests '*GetCurrentUserProfileServiceTest' --tests '*CurrentUserProfileControllerTest' --tests '*MediaCommandsTest' --tests '*MediaCasHandlersTest' --tests '*MediaAssetControllerTest' --tests '*IdentityProblemDetailsHandlerTest' --tests '*PublishingHandlersTest' --tests '*SocialConnection*'` | `BUILD SUCCESSFUL` in 24s                     |
| `pnpm --dir "apps/web/app" exec vitest run src/lib/auth-api.test.ts src/stores/auth.test.ts src/components/layout/AppShell.test.ts src/i18n/i18n-keys.test.ts src/views/MediaLibraryView.test.ts src/lib/media-api.test.ts`                                                                                                                                                                                  | 6 files passed, 109 tests passed              |
| `just backend-test-fast`                                                                                                                                                                                                                                                                                                                                                                                     | `BUILD SUCCESSFUL` in 57s                     |
| `just frontend-test`                                                                                                                                                                                                                                                                                                                                                                                         | Marketing app tests passed: 4 files, 23 tests |
| `pnpm --dir "apps/web/app" exec vitest run`                                                                                                                                                                                                                                                                                                                                                                  | App suite passed: 67 files, 700 tests         |

## Spec Compliance Matrix

| Spec scenario                                         | Covering runtime evidence                                                                                                         | Status |
|-------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|--------|
| Registration runtime has verification consumer active | `*EventConfiguration*`, `*LocalAuth*` focused backend tests; `just backend-test-fast`                                             | PASS   |
| Resend uses active consumer path                      | `*LocalAuth*`, `*EventConfiguration*` focused backend tests; `just backend-test-fast`                                             | PASS   |
| Registration triggers deliverable verification email  | `*LocalAuth*` focused backend tests assert token creation/event publication; active consumer test dispatches through email sender | PASS   |
| Resend replaces active verification email             | `*LocalAuth*` focused backend tests assert token invalidation and newest-token event publication                                  | PASS   |
| Unverified profile returns authoritative status       | `*GetCurrentUserProfileServiceTest`, `*CurrentUserProfileControllerTest`; auth API/store Vitest                                   | PASS   |
| Verified profile returns authoritative status         | Same profile/controller/store coverage                                                                                            | PASS   |
| Unverified user sees global banner                    | `AppShell.test.ts`                                                                                                                | PASS   |
| Verified user does not see banner                     | `AppShell.test.ts`                                                                                                                | PASS   |
| Resend action visible from banner                     | `AppShell.test.ts`                                                                                                                | PASS   |
| Unverified user cannot create/upload media            | `MediaCommandsTest`, `MediaCasHandlersTest`, `MediaAssetControllerTest`; `MediaLibraryView.test.ts`                               | PASS   |
| Verified user can proceed with media upload flow      | `MediaCasHandlersTest` and normal media test suite                                                                                | PASS   |
| Unverified user cannot publish/connect                | `PublishingHandlersTest` / social connection focused filters; `just backend-test-fast`                                            | PASS   |
| Verified user can use publishing capabilities         | Existing publishing handler coverage and backend fast suite                                                                       | PASS   |

## Correctness / Design Coherence

| Finding                                                                 | Evidence                                                                                                                                         | Severity | Status    |
|-------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|----------|-----------|
| Shared event wiring is reused instead of alternate subscriber path      | `IdentityEventConfiguration` imports shared `EventConfiguration`; `domainEventEmitter()` is single publisher/emitter                             | None     | Confirmed |
| Profile response is authoritative                                       | `GetCurrentUserProfileService` uses persisted identity facts for `emailStatus`; frontend profile mapping replaces token status                   | None     | Confirmed |
| Backend media gate is authoritative                                     | Media mutation handlers call `requireEmailVerification(..., AuthFeature.UPLOAD_MEDIA)` before mutation paths tested by in-memory repo assertions | None     | Confirmed |
| Frontend media/app-shell guidance is secondary UX, not sole enforcement | Backend focused tests and frontend Vitest both passed                                                                                            | None     | Confirmed |

## Issues

### CRITICAL

None.

### WARNING

- Manual Mailpit/browser verification from task 5.2 was not executed because it requires
  `just infra-up` and live local services; automated focused/backend/frontend suites passed and
  cover the required behavior at runtime.
- `just frontend-test` currently runs the marketing app tests only; the dashboard app was verified
  separately with `pnpm --dir "apps/web/app" exec vitest run`.
- pnpm emitted a workspace warning that `pnpm.onlyBuiltDependencies` in `package.json` is ignored by
  the current pnpm version. This did not fail tests and appears unrelated to this change.

### SUGGESTION

- Consider adding a later narrow Playwright smoke for the full unverified flow once local
  infra/Mailpit is available.
