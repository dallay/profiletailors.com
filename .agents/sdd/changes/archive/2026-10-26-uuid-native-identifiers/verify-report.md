# Verification Report: Native UUID Identifiers

## Overview

- Change: `uuid-native-identifiers`
- Verification date: 2026-10-26
- Branch: `feat/uuid-native-identifiers`
- Verdict: **PASS WITH WARNINGS for local technical conformance**
- Production readiness: **NOT APPROVED**
- Method: configured local `sdd-verify`, including its shared phase and OpenSpec protocols.

The implemented UUID conversion and approved WORKSPACE/WAITLIST separation have passing local runtime evidence. This verifier independently executed PostgreSQL and web fixture regressions, inspected the full final gate envelope and its XML snapshots, and read the relevant implementation, migration, tests, proposal, design, tasks and all five active delta specifications. No missing local implementation requirement or failing final test was identified. TDD provenance, some scenario evidence, and final artifact coordination have limitations described below.

This report does not authorize production SQL, deployment, commit, push or publication. Independent acceptance review and `qa-report.md` remain separate phase work.

## Completeness

| Metric | Value |
|---|---:|
| Checklist tasks total | 21 |
| Checked implementation, documentation and gate tasks | 18 |
| Unchecked final coordination tasks | 3 |
| Unfinished core source tasks identified | 0 |

The unchecked items are technical verification/report persistence, independent acceptance/report persistence, and final state reconciliation. This report supplies the first item's evidence; the orchestrator owns checkbox and state updates. These items are not unfinished implementation. Production approval and execution are deliberately outside the local checklist.

`tasks.md` is the current checklist. Earlier unchecked lists and failed runs in `apply-progress.md` are historical evidence, not the final gate result. `state.yaml` still contains the earlier four-failure/WAITLIST-blocked status and must be reconciled by the orchestrator before closure. Some inventory/design prose also retains earlier pending wording; it must not override the current executable evidence.

## Evidence locations and provenance

In the paths below, `TEMP` means:

`/private/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode`

| Evidence | Location | Provenance |
|---|---|---|
| Fresh verifier execution, XML and result snapshot | `TEMP/uuid-sdd-verifier-20261026/` | Executed by this verifier |
| Full stable backend/static/BDD gate envelope | `TEMP/uuid-waitlist-resume-20261026T165603Z/final-report.json` | Independently inspected in full |
| Preserved full-gate XML | Same directory, `04-backend-check-{test,postgresIntegrationTest}-xml/`, `05-bdd-fast-bddFastTest-xml/`, `06-bdd-postgres-bddPostgresTest-xml/` | Parsed and compared with the envelope |
| Final packaging/check/Kover log | `TEMP/uuid-final-backend-build.log` | Orchestrator execution; inspected |
| Shared Kotlin log and final totals | `TEMP/uuid-final-shared-kotlin.log`, `TEMP/uuid-final-local-totals.json` | Inspected and checked against actual XML |
| SQL harness | `TEMP/uuid-sql-orchestrator-final-console.log`, `TEMP/uuid-sql-orchestrator-final/` | Orchestrator rerun; inspected, not rerun by this verifier |
| Live local HTTP/browser acceptance | `TEMP/uuid-live-qa/http-scope-acceptance.json`, `repeat-browser-idempotency.json`, `browser-repeat.json` and screenshots | Existing packaged-backend/Astro/disposable-PostgreSQL evidence; not a new browser session by this verifier |
| Web final gates | `TEMP/web-scalpel-footer-{green,lint,type-check,build,unit}.log`, `web-scalpel-admin-{lint,type-check,build,test-run}.log`, `uuid-marketing-final-gates.log` | Existing local executions; inspected |
| Browser regression lanes | `TEMP/web-scalpel-footer-scheduler-recheck.log`, `web-scalpel-media-e2e.log`, `uuid-admin-e2e-completion-green.log` | Existing local executions; inspected |

The full final envelope records no source changes during its execution windows and no stale suites. Every preserved XML aggregate matches its corresponding envelope total. Comparison with `sources-before.json` checked 2,437 paths: only `shared/web/PRODUCT.md` differed at inspection, not runtime source or SQL. The later backend-build XML also matches the final totals. The fresh verifier command copied the preceding full PostgreSQL XML before Gradle replaced it, then copied its own two-suite XML and wrote `runtime-results.json` immediately after completion.

Independent runtime and SQL reviews were reported CLEAN by the orchestrator. Those reviews supplement, but do not replace, the execution and source evidence assessed here.

## Build and test execution

### Fresh commands executed by this verifier

No competing Gradle process was present when the runtime command started.

```text
node scripts/gradle-run.mjs :server:smp:postgresIntegrationTest --tests '*ConsentScopePostgresIntegrationTest' --tests '*R2dbcShortLinksPostgresIntegrationTest' --no-daemon --console=plain
```

Result: exit **0**, `BUILD SUCCESSFUL in 43s`. Consent scopes: **5/5**. Native PostgreSQL shortlinks: **10/10**. Total: **15 passed, 0 failures, 0 errors, 0 skipped**. XML suite timestamps are `2026-10-26T17:47:18.121Z` and `2026-10-26T17:47:28.005Z`. Evidence: `runtime.log`, `runtime-xml/`, `runtime-results.json` under the verifier evidence directory.

```text
pnpm --filter app test:run src/uuid-fixtures.test.ts --maxWorkers=2
pnpm --filter @profiletailors/admin test:run src/uuid-fixtures.spec.ts
```

Both exited **0**: app **11/11**, admin **2/2**. Evidence: `app-fixtures.log`, `admin-fixtures.log` under the same directory. These reruns are subsets of the full web suites, not additional distinct coverage.

`git diff --check` also passed. Added-line inspection found no newly introduced analysis suppression or explicit `any`; one changed test assertion retains its pre-existing `!!` while changing its fixture value. Existing suppressions/comments in surrounding source are not new exceptions or proof of compliance for future work.

### Full local gates inspected

| Gate | Result | Counts and qualifications |
|---|---|---|
| Final `just backend-check` | PASS, exit 0 | Standard task: 3,315 reported, 3,313 passed, 2 skipped, no failures/errors; PostgreSQL: 506/506 |
| `just backend-bdd-fast` | PASS, exit 0 | 325/325, no skips |
| `just backend-bdd-postgres` | PASS, exit 0 | 325/325, no skips |
| `just backend-build` | PASS | Log ends `BUILD SUCCESSFUL in 10m 48s`; includes packaging, `check`, PostgreSQL/standard tests and Kover verification |
| Shared waitlist Kotlin | PASS | 59/59; final shared log reports its test task up-to-date |
| Shared notifications Kotlin | PASS | 78/78; final shared log executes notification test compilation/tests |
| App lint/type-check/unit/build | PASS | 176 unit files, 2,005 passed |
| Admin lint/type-check/unit/build | PASS | 22 unit files, 148 passed |
| Marketing lint/check/unit/build | PASS | Astro check clean; 18 unit files, 163 passed |
| Shared web | PASS, existing local evidence | 74/74; browser receipt/schema/version unchanged |
| Critical app Chromium flows | PASS | Final footer-green run: 43 passed, no failures/skips |
| Scheduler browser lane | PASS with existing omission | 93 passed, 1 skipped; corrected HAR origin `5173` |
| Mocked media browser lane | PASS with existing omissions | 37 passed, 4 skipped |
| Admin browser lane | PASS | 15 passed, no skips |
| Marketing browser lane | PASS | 540 passed, no skips |
| Disposable SQL harness | PASS | 34 negative rejection/rollback cases; 93 validated FKs; 405 valid indexes; populated/fresh consent schemas equal |

The standard and PostgreSQL tasks overlap. Their counts must not be added as distinct tests. Both BDD lanes also execute the same 325 scenarios in separate configurations. Backend-build reruns likewise do not create additional distinct coverage.

The two standard-task omissions are `ModularStructureTest.generates module documentation()` and `prints module structure()`, not failed architecture assertions. Scheduler has its existing backend-dependent SSE omission. The four mocked-media omissions remain known limitations, not successful tests. Earlier timer, HAR-origin, stale-fixture and dialog-overflow failures are retained in historical logs; only the corrected complete reruns above are passing evidence. No assertion, skip, baseline or quality rule was weakened for this verification.

### Runner and coverage configuration

- `.agents/sdd/quality-runner.json` is absent: **fallback**, not deterministic-runner enforcement and not a passing runner result. Repository recipes and the explicitly requested narrow command provide execution evidence.
- No `rules.verify.test_command`, `build_command` or `coverage_threshold` is configured. Existing repository recipes and package scripts therefore supply the commands. No additional SDD coverage percentage threshold is invented.
- Backend `koverCachedVerify` and `koverVerify` passed in the actual backend-build log. This establishes the configured backend coverage gates, not an independently calculated changed-file coverage percentage.
- JVM class-sharing and Gradle 10 deprecation notices remain visible. Existing web bundle-size and jsdom limitations remain visible. These were not suppressed, and this report does not claim a warning-free toolchain.

## Scenario compliance matrix

All scenario names below correspond to the five active delta specifications. `COMPLIANT` requires inspected passing runtime evidence. `PARTIAL` means passing evidence covers only part of the scenario; it is not a missing implementation claim. The operational-plan rows do not certify a production rehearsal.

Test references use these source locations:

- `CS`: `server/smp/src/test/kotlin/com/profiletailors/smp/governance/infrastructure/ConsentScopePostgresIntegrationTest.kt`.
- `SL`: `server/smp/src/test/kotlin/com/profiletailors/smp/shortlinks/infrastructure/persistence/R2dbcShortLinksPostgresIntegrationTest.kt`.
- `BDD`: `server/smp/src/test/resources/features/`, with the corresponding glue and both passing BDD XML lanes.
- `SQL`: `server/smp/src/main/resources/db/changelog/uuid-native-identifiers/tests/`, with the inspected final SQL harness logs.

| Requirement / scenario | Passing test or execution evidence | Result |
|---|---|---|
| Publishing UUID: conversion and canonicalization | `CreatePublicationHandlerTest.should create new publication`; native publishing transaction `create commits publication asset links and job together`; SQL populated conversion assertions | COMPLIANT |
| Publishing UUID: invalid format | `HeaderActiveWorkspaceContextResolverTest.rejects prefixed workspace id`; `DataSubjectRequestIdTest` invalid-format parameter cases; notification ID validation tests | COMPLIANT for the tested input/consumer boundaries |
| Publishing UUID: references and workspace | `PublishingWorkspaceIsolationIntegrationTest` 5/5; publishing transaction suite 14/14, including actual asset links/jobs and rollback | COMPLIANT |
| Publishing UUID: API, clients and output surfaces | Both BDD publishing lanes; `PublicationEventEmissionTest` 5/5; app UUID fixtures 11/11 and scheduler 93 passing cases | COMPLIANT |
| Publishing UUID: external or opaque identifier | SQL semantic/opaque preservation assertions; `R2dbcMediaAssetRepositoryExternalMetadataTest.external metadata round trips through repository`; refresh/session fixture tests preserve tokens | COMPLIANT |
| Publishing migration: migrated integrity | SQL populated conversion, collision/orphan rejection and rollback assertions; publishing transaction suite | COMPLIANT |
| Publishing migration: rollout and rollback | SQL `missing_approval` and negative atomicity cases pass; runbook defines stopped writers, prepare/convert distinction and recovery. Representative restore and post-new-write recovery are not executed | PARTIAL, operational evidence remains required |
| Publishing migration: compatibility decision | Explicit-context/approval harness and UUID-only runtime pass; design/runbook specify legacy-before versus UUID-after and prohibit legacy writers. No deployed-client coordination rehearsal | PARTIAL, local plan verified only |
| Publishing migration: verification by slices | Full native PostgreSQL 506/506 across inventoried contexts; actual serialization/reference suites and both BDD lanes | COMPLIANT |
| Shortlinks UUID: canonical owner | `SL.versioned update is restricted to owner and expected version`; BDD `Authenticated user manages a link lifecycle`; `OwnerIdTest.parses canonical raw UUID` | COMPLIANT |
| Shortlinks UUID: prefixed or invalid owner | `OwnerIdTest.rejects prefixed identifier` and resolver rejection pass. Source maps `InvalidActiveWorkspaceException` to HTTP 400 before downstream context use; no dedicated malformed-header HTTP/no-repository-access regression was found | PARTIAL, direct HTTP negative-path evidence gap |
| Shortlinks UUID: root cause corrected | BDD authenticated lifecycle and cross-workspace access cases pass through actual context/HTTP/persistence; `SL` freshly passes 10/10 | COMPLIANT |
| Shortlinks transition: chosen rollout | SQL approval/atomicity cases pass; runbook defines coordinated UUID-only transition and owner mapping. Production coordination/restore is not exercised | PARTIAL, operational evidence remains required |
| Media UUID: asset identity | `R2dbcMediaRepositoriesPostgresTest.native UUID assets support collection lookup and cursor pagination`; BDD `Register a media asset via CAS PUT` / `Get a media asset by ID`; publishing asset-link transactions | COMPLIANT |
| Media UUID: workspace filter and authorization | BDD `Cross-tenant read is rejected` / `Cross-tenant delete is rejected`; mocked workspace A/B media tests | COMPLIANT |
| Media UUID: opaque key preserved | External metadata repository round trip; SQL opaque-field preservation; mocked Unsplash asset reference regression | COMPLIANT |
| Media transition: lossless migration | SQL populated conversion, row-count and relation assertions; collision/orphan rollback cases; native media repository suite 8/8 | COMPLIANT |
| Media transition: rollout compatibility | SQL approval/rollback guards pass; stopped-writer/client plan is documented. No representative production restore or deployed-client rehearsal | PARTIAL, operational evidence remains required |
| Media transition: integration by slice | Native media repository suite, actual publishing asset transactions, both media BDD lanes and mocked attachment flows pass | COMPLIANT |
| Consent scopes: public capture | `CS.public waitlist records use the real list UUID without a tenant workspace`; real browser HTTP 202 and persisted WAITLIST evidence | COMPLIANT |
| Consent scopes: idempotency | `CS.concurrent equivalent waitlist recording creates one active projection and one event` includes sequential replay; real browser repeated submission produces one entry/consent/event | COMPLIANT |
| Consent scopes: isolation | `CS.two lists and a workspace isolate reads and withdrawals for the same subject and purpose`; same-UUID BDD and live HTTP checks | COMPLIANT |
| Consent scopes: withdrawal | Same `CS` isolation test and `renewed waitlist consent preserves withdrawn history and append only event scope`; SQL withdrawal/history assertions | COMPLIANT |
| Consent scopes: concurrency | Fresh `CS` concurrent duplicate test; SQL concurrent checks for each scope column | COMPLIANT |
| Consent scopes: valid history | SQL `consent-scopes-assertions.sql` preserves projection/event identity and legal-evidence snapshots, including colon-containing semantic keys | COMPLIANT |
| Consent scopes: invalid history | SQL `missing_projection_key`, `missing_event_key`, `ambiguous_key`, `event_scope_mismatch`, `event_orphan` reject and preserve snapshots/schema/mappings | COMPLIANT |
| Consent scopes: tenant security | BDD `Tenant consent endpoints cannot query or withdraw public list consent`; live same-UUID list/history/withdrawal and unauthenticated HTTP 401 checks | COMPLIANT |
| Tenant consent API: excludes WAITLIST query | Same-UUID BDD list/history return empty; live authenticated tenant query excludes public record | COMPLIANT |
| Tenant consent API: withdrawal preserves WAITLIST | BDD tenant withdrawal returns 404 and preserves both lists' projection/events; live HTTP confirms public evidence unchanged | COMPLIANT |
| Tenant consent API: public capture grants no tenant privileges | `CS` public capture test, recorder unit tests, SQL no-fabricated-owner counts, and live no-new-workspace/unauthenticated-tenant rejection | COMPLIANT |

Compliance summary: **25/30 scenarios have compliant local behavioral evidence; 5/30 have explicitly partial evidence; 0 failing; 0 wholly untested scenarios.** Four partial rows concern operational rehearsal beyond the local implementation; the fifth is the specific malformed-shortlinks-header HTTP regression gap. These limitations must not be described as production approval or complete end-to-end negative-path coverage.

## Correctness and design coherence

| Contract or decision | Assessment |
|---|---|
| Internal UUIDs, native persistence and preserved references | Implemented in generators and UUID-typed R2DBC binds/reads. Executable manifest covers 174 unique entries across 74 tables, including optional notifications and both added waitlist references. SQL preserves counts, captures/recreates foreign keys, validates types and restores generated UUID defaults for former serial/identity keys |
| Semantic/provider/opaque exclusions | Preserved. Role/permission semantic keys, waitlist keys, tokens, storage keys, provider values, publication group correlation and `worker-*` leases are not blindly converted. Historical embedded values use typed mappings or fail-closed guards |
| Separate real consent scope | Implemented as framework-free `ConsentScope.Workspace` / `Waitlist` with UUID identities. Capture passes the resolved list ID through the existing application contract; governance does not import leadcapture domain entities |
| Exclusive scope and event integrity | SQL XOR checks, real-owner FKs, scope-paired event FKs and separate active uniqueness indexes enforce the approved contract. Repository scope predicates compare both references; same UUID values cannot merge scope identities |
| Scoped idempotency and history | Transactional insert/event handling returns the active row; only a new projection appends an event. Withdrawal/query/history bind the requested scope. Mutation through another scope is rejected and tested |
| Tenant HTTP authorization | Authorized workspace handlers construct WORKSPACE scope. Request DTOs do not expose a public-scope switch; tenant response mapping refuses WAITLIST records. Existing HTTP shape/media type and permissions remain in place |
| Root workspace-to-shortlinks path | Resolver canonicalizes accepted UUID input before resource context; shortlinks derives owner from that context. Authentication, ownership filters and native persistence remain separate and have passing integrated positive/isolation evidence |
| Explicit cutover, not implicit startup migration | Both changesets require `@uuid-native-identifiers`; convert additionally checks connection-local approval. Ordinary startup is not conversion authorization. Optional notifications startup creates a native table but does not silently alter a legacy table |
| Rollout and recovery | Conditional coordinated cutover is prepared, not operationally approved. Prepare and convert are separate transactions. No reverse conversion or lossless post-new-write restore is promised; incompatible rolling writers are prohibited |

No architectural bypass or permanent dual wire representation was identified in the reviewed implementation. Existing architecture tests remain in the passing standard gate. Backend domain/application boundaries, separate web surfaces and framework-neutral shared consent remain intact.

Product/contract impact is recorded in the app/admin/marketing/shared-web product files, active deltas, ADR-0029/0030, ADR index, architecture/C4 records, data inventory, observability and migration/rollback runbooks. Browser receipt fields/version are unchanged, so a consent receipt version bump is not required. API media type remains `application/vnd.api.v1+json`; the approved ID-format break is documented rather than hidden behind a new version. Main-spec synchronization, including the new consent-scopes spec and superseded nominal examples, belongs to archive and is not claimed as already performed.

## Strict TDD audit

`.agents/sdd/config.yaml` enables `testing.strict_tdd: true`.

| Audit item | Finding |
|---|---|
| RED→GREEN evidence | Confirmed for several recorded slices: notification/privacy validation, workspace provisioning, publishing generators, credential/refresh/consent adapters and web fixture/dialog regressions |
| RED evidence for every task | **Not verifiable**. Apply progress explicitly records incomplete pre-change RED evidence for some notification fallback, refresh/hashtag and initial principal/workspace slices |
| Commit ordering | Current change is an uncommitted worktree. New tests have no addition history; existing test history predates this migration. Git history cannot establish per-task test-first ordering |
| REFACTOR verification | Final runtime/static gates pass, but no complete per-task RED→GREEN→REFACTOR ledger exists |
| Confirmed implementation-before-test violation | Not established from the available evidence; absence of RED logs is not proof of chronology |

**WARNING:** full strict-TDD compliance is not certified. Passing retrospective regressions and final gates do not repair missing historical RED evidence. This is an evidence limitation, not a claim that all tasks followed TDD or a fabricated CRITICAL chronology finding.

## Residual warnings and nondecisions

### Critical

None identified for local implementation conformance. No final failing runtime test or missing implemented requirement was found.

### Warnings

1. Preserve the strict-TDD evidence limitation above. Do not mark every task TDD-verified.
2. The malformed/prefixed shortlinks workspace path has passing parser rejection and implemented HTTP mapping, but lacks a dedicated integrated HTTP rejection/no-access test in the inspected final evidence. Do not describe this exact negative path as independently HTTP-tested.
3. Complete the independent QA report and reconcile `state.yaml`, final coordination checkboxes and stale progress/inventory wording before closure. Some active planning/ADR text is Spanish despite the repository's English-only documentation standard; this report is English and does not silently rewrite those owner-controlled artifacts.
4. Keep the two documentation skips, scheduler/media omissions and existing toolchain/browser warnings visible. Real third-party Unsplash E2E was not run. Earlier failed attempts are not passing evidence.

### Release stop conditions, not local source tasks

- Unknown historical payloads, polymorphic references, replay responses and other explicitly guarded populated shapes remain **fail closed**. Production preflight must determine whether any guard applies. Such data requires a reviewed mapping and tested implementation; deleting evidence or bypassing a guard is not allowed.
- Representative production backup/restore, measured timing, full deployed-consumer inventory, named operational approver, stopped-writer verification and user-executed production pre/postconditions are not evidenced.
- Legal review for the separate public-consent scope remains pending. Engineering acceptance does not establish legal approval.
- Downtime, a maintenance window, rolling compatibility and a fallback compatibility implementation are not approved. If stopped-writer coordinated cutover cannot be approved, deployment stays blocked pending a separate plan.
- Remote GitHub CI/security/Sonar gates, production SQL, production smoke, deployment and live third-party provider verification were not executed by this mission. Local build/test evidence does not establish any of them.

## Verdict and handoff

**PASS WITH WARNINGS — local technical conformance.** Final local backend, PostgreSQL, BDD, web and SQL evidence is green, and this verifier's fresh 15 PostgreSQL plus 13 web fixture checks pass. The partial scenario evidence and TDD provenance limitations remain explicit. No implementation files were modified by this verifier.

Next: independent `sdd-qa` evidence assessment and `qa-report.md`, followed by orchestrator reconciliation of state/checklists and archive eligibility. Do not archive while acceptance is blocked or while final state still reports obsolete failures. Do not infer production or publication permission from this verdict.

Skill Resolution: fallback-registry — loaded configured local `sdd-verify` through the skill tool; read its shared phase/OpenSpec protocols and the repository skill registry. Applied repository architecture/testing boundaries without delegating or modifying implementation.
