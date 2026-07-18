# Apply Progress: Reusable Lead Capture Waitlist Capability

## Overview

**Change**: `reusable-lead-capture-waitlist`

## Changes

### Delivery Strategy

- Approved strategy: `size-exception`
- Current slice: DALLAY-441 Phase 7 marketing integration and DALLAY-443 Phase 9 documentation updates complete, except canonical spec sync that belongs to `sdd-archive`.
- Rationale: Broader SDD change exceeds the standard review budget, but this continuation is constrained to wiring the marketing waitlist form to the existing backend endpoint, verifying the frontend contract, and updating architecture documentation to reflect the implemented Lead Capture bounded context and shared modules.

### Completed Tasks

### Phase 1 — Foundation / Shared Module Boundaries (DALLAY-437)

- [x] 1.1 ArchUnit/module-boundary tests exist for forbidden dependencies under `shared/lead-capture/**`.
- [x] 1.2 Gradle auto-discovers `:shared:lead-capture:common` and `:shared:lead-capture:waitlist` via `settings.gradle.kts` recursive `shared` scanning.
- [x] 1.3 Manifest/import-level framework isolation is covered by ArchUnit checks for Spring/R2DBC/server/common dependencies.
- [x] 1.4 Shared module `build.gradle.kts` files remain framework-free and only declare Kotlin/test dependencies plus `waitlist -> common`.

### Phase 2 — Domain (shared) (DALLAY-437)

- [x] 2.1 Common value-object tests exist for `EmailAddress`, `NormalizedEmail`, `CaptureSource`, `CaptureLocale`, `LeadMetadata`.
- [x] 2.2 Common value objects are implemented under `shared/lead-capture/common`.
- [x] 2.3 Waitlist aggregate/status tests exist.
- [x] 2.4 `Waitlist` and `WaitlistStatus` are implemented.
- [x] 2.5 Waitlist entry/status/consent tests exist.
- [x] 2.6 `WaitlistEntry`, status transitions, lifecycle invariants, and `WaitlistConsent` are implemented.

### Phase 3 — Application (shared) (DALLAY-437)

- [x] 3.1 Port contract tests exist for `WaitlistRepository` and `WaitlistEntryRepository`.
- [x] 3.2 Ports are defined in `shared/lead-capture/waitlist/application/ports`.
- [x] 3.3 `JoinWaitlistCommand` / `JoinWaitlistHandler` tests cover accepted/idempotent join behavior and missing/invalid scenarios covered by domain consent construction.
- [x] 3.4 `JoinWaitlistHandler` is implemented with atomic `saveIfNotExists` semantics.

### Phase 4 — Persistence (DALLAY-438)

- [x] 4.1 Liquibase changelog tests assert the master includes lead-capture changelogs and the schema changelog defines `waitlists`, `waitlist_entries`, `UNIQUE(waitlist_id, normalized_email)`, and indexes including `status`, `source`, and `form_id`.
- [x] 4.2 Liquibase schema changelog creates `waitlists` and `waitlist_entries` with the required DALLAY-438 indexes.
- [x] 4.3 Postgres-backed repository tests cover seeded waitlist lookup, entry round-trip, same-waitlist dedupe, and cross-waitlist reuse.
- [x] 4.4 R2DBC adapters implement `WaitlistRepository` and the sealed `WaitlistEntryRepository.SaveResult` contract.
- [x] 4.5 Repository test asserts `profile-tailors-launch` exists after migrations.
- [x] 4.6 Liquibase seed changelog inserts active `profile-tailors-launch` waitlist.

### Phase 5 — HTTP Endpoint (DALLAY-439)

- [x] 5.1 RED WebTestClient/controller tests added for new join accepted response, duplicate uniform accepted response, invalid email 400, missing/false early-access consent 400, unknown waitlist key 404, paused/closed waitlist 409, and unexpected handler failure 500. Focused RED failed at test compilation because `WaitlistController` was intentionally not implemented yet.
- [x] 5.2 GREEN `WaitlistController` implemented with request/response DTOs, HTTP-to-command mapping, validation/error mapping, and uniform `202 Accepted` public response for new and duplicate joins.
- [x] 5.3 RED coverage confirmed in existing `JoinWaitlistHandlerTest`: new joins assert `JoinResult.JOINED_NEW`, duplicate joins assert `JoinResult.ALREADY_JOINED`, and the uniform `toString()` assertion proves the public-ish string representation does not expose the internal distinction.
- [x] 5.4 GREEN coverage confirmed in existing `WaitlistControllerTest`: both new and duplicate joins assert the same `202 Accepted` response body and explicitly assert `$.duplicate` does not exist. No production code change was needed.

### Phase 6 — Rate Limiting (DALLAY-440)

- [x] 6.1 Added a Postgres-backed WebTestClient integration test asserting the 11th request from the same `X-Forwarded-For` IP to `POST /api/waitlists/{waitlistKey}/entries` returns `429` with the existing shared rate-limit error contract.
- [x] 6.2 Wired the real waitlist endpoint prefix `/api/waitlists` to `RateLimitStrategy.WAITLIST` by updating `RateLimitProperties` defaults and server `application.yaml` overrides.
- [x] 6.3 Added integration coverage proving the rate limit consumes tokens across duplicate joins (`202`) and validation-error responses (`400`) before the controller can bypass it.
- [x] 6.4 Registered the shared rate-limit components in the SMP context and permitted the public waitlist endpoint through Spring Security so the WebFlux `RateLimitingFilter` runs before controller handling.
- [x] 6.5 Added a same-IP, cross-waitlist Postgres integration regression proving waitlist B remains accepted after waitlist A exhausts its quota. `RateLimitingService` now passes a WAITLIST-only `IP:path` bucket identity to `Bucket4jRateLimiter`, while retaining the original IP for configuration, metrics, logging, and rate-limit events. AUTH, BUSINESS, and RESUME continue using their existing `strategy:identifier` cache identities.
- [x] 6.2 CI regression remediation: added focused Spring binding coverage proving SMP enables only shared WAITLIST while shared AUTH, BUSINESS, and RESUME are disabled in the SMP application context. `application.yaml` now explicitly disables those non-WAITLIST shared strategies, preserving the pre-DALLAY-440 effective behavior because SMP already owns authentication throttling through `AuthRateLimitWebFilter`.
- [x] 6.6 P2 Codex security remediation (PR #378): `RateLimitingFilter.getIdentifier` now keys buckets on `exchange.request.remoteAddress` only and no longer trusts client-supplied `X-Forwarded-For`. Without trusted-proxy wiring at the edge, the prior code let any caller rotate the forwarded header to evade the 10/min WAITLIST and configurable BUSINESS limits. The fix keeps the `IP:` prefix, sanitization (length cap + allowed-character regex), and `"unknown"` fallback semantics. Inline comment in `getIdentifier` documents the deferred `ForwardedHeaderFilter` / trusted-proxy work as a separate, follow-up change. No metrics, events, response contract, or non-WAITLIST strategy semantics were touched. RED tests in `RateLimitingFilterTest` lock the security property (same `remoteAddress` + distinct `X-Forwarded-For` values produce one bucket identifier; the existing `"unknown"`-fallback test is preserved). The SMP integration test's 11th-request scenario now rotates `X-Forwarded-For` per call to prove header rotation cannot bypass the limit; a `@BeforeEach` resets `Bucket4jRateLimiter.clearCache()` so per-method state does not leak across test methods (every test now exercises the same loopback remote address because WebTestClient on `RANDOM_PORT` cannot spoof `remoteAddress`).
- [x] 6.7 Production safety: WAITLIST default disabled pending distributed bucket + trusted proxy wiring (PR to be opened, citing DALLAY-512 distributed bucket backend and DALLAY-513 trusted-proxy / `ForwardedHeaderFilter` allowlist). `application.rate-limit.waitlist.enabled` now defaults to `false`; the env override `${SMP_WAITLIST_RATE_LIMIT_ENABLED:false}` keeps operator opt-in explicit. RED updated `WaitlistRateLimitConfigurationTest` to assert the new default-off contract, added a sibling `WaitlistRateLimitConfigurationOverrideTest` that proves the env override still flips the bound property to `true` (matches what `WaitlistRateLimitIntegrationTest` already does via `@SpringBootTest(properties = [...])`), and locked the existing non-WAITLIST-disable / endpoint / bandwidth binding. The shared `RateLimitingFilter.getIdentifier` security wire and remoteAddress-only behavior from PR #378 are NOT touched here; no `ForwardedHeaderFilter` is added and no distributed bucket backend is introduced in this PR.

### Phase 7 — Marketing Integration (DALLAY-441)

- [x] 7.1 Added Vitest payload-contract coverage for `buildWaitlistPayload`.
- [x] 7.2 Replaced the hero-only early-access placeholder with `WaitlistForm.astro`, which builds the backend payload and submits to `POST /api/waitlists/{waitlistKey}/entries`.
- [x] 7.3 Added Playwright E2E coverage for successful submission when the backend returns `202 Accepted`.
- [x] 7.4 Stubbed the backend waitlist endpoint via Playwright route interception.
- [x] 7.5 Added Playwright E2E coverage for empty and invalid email submission paths.
- [x] 7.6 Added `novalidate` plus JavaScript validation for invalid email and missing early-access consent before any network submission.

### Phase 8 — Comprehensive Tests

- [x] 8.1 Domain tests in `shared/lead-capture/waitlist/src/test/`.
- [x] 8.2 Application tests for `JoinWaitlistHandler`.
- [x] 8.3 R2DBC repository tests are Postgres-tagged and run against Testcontainers.
- [x] 8.4 WebTestClient tests cover `WaitlistController`.
- [x] 8.5 ArchUnit/module-boundary tests asserting shared modules are framework-free.
- [x] 8.6 Frontend Vitest + Playwright E2E cover the marketing waitlist form.
- [x] 8.7 Frontend lint/test/build and focused Playwright coverage are verified locally; full multi-browser E2E remains covered by `just ci`.

### Phase 9 — Documentation (DALLAY-443)

- [x] 9.1 ADR-0011 status flipped from Proposed to Accepted.
- [x] 9.2 `docs/architecture/shared/dependencies.md` includes `:shared:lead-capture:common` and `:shared:lead-capture:waitlist`.
- [x] 9.3 C4 container and component docs include the Lead Capture bounded context, database relationship, and waitlist rate-limit caveat.
- [ ] 9.4 Canonical specs under `openspec/specs/` are deferred to `sdd-archive`.
- [x] 9.5 Root architecture README links the ADR index.
- [x] 9.6 `docs/architecture/adr/README.md` indexes ADR-0011.

### Code Changes in This Apply Continuation

- Added `WaitlistControllerTest` under `server/smp/src/test/kotlin/com/profiletailors/smp/leadcapture/infrastructure/http/` with WebTestClient coverage for the DALLAY-439 endpoint. The tests include in-memory/failing test doubles for `JoinWaitlistHandler` dependencies.
- Added `WaitlistController` under `server/smp/src/main/kotlin/com/profiletailors/smp/leadcapture/infrastructure/http/` with request/response DTOs, validation/error mapping, command mapping, and uniform `202 Accepted` public response.
- Added `WaitlistApplicationConfiguration` under `server/smp/src/main/kotlin/com/profiletailors/smp/leadcapture/infrastructure/configuration/` with explicit `@Bean` wiring for `JoinWaitlistHandler` and `WaitlistEntryIdGenerator`, so the controller can resolve dependencies during Spring context startup.
- Aligned `spec.md` to `202 Accepted` for new and duplicate public responses after user confirmation; the public contract does not expose a duplicate flag.
- Fixed the DALLAY-438 verify gap by strengthening `LeadCaptureLiquibaseChangelogTest` to assert `idx_waitlist_entries_status`, `idx_waitlist_entries_source`, and `idx_waitlist_entries_form_id`, then adding those indexes to `001-create-waitlists.yaml`.
- Added lead-capture Liquibase changelogs to create `waitlists` and `waitlist_entries`, including per-waitlist dedupe on `(waitlist_id, normalized_email)` and supporting indexes.
- Added a seed changelog for active `profile-tailors-launch` waitlist.
- Included lead-capture changelogs from the master Liquibase changelog.
- Added `R2dbcWaitlistRepository` and `R2dbcWaitlistEntryRepository` infrastructure adapters under `server/smp`.
- Preserved the sealed `WaitlistEntryRepository.SaveResult` contract by returning `Saved` or `AlreadyExists` from `saveIfNotExists`.
- Added Postgres-backed persistence tests plus a changelog presence/shape test.
- Added server dependency edges to `:shared:lead-capture:common` and `:shared:lead-capture:waitlist`.
- Renamed lead-capture shared module archive names and group IDs to avoid colliding with existing `:shared:common` artifact coordinates on the server classpath.
- Added lead-capture cleanup statements to Postgres test support and updated cleanup ordering coverage.
- Added `leadcapture` Modulith metadata for the new server bounded context.

## Usage

### Commands Run

| Command | Exit | Evidence |
|---|---:|---|
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.LeadCaptureLiquibaseChangelogTest'` | 1 | RED: failed first on missing changelog files/master includes; after fixing test path typo, failed on absent lead-capture changelogs. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.LeadCaptureLiquibaseChangelogTest'` | 0 | GREEN for Liquibase changelog and seed shape after adding changelogs and master includes. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.R2dbcWaitlistRepositoriesPostgresTest'` | 1 | RED: failed compile because R2DBC repository classes did not exist and server lacked shared lead-capture dependencies. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors_test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.R2dbcWaitlistRepositoriesPostgresTest'` | 1 | RED/GREEN iteration: after implementing adapters, exposed classpath collision with `:shared:common`, then cleanup/dedup setup issues. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors_test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.R2dbcWaitlistRepositoriesPostgresTest'` | 0 | Focused GREEN for Postgres repository tests after unique shared module coordinates and cleanup fixes. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors_test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.R2dbcWaitlistRepositoriesPostgresTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.LeadCaptureLiquibaseChangelogTest' --tests 'com.profiletailors.smp.integration.support.PostgresTestContainerSupportTest'` | 0 | Focused regression pass for DALLAY-438 tests and updated cleanup support test. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors_test ./gradlew :server:smp:test` | 0 | Broader unfiltered server module test passed. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.LeadCaptureLiquibaseChangelogTest'` | 1 | RED for DALLAY-438 verify gap: failed after adding assertions for `idx_waitlist_entries_status`, `idx_waitlist_entries_source`, and `idx_waitlist_entries_form_id` while the changelog still lacked those indexes. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.LeadCaptureLiquibaseChangelogTest'` | 0 | GREEN after adding the missing `status`, `source`, and `form_id` indexes to `001-create-waitlists.yaml`. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.LeadCaptureLiquibaseChangelogTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.R2dbcWaitlistRepositoriesPostgresTest'` | 0 | Focused DALLAY-438 verification passed with Testcontainers password set. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test` | 0 | Broader unfiltered server module test passed after the changelog fix. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.http.WaitlistControllerTest'` | 1 | RED for DALLAY-439 Phase 5.1: test compilation fails with `Unresolved reference 'WaitlistController'`, confirming the endpoint/controller implementation is missing while test setup reaches the expected focused compile target. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.http.WaitlistControllerTest'` | 0 | GREEN for DALLAY-439 Phase 5.2 after adding `WaitlistController` and aligning the public success contract to `202 Accepted`. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.http.WaitlistControllerTest'` | 0 | Phase 5.4 focused verification passed; new and duplicate responses remain uniform and expose no duplicate flag. |
| `./gradlew :shared:lead-capture:waitlist:test --tests 'com.profiletailors.leadcapture.waitlist.application.JoinWaitlistHandlerTest'` | 0 | Phase 5.3 focused verification passed; handler keeps internal `JOINED_NEW` vs `ALREADY_JOINED` distinction while uniform accepted representation remains covered. |
| `./gradlew :server:smp:detekt --no-daemon` | 1 | RED for DALLAY-439 follow-up: `WaitlistController` repeated public error-string literals failed detekt’s `StringLiteralDuplication` rule. Fixed by extracting the codes into private companion constants. |
| `./gradlew :server:smp:detekt --no-daemon` | 0 | GREEN after refactoring `WaitlistController` to use private error-code constants. |
| `./gradlew :server:smp:test --no-daemon` | 1 | RED for DALLAY-439 wiring gap: integration tests failed to start Spring context because `JoinWaitlistHandler` had no `@Bean` registered for it (controller depended on it). Fixed by adding `WaitlistApplicationConfiguration`. |
| `./gradlew :server:smp:test --no-daemon` | 0 | GREEN after registering `JoinWaitlistHandler` and `WaitlistEntryIdGenerator` beans; full server module test suite passed. |
| `just ci` | 0 | Full CI pipeline (gitleaks, frontend lint, frontend tests, marketing build/coverage, backend detekt, backend tests, backend BDD fast, frontend E2E across chromium/firefox/webkit/Mobile Chrome/Mobile Safari) completed green. |
| `./gradlew :shared:shield:ratelimit:test --rerun-tasks` | 1 | RED after updating expected WAITLIST endpoint to `/api/waitlists`: initial edit left a malformed `listOf` close in `RateLimitingFilterTest`. |
| `./gradlew :shared:shield:ratelimit:test --rerun-tasks` | 0 | GREEN after fixing the test syntax and updating WAITLIST filter/factory expectations to `/api/waitlists`. |
| `SMP_POSTGRES_TEST_PASSWORD=... ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.integration.WaitlistRateLimitIntegrationTest' --rerun-tasks` | 1 | RED: 11th waitlist request returned `202 Accepted`, proving the waitlist route was not yet being rate limited in the SMP context. |
| `SMP_POSTGRES_TEST_PASSWORD=... ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.integration.WaitlistRateLimitIntegrationTest'` | 0 | GREEN after adding the SMP dependency on `:shared:shield:ratelimit`, registering shared rate-limit components, opening `/api/waitlists/*/entries` in Spring Security, and wiring `/api/waitlists` as the WAITLIST endpoint. |
| `SMP_POSTGRES_TEST_PASSWORD=... ./gradlew :shared:shield:ratelimit:test :server:smp:test --tests 'com.profiletailors.smp.leadcapture.integration.WaitlistRateLimitIntegrationTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.http.WaitlistControllerTest'` | 0 | Focused DALLAY-440 regression pass for shared rate-limit tests, waitlist rate-limit integration, and existing controller contract tests. |
| `just backend-test-fast` | 1 | Broader server test surfaced an unrelated auth-rate-limit interaction: `LocalAuthEndpointIntegrationTest.rejects invalid password` received `429` because enabling default shared rate-limit config turned AUTH rate limiting on globally. Fixed by disabling non-WAITLIST shared strategies by default in SMP `application.yaml`, leaving WAITLIST enabled. |
| `./gradlew :shared:shield:ratelimit:test --tests 'com.profiletailors.ratelimit.infrastructure.RateLimitingFilterTest' --rerun-tasks` | 1 | RED: new waitlist contract test failed because the filter emitted a flat, waitlist-only response instead of the shared nested `error` envelope. |
| `./gradlew :shared:shield:ratelimit:test --tests 'com.profiletailors.ratelimit.infrastructure.RateLimitingFilterTest' --tests 'com.profiletailors.ratelimit.infrastructure.BucketConfigurationFactoryTest' --rerun-tasks` | 0 | GREEN: waitlist denials retain the existing shared rate-limit error envelope; real plural child paths match and singular paths do not. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.integration.WaitlistRateLimitIntegrationTest' --rerun-tasks` | 0 | GREEN: all three Postgres-backed rate-limit scenarios pass, including public security access and filter-before-controller behavior. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.integration.WaitlistRateLimitIntegrationTest.same IP can join a different waitlist after exhausting the first waitlist quota' --rerun-tasks` | 1 | RED: the request to `profile-tailors-beta` returned `429` after the same IP exhausted `profile-tailors-launch`, proving the cache key was cross-waitlist. |
| `./gradlew :shared:shield:ratelimit:test --tests 'com.profiletailors.ratelimit.infrastructure.Bucket4jRateLimiterTest.should isolate WAITLIST buckets by bucket identity while retaining the IP identifier' --rerun-tasks` | 1 | RED: the new three-argument bucket-identity contract did not exist, so the focused unit test failed at compilation. |
| `./gradlew :shared:shield:ratelimit:test --rerun-tasks` | 0 | GREEN: 152 shared rate-limit tests pass, including WAITLIST bucket identity and event publication coverage. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.integration.WaitlistRateLimitIntegrationTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.http.WaitlistControllerTest' --rerun-tasks` | 0 | GREEN: Postgres integration coverage passes for same-waitlist `429`, duplicate/validation token consumption, response headers, cross-waitlist isolation, and controller response contracts. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.configuration.WaitlistRateLimitConfigurationTest' --rerun-tasks` | 1 | RED: focused Spring binding test failed because importing shared rate-limit wiring left shared AUTH enabled in the SMP application context. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.configuration.WaitlistRateLimitConfigurationTest' --rerun-tasks` | 0 | GREEN: `application.yaml` now explicitly disables shared AUTH, BUSINESS, and RESUME while keeping shared WAITLIST enabled/configurable. |
| `export SMP_POSTGRES_TEST_PASSWORD=$(grep '^SMP_POSTGRES_TEST_PASSWORD=' .env \| cut -d= -f2-) && ./gradlew :server:smp:test --tests 'com.profiletailors.smp.integration.LocalAuthEndpointIntegrationTest.rejects invalid password' --rerun-tasks` | 0 | GREEN: original CI regression target now returns the expected invalid-password response instead of being polluted by shared AUTH buckets. |
| `export SMP_POSTGRES_TEST_PASSWORD=$(grep '^SMP_POSTGRES_TEST_PASSWORD=' .env \| cut -d= -f2-) && ./gradlew :server:smp:test --no-daemon` | 0 | GREEN: broader unfiltered SMP backend suite passed after the CI regression remediation. |
| `./gradlew :shared:shield:ratelimit:test --tests 'com.profiletailors.ratelimit.infrastructure.RateLimitingFilterTest' --rerun-tasks` | 1 | RED for P2 Codex remediation (4 failures): new `should derive identifier from remoteAddress and ignore X-Forwarded-For header`, `should produce the same bucket identifier when X-Forwarded-For is rotated but remoteAddress is stable`, `should ignore comma-separated X-Forwarded-For and use remoteAddress`, and `should sanitize and truncate IP from remoteAddress when header is ignored` all failed because the production filter still trusted `X-Forwarded-For` (e.g. `MockKException: no answer found for consumeToken(IP:192.168.1.100, ...) among the configured answers: (consumeToken(eq(IP:203.0.113.5), ...))`). |
| `./gradlew :shared:shield:ratelimit:test --tests 'com.profiletailors.ratelimit.infrastructure.RateLimitingFilterTest' --rerun-tasks` | 0 | GREEN after fixing `RateLimitingFilter.getIdentifier` to use `exchange.request.remoteAddress` only. All 27 tests in `RateLimitingFilterTest` pass, and the broader shared rate-limit suite (`./gradlew :shared:shield:ratelimit:test --rerun-tasks`) is green. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.integration.WaitlistRateLimitIntegrationTest' --rerun-tasks` | 0 | GREEN: all 4 Postgres-backed scenarios pass (11th-request returns 429 with rotated `X-Forwarded-For`, cross-waitlist isolation, duplicate/validation consumption, headers). `BUILD SUCCESSFUL in 49s`. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :shared:shield:ratelimit:test :server:smp:test --tests 'com.profiletailors.smp.leadcapture.integration.WaitlistRateLimitIntegrationTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.http.WaitlistControllerTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.configuration.WaitlistRateLimitConfigurationTest' --tests 'com.profiletailors.smp.identity.infrastructure.security.AuthRateLimitWebFilterTest' --rerun-tasks` | 0 | GREEN: focused regression pass for affected suites. `BUILD SUCCESSFUL in 45s`. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test --rerun-tasks` | 0 | GREEN: full SMP backend suite (`./gradlew :server:smp:test`) passes. `BUILD SUCCESSFUL in 3m 56s`. |
| `./gradlew :shared:shield:ratelimit:detekt` | 0 | GREEN: no detekt regressions introduced by the security fix. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.configuration.WaitlistRateLimitConfigurationTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.configuration.WaitlistRateLimitConfigurationOverrideTest' --rerun-tasks` | 1 | RED for Phase 6.7: new `SMP defaults shared WAITLIST to disabled ...` assertion failed because the SMP `application.yaml` still defaulted `application.rate-limit.waitlist.enabled` to `true`. The override test, non-WAITLIST-disable assertions, and bandwidth-limit assertions were green. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.configuration.WaitlistRateLimitConfigurationTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.configuration.WaitlistRateLimitConfigurationOverrideTest' --rerun-tasks` | 0 | GREEN after flipping the WAITLIST default to `${SMP_WAITLIST_RATE_LIMIT_ENABLED:false}` in `server/smp/src/main/resources/application.yaml` (with DALLAY-512/DALLAY-513 rationale comment). `BUILD SUCCESSFUL in 20s`. |
| `pnpm --filter marketing lint && pnpm --filter marketing test && pnpm --filter marketing build` | 0 | GREEN for Phase 7 frontend implementation: Biome checked 45 files with no fixes; Vitest passed 6 files / 43 tests including `waitlist-form.test.ts` and `waitlist-form-validator.test.ts`; Astro built 10 static pages successfully. |
| `pnpm --filter marketing exec playwright test --project=chromium --grep "Waitlist Form"` | 0 | GREEN for focused Phase 7 E2E: 7 chromium tests passed, covering hero form rendering, 202 success, empty/invalid email blocking, missing early-access consent blocking, 429 friendly error, and configured waitlist key submission. |
| `git diff --check` | 0 | GREEN after Phase 7/9 edits; no whitespace errors. |

## Troubleshooting

### Remaining Tasks

- Phase 9.4 remains deferred to `sdd-archive`: sync canonical specs to `openspec/specs/lead-capture-common/spec.md` and `openspec/specs/lead-capture-waitlist/spec.md`.
- Run `sdd-verify` for the full change before archive.
- The DALLAY-512 distributed bucket backend and DALLAY-513 trusted-proxy / `ForwardedHeaderFilter` allowlist remain separate production-safety follow-up changes before enabling WAITLIST rate limiting outside tests.

### Deviations

- The DALLAY-439 public success contract was resolved to `202 Accepted` for new and duplicate joins after user confirmation. `spec.md`, `tasks.md`, and tests are now aligned.
- No functional deviation from the DALLAY-438 persistence design after this continuation. The persisted column uses `normalized_email`, matching existing domain naming and tests, while the OpenSpec task text says `email_normalized`; the requirement/design explicitly require `UNIQUE(waitlist_id, normalized_email)` / normalized email dedupe semantics.
- The shared lead-capture module Gradle `group` and archive names were adjusted because both `:shared:common` and `:shared:lead-capture:common` otherwise produced identical `com.profiletailors:common` coordinates, causing the server classpath to resolve the wrong artifact.
- DALLAY-439 Phase 5 review asked to wrap `JoinWaitlistHandler.handle` in `withContext(Dispatchers.IO)` inside a `suspend` controller, ostensibly to keep blocking I/O off the WebFlux event loop. Rejected as a fix for this PR because:
  - `JoinWaitlistHandler.handle` is not `suspend`; the R2DBC repositories wrap the call in `runBlocking {}` (see warning in `verify-report.md` for DALLAY-438). Wrapping that in `Dispatchers.IO` is a layered `runBlocking` that hides the underlying suspend-port debt instead of fixing it.
  - The structural fix (changing shared ports to `suspend`) is a separate, cross-cutting change. It must be scoped, specced, and approved independently of DALLAY-439. Tracking hint left here; ticket creation deferred.
- Post-PR review feedback (PR #367) flagged an `IllegalArgumentException` catch-all in `WaitlistController.toPublicErrorCode()` that mapped any domain failure to `invalid_email`. Fixed by mapping only the known VOs (`EmailAddress`, `CaptureSource`, `CaptureLocale`, `WaitlistConsent`, `WaitlistKey`) and re-throwing unknown `IllegalArgumentException`s to `GlobalExceptionHandler`, which already returns a 400 problem detail. Added a focused test to lock the new contract.
- Post-PR review feedback (PR #367) asked to make `WaitlistEntryIdGenerator`'s UUID input charset explicit. Changed `"${...}".toByteArray()` to use `StandardCharsets.UTF_8`. JVM defaults already use UTF-8, but explicit is more portable and removes reviewer ambiguity.
- P2 Codex finding on PR #378 (`RateLimitingFilter.getIdentifier` trusted `X-Forwarded-For`): the filter now keys on `exchange.request.remoteAddress` only, keeping the `IP:` prefix and the existing sanitization (length cap + allowed-character regex). `ForwardedHeaderFilter` / trusted-proxy allowlist configuration is intentionally deferred to a separate change and called out in the inline `// SECURITY` comment inside `getIdentifier`.
- Production safety follow-up on DALLAY-440 (Phase 6.7, DALLAY-512 + DALLAY-513): the WAITLIST bucket default flipped to `false` so the limiter cannot be turned on by accident while SMP runs behind ingress with multiple replicas. Toggled by `${SMP_WAITLIST_RATE_LIMIT_ENABLED:false}`; the integration suite, the new override test, and any operator who wants the limiter on must set the env var or the equivalent Spring property explicitly. No remoteAddress change, no shared filter security wire touch, no `ForwardedHeaderFilter`, no distributed bucket backend — those remain separate, scoped changes.

## References

### Status

47 of 48 tasks are complete. DALLAY-437 through DALLAY-443 apply work is complete except Phase 9.4 canonical spec sync, which belongs to `sdd-archive`. Phase 7 marketing integration and Phase 9 documentation updates have focused verification evidence. No commit, push, or PR action was performed.
