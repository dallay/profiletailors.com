# Tasks: Password Recovery

## Overview

This document tracks implementation tasks across three stacked PRs for the Password Recovery
feature. Each task represents a unit of work with RED (test) / GREEN (implement) / REFACTOR phases.

## Changes

### Review Workload Forecast

| Field       | Value                                               |
|-------------|-----------------------------------------------------|
| Estimate    | Overall ~2800 lines; PR 2 ~650–950 lines; High risk |
| Chained PRs | Yes                                                 |
| Split       | PR 1 backend → PR 2 frontend → PR 3 hardening       |
| Strategy    | ask-on-risk; stacked-to-main                        |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

Bases: PR 1 `main`; PR 2 after PR 1; PR 3 after PR 1, independent of PR 2.

### PR 1 — Core backend

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
- [x] [PR-1.21] Evidence request behavior with executable BDD for public HTTP flow, privacy,
  lifecycle, notification, limits, validation, and flag; use focused unit/WebFlux/PostgreSQL tests
  for timing and transaction-failure invariants.
- [x] [PR-1.22] Evidence reset behavior with executable BDD for public errors, concurrency, hash,
  revocation, limits, validation, and flag; use focused unit/PostgreSQL tests for exact clock
  boundaries and rollback invariants.
- [x] [PR-1.23] Evidence persistence behavior with executable PostgreSQL BDD where externally
  observable; use focused repository/schema/PostgreSQL tests for hash-only storage, schema metadata,
  rollback, and lower-level concurrency; exclude cleanup.
- [x] [PR-1.24] Evidence notification behavior with executable BDD for dispatch and rendered
  content; use focused unit/integration tests for post-commit ordering, asynchronous provider
  delivery, failure logging, and template escaping; exclude retries/failures/telemetry.
- [x] [PR-1.25] Evidence security behavior with executable BDD for enumeration responses, replay,
  CORS, and brute-force limits; use focused deterministic tests for entropy/10,000-token uniqueness
  and post-work timing equalization; exclude audit.
- [x] [PR-1.26] Audit password-recovery glue: remove unused no-op steps, make every PR 1-referenced
  step executable, use `@identity @smoke @fast`, and reserve PR 3 scenarios for their later
  acceptance owner.
- [x] [PR-1.27] Run `just backend-check`.
- [x] [PR-1.28] Run `just backend-bdd-fast`.
- [x] [PR-1.29] Run `just backend-test-postgres` and `just backend-bdd-postgres`.
- [ ] [PR-1.30] Open backend PR when authorized.

### PR 2 — Frontend after PR 1

Route contract: `/forgot-password` is `guestOnly`; `/reset-password` is public/session-agnostic.
Both are `standalone` and render outside `AppShell`.

- [x] [PR-2.01] RED: cover POST bodies, `Accept-Language`, empty 202/204, and RFC 9457 status/code
  in `apps/web/app/src/modules/auth/infrastructure/auth-api.test.ts`; GREEN/REFACTOR `auth-api.ts`
  with typed void recovery calls.
- [x] [PR-2.02] RED: cover normalized email, password 8/128 boundaries, blank and mismatch in
  `apps/web/app/src/shared/lib/validation/schemas.test.ts`; GREEN/REFACTOR dedicated schemas in
  `schemas.ts`.
- [x] [PR-2.03] RED: cover validation, duplicate lock, generic confirmation, 429/503/fallback states
  in `apps/web/app/src/modules/auth/presentation/ForgotPasswordView.spec.ts`; GREEN/REFACTOR
  `ForgotPasswordView.vue`.
- [x] [PR-2.04] RED: cover missing/blank/array token, policy/mismatch, duplicate lock, generic token
  errors, and no auto-login in `ResetPasswordView.spec.ts`; GREEN/REFACTOR `ResetPasswordView.vue`.
- [x] [PR-2.05] RED: extend `apps/web/app/src/router/index.spec.ts` and `index.guard.test.ts`; GREEN
  routes in `index.ts`: forgot `guestOnly`, reset public/session-agnostic, protected behavior
  unchanged.
- [x] [PR-2.06] RED: cover metadata shell bypass in `apps/web/app/src/App.test.ts`; GREEN/REFACTOR
  `App.vue` and auth/recovery route metadata to use `standalone` instead of a route-name allowlist.
- [x] [PR-2.07] RED: assert a keyboard-reachable login-only forgot link in
  `apps/web/app/src/modules/auth/presentation/AuthView.spec.ts`; GREEN/REFACTOR `AuthView.vue`
  without regressing native form semantics.
- [x] [PR-2.08] Add parity-tested EN/ES recovery copy in
  `apps/web/app/src/shared/i18n/locales/{en,es}/passwordRecovery.ts`, locale `index.ts` files, and
  `shared/i18n/i18n-keys.test.ts`; verify wrapping-safe copy.
- [x] [PR-2.09] Add safe Problem Details mocks/constants in
  `apps/web/app/e2e/fixtures/{auth-helpers,test-data}.ts` and a locale-independent
  `e2e/pages/password-recovery-page.ts`; never log/snapshot secrets.
- [x] [PR-2.10] RED/GREEN core scenarios in
  `apps/web/app/e2e/specs/password-reset-frontend.spec.ts`: generic forgot success, 429/503,
  missing/invalid token, valid reset 204, success-to-login, and no auto-authentication.
- [x] [PR-2.11] RED/GREEN Playwright coverage for keyboard/announcements/labels, Pixel 5
  overflow/touch targets, EN/ES, authenticated forgot redirect, authenticated reset access, and
  token/password absence from storage/analytics/errors.
- [ ] [PR-2.12] Run focused `pnpm --filter app test:run -- <files>`, full
  `pnpm --filter app test:run`, `pnpm --filter app lint`, targeted
  `pnpm --filter app exec playwright test -c e2e/playwright.config.ts e2e/specs/password-reset-frontend.spec.ts e2e/specs/route-guards.spec.ts`,
  then `just app-build`; run full `just ci` before an authorized PR. Do not use `just frontend-*` (
  marketing).

Codecov follow-up: after PR 2, inspect focused app coverage/reporting and open a separate
frontend-only follow-up if recovery files miss expected coverage; do not import backend coverage
debt into this slice.

### PR 3 — Hardening after PR 1

- [x] [PR-3.01] RED: Test cleanup retention/idempotency and `@pr-3` glue.
- [x] [PR-3.02] GREEN/REFACTOR: Add cleanup port, scheduler, config, PostgreSQL adapter under
  `identity/`.
- [x] [PR-3.03] RED: Test successful/suspicious audit redaction.
- [x] [PR-3.04] GREEN/REFACTOR: Add post-commit audit; failures cannot roll back reset.
- [x] [PR-3.05] RED: Test retry, terminal failure, telemetry.
- [x] [PR-3.06] GREEN/REFACTOR: Add email retries, safe failure store, redaction.
- [x] [PR-3.07] RED: Test PII-free metric labels and span attributes.
- [x] [PR-3.08] GREEN: Add metrics/spans under `identity/infrastructure/observability/`.
- [x] [PR-3.09] RED: Add runbook acceptance checks.
- [x] [PR-3.10] GREEN: Create `docs/runbooks/password-recovery.md` for incidents, retries, cleanup,
  metrics, and rollback.
- [x] [PR-3.11] Run backend checks and all `@pr-3` scenarios; open PR after PR 1.

## Usage

Each task follows the RED/GREEN/REFACTOR cycle: write a failing test first (RED), implement the
minimum code to pass (GREEN), then refactor safely. Run verification via `just` recipes.

## References

- [Specification](../password-recovery/spec.md)
- [Design](../password-recovery/design.md)
- [Verify Report](../password-recovery/verify-report.md)
