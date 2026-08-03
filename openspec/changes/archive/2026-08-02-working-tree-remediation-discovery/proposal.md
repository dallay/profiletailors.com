# Proposal: Working Tree Remediation — 97 Paths, 11 Work Units

## MVP Decision Update — Distributed Waitlist Rate Limiting Deferred

The approved pragmatic MVP decision is to defer Redis and all distributed rate-limit
implementations. The current accepted behavior is bounded per-JVM Caffeine rate limiting, with
the SMP waitlist limiter defaulting to OFF. Multi-replica waitlist enablement remains blocked
until DALLAY-512 (distributed bucket backend) and DALLAY-513 (trusted proxy/client identity) are
resolved. Slice F7.1 is therefore cancelled/deferred, not failed.

## Intent

The original intent was to land all 97 uncommitted working-tree paths (11 units; index: 4 staged,
1 `MM`, 16 untracked) as safe, verifiable, unit-scoped commit slices. The approved MVP decision
removes the distributed waitlist test from that delivery: F7.1 is deferred outside MVP rather than
implemented or treated as a failed requirement.

## Scope

### In Scope
- 9-commit plan: **A1** consent (staged); **A2** marketing a11y/SEO; **B** password 8→12 (backend+app+specs+ES i18n); **C** authz BDD; **D** LinkedIn signer guard; **E** ideas fmt + detekt baseline; **F** waitlist rate-limit E2E (cancelled/deferred); **G** gradle licenses; **H** compliance docs; **I** repo config+docs.
- Fix gaps: (1) ES i18n stale — `es/auth.ts` placeholder + `es/passwordRecovery.ts` `passwordTooShort` still "8"; (2) `registerSchema` lacks min(12) parity; (3) deliberate staged-slice handling (`ConsentBanner.astro` `MM`).
- TDD: validate in-tree tests; add missing regressions (ES "12" assertion, registerSchema parity); focused verification per unit.

### Out of Scope
Nothing outside the working tree; no new features beyond diff intent; no committing without approval; composer i18n UI deferred (groundwork only).

## Capabilities

### New Capabilities
None — remediation of already-built work.

### Modified Capabilities
- `e2e` (login/register flows): password min 8→12 (spec-sync in tree)
- `iam`: 12-char enforcement (SEC-009), proxy removal (SEC-001), signer guard (SEC-002)
- `publishing`: LinkedIn signer fail-fast on placeholder secrets (SEC-002)
- `lead-capture-waitlist`: retain per-instance bounded rate limiting; defer distributed enforcement from MVP

## Approach

Apply in exploration order (Tier 1: B→C→D; Tier 2: A→E→F→G; Tier 3: H→I): failing test → minimum code → focused verification → stage only that unit's paths. Docs land after referenced implementation. F is resolved by recording the MVP deferral and removing its untracked test; no Redis implementation is added.

## Affected Areas

`apps/web/marketing`, `apps/web/app`, `server/smp`, `gradle/*` + `libs.versions.toml`, `docs/`, `openspec/` — all Modified.

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| awsS3 major bump breaks compile | Med | `backend-build` gate in G |
| SEC-002 fail-fast rejects dev `.env` secrets | Med | Verify real secrets before D |
| Baseline deletion fails lint | Med | E bundles ideas formatting |
| Retention docs claim unverified governance API | High | Validate API before H |
| Distributed waitlist enforcement is not available in the MVP | Accepted | Keep bounded per-JVM Caffeine behavior, default SMP waitlist limiting OFF, and defer multi-replica enablement until DALLAY-512/DALLAY-513 |

## Rollback Plan

Atomic slices: revert any failed slice via `git revert`/reset; un-stage A1 via `git restore --staged`; no migrations.

## Dependencies

Docker + `SMP_DB_TEST_PASSWORD` (Postgres); `just infra-up` (Postgres BDD); strong `SMP_LINKEDIN_STATE_SIGNING_SECRET` before D.

## Success Criteria

- [ ] Original 97-path plan reconciled into unit-scoped commits, with F7.1 explicitly deferred outside MVP
- [ ] ES i18n asserts "12"; registerSchema min(12) + mirror test
- [ ] `just ci-local` green after I; no commit without approval

## Verification Commands (`just`)

`backend-test-fast`, `backend-bdd-fast`, `backend-test-postgres`, `backend-bdd-postgres`, `backend-lint`, `backend-check`, `backend-build`, `licence-check`; `frontend-test`, `frontend-test-e2e`, `frontend-check`, `frontend-lint`; `pnpm --filter app test:run` / `type-check` / `test:e2e:scheduler`; final `ci-local`.
