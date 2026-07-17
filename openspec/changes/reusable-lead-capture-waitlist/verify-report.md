# Verification Report

## Overview

**Change**: reusable-lead-capture-waitlist
**Mode**: openspec
**Verification scope**: DALLAY-440 Phase 6 (initial wiring + CI regression remediation) and Phase 6.6 (P2 Codex security remediation on PR #378: `RateLimitingFilter.getIdentifier` no longer trusts client-supplied `X-Forwarded-For`) and Phase 6.7 (production-safety default-off for `application.rate-limit.waitlist.enabled`, pending DALLAY-512 distributed bucket backend and DALLAY-513 trusted-proxy / `ForwardedHeaderFilter` allowlist wiring).
**Verdict**: PASS-WITH-CRITICAL-WARNINGS

## Executive Summary

The CI remediation confirms the root cause: importing shared `shared:shield:ratelimit` wiring into SMP made shared `RateLimitProperties.auth.enabled=true` effective at runtime, polluting authentication endpoint buckets even though SMP already has its own `AuthRateLimitWebFilter`.

`application.yaml` now explicitly disables shared AUTH, BUSINESS, and RESUME for the SMP application context while keeping shared WAITLIST enabled and env-configurable. This preserves SMP's pre-DALLAY-440 effective behavior for non-WAITLIST shared strategies and fixes `LocalAuthEndpointIntegrationTest.rejects invalid password` in the broader backend suite.

The P2 Codex security remediation (PR #378) dropped `X-Forwarded-For` trust from `RateLimitingFilter.getIdentifier` so the 10/min WAITLIST cannot be bypassed by rotating client-supplied headers; trusted-proxy wiring is intentionally deferred.

Phase 6.7 records the residual risk explicitly: WAITLIST is therefore turned off by default in the SMP context (`application.rate-limit.waitlist.enabled` defaults to `false`, `${SMP_WAITLIST_RATE_LIMIT_ENABLED:false}`), so flipping the limiter on still requires explicit operator action. The two production blockers are tracked as DALLAY-512 (distributed bucket backend; in-process Caffeine allows N×capacity blowout across replicas) and DALLAY-513 (trusted-proxy / `ForwardedHeaderFilter` allowlist so `remoteAddress` becomes the real client and not the ingress address).

## Completeness

| Metric | Value |
|---|---:|
| Total change tasks | 48 |
| Marked complete | 34 / 48 |
| DALLAY-440 Phase 6 tasks marked complete | 7 / 7 (6.1–6.7) |
| DALLAY-440 Phase 6 tasks verified compliant | 7 / 7 |
| Remaining change tasks | 14 (Phases 7–9 and Phase 8 subset) |

## Build and Test Evidence

| Command | Result | Evidence |
|---|---|---|
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :shared:shield:ratelimit:test --rerun-tasks` | PASS | Prior final run: `BUILD SUCCESSFUL in 10s`; recompiles and runs the full shared rate-limit suite, including WAITLIST identity isolation, service event/identifier behavior, filter matching, headers, and existing strategy default tests. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.integration.WaitlistRateLimitIntegrationTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.configuration.WaitlistRateLimitConfigurationTest' --rerun-tasks` | PASS | Prior final run: `BUILD SUCCESSFUL in 45s`; real Postgres-backed requests cover 11th-request `429`, duplicate/validation consumption, cross-waitlist same-IP isolation, headers, public access, and the no-scope-creep configuration contract. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.configuration.WaitlistRateLimitConfigurationTest' --tests 'com.profiletailors.smp.integration.LocalAuthEndpointIntegrationTest.rejects invalid password' --tests 'com.profiletailors.smp.leadcapture.integration.WaitlistRateLimitIntegrationTest' --rerun-tasks` | PASS | Verification rerun: `BUILD SUCCESSFUL in 1m 14s`; confirms SMP shared rate-limit binding, the prior auth test pollution regression, and DALLAY-440 waitlist runtime behavior in one focused server execution. |
| `git diff --check` | PASS | Re-run after focused tests; no whitespace errors in the working tree. |
| `./gradlew :shared:shield:ratelimit:test --tests 'com.profiletailors.ratelimit.infrastructure.RateLimitingFilterTest' --rerun-tasks` (post-fix) | PASS | `BUILD SUCCESSFUL in 6s`; 27 tests, 0 failures. Confirms the security property: `getIdentifier` keys on `remoteAddress`, ignores `X-Forwarded-For` (single value, comma-separated, rotation, malformed payload), and falls back to `IP:unknown` when `remoteAddress` is null. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.integration.WaitlistRateLimitIntegrationTest' --rerun-tasks` (post-fix) | PASS | `BUILD SUCCESSFUL in 49s`; 4 tests, 0 failures. The 11th-request scenario now rotates `X-Forwarded-For` per call (e.g. `198.51.100.1` … `198.51.100.10`, then `198.51.100.250`) and still returns `429 RATE_LIMIT_EXCEEDED`, proving the limit cannot be bypassed by rotating the header. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :shared:shield:ratelimit:test :server:smp:test --tests 'com.profiletailors.smp.leadcapture.integration.WaitlistRateLimitIntegrationTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.http.WaitlistControllerTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.configuration.WaitlistRateLimitConfigurationTest' --tests 'com.profiletailors.smp.identity.infrastructure.security.AuthRateLimitWebFilterTest' --rerun-tasks` | PASS | `BUILD SUCCESSFUL in 45s`; focused regression pass for the affected suites. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test --rerun-tasks` | PASS | `BUILD SUCCESSFUL in 3m 56s`; broader unfiltered SMP backend suite passes after the security fix. |
| `./gradlew :shared:shield:ratelimit:detekt` | PASS | `BUILD SUCCESSFUL in 2s`; no detekt regressions. |
| `./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.configuration.WaitlistRateLimitConfigurationTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.configuration.WaitlistRateLimitConfigurationOverrideTest' --rerun-tasks` | PASS | RED → GREEN for Phase 6.7. RED: new "SMP defaults shared WAITLIST to disabled" assertion failed (still bound to `true`). GREEN after flipping `${SMP_WAITLIST_RATE_LIMIT_ENABLED:false}` in `server/smp/src/main/resources/application.yaml`. `BUILD SUCCESSFUL in 20s`; the override test, the bandwidth-limit assertions, and the non-WAITLIST disables all stay green. |
| `./gradlew :server:smp:detekt --no-daemon` | PASS | `BUILD SUCCESSFUL`; the multi-line `# ...` comment block added on `application.rate-limit.waitlist.enabled` does not trip detekt (it lives in YAML, not Kotlin source). |

No coverage command was run: OpenSpec sets no coverage threshold, and focused runtime acceptance evidence is available.

## Spec Compliance Matrix

| Requirement / scenario | Evidence | Runtime result | Compliance |
|---|---|---|---|
| Public `POST /api/waitlists/{waitlistKey}/entries` is unauthenticated | `IdentitySecurityConfiguration` permits `POST /api/waitlists/*/entries`; integration requests contain no authentication header. | PASS | COMPLIANT |
| WAITLIST limit is configurable and defaults to 10 requests/minute per IP | SMP configuration provides env-overridable capacity/refill values; integration test exhausts exactly 10 tokens for one IP and route. | PASS | COMPLIANT |
| Plural `/api/waitlists` prefix matches and singular `/api/waitlist` does not | `RateLimitingFilterTest` exercises plural child matching and singular-prefix rejection. | PASS | COMPLIANT |
| Request 11 receives shared `429` body and headers | Integration test asserts `RATE_LIMIT_EXCEEDED` and message; header scenario asserts `Retry-After` and `X-RateLimit-Limit`. | PASS | COMPLIANT |
| Duplicate joins and validation errors consume before the controller | Integration test sends five duplicate `202` requests and five invalid `400` requests, then receives `429`; filter consumes before `chain.filter`. | PASS | COMPLIANT |
| Same IP is independently limited for different waitlists | Postgres test exhausts `profile-tailors-launch` for `10.0.0.4`, then gets `202` from `profile-tailors-beta`; shared Bucket4j test independently proves distinct WAITLIST bucket identities. | PASS | COMPLIANT |
| SMP explicitly disables shared AUTH/BUSINESS/RESUME | `application.yaml` sets `application.rate-limit.auth.enabled=false`, `business.enabled=false`, and `resume.enabled=false`; `WaitlistRateLimitConfigurationTest` asserts the bound SMP properties. | PASS | COMPLIANT |
| Existing non-WAITLIST bucket-key semantics remain intact | `RateLimitingService` calls the three-argument limiter only for `WAITLIST`; all other strategies retain the two-argument call and the complete shared suite passes. | PASS | COMPLIANT |
| Rate-limit events retain the original IP rather than `IP:path` | `RateLimitingServiceTest` captures the published WAITLIST event and asserts its identifier remains the IP. | PASS | COMPLIANT |
| `RateLimitingFilter.getIdentifier` does not trust client-supplied `X-Forwarded-For` | `RateLimitingFilterTest` exercises distinct forwarded headers with a stable `remoteAddress` and asserts the adapter is invoked with the same `IP:<remote>` identifier for every call. The existing `IP:unknown` fallback test is preserved. | PASS | COMPLIANT |
| WAITLIST 11th-request scenario is not bypassable by rotating `X-Forwarded-For` | `WaitlistRateLimitIntegrationTest` issues 10+1 calls to `/api/waitlists/profile-tailors-launch/entries` with `X-Forwarded-For` rotating per call (`198.51.100.1` … `198.51.100.10`, then `198.51.100.250`); the 11th call still receives `429 RATE_LIMIT_EXCEEDED` because the filter keys on `remoteAddress` (loopback) only. | PASS | COMPLIANT |
| Inline comment explains the security trade-off and the deferred trusted-proxy change | `RateLimitingFilter.kt::getIdentifier` carries a `// SECURITY:` block-comment that documents why `X-Forwarded-For` is dropped and points at the future `ForwardedHeaderFilter` / trusted-proxy wiring as a separate change. No other comment-only additions (per `AGENTS.md` policy: comments only when explaining WHY). | PASS | COMPLIANT |
| `application.rate-limit.waitlist.enabled` defaults to false in the SMP context | `WaitlistRateLimitConfigurationTest` explicitly asserts `properties.waitlist.enabled` is `false`, plus a sibling `WaitlistRateLimitConfigurationOverrideTest` proves setting `application.rate-limit.waitlist.enabled=true` flips the bound property (mirroring what `WaitlistRateLimitIntegrationTest` already does). `server/smp/src/main/resources/application.yaml` declares `enabled: ${SMP_WAITLIST_RATE_LIMIT_ENABLED:false}` with a rationale comment citing DALLAY-512 and DALLAY-513. | PASS | COMPLIANT |

## Correctness Table

| Finding | Judge A | Judge B | Severity | Status |
|---|---|---|---|---|
| Shared AUTH bucket pollution caused the auth integration CI regression | ✅ Focused configuration test failed before YAML remediation | ✅ `LocalAuthEndpointIntegrationTest.rejects invalid password` passed after remediation | INFO | Confirmed |
| SMP enables only shared WAITLIST in its application context | ✅ `WaitlistRateLimitConfigurationTest` asserts bound properties | ✅ Full SMP backend suite passed | INFO | Confirmed |
| WAITLIST remains configurable | ✅ `application.yaml` keeps `SMP_WAITLIST_RATE_LIMIT_*` overrides | ✅ DALLAY-440 waitlist integration coverage remains documented in apply progress | INFO | Confirmed |

## Design Coherence

| Design decision | Followed? | Notes |
|---|---|---|
| Server adapter wires shared rate-limit infrastructure | YES | `WaitlistRateLimitConfiguration` imports the shared configuration and components. |
| HTTP filter runs before controller handling | YES | The filter consumes before `chain.filter`; integration behavior confirms duplicate and validation paths cannot bypass it. |
| Per-IP, per-waitlist enforcement | YES | WAITLIST buckets use a stable `IP:path` cache identity; configuration, metrics, logs, and events retain the IP identifier. |
| Preserve non-WAITLIST effective behavior in SMP | YES | Shared AUTH, BUSINESS, and RESUME are explicitly disabled because DALLAY-440 only approved shared WAITLIST wiring; SMP's existing `AuthRateLimitWebFilter` continues to own auth endpoint throttling. |

## Issues

### CRITICAL

1. DALLAY-512 — Distributed bucket backend not implemented. `Bucket4jRateLimiter` stores buckets in an in-process Caffeine cache, and SMP runs in production behind Kubernetes / Cloud Run ingress with 3+ API replicas (`docs/architecture/c4/02-container.md` lines 330-345). Every replica maintains its own bucket per client, so a single client can exceed the configured 10/min WAITLIST allowance by up to `N × capacity` where `N` is the replica count. The KDoc on `Bucket4jRateLimiter` already warns about this. The P2 Codex remediation on PR #378 did not introduce distributed state; it intentionally left the in-process cache alone because distributed counters require their own topology decision (Redis / Hazelcast / shared Postgres). Tracked under DALLAY-512.
2. DALLAY-513 — Trusted-proxy / `ForwardedHeaderFilter` allowlist not wired. With the P2 fix, `RateLimitingFilter.getIdentifier` keys on `exchange.request.remoteAddress`, but that address is the ingress / load-balancer inside Kubernetes / Cloud Run (per `docs/architecture/c4/02-container.md`). Without a trusted-proxy allowlist (or the equivalent for Cloud Run / GCLB), all clients routed through the same ingress would collapse into one WAITLIST bucket — a fundamentally different bucket-collision mode than DALLAY-512. The previous code trusted client-supplied `X-Forwarded-For` and was exploitable; the current code has no positive trust list and silently keys on the wrong address when the deployment is multi-tenant behind a shared egress. Tracked under DALLAY-513.

Both blockers are mitigated for the current branch by defaulting the WAITLIST strategy to disabled (`SMP_WAITLIST_RATE_LIMIT_ENABLED:false`), so flipping the limiter on is always an explicit operator decision. They MUST be closed before enabling the WAITLIST limiter in any non-test environment with multiple replicas or shared ingress.

### WARNING

None.

### SUGGESTION

1. Future phases should complete the remaining 14 change tasks before treating the full reusable waitlist capability as delivered; they are out of this DALLAY-440 verification scope.

## Final Verdict

PASS-WITH-CRITICAL-WARNINGS — Phase 6 (1–6) + P2 Codex security remediation (6.6) + production-safety default-off (6.7) verified. Shared `RateLimitingFilter` no longer trusts client-supplied `X-Forwarded-For`, the SMP 11th-request scenario is not bypassable by rotating the header, and the WAITLIST limiter is now OFF by default with the env override kept explicit. Two CRITICAL production blockers remain unfixed in this branch: DALLAY-512 (distributed bucket backend) and DALLAY-513 (trusted-proxy / `ForwardedHeaderFilter` wiring). They must be closed before the WAITLIST limiter can be safely enabled in production.
