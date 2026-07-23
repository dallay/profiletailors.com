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
| `docs/monitoring/actuator-security.md`: Only basic health status `/actuator/health` is public. Everything else requires authentication. | `IdentitySecurityConfiguration.kt` (lines 133-142): Excludes `/actuator/prometheus` via `permitAll()` under `HttpMethod.GET` | Unauthenticated public access to Prometheus metrics is permitted on the dedicated management port `9091`. (Note: Actuator endpoints are bound only to port 9091 per `application.yaml` management.server.port configuration and are not exposed on the main application port 7638.) | HIGH | Unresolved (Persisted due to VPC operational dependencies) |

## Validation Table

| Check Name | Target/Command | Status | Notes |
| :--- | :--- | :--- | :--- |
| **Backend Build** | `just backend-build` | Not run | Verification pending. |
| **Backend Fast Tests** | `just backend-test-fast` | Not run | Verification pending. |
| **Frontend Biome Check** | `just frontend-lint` | Not run | Verification pending. |

## Unresolved Findings

### 1. Actuator Prometheus Public Exposure

- **Finding ID:** `actuator-prometheus-exposure-drift`
- **Description:** The `/actuator/prometheus` endpoint is matched under `permitAll()` in the main Spring Security filter chain. While the endpoint is only bound to the dedicated management port 9091 (not the main application port 7638) per `application.yaml`, it remains unauthenticated on that management port.
- **Risk:** High. Allows unauthenticated access to system metrics, process properties, and internal paths on management port 9091.
- **Why Unresolved:** Modifying this rule to enforce HTTP-level authentication would break Prometheus scraping if existing scrapers expect unauthenticated access inside isolated VPC environments on port `9091`. Remediation requires safe, synchronized verification of deployment environments and confirmation that network-level restrictions (firewall rules) are sufficient.

## Blockers

- **None.**

## Automation State

- **Last Execution:** `2026-07-23T18:45:32Z`
- **Schema Version:** `1`
- **Task Identity:** `security-configuration-drift-auditor`

## Risk Assessment

- **Low Risk Actions:** Documenting identified configuration drift and auditing properties. (Passed)
- **High Risk Actions:** Changing Spring Security filter chains or requiring authentication for `/actuator/prometheus` (removing `permitAll()`). (Avoided/Persisted)

## Human Review Notes

1. **Review Prometheus Scraper Authentication:** Verify whether existing internal network/VPC scrapers can supply HTTP basic auth or Bearer tokens before removing `permitAll()` for `/actuator/prometheus`.
2. **Port Separation Enforcement:** Ensure that management port `9091` is strictly blocked at the firewall level for external traffic in production, reducing the real-world exposure of `/actuator/prometheus` to the internal VPC only.
