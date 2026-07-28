# Tasks: Password Recovery

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimate | ~2800 lines; High risk |
| Chained PRs | Yes |
| Split | PR 1 backend → PR 2 frontend → PR 3 hardening |
| Strategy | ask-on-risk; stacked-to-main |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

Bases: PR 1 `main`; PR 2 after PR 1; PR 3 after PR 1, independent of PR 2.

## PR 1 — Core backend

- [x] [PR-1.01] Add Liquibase schema.
- [x] [PR-1.02] Add rollback.
- [x] [PR-1.03] Add `identity/domain/PasswordResetToken.kt`.
- [x] [PR-1.04] Add `identity/domain/PasswordResetRequested.kt`.
- [x] [PR-1.05] Add/test `identity/application/PasswordResetTokenHasher.kt`.
- [x] [PR-1.06] Add token exceptions.
- [x] [PR-1.07] Add recovery-disabled exception.
- [x] [PR-1.08] Add commands/results.
- [x] [PR-1.09] Add token repository port.
- [x] [PR-1.10] Add/test request handler.
- [x] [PR-1.11] Add/test atomic reset handler.
- [x] [PR-1.12] Add/test R2DBC token adapter.
- [x] [PR-1.13] Add/test safe email template.
- [x] [PR-1.14] Add primary email consumer.
- [x] [PR-1.15] Add 202/204 endpoints.
- [x] [PR-1.16] Add Problem mappings.
- [x] [PR-1.17] Permit recovery endpoints.
- [x] [PR-1.18] Add session revocation.
- [x] [PR-1.19] Add rate limits.
- [x] [PR-1.20] Add recovery feature flag.
- [x] [PR-1.21] Evidence request behavior with executable BDD for public HTTP flow, privacy, lifecycle, notification, limits, validation, and flag; use focused unit/WebFlux/PostgreSQL tests for timing and transaction-failure invariants.
- [x] [PR-1.22] Evidence reset behavior with executable BDD for public errors, concurrency, hash, revocation, limits, validation, and flag; use focused unit/PostgreSQL tests for exact clock boundaries and rollback invariants.
- [x] [PR-1.23] Evidence persistence behavior with executable PostgreSQL BDD where externally observable; use focused repository/schema/PostgreSQL tests for hash-only storage, schema metadata, rollback, and lower-level concurrency; exclude cleanup.
- [x] [PR-1.24] Evidence notification behavior with executable BDD for dispatch and rendered content; use focused unit/integration tests for post-commit ordering, asynchronous provider delivery, failure logging, and template escaping; exclude retries/failures/telemetry.
- [x] [PR-1.25] Evidence security behavior with executable BDD for enumeration responses, replay, CORS, and brute-force limits; use focused deterministic tests for entropy/10,000-token uniqueness and post-work timing equalization; exclude audit.
- [x] [PR-1.26] Audit password-recovery glue: remove unused no-op steps, make every PR 1-referenced step executable, use `@identity @smoke @fast`, and reserve PR 3 scenarios for their later acceptance owner.
- [x] [PR-1.27] Run `just backend-check`.
- [x] [PR-1.28] Run `just backend-bdd-fast`.
- [x] [PR-1.29] Run `just backend-test-postgres` and `just backend-bdd-postgres`.
- [ ] [PR-1.30] Open backend PR when authorized.

## PR 2 — Frontend after PR 1

- [ ] [PR-2.01] RED/GREEN: Test/add recovery API in `auth/api/auth.ts`.
- [ ] [PR-2.02] RED/GREEN: Test/add forgot view.
- [ ] [PR-2.03] RED/GREEN: Test/add reset view.
- [ ] [PR-2.04] Add login link and guest routes under `apps/web/app/src/modules/auth/`.
- [ ] [PR-2.05] Add EN/ES copy under `apps/web/app/src/locales/`.
- [ ] [PR-2.06] Run frontend tests; open PR after PR 1.

## PR 3 — Hardening after PR 1

- [ ] [PR-3.01] RED: Test cleanup retention/idempotency and `@pr-3` glue.
- [ ] [PR-3.02] GREEN/REFACTOR: Add cleanup port, scheduler, config, PostgreSQL adapter under `identity/`.
- [ ] [PR-3.03] RED: Test successful/suspicious audit redaction.
- [ ] [PR-3.04] GREEN/REFACTOR: Add post-commit audit; failures cannot roll back reset.
- [ ] [PR-3.05] RED: Test retry, terminal failure, telemetry.
- [ ] [PR-3.06] GREEN/REFACTOR: Add email retries, safe failure store, redaction.
- [ ] [PR-3.07] RED: Test PII-free metric labels and span attributes.
- [ ] [PR-3.08] GREEN: Add metrics/spans under `identity/infrastructure/observability/`.
- [ ] [PR-3.09] RED: Add runbook acceptance checks.
- [ ] [PR-3.10] GREEN: Create `docs/runbooks/password-recovery.md` for incidents, retries, cleanup, metrics, and rollback.
- [ ] [PR-3.11] Run backend checks and all `@pr-3` scenarios; open PR after PR 1.
