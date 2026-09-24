# Environment Configuration Audit Report

## Purpose

Audit environment configuration, `.env.example`, and application properties for drift and missing placeholders.

## Execution Result

`CHANGES_APPLIED` — Audit completed successfully. Identified and reconciled variable name drift in `server/smp/src/main/resources/application-dev.yaml` for `app.email.publicAppUrl`.

## Scope Inspected

- `.env.example` (Canonical environment template)
- `server/smp/src/main/resources/application.yaml` (Spring Boot configuration properties)
- `server/smp/src/main/resources/application-dev.yaml` (Dev profile configuration properties)
- `server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/security/ProductionCredentialsValidator.kt` (Production credential safety check)
- `apps/web/marketing/astro.config.mjs` (Astro marketing env schema)
- `apps/web/app/vite.config.ts` (App dashboard Vite proxy env setup)
- `infra/apps/smp/production/compose.yaml` & `infra/apps/smp/swarm/stack.yaml` (Deployment compose and swarm configs)

## Changes Applied

- Reconciled `app.email.publicAppUrl` binding in `server/smp/src/main/resources/application-dev.yaml` to `${SMP_EMAIL_PUBLIC_APP_URL:${SMP_PUBLIC_APP_URL:https://pt-app.localhost}}` so canonical `.env.example` key `SMP_EMAIL_PUBLIC_APP_URL` is respected in dev mode while retaining worktree fallback `SMP_PUBLIC_APP_URL`.

## Evidence Table

| Environment Variable / Resource | Canonical Location | Application / Deployment Binding | Alignment Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `SMP_BACKEND_PORT` | `.env.example` | `application.yaml` (`server.port`) | Aligned | Defaults to `7638` |
| `SMP_EMAIL_PUBLIC_APP_URL` | `.env.example` | `application.yaml` & `application-dev.yaml` | Aligned (Fixed) | Prioritized over `SMP_PUBLIC_APP_URL` in `application-dev.yaml` |
| `SMP_DB_PASSWORD` | `.env.example` | `application.yaml` & `ProductionCredentialsValidator` | Aligned | Validates min length 32 in non-test profiles |
| `PUBLISHING_CREDENTIALS_ENCRYPTION_KEY` | `.env.example` | `application.yaml` & `ProductionCredentialsValidator` | Aligned | Required for AES-256 token encryption |
| `SMP_LOCAL_JWT_SECRET` | `.env.example` | `application.yaml` & `ProductionCredentialsValidator` | Aligned | Required outside dev profile |
| `SMP_MEDIA_PREVIEW_SIGNING_SECRET` | `.env.example` | `application.yaml` & `ProductionCredentialsValidator` | Aligned | Base64 32-byte signing secret |
| `SMP_LINKEDIN_STATE_SIGNING_SECRET` | `.env.example` | `application.yaml` & `ProductionCredentialsValidator` | Aligned | OAuth state signing secret |
| `WAITLIST_API_BASE` / `WAITLIST_ENABLED` | `.env.example` | `astro.config.mjs` (envField client schema) | Aligned | Client-side public fields |
| `VITE_API_BASE_URL` | `.env.example` | `apps/web/app/` | Aligned | Dashboard API target URL |

## Validation Table

| Check Name | Target | Status | Notes |
| :--- | :--- | :--- | :--- |
| `env-example-spring-bindings-alignment` | `.env.example`, `application.yaml`, `application-dev.yaml` | PASSED | All properties accurately mapped with safe defaults or mandatory placeholders. |
| `production-credentials-validator-audit` | `ProductionCredentialsValidator.kt` | PASSED | Hardening check for production secret overrides verified intact. |
| `frontend-env-schema-alignment` | `astro.config.mjs`, `vite.config.ts` | PASSED | Client public and proxy environment configurations validated. |
| `backend-unit-and-integration-tests` | `:server:smp:test` | PASSED | Spring Boot configuration property loading and identity tests passed. |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

- **Last Execution:** `2026-09-18T19:30:00Z`
- **Execution Outcome:** `CHANGES_APPLIED`
- **Schema Version:** `1`
- **Task Identity:** `environment-configuration-auditor`

## Risk Assessment

- **Overall Risk:** LOW (Reconciled property binding in dev profile configuration; no breaking changes or production impact).

## Human Review Notes

Reconciled `app.email.publicAppUrl` in `application-dev.yaml` to accept `SMP_EMAIL_PUBLIC_APP_URL` alongside `SMP_PUBLIC_APP_URL`. All other environment configurations align with monorepo specifications.
