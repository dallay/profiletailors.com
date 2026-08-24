# Verification Report

## Overview

**Change**: reusable-lead-capture-waitlist
**Mode**: openspec
**Verification date**: 2026-07-18
**Verification scope**: Full active change, with fresh focus on Phase 7 marketing integration and
Phase 9 documentation updates completed after prior backend verification. Phase 9.4 remains
intentionally unchecked because canonical spec sync belongs to `sdd-archive`.
**Verdict**: PASS WITH WARNINGS

## Executive Summary

The reusable lead-capture waitlist change is behaviorally compliant with the OpenSpec deltas,
design, and task checklist for the implemented scope. The marketing site now renders the reusable
`WaitlistForm.astro`, builds the documented backend payload, submits to
`POST /api/waitlists/{waitlistKey}/entries`, blocks invalid input client-side, handles 202 and 429
responses, and is covered by passing Vitest and Playwright evidence.

The documentation updates are present: ADR-0011 is Accepted, architecture indexes reference ADRs,
shared module dependencies include the lead-capture modules, and C4 container/component
documentation includes the Lead Capture bounded context and rate-limit caveat. The only incomplete
task is Phase 9.4, correctly deferred to `sdd-archive`; existing DALLAY-512/DALLAY-513
production-safety warnings remain mitigated by WAITLIST rate limiting being default-off in SMP.

## Completeness

| Metric                               | Value |
|--------------------------------------|------:|
| Total tasks                          |    48 |
| Complete tasks                       |    47 |
| Incomplete tasks                     |     1 |
| Core implementation tasks incomplete |     0 |
| Archive-only tasks incomplete        |     1 |

### Incomplete Tasks

| Task                                                                                        | Severity | Verification judgment                                                                       |
|---------------------------------------------------------------------------------------------|----------|---------------------------------------------------------------------------------------------|
| 9.4 — After `sdd-archive`, add the canonical `openspec/specs/lead-capture-waitlist/spec.md` | WARNING  | Correctly deferred. Canonical spec sync is archive-phase work, not an apply/verify blocker. |

## Build and Test Evidence

| Command                                                                                                                       | Result  | Evidence                                                                                                                                                                                                                                                                                                |
|-------------------------------------------------------------------------------------------------------------------------------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `git diff --check`                                                                                                            | PASS    | Fresh continuation evidence from orchestrator and rerun in this verify phase: no whitespace output.                                                                                                                                                                                                     |
| `pnpm --filter marketing lint && pnpm --filter marketing test && pnpm --filter marketing build`                               | PASS    | Fresh continuation evidence: Biome checked 45 files, Vitest passed 6 files / 43 tests, Astro built 10 pages.                                                                                                                                                                                            |
| `pnpm --filter marketing exec vitest run src/components/waitlist-form.test.ts src/components/waitlist-form-validator.test.ts` | PASS    | Rerun during this verification: 2 files passed / 14 tests passed. Confirms payload shape, metadata whitelist filtering, marketing consent behavior, and email validation.                                                                                                                               |
| `pnpm --filter marketing exec playwright test --project=chromium --grep "Waitlist Form"`                                      | PASS    | Fresh continuation evidence: 7 Chromium tests passed. Covers hero form rendering, 202 success path, empty/invalid email blocking, missing early-access consent blocking, 429 friendly error, and configured waitlist key URL.                                                                           |
| Prior backend focused and broad evidence from `apply-progress.md`                                                             | PASS    | Full SMP backend suite previously passed (`SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test --rerun-tasks`), focused controller/persistence/rate-limit suites passed, and shared rate-limit security remediation suites passed. No backend files changed in this continuation. |
| Coverage                                                                                                                      | Not run | `openspec/config.yaml` sets `coverage_threshold: 0`; no meaningful threshold is configured. Focused runtime behavior evidence exists.                                                                                                                                                                   |

Broad `./gradlew build` was not rerun in this continuation because the latest working-tree changes
are frontend integration/docs only, fresh focused frontend verification is green, and the user
explicitly requested avoiding broad expensive CI unless strictly required. Prior backend
verification remains applicable because no backend source files changed after it.

## Spec Compliance Matrix

| Requirement                  | Scenario                                             | Test / Evidence                                                                                            | Result    |
|------------------------------|------------------------------------------------------|------------------------------------------------------------------------------------------------------------|-----------|
| EmailAddress Value Object    | Valid email preserved                                | `EmailAddressTest`; prior backend/shared test evidence in `apply-progress.md`                              | COMPLIANT |
| EmailAddress Value Object    | Blank email rejected                                 | `EmailAddressTest`; prior backend/shared test evidence                                                     | COMPLIANT |
| EmailAddress Value Object    | Invalid email rejected                               | `EmailAddressTest`; prior backend/shared test evidence                                                     | COMPLIANT |
| NormalizedEmail Value Object | Normalization is conservative                        | `NormalizedEmailTest`; implementation trims/lowercases only                                                | COMPLIANT |
| NormalizedEmail Value Object | No Gmail canonicalization                            | `NormalizedEmailTest`; implementation preserves dots/plus after lowercase                                  | COMPLIANT |
| CaptureSource Value Object   | Valid source accepted                                | Shared VO tests; source used as capture origin, not waitlist key                                           | COMPLIANT |
| CaptureSource Value Object   | Blank source rejected                                | Shared VO tests and controller invalid-source mapping                                                      | COMPLIANT |
| CaptureLocale Value Object   | Valid locale accepted                                | Shared VO tests; marketing passes `locale` from `Hero`/`_HomePage`                                         | COMPLIANT |
| LeadMetadata Value Object    | Whitelisted keys accepted                            | `LeadMetadataTest` plus `waitlist-form.test.ts` metadata contract                                          | COMPLIANT |
| LeadMetadata Value Object    | Unlisted keys rejected/ignored                       | `LeadMetadataTest` plus `waitlist-form.test.ts` drops `random_internal_id`                                 | COMPLIANT |
| Common framework isolation   | No Spring imports                                    | ArchUnit/module-boundary tests from Phase 1/8 evidence                                                     | COMPLIANT |
| Common framework isolation   | No R2DBC imports                                     | ArchUnit/module-boundary tests from Phase 1/8 evidence                                                     | COMPLIANT |
| Common framework isolation   | No server package dependency                         | ArchUnit/module-boundary tests from Phase 1/8 evidence                                                     | COMPLIANT |
| Waitlist Aggregate           | Active waitlist accepts entries                      | `JoinWaitlistHandlerTest` / controller tests / Postgres seed active waitlist                               | COMPLIANT |
| Waitlist Aggregate           | Paused waitlist rejects entries                      | `WaitlistControllerTest` returns `409 waitlist_closed`; handler throws closed exception                    | COMPLIANT |
| Waitlist Aggregate           | Unknown waitlist key returns not found               | `WaitlistControllerTest` returns `404 waitlist_not_found`                                                  | COMPLIANT |
| WaitlistEntry Entity         | New entry starts pending                             | `WaitlistEntryTest` and repository round-trip tests                                                        | COMPLIANT |
| WaitlistConsent Value Object | Early access consent required                        | `WaitlistControllerTest`, `JoinWaitlistCommandTest`, and Playwright missing-consent block                  | COMPLIANT |
| WaitlistConsent Value Object | Marketing consent defaults false / not implicit      | `WaitlistConsent` default, `waitlist-form.test.ts`, and E2E success payload asserts `marketing: false`     | COMPLIANT |
| Idempotent Join              | New join returns accepted                            | `WaitlistControllerTest` and Playwright 202 success path                                                   | COMPLIANT |
| Idempotent Join              | Duplicate join returns accepted                      | `WaitlistControllerTest`, `JoinWaitlistHandlerTest`, repository duplicate save test                        | COMPLIANT |
| Idempotent Join              | Internal distinction is not public                   | `WaitlistControllerTest` asserts same 202 body and `$.duplicate` absent                                    | COMPLIANT |
| Email dedupe per waitlist    | Same email different waitlists                       | `R2dbcWaitlistRepositoriesPostgresTest` cross-waitlist reuse; rate-limit cross-waitlist isolation evidence | COMPLIANT |
| Email dedupe per waitlist    | Same email same waitlist is idempotent               | `R2dbcWaitlistRepositoriesPostgresTest` and controller duplicate tests                                     | COMPLIANT |
| Repository Ports             | Port is framework-free                               | Phase 1/8 module-boundary tests and source inspection of shared ports                                      | COMPLIANT |
| Waitlist framework isolation | No Spring imports in waitlist                        | ArchUnit/module-boundary tests                                                                             | COMPLIANT |
| Waitlist framework isolation | No R2DBC imports in waitlist                         | ArchUnit/module-boundary tests                                                                             | COMPLIANT |
| Waitlist framework isolation | No server package dependency in waitlist             | ArchUnit/module-boundary tests                                                                             | COMPLIANT |
| Marketing integration        | Form sends documented payload shape                  | `waitlist-form.test.ts` and `waitlist-form.spec.ts` intercepted request body                               | COMPLIANT |
| Marketing integration        | Successful backend 202 shows success and resets form | `tests/e2e/waitlist-form.spec.ts` success test                                                             | COMPLIANT |
| Marketing integration        | Empty/invalid email blocks submission                | `waitlist-form-validator.test.ts` and Playwright empty/invalid-email tests                                 | COMPLIANT |
| Marketing integration        | Missing early-access consent blocks submission       | Playwright missing-consent test                                                                            | COMPLIANT |
| Marketing integration        | 429 displays friendly retry message                  | Playwright 429 test                                                                                        | COMPLIANT |
| Marketing integration        | Configured waitlist key is used in endpoint URL      | Playwright configured-key test asserts `/api/waitlists/profile-tailors-launch/entries`                     | COMPLIANT |

**Compliance summary**: 36/36 mapped scenarios compliant with passing runtime test evidence or prior
passing backend/shared evidence recorded in `apply-progress.md`.

## Correctness (Static — Structural Evidence)

| Requirement / area       | Status      | Notes                                                                                                                                                                               |
|--------------------------|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Frontend payload shape   | Implemented | `buildWaitlistPayload` emits `email`, `source`, `formId`, `locale`, `consent`, optional whitelisted `metadata`; email is trimmed before submission.                                 |
| Form submission behavior | Implemented | `WaitlistForm.astro` submits JSON to `/api/waitlists/${waitlistKey}/entries` with `content-type: application/json`; success, 429, generic error, and reset states are implemented.  |
| Validation behavior      | Implemented | `novalidate` plus JavaScript validation blocks empty/invalid email and missing early-access consent before network submission.                                                      |
| Consent semantics        | Implemented | Early access is required; marketing consent is explicit and defaults false unless user checks the marketing box.                                                                    |
| Hero integration         | Implemented | `Hero.astro` renders `WaitlistForm` and passes locale from `_HomePage.astro`; `EarlyAccessStatus` remains in `noscript` fallback only.                                              |
| Documentation updates    | Implemented | ADR-0011 status is Accepted; shared dependency graph includes lead-capture modules; C4 docs include Lead Capture; ADR index includes ADR-0011; architecture README links ADR index. |
| Archive spec sync        | Deferred    | Task 9.4 remains open by design and should be completed by `sdd-archive`.                                                                                                           |

## Coherence (Design)

| Design decision                                 | Followed? | Notes                                                                                                                                                         |
|-------------------------------------------------|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Two shared lead-capture modules, framework-free | Yes       | Prior shared/backend verification and module-boundary tests confirm `common` and `waitlist` isolation.                                                        |
| Consent lives in waitlist, not common           | Yes       | `WaitlistConsent` is in `shared/lead-capture/waitlist/domain`; no consent type was added to common.                                                           |
| Idempotent public response                      | Yes       | Controller returns uniform `202 accepted`; duplicate distinction is internal only.                                                                            |
| Conservative email normalization                | Yes       | `NormalizedEmail.from` does trim + lowercase only; frontend trims before payload.                                                                             |
| Per-waitlist deduplication                      | Yes       | Repository and Liquibase tests cover `(waitlist_id, normalized_email)` semantics and cross-waitlist reuse.                                                    |
| Metadata whitelist                              | Yes       | `LeadMetadata` and frontend builder restrict metadata to approved keys.                                                                                       |
| Marketing form posts to backend endpoint        | Yes       | `WaitlistForm.astro` posts to `/api/waitlists/{waitlistKey}/entries`; Playwright route interception proves URL and payload.                                   |
| Documentation reflects implemented architecture | Yes       | ADR, shared dependencies, C4 container/component docs, and ADR indexes are updated.                                                                           |
| Rate limiting safe production posture           | Warning   | WAITLIST limiter remains default-off in SMP pending DALLAY-512 and DALLAY-513. This matches the mitigation strategy but remains a production enablement risk. |

## Issues Found

### CRITICAL

None for archive readiness. The only open checklist item is explicitly archive-owned and the
production rate-limit blockers are mitigated by the limiter being default-off.

### WARNING

1. **DALLAY-512 — distributed bucket backend still required before enabling WAITLIST rate limiting
   in multi-replica environments.** Current mitigation: `application.rate-limit.waitlist.enabled`
   defaults to `false` in SMP, so operators must explicitly opt in.
2. **DALLAY-513 — trusted-proxy / `ForwardedHeaderFilter` allowlist still required before enabling
   WAITLIST rate limiting behind shared ingress.** Current mitigation: client-supplied
   `X-Forwarded-For` is no longer trusted, and the limiter remains default-off.
3. **Phase 9.4 remains open by design.** Canonical OpenSpec sync belongs to `sdd-archive` and should
   be completed there.

### SUGGESTION

1. When archiving, copy/sync the waitlist delta into `openspec/specs/lead-capture-waitlist/spec.md`,
   then close task 9.4 as part of archive evidence.
2. Track DALLAY-512 and DALLAY-513 as separate follow-up changes before any non-test environment
   enables `SMP_WAITLIST_RATE_LIMIT_ENABLED=true`.

## Verdict Table

| Finding                                                 | Judge A                                 | Judge B                                                | Severity | Status              |
|---------------------------------------------------------|-----------------------------------------|--------------------------------------------------------|----------|---------------------|
| Marketing form sends documented backend payload         | ✅ Vitest payload contract passed        | ✅ Playwright intercepted POST body passed              | INFO     | Confirmed           |
| Client-side validation blocks invalid email before POST | ✅ Validator Vitest passed               | ✅ Playwright empty/invalid-email tests passed          | INFO     | Confirmed           |
| Missing early-access consent blocks submission          | ✅ Astro form requires explicit checkbox | ✅ Playwright missing-consent test passed               | INFO     | Confirmed           |
| Documentation phase updated ADR/shared/C4 references    | ✅ Source inspection                     | ✅ Tasks/apply-progress aligned                         | INFO     | Confirmed           |
| Phase 9.4 canonical spec sync incomplete                | ✅ Task remains unchecked                | ✅ Archive convention owns canonical specs              | WARNING  | Confirmed deferred  |
| DALLAY-512 distributed bucket backend                   | ✅ Prior verify evidence                 | ✅ State/apply-progress document default-off mitigation | WARNING  | Confirmed follow-up |
| DALLAY-513 trusted proxy allowlist                      | ✅ Prior verify evidence                 | ✅ State/apply-progress document default-off mitigation | WARNING  | Confirmed follow-up |

## Final Verdict

PASS WITH WARNINGS — The implemented reusable lead-capture waitlist capability matches the proposal,
specs, design, and tasks for the verify-ready scope. Proceed to `sdd-archive`; close Phase 9.4
during archive and keep DALLAY-512/DALLAY-513 as production-enablement follow-ups before enabling
WAITLIST rate limiting outside tests.
