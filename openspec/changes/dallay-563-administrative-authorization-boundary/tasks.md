# Tasks: `dallay-563-administrative-authorization-boundary`

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~120 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

## Phase 1: Unit Tests — `OperatorAccessResolver`

- [x] 1.1 Add `OperatorAccessResolverTest` case: `findActiveByPrincipalId` returns empty list → `resolve()` returns `OperatorAccess(id, emptySet())` (default-deny)
- [x] 1.2 Add `OperatorAccessResolverTest` case: two active assignments (PLATFORM_OPERATOR + AUDITOR) → `resolve()` returns `OperatorAccess(id, {OPERATOR, AUDITOR})`
- [x] 1.3 Add `OperatorAccessResolverTest` case: prefixed principal ID `user-<uuid>` is stripped before `findActiveByPrincipalId` lookup (bare UUID passed to repo)

## Phase 2: Controller Integration Tests

- [x] 2.1 Add `AdminOperatorControllerTest` case: `GET /api/admin/operators` with no role assignment → 403 with `PLATFORM_ACCESS_DENIED` code
- [x] 2.2 Add `AdminOperatorControllerTest` case: `GET /api/admin/operators` with OPERATORS_READ permission → 200 with operator list
- [x] 2.3 Add `AdminOperatorControllerTest` case: missing principal context → 401

## Phase 3: Documentation

- [x] 3.1 Add note in `design.md` (or a new `docs/architecture/adr/`) documenting the `AdminDashboardController` deviation: it returns `ResponseEntity.status(HttpStatus.FORBIDDEN)` instead of throwing `PlatformAccessDeniedException` — out of scope for this change, flagged for future review

## Phase 4: Verification

- [ ] 4.1 Run scoped tests: `just backend-test-fast --tests "*platformadmin*OperatorAccessResolver*"` — **BLOCKED** by pre-existing publishing/ compilation errors
- [ ] 4.2 Confirm all 3 new resolver cases pass and existing tests still pass — **BLOCKED** by pre-existing publishing/ compilation errors
- [ ] 4.3 Run full `just backend-test-fast` to confirm no regressions — **BLOCKED** by pre-existing publishing/ compilation errors

## Dependencies

- Phase 1 (resolver unit tests) has no production dependencies — pure test additions
- Phase 2 (controller integration tests) has no production dependencies — pure test additions
- Phase 3 (documentation) is independent
- Phase 4 (verification) depends on Phases 1 and 2

## Files to Modify

| File | Change |
|------|--------|
| `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/OperatorAccessResolverTest.kt` | Add 4 new test cases (3 from tasks + 1 prefix-stripping verification) |
| `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminOperatorControllerTest.kt` | No changes needed — tasks 2.1–2.3 already covered by existing tests |
| `openspec/changes/dallay-563-administrative-authorization-boundary/design.md` | Add `AdminDashboardController` deviation note (Phase 3) |
