# Spring Configuration Binding Audit Report

## Purpose

Audit Spring configuration properties, environmental placeholders, `.env.example`, property-class bindings, and startup validations to detect drift or configuration mismatch.

## Execution Result

`CHANGES_APPLIED`

Reconciled configuration property bindings across `application.yaml`, `@ConfigurationProperties` classes, `@Value` injections, and `.env.example`. Applied minor documentation and YAML configuration remediations for detected low-risk drift.

## Scope Inspected

- `server/smp/src/main/resources/application.yaml`
- `.env.example`
- `@ConfigurationProperties` classes across `server/smp/src/main/kotlin/...`
- `@Value` annotations in Spring configuration classes (`IdentityEventConfiguration.kt`, `LinkedInPublishingWiring.kt`, `PublishingApplicationConfiguration.kt`, `McpSecurityConfiguration.kt`, `IdentityEmailDispatcher.kt`)

## Changes Applied

1. **`server/smp/src/main/resources/application.yaml`**:
   - Added `public-app-url: ${SMP_EMAIL_PUBLIC_APP_URL:https://app.profiletailors.com}` under `app.email` to match `EmailProperties.kt` and `IdentityEventConfiguration.kt`.
2. **`.env.example`**:
   - Documented `SMP_EMAIL_PUBLIC_APP_URL=https://app.profiletailors.com` under the Transactional emails section.
   - Documented `SMP_PUBLISHING_WORKER_STALE_GRACE=PT5M` under the Publishing worker section.

## Evidence Table

| Property / Finding ID | Binding Target | Issue Description | Remediation Applied |
| :--- | :--- | :--- | :--- |
| `BINDING-001` | `app.email.public-app-url` | Property used by `EmailProperties` and `@Value` in `IdentityEventConfiguration`, missing in `application.yaml`. | Added placeholder in `application.yaml` and documented in `.env.example`. |
| `BINDING-002` | `publishing.worker.stale-grace` | Used `${SMP_PUBLISHING_WORKER_STALE_GRACE:PT5M}` in `application.yaml`, missing from `.env.example`. | Added variable documentation in `.env.example`. |

## Validation Table

| Check Name | Target | Status | Notes |
| :--- | :--- | :--- | :--- |
| IdentityEventConfigurationTest | `com.profiletailors.smp.identity.infrastructure.IdentityEventConfigurationTest` | Passed | Verified email configuration properties binding. |
| spotlessKotlinCheck | `server/smp` | Passed | Formatting check passed. |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

- **Last Execution:** `2026-03-31T00:00:00Z`
- **Outcome:** `CHANGES_APPLIED`
- **Schema Version:** `1`
- **Task Identity:** `spring-configuration-binding-auditor`

## Risk Assessment

- **Overall Risk:** LOW RISK. All changes are limited to property placeholder alignment, default fallback definitions, and documentation in `.env.example`.

## Human Review Notes

Changes re-align Spring configuration bindings with `.env.example` without altering any production runtime defaults or breaking compatibility.
