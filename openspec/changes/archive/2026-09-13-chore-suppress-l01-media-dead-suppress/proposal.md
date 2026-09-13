# Proposal: Lote 1 — Remove Dead TooGenericExceptionCaught Suppressions in media/application

## Intent

Two `@Suppress("TooGenericExceptionCaught")` annotations in `media/application` are dead: `detekt.yml:202-215` excludes `**/application/**` from that rule, so the suppressions silence nothing. Removing them starts paying down epic #1019 debt with a zero-behavior batch.

## Scope

### In Scope
- Delete `@Suppress("TooGenericExceptionCaught")` at `MediaHandlers.kt:243`.
- Delete `@Suppress("TooGenericExceptionCaught")` (+ trailing comment) at `StaleAssetReconciler.kt:96`.
- Gates: `just backend-lint` PASS and `just backend-check` PASS.

### Out of Scope
- All remaining suppressions (tenancy x2, UNUSED_PARAMETER, SQL constants, publishing structural — future lotes).
- Any `shared/` module, `detekt.yml`, `detekt-baseline.xml`, or production-logic change.
- `package.json` / `pnpm-lock.yaml` (dirty in worktree — do not touch).

## Capabilities

### New Capabilities
None — pure annotation deletion, no spec-level behavior changes.

### Modified Capabilities
None — no requirement changes.

## Approach

Delete-only the two annotations; no catch-block, signature, or logic edits. Application-layer direction (`domain <- application <- infrastructure`) untouched — files stay in place. Confirm `HexagonalArchTest` + `ComponentScanArchTest` green via `backend-check`.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `server/smp/.../media/application/MediaHandlers.kt:243` | Modified | Remove 1 dead annotation line |
| `server/smp/.../media/application/StaleAssetReconciler.kt:96` | Modified | Remove 1 dead annotation + comment |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Baseline ID embeds annotation text (`detekt-baseline.xml:88`, LongMethod on `processBlob`) and resurfaces as new finding | Low | Run `backend-lint`; if it resurfaces, restore that single line and record evidence |
| Recount variance (explore: 86 files/178 lines; verified: ~70 files main-only) mis-scopes epic | Low | Later lotes re-baseline counts before proposing |

## Rollback Plan

Re-add the two deleted annotation lines (`git diff` shows exactly 2 lines). No migration, config, or data involved.

## Dependencies

None. Epic tracker: issue #1019.

## Success Criteria

- [ ] Both annotations removed, surrounding code byte-identical otherwise
- [ ] `just backend-lint` PASS with zero new findings (baseline only shrinks or stays equal)
- [ ] `just backend-check` PASS (arch tests green)
- [ ] Zero new `@Suppress` introduced (`ForbiddenSuppress` stays clean)

## Future Lote Order

tenancy dead x2 → single-file UNUSED_PARAMETER → SQL constants → publishing structural last (highest behavior-adjacent risk). One small change per lote under epic #1019.
