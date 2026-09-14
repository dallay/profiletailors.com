# Archive Report: chore-suppress-publishing-r01-problem-details-unused-param

- change: `chore-suppress-publishing-r01-problem-details-unused-param` (epic issue #1046, Publishing R01 — 3 live `UNUSED_PARAMETER` suppressions)
- mode: openspec
- date: 2026-09-14
- verify verdict: **PASS WITH WARNINGS** (no CRITICAL issues; 3 warnings + 1 info, all documentation-level)
- archived to: `openspec/changes/archive/2026-09-14-chore-suppress-publishing-r01-problem-details-unused-param/`

## Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| code-hygiene | Updated | Appended 4 requirements from delta to existing main spec (all prior requirements preserved byte-identical): Live Suppression Removal in PublishingProblemDetailsHandler (3 rename-and-drops), Behavior-Preserving Handler Rename, Static-Analysis Gates Stay Green (Publishing R01), Architecture and Config Integrity (Publishing R01) |

Merge note: the delta's two gate requirements already carry a ` (Publishing R01)` qualifier, so headings stay unique against Lote 1's and Lote 2's gate requirements — appended verbatim, non-destructively. The delta spec line 7 was amended during verify to the implemented site-3 name `handleRecurringScheduleMissing` (explicitly authorized); that amended form is what was synced. All prior live-spec requirements are untouched.

## Archive Contents

- proposal.md ✅ (point-in-time intent: site-3 named `handleRecurringScheduleNotFound`; actual outcome `handleRecurringScheduleMissing` — deviation adjudicated in verify report, normative truth lives in the delta spec)
- specs/code-hygiene/spec.md ✅ (amended to the implemented site-3 name before archiving)
- design.md ✅ (point-in-time intent record; still references the old 31-char site-3 name — see R02 notes; delta spec is normative)
- tasks.md ✅ (13/13 items done: Phase 1 oracle 1.1–1.3, Phase 2 rename-and-drop 2.1–2.4, Phase 3 gates 3.1–3.4)
- verify-report.md ✅ (PASS WITH WARNINGS)
- state.yaml ✅ (current_phase: archive, next: done)

## Source of Truth Updated

- `openspec/specs/code-hygiene/spec.md` (now 14 requirements: 4 Lote 1 + 5 Lote 2 + 1 Lote 3 sweep + 4 Publishing R01)

## Production Diff (ready for PR)

- `server/smp/.../publishing/infrastructure/http/PublishingProblemDetailsHandler.kt`: +3/−6 (3 `@Suppress` + 3 `exception:` params removed; `handleProviderNotConfigured()`, `handlePublicationNotFound()`, `handleRecurringScheduleMissing()`); bodies byte-identical, `@ExceptionHandler(...)` values untouched
- `PublishingProblemDetailsHandlerTest.kt` + `BulkPublishingProblemDetailsHandlerTest.kt`: call sites converted to renamed zero-arg methods, same status/title assertions (test-strategy merge + second-file deviations adjudicated in scope)
- `package.json` / `pnpm-lock.yaml` dirt in worktree is pre-existing and out of scope — excluded from the batch PR, untouched by this change
- No commit, no push (not requested)

## R02 Notes (carried to future epic #1046 batch)

- `design.md` (`:11`, `:22`, `:32`, `:45`) and `tasks.md` (`2.1`, `2.4`) still hardcode the old 31-char site-3 name `handleRecurringScheduleNotFound`; the implemented name is the 30-char `handleRecurringScheduleMissing` (`FunctionNameMaxLength` cap is 30, verified green by forced lint). Doc-consistency fix only; behavior unaffected.
- Remaining `UNUSED_PARAMETER` count needs empirical recount at R02 planning: proposal/spec/design say "remaining 8 sites" but HEAD had 12 and 9 remain (`:62,:80,:89,:107,:167,:180,:193,:212,:230`). Verify flagged "8 vs 9" as a WARNING (spec text drift, no behavior impact); R02 scope statement should say 9 pending a fresh grep.
- `just backend-check` (36m) and BDD (247 scenarios, feature 8/8) were accepted on cited evidence, not re-run; mitigated by the verifier's forced Detekt PASS + byte-identical handler bodies.

## Remaining Debt (carried to future epic #1046 batch)

- `TooManyFunctions` (`:35`) retained byte-identical; removal is a separately scoped change
- 9 deferred `UNUSED_PARAMETER` sites retained byte-identical (R02 scope, after recount)
- Baseline MUST NOT be hand-edited

## SDD Cycle Complete

Planned, implemented, verified (PASS WITH WARNINGS), and archived. Ready for the next change.
