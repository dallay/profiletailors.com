# Tasks: First-Class Invitation (DALLAY-564)

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 650–900 across backend, migration, tests |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 domain/ports → PR 2 persistence/races → PR 3 evidence/docs |
| Delivery strategy | ask-on-risk |
| Chain strategy | github-stacked-prs |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: github-stacked-prs
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Domain and application contracts | PR 1 | trunk=main; parent_branch=main; base=main; branch=feature/dallay-564-first-class-invitation; position=1; Linear=DALLAY-564 |
| 2 | Persistence, Liquibase, CAS, races | PR 2 | trunk=main; parent_branch=feature/dallay-564-first-class-invitation; base=feature/dallay-564-first-class-invitation; branch=feature/dallay-564-first-class-invitation-persistence; position=2; Linear=DALLAY-564 |
| 3 | Evidence, compatibility, docs | PR 3 | trunk=main; parent_branch=feature/dallay-564-first-class-invitation-persistence; base=feature/dallay-564-first-class-invitation-persistence; branch=feature/dallay-564-first-class-invitation-evidence; position=3; Linear=DALLAY-564 |

**Hard gates:** block apply on a second token subsystem, token-bearing durable value, notification
duplication/path, Invitation delivery field, or `WaitlistInvitation` substitution.

## Phase 1: Domain and Application Foundation

- [x] 1.1 **RED → GREEN → focused verification:** Extend `PlatformAdminMarkerCoverageTest.kt`, `AggregateBoundaryTest.kt`, and `ValueObjectImmutabilityTest.kt`; mark Invitation types without changing `WaitlistInvitation`.
- [x] 1.2 **RED → GREEN → focused verification:** Expand `domain/InvitationTest.kt`; implement `domain/Invitation.kt` normalization, source/reference, metadata, terminal rejection, exclusive expiry, `expire(at)`, `revoke()`, and version increments.
- [x] 1.3 **RED → GREEN → focused verification:** Add fake-port tests in `application/AcceptInvitationHandlerTest.kt`; create `application/contracts/InvitationRepository.kt`, keep keys opaque, and make `application/AcceptInvitation.kt` use an acceptance façade over canonical SQL.

## Phase 2: Canonical Persistence and Security Gates

- [x] 2.1 **RED → GREEN → focused verification:** Add `R2dbcInvitationRepositoryTest.kt`; implement `R2dbcInvitationRepository.kt` mapping, reads, locked lookup, save, version CAS, and conditional accept/expire/revoke through the transaction seam.
- [x] 2.2 **RED → GREEN → focused verification:** Create `platformadmin/application/InvitationSecurityBoundaryTest.kt`; reject a second token generator/hasher/URL builder, raw token/URL, `Notification` dependency, or delivery field; preserve DALLAY-565/566.

## Phase 3: Schema and Concurrency Proof

- [x] 3.1 **RED → GREEN → focused verification:** Add `platform-admin/005-harden-invitations.yaml` after `004` and include it in `db.changelog-master.yaml`; enforce lifecycle/source/email/expiry/metadata checks, version, lookup uniqueness/indexes, and one active workspace/email without touching waitlist tables.
- [x] 3.2 **RED → GREEN → focused verification:** Extend or replace `PlatformAdminInvitationTransactionPostgresIntegrationTest.kt` for invalid rows, duplicate-active-email races, locks, rollback, stale CAS, and two-client acceptance races.

## Phase 4: Evidence, Compatibility, and Documentation

- [ ] 4.1 **RED → GREEN → focused verification:** Extend `PlatformAdminInvitationCompatibilityTest.kt` and architecture checks; prove legacy waitlist behavior remains and no forbidden token/delivery/notification coupling exists.
- [ ] 4.2 **RED → GREEN → focused verification:** Update affected ADR/C4/data-model/operations/docs and OpenSpec artifacts with implemented contracts, explicit deferrals, exact validation commands, and evidence status.

## Apply Boundary

This apply slice is PR 2 only: persistence, Liquibase, CAS/race integration, and security-boundary proof.
Compatibility, evidence, and documentation tasks remain pending for their approved stacked work units. The
existing intentional worktree edits are preserved; no commit or push is part of apply.

## Phase 4: Evidence and Documentation

- [x] 4.1 **Compatibility and architecture evidence:** `WaitlistInvitation` and `waitlist_invitations` remain untouched. No second token subsystem, raw token persistence, accept URL, delivery field, or `Notification` dependency was introduced. Canonical `R2dbcInvitationRepository` is the sole R2DBC adapter owning the `invitations` table.
- [x] 4.2 **ADR, C4, data-model, operations, and OpenSpec documentation:** New ADR `0020-first-class-invitation-aggregate.md` added; ADR index updated; data-model README corrected; OpenSpec artifacts aligned.

## Phase 5: Final Verification

- [ ] 5.1 **Final verification and `sdd-qa` acceptance:** Pending independent verification and acceptance QA cycles.
