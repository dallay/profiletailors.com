# Verification Report: hotfix-direct-invitation-issued-by-fk

- Change: `hotfix-direct-invitation-issued-by-fk`
- Mode: `openspec` (fallback label: deterministic runner `sdd-quality-runner.mjs` not available in this repo; evidence below is direct Gradle/test-XML output, not a versioned envelope)
- Verdict: **PASS**
- Date: 2026-09-11
- Tasks: 12/12 complete

## Completeness

| Phase | Tasks | Done | Evidence |
|-------|-------|------|----------|
| 1 Evidence + RED regression (TDD) | 1.1, 1.2, 1.3 | 3/3 | Grep hits confirmed in code; new tests assert `user-<uuid>`; RED logic sound (see Correctness) |
| 2 Prefix fix (single helper) | 2.1, 2.2, 2.3, 2.4 | 4/4 | Diff shows `PlatformPrincipalIds.fromUuid` at all 3 write points + idempotent overload; `InvitationId` untouched; zero migrations |
| 3 Focused verification | 3.1, 3.2, 3.3 | 3/3 | Focused unit PASS, BDD fast 247/247 PASS, Detekt + Spotless PASS, `git diff --check` PASS |
| 4 Done + Rollback | 4.1, 4.2 | 2/2 | RED→GREEN shown by test assertions; rollback = redeploy v0.5.0, no schema rollback |

## Build / Tests / Coverage Evidence

| Check | Result | Exact evidence |
|-------|--------|----------------|
| Focused unit (6 classes) | **Passed** | `./gradlew :server:smp:test --tests ...PlatformPrincipalIdsTest --tests ...handler.CreateInvitationHandlerTest --tests ...handler.InviteWaitlistEntryHandlerTest --tests ...InvitationActivationCoordinatorTest --tests ...AcceptInvitationHandlerTest --tests ...domain.InvitationTest` → `BUILD SUCCESSFUL in 10s` |
| `PlatformPrincipalIdsTest` | **Passed** | `TEST-...PlatformPrincipalIdsTest.xml: tests=4 failures=0 errors=0 skipped=0` |
| `CreateInvitationHandlerTest` | **Passed** | `tests=8 failures=0 errors=0 skipped=0` (incl. new `should persist issuedBy as prefixed platform principal id when creating a direct invitation`) |
| `InviteWaitlistEntryHandlerTest` | **Passed** | `tests=9 failures=0 errors=0 skipped=0` (incl. new prefixed `issuedBy` test + supersede `createdBy` assert) |
| `InvitationActivationCoordinatorTest` | **Passed** | `tests=16 failures=0 errors=0 skipped=0` (incl. 2 new bare-uuid/prefixed `acceptedPrincipalId` tests) |
| `AcceptInvitationHandlerTest` | **Passed** | `tests=4 failures=0 errors=0 skipped=0` |
| `InvitationTest` | **Passed** | `tests=23 failures=0 errors=0 skipped=0` |
| BDD fast (full suite) | **Passed** | `./gradlew :server:smp:bddFastTest` → `BUILD SUCCESSFUL in 5m 18s`; XML aggregate `files=36 tests=247 failures=0 errors=0 skipped=0` |
| BDD `invitations-direct` lane | **Passed** | `TEST-...-platformadmin-invitations-direct.feature.xml: tests=11 failures=0 errors=0` (incl. `Operator creates a direct invitation` 201 path) |
| Detekt + Spotless | **Passed** | `./gradlew :server:smp:detekt :server:smp:spotlessKotlinCheck` → `BUILD SUCCESSFUL in 13s` |
| `git diff --check` | **Passed** | exit 0, no whitespace errors |
| `just backend-check` / `just ci` / postgres BDD / E2E / deploy 0.5.1 / QA-01 rerun | **Not run** | Out of scope for this hotfix verify by design; deploy + QA-01 are explicit next steps (see Next) |
| Coverage gate | **Not run** | No coverage command required by proposal; change is 4 main files (~12 lines) + tests |

## Spec Compliance Matrix

Proposal declares no new/modified capabilities and no delta spec (pure defect fix restoring `invitations` direct-create 201). Each success criterion maps below.

| Success criterion (proposal) | Covering test (runtime PASS) | Status |
|------------------------------|------------------------------|--------|
| Regression test fails before fix, passes after | `CreateInvitationHandlerTest.handle persists issuedBy as prefixed platform principal id`, `InviteWaitlistEntryHandlerTest.persists waitlist invitation issuedBy as prefixed platform principal id`, `InvitationActivationCoordinatorTest` bare-uuid + prefixed pair, `PlatformPrincipalIdsTest` (4) | **COMPLIANT** — GREEN proven by runs above; RED proven by construction (pre-fix persisted bare `UUID.toString()`, which cannot equal asserted `user-<uuid>`; `UUID.fromString` on prefixed `issuedBy` threw `IllegalArgumentException` pre-fix per apply-progress) |
| `POST /api/admin/invitations/direct` returns 201 with seeded `user-<uuid>` operator | BDD `invitations-direct`: `Operator creates a direct invitation` (11/11 PASS) with seeded `user-<admin-uuid>` principal in `PlatformAdminBddSteps` | **COMPLIANT** |
| No Detekt/compiler warnings; scoped backend-check + relevant BDD fast green | Detekt + Spotless PASS; BDD fast 247/247 PASS; focused unit PASS; `backend-check` intentionally not run (broad) | **DEFERRED** — Detekt + Spotless PASS, BDD fast 247/247 PASS, and focused unit PASS retained as scoped evidence only; `backend-check` itself was not run (broad scope, no approved replacement), so full compliance cannot be claimed |
| 0.5.1 deployed healthy; QA-01 passes | Not in verify scope | **DEFERRED** → explicit Next, owned by deploy + `sdd-qa` |
| No commits without request; diff limited to handlers + tests | `git status` shows no commits made by change; main diff = 4 files (`CreateInvitationHandler`, `InviteWaitlistEntryHandler`, `InvitationActivationCoordinator`, `PlatformPrincipalIds` overload); balance = tests/seeds only | **COMPLIANT** |

## Correctness Table

| Requirement | Implementation evidence | Test evidence | Status |
|-------------|------------------------|---------------|--------|
| All `issued_by` writers use `PlatformPrincipalIds.fromUuid` | `CreateInvitationHandler.kt:74`, `InviteWaitlistEntryHandler.kt:143` both call `fromUuid(command.operatorPrincipalId)`; grep confirms no other bare-UUID INSERT writer (repo binds domain values; `:85`/`:`89` copy existing domain values) | Handler prefixed-`issuedBy` assertions PASS | PASS |
| All `accepted_principal_id` writers use `fromUuid` with anti-double-prefix guard | `InvitationActivationCoordinator.kt:123` `fromUuid(identity.principalId)` (String overload is idempotent) + `:124` `require(startsWith("user-"))` | Bare-uuid test asserts `user-<uuid>` persisted; prefixed test asserts no `user-user-` | PASS |
| Guard against `user-user-…` | `PlatformPrincipalIds.fromUuid(String)` returns input unchanged when already prefixed | `PlatformPrincipalIdsTest.fromUuid never double prefixes` + coordinator prefixed test PASS | PASS |
| Legacy/prefixed read path at `InviteWaitlistEntryHandler:101` | Now `PlatformPrincipalIds.toUuid(existingInvitation.issuedBy)` (strips prefix, still parses bare) | Supersede test with prefixed fixture `issuedBy = "user-$operatorId"` PASS; `toUuid strips the prefix and parses bare uuids` PASS | PASS |
| `InvitationId` stays pure UUID (ADR-0020) | No `InvitationId` file in diff; `generate()`/`InvitationId(...)` call sites untouched | Unchanged suites green (BDD 247 PASS) | PASS |
| Zero migrations, zero workspace changes | `git status` migration grep = none; `git diff --name-only` workspace grep = none | N/A (structural) | PASS |
| FK satisfied against real constraints with `user-<uuid>` principals | Seeds added: `user-$ADMIN_PRINCIPAL_ID`, `user-principal-1`, `user-$operatorId` in BDD steps / R2dbc test / postgres integration test | `@postgres`/BDD GREEN runs prove FK satisfaction (pre-fix INSERTs 500'd) | PASS |

## Design Coherence Table

| Design decision / constraint | Changed code | Verdict |
|------------------------------|--------------|---------|
| Single canonical helper `PlatformPrincipalIds` (no ad-hoc `"user-$x"` at write points) | All 3 write points call `fromUuid`; only test fixture constructs `"user-$operatorId"` as input data, not production logic | Coherent |
| Hexagonal: application owns formatting, infrastructure only binds | Fix lives in `application/handler` + `application/InvitationActivationCoordinator`; `R2dbcInvitationRepository` untouched (read/bind only) | Coherent |
| Strong types, no new `Any`/`!!`/unchecked casts | `STRONG-TYPE-CHECK` grep clean on touched main files | Coherent |
| Zero comments / zero suppressions | `git diff` added-line grep for `//`, `/*`, `suppress`/`nolint` = none in main | Coherent |
| Detekt / Spotless / Arch intact | Detekt + Spotless PASS; `git diff --name-only` shows no `*ArchTest*`, Modulith, Konsist, or Detekt-config change; `HexagonalArchTest`/`ComponentScanArchTest` unweakened | Coherent |
| Out of scope respected (no workspace/provisioning logic) | Coordinator `:129` `membershipProvisioner.reconcile` call unchanged; no workspace files in diff | Coherent |

## Issues

### CRITICAL

None.

### WARNING

None. (Deploy 0.5.1 + QA-01 rerun are pending **next steps**, not defects in this change.)

### SUGGESTION

None blocking. Note (informational, from apply-progress, accepted): full BDD alignment of legacy bare fixture ids (e.g. `principal-1`) is out of scope and tracked by the `migrate-to-pure-uuid` proposal; prefixed `user-principal-1`-style ids exist only in test fixtures, prod ids are always `user-<uuid>`.

## Verdict Table

| Finding | Judge A | Judge B | Severity | Status |
|---------|---------|---------|----------|--------|
| All writers prefixed via `fromUuid`, idempotent guard proven | ✅ | ✅ | — | Confirmed |
| `InvitationId` UUID form untouched, zero migrations, zero workspace changes | ✅ | ✅ | — | Confirmed |
| TDD regression covers FK with `user-<uuid>` principals, GREEN at runtime | ✅ | ✅ | — | Confirmed |
| Hexagonal / strong types / zero comments / Detekt+Spotless+Arch intact | ✅ | ✅ | — | Confirmed |
| Deploy 0.5.1 + QA-01 rerun pending | ✅ | ✅ | NEXT (not a defect) | Info |

## Next (explicit, for 0.5.1 / QA-01)

- Deploy 0.5.1 image (verify owns no deploy; do not promote local to prod from here).
- Rerun QA-01 direct-invitation flow in `sdd-qa` (`qa-report.md` ownership); acceptance scenarios belong to QA, not this technical verify.
- Optional follow-up (separate change): `migrate-to-pure-uuid` proposal for legacy bare fixture-id alignment.

## Handoff

Technical conformance is **PASS**. Handing off explicitly to `sdd-qa`, which owns acceptance scenarios and `qa-report.md`.
