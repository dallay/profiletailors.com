# Tasks: Reusable Lead-Capture Waitlist

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 900–1300 (shared + server + DB + form + tests) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (shared) → PR 2 (server + DB) → PR 3 (form + verify) |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Foundation: shared/lead-capture modules + domain + ports | PR 1 → main | Pure Kotlin; ArchUnit boundary guard |
| 2 | Server adapters: Liquibase + R2DBC + HTTP + rate-limit | PR 2 → main | Depends on PR 1; backend-only |
| 3 | Marketing form + E2E + verification | PR 3 → main | Depends on PR 2; cross-stack |

## Phase 1: Foundation / Shared Module Boundaries

- [ ] 1.1 **RED** — `ArchUnit` test asserting `com.profiletailors.leadcapture.common` has zero deps on `server/*` or `org.springframework.*`. Files: `shared/lead-capture/common/build.gradle.kts`, `shared/lead-capture/common/src/test/kotlin/com/profiletailors/leadcapture/common/LeadCaptureModuleBoundariesTest.kt`. Verify: `:shared:lead-capture:common:test` fails (module unconfigured).
- [ ] 1.2 **GREEN** — Wire `shared/lead-capture/common` + `waitlist` with `com.profiletailors.kotlin.library` only (no Spring, no R2DBC); `waitlist` depends on `common`. Files: `shared/lead-capture/common/build.gradle.kts`, `shared/lead-capture/waitlist/build.gradle.kts`. Verify: ArchUnit passes; both compile.
- [ ] 1.3 **RED** — Contract test scanning `shared/lead-capture/**/src/main/kotlin/**` for forbidden imports (`org.springframework.*`, `io.r2dbc.*`, `com.profiletailors.smp.*`). File: `shared/lead-capture/common/src/test/kotlin/com/profiletailors/leadcapture/common/SharedModuleManifestTest.kt`. Verify: fails (no source yet).
- [ ] 1.4 **GREEN** — Activate `SharedModuleManifestTest` + `forbiddenApis` on both modules. Files: `shared/lead-capture/{common,waitlist}/build.gradle.kts`, `shared/lead-capture/waitlist/src/test/kotlin/com/profiletailors/leadcapture/waitlist/SharedModuleManifestTest.kt`. Verify: `:shared:lead-capture:{common,waitlist}:test` green.

## Phase 2: Domain (`shared/lead-capture/common` + `waitlist`)

- [ ] 2.1 **RED** — VO tests: `EmailAddress`, `NormalizedEmail`, `CaptureSource`, `CaptureLocale`, `LeadMetadata` — valid/invalid, length caps (320/50/10), whitelist enforcement, original preservation. Files: `shared/lead-capture/common/src/test/kotlin/com/profiletailors/leadcapture/common/vo/{EmailAddressTest,NormalizedEmailTest,CaptureSourceTest,CaptureLocaleTest,LeadMetadataTest}.kt`.
- [ ] 2.2 **GREEN** — Implement VOs (whitelist keys `utm_source|utm_medium|utm_campaign|page_url|page_title`, ≤5 entries, 50-char keys, 200-byte values, HTML strip). Files: `shared/lead-capture/common/src/main/kotlin/com/profiletailors/leadcapture/common/vo/{EmailAddress,NormalizedEmail,CaptureSource,CaptureLocale,LeadMetadata}.kt`.
- [ ] 2.3 **RED** — Tests for `WaitlistStatus` transitions (`draft→active→paused↔active→closed→archived`) + `Waitlist` invariants (unique key, `active` required). Files: `shared/lead-capture/waitlist/src/test/kotlin/com/profiletailors/leadcapture/waitlist/domain/{WaitlistStatusTest,WaitlistTest}.kt`.
- [ ] 2.4 **GREEN** — Implement `WaitlistStatus`, `WaitlistId`/`WaitlistKey`, `Waitlist` (extends `com.profiletailors.common.domain.AggregateRoot`). Files: `shared/lead-capture/waitlist/src/main/kotlin/com/profiletailors/leadcapture/waitlist/domain/{WaitlistStatus,WaitlistId,WaitlistKey,Waitlist}.kt`.
- [ ] 2.5 **RED** — Tests for `WaitlistEntry`, `WaitlistEntryStatus` (`pending→invited→converted | pending→cancelled`), `WaitlistConsent` (earlyAccess required, marketing opt-in), lifecycle timestamps. Files: `shared/lead-capture/waitlist/src/test/kotlin/com/profiletailors/leadcapture/waitlist/domain/{WaitlistEntryTest,WaitlistEntryStatusTest,WaitlistConsentTest}.kt`.
- [ ] 2.6 **GREEN** — Implement `WaitlistEntryStatus`, `WaitlistConsent`, `WaitlistEntry`, `WaitlistEntryId`, `markUpdated()`. Files: `shared/lead-capture/waitlist/src/main/kotlin/com/profiletailors/leadcapture/waitlist/domain/{WaitlistEntryStatus,WaitlistConsent,WaitlistEntry,WaitlistEntryId}.kt`.

## Phase 3: Application (`shared/lead-capture/waitlist`)

- [ ] 3.1 **RED** — Port tests with failing fake: `WaitlistRepository` (findByKey, listActive) + `WaitlistEntryRepository` (insertIfAbsent, findByWaitlistAndNormalizedEmail, count). Files: `shared/lead-capture/waitlist/src/test/kotlin/com/profiletailors/leadcapture/waitlist/application/{WaitlistRepositoryTest,WaitlistEntryRepositoryTest}.kt`.
- [ ] 3.2 **GREEN** — Define ports as reactive (`reactor.core.publisher.Mono/Flux`) `interface`s — NO Spring stereotypes on shared side. Files: `shared/lead-capture/waitlist/src/main/kotlin/com/profiletailors/leadcapture/waitlist/application/{WaitlistRepository,WaitlistEntryRepository}.kt`.
- [ ] 3.3 **RED** — Handler tests: new → `Joined`; duplicate → `Joined(existing)` no row; `draft|paused|closed|archived` → `WaitlistClosedException`; `earlyAccess=false` → `ConsentRequiredException`. File: `shared/lead-capture/waitlist/src/test/kotlin/com/profiletailors/leadcapture/waitlist/application/JoinWaitlistHandlerTest.kt`.
- [ ] 3.4 **GREEN** — Implement `JoinWaitlistCommand`, `JoinWaitlistResult`, exceptions, `JoinWaitlistHandler` with dedupe via `findByWaitlistAndNormalizedEmail` + `insertIfAbsent`. Files: `shared/lead-capture/waitlist/src/main/kotlin/com/profiletailors/leadcapture/waitlist/application/{JoinWaitlistCommand,JoinWaitlistResult,JoinWaitlistHandler,WaitlistExceptions}.kt`.

## Phase 4: Persistence (`server/smp` R2DBC)

- [ ] 4.1 **RED** — Liquibase parser test asserting changelogs exist, contain `UNIQUE(waitlist_id, email_normalized)`, and are included in `db.changelog-master.yaml`. File: `server/smp/src/test/resources/db/changelog/leadcapture/LeadcaptureChangelogTest.kt`.
- [ ] 4.2 **GREEN** — Create `001-create-waitlists.yaml` (PK + UNIQUE `key`), `002-create-waitlist-entries.yaml` (FK + UNIQUE `(waitlist_id, email_normalized)` + indexes), `003-seed-waitlists.yaml` (insert `profile-tailors-launch` active); include in master. Files: `server/smp/src/main/resources/db/changelog/leadcapture/{001-create-waitlists,002-create-waitlist-entries,003-seed-waitlists}.yaml`, `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml`.
- [ ] 4.3 **RED** — R2DBC repo tests (Testcontainers Postgres): findByKey hit/miss; `insertIfAbsent` returns new id first time, existing id second time, no new row. Files: `server/smp/src/test/kotlin/com/profiletailors/smp/leadcapture/infrastructure/persistence/{R2dbcWaitlistRepositoryTest,R2dbcWaitlistEntryRepositoryTest}.kt`.
- [ ] 4.4 **GREEN** — Implement repos with `DatabaseClient`; map VOs to columns; wire as beans in `LeadcaptureConfiguration`. Files: `server/smp/src/main/kotlin/com/profiletailors/smp/leadcapture/infrastructure/persistence/{R2dbcWaitlistRepository,R2dbcWaitlistEntryRepository}.kt`, `server/smp/src/main/kotlin/com/profiletailors/smp/leadcapture/infrastructure/config/LeadcaptureConfiguration.kt`, `server/smp/build.gradle.kts`.

## Phase 5: HTTP Adapter (`server/smp`)

- [ ] 5.1 **RED** — `WebTestClient` tests for `POST /api/waitlists/{waitlistKey}/entries`: 200/202 new+duplicate, 400 (`invalid_email`/`consent_required`/`invalid_metadata`), 404 (`waitlist_not_found`), 409 (`waitlist_closed`), 429 (`rate_limited`). File: `server/smp/src/test/kotlin/com/profiletailors/smp/leadcapture/infrastructure/http/WaitlistControllerTest.kt`.
- [ ] 5.2 **GREEN** — `WaitlistController` (`@RestController` reactive), DTOs, Bean Validation (`@Email`, `@NotBlank`, `@Size`), `@ExceptionHandler` mapping shared exceptions. Identical 200/202 payload for new and duplicate. Files: `server/smp/src/main/kotlin/com/profiletailors/smp/leadcapture/infrastructure/http/{WaitlistController,WaitlistDtos,WaitlistExceptionHandler}.kt`.

## Phase 6: Security / Rate Limit

- [ ] 6.1 **RED** — Integration test: 11th request to `/api/waitlists/**` returns 429 `rate_limited`. File: `server/smp/src/test/kotlin/com/profiletailors/smp/leadcapture/infrastructure/http/WaitlistRateLimitTest.kt`.
- [ ] 6.2 **GREEN** — Wire `RateLimitStrategy.WAITLIST`: add `/api/waitlists/**` to `RateLimitProperties.waitlist.endpoints`; patch `RateLimitingFilter` only if wildcard matcher missing. Files: `shared/shield/ratelimit/src/main/kotlin/com/profiletailors/ratelimit/infrastructure/config/RateLimitProperties.kt`, `server/smp/src/main/resources/application.yaml`.

## Phase 7: Marketing Integration

- [ ] 7.1 **RED** — Vitest unit test on `WaitlistForm.astro` payload contract: `waitlistKey: "profile-tailors-launch"`, `source: "marketing-site"`, `formId: "waitlist-hero"`, `locale: { language: "en" }`, `consent: { earlyAccess: true, marketing: <checkbox> }`, whitelisted UTM metadata. File: `apps/web/marketing/src/components/WaitlistForm.test.ts`.
- [ ] 7.2 **GREEN** — Wire form to POST JSON to `/api/waitlists/profile-tailors-launch/entries`: loading state, success message on 200/202, inline error on 400/404/409/429, log hashed email only. File: `apps/web/marketing/src/components/WaitlistForm.astro`. Verify: 7.1 unit + Playwright E2E pass.

## Phase 8: Verification

- [ ] 8.1 `just backend-test-fast` — backend tests pass; no new detekt warnings.
- [ ] 8.2 `just frontend-test` — Vitest green incl. Phase 7.1.
- [ ] 8.3 `just frontend-test-e2e` — `tests/e2e/waitlist.spec.ts` covers 200/202, 400 invalid email, 404 unknown waitlist, 429.

## Critical Path

`1.1 → 1.2 → 2.1 → 2.2 → 2.5 → 2.6 → 3.3 → 3.4 → 4.1 → 4.2 → 4.3 → 4.4 → 5.1 → 5.2 → 6.1 → 6.2 → 7.1 → 7.2 → 8.1–8.3`

## Parallelizable

- Phase 2 VOs (2.1/2.2), aggregates (2.3/2.5 vs 2.4/2.6); Phase 4 changelogs (4.2) vs repo code (4.3/4.4); Phase 5 DTOs vs controller; Phase 6 property edit vs filter assertion.
