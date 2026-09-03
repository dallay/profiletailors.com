# Tasks: DALLAY-565 Invitation Delivery

## Overview

This plan contains 16 tasks across four stacked implementation units. Two tasks are complete and 14
remain incomplete; task 1.3 is the current DALLAY-566 dependency gate.

## Changes

### Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 700–900 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 → PR 2 → PR 3 → PR 4 |
| Delivery strategy | ask-on-risk |
| Chain strategy | github-stacked-prs |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: github-stacked-prs
400-line budget risk: High

### Work Units

| Unit | Goal | Stack metadata |
|---|---|---|
| 1 | Contracts/model | `trunk=main; parent_branch=main; base=main; branch=feat/dallay-565-contracts; position=1; issue=DALLAY-565; gate=DALLAY-564` |
| 2 | Delivery/handoff | `trunk=main; parent_branch=feat/dallay-565-contracts; base=feat/dallay-565-contracts; branch=feat/dallay-565-delivery; position=2; issue=DALLAY-565; gate=DALLAY-566` |
| 3 | Admin composition | `trunk=main; parent_branch=feat/dallay-565-delivery; base=feat/dallay-565-delivery; branch=feat/dallay-565-admin-read; position=3; issue=DALLAY-565` |
| 4 | Acceptance/docs | `trunk=main; parent_branch=feat/dallay-565-admin-read; base=feat/dallay-565-admin-read; branch=feat/dallay-565-verification; position=4; issue=DALLAY-565` |

### Phase 1: Gates and Shared Contracts

- [x] 1.1 Hard gate: verify DALLAY-564 standalone `Invitation`, schema, ports, and commands; stop if unavailable.
- [x] 1.2 RED token-free event/kind, correlation, delivery, and summary tests; GREEN minimal shared notification domain/events/ports changes; VERIFY focused tests.
- [ ] 1.3 Hard gate: require DALLAY-566’s approved ephemeral token handoff, envelope, TTL, and ownership; consume only, inventing no transport.

### Phase 2: Backend Domain/Application/Infrastructure

- [ ] 2.1 RED handler tests for stable Invitation ID, command ID, same-key reuse, and new resend key adding one delivery; GREEN update commands, handlers, and bootstrap; VERIFY unit tests.
- [ ] 2.2 RED commit/rollback tests; GREEN add `InvitationNotificationScheduler` after successful completion with best-effort `AFTER_COMMIT`; VERIFY PostgreSQL integration.
- [ ] 2.3 RED consumer tests for one record per key, independent SENT/FAILED state, safe failure, and no reverse event; GREEN modify consumer/email, remove old bridge/events; VERIFY focused tests.
- [ ] 2.4 RED repository tests for correlation, uniqueness races, timestamps, and zero/many reads; GREEN modify Notification domain/port/repository/schema; VERIFY PostgreSQL tests.

### Phase 3: Admin Read and Compatibility

- [ ] 3.1 RED WebFlux tests for lifecycle plus Notifications summary, empty/many deliveries, latest timestamps, and no payload; GREEN modify admin query/model/controller; VERIFY auth/serialization.
- [ ] 3.2 RED legacy-read/rollback tests; GREEN retain legacy table/columns/query/fields while new flows write none; VERIFY startup/history tests.
- [ ] 3.3 RED admin contract tests; GREEN update `apps/web/admin/src/views/WaitlistEntryView.vue` for the composed summary; VERIFY `just admin-check` and `just admin-test`.

### Phase 4: Acceptance, Security, Architecture

- [ ] 4.1 RED scenarios/steps in `platform-admin.feature`/`PlatformAdminBddSteps.kt`; GREEN wire commit/rollback, provider failure, duplicate key, resend multiplicity, composed read, and token-free cases; VERIFY fast/PostgreSQL BDD.
- [ ] 4.2 RED security assertions; GREEN enforce no raw bearer or recoverable URL in events, audit, metrics, logs, payloads, or responses; VERIFY security tests and secret scan.
- [ ] 4.3 RED boundary assertions; GREEN adjust only `HexagonalArchTest.kt`, `ComponentScanArchTest.kt`, `ModularStructureTest.kt`, and `ModularityVerificationTest.kt`; VERIFY `just backend-test-fast` without weakening owners.

### Phase 5: Documentation and OpenSpec

- [ ] 5.1 Update `openspec/changes/dallay-565/{proposal.md,design.md,specs/**}` with contracts, gates, and best-effort limits.
- [ ] 5.2 Update `docs/architecture/c4/{03-component.md,04-code.md}`, transaction/API guidance, compatibility, and rollback; do not claim DALLAY-566 behavior.
- [ ] 5.3 Run focused gates, inspect the diff, and record local versus CI evidence before advancing state.

## Usage

Execute units in stack order. Do not begin unit 2 until task 1.3 is satisfied by DALLAY-566; keep
later-unit validation scoped to the unit that implements the behavior.

## Troubleshooting

If a dependency or runtime is unavailable, leave its task incomplete and record the work as blocked
or not run. Do not infer delivery, persistence, or acceptance results from unit-1 contract tests.

## References

- [Proposal](proposal.md)
- [Design](design.md)
- [Apply progress](apply-progress.md)
- [Change state](state.yaml)
