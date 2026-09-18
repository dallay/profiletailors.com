# Verification Report: 662-invitation-token-lifecycle — PR1 Slice (Work Unit 1: metrics + CAS)

Scope: PR1 ONLY. Tasks 2.1 and 2.2. All other tasks (2.3–2.5, Phase 3, Phase 4) are explicitly OUT and marked Not-run below. Do NOT read this report as a full-change verdict.

## Change and Mode

| Field | Value |
|---|---|
| Change | 662-invitation-token-lifecycle (issue #662) |
| Slice | PR1 / Work Unit 1: metrics + CAS fixes + unit tests |
| Mode | openspec |
| Date | 2026-09-17 |
| Verifier | sdd-verify sub-agent |

## Completeness Table (PR1 scope only)

| Task | Claim | Status |
|---|---|---|
| 2.1 `recordBulkInvite` aggregate-only, no per-value tags, signature unchanged | Checked in tasks.md | DONE, verified by inspection + test run |
| 2.2 `InviteWaitlistEntryHandler` honors `updateIfVersionMatches`, throws conflict, uses domain `revoke()`, no manual REVOKED copy | Checked in tasks.md | DONE, verified by inspection + test run |
| 2.3 Bearer scoping (`InvitationIssued`/`DirectInvitationResent`) | Out for PR1 | NOT-RUN, pending PR2 |
| 2.4 `acceptUrl` redaction / scoping | Out for PR1 | NOT-RUN, pending PR2 |
| 2.5 Accept-attempt throttle | Out for PR1 | NOT-RUN, pending PR2 |
| Phase 1 items 1.1/1.2/1.3 (sign-offs, RFC clauses) | Recorded pending in design.md Open Questions | NOT-RUN, pending owners |
| Phase 3 (hash-only, rejection, concurrency BDD, no-bearer assertions) | Out for PR1 | NOT-RUN, pending PR3 |
| Phase 4 (spec reconciliation, full gates) | Out for PR1 | NOT-RUN, pending PR3 |

## Build / Test / Coverage Evidence (actually executed by verifier)

| Check | Command | Result |
|---|---|---|
| Narrow unit suites (forced rerun) | `./gradlew :server:smp:test --tests "...InvitationObservabilityTest" --tests "...InviteWaitlistEntryHandlerTest" --rerun-tasks -x detekt` | BUILD SUCCESSFUL (4m 22s) |
| `InvitationObservabilityTest` | XML report 2026-09-17T07:09:45Z | 5 tests, 0 failures, 0 errors |
| `InviteWaitlistEntryHandlerTest` | XML report 2026-09-17T07:09:34Z | 10 tests, 0 failures, 0 errors |
| Detekt (forced rerun) | `./gradlew :server:smp:detekt --rerun-tasks` | BUILD SUCCESSFUL, 0 `<error>` in detekt.xml |
| Comment/suppression scan | `git diff \| grep -E "//\|@Suppress\|baseline"` on added lines | Clean: no new comments, no new suppressions |
| Baseline/config scan | `git diff --stat -- "*detekt*" "*baseline*" "*.yml" "*.yaml" "Justfile"` | No baseline, config, or threshold changes |
| `just backend-check` / `just backend-bdd-fast` / `just backend-test-postgres` | Out for PR1 (Phase 4) | NOT-RUN |

RED→GREEN evidence: new tests `records bulk outcomes as aggregate counters without per-value tags` and `surfaces conflict when conditional supersede update reports false`, plus updated contract test `records bulk counter with batch size and outcome counts` (rewritten from per-value-tag lookup to outcome-tag lookup). RED verified by structural reasoning: old `recordBulkInvite` emitted a single counter tagged `requested="5"` etc., so the new `counterFor(outcome)` lookups (`requireNotNull`) fail against old code; old handler discarded the `updateIfVersionMatches` boolean, so `assertThrows<OptimisticLockException>` fails against old code. GREEN proven by the passing runs above. Full RED-run replay was not re-executed (worktree already GREEN); no production code without covering tests was introduced.

## Spec Compliance Matrix (PR1-relevant scenarios only)

| Spec scenario | Implementation evidence | Covering test (passed at runtime) | Verdict |
|---|---|---|---|
| invitations / Aggregate counters only: aggregate outcome counters recorded, no per-batch count/identifier in any tag value | `InvitationObservability.recordBulkInvite` increments 4 `outcome`-tagged counters; signature `(requested, invited, skipped, failed)` unchanged in `InvitationTelemetry` | `records bulk counter with batch size and outcome counts` (updated) + `records bulk outcomes as aggregate counters without per-value tags` (new; asserts no numeric tag values across two batches) | COMPLIANT |
| invitations / CAS conflict surfaces: conditional update false MUST surface conflict, no silent divergence | `InviteWaitlistEntryHandler` checks `updateIfVersionMatches` boolean, throws `OptimisticLockException` on false; `AdminProblemDetailsHandler` maps it to 409 `OPTIMISTIC_LOCK_CONFLICT` (pre-existing, unchanged) | `surfaces conflict when conditional supersede update reports false` (new) | COMPLIANT |
| invitations / CAS-honoring: MUST use `revoke()` instead of hand-built REVOKED copy | Manual `Invitation(...)` copy with `status = REVOKED` deleted; replaced by `existingInvitation.revoke()` | Same CAS test path exercises `revoke()`; `revoke()` domain semantics covered by existing domain tests | COMPLIANT |
| invitations / Token lifecycle acceptance evidence (hash-only, rejected states, concurrent winner, no-bearer) | Out for PR1 | None (pending PR3) | NOT-RUN |
| invitations / Accept-attempt throttle decision | Out for PR1 | None (pending PR2) | NOT-RUN |
| invitations / Sealed handoff or scoped debt | Out for PR1 | None (pending PR2) | NOT-RUN |
| email-notifications / Temporary exception + Persisted payload holds no bearer | Out for PR1 | None (pending PR2) | NOT-RUN |

## Correctness Table

| Property | Evidence | Status |
|---|---|---|
| `recordBulkInvite` signature unchanged | `InvitationTelemetry.kt` untouched (not in diff); same 4 Int params | PASS |
| No per-value tags; low-cardinality `outcome` tag (4 fixed values) | Source inspection + numeric-tag-absence assertion green | PASS |
| `updateIfVersionMatches` boolean honored; false → `OptimisticLockException` → HTTP 409 | Source inspection + new test green + pre-existing 409 mapping confirmed | PASS |
| Domain `revoke()` used; no manual REVOKED copy | 14-line manual construction deleted from diff | PASS |
| No new bearer surface | Diff touches only observability + handler supersede path; no token fields added | PASS |
| Zero-comment policy | Added-line scan clean | PASS |
| No suppressions / baselines / config weakening | `@Suppress("ThrowsCount", "LongMethod")` is pre-existing (not in diff); no baseline/config files touched | PASS |

## Design Coherence Table

| Design decision | Code | Status |
|---|---|---|
| Aggregate counters only; outcome tag low-cardinality; drop numeric tags | Matches exactly | COHERENT |
| Honor boolean; conflict throws; use domain `revoke()` | Matches exactly, including the `if (!ok) throw OptimisticLockException()` shape | COHERENT |
| `recordBulkInvite` args unchanged; `updateIfVersionMatches` untouched | Confirmed; no #660 drift introduced by this slice | COHERENT |
| "Aggregate outcome counters + one bulk counter (size + counts)" | Implementation emits 4 outcome series but no separate single batch-count increment; the updated contract test encodes the 4-series shape as the executable spec | WARNING (see issues) |

## Issues

| Finding | Judge A | Judge B | Severity | Status |
|---|---|---|---|---|
| Design text "plus one bulk counter" vs 4 outcome-only series (no batch-count increment) | ✅ | ✅ | WARNING | Confirmed — reconcile wording or add batch counter in PR2/PR3 Phase 4.1; cardinality goal itself is met |
| Staged `openspec/specs/lead-capture-waitlist/spec.md` 1-line eligibility wording change sits outside PR1's 4-file slice | ✅ | ❌ | INFO | Suspect — appears to belong to another change; confirm ownership before PR1 merge so PR1 stays a clean 4-file slice |
| Phase 1 sign-offs (DALLAY-565/566, observability owners, RFC clauses) still pending per design.md | ✅ | ✅ | WARNING (pre-recorded, not PR1-blocking) | INFO — must clear before PR2/PR3 merge |

No CRITICAL issues. No SUGGESTION issues.

## Final Verdict

**PASS WITH WARNINGS** — strictly for the PR1 slice (tasks 2.1, 2.2). Both fixes match spec deltas and design decisions, are covered by new/updated tests that pass on forced rerun, and Detekt is clean with no suppressions, baselines, or comment violations. Warnings are non-blocking for PR1 but must be resolved in PR2/PR3 (design wording reconciliation; pending owner sign-offs; staged spec-line ownership). Out-of-scope items are Not-run by instruction and MUST NOT be read as failures.
