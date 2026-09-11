# Proposal: Fix POST /api/admin/invitations/direct 500 (fk_invitations_issued_by)

## Intent

`POST /api/admin/invitations/direct` returns 500 in prod (fenix, v0.5.0): `DataIntegrityViolationException` on `fk_invitations_issued_by`. Handler stores bare UUID as `issued_by`, but `principals(id)` holds `user-<uuid>` per ADR-0005. Restore direct-invitation creation without schema changes.

## Scope

### In Scope
- Format `issuedBy` with prefix via `PlatformPrincipalIds.fromUuid` in `CreateInvitationHandler`
- Apply same fix to `acceptedPrincipalId` in `InviteWaitlistEntryHandler`, `AcceptInvitation.kt`/facade where bare UUID is persisted
- TDD regression test: seeded `user-<uuid>` principal + direct create succeeds
- Focused verification: backend-check scoped, relevant BDD fast; deploy 0.5.1 + rerun QA-01

### Out of Scope
- Changing `InvitationId` UUID form (ADR-0020 exception stays)
- Table migrations, backfills, workspace/provisioning logic
- New endpoints, permissions, UI, notification behavior

## Capabilities

### New Capabilities
- None — pure defect fix, no new product contract.

### Modified Capabilities
- None — restores `invitations` spec behavior (direct create returns 201); no REQUIREMENT text changes, delta spec not needed.

## Approach

Replace `operatorPrincipalId.toString()` with `PlatformPrincipalIds.fromUuid(...)` at invitation persistence points so `issued_by`/`accepted_principal_id` satisfy the FK to `principals(id)`. Keep `InvitationId` untouched. Red regression test first, then fix, then scoped gates.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/.../platformadmin/application/handler/CreateInvitationHandler.kt` | Modified | Prefix `issuedBy` at save |
| `server/smp/.../platformadmin/application/handler/InviteWaitlistEntryHandler.kt` | Modified | Same prefix fix if bare UUID persisted |
| `server/smp/.../platformadmin/application/AcceptInvitation.kt` (+facade/coordinator callers) | Modified | Prefix `acceptedPrincipalId` where applicable |
| `server/smp/.../platformadmin/application/PlatformPrincipalIds.kt` | Reused | Canonical prefix helper, no change expected |
| Backend tests (unit + BDD fast) | New/Modified | One red-first regression test |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Another writer still stores bare UUID | Med | Grep all `issued_by`/`accepted_principal_id` writers in change; cover each in test |
| Double-prefix (`user-user-…`) | Low | Use single helper; assert exact `user-<uuid>` in test |
| Scope creep into workspace logic | Low | Hotfix gate: reject files outside listed handlers/tests |

## Rollback Plan

Redeploy v0.5.0 image; no migration means zero schema rollback. Revert handler commit if 0.5.1 regresses (failing INSERT wrote nothing).

## Dependencies

- ADR-0005 (prefixed platform principal IDs), ADR-0020 (`InvitationId` UUID exception)
- Prod log evidence: `AdminInvitationController#createDirectInvitation` FK violation

## Success Criteria

- [ ] Regression test fails before fix, passes after
- [ ] `POST /api/admin/invitations/direct` returns 201 with seeded `user-<uuid>` operator
- [ ] No Detekt/compiler warnings; scoped backend-check + relevant BDD fast green
- [ ] 0.5.1 deployed healthy; QA-01 direct-invitation flow passes
- [ ] No commits without request; diff limited to handlers + tests
