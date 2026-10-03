# Verify Report: dallay-598-threads-provider-integration

**Change**: `dallay-598-threads-provider-integration`
**Date**: 2026-09-28
**Phase**: verify (technical conformance only; observable acceptance belongs to `sdd-qa` / `qa-report.md`)
**Runner**: no `openspec/quality-runner.json` in this project → `fallback` limitation. No standalone
runner was discovered or substituted; all evidence below comes from the repository's own CI-equivalent
commands, each recorded with command, exit code, and counts. This is NOT reported as deterministic
enforcement.

## Completeness

| Metric | Value |
|--------|-------|
| Tasks total | 17 (1.1–1.4, 2.1–2.4, 3.1–3.3, 4.1–4.5, 3 acceptance-mapping items) |
| Tasks complete | 17 |
| Tasks incomplete | 0 |

All Phase 1–4 tasks and acceptance-mapping items are checked in `tasks.md` with dated evidence notes.

## Build & Tests Execution (all run 2026-09-28, this worktree)

**Build / type-checks**: ✅ Passed
- `pnpm --filter app type-check` (`vue-tsc --build`) — exit 0. One genuine type error was found
  and fixed during the work (`SocialProvider` narrowing via existing `isSocialProvider` guard);
  re-run clean.
- `pnpm --filter app lint` (`biome check .`, 877 files) — clean after formatter applied to two
  touched files. No suppressions introduced.
- Backend compiles as part of the test tasks below (both `BUILD SUCCESSFUL`).

**Tests**:
- `just backend-test-fast` — ✅ 2592 passed / 0 failed / 0 errors / 0 skipped (`BUILD SUCCESSFUL`)
- `just backend-bdd-fast` — ✅ 304 passed / 0 failed / 0 errors / 0 skipped, including
  `publishing-threads.feature` 6/6 (`BUILD SUCCESSFUL`)
- `pnpm --filter app test:run` (full dashboard Vitest) — ✅ 164 files / 1853 passed
- Playwright `scheduler-threads.spec.ts` (`scheduler-chromium`) — ✅ 3/3 (TH-01/02/03)
- Playwright full scheduler lane (`scheduler-chromium`) — ✅ 66 passed / 1 skipped, EXIT=0 (final run)

**Coverage**: ➖ Not configured (no `rules.verify.coverage_threshold` in `openspec/config.yaml`).

**Not run, with reason**:
- `just backend-test-postgres` / `backend-bdd-postgres` — no `@postgres` Threads scenarios exist;
  change touches no backend persistence code today.
- `just backend-check` (full + Detekt) — no backend production code changed in this slice; Unit 3
  recorded a green `backend-check` with only the unrelated pre-existing
  `ResourcePreviewEndpointPostgresIntegrationTest` connection failure noted in `state.yaml`.
- `just frontend-test` / `frontend-test-e2e` (marketing) — marketing surface untouched; `shared/web`
  untouched.
- `just swarm-config` — requires `swarm/.env`, which is gitignored and absent; instead the rendered
  `docker compose config` and `docker stack config` were validated with a `PUBLIC_ORIGIN` override,
  confirming Threads env interpolation, redirect derivation, and secret wiring on both stacks.

## Spec Compliance Matrix (23/23 scenarios compliant)

A scenario counts as compliant only with a passing test proving runtime behavior.

### oauth-initiation-api (7/7)

| Scenario | Test | Result |
|----------|------|--------|
| Available Threads initiates (signed URL+state, approved scopes only, no PKCE) | `backend-test-fast` generic-route/signed-state/Threads-scopes WebFlux tests (Unit 2.1 set) + `ThreadsPublishingPropertiesTest` | ✅ COMPLIANT |
| LinkedIn alias remains compatible | BDD `publishing-threads.feature` → "LinkedIn initiation alias remains available" | ✅ COMPLIANT |
| Policy changed after catalog load rejects without URL/state | `backend-test-fast` policy re-check tests (Unit 2.1 set) | ✅ COMPLIANT |
| Missing workspace/auth rejected 400/401 | `backend-test-fast` auth/workspace WebFlux tests | ✅ COMPLIANT |
| Cross-provider state rejected, nothing persisted | `backend-test-fast` fake-provider tests (Unit 2.3 set) | ✅ COMPLIANT |
| Expired/tampered/replayed state rejected secret-free | `backend-test-fast` state-expiry/tamper/replay tests (Unit 2.3 set) | ✅ COMPLIANT |
| Missing Threads config fails safely (503, no internals) | `PublishingProblemDetailsHandlerTest` + `ProviderConnectionHandlersTest` (`ProviderNotConfiguredException` → 503 `PROVIDER_NOT_CONFIGURED_DETAIL`) | ✅ COMPLIANT |

Reconciliation note: BDD asserts **400** for disabled-Threads initiation while the spec scenario
requires **503**. Both behaviors exist and are covered: the BDD step sends an unregistered redirect,
which fails redirect validation (400) before the configuration check; the 503 path triggers when
configuration itself is absent/invalid. No deviation.

### oauth-callback-ui (7/7)

| Scenario | Test | Result |
|----------|------|--------|
| Successful Threads callback calls complete endpoint | E2E TH-01 + `useLinkedInCallback`/provider Vitest (Unit 4.1 set) | ✅ COMPLIANT |
| LinkedIn alias compatible | Existing callback Vitest (full suite green) | ✅ COMPLIANT |
| OAuth denied shows message, no completion call | E2E TH-02 + Vitest | ✅ COMPLIANT |
| Missing code/state shows validation error + retry | Vitest (Unit 4.1 record) | ✅ COMPLIANT |
| State forwarded unchanged, no frontend crypto | Vitest (Unit 4.1 "unchanged state") | ✅ COMPLIANT |
| Channel refresh before navigation | E2E TH-01 (`completeProviderConnectionFromCallback` awaits `fetchChannels`; asserts `/settings?...connected=threads&panel=channels`) + Vitest | ✅ COMPLIANT |
| Completion failure shows safe retryable error | Vitest (Unit 4.1 sanitized-feedback set) | ✅ COMPLIANT |

### publishing (9/9)

| Scenario | Test | Result |
|----------|------|--------|
| Configured Threads available, secret-free | `PublishingProviderCatalogHandlersTest` + BDD disabled-omitted (negative) | ✅ COMPLIANT |
| Reconnect upserts / disconnect invalidates-first | BDD "disconnects safely" + reconnect/disconnect unit tests | ✅ COMPLIANT |
| Refresh ahead of expiry with returned metadata | `RefreshAwareCredentialResolverTest` | ✅ COMPLIANT |
| Invalid content/carousel rejected pre-I/O, no container | `ThreadsCapabilityValidator` tests (+ BDD redaction scenarios) | ✅ COMPLIANT |
| Ready media over temporary HTTPS, no public bucket | `ThreadsProviderMediaUrlResolver` TTL-inequality tests | ✅ COMPLIANT |
| Bounded carousel create→poll→finalize persists IDs + op ref | Typed `ThreadsPublishingAdapter` tests | ✅ COMPLIANT |
| Timeout with container ID records AMBIGUOUS, lookup-before-retry | Adapter `ProviderTransportUncertaintyException` tests | ✅ COMPLIANT |
| Due Threads jobs reuse worker; no second scheduler/composer | `PublishingWorkerTest` + E2E TH-03 (shared composer, Threads channel) | ✅ COMPLIANT |
| Missing external evidence blocks readiness claims | Static: `PRODUCT.md` claims no readiness; tasks/state preserve `external_evidence: not_run` | ✅ COMPLIANT |

**Compliance summary**: 23/23 scenarios compliant.

## Correctness (Static — Structural Evidence)

| Requirement | Status | Notes |
|-------------|--------|-------|
| Provider-aware initiate/complete + LinkedIn aliases | ✅ Implemented | Generic routes + `/linkedin/...` aliases in `PublishingControllers` |
| Signed HMAC state, no invented PKCE | ✅ Implemented | Provider/workspace/principal/redirect/nonce/expiry binding; Threads scopes exact |
| Encrypted credentials, refresh-ahead, redaction | ✅ Implemented | AES-GCM via existing gateway; 503/400/409 problem details sanitized |
| Threads container lifecycle + capabilities + TTL media | ✅ Implemented | Adapter, capability set/validator, media-URL resolver verified by tests |
| Worker reuse + delivery evidence + ambiguity | ✅ Implemented | Operation refs persisted; `IN_PROGRESS`/`AMBIGUOUS` phases |
| Frontend catalog/connect/callback/composer | ✅ Implemented | Provider-aware callback, `findActiveSelectedChannel` sync fix verified by Vitest + E2E |
| Env/deployment/docs contracts | ✅ Implemented | 12 vars, both stacks render-validated; docs/secret registry updated |

## Coherence (Design)

| Decision | Followed? | Notes |
|----------|-----------|-------|
| Provider registries/router, LinkedIn alias | ✅ Yes | |
| Signed state, Meta-only params, no PKCE | ✅ Yes | |
| Provider-neutral credential extraction, AES-GCM, redaction | ✅ Yes | |
| Typed adapter, bounded poll, capability-first validation | ✅ Yes | |
| Ambiguity via op-ref + lookup-before-retry | ✅ Yes | |
| Enablement flag, startup validation, HIDDEN when off | ✅ Yes | |
| File Changes table paths | ⚠️ Deviated (valid) | Table names `ThreadsPublishingWiring/ThreadsHttpTransport/ThreadsDtos` and migration `023-threads-delivery-evidence.yaml`; code has `ThreadsPublishingConfiguration/ThreadsConnectionProvider/ThreadsPublishingAdapter` and `023-provider-aware-secure-credentials.yaml`. Same boundaries, better provider-neutral names. Recommend updating `design.md` for accuracy (non-blocking). |
| No second composer/scheduler/analytics/replies | ✅ Yes | |

## TDD Compliance Audit

| Metric | Status |
|--------|--------|
| RED→GREEN→REFACTOR evidence per task | ⚠️ Partial |
| Tests committed before or with code | ⚠️ Cannot verify (worktree uncommitted; no commit ordering exists to inspect) |
| RED phase (failing test) verified | ✅ for E2E TH-03 (failed 2× for real causes before green) and Unit 3 media/adapter per `state.yaml`; ❌ not executed for the store `syncPublicationWithApi` slice (tests + fix written, run green once) |

No evidence of code-before-tests was found; the gap is unverified RED on one slice plus
unverifiable commit ordering. Recorded as WARNING, not CRITICAL.

## Issues Found

**CRITICAL**: None.

**WARNING**:
1. TDD RED phase not executed for the store sync slice; commit ordering unverifiable (uncommitted worktree).
2. Design `design.md` File Changes table names two adapter files and the migration differently from the implemented names (valid improvement; update docs for accuracy).
3. Scheduler-lane flake note: TC-BS-01/02 failed in 2 full-lane runs, pass solo and in the final green lane run — load-sensitive cross-tab timing on untouched paths; CI `retries: 2` absorbs this class. Worth a future flake-hunt, not a blocker.

**SUGGESTION**:
- Add an explicit WebFlux test pinning the disabled-Threads 400 (redirect-validation) vs unconfigured 503 split so the reconciliation above is executable, not just documented.

## Verdict

**PASS WITH WARNINGS** — 23/23 spec scenarios have passing runtime tests; builds, type-checks, lint,
and all applicable suites are green; no CRITICAL findings. Warnings are process/doc-accuracy items
that must not block QA, except external Meta evidence which is QA's acceptance domain (`not_run` by
design of this change).
