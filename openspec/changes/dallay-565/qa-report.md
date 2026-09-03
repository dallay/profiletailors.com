# Acceptance QA Report: dallay-565

## Overview

### Identity

- Change: `dallay-565` / DALLAY-565
- Unit: stacked PR unit 1 — contracts/model
- Branch: `feat/dallay-565-contracts`
- Base: `main`
- Position: 1 of 4
- Mode: `openspec`
- QA phase: `qa` for unit 1 only
- Date: 2026-09-01
- Runner: `fallback` — `openspec/quality-runner.json` and `sdd-quality-runner.mjs` are unavailable
- Final QA verdict: `NOT TESTED`

This report is an acceptance-gate record for unit 1. It does not claim full DALLAY-565 product
acceptance, delivery acceptance, provider acceptance, or acceptance of the complete stacked change.

## Changes

### Sources of Truth and Technical Verification Handoff

| Artifact | Status | Unit-1 handoff |
|---|---|---|
| `openspec/changes/dallay-565/proposal.md` | Read | Defines Invitation correlation, token-free boundaries, best-effort delivery, and DALLAY-566 dependency. |
| `openspec/changes/dallay-565/specs/invitation-notification-delivery/spec.md` | Read | Defines the canonical Invitation prerequisite, contract shape, safe boundaries, and deferred delivery scenarios. |
| `openspec/changes/dallay-565/specs/email-notifications/spec.md` | Read | Requires invitation triggers to come from the approved post-commit handoff and keeps Notifications as owner. |
| `openspec/changes/dallay-565/design.md` | Read | Defines the event, delivery kind, summary reader, and explicit DALLAY-566 handoff boundary. |
| `openspec/changes/dallay-565/tasks.md` | Read | Unit 1.1 and 1.2 are complete; unit 1.3 and all later delivery work remain incomplete. |
| `openspec/changes/dallay-565/apply-progress.md` | Read | Records RED-to-GREEN contract evidence and the local PostgreSQL connection failure. |
| `openspec/changes/dallay-565/verify-report.md` | Read | Technical verification is `PASS WITH WARNINGS` for unit 1 only, using fallback direct commands. |
| `openspec/changes/dallay-565/state.yaml` | Read | Unit 1 remains partial; DALLAY-566 is a pending hard gate. |
| `openspec/config.yaml` | Read | Acceptance is required for behavior changes; acceptance-relevant `BLOCKED`/`NOT TESTED` blocks archive. |

#### Current contract update — 2026-09-03

The durable request was narrowed to `invitationId`, `commandId`, and delivery kind, and a focused
negative-count summary test was added. Seven focused contract tests and Kotlin formatting pass, and
the downstream SMP compilation passes. These technical results do not change the acceptance verdict
because no application-under-test target was exercised.

#### Technical handoff used by QA

The verification handoff reports that the focused shared contract tests, formatting, downstream
compile, and DALLAY-564 domain/application unit tests passed. It also records that the DALLAY-564
repository integration tests failed because local PostgreSQL was unavailable, and that the quality
runner was unavailable. Those are technical evidence inputs only. Under the QA contract, unit tests,
source inspection, and diff inspection are not converted into product-acceptance `PASS` results.

## Usage

### Target, Environment, Permissions, and Limitations

- Target: No deployed, preview, running backend, running admin SPA, or other application-under-test
  target was supplied. The local worktree and the shared notifications contract module are the only
  available scope.
- Target surface: unit 1 production additions are
  `InvitationNotificationRequested.kt` and `InvitationDeliverySummaryReader.kt`, with one focused
  test class. No scheduler, consumer, repository correlation, admin composition, provider, or
  DALLAY-566 transport is in this unit.
- DALLAY-564 prerequisite evidence: standalone `Invitation`, `InvitationId`, acceptance command/port,
  lifecycle tests, and `004-create-invitations.yaml` are present. Pure domain/application tests ran;
  repository runtime proof was attempted but blocked by PostgreSQL connection failure.
- Environment: macOS worktree, Gradle/JVM available, local filesystem and focused Gradle execution
  permitted. Docker/PostgreSQL was not available to the repository test connection.
- Credentials and permissions: no deployed-target credentials, provider test account, admin session,
  or authorization fixture for an observable product target was supplied.
- Worktree preservation: During the original QA run, `git diff --name-status` and `git diff --check`
  showed no modified tracked files and the DALLAY-565 artifacts were untracked. The current review
  changes only those now-tracked DALLAY-565 artifacts and shared contract files; no unrelated change
  was discarded or rewritten.
- Limitation: the deterministic runner/FSM is unavailable, so all evidence is `fallback`. No prose
  in this report overrides that limitation.

### Capability Inventory

| Capability | Resolution | Rationale |
|---|---|---|
| Backend unit runner | `selected` | Focused Gradle/JUnit tests can provide supporting contract evidence, but not product acceptance. |
| DALLAY-564 domain/application unit checks | `selected` | The prerequisite lifecycle and handler tests are executable locally and were rerun. |
| PostgreSQL persistence/integration | `selected — BLOCKED` | The repository tests were attempted; both selected tests failed with `java.net.ConnectException`. |
| API/WebFlux acceptance | `unavailable` | No running SMP target, endpoint session, or supplied credentials were available. |
| Browser/Playwright/Chrome | `rejected` | No application target or dev server was supplied; unit 1 has no UI surface. |
| Data/durable-boundary inspection | `selected — supporting only` | Source/diff inspection can establish scope evidence, but cannot prove runtime persistence or observability acceptance. |
| Accessibility | `rejected` | No user-facing UI is introduced by unit 1. No accessibility pass is claimed. |
| Responsive behavior | `rejected` | No user-facing UI is introduced by unit 1. No viewport pass is claimed. |
| Locale/internationalization | `rejected` | The identity-only contract excludes locale; unit 1 does not render or expose a locale surface. |
| Exploratory testing | `unavailable` | No executable target exists to explore. |
| Manual/operator acceptance | `unavailable` | No operator session, target, or acceptance credentials were supplied. |
| Full CI / broad repository suite | `rejected` | The request is limited to stacked PR unit 1; broad `just backend-test-fast`/CI execution is outside the focused boundary. |
| Deterministic quality runner | `unavailable` | `openspec/quality-runner.json` and `sdd-quality-runner.mjs` are absent; direct commands are fallback evidence. |

### Technical Evidence Executed During QA

These results are preserved as technical evidence and are not acceptance scenario results.

| Check | Result | Evidence |
|---|---|---|
| `./gradlew :shared:notifications:test --tests 'com.profiletailors.notifications.domain.InvitationNotificationContractsTest' --rerun-tasks --no-daemon --console=plain` | `PASS` | Exit 0; Gradle `BUILD SUCCESSFUL`; six focused contract tests completed. |
| `./gradlew :shared:notifications:spotlessKotlinCheck :shared:notifications:test --tests 'com.profiletailors.notifications.domain.InvitationNotificationContractsTest' --rerun-tasks --no-daemon --console=plain` | `PASS` | Exit 0; Spotless check and six focused contract tests completed; Gradle `BUILD SUCCESSFUL`. Existing unrelated test warnings were emitted for unnecessary Kotlin non-null assertions. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.platformadmin.domain.InvitationTest' --tests 'com.profiletailors.smp.platformadmin.application.AcceptInvitationHandlerTest' --rerun-tasks --no-daemon --console=plain` | `PASS` | Exit 0; selected DALLAY-564 domain/application tests completed; Gradle `BUILD SUCCESSFUL`. Existing unrelated Boolean boxing warnings were emitted during compilation. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcInvitationAcceptanceRepositoryTest' --rerun-tasks --no-daemon --console=plain` | `BLOCKED` | Two repository tests completed and both failed at PostgreSQL connection setup with `java.net.ConnectException` wrapped by `org.postgresql.util.PSQLException`; Gradle task failed. |
| `git diff --check` | `PASS` | No whitespace errors in the tracked diff; untracked unit files were also checked through Spotless. This is not product acceptance evidence. |
| Deterministic quality runner | `UNAVAILABLE` | No `openspec/quality-runner.json` or `sdd-quality-runner.mjs` found; fallback mode retained. |

### Scenario Matrix

Every row has one allowed QA result. Focused unit tests and static inspection are cited as supporting
evidence only; they do not create an acceptance `PASS` without an application-under-test target.

| ID | Capability/category | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| U1-QA-01 | DALLAY-564 prerequisite / happy path | A committed standalone `Invitation` exists and is usable as the durable correlation source. | `BLOCKED` | `Invitation`, `InvitationId`, acceptance command/port, lifecycle tests, and the Liquibase invitations schema are present. The repository integration proof was attempted and blocked by PostgreSQL `java.net.ConnectException`; no running application target was available. |
| U1-QA-02 | Canonical model / security | Delivery integration uses `InvitationId` and does not target `WaitlistInvitation`. | `NOT TESTED` | Unit 1 adds contracts only and does not execute a delivery flow. The diff contains no unit-1 wiring to either model, and static inspection cannot produce an acceptance pass. |
| U1-QA-03 | Happy path / token safety | An `InvitationNotificationRequested` handoff exposes only Invitation identity, command identity, and delivery kind. | `NOT TESTED` | The focused reflection/shape test passed, but no event publisher, consumer, serialization boundary, durable store, or application target was available for observable acceptance. |
| U1-QA-04 | Boundary / delivery kind | Initial and resend requests expose only `INITIAL` and `RESEND` and retain the same Invitation identity. | `NOT TESTED` | The enum unit test passed, but no command, resend, or runtime event flow exists in unit 1 to exercise the user/operator outcome. |
| U1-QA-05 | Negative / safe validation | A malformed request with a blank command ID is rejected safely, without entering delivery processing. | `NOT TESTED` | The focused blank-command test passed, but it is a library unit check rather than an observable request boundary; external error handling was not exercised. |
| U1-QA-06 | Boundary / delivery summary | A not-yet-recorded or lost handoff yields a zero-count empty summary while Invitation lifecycle data remains readable. | `NOT TESTED` | `InvitationDeliverySummary.EMPTY` was exercised by unit test, but no composed admin read or running target exists. |
| U1-QA-07 | Security / data contract | A many-delivery summary exposes count, latest status, and timestamps without payload, recipient, or token-bearing data. | `NOT TESTED` | The focused reflection test passed for the declared shape, but no repository data or admin response was observed. PostgreSQL execution was unavailable. |
| U1-QA-08 | Repeated / idempotency | Repeating one command key reuses exactly one delivery, while a new resend key creates one additional delivery for the same Invitation. | `NOT TESTED` | Consumer, persistence correlation, uniqueness, and resend behavior are explicitly deferred to later units; no executable path exists in unit 1. |
| U1-QA-09 | Interrupted / state transition | A transaction rollback creates no handoff or Notification, while a successful commit attempts one best-effort handoff. | `NOT TESTED` | Scheduler and transaction callback behavior are not part of unit 1. No transaction target or outbox/after-commit runtime was exercised. |
| U1-QA-10 | Failure / ownership | A provider failure records Notifications failure state while the Invitation remains ACTIVE. | `NOT TESTED` | Provider consumer and delivery-state ownership are later-unit work; no provider or application target was available. |
| U1-QA-11 | Persistence | DALLAY-564 repository reads and conditional acceptance work against PostgreSQL. | `BLOCKED` | The focused repository test was executed; both tests failed before assertions because PostgreSQL could not be reached (`java.net.ConnectException`). Rerun requires Docker/PostgreSQL availability. |
| U1-QA-12 | Unauthorized/security | An unauthorized admin/API caller cannot read or mutate invitation delivery data. | `NOT TESTED` | Unit 1 exposes no endpoint and no target credentials/session were supplied. No security boundary can be observed. |
| U1-QA-13 | DALLAY-566 dependency boundary | Delivery implementation stops before inventing token transport, envelope, encoding, TTL, validation, or recipient binding. | `BLOCKED` | DALLAY-566 has not defined the handoff properties. Tasks 1.3 is unchecked and the design explicitly keeps this boundary pending; unit 2 must remain held. |
| U1-QA-14 | Later-unit containment | Unit 1 introduces no scheduler, consumer, repository correlation/schema, admin composition, provider, or token-handoff production behavior. | `NOT TESTED` | The branch additions remain limited to two shared contract files and one focused test file; this review narrows one contract and updates its tests and artifacts. This is diff evidence only and cannot be an acceptance pass. |
| U1-QA-15 | Preservation / scope safety | Unrelated worktree changes remain untouched while unit 1 is evaluated. | `NOT TESTED` | Original QA status showed only DALLAY-565 artifacts and unit files. The current diff remains scoped to those files, but no independent mutation harness was available to observe preservation behavior. |
| U1-QA-16 | Browser | An operator or user can observe the unit-1 capability through a browser surface. | `NOT TESTED` | Unit 1 creates no browser surface and no dev server or deployed URL was supplied. |
| U1-QA-17 | Accessibility | The affected user/operator surface is keyboard and screen-reader usable. | `NOT TESTED` | No affected UI exists in unit 1 and no browser target is available. |
| U1-QA-18 | Responsive | The affected surface behaves at supported viewport sizes. | `NOT TESTED` | No affected UI exists in unit 1 and no viewport target is available. |
| U1-QA-19 | Internationalization | Locale is resolved in its owning context without entering the durable request contract. | `NOT TESTED` | Unit 1 intentionally excludes locale from the identity-only event and does not execute rendering, transport, or locale-specific behavior. |
| U1-QA-20 | Exploratory/manual | An operator can inspect, repeat, interrupt, and recover the invitation notification workflow. | `NOT TESTED` | No executable product target, operator session, provider account, or manual QA environment was supplied. |

## Troubleshooting

### Untested Scope and Rerun Prerequisites

| Scope | Reason | Rerun prerequisite |
|---|---|---|
| Live Invitation-to-Notifications behavior | No application-under-test target; unit 1 has no scheduler/consumer/admin flow. | Provide a matching local/preview target and execute the relevant later-unit acceptance scenarios after implementation. |
| DALLAY-564 PostgreSQL repository proof | Both selected repository tests failed with `java.net.ConnectException`. | Start the repository's PostgreSQL/Testcontainers environment, then rerun `R2dbcInvitationAcceptanceRepositoryTest`. |
| Token-free serialization, logs, metrics, persistence, and provider output | Unit 1 only defines contracts; no consumer/provider/durable runtime exists here. | Keep DALLAY-566 as the gate, then run the unit-2/unit-4 security and integration checks against a real target. |
| Commit/rollback, idempotency, resend, delivery ownership, admin composition, and legacy reads | Deferred to later stacked units by `tasks.md`. | Do not test or implement across the boundary in unit 1; rerun after the appropriate stacked units land. |
| Browser, accessibility, responsive, locale, exploratory, and manual acceptance | No affected UI or target was supplied. | Provide the affected admin/product surface and a browser/manual acceptance environment. |
| Deterministic runner envelopes | Quality runner files are unavailable. | Restore the configured runner/FSM or retain the explicit `fallback` limitation. |

### Findings

| ID | Severity | Scenario / location | Evidence | Status |
|---|---|---|---|---|
| F-01 | `P1` | Acceptance target unavailable | No deployed target, running SMP API, admin SPA, provider account, or browser target was supplied; `openspec/config.yaml` requires acceptance for behavior changes. | `open — archive blocking` |
| F-02 | `P1` | DALLAY-566 token handoff gate | `tasks.md` task 1.3 is unchecked; `design.md` and the spec assign transport, envelope, TTL, validation, recipient binding, and correlation to DALLAY-566. | `dependency hold — expected and must remain` |
| F-03 | `P2` | DALLAY-564 PostgreSQL repository evidence | `R2dbcInvitationAcceptanceRepositoryTest` ran two tests and both failed with `java.net.ConnectException` before repository assertions. | `open — environment blocked` |
| F-04 | `P2` | Safe-validation evidence coverage | Blank recipient/workspace cases no longer apply to the identity-only event. Focused unit coverage now rejects a negative summary count; an external validation boundary remains unavailable. | `partially resolved — acceptance evidence unavailable` |
| F-05 | `P2` | Durable token-free behavior not observable in unit 1 | Event/summary shape checks pass as supporting unit evidence, but no serialization, event bus, log/metric, persistence, or provider runtime exists in this unit. | `deferred — later-unit acceptance required` |

No `CRITICAL` or `P0` implementation defect was observed in the unit-1 diff. The P1 findings are
acceptance/progression blockers, not permission to cross the DALLAY-566 boundary.

### Verdict

`NOT TESTED`

#### Rationale

The available Gradle checks provide useful technical evidence: unit-1 contract tests, formatting, and
DALLAY-564 pure domain/application tests passed, while the DALLAY-564 repository check was blocked by
PostgreSQL unavailability. They do not establish observable user/operator acceptance. This repository
has no supplied application-under-test target for the contract surface, no deterministic QA runner, and
no runtime path for the later delivery behaviors. Therefore the correct QA verdict is `NOT TESTED`, not
`PASS` or `PASS WITH WARNINGS`.

The acceptance gate remains blocked for archive. DALLAY-566 is still a hard dependency hold, and no
unit-2 delivery/handoff work may begin by inventing or duplicating token transport.

### Limitations and Implementation Handoff

- QA did not modify source code, tests, production behavior, or the DALLAY-566 boundary.
- QA did not commit, push, deploy, call a provider, mutate application data, or create a PR.
- Do not claim full DALLAY-565 acceptance from this report.
- Preserve the unit-1 boundary: `InvitationNotificationRequested`, `InvitationDeliveryKind`,
  `InvitationDeliverySummary`, and `InvitationDeliverySummaryReader` are contracts/model only.
- Keep DALLAY-566 as a dependency hold until its approved ephemeral handoff, envelope, TTL, ownership,
  validation, recipient binding, and non-secret correlation are defined.
- Before rerunning acceptance, make PostgreSQL available for the DALLAY-564 repository cases and
  provide a matching executable target for observable checks.

## References

- [Proposal](proposal.md)
- [Design](design.md)
- [Tasks](tasks.md)
- [Verification report](verify-report.md)
- [Change state](state.yaml)
