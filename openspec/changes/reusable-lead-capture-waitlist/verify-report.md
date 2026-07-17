# Verification Report

## Overview

**Change**: reusable-lead-capture-waitlist
**Mode**: openspec
**Verification scope**: DALLAY-438 persistence slice only. DALLAY-439 endpoint, DALLAY-440 rate limiting, DALLAY-441 frontend, DALLAY-442 broader QA beyond 8.3, and DALLAY-443 docs are intentionally out of scope.
**Verdict**: PASS WITH WARNINGS

## Changes

### Executive Summary

DALLAY-438 now verifies successfully for the persistence slice. The previous missing-index gap is closed: `waitlist_entries` includes indexes for `status`, `source`, and `form_id`, the Liquibase changelog test asserts them, the R2DBC adapters preserve the shared port semantics, and focused runtime verification passed.

### Completeness

| Metric | Value |
|--------|-------|
| Total tasks in change | 47 |
| Marked complete | 24 |
| In-scope marked-complete tasks assessed | 7 |
| In-scope tasks passing verification | 7 |
| In-scope tasks partial / failing verification | 0 |
| Out-of-scope tasks ignored for this verdict | DALLAY-439, DALLAY-440, DALLAY-441, DALLAY-442 except 8.3, DALLAY-443 |

### Verification Scope

Validated only:

- Tasks 4.1 through 4.6.
- Task 8.3.
- DALLAY-438 persistence semantics: `waitlists`, `waitlist_entries`, per-waitlist dedupe on normalized email, persistence indexes including `status`, `source`, and `form_id`, `profile-tailors-launch` seed, R2DBC adapters implementing shared ports, sealed `WaitlistEntryRepository.SaveResult` semantics, hexagonal dependency direction, and reproducible focused verification evidence.

Intentionally not required:

- DALLAY-439 HTTP endpoint.
- DALLAY-440 rate limiting.
- DALLAY-441 frontend integration.
- DALLAY-442 broader comprehensive QA except marked 8.3.
- DALLAY-443 documentation/archive work.

### Evidence

| Command / inspection | Result | Evidence |
|---|---|---|
| Source inspection: `server/smp/src/main/resources/db/changelog/lead-capture/001-create-waitlists.yaml` | PASS | Defines `waitlists` and `waitlist_entries`, FK `fk_waitlist_entries_waitlist`, `UNIQUE(waitlist_id, normalized_email)`, `idx_waitlist_entries_waitlist_joined_at`, `idx_waitlist_entries_normalized_email`, `idx_waitlist_entries_status`, `idx_waitlist_entries_source`, and `idx_waitlist_entries_form_id`. |
| Source inspection: `server/smp/src/test/kotlin/com/profiletailors/smp/leadcapture/infrastructure/persistence/LeadCaptureLiquibaseChangelogTest.kt` | PASS | The changelog test now asserts `idx_waitlist_entries_status`, `idx_waitlist_entries_source`, and `idx_waitlist_entries_form_id`, closing the previous verification gap. |
| Source inspection: `server/smp/src/main/resources/db/changelog/lead-capture/002-seed-profile-tailors-launch.yaml` | PASS | Inserts waitlist `id/key = profile-tailors-launch`, `name = Profile Tailors Launch`, `context = profile-tailors`, `status = ACTIVE`. |
| Source inspection: `server/smp/src/main/resources/db/changelog/db.changelog-master.yaml` | PASS | Includes `db/changelog/lead-capture/001-create-waitlists.yaml` and `002-seed-profile-tailors-launch.yaml`. |
| Source inspection: `server/smp/src/main/kotlin/com/profiletailors/smp/leadcapture/infrastructure/persistence/R2dbcWaitlistRepositories.kt` | PASS WITH WARNING | `R2dbcWaitlistRepository` implements `WaitlistRepository`; `R2dbcWaitlistEntryRepository` implements `WaitlistEntryRepository`; `saveIfNotExists` returns sealed `SaveResult.Saved` vs `SaveResult.AlreadyExists` using `ON CONFLICT (waitlist_id, normalized_email) DO NOTHING`. Warning: repository methods are synchronous port methods bridged with `runBlocking`, consistent with current shared port shape but worth revisiting if the app layer becomes suspend/reactive. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.LeadCaptureLiquibaseChangelogTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.persistence.R2dbcWaitlistRepositoriesPostgresTest' --tests 'com.profiletailors.smp.HexagonalArchTest' --rerun-tasks` | PASS | `BUILD SUCCESSFUL in 40s`; test XML shows 17 tests, 0 failures/errors/skipped across Liquibase, R2DBC PostgreSQL, and server hexagonal architecture tests. |
| `./gradlew :shared:lead-capture:waitlist:test --tests 'com.profiletailors.leadcapture.waitlist.LeadCaptureArchTest' --rerun-tasks` | PASS | `BUILD SUCCESSFUL in 4s`; test XML shows 4 tests, 0 failures/errors/skipped proving shared lead-capture modules stay framework-free and server-free. |
| Coverage | NOT APPLICABLE | `coverage_threshold: 0` in `openspec/config.yaml`; no meaningful threshold enforcement required for this focused persistence verification. |

#### Runtime Test Counts From XML

| Test suite | Tests | Failed | Errors | Skipped |
|---|---:|---:|---:|---:|
| `LeadCaptureLiquibaseChangelogTest` | 3 | 0 | 0 | 0 |
| `R2dbcWaitlistRepositoriesPostgresTest` | 4 | 0 | 0 | 0 |
| `HexagonalArchTest` | 10 | 0 | 0 | 0 |
| `LeadCaptureArchTest` | 4 | 0 | 0 | 0 |
| Total focused runtime evidence | 21 | 0 | 0 | 0 |

### Completed Task Assessment

| Task | Verification status | Evidence |
|---|---|---|
| 4.1 RED: Liquibase change-set test asserting tables, per-waitlist unique constraint, and indexes | IMPLEMENTED | `LeadCaptureLiquibaseChangelogTest` asserts both tables, `UNIQUE(waitlist_id, normalized_email)`, and indexes including `idx_waitlist_entries_status`, `idx_waitlist_entries_source`, and `idx_waitlist_entries_form_id`. Passed at runtime. |
| 4.2 GREEN: Add Liquibase changelogs | IMPLEMENTED | Changelog exists, is included from the master changelog, and defines required tables, FK, per-waitlist dedupe constraint, and all verified indexes. Passed static inspection and runtime changelog test. |
| 4.3 RED: Repository tests for round-trip and dedupe | IMPLEMENTED | `R2dbcWaitlistRepositoriesPostgresTest` covers seeded waitlist lookup, entry round-trip, duplicate same-waitlist `AlreadyExists`, and same-email different-waitlist success. Passed against Testcontainers PostgreSQL. |
| 4.4 GREEN: R2DBC repositories implementing shared ports | IMPLEMENTED | `R2dbcWaitlistRepository : WaitlistRepository`; `R2dbcWaitlistEntryRepository : WaitlistEntryRepository`; `saveIfNotExists` preserves sealed `SaveResult` semantics. Compile and focused repository tests passed. |
| 4.5 RED: Test seed `profile-tailors-launch` exists after migrations | IMPLEMENTED | Changelog test asserts seed content; PostgreSQL repository test verifies `findByKey(profile-tailors-launch)` after Liquibase baseline. Passed at runtime. |
| 4.6 GREEN: Add Liquibase seed changelog | IMPLEMENTED | `002-seed-profile-tailors-launch.yaml` is included by master changelog and applied during PostgreSQL test. |
| 8.3: R2DBC repository tests | IMPLEMENTED | `R2dbcWaitlistRepositoriesPostgresTest` exists, is PostgreSQL-backed via Testcontainers, and passed 4/4 focused tests with required environment variable. |

### Spec / Roadmap Compliance Matrix

| Requirement / scenario | Covering test or evidence | Runtime result | Compliance |
|---|---|---|---|
| Schema includes `waitlists` | `LeadCaptureLiquibaseChangelogTest.schema changelog creates waitlists and entries...`; source inspection | PASS | COMPLIANT |
| Schema includes `waitlist_entries` | `LeadCaptureLiquibaseChangelogTest.schema changelog creates waitlists and entries...`; source inspection | PASS | COMPLIANT |
| Per-waitlist dedupe via `UNIQUE(waitlist_id, normalized_email)` | Changelog test plus `R2dbcWaitlistRepositoriesPostgresTest.saveIfNotExists returns AlreadyExists...` | PASS | COMPLIANT |
| Same email can join different waitlists | `R2dbcWaitlistRepositoriesPostgresTest.dedupe key is scoped per waitlist` | PASS | COMPLIANT |
| Seed `profile-tailors-launch` as active profile-tailors waitlist | Changelog seed test plus `R2dbcWaitlistRepositoriesPostgresTest.findByKey maps the seeded...` | PASS | COMPLIANT |
| R2DBC adapters implement shared ports | Source inspection plus selected `:server:smp:test` compile/test lifecycle | PASS | COMPLIANT |
| Preserve `WaitlistEntryRepository.SaveResult` sealed semantics | `R2dbcWaitlistRepositoriesPostgresTest.saveIfNotExists returns AlreadyExists...`; source inspection | PASS | COMPLIANT |
| Hexagonal direction: shared modules remain framework-free | `LeadCaptureArchTest` | PASS | COMPLIANT |
| Hexagonal direction: server domain/application do not depend on infrastructure or forbidden frameworks | `HexagonalArchTest` | PASS | COMPLIANT |
| Required/recommended indexes: `waitlist_id`, `joined_at`, `normalized_email`, `status`, `source`, `form_id` | Changelog source inspection and `LeadCaptureLiquibaseChangelogTest` assertions | PASS | COMPLIANT |
| Reproducible focused verification evidence | Gradle focused commands above | PASS WITH SETUP REQUIREMENT | COMPLIANT when `SMP_POSTGRES_TEST_PASSWORD` is set. |

**Compliance summary**: 11/11 DALLAY-438 checks compliant.

### Correctness Table

| Finding | Judge A | Judge B | Severity | Status |
|---------|---------|---------|----------|--------|
| Previous missing indexes for `waitlist_entries.status`, `waitlist_entries.source`, and `waitlist_entries.form_id` are now present and tested | ✅ Source inspection | ✅ Runtime changelog test | INFO | Confirmed fixed |
| `saveIfNotExists` preserves `Saved` vs `AlreadyExists` sealed result semantics | ✅ Source inspection | ✅ Runtime repository test | INFO | Confirmed |
| Per-waitlist dedupe allows the same email on different waitlists | ✅ Source inspection | ✅ Runtime repository test | INFO | Confirmed |
| Seed exists and maps through repository after migrations | ✅ Changelog inspection | ✅ Runtime PostgreSQL test | INFO | Confirmed |
| Hexagonal dependency direction remains valid for shared lead-capture modules and server layers | ✅ ArchUnit source inspection | ✅ Runtime ArchUnit tests | INFO | Confirmed |
| Local PostgreSQL test command needs `SMP_POSTGRES_TEST_PASSWORD` | ✅ Test infrastructure behavior | ✅ Repository verification command | WARNING | Confirmed |

### Design Coherence Table

| Design decision | Followed? | Notes |
|---|---|---|
| Infrastructure lives in `server/smp`, shared ports live in `shared/lead-capture/waitlist` | YES | R2DBC adapters are under `server/smp/.../infrastructure/persistence`; ports remain in the shared module. |
| `server/smp` implements ports and depends on shared modules | YES | Adapters implement shared ports and selected server test lifecycle compiles them. |
| Shared lead-capture modules stay framework-free | YES | `LeadCaptureArchTest` passed: no Spring, R2DBC, server, or legacy shared-common dependency leakage. |
| Server hexagonal dependency direction remains valid | YES | `HexagonalArchTest` passed 10/10 selected architecture checks. |
| `UNIQUE(waitlist_id, normalized_email)` per-waitlist dedupe | YES | Liquibase constraint and repository `ON CONFLICT` target match the design. |
| Seed `profile-tailors-launch` | YES | Active seeded row exists and is verified through repository lookup. |
| Required persistence indexes | YES | Missing-index gap is closed for `status`, `source`, and `form_id`; changelog test asserts all three. |
| Public API uniform response | OUT OF SCOPE | DALLAY-439 endpoint not required for this verification. |
| Rate limiting | OUT OF SCOPE | DALLAY-440 not required for this verification. |

## Usage

Run the commands in the Evidence section to reproduce the focused DALLAY-438 verification.

## Troubleshooting

### Gaps or Issues

#### CRITICAL

None.

#### WARNING

1. Reproducibility requires setting `SMP_POSTGRES_TEST_PASSWORD`; without it, the focused PostgreSQL test suite cannot initialize its Testcontainers database. Use the exact command shown in the evidence table.
2. Existing unrelated compile warnings remain in `shared/storage/src/main/kotlin/com/profiletailors/storage/infrastructure/S3RetryHelper.kt`, `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/persistence/R2dbcMediaRepositories.kt`, and `server/smp/src/test/kotlin/com/profiletailors/smp/media/application/MediaCasHandlersTest.kt`. They did not fail the focused verification and are outside DALLAY-438.
3. The persisted column names differ from the Linear issue's database model wording: implementation uses `metadata`, `consent_early_access`, and `consent_marketing` instead of `metadata_json`, `early_access_consent`, and `marketing_consent`. This does not break the OpenSpec/shared-port behavior, but it remains a roadmap naming deviation worth accepting explicitly or aligning later.
4. `R2dbcWaitlistEntryRepository.save(entry)` uses plain insert and will surface a database duplicate error on duplicate entries; idempotent semantics are correctly implemented through `saveIfNotExists`, which the handler uses. Keep repository callers disciplined around the correct method.

#### SUGGESTION

1. If the roadmap column names are intentional, record the naming decision in design/OpenSpec so future verification does not treat it as drift.

## References

- `tasks.md`
- `apply-progress.md`
- `design.md`
- `spec.md`

### Final Verdict

PASS WITH WARNINGS

The DALLAY-438 persistence slice is complete for the requested scope. The missing-index blocker from the previous verification is fixed, all in-scope tasks 4.1-4.6 and 8.3 are implemented, and focused runtime verification passed with 21/21 tests successful.
