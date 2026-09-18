# Proposal: Secure Invitation Token Lifecycle

## Intent

Close bearer-handoff, metric-cardinality, CAS, and test-evidence gaps in the otherwise proven invitation token path so accept is hash-only, leak-free, expiring, and exactly-once.

## Scope

### In Scope
- Hash-only persistence proof: no raw token on `invitations`, events carry no new bearer surface
- `recordBulkInvite` low-cardinality fix (aggregate counts, no per-value tags)
- Honor discarded `updateIfVersionMatches` boolean in waitlist re-invite path; use `revoke()`
- Security + concurrency tests: hash-only, expiry/revoked/consumed rejection, replay, exactly-one-winner, no-bearer audit/log/metric assertions
- Explicit accept-attempt throttle decision (BCrypt ~100ms/amplification)

### Out of Scope
- Token-infra redesign (peppered HMAC, single-statement consume, pepper rotation) — deferred
- Generator change (`SecureRandom` 32B url-safe proven) — untouched
- Coordinator validation + version-CAS rewrite — reused as-is
- General notification contract redesign (DALLAY-565 owned)

## Capabilities

### New Capabilities
- None

### Modified Capabilities
- `invitations`: reconcile `InvitationIssued`-without-raw-token rule with ADR-0020 interim handoff; add token-lifecycle, CAS-honoring, low-cardinality observability requirements
- `email-notifications`: scope persisted token-bearing `acceptUrl` as accepted delivery-surface debt or redact to template-params + delivery key

## Approach

Keep generation, BCrypt+SHA-256 storage, coordinator validation, version-CAS. Scoped hardening + minimal sealed-handoff slice: fix metrics and CAS-ignore first; resolve spec-vs-code contradiction by either redacting `acceptUrl` from persisted payload or documenting it as delivery-surface debt with DALLAY-565 owners; never add new bearer surface. Add regression tests mirroring password-reset atomic-consume and hash-only patterns.

| Option | Verdict | Why |
|---|---|---|
| Scoped hardening, transient event handoff | Base | Smallest blast radius; matches ADR-0020 interim stance |
| Sealed delivery (remove rawToken, redact acceptUrl) | Scoped slice | Ends spec contradiction; needs DALLAY-565 contract sign-off |
| Storage/verify upgrade (HMAC, single-statement) | Deferred | Out of scope; pepper rotation is new operational surface |

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `platformadmin/.../InvitationIssued.kt`, `DirectInvitationResent.kt` | Modified | Scope or remove rawToken bearer field |
| `shared/notifications/.../InvitationEmail.kt`, `InvitationCreated.kt`, `InvitationResent.kt` | Modified | Redact or scope persisted token-bearing acceptUrl |
| `platformadmin/.../observability/InvitationObservability.kt` | Modified | Aggregate bulk counts, drop per-value tags |
| `platformadmin/.../handler/InviteWaitlistEntryHandler.kt` | Modified | Honor CAS boolean; use `revoke()` |
| `platformadmin/.../InvitationActivationCoordinator.kt` | Unchanged | Reused validation/CAS core |
| `openspec/specs/invitations/spec.md`, `email-notifications/spec.md` | Modified | Delta specs reconciling handoff rule |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Spec-vs-code contradiction fails verify | High | Resolve explicitly in spec delta, not silently |
| Persisted acceptUrl bearer overlooked | Med | Scope precisely; redact or document with owner sign-off |
| #660 interface drift | Med | Confirm contract before spec phase |
| Dashboard breakage from metric change | Low | Coordinate with observability owners |
| Missing RFC 12/14/40/43/44 clauses | Med | Link or inline clauses; no assumptions |

## Rollback Plan

Revert delta specs + code in one change; generator/storage/coordinator untouched so rollback is spec-only plus the two isolated fixes (metrics, CAS).

## Dependencies

- DALLAY-565 owners: notifications event/payload contract decision
- #660 interface confirmation; RFC clauses located or inlined

## Success Criteria

- [ ] No raw token in persisted state, logs, metrics, audit (test-proven)
- [ ] Bulk metrics low-cardinality; CAS boolean honored
- [ ] Concurrent accept yields exactly one winner (invitation-layer test)
- [ ] Spec and code agree on handoff; no new bearer surface
