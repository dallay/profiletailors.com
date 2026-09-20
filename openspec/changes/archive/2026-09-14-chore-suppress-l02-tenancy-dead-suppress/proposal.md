# Proposal: Batch 2 — Remove Dead ThrowsCount / TooGenericExceptionCaught Suppressions in Tenancy Handlers

## Intent

Six `@Suppress` annotations in the tenancy application layer are dead: two
`TooGenericExceptionCaught` silenced nothing (`detekt.yml:214` excludes `**/application/**`, same as
Lote 1's `MediaHandlers.kt:243`), and four `ThrowsCount` sit below the `max:3` strict-`>` threshold.
Clearing them continues epic #1019 paydown as a zero-behavior batch. Supersedes Lote 1's "tenancy
x2" estimate: 6 annotations.

## Scope

### In Scope

- `TenancyOwnershipHandlersInternal.kt:35` — delete dead `@Suppress("TooGenericExceptionCaught")`.
- Same file `:66` — delete dead `@Suppress("ThrowsCount")` on `AddWorkspaceOwnerHandler.handle` (1
  throw).
- Same file `:140` — narrow `@Suppress("ThrowsCount", "LongMethod")` to `@Suppress("LongMethod")` (3
  throws; `require`/`requireNotNull` don't count).
- Same file `:223` — delete dead `@Suppress("ThrowsCount")` on `RemoveWorkspaceOwnerHandler.handle`
  (2 throws).
- `UpdateWorkspaceMembershipStatusHandler.kt:25` — delete dead `@Suppress("ThrowsCount")` (2 throws
  at `:34,:37`).
- Same file `:62` — delete dead `@Suppress("TooGenericExceptionCaught")` (same application
  exclusion).
- Gates: `just backend-lint` PASS and `just backend-check` PASS.

### Out of Scope

- Retaining `LongMethod` on `TransferWorkspaceOwnershipHandler.handle` (~66 vs 60 — recorded
  deferred debt, not cleared here).
- Any `shared/` module, `detekt.yml`, `detekt-baseline.xml` (ratchet-only, hand-untouched), or
  production-logic change.
- Remaining epic batches (UNUSED_PARAMETER, SQL constants, publishing structural).

## Capabilities

### New Capabilities

None — pure annotation deletion/narrowing, no spec-level behavior changes.

### Modified Capabilities

None — no requirement changes.

## Approach

Annotation-only diff, zero logic/signature changes. Baseline-clearance verified per candidate (no
baseline IDs reference these symbols — the Lote 1 `StaleAssetReconciler` trap does not recur).
Control: unsuppressed `auditedMutation :26-53` (2 throws) passes lint. Handlers stay internal
`@Service` via mediator; existing tenancy handler tests instantiate directly — no test or BDD
changes.

## Affected Areas

| Area                                                                                      | Impact   | Description               |
|-------------------------------------------------------------------------------------------|----------|---------------------------|
| `server/smp/.../tenancy/application/TenancyOwnershipHandlersInternal.kt:35,:66,:140,:223` | Modified | 3 deletions + 1 narrowing |
| `server/smp/.../tenancy/application/UpdateWorkspaceMembershipStatusHandler.kt:25,:62`     | Modified | 2 deletions               |

## Risks

| Risk                                                                        | Likelihood | Mitigation                                                                          |
|-----------------------------------------------------------------------------|------------|-------------------------------------------------------------------------------------|
| `LongMethod` marginality on Transfer (~66 vs 60) trips lint after narrowing | Med        | Empirical `backend-lint` gate is the control; fallback restores minimal suppression |
| Throw-count miscount (`require` variants, nested lambdas)                   | Low        | Detekt rule-source evidence verified; lint gate confirms                            |

## Rollback Plan

Re-add narrowed/deleted annotation lines (`git diff` shows ≤6 lines). No migration, config, or data
involved.

## Dependencies

None. Epic tracker: dallay/profiletailors.com#1019. Zero new `@Suppress`.

## Success Criteria

- [ ] 5 deletions + 1 narrowing applied, surrounding code byte-identical
- [ ] `LongMethod` on Transfer retained as sole suppression
- [ ] `just backend-lint` PASS with zero new findings (baseline only shrinks or stays equal)
- [ ] `just backend-check` PASS (arch tests green)
- [ ] Zero new `@Suppress` introduced

## Future Batch Order

UNUSED_PARAMETER → SQL constants → publishing structural last. One small change per batch.
