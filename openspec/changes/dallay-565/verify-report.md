# Verification Report — DALLAY-565 stacked PR unit 1

## Overview

**Change:** `dallay-565` (DALLAY-565)
**Unit:** 1 — contracts/model
**Branch:** `feat/dallay-565-contracts`
**Base/parent:** `main`
**Position:** 1 of 4
**Mode:** `openspec` (`artifact_store.mode=openspec`, `strict_tdd=true`)
**Date:** 2026-09-03
**Runner:** `fallback` — `openspec/quality-runner.json` and `sdd-quality-runner.mjs` are unavailable; direct Gradle commands and source inspection were used. These results are not deterministic runner envelopes.
**Scope boundary:** Only unit 1 is verified. Units 2–4 are not verified or implemented here. DALLAY-566 remains a hard gate and remains pending.

## Changes

### Completeness

| Scope | Status | Evidence |
|---|---|---|
| Unit 1.1 — DALLAY-564 standalone Invitation prerequisite | ✅ Complete | `server/smp` standalone `Invitation`, `InvitationId`, acceptance port/command, lifecycle tests, repository test, and `invitations` Liquibase schema are present. |
| Unit 1.2 — token-free event/kind and summary/read contracts | ✅ Complete | Three new shared contract files are present and the focused contract test passes. |
| Unit 1.3 — DALLAY-566 handoff gate | ⏳ Pending / hard gate | `tasks.md` leaves 1.3 unchecked; design and spec explicitly stop before the DALLAY-566 handoff is defined. |
| Full DALLAY-565 | ⏳ Not complete | `state.yaml` records `current_phase: qa-unit-1`, `apply_unit: 1`, `apply_status: partial`; no full-change completion or advancement was made. |

The change-level task list has 16 total tasks: 2 completed and 14 incomplete. The incomplete tasks
are outside this stacked unit except task 1.3, which is intentionally pending until DALLAY-566
supplies the approved handoff.

## Usage

### Build, formatting, and test execution

| Check | Command | Result | Evidence |
|---|---|---|---|
| Focused contracts | `./gradlew :shared:notifications:test --tests 'com.profiletailors.notifications.domain.InvitationNotificationContractsTest' --no-daemon --console=plain` | ✅ PASS | Original unit-1 run; six focused tests were selected and no failures were reported. |
| Focused contracts, forced execution | `./gradlew :shared:notifications:test --tests 'com.profiletailors.notifications.domain.InvitationNotificationContractsTest' --rerun-tasks --no-daemon --console=plain` | ✅ PASS | Original unit-1 run; six focused tests passed and Gradle reported `BUILD SUCCESSFUL`. |
| Identity-only contract maintenance | `./gradlew :shared:notifications:spotlessKotlinCheck :shared:notifications:test --tests 'com.profiletailors.notifications.domain.InvitationNotificationContractsTest' --rerun-tasks --no-daemon --console=plain` | ✅ PASS | 2026-09-03 review update; seven focused tests passed, formatting passed, and Gradle reported `BUILD SUCCESSFUL`. |
| Shared notifications suite | `./gradlew :shared:notifications:test --no-daemon --console=plain` | ✅ PASS | Exit 0; Gradle `BUILD SUCCESSFUL`; unfiltered task completed. |
| Formatting | `./gradlew :shared:notifications:spotlessKotlinCheck --rerun-tasks --no-daemon --console=plain` | ✅ PASS | Exit 0; Gradle `BUILD SUCCESSFUL`. |
| Downstream compile | `./gradlew :server:smp:compileKotlin --no-daemon --console=plain` | ✅ PASS | Re-run on 2026-09-03; Gradle reported `BUILD SUCCESSFUL`. Two pre-existing/unrelated Boolean boxing warnings in `R2dbcBulkImportJobRepository.kt` were emitted. |
| DALLAY-564 domain/application unit tests | `./gradlew :server:smp:test --tests 'com.profiletailors.smp.platformadmin.domain.InvitationTest' --tests 'com.profiletailors.smp.platformadmin.application.AcceptInvitationHandlerTest' --no-daemon --console=plain` | ✅ PASS | Exit 0; Gradle `BUILD SUCCESSFUL`. |
| DALLAY-564 repository integration tests | `./gradlew :server:smp:test --tests 'com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcInvitationAcceptanceRepositoryTest' --no-daemon --console=plain` | ⚠️ WARNING | 2 tests completed, 2 failed because local PostgreSQL was unavailable; `java.net.ConnectException` was the root cause. This is not reported as success. |
| Coverage | Not configured for this change | ➖ Not run | `openspec/config.yaml` defines a coverage capability but no `coverage_threshold`; no coverage claim is made. |
| Quality runner | `openspec/quality-runner.json` / `sdd-quality-runner.mjs` | ⚠️ Unavailable | Verification uses fallback direct commands. |

The `apply-progress.md` command record remains historical evidence for the original six-test contract
slice. The current contract class contains seven tests after adding negative-count coverage. The
repository test failure is correctly treated as a local connection warning rather than fabricated
success.

### Spec compliance matrix — unit 1 scope

Unit 1 establishes prerequisites and contracts only. Full delivery, scheduling, persistence, admin, provider, and acceptance scenarios are intentionally not attributable to this unit.

| Requirement / scenario | Implementation evidence | Passing runtime test | Result |
|---|---|---|---|
| Canonical Invitation and prerequisite — DALLAY-564 landed | `Invitation`, `InvitationId`, `AcceptInvitationCommand`, `InvitationAcceptanceRepository`, `InvitationTest`, `R2dbcInvitationAcceptanceRepositoryTest`, and `004-create-invitations.yaml` exist. | `InvitationTest`, `AcceptInvitationHandlerTest` pass; repository tests are blocked by local PostgreSQL connection failure. | ⚠️ PARTIAL — static prerequisite confirmed; repository runtime proof unavailable locally |
| Canonical flow — use standalone Invitation ID, not WaitlistInvitation | Unit 1 does not wire a delivery flow; standalone model and acceptance seam are present. | No unit-1 delivery-flow test exists or is expected. | ⚠️ PARTIAL — contract prerequisite only |
| Token-safe durable boundary — requested event is identity-only | `InvitationNotificationRequested` declares only `invitationId`, `commandId`, and `kind`; it excludes mutable invitation details and token-bearing values. | `requested event carries only identity correlation and delivery kind` passes. | ✅ COMPLIANT |
| Token-safe durable boundary — summary excludes payload and sensitive values | `InvitationDeliverySummary` exposes count/status/timestamps only; no payload, recipient, or token field. | `delivery summary exposes operational latest values without payload` passes. | ✅ COMPLIANT |
| Approved event shape and delivery kinds | Event carries identity correlation only; enum contains `INITIAL` and `RESEND` only. | `requested event exposes the approved identity-only shape` and `invitation delivery kind contains only initial and resend` pass. | ✅ COMPLIANT |
| Contract validation — command ID cannot be blank | Event constructor requires a non-blank `commandId`. | `requested event rejects a blank command id` passes. | ✅ COMPLIANT |
| Contract validation — delivery count cannot be negative | Summary constructor requires a non-negative `count`. | `delivery summary rejects a negative count` passes. | ✅ COMPLIANT |
| Empty/lost handoff representation | `InvitationDeliverySummary.EMPTY` contains zero count and null latest values. | `empty delivery summary represents a lost or not yet recorded handoff` passes. | ✅ COMPLIANT |
| Post-commit handoff / rollback / delivery ownership / resend / admin composition / safe provider failure / legacy compatibility | No scheduler, consumer, repository correlation, admin composition, provider transport, or schema change is in this unit. | No unit-1 passing test can prove these later-unit scenarios. | ⏳ NOT TESTED — intentionally deferred to later stacked units |
| DALLAY-566 integration boundary | `tasks.md` 1.3 is unchecked; `design.md` says the concrete handoff is owned by DALLAY-566 and must not be invented here. | No handoff test exists, correctly. | ⏳ PENDING HARD GATE |

**Unit-1 contract summary:** 6 contract behaviors are runtime-compliant. The canonical prerequisite
is structurally present and its pure unit tests pass; its PostgreSQL repository proof is locally
unavailable. Later-unit scenarios are not claimed.

### Correctness — static structural evidence

| Check | Status | Notes |
|---|---|---|
| DALLAY-564 standalone `Invitation` is available | ✅ Implemented | First-class `Invitation` remains separate from legacy `WaitlistInvitation`; validity model has no delivery fields. |
| Requested event contract | ✅ Implemented | Stable UUID correlation, command correlation, and delivery kind only. Constructor rejects a blank command ID. |
| Delivery summary/read contract | ✅ Implemented | Notifications-owned narrow port keyed by UUID and operational summary fields only. Negative counts are rejected. |
| Raw token/hash/accept URL crossing the new durable event | ✅ Absent | New event source and passing reflection test contain no `rawToken`, `tokenHash`, or `acceptUrl` fields. |
| Raw sensitive data in new summary | ✅ Absent | New summary source and passing reflection test contain no payload, recipient, or raw token fields. |
| Delivery consumer/scheduler/admin/schema/token transport accidentally added | ✅ Absent from unit diff | The unit branch adds only the three shared contract/test files. Existing legacy consumer/bridge files are unchanged and are outside this unit. |
| DALLAY-566 handoff invented | ✅ Not invented | No transport, envelope, TTL, validation, recipient binding, or token handoff was added. |

### Design coherence

| Design decision | Followed? | Notes |
|---|---|---|
| DALLAY-564 `Invitation` is canonical and correlation is by identity | ✅ Yes | Prerequisite model and UUID identity are present; unit 1 adds only the notification-side UUID contract. |
| Narrow token-free event seam | ✅ Yes | Event shape matches the design interface and excludes Invitation aggregate/token material. |
| Explicit Notifications summary port | ✅ Yes | `InvitationDeliverySummaryReader` is a suspend functional port in the Notifications application layer. |
| DALLAY-566 owns concrete ephemeral token handoff | ✅ Yes | Unit 1 stops before the unresolved handoff and leaves task 1.3 pending. |
| No consumer/scheduler/admin/schema work in unit 1 | ✅ Yes | No such production files were added or modified by the unit diff. |

### Strict TDD audit

| Metric | Status | Evidence |
|---|---|---|
| RED → GREEN evidence | ✅ Confirmed | `apply-progress.md` records the initial `ClassNotFoundException` RED run, minimum contracts, then GREEN runs; the second shape assertion's compile correction and passing run are also recorded. |
| Tests before or with implementation | ⚠️ Cannot verify from git history | Commit `379430b9` contains the contract and test files together, so commit ordering does not independently prove RED-first execution. The explicit apply-progress RED evidence is the available provenance. |
| Focused runtime tests | ✅ Confirmed | Forced focused Gradle execution passed after the contracts were added. |
| Strict TDD mode | ✅ Active | `openspec/config.yaml` and `state.yaml` set `strict_tdd: true`; no strict-TDD module file was available at the configured skill path, so the recorded apply evidence was used. |

## Troubleshooting

### Issues found

#### CRITICAL

None for unit 1's approved contracts scope.

#### WARNING

1. **Local PostgreSQL unavailable for DALLAY-564 repository proof.** The focused repository integration run failed 2/2 with `java.net.ConnectException`. This is a real unavailability warning, not a passing result; rerun with local PostgreSQL/Testcontainers available.
2. **Full DALLAY-565 scenarios remain untested by design.** Scheduling, rollback suppression, delivery persistence, idempotency, resend multiplicity, admin composition, provider failure, legacy compatibility, and acceptance scenarios belong to later units and are not claimed here.
3. **Quality runner unavailable.** `openspec/quality-runner.json` and `sdd-quality-runner.mjs` were not found, so this report is fallback evidence rather than versioned runner-envelope evidence.
4. **TDD commit ordering cannot be independently verified.** Commit `379430b9` contains tests and
   implementation together; apply-progress supplies explicit RED→GREEN execution evidence.

#### SUGGESTION

1. Preserve the unit boundary when stacking: do not add the DALLAY-566 handoff or any delivery consumer/scheduler/admin/schema behavior until task 1.3 is approved and the next unit is based on the correct gate.

### Verdict table

| Finding | Judge A | Judge B | Severity | Status |
|---|---|---|---|---|
| DALLAY-564 standalone Invitation prerequisite is present | ✅ | ✅ | — | Confirmed |
| Requested event is token-free and identity-only | ✅ | ✅ | — | Confirmed |
| Summary/read contract is narrow and token-free | ✅ | ✅ | — | Confirmed |
| Consumer/scheduler/admin/schema/token transport was accidentally added to unit 1 | ✅ | ✅ | CRITICAL if present | Not present |
| DALLAY-566 hard gate remains pending | ✅ | ✅ | CRITICAL gate | Confirmed pending |
| PostgreSQL repository test unavailable locally | ✅ | ✅ | WARNING | Confirmed |
| Strict TDD RED→GREEN evidence recorded | ✅ | ✅ | — | Confirmed from apply-progress |
| TDD commit ordering independently proven | ❌ | ✅ | WARNING | Cannot verify from the combined implementation/test commit |
| Quality runner envelope available | ✅ | ✅ | WARNING | Unavailable; fallback used |

### Verdict

**PASS WITH WARNINGS for stacked PR unit 1 only.** The unit-1 contracts compile, format, and pass focused/runtime shared tests; DALLAY-564 pure unit coverage passes; the new durable event and summary are token-free; and no later-unit implementation was pulled into this slice. PostgreSQL repository evidence remains unavailable due to the local connection failure, and DALLAY-566 remains pending. This is technical verification only; hand off to `sdd-qa` for acceptance QA. The full DALLAY-565 change is not complete and must not advance beyond the DALLAY-566 gate.

## References

- [Proposal](proposal.md)
- [Design](design.md)
- [Tasks](tasks.md)
- [Acceptance QA report](qa-report.md)
- [Change state](state.yaml)
