# Security Configuration Drift Audit Report

## Purpose

Audit the codebase to detect drift between the specified security architecture, documentation, and actual runtime configuration. This audit focuses on Spring Security configurations, endpoint exclusions, CORS rules, CSRF settings, cookie security attributes, and port-level separation requirements.

## Execution Result

Security configuration drift detected and remediated. `/actuator/prometheus` was exposed via public `permitAll()` in `IdentitySecurityConfiguration.kt`, contradicting `docs/monitoring/actuator-security.md`. The configuration was updated to require authentication for `/actuator/prometheus`.

## Scope Inspected

- `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/security/CredentialsSecurityConfiguration.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/mcp/infrastructure/McpSecurityConfiguration.kt`
- `docs/monitoring/actuator-security.md`
- `docs/architecture/adr/0009-jwt-and-httponly-cookie-authentication.md`

## Changes Applied

- Removed `"/actuator/prometheus"` from public `permitAll()` pathMatchers in `IdentitySecurityConfiguration.kt`.

## Evidence Table

| Target | Expected Security Control | Actual Security Control | Alignment Status |
| :--- | :--- | :--- | :--- |
| `/actuator/prometheus` | Internal / Authenticated only | Unauthenticated `permitAll()` | Fixed (now requires authentication) |
| `/actuator/health` | Publicly accessible | Unauthenticated `permitAll()` | Aligned |
| Refresh session cookies | `HttpOnly`, `SameSite=Lax/Strict` | `HttpOnly`, `SameSite=Lax` | Aligned |
| CORS origins & headers | Strict configuration via properties | Filtered and explicit | Aligned |
| MCP endpoints | Bearer JWT required | `mcpSecurityWebFilterChain` enforced | Aligned |

## Validation Table

| Check Name | Target | Status | Notes |
| :--- | :--- | :--- | :--- |
| Spring Security PermitAll Audit | `IdentitySecurityConfiguration.kt` | Passed | Verified `/actuator/prometheus` removed from `permitAll()`. |
| Actuator Endpoint Exposure Alignment | `docs/monitoring/actuator-security.md` | Passed | Verified runtime matches documented architecture. |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

- **Last Execution:** `2026-03-31T20:00:00Z`
- **Schema Version:** `1`
- **Task Identity:** `security-configuration-drift-auditor`

## Risk Assessment

- **Overall Risk:** LOW (High-risk finding remediated safely without weakening any security controls).

## Human Review Notes

Remediated public exposure of `/actuator/prometheus` endpoint in Spring Security configuration to conform with actuator security architecture documentation (`docs/monitoring/actuator-security.md`).
