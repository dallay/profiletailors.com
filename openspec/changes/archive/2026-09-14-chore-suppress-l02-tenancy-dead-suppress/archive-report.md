# Archive Report: chore-suppress-l02-tenancy-dead-suppress

- change: `chore-suppress-l02-tenancy-dead-suppress` (epic issue #1019, Batch 2 — tenancy dead
  suppressions)
- mode: openspec
- date: 2026-09-14
- verify verdict: **PASS WITH NOTES** (no CRITICAL issues; 4 informational notes)
- archived to: `openspec/changes/archive/2026-09-14-chore-suppress-l02-tenancy-dead-suppress/`

## Specs Synced

| Domain       | Action  | Details                                                                                                                                                                                                                                                                                                                                                                                                                            |
|--------------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| code-hygiene | Updated | Appended 5 requirements from delta to existing main spec (Lote 1's 4 requirements preserved byte-identical): Dead Suppression Removal in Tenancy Handlers (4 deletions + 1 retention as deferred debt), Narrowed Suppression at TransferWorkspaceOwnershipHandler (1 narrowing), Deferred LongMethod Debt on Transfer Handler, Static-Analysis Gates Stay Green (Tenancy Batch), Architecture and Config Integrity (Tenancy Batch) |

Merge note: the delta's two gate requirements share names with Lote 1's gate requirements
(`Static-Analysis Gates Stay Green`, `Architecture and Config Integrity`) but carry batch-specific
scope. Replacing by name would have destroyed Lote 1 wording, so they were appended with a
` (Tenancy Batch)` qualifier — non-destructive, headings stay unique. All Lote 1 requirements are
untouched.

## Archive Contents

- proposal.md ✅ (point-in-time intent: 5 deletions + 1 narrowing; actual outcome 4 + 1 narrowing + 1
  retained — deviation adjudicated in verify report, normative truth lives in the delta spec)
- specs/code-hygiene/spec.md ✅ (amended to the true outcome before archiving)
- design.md ✅ (point-in-time intent record; delta spec is normative)
- tasks.md ✅ (7/8 Phase 1–2 items done + 1 reverted-as-debt 1.5; Phase 3 conditional applied
  analogously at Update :25)
- verify-report.md ✅ (PASS WITH NOTES)
- state.yaml ✅ (current_phase: archive, next: done)

## Source of Truth Updated

- `openspec/specs/code-hygiene/spec.md` (now 9 requirements: 4 Lote 1 + 5 Lote 2)

## Production Diff (ready for PR)

- `server/smp/.../tenancy/application/TenancyOwnershipHandlersInternal.kt`: 3 deleted lines (`:35`,
  `:66`, `:223`) + 1 narrowed line (`:140` → `@Suppress("LongMethod")`)
- `server/smp/.../tenancy/application/UpdateWorkspaceMembershipStatusHandler.kt`: 1 deleted line
  (`:62`); `:25` retained byte-identical (proven live: 4 counted throws > max 3)
- `package.json` / `pnpm-lock.yaml` dirt in worktree is pre-existing and out of scope — excluded
  from the batch PR, untouched by this change
- No commit, no push (not requested)

## Remaining Debt (carried to future epic #1019 batch)

- `TenancyOwnershipHandlersInternal.kt:140` narrowed `@Suppress("LongMethod")` retained
  byte-identical (~66 effective LOC vs 60); removal is a separately scoped change, baseline MUST NOT
  be hand-edited
- `UpdateWorkspaceMembershipStatusHandler.kt:25` `@Suppress("ThrowsCount")` retained byte-identical
  (4 counted throws > max 3); removal would require handler refactor in a separately scoped change

## SDD Cycle Complete

Planned, implemented, verified (PASS WITH NOTES), and archived. Ready for the next change.
