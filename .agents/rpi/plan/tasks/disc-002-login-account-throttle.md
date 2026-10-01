# Login throttle by account (disc-002)

Extend the password-reset per-email pattern to login. Socket-IP throttle
collapses behind ingress; a per-account bucket survives shared egress.

## Route

Delegated direct. Same branch fix/security-audit-run-1. TDD fail-first.

## Changes

1. Identity application — new `LoginRateLimitExceededException`.
2. `IdentityProblemDetailsHandler.kt` — map it to 429 like the reset one.
3. `LoginUserHandler` — inject the `RateLimit` port, tryAcquire on
   `auth-login-email:{normalized}` with 10 per 15 minutes after
   normalization and before credential lookup, mirroring the reset handler.
   Register keeps IP-only throttling: rotating emails defeats per-email keys.

## Non-goals

No lockout semantics, no Redis, no response-shape change besides 429 after
the budget. In-process state keeps the single-instance caveat.

## Gates

Handler unit tests plus Detekt plus the security BDD lane.
