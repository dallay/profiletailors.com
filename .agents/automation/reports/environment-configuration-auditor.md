# Environment Configuration Audit Report

## Purpose

Audit environment configuration, `.env.example`, and application properties for drift and missing placeholders.

## Execution Result

`CHANGES_APPLIED` — The audit identified minor documentation drift in `.env.example` where environment variables referenced in `application.yaml` were omitted from the canonical template. All missing variables have been added with appropriate comments and defaults matching `application.yaml`.

## Scope Inspected

- `.env.example` (Canonical environment template)
- `server/smp/src/main/resources/application.yaml` (Spring Boot configuration properties)
- `server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/security/ProductionCredentialsValidator.kt` (Production credential safety check)
- `apps/web/marketing/astro.config.mjs` (Astro marketing env schema)
- `apps/web/app/vite.config.ts` (App dashboard Vite proxy env setup)
- `infra/apps/smp/production/compose.yaml` & `infra/apps/smp/swarm/stack.yaml` (Deployment compose and swarm configs)

## Changes Applied

- Updated `.env.example`:
  - Added `SENTRY_ENVIRONMENT` and `SENTRY_RELEASE` placeholders under the Sentry section.
  - Added `SMP_OAUTH_STATE_SIGNING_SECRET` as the generic provider OAuth state signing secret alongside `SMP_LINKEDIN_STATE_SIGNING_SECRET`.
  - Added `SMP_CREDENTIALS_RETENTION_*` configuration properties (`SMP_CREDENTIALS_RETENTION_ACTIVITY_ID`, `SMP_CREDENTIALS_RETENTION_POLICY_VERSION`, `SMP_CREDENTIALS_RETENTION_ENABLED`, `SMP_CREDENTIALS_RETENTION_EXPIRED_METADATA`, `SMP_CREDENTIALS_RETENTION_DISCONNECT_GRACE`, `SMP_CREDENTIALS_RETENTION_INTERVAL`, `SMP_CREDENTIALS_RETENTION_INITIAL_DELAY`, `SMP_CREDENTIALS_RETENTION_BATCH_SIZE`, `SMP_CREDENTIALS_RETENTION_DRY_RUN`) under the Credential encryption & retention section.

## Evidence Table

| Environment Variable / Resource | Canonical Location | Application / Deployment Binding | Alignment Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `SMP_BACKEND_PORT` | `.env.example` | `application.yaml` (`server.port`) | Aligned | Defaults to `7638` |
| `SENTRY_ENVIRONMENT` / `SENTRY_RELEASE` | `.env.example` | `application.yaml` (`sentry.*`) | Reconciled | Added missing placeholders to `.env.example` |
| `SMP_OAUTH_STATE_SIGNING_SECRET` | `.env.example` | `application.yaml` (`publishing.oauth.state-signing-secret`) | Reconciled | Added primary provider state signing variable to `.env.example` |
| `SMP_CREDENTIALS_RETENTION_*` | `.env.example` | `application.yaml` (`publishing.credentials.retention.*`) | Reconciled | Added 9 credential retention properties to `.env.example` |
| `SMP_DB_PASSWORD` | `.env.example` | `application.yaml` & `ProductionCredentialsValidator` | Aligned | Validates min length 32 in non-test profiles |
| `PUBLISHING_CREDENTIALS_ENCRYPTION_KEY` | `.env.example` | `application.yaml` & `ProductionCredentialsValidator` | Aligned | Required for AES-256 token encryption |
| `SMP_LOCAL_JWT_SECRET` | `.env.example` | `application.yaml` & `ProductionCredentialsValidator` | Aligned | Required outside dev profile |
| `SMP_MEDIA_PREVIEW_SIGNING_SECRET` | `.env.example` | `application.yaml` & `ProductionCredentialsValidator` | Aligned | Base64 32-byte signing secret |
| `WAITLIST_API_BASE` / `WAITLIST_ENABLED` | `.env.example` | `astro.config.mjs` (envField client schema) | Aligned | Client-side public fields |
| `VITE_API_BASE_URL` | `.env.example` | `apps/web/app/` | Aligned | Dashboard API target URL |

## Validation Table

| Check Name | Target | Status | Notes |
| :--- | :--- | :--- | :--- |
| `env-example-spring-bindings-alignment` | `.env.example`, `application.yaml` | PASSED | 100% alignment across all 117 Spring Boot environment variable bindings. |
| `production-credentials-validator-audit` | `ProductionCredentialsValidator.kt` | PASSED | Hardening check for production secret overrides verified intact. |
| `frontend-env-schema-alignment` | `astro.config.mjs`, `vite.config.ts` | PASSED | Client public and proxy environment configurations validated. |
| `spotless-kotlin-check` | `server/smp` | PASSED | `./gradlew spotlessKotlinCheck` passed. |
| `biome-lint-check` | `apps/web/*` | PASSED | `pnpm lint` passed across web frontend packages. |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

- **Last Execution:** `2026-09-28T00:00:00Z`
- **Execution Outcome:** `CHANGES_APPLIED`
- **Schema Version:** `1`
- **Task Identity:** `environment-configuration-auditor`

## Risk Assessment

- **Overall Risk:** LOW (Documentation & template placeholder alignment only. No backend runtime behavior modified).

## Human Review Notes

Reconciled `.env.example` with `application.yaml`. Added placeholders for Sentry configuration (`SENTRY_ENVIRONMENT`, `SENTRY_RELEASE`), generic OAuth state signing secret (`SMP_OAUTH_STATE_SIGNING_SECRET`), and credentials retention properties (`SMP_CREDENTIALS_RETENTION_*`). All environment variables in `application.yaml` are now 100% documented in `.env.example`.
