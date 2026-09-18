# Apply Progress: hotfix-direct-invitation-issued-by-fk

## Delivery

- Strategy: `single-pr`
- Base: `main`
- Worktree branch: `feat/dallay-567-invitation-evidence` (pre-existing dirty dallay-567 files untouched)
- Layer position: Single PR; no chained-PR metadata applies.
- Review workload: forecast Low (60-120 lines), actual main-code diff ~12 lines, tests ~140 lines.

## Completed

- [x] 1.1 Grepped all writers: `CreateInvitationHandler.kt:73`, `InviteWaitlistEntryHandler.kt:101,142`, `InvitationActivationCoordinator.kt:123`; repository only binds domain values; sole domain `accept()` caller is the coordinator.
- [x] 1.2 Added RED regression tests: prefixed `issuedBy` in `CreateInvitationHandlerTest` and `InviteWaitlistEntryHandlerTest`, prefixed fixture + `createdBy` assert in supersede test, bare-uuid/prefixed `acceptedPrincipalId` tests in `InvitationActivationCoordinatorTest`, new `PlatformPrincipalIdsTest`.
- [x] 1.3 RED confirmed: 4 failures (bare-UUID assertions + `IllegalArgumentException` from `UUID.fromString` on prefixed `issuedBy`).
- [x] 2.1 `CreateInvitationHandler`: `issuedBy = PlatformPrincipalIds.fromUuid(command.operatorPrincipalId)`.
- [x] 2.2 `InviteWaitlistEntryHandler`: same helper at `:142`; `:101` now `PlatformPrincipalIds.toUuid(existingInvitation.issuedBy)` (handles prefixed + legacy bare).
- [x] 2.3 Accept path: `PlatformPrincipalIds.fromUuid(identity.principalId)` (idempotent overload, no `user-user-`) + `require(startsWith("user-"))`; `Invitation.accept()` signature untouched.
- [x] 2.4 `InvitationId` untouched (ADR-0020); zero migrations; no workspace/provisioning logic touched.
- [x] 3.1 GREEN: focused 6-class run PASS; full `platformadmin.*` incl. `@postgres` PASS after seeding prefixed `principals` rows in `R2dbcInvitationRepositoryTest` and `PlatformAdminInvitationTransactionPostgresIntegrationTest`.
- [x] 3.2 BDD fast: full suite 247 scenarios, 0 failures (includes `invitations-direct` 201 create, waitlist invite, acceptance, invite-only registration); `git diff --check` PASS.
- [x] 3.3 `:server:smp:detekt` + `:server:smp:spotlessKotlinCheck` PASS; no broad builds (`backend-check`, `ci`, image, deploy not run).
- [x] 4.1 RED→GREEN shown; BDD proves direct create returns 201 with seeded `user-<uuid>` operator; diff limited to 4 main files + tests/seeds.
- [x] 4.2 Rollback: redeploy v0.5.0 image, revert handler commit; no schema rollback needed. No commits made, prod untouched.

## Verification

- Focused unit (6 classes: Create/InviteWaitlist handlers, Coordinator, PlatformPrincipalIds, Accept handler, InvitationTest): PASS.
- `./gradlew :server:smp:test --tests "com.profiletailors.smp.platformadmin.*"`: PASS (includes `@postgres` R2dbc + transaction integration).
- `:server:smp:bddFastTest` (full fast suite, 247 scenarios): PASS, 0 failures.
- `:server:smp:detekt`, `:server:smp:spotlessKotlinCheck`: PASS.
- `git diff --check`: PASS.
- Not run: `just backend-check`, `just ci`, postgres BDD suite, E2E, deploy 0.5.1, QA-01 rerun (verify/QA ownership).

## Deviations

- RED proof is a unit assertion of bare-UUID persistence (the FK-violation cause), not a live `DataIntegrityViolationException`; the `@postgres`/`bddFastTest` GREEN runs prove FK satisfaction against real constraints.
- BDD/`@postgres` seeds gained prefixed `principals` rows (`user-<admin-uuid>`, `user-principal-1`); prefixed fixture ids mirror the canonical ADR-0005 form the fixed code writes. Full BDD alignment of legacy bare fixture ids (e.g. `principal-1`) is out of scope (see `migrate-to-pure-uuid` proposal).

## Risks

- Legacy bare-UUID `issued_by` rows cannot exist from these paths (both INSERTs 500'd pre-fix), so no backfill needed; if any bare rows were written by another path, they remain untouched.
- `user-principal-1`-style prefixed test ids are fixture-only; prod ids are always `user-<uuid>`.
- Deploy + QA-01 rerun still pending (verify/QA phases own them).

## Remaining

- None for apply. Next: `sdd-verify` (12/12 tasks complete).
