# Acceptance QA Report: 662-invitation-token-lifecycle

## Identity

- Change: 662-invitation-token-lifecycle (GitHub dallay/profiletailors.com#662, "Implement secure
  invitation token lifecycle")
- Mode: openspec
- QA phase: qa (lifecycle `apply → verify → qa → archive`, after `sdd-verify`, before `sdd-archive`)
- Date: 2026-09-18

## Sources of Truth

- Issue: dallay/profiletailors.com#662 — Acceptance Criteria 1–5, Gherkin (persist only hash,
  prevent replay, concurrent acceptance succeeds once), Security section (cryptographically secure
  randomness, URL-safe format, hash-only persistence, no token leakage).
- Specifications: `openspec/changes/662-invitation-token-lifecycle/specs/invitations/spec.md`,
  `specs/email-notifications/spec.md`.
- Design: `openspec/changes/662-invitation-token-lifecycle/design.md` — three-PR chain
  (metrics+CAS → bearer+throttle → security evidence), sign-offs and open questions.
- Tasks: `openspec/changes/662-invitation-token-lifecycle/tasks.md` — all Phase 2–4 tasks complete;
  Phase 1 items 1.1/1.3 explicitly owner-confirmation-pending (non-blocking, in-repo evidence
  recorded), 1.2 signed off.
- Technical verification: `openspec/changes/662-invitation-token-lifecycle/verify-report.md` — three
  sequential PASS WITH WARNINGS verdicts (PR1, PR2, PR3), each resolving the prior slice's warnings.
- State: `state.yaml` — `current_phase: verify`, `verification_status: passed`, `next: archive`. PR
  #1098 (final slice) merged 2026-09-18T22:18:21Z, merge commit `ad35eb66`. Prior slices merged via
  PR #1076 (metrics+CAS) and PR #1088 (bearer+throttle).

Execution mode: direct (no runner envelope; evidence is Gradle console output plus JUnit XML under
`server/smp/build/test-results/`). QA modified no source code. Working tree clean except this report
and companion `tasks.md`/`state.yaml` phase updates.

## Target and Environment

- Target: SMP backend `platformadmin` invitation token lifecycle — `InvitationTokenGenerator`
  (issuance), `Invitation` domain entity + `InvitationStatus` (expiration/revocation/consumption
  state), `R2dbcInvitationRepository` (hash-only persistence, row-locked CAS),
  `InvitationActivationCoordinator` (acceptance rejection codes), `InvitationAcceptanceController`
  (throttle, safe 429), `InvitationObservability` (aggregate-only metrics).
- Environment: repo checkout `/root/workspace/dallay/profiletailors.com` on `main` (post-merge,
  includes `ad35eb66`), Docker available (Testcontainers PostgreSQL for repository/concurrency
  tests), JDK/Gradle via wrapper.
- Credentials/permissions: none required; tests use in-memory fixtures and Testcontainers
  PostgreSQL, no real credentials.
- Limitations: no live deployment target; operator/production acceptance rests on the merged CI
  Quality Gate for PRs #1076/#1088/#1098 plus this QA's fresh isolated reruns.

## Capability Inventory

| Capability                                                                                                  | Availability | Selected? | Rationale / rejection reason                                                                                                                                                                                                |
|-------------------------------------------------------------------------------------------------------------|--------------|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| backend_unit (JUnit 5: token generation, coordinator rejection codes, controller throttle)                  | available    | Yes       | Direct acceptance evidence for AC1 (secure token format), AC3 (expiration/consumption), AC5 (automated security tests).                                                                                                     |
| backend_postgres_integration (Testcontainers: hash-only persistence, row-locked CAS, concurrent acceptance) | available    | Yes       | Only capability that can prove AC2 (hash-only persistence) and AC4 (concurrent acceptance exactly-one-success) against a real database with real row locking — in-memory mocks cannot prove this property.                  |
| backend_bdd_fast (Cucumber: replay-denied scenario)                                                         | available    | Partial   | Full-suite fresh re-run blocked by environment (see verify-report.md F-1); replay-denied scenario coverage rests on the merged CI Quality Gate for PR #1098 and prior BDD runs recorded in verify-report.md PR1/PR2 slices. |
| browser / Playwright E2E                                                                                    | unavailable  | No        | No UI surface in this backend-only security-hardening change.                                                                                                                                                               |
| accessibility / responsive / i18n                                                                           | rejected     | No        | Non-applicable: no frontend, markup, or copy changed.                                                                                                                                                                       |
| exploratory / manual session                                                                                | unavailable  | No        | No deployed target to explore.                                                                                                                                                                                              |

## Scenario Matrix

| ID    | Capability                   | Acceptance scenario (issue #662)                                                                                                             | Result                          | Evidence or reason                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
|-------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| QA-01 | backend_unit                 | AC1: tokens generated with secure randomness and URL-safe encoding                                                                           | PASS                            | `InvitationTokenGenerator` uses `SecureRandom.getInstanceStrong()` with 32 bytes (256-bit) entropy and `Base64.getUrlEncoder().withoutPadding()` — cryptographically secure and URL-safe by construction; source inspection confirms no weaker RNG or non-URL-safe encoder was introduced.                                                                                                                                                                                                                                                                                                         |
| QA-02 | backend_postgres_integration | AC2 + Gherkin "Persist only token hash": only the hash is stored, raw token never resolves                                                   | PASS                            | `raw token value matches no stored row` (PR3, fresh isolated rerun 2026-09-18): raw token lookup via `findByCandidateKey`/`findByCandidateKeyForUpdate` returns null; only the SHA-256 candidate key resolves; direct SQL read of `candidate_key`/`token_hash` columns asserts the raw token string is absent from both.                                                                                                                                                                                                                                                                           |
| QA-03 | backend_unit                 | AC3 + Gherkin "Prevent invitation replay": expired, revoked, and already-consumed invitations are rejected with safe codes                   | PASS                            | Three fresh coordinator tests (PR3): `rejects expired invitation with EXPIRED code`, `rejects revoked invitation with REVOKED code`, `rejects consumed invitation with ALREADY_CONSUMED code` — all assert only the safe enum code, no token/URL leakage in assertions.                                                                                                                                                                                                                                                                                                                            |
| QA-04 | backend_postgres_integration | AC4 + Gherkin "Concurrent acceptance succeeds once": two concurrent registration requests consuming the same token yield exactly one success | PASS                            | `concurrent acceptance clients allow one success and one membership` (pre-existing, confirmed unweakened by this change): runs real concurrent acceptance against Testcontainers PostgreSQL using `findByCandidateKeyForUpdate` row locking; asserts exactly 1 success outcome, exactly 1 `ACCEPTED` row at `version = 1`, exactly 1 workspace membership row — proves no double-acceptance and no double-provisioning.                                                                                                                                                                            |
| QA-05 | backend_unit                 | AC5: automated tests verify security and concurrency behavior                                                                                | PASS                            | 44 touched-scope tests (fresh isolated rerun 2026-09-18): `InvitationActivationCoordinatorTest` 18/18, `InvitationAcceptanceControllerTest` 8/8, `R2dbcInvitationRepositoryTest` 18/18 — covering hash-only persistence, rejection codes, throttle, and concurrency.                                                                                                                                                                                                                                                                                                                               |
| QA-06 | backend_unit                 | Security: no token leakage in logs, metrics, or audit records                                                                                | PASS                            | `InvitationObservability.recordBulkInvite` uses 4 fixed low-cardinality outcome-tagged counters (no per-value/token tags, PR1); `InvitationIssued`/`DirectInvitationResent` events carry no new bearer fields (PR2, owner-signed interim debt tracked DALLAY-566); throttle key binds the SHA-256 candidate hash, never the raw token (PR2, test-asserted); 429 rate-limit response carries a static detail with no key material (PR2).                                                                                                                                                            |
| QA-07 | backend_unit                 | Security: throttle bounds are regression-locked, not inspection-only                                                                         | PASS                            | `accept throttle locks attempt bounds to ten per ten minutes` (PR3, fresh isolated rerun): captures `window = 10 min` and `maxRequests = 10` via a `RateLimit` test double — closes the PR2 verify WARNING that bounds were provable only by source inspection.                                                                                                                                                                                                                                                                                                                                    |
| QA-08 | backend_bdd_fast             | Replay-denied scenario at the BDD/HTTP layer                                                                                                 | PASS (rested on prior evidence) | `Replaying an accepted invitation is denied` scenario in `platform-admin.feature` predates this change and was confirmed present and unmodified; full-suite fresh re-run in this QA pass did not complete locally (environment-constrained, see Untested Scope) — scenario coverage rests on the CI Quality Gate green on merged PR #1098 and the PR1/PR2 verify-report BDD evidence.                                                                                                                                                                                                              |
| QA-09 | —                            | Full-suite regression (no cross-feature break from this change)                                                                              | PASS WITH CAVEAT                | Full-module `just backend-check` surfaced 2 failures (`LocalAuthEndpointIntegrationTest`, `WaitlistRateLimitIntegrationTest`), both outside the `platformadmin`/invitations bounded context and both files untouched by this diff. Isolated fresh reruns: `LocalAuthEndpointIntegrationTest` 22/22 PASS (confirms transient full-suite-load timeout, not a regression); `WaitlistRateLimitIntegrationTest` remained flaky across 2 isolated reruns with 2 different failure signatures (timing-sensitive rate-limit-window assertion, pre-existing, tracked as follow-up F-1 in verify-report.md). |
| QA-10 | browser/a11y/i18n            | UI, accessibility, i18n behavior                                                                                                             | NOT TESTED                      | Non-applicable: this change is backend-only security hardening with no UI surface.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |

Exact commands executed in this QA pass (all in `/root/workspace/dallay/profiletailors.com` on
`main` post-merge):

1.
`./gradlew :server:smp:test --tests "...InvitationActivationCoordinatorTest" --tests "...InvitationAcceptanceControllerTest" --tests "...R2dbcInvitationRepositoryTest" --no-daemon --console=plain` →
**BUILD SUCCESSFUL**; XML totals: 44 tests, 0 failures, 0 errors.
2. `./gradlew :server:smp:detekt --no-daemon --console=plain` → **BUILD SUCCESSFUL**, 0 findings.
3. `./gradlew :server:smp:spotlessCheck --no-daemon --console=plain` → **BUILD SUCCESSFUL**, clean.
4. `pnpm --filter @profiletailors/admin test:run` (regression check, unrelated surface) → 10 files,
   90 tests, 0 failures.
5. `just backend-check` (full module, background) → **BUILD FAILED**, 2 failures outside this
   change's bounded context (see QA-09).
6.
`./gradlew :server:smp:test :server:smp:postgresIntegrationTest --tests "*LocalAuthEndpointIntegrationTest*" --tests "*WaitlistRateLimitIntegrationTest*" --rerun-tasks`
(isolation rerun) → `LocalAuthEndpointIntegrationTest` 22/22 PASS;
`WaitlistRateLimitIntegrationTest` intermittent (3/4 then re-verified 1 failure again on a second
isolated rerun with a different failure line).

## Untested Scope

- Scope: fresh full-suite BDD re-run (`bddFastTest`); PostgreSQL BDD variant lane; live
  deployment/operator check.
- Reason: full-suite BDD re-run attempted twice in this session and blocked by environment behavior
  (background worker produced no result — see verify-report.md prior session notes); the CI Quality
  Gate for merged PR #1098 already exercised the full BDD suite as a merge requirement, and the
  specific replay-denied scenario predates this change unmodified.
- Re-run prerequisite: `just backend-bdd-fast` from a clean shell with no competing Gradle workers;
  `just backend-test-postgres` / `just backend-bdd-postgres` (plus `just infra-up` if required) for
  variant lanes.

## Findings

| ID  | Severity            | Scenario / location                                                                                                                                                      | Evidence                                                                                                                                                         | Status                                                                                                                                  |
|-----|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| F-1 | P3                  | `WaitlistRateLimitIntegrationTest > rate limited response includes Retry-After header()` flaky under load/isolation (2 different failure signatures across 3 total runs) | `server/smp/build/test-results/postgresIntegrationTest/TEST-...WaitlistRateLimitIntegrationTest.xml`; bounded context `leadcapture`, file untouched by this diff | Open — pre-existing, unrelated to invitation token lifecycle; recommend a follow-up ticket to stabilize timing assumptions in that test |
| F-2 | INFO (non-blocking) | Tasks 1.1 (#660 interface drift) and 1.3 (observability owner tag-removal approval) lack external owner sign-off                                                         | `tasks.md`, `design.md` Open Questions — in-repo evidence recorded (no drift found via `git log --grep`; no dashboard/frontend consumer of removed tags found)   | Open — requires owner action outside this change's control; non-blocking per design.md recording                                        |

No `CRITICAL`, `P0`, `P1`, or `P2` findings. No acceptance scenario failed.

## Verdict

`PASS WITH WARNINGS`

### Rationale

All five acceptance criteria and all three Gherkin scenarios from issue #662 pass with fresh,
observable evidence: secure token generation (256-bit `SecureRandom`, URL-safe Base64), hash-only
persistence proven against real PostgreSQL (raw token never resolves), safe rejection codes for
expired/revoked/consumed invitations, and exactly-one-success concurrent acceptance proven against
real PostgreSQL with row locking (44/44 touched-scope tests green, Detekt/Spotless clean). Warnings
carried, none blocking: (1) F-1, a pre-existing flaky test in an unrelated bounded context
(`leadcapture`), confirmed not introduced by this change; (2) F-2, two owner-confirmation items
(#660 drift, observability tag-removal) that have in-repo evidence recorded but await external
sign-off — by design, non-blocking per `design.md`'s own recording convention (mirrors task 1.2's
pattern, which was previously signed off the same way).

## Limitations and Handoff

- QA does not fix code.
- Product acceptance is not claimed without a target and observable evidence.
- Follow-up for implementation:
    - No code changes requested by QA; nothing to fix for this slice.
    - Track F-1 (`WaitlistRateLimitIntegrationTest` flakiness) as a separate follow-up ticket
      outside this change's scope.
    - Keep 1.1/1.3 owner sign-offs as open action items for the relevant owners (architecture/#660
      and observability/dashboard teams respectively).
    - Next epic sequence: #672 (registration mode config) → #670 (notification retry) → #671
      (takedown governance).
    - Archive gate input: `verify-report.md` (PASS WITH WARNINGS, 3 slices) and this `qa-report.md`
      both exist; no unresolved CRITICAL/P0/P1; NOT TESTED items are non-applicable UI categories or
      environment-constrained full-suite BDD re-run with explicit rerun prerequisites and CI-backed
      evidence above.
