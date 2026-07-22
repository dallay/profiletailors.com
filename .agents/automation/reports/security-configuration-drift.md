# Security Configuration Drift Audit Report

## Purpose

Audit the codebase to detect any drift between the specified security architecture, documentation, and actual runtime configuration. This audit focuses on Spring Security configurations, endpoint exclusions, CORS rules, CSRF settings, cookie security attributes, and port-level separation requirements.

## Execution Result

**Outcome:** `PARTIALLY_COMPLETED`

A security configuration drift has been successfully identified, verified, and documented. Because correcting this drift involves modifying core runtime security rules that may disrupt operational metrics collection (classified as HIGH RISK), the finding has been safely persisted as unresolved rather than applying speculative modifications.

## Scope Inspected

- **Spring Security Configuration:** `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt`
- **Actuator Security Specifications:** `docs/monitoring/actuator-security.md`
- **Application Properties:** `server/smp/src/main/resources/application.yaml`, `server/smp/src/main/resources/application-dev.yaml`, `.env.example`
- **E2E Security Specifications:** `apps/web/app/e2e/specs/security.spec.ts`
- **JWT & Cookie Specifications:** `docs/architecture/adr/0009-jwt-and-httponly-cookie-authentication.md`

## Changes Applied

- State file updated: `.agents/automation/state/security-configuration-drift.yaml`
- Report file updated: `.agents/automation/reports/security-configuration-drift.md`
- *No runtime code modifications were applied* to avoid violating high-risk runtime security boundaries.

## Evidence Table

| Spec / Doc Reference | Actual Code / Behavior | Identified Drift | Risk Level | Status |
| :--- | :--- | :--- | :--- | :--- |
| `docs/monitoring/actuator-security.md`: Only basic health status `/actuator/health` is public. Everything else requires authentication. | `IdentitySecurityConfiguration.kt` (lines 148-158): Excludes `/actuator/prometheus` via `permitAll()` under `HttpMethod.GET` | Unauthenticated public access to Prometheus metrics is permitted globally and on both port `7638` and management port `9091`. | HIGH | Unresolved (Persisted due to VPC operational dependencies) |

## Validation Table

| Check Name | Target/Command | Status | Notes |
| :--- | :--- | :--- | :--- |
| **Backend Build** | `just backend-build` | Passed | Application compiles successfully. |
| **Backend Fast Tests** | `just backend-test-fast` | Passed | Unit/integration test suites pass without regression. |
| **Frontend Biome Check** | `just frontend-lint` | Passed | Frontend formatting and linting rules are fully satisfied. |

## Unresolved Findings

### 1. Actuator Prometheus Public Exposure
- **Finding ID:** `actuator-prometheus-exposure-drift`
- **Description:** The `/actuator/prometheus` endpoint is matched under `permitAll()` in the main Spring Security filter chain.
- **Risk:** High. Allows unauthenticated access to system metrics, process properties, and internal paths.
- **Why Unresolved:** Modifying this rule to enforce HTTP-level authentication would break Prometheus scraping if existing scrapers expect unauthenticated access inside isolated VPC environments on port `9091`. Remediation requires safe, synchronized verification of deployment environments.

## Blockers

- **None.**

## Automation State

- **Last Execution:** `2026-07-23T12:00:00Z`
- **Schema Version:** `1`
- **Task Identity:** `security-configuration-drift-auditor`

## Risk Assessment

- **Low Risk Actions:** Documenting identified configuration drift and auditing properties. (Passed)
- **High Risk Actions:** Changing Spring Security filter chains or disabling authentication endpoints. (Avoided/Persisted)

## Human Review Notes

1. **Review Prometheus Scraper Authentication:** Verify whether existing internal network/VPC scrapers can supply HTTP basic auth or Bearer tokens before removing `permitAll()` for `/actuator/prometheus`.
2. **Port Separation Enforcement:** Ensure that management port `9091` is strictly blocked at the firewall level for external traffic in production, reducing the real-world exposure of `/actuator/prometheus` to the internal VPC only.
