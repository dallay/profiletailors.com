# Apply Progress: Email Verification Reliability

## Delivery

- Strategy: `feature-branch-chain` (explicitly approved by the user)
- Implemented slice: Work Unit 1 / backend Phases 1 and 2 only
- Intended boundary: backend event reliability and authoritative `/api/auth/me` email-status contract
- Frontend and media gating remain out of scope for this batch

## Completed Tasks

- [x] 1.1 Added an application-context regression test proving a single `EventPublisher`/`EventEmitter`, active shared `EventConfiguration`, and verification-email consumption.
- [x] 1.2 Replaced the standalone publisher bean with one authoritative `EventEmitter<DomainEvent>` and imported shared subscriber wiring.
- [x] 1.3 Verified registration/resend auth tests and asserted resend dispatch uses the newest persisted token.
- [x] 2.1 Added persisted `PENDING` and `VERIFIED` profile-status regression coverage, including controller response coverage.
- [x] 2.2 Extended `CurrentUserProfile` and service mapping with persisted `EmailStatus`.

## TDD Evidence

### RED — Event Wiring

Command:

```text
./gradlew :server:smp:test --tests '*IdentityEventConfigurationTest'
```

Result: `FAILED` — `IdentityEventConfigurationTest` failed because shared `EventConfiguration` was absent and the existing publisher was not the emitter required for subscriber registration.

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

## Remaining Tasks

- [ ] 2.3 Frontend auth API/store authoritative status
- [ ] Phase 3 global verification guidance
- [ ] Phase 4 media upload enforcement
- [ ] Phase 5 integrated verification
