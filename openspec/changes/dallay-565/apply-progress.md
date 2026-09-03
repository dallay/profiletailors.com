# Apply Progress: `dallay-565` unit 1

## Overview

### Scope

- **Change:** DALLAY-565 Invitation Notification Delivery
- **Unit:** 1, contracts/model
- **Branch:** `feat/dallay-565-contracts`
- **Base:** `main`
- **Position:** 1 of 4
- **Strategy:** `github-stacked-prs`

## Changes

### Completed Tasks

- [x] 1.1 Verified the DALLAY-564 standalone `Invitation`, `InvitationId`, acceptance port/command,
  lifecycle invariants, and `invitations` schema are present in this checkout. The existing
  waitlist handlers remain outside this unit and are not changed.
- [x] 1.2 Added token-free `InvitationNotificationRequested` and `InvitationDeliveryKind` contracts,
  `InvitationDeliverySummary` and `InvitationDeliverySummaryReader`, plus focused contract tests.

### TDD Evidence

The focused test was written before the new contracts. The first RED run failed because the requested
contract classes were absent (`ClassNotFoundException`). After the minimum shared contracts were added,
the focused test passed. A second focused test assertion was added for the event shape; it initially
failed to compile until the new event types were imported, then passed after that test-only correction.
The existing `Notification` model was intentionally left unchanged because unit 1 does not yet own
durable notification persistence correlation fields; unit 2/4 must add those fields with repository
and race coverage.

### Files Changed

- `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/event/InvitationNotificationRequested.kt`
- `shared/notifications/src/main/kotlin/com/profiletailors/notifications/application/ports/InvitationDeliverySummaryReader.kt`
- `shared/notifications/src/test/kotlin/com/profiletailors/notifications/domain/InvitationNotificationContractsTest.kt`
- `openspec/changes/dallay-565/tasks.md`
- `openspec/changes/dallay-565/state.yaml`

## Usage

### Commands Run

- `./gradlew :shared:notifications:test --tests 'com.profiletailors.notifications.domain.InvitationNotificationContractsTest' --no-daemon`
  — RED: failed as expected with five `ClassNotFoundException` failures before production contracts.
- Same command — GREEN: passed, 5 tests completed.
- Same command — GREEN after event-shape assertion — passed, 7 tests completed.
- `./gradlew :shared:notifications:spotlessKotlinCheck :shared:notifications:test --no-daemon`
  — passed; 7 focused tests completed after formatting.
- `./gradlew :shared:notifications:test --no-daemon` — passed; unfiltered shared notifications test
  task completed successfully.
- `./gradlew :server:smp:compileKotlin --no-daemon` — passed; downstream compilation remains valid.
- `./gradlew :server:smp:test --tests 'com.profiletailors.smp.platformadmin.domain.InvitationTest' --tests 'com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcInvitationAcceptanceRepositoryTest' --no-daemon`
  — compile passed; 11 unit tests passed and 2 PostgreSQL tests failed because the local PostgreSQL
  connection was unavailable (`java.net.ConnectException`).

The required broader unfiltered task invocation (`just backend-test-fast`) was not run because this
unit is a shared contract slice and the full SMP fast suite is outside its focused boundary. Apply
does not claim the broader repository suite is green.

## Troubleshooting

### Boundaries and Risks

- No delivery consumer, scheduler, admin composition, schema migration, token transport, raw-token
  handling, or DALLAY-566 behavior was implemented.
- The requested event contains only Invitation identity, command identity, and delivery kind.
  Recipient, workspace, locale, and token-bearing values must be resolved through their owning
  contexts at the approved ephemeral handoff and are not part of this durable contract.
- DALLAY-566 remains a hard gate for any ephemeral token handoff. Unit 1 does not cross it.

## References

- [Tasks](tasks.md)
- [Design](design.md)
- [Verification report](verify-report.md)
- [Change state](state.yaml)
