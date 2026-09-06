# Feature Flag Consistency Audit Report

## Purpose

Audit feature flags for consistency between configuration, code, and documentation.

## Execution Result

The feature flag auditor completed an inspection across all backend Spring properties, environment configuration files (`application.yaml`, `.env.example`), domain models, and frontend applications. No feature flag drift, dead flags, or inconsistent flag defaults were detected (`NO_DRIFT_DETECTED`).

## Scope Inspected

- **Configuration:** `server/smp/src/main/resources/application.yaml`, `.env.example`, `apps/web/marketing/.env.example`
- **Backend Properties:** `SocialContentProperties.kt`, `AuditHooksProperties.kt`, `PublishingWorkerProperties.kt`, `RateLimitProperties.kt`, `MediaProperties.kt`, `UnsplashProperties.kt`
- **Backend Domain & Security:** `SocialContentFeatureGates.kt`, `DefaultCapabilityResolver.kt`, `SocialContentAccessGate.kt`, `PublicCapabilitiesController.kt`
- **Frontend Toggles:** `apps/web/marketing/src/legal/commerce-publication.ts`, `apps/web/marketing/src/legal/legal-publication.ts`

## Changes Applied

None.

## Evidence Table

| Flag / Property Name | Configuration Default | Code Default | Alignment Status | Notes / Call Sites |
| :--- | :--- | :--- | :--- | :--- |
| `spring.ai.mcp.server.enabled` | `false` | `false` | Aligned | Controls Spring AI MCP server transport bean loading |
| `application.rate-limit.*.enabled` | `false` | `true` (override in `application.yaml`) | Aligned | Gated in `RateLimitingFilter.kt` and `BucketConfigurationFactory.kt` |
| `media.dedup.enabled` | `true` | `true` | Aligned | Controls media asset deduplication |
| `platform.media.context.integration.enabled` | `true` | `true` | Aligned | Gated in media context handlers |
| `platform.hooks.audit.enabled` | `false` | `false` | Aligned | Gated in `AuditHooksProperties.kt` |
| `platform.hooks.metrics.enabled` | `false` | `false` | Aligned | Metric collection hooks |
| `platform.hooks.rate-limit.enabled` | `false` | `false` | Aligned | Platform rate limiting hook toggle |
| `publishing.worker.enabled` | `false` | `false` | Aligned | Gated in `PublishingWorker.kt` |
| `publishing.social-content.*` | `false` | `false` | Aligned | `discoveryEnabled`, `importEnabled`, `inboxEnabled`, `repliesEnabled`, `syncEnabled` in `SocialContentProperties.kt` |
| `mediaprovider.unsplash.enabled` | `false` | `false` | Aligned | Gated in `UnsplashAutoConfiguration.kt` |

## Validation Table

| Check Name | Target | Status | Notes |
| :--- | :--- | :--- | :--- |
| `feature-flag-declarations-consistency` | Backend Properties & Yaml | Passed | Verified call sites and default values across application.yaml, .env.example, and properties classes. |
| `feature-flag-rollout-intent-audit` | Monorepo Feature Toggles | Passed | Confirmed no dead or stale flags exist. |
| `backend-tests` | `:server:smp:test` | Passed | Ran `./gradlew :server:smp:test -PexcludeTags=modularity,postgres`. |

## Unresolved Findings

None.

## Blockers

None.

## Automation State

- **Last Execution:** `2026-09-05T17:57:01Z`
- **Schema Version:** `1`
- **Task Identity:** `feature-flag-auditor`

## Risk Assessment

- **Overall Risk:** LOW (Audit execution with no code changes; NO_DRIFT_DETECTED).

## Human Review Notes

All feature flags across the application maintain strict consistency between environment templates (`.env.example`), Spring application configuration (`application.yaml`), and Kotlin properties classes. No action is required.
