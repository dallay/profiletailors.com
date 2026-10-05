# Design: Batch 2 — Remove Dead ThrowsCount / TooGenericExceptionCaught Suppressions in Tenancy Handlers

## Technical Approach

Delete-only plus one narrowing: remove 5 dead `@Suppress` lines and narrow 1 split live/dead pair in
`tenancy/application`. No logic, signature, config, or baseline edits. Verified against Lote 1
(`archive/2026-09-13-chore-suppress-l01-media-dead-suppress/design.md`): same dead-annotation
mechanism, plus a new narrowing pattern Lote 1 never exercised. Proposal baseline-clearance holds —
no baseline IDs reference these symbols, so the Lote 1 `StaleAssetReconciler` trap does not recur.

## Architecture Decisions

| Option                                                                     | Tradeoff                                                                                                | Decision                                                    |
|----------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| Delete 5 dead annotations; narrow `:140` pair to `@Suppress("LongMethod")` | Zero behavior change; split-pair narrowing is the approved technique for live+dead annotation pairs     | **Chosen** — matches proposal scope                         |
| Delete the whole `:140` line including `LongMethod`                        | Clears one more line but `Transfer.handle` is ~66 effective LOC vs 60 limit — resurfaces a live finding | Rejected — `LongMethod` is recorded deferred debt, retained |
| Touch `detekt.yml` / `detekt-baseline.xml` by hand                         | Out of scope; baseline is a ratchet (shrink only via tool run)                                          | Rejected — never hand-edit                                  |
| Restructure handlers or move layers                                        | Unrelated churn, violates minimal-scope rule                                                            | Rejected                                                    |

### Narrowing pattern (new vs Lote 1)

Lote 1 only deleted whole lines. Here `:140` carries a split pair — `ThrowsCount` dead, `LongMethod`
live — so the technique is narrowing, not deletion:

```kotlin
@Suppress("ThrowsCount", "LongMethod")  // before
@Suppress("LongMethod")                 // after
```

Approved for future batches whenever a live/dead pair shares one line.

## Data Flow

Not applicable — no runtime change. Annotation removal only affects static analysis.

## File Changes

| File                                                                        | Action        | Description                                                                                                                          |
|-----------------------------------------------------------------------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/.../tenancy/application/TenancyOwnershipHandlersInternal.kt:35` | Delete 1 line | Dead `TooGenericExceptionCaught`: `detekt.yml:214` excludes `**/application/**`                                                      |
| `.../TenancyOwnershipHandlersInternal.kt:66`                                | Delete 1 line | Dead `ThrowsCount` on `Add.handle`: 1 throw, strict `>` vs `max:3` (`detekt.yml:553-555`) never fires                                |
| `.../TenancyOwnershipHandlersInternal.kt:140`                               | Narrow        | Dead `ThrowsCount` (3 throws ≤ 3; `require`/`requireNotNull` are calls, not `throw` expressions) removed; live `LongMethod` retained |
| `.../TenancyOwnershipHandlersInternal.kt:223`                               | Delete 1 line | Dead `ThrowsCount` on `Remove.handle`: 2 throws                                                                                      |
| `.../tenancy/application/UpdateWorkspaceMembershipStatusHandler.kt:25`      | Delete 1 line | Dead `ThrowsCount`: 2 throws at `:34,:37`                                                                                            |
| `.../UpdateWorkspaceMembershipStatusHandler.kt:62`                          | Delete 1 line | Dead `TooGenericExceptionCaught`: same application exclusion                                                                         |

## Interfaces / Contracts

None — no new types, APIs, or signatures.

## Testing Strategy

| Layer  | What to Test                      | Approach                                                                                                                                                          |
|--------|-----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Static | Zero new findings after narrowing | `just backend-lint` PASS — empirical gate for `LongMethod` marginality (~66 vs 60); pre-approved fallback restores minimal suppression, no refactor in this batch |
| Arch   | Hexagonal direction intact        | `just backend-check` PASS                                                                                                                                         |
| Unit   | None new                          | Existing `TenancyOwnershipHandlersInternalTest` + `UpdateWorkspaceMembershipStatusHandlerTest` cover handlers; annotation-only diff needs no test changes         |

## Migration / Rollout

No migration required. Blast radius: 2 files, ≤6 annotation lines, surrounding code byte-identical.
Rollback: re-add deleted/narrowed lines (`git diff` shows ≤6 lines). Constraints: `shared/`,
`detekt.yml`, baseline untouched; zero new suppressions; `ForbiddenSuppress` scope stays `kt`.

## Open Questions

None — scope closed, spec uncontradicted.
