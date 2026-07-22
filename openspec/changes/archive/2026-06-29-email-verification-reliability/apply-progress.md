# Apply Progress: Email Verification Reliability

## Delivery

- Strategy: `feature-branch-chain` (explicitly approved by the user)
- Implemented slice: Work Units 1–3, including backend reliability/contracts, frontend global
  guidance, and media upload enforcement
- Intended boundary: PR 3 media backend/frontend gating on top of PR 2 under the approved
  feature-branch chain
- State remains apply in-progress because Phase 5 integrated verification is still pending

## Completed Tasks

- [x] 1.1 Added an application-context regression test proving a single `EventPublisher`/
  `EventEmitter`, active shared `EventConfiguration`, and verification-email consumption.
- [x] 1.2 Replaced the standalone publisher bean with one authoritative `EventEmitter<DomainEvent>`
  and imported shared subscriber wiring.
- [x] 1.3 Verified registration/resend auth tests and asserted resend dispatch uses the newest
  persisted token.
- [x] 2.1 Added persisted `PENDING` and `VERIFIED` profile-status regression coverage, including
  controller response coverage.
- [x] 2.2 Extended `CurrentUserProfile` and service mapping with persisted `EmailStatus`.
- [x] 2.3 Updated frontend `CurrentUserProfile` contract and auth store so `/api/auth/me` profile
  status overrides token-derived status; added resend helper/store action tests.
- [x] 3.1 Added AppShell cases for hidden verified banner, persistent unverified alert, non-verified
  statuses, resend success/error/loading states, and guidance copy.
- [x] 3.2 Implemented accessible app-shell `role="alert"` verification banner and resend flow using
  auth-store state plus `/api/auth/resend-verification`.
- [x] 3.3 Added EN/ES i18n keys and verified static key coverage.
- [x] 4.1 Added verified/unverified regression coverage for legacy create/upload and CAS
  register/binary flows, asserting denial occurs before asset, blob, rate-limit, or storage
  mutation.
- [x] 4.2 Added `UPLOAD_MEDIA` to the shared feature policy and enforced the gate at the start of
  every media mutation handler while leaving publish/social-connect gates unchanged.
- [x] 4.3 Disabled MediaLibrary upload for non-verified profiles, added localized verification
  guidance, and preserved the backend 403 problem code through API/store error mapping.

## TDD Evidence

### RED — Event Wiring

Command:

```text
./gradlew :server:smp:test --tests '*IdentityEventConfigurationTest'
```

Result: `FAILED` — `IdentityEventConfigurationTest` failed because shared `EventConfiguration` was
absent and the existing publisher was not the emitter required for subscriber registration.

### GREEN — Event Wiring

Command:

```text
./gradlew :server:smp:test --tests '*IdentityEventConfigurationTest'
```

Result: `BUILD SUCCESSFUL` — one publisher/emitter and active consumer dispatch confirmed.

### RED — Authoritative Profile

Command:

```text
./gradlew :server:smp:test --tests '*GetCurrentUserProfileServiceTest' --tests '*CurrentUserProfileControllerTest'
```

Result: `FAILED` during test compilation — `CurrentUserProfile.emailStatus` did not exist.

### GREEN — Combined Focused Backend Slice

Command:

```text
./gradlew :server:smp:cleanTest :server:smp:test --tests '*LocalAuth*' --tests '*EventConfiguration*' --tests '*GetCurrentUserProfileServiceTest' --tests '*CurrentUserProfileControllerTest'
```

Result: `BUILD SUCCESSFUL` — 35 focused tests passed after cleaning stale test output.

### RED — Frontend Contract and Banner

Initial command requested by task used unsupported pnpm syntax in this workspace:

```text
pnpm --dir apps/web/app vitest run src/lib/auth-api.test.ts src/stores/auth.test.ts src/components/layout/AppShell.test.ts src/i18n/i18n-keys.test.ts
```

Result: `ERR_PNPM_RECURSIVE_EXEC_FIRST_FAIL` — `Command "apps/web/app" not found`.

Focused equivalent command:

```text
pnpm --dir "apps/web/app" exec vitest run src/lib/auth-api.test.ts src/stores/auth.test.ts src/components/layout/AppShell.test.ts src/i18n/i18n-keys.test.ts
```

Result: `FAILED` — new tests failed because `resendVerification` and `auth.resendVerificationEmail`
did not exist, profile mapping kept token `emailStatus`, and AppShell had no unverified
`role="alert"` banner/resend states.

### GREEN — Frontend Contract and Banner

Command:

```text
pnpm --dir "apps/web/app" exec vitest run src/lib/auth-api.test.ts src/stores/auth.test.ts src/components/layout/AppShell.test.ts src/i18n/i18n-keys.test.ts
```

Result: `PASS` — 4 test files passed, 58 tests passed.

### RED — Media Upload Enforcement

Commands:

```text
./gradlew :server:smp:test --tests '*MediaCommandsTest' --tests '*MediaCasHandlersTest' --tests '*MediaAssetControllerTest'
pnpm --dir "apps/web/app" exec vitest run src/views/MediaLibraryView.test.ts
```

Results: backend `FAILED` during test compilation because `AuthFeature.UPLOAD_MEDIA` and
verification-aware media-handler constructors did not exist (the run also exposed a pre-existing
generic cast compilation issue in `IdentityEventConfigurationTest`); frontend `FAILED` with 3 new
regression failures because upload was enabled and no verification guidance existed.

### GREEN — Media Upload Enforcement

Commands:

```text
./gradlew :server:smp:cleanTest :server:smp:test --tests '*MediaCommandsTest' --tests '*MediaCasHandlersTest' --tests '*MediaAssetControllerTest' --tests '*IdentityProblemDetailsHandlerTest'
pnpm --dir "apps/web/app" exec vitest run src/views/MediaLibraryView.test.ts src/i18n/i18n-keys.test.ts
```

Results: backend `BUILD SUCCESSFUL`; frontend `PASS` — 2 files and 12 tests passed. The focused
backend run includes the shared 403 `EMAIL_VERIFICATION_REQUIRED` mapper. A minimal explicit
`EventEmitter<DomainEvent>` cast repaired the unrelated Work Unit 1 test-compilation blocker without
changing production behavior.

## Remaining Tasks

- [ ] Phase 5 integrated verification
