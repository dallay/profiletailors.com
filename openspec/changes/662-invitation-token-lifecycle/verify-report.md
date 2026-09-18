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

---

# Verification Report: 662-invitation-token-lifecycle — PR2 Slice (Work Unit 2: bearer scoping + accept throttle)

Scope: PR2 ONLY. Tasks 2.3, 2.4, 2.5. Phase 3 (hash-only, rejection, exactly-one-winner BDD/postgres) and Phase 4 full gates (`just backend-check`, `just backend-bdd-fast`, `just backend-test-postgres`) are explicitly OUT and marked Not-run below. Do NOT read this report as a full-change verdict.

## Change and Mode

| Field | Value |
|---|---|
| Change | 662-invitation-token-lifecycle (issue #662) |
| Slice | PR2 / Work Unit 2: bearer scoping (2.3, 2.4) + accept throttle (2.5) |
| Mode | openspec |
| Date | 2026-09-17 |
| Verifier | sdd-verify sub-agent |
| Branch | `feat/662-invitation-bearer-throttle` (synced with main; PR1 `a1384c32` merged) |

## Completeness Table (PR2 scope only)

| Task | Claim | Status |
|---|---|---|
| 2.3 `InvitationIssued` / `DirectInvitationResent` interim debt, no new fields | Checked in tasks.md | DONE, verified by inspection (both files absent from diff; `rawToken` fields pre-existing only) + sign-off recorded in design.md |
| 2.4 `InvitationEmail.toPayload()` scoped `acceptUrl` only; transient render path | Checked in tasks.md | DONE, verified by inspection (prod files untouched) + regression tests green |
| 2.5 Per-key+IP accept throttle at transport edge, coordinator pure | Checked in tasks.md | DONE, verified by inspection + tests green |
| DALLAY-565 sign-off on interim `rawToken`/`acceptUrl` debt | Recorded in design.md Open Questions (owner-approved 2026-09-17) | DONE (recorded; owner approval as stated) |
| Phase 3 (hash-only, rejection, concurrent-winner BDD, postgres) | Out for PR2 | NOT-RUN, pending PR3 |
| Phase 4 (spec reconciliation, full gates) | Out for PR2 | NOT-RUN, pending PR3 |

## Build / Test / Coverage Evidence (actually executed by verifier)

| Check | Command | Result |
|---|---|---|
| Narrow SMP suites (forced rerun) | `./gradlew :server:smp:test --tests "...InvitationAcceptanceControllerTest" --tests "...AdminProblemDetailsHandlerTest" --tests "...SendInvitationEmailConsumerTest" --tests "...ResendWaitlistInvitationHandlerTest" --rerun-tasks -x detekt` | BUILD SUCCESSFUL (5m 09s) |
| `InvitationAcceptanceControllerTest` | XML report at run time | 7 tests, 0 failures, 0 errors |
| `AdminProblemDetailsHandlerTest` | XML report at run time | 15 tests, 0 failures, 0 errors |
| `SendInvitationEmailConsumerTest` | XML report at run time | 15 tests, 0 failures, 0 errors |
| `ResendWaitlistInvitationHandlerTest` | XML report at run time (covers `resendLimitExceeded` factory rename) | 6 tests, 0 failures, 0 errors |
| Shared notifications suite (forced rerun) | `./gradlew :shared:notifications:test --tests "...InvitationEmailTest" --rerun-tasks -x detekt` | BUILD SUCCESSFUL (25s); 17 tests, 0 failures, 0 errors |
| Detekt SMP (forced rerun) | `./gradlew :server:smp:detekt --rerun-tasks` | BUILD SUCCESSFUL, 0 `<error>` in detekt.xml |
| Detekt shared-notifications (forced rerun) | `./gradlew :shared:notifications:detekt --rerun-tasks` | BUILD SUCCESSFUL |
| Spotless SMP + shared-notifications | `:server:smp:spotlessCheck`, `:shared:notifications:spotlessCheck --rerun-tasks` | Both BUILD SUCCESSFUL |
| Comment/suppression scan | Added-line scan for `//` comments, block comments, `@Suppress`, baselines | Clean (single `//` hit is the `https://` inside a test string literal, not a comment) |
| Baseline/config scan | `git diff` name check for `*detekt*`, `*baseline*`, `*.yml`, `*.yaml`, `Justfile` | No baseline, config, or threshold changes (only openspec docs + Kotlin) |
| `just backend-check` / `just backend-bdd-fast` / `just backend-test-postgres` | Out for PR2 (Phase 4) | NOT-RUN |

Total PR2 narrow evidence: 60 tests, 0 failures, 0 errors. Only pre-existing, unrelated compiler deprecation warnings observed (`MediaCasHandlersTest` legacy-upload deprecations; not introduced by PR2).

## Spec Compliance Matrix (PR2-relevant scenarios only)

| Spec scenario | Implementation evidence | Covering test (passed at runtime) | Verdict |
|---|---|---|---|
| invitations / Sealed handoff or scoped debt, never silent | `InvitationIssued.kt` / `DirectInvitationResent.kt` untouched by diff (no new bearer fields; existing `rawToken` only); interim in-memory handoff recorded as owner-signed debt in design.md with removal tracked DALLAY-566 | Debt-scope documented; no-code-change claim verified by empty `git diff` on both files | COMPLIANT |
| email-notifications / Temporary exception remains visible | Handler-to-event-to-consumer `rawToken` path unchanged; no new durable field, no HTTP/audit/log/metric exposure added (diff touches no producer, event, or audit code) | `SendInvitationEmailConsumerTest` — dispatched email renders accept URL transiently while persisted payload carries no raw token (new; asserts `rendered.text` contains template URL, payload has no `rawToken` key/value) | COMPLIANT |
| email-notifications / Persisted payload holds no bearer | `InvitationEmail.toPayload()` unchanged: keys locked to `email, workspaceName, target, acceptUrl, locale`; `rawToken` never a separate key; only scoped `acceptUrl` (equal to template-built URL) persists per sign-off | `InvitationEmailTest` — key-set lock test + no-raw-token-outside-`acceptUrl` test (both new); consumer test asserts `payload["acceptUrl"] == acceptUrl` | COMPLIANT |
| invitations / Throttle decision recorded | Design Open Questions records chosen bounds: 10 attempts / 10 min per candidateKey+IP at `InvitationAcceptanceController`, coordinator pure, 429 `INVITATION_RATE_LIMIT_EXCEEDED` with static detail | `InvitationAcceptanceControllerTest` — 429 safe-code denial without handler call (new); throttle-key binds candidate key, never raw token (new) | COMPLIANT |

## Correctness Table

| Property | Evidence | Status |
|---|---|---|
| No new bearer fields on events | `git diff` empty for `InvitationIssued.kt`, `DirectInvitationResent.kt`, `InvitationEmail.kt`, `SendInvitationEmailConsumer.kt` | PASS |
| `toPayload()` key set locked; `rawToken` excluded as separate key | Source inspection + key-set test green | PASS |
| Persisted `acceptUrl` equals template-built URL; render is transient via `render()` | Consumer dispatches `email.render()` while persisting `email.toPayload()` (pre-existing flow, unchanged); new test asserts both sides | PASS |
| Throttle at transport edge: 10 attempts / 10 min per `candidateKey + IP` | `ACCEPT_ATTEMPT_MAX = 10`, `ACCEPT_ATTEMPT_WINDOW = 10 min`, key `invitation-accept:<candidateKey>:<ip>` in `InvitationAcceptanceController` | PASS (bounds by inspection; see WARNING) |
| Throttle key binds SHA-256 candidate hash, never raw token | `invitationTokenCandidateKey.candidateKey(token)` (`BCryptTokenHasher` SHA-256 hex); test asserts key contains candidate hash and not raw token | PASS |
| 429 `INVITATION_RATE_LIMIT_EXCEEDED`, no key material in body | `acceptAttemptThrottled()` static detail `"Invitation accept rate limit exceeded. Try again later."`; `AdminProblemDetailsHandler` maps to 429 + code; test asserts exact code/status/detail | PASS |
| Coordinator untouched / pure | `git diff` empty for `InvitationActivationCoordinator.kt`; throttle enforced before `handle()` call; denied path verified `handle` never invoked | PASS |
| Resend path preserved through exception-shape refactor | `ResendWaitlistInvitationHandler` one-line factory rename; `ResendWaitlistInvitationHandlerTest` 6/6 green | PASS |
| Production wiring follows proven pattern | Single `RateLimit` bean (`InMemoryRateLimit`), `BCryptTokenHasher` already injected as `InvitationTokenCandidateKey` elsewhere, `Clock` bean exists; no new configuration files | PASS |
| Zero-comment policy | Added-line comment scan clean | PASS |
| No suppressions / baselines / config weakening | Suppression + baseline/config scans clean | PASS |

## Design Coherence Table

| Design decision | Code | Status |
|---|---|---|
| Interim in-memory handoff as scoped debt; sealed delivery owned by DALLAY-566 | Events unchanged; sign-off + removal tracking recorded in design.md | COHERENT |
| Canonical target template-params + delivery key; interim token-bearing `acceptUrl` only with sign-off | Payload key set locked by tests; `acceptUrl` equality to template URL asserted; sign-off recorded | COHERENT |
| Bounded per-key+IP attempt throttle at transport edge; coordinator stays pure | Controller admits via `RateLimit` before handler; coordinator file untouched | COHERENT |
| Throttle reuses `InvitationRateLimitExceededException` shape at accept edge | Private constructor + `resendLimitExceeded` / `acceptAttemptThrottled` factories; 429 mapping reused | COHERENT |

## Issues

| Finding | Judge A | Judge B | Severity | Status |
|---|---|---|---|---|
| Throttle bounds (10 attempts / 10 min) proven by source inspection only — throttle tests lock deny→429 behavior and key binding but do not capture window/limit args, so a silent constant change would stay green | ✅ | ✅ | WARNING | Confirmed — suggest capturing `window` + `maxRequests` in the throttle-key test in PR3; bounds themselves are correct in code |
| Production wiring of new controller deps (`RateLimit`, `InvitationTokenCandidateKey`, `Clock`) has no full-context boot test in the narrow scope; risk is low (single `RateLimit` bean, pre-existing interface-injection pattern, `Clock` bean present) | ✅ | ❌ | SUGGESTION | Suspect — consider a slice/context test or rely on PR3 gates; not PR2-blocking |
| Staged `openspec/specs/lead-capture-waitlist/spec.md` 1-line change still present (carried from PR1) and outside the PR2 slice | ✅ | ❌ | INFO | Suspect — confirm ownership before merge so PR2 stays a clean slice |
| Phase 3 BDD/postgres exactly-one-winner + Phase 4 full gates not run | ✅ | ✅ | INFO (explicitly out of scope) | NOT-RUN — must be evidenced in PR3; do NOT read as failure |

No CRITICAL issues.

## Final Verdict

**PASS WITH WARNINGS** — strictly for the PR2 slice (tasks 2.3, 2.4, 2.5). Bearer scoping holds (no new bearer surface; payload contract locked by tests; transient render covered), the per-key+IP throttle is enforced at the transport edge with safe 429 semantics and a raw-token-free key, and the coordinator is untouched. 60/60 narrow tests green on forced rerun; Detekt (both modules) and Spotless clean; no suppressions, baselines, or comment violations. The single WARNING (bounds not test-locked) and the SUGGESTION (no full-context wiring test) are non-blocking for PR2 but should be addressed in PR3. Out-of-scope items are Not-run by instruction and MUST NOT be read as failures.
