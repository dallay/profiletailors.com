# Design: Secure Invitation Token Lifecycle

## Technical Approach

Reuse proven path end-to-end: `InvitationTokenGenerator` (SecureRandom 32B url-safe), BCrypt+SHA-256
storage (`candidate_key` lookup + `token_hash` verify), `InvitationActivationCoordinator`
validation + version-CAS inside `AtomicTransactionRunner` + `FOR UPDATE`. This change fixes two
isolated defects (bulk-metric cardinality, discarded CAS boolean), scopes the bearer handoff as
signed interim debt, and adds regression evidence. No generator/coordinator/storage rewrite; no
general token-infra redesign (HMAC/single-statement deferred per proposal).

## Architecture Decisions

| Option                                                                           | Tradeoff                                                                   | Decision                                                                                                                                                                             |
|----------------------------------------------------------------------------------|----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Keep `InvitationIssued.rawToken` in-memory only vs sealed delivery now           | Sealed ends contradiction but breaks DALLAY-565 contract without owners    | Interim in-memory handoff as scoped debt; sealed delivery owned by DALLAY-566                                                                                                        |
| Redact persisted `acceptUrl` now vs scope as debt                                | Redact now is safest but changes notification payload contract             | Canonical target: template-params + delivery key only; interim token-bearing `acceptUrl` requires DALLAY-565 owner sign-off below, removal tracked DALLAY-566, no new bearer surface |
| `recordBulkInvite` per-value tags vs aggregate counters                          | Per-value tags aid ad-hoc slicing but explode cardinality (~51 values/tag) | Aggregate counters only; outcome tag low-cardinality; drop numeric tags                                                                                                              |
| Ignore vs honor `updateIfVersionMatches` boolean in `InviteWaitlistEntryHandler` | Ignoring is simpler; honoring surfaces conflicts                           | Honor boolean; conflict throws; use domain `revoke()` not hand-built copy                                                                                                            |
| Accept-attempt throttle vs no-throttle                                           | BCrypt `matches` ~100ms/attempt is CPU-amplifiable; throttle adds state    | Bounded per-key+IP attempt throttle at transport edge; coordinator stays pure                                                                                                        |

## Data Flow

```
issue → save(hash material) → publish(InvitationIssued*) ──AFTER_COMMIT──→ consumer
  renders acceptUrl in memory → persist Notification(template params + delivery key†)
  → dispatch email → recipient only

accept: findByCandidateKey[ForUpdate] → BCrypt matches → lifecycle/email check
  → provision → accept() → updateIfVersionMatches (CAS) → reconcile (inside tx)
```

`*` interim debt until DALLAY-566; `†` canonical target, interim `acceptUrl` only with sign-off.

Transaction/CAS ordering proof: `complete`/`activateForRegistration` re-resolve via
`findByCandidateKeyForUpdate` inside the atomic boundary; `prepare()` preflight is advisory only and
`completeLocked` re-validates token + lifecycle + context + email after acquiring the row lock,
closing preflight TOCTOU. Version-CAS (`updateIfVersionMatches`, `OptimisticLockException` on false)
makes exactly-once structural under concurrency: N racers, one CAS winner. Membership `reconcile`
and waitlist `convert` stay inside the same `AtomicTransactionRunner` boundary. ADR-0020 (invitation
aggregate boundary) / ADR-0021 (operational-event safety) cited, not relitigated.

## File Changes

| File                                                                                                                                                  | Action    | Description                                                                                                                                                                               |
|-------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `platformadmin/infrastructure/observability/InvitationObservability.kt`                                                                               | Modify    | `recordBulkInvite`: increment four fixed outcome series (`requested`, `invited`, `skipped`, `failed`) on the single bulk metric; batch size carried by `requested`; remove per-value tags |
| `platformadmin/application/handler/InviteWaitlistEntryHandler.kt`                                                                                     | Modify    | Honor CAS boolean (conflict on false); replace manual REVOKED copy with `revoke()`                                                                                                        |
| `platformadmin/domain/InvitationIssued.kt`, `DirectInvitationResent.kt`                                                                               | Modify    | Document interim debt; no new fields; removal tracked DALLAY-566                                                                                                                          |
| `notifications/infrastructure/email/SendInvitationEmailConsumer.kt`                                                                                   | Modify    | Persist template-params + delivery key; render URL transiently                                                                                                                            |
| `shared/notifications/.../InvitationEmail.kt` (`toPayload`)                                                                                           | Modify    | Drop token-bearing `acceptUrl` from persisted payload (or scope per sign-off)                                                                                                             |
| `platformadmin/application/InvitationActivationCoordinator.kt`, `InvitationTokenGenerator.kt`, `BCryptTokenHasher.kt`, `R2dbcInvitationRepository.kt` | Unchanged | Reused as-is                                                                                                                                                                              |
| Accept transport (controller/gateway + BDD glue)                                                                                                      | Modify    | Per-key+IP attempt throttle returning safe codes; thin controllers, media type `application/vnd.api.v1+json`                                                                              |

## Interfaces / Contracts

No new public contracts. `InvitationTelemetry.recordBulkInvite(requested, invited, skipped, failed)`
signature unchanged; semantics become aggregate increments. CAS fix:

```kotlin
val ok = newInvitationRepository.updateIfVersionMatches(superseded)
if (!ok) throw OptimisticLockException()
```

Throttle reuses `InvitationRateLimitExceededException` shape at the accept edge.

## Testing Strategy

| Layer         | What to Test                                                                                                         | Approach                                                                                                                                 |
|---------------|----------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| Unit          | Hash-only (raw lookup returns nothing); CAS-false surfaces conflict; `revoke()` used; bulk emits aggregate-only tags | Mirror password-reset hash-only + `SendInvitationEmailConsumerTest` payload assertions                                                   |
| Integration   | Expiry/revoked/consumed/replay rejection with safe codes; no bearer in audit/log/metric payloads                     | `R2dbcInvitationRepositoryTest`, coordinator tests, Postgres                                                                             |
| E2E/BDD       | Concurrent accept exactly-one-winner at invitation layer; throttled accept returns safe code                         | Mirror `concurrent consumeAndUpdatePassword allows exactly one success`; `platform-admin.feature` replay-denied; `just backend-bdd-fast` |
| Observability | Dashboard coordination for renamed/dropped tags                                                                      | Confirm with observability owners before apply                                                                                           |

## Migration / Rollout

No migration. Metric tag removal is dashboard-breaking: coordinate owners pre-apply.
Persisted-payload redaction applies to new notifications only; existing rows untouched.

## Open Questions

- [x] DALLAY-565 owner sign-off: interim in-memory `rawToken` handoff + persisted token-bearing
  `acceptUrl` accepted as scoped delivery-surface debt, removal tracked DALLAY-566. Sign: repository
  owner (approved 2026-09-17, PR2 proceeds on interim-debt branch).
- [ ] DALLAY-566 confirms sealed-handoff ownership (generation, rotation, TTL, recipient binding,
  URL assembly)?
- [ ] Throttle bounds acceptable to security? PR2 records chosen bounds: 10 attempts per 10 minutes
  per candidateKey+IP, enforced at `InvitationAcceptanceController` before the handler runs;
  `InvitationActivationCoordinator` stays pure; denial reuses `InvitationRateLimitExceededException`
  shape mapped to 429 `INVITATION_RATE_LIMIT_EXCEEDED` with a static detail carrying no token
  material. PR3 (2026-09-18) test-locks the bounds
  (`accept throttle locks attempt bounds to ten per ten minutes` captures window + maxRequests),
  resolving the PR2 WARNING; security-owner acceptance of the bounds themselves still pending.
- [x] PR2 (2026-09-17) scope-branch confirmation: `InvitationIssued`/`DirectInvitationResent` keep
  existing `rawToken` fields with no new fields (interim in-memory handoff, removal DALLAY-566);
  `InvitationEmail.toPayload()` keeps scoped `acceptUrl` equal to the template-built URL while
  `SendInvitationEmailConsumer` renders the URL transiently via `render()`; persisted payload key
  set locked to template params plus scoped `acceptUrl` by regression tests.
- [ ] #660 interface drift confirmed none? PR3 (2026-09-18) in-repo evidence:
  `git log --all --grep="#660"` is empty; PR1 (#1076) and PR2 (#1088) diffs changed no public
  signatures (`recordBulkInvite` args unchanged, `updateIfVersionMatches` untouched, throttle added
  controller-local deps only). No drift detected in-repo — full owner confirmation still pending.
- [ ] PR1 (2026-09-17) pending: `recordBulkInvite` tag removal coordinated with observability
  owners — NOT yet confirmed; dashboard owners must approve before merge. PR3 (2026-09-18) in-repo
  evidence: repo-wide search finds no dashboard, alert, doc, or frontend consumer of the removed
  per-value tags (bulk metric names appear only in `InvitationObservability` + its test); external
  owner approval still pending. RFC 12/14/40/43/44
  clauses not located — linkage pending, no assumptions made.
- [x] PR3 (2026-09-18) reconciliation notes: delta `Bulk observability` wording fixed to the merged
  4-series shape (single bulk metric, `requested` carries batch size, no separate batch counter),
  resolving the PR1 WARNING; `REPLAYED` (`INVITATION_REPLAYED`, 409) is defined and mapped but never
  thrown — second accept yields `ALREADY_CONSUMED` (BDD-proven) and accept-path CAS loss yields 409
  via the `OptimisticLockException` mapping, so the delta scenario's safe-code set holds with no
  prod change and no silent contradiction.
