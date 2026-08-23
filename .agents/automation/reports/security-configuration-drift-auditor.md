# Security Configuration Drift Audit Report

## Purpose

Audit the codebase to detect any drift between the specified security architecture, documentation, and actual runtime configuration. This audit focuses on Spring Security configurations, endpoint exclusions, CORS rules, CSRF settings, cookie security attributes, and port-level separation requirements.

## Execution Result

**Outcome:** `PARTIALLY_COMPLETED`

A security configuration drift has been successfully identified, verified, and documented. The finding is classified as HIGH ambiguous: removing `permitAll` from `/actuator/prometheus` is a candidate remediation, but the correct action depends on whether internal VPC Prometheus scrapers can supply authentication — operational context not conclusively supported by repository evidence. Per the framework remediation policy, the agent does not guess and persists the finding with a proposed remediation.

## Scope Inspected

- **Spring Security Configuration:** `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt`
- **Actuator Security Specifications:** `docs/monitoring/actuator-security.md`
- **Application Properties:** `server/smp/src/main/resources/application.yaml`, `server/smp/src/main/resources/application-dev.yaml`, `.env.example`
- **E2E Security Specifications:** `apps/web/app/e2e/specs/security.spec.ts`
- **JWT & Cookie Specifications:** `docs/architecture/adr/0009-jwt-and-httponly-cookie-authentication.md`

## Changes Applied

- State file updated: `.agents/automation/state/security-configuration-drift-auditor.yaml`
- Report file updated: `.agents/automation/reports/security-configuration-drift-auditor.md`
- *No runtime code modifications were applied* — the finding is HIGH ambiguous and the correct remediation is not conclusively supported by repository evidence.

## Evidence Table

| Spec / Doc Reference | Actual Code / Behavior | Identified Drift | Risk Level | Status |
| :--- | :--- | :--- | :--- | :--- |
| `docs/monitoring/actuator-security.md`: Only basic health status `/actuator/health` is public. Everything else requires authentication. | `IdentitySecurityConfiguration.kt` (lines 133-142): Excludes `/actuator/prometheus` via `permitAll()` under `HttpMethod.GET` | Unauthenticated public access to Prometheus metrics is permitted on the dedicated management port `9091`. (Note: Actuator endpoints are bound only to port 9091 per `application.yaml` management.server.port configuration and are not exposed on the main application port 7638.) | HIGH ambiguous | Unresolved (remediation proposed) |

## Validation Table

| Check Name | Target/Command | Status | Notes |
| :--- | :--- | :--- | :--- |
| **Backend Build** | `just backend-build` | Not run | Verification skipped to minimize risk; no backend code modified. |
| **Backend Fast Tests** | `just backend-test-fast` | Not run | Verification skipped to minimize risk; no backend code modified. |
| **Frontend Biome Check** | `just frontend-lint` | Passed | Verified repository frontend code formatting and linting. |

## Unresolved Findings

### 1. Actuator Prometheus Public Exposure

- **Finding ID:** `actuator-prometheus-exposure-drift`
- **Description:** The `/actuator/prometheus` endpoint is matched under `permitAll()` in the main Spring Security filter chain. While the endpoint is only bound to the dedicated management port 9091 (not the main application port 7638) per `application.yaml`, it remains unauthenticated on that management port.
- **Risk:** HIGH ambiguous. Allows unauthenticated access to system metrics, process properties, and internal paths on management port 9091.
- **Remediation Status:** `proposed` — remove `permitAll` from `/actuator/prometheus` to enforce authentication.
- **Why Not Implemented:** The correct action depends on whether internal VPC Prometheus scrapers can supply authentication. This is operational context not conclusively supported by repository evidence. Per the framework, the agent does not guess on HIGH ambiguous findings. The proposed remediation is included for human review.

## Blockers

- **None.**

## Automation State

- **Last Execution:** `2026-08-22T22:47:58Z`
- **Schema Version:** `1`
- **Task Identity:** `security-configuration-drift-auditor`

## Risk Assessment

- **Low Risk Actions:** Documenting identified configuration drift and auditing properties. (Passed)
- **High-Risk Actions:** Removing `permitAll()` from `/actuator/prometheus` is HIGH ambiguous — a proposed remediation exists but the correct action requires operational context (VPC scraper authentication) not available in repository evidence. Not implemented; persisted for human review.

## Human Review Notes

1. **Review Prometheus Scraper Authentication:** Verify whether existing internal network/VPC scrapers can supply HTTP basic auth or Bearer tokens before removing `permitAll()` for `/actuator/prometheus`.
2. **Port Separation Enforcement:** Ensure that management port `9091` is strictly blocked at the firewall level for external traffic in production, reducing the real-world exposure of `/actuator/prometheus` to the internal VPC only.
