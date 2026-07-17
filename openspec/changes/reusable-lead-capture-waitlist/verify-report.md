# Verification Report

## Overview

**Change**: reusable-lead-capture-waitlist
**Mode**: openspec
**Verification scope**: DALLAY-440 Phase 6 CI regression remediation after shared rate-limit wiring enabled non-WAITLIST defaults in SMP
**Verdict**: PASS

## Executive Summary

The CI remediation confirms the root cause: importing shared `shared:shield:ratelimit` wiring into SMP made shared `RateLimitProperties.auth.enabled=true` effective at runtime, polluting authentication endpoint buckets even though SMP already has its own `AuthRateLimitWebFilter`.

`application.yaml` now explicitly disables shared AUTH, BUSINESS, and RESUME for the SMP application context while keeping shared WAITLIST enabled and env-configurable. This preserves SMP's pre-DALLAY-440 effective behavior for non-WAITLIST shared strategies and fixes `LocalAuthEndpointIntegrationTest.rejects invalid password` in the broader backend suite.

## Completeness

| Metric | Value |
|---|---:|
| Total change tasks | 48 |
| Marked complete | 33 / 48 |
| DALLAY-440 Phase 6 tasks marked complete | 5 / 5 |
| DALLAY-440 Phase 6 tasks verified compliant | 5 / 5 |
| Remaining change tasks | 15 (Phases 7–9 and Phase 8 subset) |

## Build and Test Evidence

| Command | Result | Evidence |
|---|---|---|
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :shared:shield:ratelimit:test --rerun-tasks` | PASS | Prior final run: `BUILD SUCCESSFUL in 10s`; recompiles and runs the full shared rate-limit suite, including WAITLIST identity isolation, service event/identifier behavior, filter matching, headers, and existing strategy default tests. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.integration.WaitlistRateLimitIntegrationTest' --tests 'com.profiletailors.smp.leadcapture.infrastructure.configuration.WaitlistRateLimitConfigurationTest' --rerun-tasks` | PASS | Prior final run: `BUILD SUCCESSFUL in 45s`; real Postgres-backed requests cover 11th-request `429`, duplicate/validation consumption, cross-waitlist same-IP isolation, headers, public access, and the no-scope-creep configuration contract. |
| `SMP_POSTGRES_TEST_PASSWORD=profiletailors-test ./gradlew :server:smp:test --tests 'com.profiletailors.smp.leadcapture.infrastructure.configuration.WaitlistRateLimitConfigurationTest' --tests 'com.profiletailors.smp.integration.LocalAuthEndpointIntegrationTest.rejects invalid password' --tests 'com.profiletailors.smp.leadcapture.integration.WaitlistRateLimitIntegrationTest' --rerun-tasks` | PASS | Verification rerun: `BUILD SUCCESSFUL in 1m 14s`; confirms SMP shared rate-limit binding, the prior auth test pollution regression, and DALLAY-440 waitlist runtime behavior in one focused server execution. |
| `git diff --check` | PASS | Re-run after focused tests; no whitespace errors in the working tree. |

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

None.

### WARNING

None.

### SUGGESTION

1. Future phases should complete the remaining 15 change tasks before treating the full reusable waitlist capability as delivered; they are out of this DALLAY-440 verification scope.

## Final Verdict

PASS
