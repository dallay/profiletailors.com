# Tasks: Email Verification Reliability

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 650–900 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 backend reliability/contracts → PR 2 frontend UX → PR 3 media gating/E2E |
| Delivery strategy | feature-branch-chain |
| Chain strategy | feature-branch-chain |

Decision needed before apply: Resolved — user approved feature-branch-chain
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Event dispatch and authoritative profile | PR 1 | Backend focused tests included |
| 2 | Store contract and global banner | PR 2 | Base PR 1; Vitest/i18n included |
| 3 | Media gates and integrated verification | PR 3 | Base PR 2; preserves publish/connect |

## Phase 1: Event and Email Reliability

- [x] 1.1 RED: add context/subscription regression coverage for one `EventPublisher<DomainEvent>` and active `SendVerificationEmailConsumer` in `server/smp/src/test/kotlin/com/profiletailors/smp/identity/infrastructure/`.
- [x] 1.2 GREEN: import shared `EventConfiguration` and reuse `EventEmitter` in `SmpApplication.kt` / `IdentityEventConfiguration.kt`; remove duplicate publisher ambiguity only as required.
- [x] 1.3 VERIFY: extend `LocalAuthControllerTest.kt`/`LocalAuthHandlersTest.kt` so registration and resend dispatch the newest token through the same consumer; run `./gradlew :server:smp:test --tests '*LocalAuth*' --tests '*EventConfiguration*'`.

## Phase 2: Authoritative Profile Contract

- [x] 2.1 RED: update `GetCurrentUserProfileServiceTest.kt` and `CurrentUserProfileControllerTest.kt` for unverified/verified `emailStatus` from persisted identity.
- [x] 2.2 GREEN: add status to `CurrentUserProfile.kt` and map it in `GetCurrentUserProfileService.kt`; minimally satisfy `/api/auth/me` tests.
- [ ] 2.3 RED/GREEN/VERIFY: update `auth-api.test.ts` and `auth.test.ts` first, then `auth-api.ts`/`auth.ts` so profile status overrides token heuristics; run `pnpm --dir apps/web/app vitest run src/lib/auth-api.test.ts src/stores/auth.test.ts`.

## Phase 3: Global Verification Guidance

- [ ] 3.1 RED: add `AppShell.test.ts` cases for hidden verified banner, persistent unverified alert, resend loading/success/error states, and verification guidance.
- [ ] 3.2 GREEN: implement the accessible `role="alert"` banner/resend flow in `AppShell.vue` using auth-store state and the existing resend endpoint.
- [ ] 3.3 RED/GREEN/VERIFY: add EN/ES keys in `src/i18n/index.ts`, update `i18n-keys.test.ts`, then run `pnpm --dir apps/web/app vitest run src/components/layout/AppShell.test.ts src/i18n/i18n-keys.test.ts`.

## Phase 4: Media Upload Enforcement

- [ ] 4.1 RED: add unverified/verified cases to `MediaCommandsTest.kt`, `MediaCasHandlersTest.kt`, and `MediaAssetControllerTest.kt` covering legacy create/upload plus CAS register/binary/confirm paths and zero persisted assets on denial.
- [ ] 4.2 GREEN: add `UPLOAD_MEDIA` policy and call `requireEmailVerification` in all relevant `media/application` handlers/configuration; retain existing publish/social-connect gates unchanged.
- [ ] 4.3 RED/GREEN/VERIFY: update `MediaLibraryView.test.ts` first, then `MediaLibraryView.vue`/`stores/media.ts` for disabled upload and 403 guidance; run focused media Gradle tests and `pnpm --dir apps/web/app vitest run src/views/MediaLibraryView.test.ts`.

## Phase 5: Integrated Verification

- [ ] 5.1 Run `just backend-test-fast` and `just frontend-test`; do not run broad builds unless focused failures require compilation beyond these suites.
- [ ] 5.2 With `just infra-up`, verify registration/resend delivery and replacement token links in Mailpit, then verify banner refresh and publish/connect/upload 403 behavior; run the narrow existing Playwright auth/media specs where feasible and record environmental gaps.
