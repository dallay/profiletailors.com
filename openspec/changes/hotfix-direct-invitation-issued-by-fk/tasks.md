# Tasks: Fix POST /api/admin/invitations/direct 500 (fk_invitations_issued_by)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 60-120 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | single PR |
| Delivery strategy | single-pr |
| Chain strategy | single-pr |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: single-pr
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | RED test + prefix fix + scoped verify | PR 1 | base `main`; handlers + tests only, no migrations |

## Phase 1: Evidence + RED regression (TDD)

- [x] 1.1 Grep all writers of `issued_by`/`accepted_principal_id`/`issuedBy`/`acceptedPrincipalId` in `server/smp/src/main/kotlin`; list hits in `CreateInvitationHandler.kt:73`, `InviteWaitlistEntryHandler.kt:101,142`, `InvitationActivationCoordinator.kt:123`.
- [x] 1.2 Add RED test seeding `principals(id=user-<uuid>)` then direct create via `CreateInvitationHandler`; assert `issuedBy == user-<uuid>` and 201 persist.
- [x] 1.3 Run new test to confirm RED (`DataIntegrityViolationException` on `fk_invitations_issued_by` before fix).

## Phase 2: Prefix fix (single helper)

- [x] 2.1 Fix `CreateInvitationHandler.kt:73`: `issuedBy = PlatformPrincipalIds.fromUuid(command.operatorPrincipalId)`.
- [x] 2.2 Fix `InviteWaitlistEntryHandler.kt:142` same helper; guard `:101` `UUID.fromString` against prefixed value (strip via `toUuid` or reuse stored value).
- [x] 2.3 Fix Accept path (`InvitationActivationCoordinator.kt:123` + `Invitation.accept()` callers): persist `identity.principalId` as-is if already `user-<uuid>`, else `fromUuid`; add `require(startsWith("user-"))` assert, no double-prefix.
- [x] 2.4 Keep `InvitationId` UUID untouched; add zero migrations.

## Phase 3: Focused verification

- [x] 3.1 Re-run RED test GREEN plus `InvitationTest`, `AcceptInvitationHandlerTest`, `R2dbcInvitationRepositoryTest`.
- [x] 3.2 Run scoped `just backend-test-fast` for `platformadmin.*` and relevant `just backend-bdd-fast`; run `git diff --check`.
- [x] 3.3 Confirm Detekt/compiler clean; no broad builds.

## Phase 4: Done + Rollback

- [x] 4.1 Done: RED→GREEN shown, direct create 201, `user-<uuid>` asserts pass, diff handlers+tests only.
- [x] 4.2 Rollback: redeploy v0.5.0 image, revert handler commit; no schema rollback needed.
