# Proxy throttle by session (disc-001 follow-up, option A)

Anonymous img tags carry no identity because pt_refresh is scoped to
Path=/api/auth. Copy the invitation-accept pattern (RateLimit port plus
principal-scoped key) to the media proxy.

## Route

Delegated direct. Same branch fix/security-audit-run-1. TDD fail-first.

## Changes

1. `RefreshSessionConfigurationProperties.kt` — default cookiePath
   `/api/auth` becomes `/api`. Env override untouched.
2. `application.yaml` — default cookie-path `/api/auth` becomes `/api`.
3. `AuthRateLimitWebFilter.kt` — inject RefreshSessionGateway plus
   RefreshSessionProperties. Proxy path only: resolve pt_refresh cookie to
   RefreshSessionToken, call requireActive, throttle by
   proxy-session principalId with a generous budget. Missing, malformed or
   inactive session falls back to a strict proxy-ip bucket. Auth and waitlist
   paths keep current behavior untouched.
4. Tests — fail-first: valid session cookie gets the generous budget and
   isolates principals; absent or forged cookie lands in the strict bucket.

## Non-goals

No auth decision changes. The proxy stays permitAll. No frontend change;
browsers send the cookie automatically once the path covers it. No signed
URL issuance. No shared state yet; Redis stays a disc-002 follow-up.

## Risks

Per-image session lookup costs one keyed read plus a hash check on hit.
Browser caching bounds the rate. Random cookie values fail closed into the
strict IP bucket, so rotation buys no evasion beyond IP rotation.
Cookie scope widening sends the refresh token to all API paths; it stays
HttpOnly, Secure and SameSite Lax, and no endpoint logs cookie values.
Refresh origin validation is unchanged.

## Gates

Focused filter tests plus Detekt plus the security BDD lane.
