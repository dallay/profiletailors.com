# Fix disc-001 — harden public media proxy

Keep the proxy reachable for dashboard img tags while bounding abuse.

## Route

Delegated direct. User decision: harden-public, keep permitAll and img tags working.

## Problem

`GET /api/media/proxy` is anonymous by design. The BDD asserts 400 instead
of 401 for anonymous calls, and dashboard img tags cannot send bearer tokens.
Allowlist-only plus unthrottled plus reflected content-type enables anonymous
egress and cost burn with type confusion. Size and time bounds exist
(2MB per 10s) but no throttle, no explicit redirect policy, and any upstream
content-type is reflected.

## Invariant

Public fetch must be throttled, never follow unvalidated redirects, and only
serve image bytes.

## Changes

1. `platform/.../http/ImageProxyController.kt` — allowlist reflected
   content-type to image jpeg, png and gif; other types return 502.
2. `platform/.../http/WebFluxConfiguration.kt` — explicit no-follow for
   redirects on the proxy client.
3. `identity/.../security/AuthRateLimitWebFilter.kt` — include the proxy path
   with its own proxy-ip bucket and policy, reusing the in-process mechanism.

## Tests

- `ImageProxyControllerTest` — non-image upstream returns 502, image trio
  still returns 200.
- `AuthRateLimitWebFilterTest` — proxy burst over the limit returns 429.
- BDD `security-endpoint-authorization.feature` — unchanged 400-public
  scenario, still passing.

## Gates

- Focused controller plus filter tests.
- Detekt clean.
- BDD fast lane for the security feature.

## Evidence

- TDD red before the fix, green after; removing the fix breaks the new tests.
- Residual risks: shared-IP throttle weakness, signed-URL future work.
