# Proposal: Batch 1 — Remove Dead TooGenericExceptionCaught Suppression in MediaHandlers

## Intent

The `@Suppress("TooGenericExceptionCaught")` annotation in `MediaHandlers.kt` is dead: `detekt.yml:202-215` excludes `**/application/**` from that rule, so the suppression silences nothing. Removing it starts paying down epic #1019 debt with a zero-behavior batch. The equivalent annotation in `StaleAssetReconciler.kt` is explicitly deferred because it is coupled to the Detekt baseline; retaining it is a minor documentation/maintainability issue, not a runtime correctness issue.

## Scope

### In Scope
- Delete `@Suppress("TooGenericExceptionCaught")` at `MediaHandlers.kt:243`.
- Gates: `just backend-lint` PASS and `just backend-check` PASS.

### Out of Scope
- The `StaleAssetReconciler.kt:96` suppression; defer any potential removal to a separately scoped future batch with tool-run baseline regeneration.
- All remaining suppressions (tenancy x2, UNUSED_PARAMETER, SQL constants, publishing structural — future batches).
- Any `shared/` module, `detekt.yml`, `detekt-baseline.xml`, or production-logic change.
- `package.json` / `pnpm-lock.yaml` (dirty in worktree — do not touch).

## Capabilities

### New Capabilities
None — pure annotation deletion, no spec-level behavior changes.

### Modified Capabilities
None — no requirement changes.

## Approach

Delete only the `MediaHandlers.kt` annotation; no catch-block, signature, or logic edits. Retain `StaleAssetReconciler.kt:96` byte-identical. Application-layer direction (`domain <- application <- infrastructure`) untouched — files stay in place. Confirm `HexagonalArchTest` + `ComponentScanArchTest` green via `backend-check`.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/.../media/application/MediaHandlers.kt:243` | Modified | Remove 1 dead annotation line |
| `server/smp/.../media/application/StaleAssetReconciler.kt:96` | Deferred | Retain byte-identical; any potential removal requires a separately scoped change |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Baseline ID embeds annotation text (`detekt-baseline.xml:88`, LongMethod on `processBlob`) | Low | Keep the `StaleAssetReconciler.kt:96` suppression out of scope and byte-identical |
| Recount variance (explore: 86 files/178 lines; verified: ~70 files main-only) mis-scopes epic | Low | Later batches re-baseline counts before proposing |

## Rollback Plan

Re-add the deleted `MediaHandlers.kt` annotation line (`git diff` shows exactly 1 line). No migration, config, or data involved.

## Dependencies

None. Epic tracker: issue #1019.

## Success Criteria

- [ ] `MediaHandlers.kt` annotation removed, surrounding code byte-identical otherwise
- [ ] `StaleAssetReconciler.kt:96` retained byte-identical as deferred work
- [ ] `just backend-lint` PASS with zero new findings (baseline only shrinks or stays equal)
- [ ] `just backend-check` PASS (arch tests green)
- [ ] Zero new `@Suppress` introduced (`ForbiddenSuppress` stays clean)

## Future Batch Order

tenancy dead x2 → single-file UNUSED_PARAMETER → SQL constants → publishing structural last (highest behavior-adjacent risk). One small change per batch under epic #1019.
