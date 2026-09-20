## Exploration: Secure invitation token lifecycle (issue #662)

### Current State
Issuance, storage, validation, expiration, and single-use consumption for canonical
`Invitation` already exist and are partially proven. The remaining work for #662 is
closing the bearer-handoff, cardinality, and test-evidence gaps — not building a token
subsystem from scratch.

- **Generation** (`platformadmin/domain/InvitationTokenGenerator.kt`): `SecureRandom.getInstanceStrong()`,
  32 bytes, base64url-no-padding → URL-safe, 43 chars. Covered by
  `InvitationTokenGeneratorTest` (charset, length, uniqueness). No change expected.
- **Storage** (`db/changelog/platform-admin/004/005`, `R2dbcInvitationRepository.kt`):
  `candidate_key` (SHA-256 hex of raw, unique, indexed) for lookup + `token_hash`
  (BCrypt, unique) for verification. Raw token is never persisted on `invitations`.
  `chk_invitations_token_material` enforces non-blank material. Reference contrast:
  password-reset/email-verification use pure SHA-256 hash-only with single-statement
  atomic consume (`UPDATE … WHERE used_at IS NULL AND expires_at > :now`).
- **Validation/acceptance** (`InvitationActivationCoordinator.kt`, `AcceptInvitation.kt`):
  `findByCandidateKey[ForUpdate]` (`SELECT … FOR UPDATE`) → BCrypt `matches` →
  lifecycle check (`ACTIVE` + `Clock`-based exclusive expiry) → email match → provision →
  `invitation.accept()` → `updateIfVersionMatches` (optimistic version CAS). Both entry
  points (`AcceptInvitationHandler`, `InvitationRegistrationGatewayAdapter`) run inside
  `AtomicTransactionRunner`; `prepare()` re-validates in `completeLocked`, closing the
  preflight TOCTOU. Failure codes are safe (`INVALID`/`EXPIRED`/`REVOKED`/
  `ALREADY_CONSUMED`/`REPLAYED`/`EMAIL_MISMATCH`); controller returns workspace +
  membership only (BDD asserts no token/email in response).
- **Concurrency evidence on hand**: `R2dbcInvitationRepositoryTest` (one success + replay
  rejected), `LocalAuthHandlersTransactionPostgresIntegrationTest.concurrent registration
  with one invitation produces one winner`, BDD `platform-admin.feature` replay-denied
  scenario. Password-reset has the cleanest exactly-once pattern
  (`R2dbcPasswordResetTokenRepositoryTest.concurrent consumeAndUpdatePassword calls allow
  exactly one success`).
- **Known bearer-handoff debt**: `InvitationIssued.rawToken` and
  `DirectInvitationResent.rawToken` carry the raw token handler → event →
  `SendInvitationEmailConsumer`, which renders the accept URL. `openspec/specs/invitations/spec.md`
  ("No raw token in InvitationIssued event") forbids this; ADR-0020's addendum explicitly
  marks the handoff "temporary and non-canonical until DALLAY-566 supplies the token-safe
  replacement" and notes the persisted notification payload carries the token-bearing
  `acceptUrl` as the current delivery surface. `InvitationEmail.toPayload()` correctly
  excludes a `rawToken` key (test-enforced) but persists `acceptUrl` embedding the raw
  token in the `notifications` row. Shared events `InvitationCreated`/`InvitationResent`
  also carry `rawToken`.
- **Audit/logs**: `AdminAuditEvent` carries IDs/actions only; `InvitationAccepted`
  listener emits target/outcome/timestamps; consumer logs invitationId + idempotency key.
  No token in logs found. ADR-0021's sensitive-key sanitizer covers token families at the
  operational-event boundary.
- **Metrics cardinality violation**: `InvitationObservability.recordBulkInvite` tags
  `requested/invited/skipped/failed` with raw integer values (up to 51 distinct values
  per tag) — high-cardinality, against the issue's aggregate-counts-only rule. All other
  counters are low-cardinality (one `target` tag on accepted).
- **Dropped CAS result**: `InviteWaitlistEntryHandler` INVITED branch calls
  `newInvitationRepository.updateIfVersionMatches(superseded)` and discards the boolean —
  concurrent re-invites can silently diverge. It also hand-builds a REVOKED copy instead
  of `revoke()`.
- **Acceptance-rate limiting**: resend has `InvitationRateLimitExceededException`; no
  acceptance-attempt throttle was found (BCrypt `matches` ≈ 100 ms per attempt → DoS
  amplification surface worth a proposal decision, not an explore assumption).

### Affected Areas
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/InvitationIssued.kt` — rawToken bearer field; spec-forbidden, ADR-marked temporary
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/DirectInvitationResent.kt` — same bearer field + token-bearing acceptUrl
- `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/infrastructure/email/SendInvitationEmailConsumer.kt` — after-commit consumer; renders/sends raw token; logs are clean
- `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/InvitationEmail.kt` — `toPayload()` persists token-bearing `acceptUrl`; `rawToken` correctly excluded
- `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/event/InvitationCreated.kt`, `InvitationResent.kt` — rawToken in shared contract
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/observability/InvitationObservability.kt` — `recordBulkInvite` high-cardinality tags
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/InviteWaitlistEntryHandler.kt` — discarded CAS boolean (line ~92), manual REVOKED copy
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/InvitationActivationCoordinator.kt` — validation/CAS core; candidateKey cast-fail path; reconcile-after-CAS ordering
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/BCryptTokenHasher.kt` — BCrypt + SHA-256 pair; pepper/none, cost, timing notes for proposal
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcInvitationRepository.kt` — FOR UPDATE + version CAS; resend rotates candidateKey+hash
- `server/smp/src/main/resources/db/changelog/platform-admin/004-create-invitations.yaml`, `005-harden-invitations.yaml` — unique token_hash on salted BCrypt is vacuous; candidate_key uniqueness is the real guard
- `openspec/specs/invitations/spec.md` — Token-ownership / safe-audit requirements already constrain the design; proposal must reconcile the `InvitationIssued`-without-raw-token rule with the live code
- `openspec/changes/dallay-565-invitation-notification-integration/`, `dallay-567-accept-invitations-registration-flow/`, `dallay-568-direct-invitation-admin-commands/`, `dallay-565/` — reuse notification idempotency keys, coordinator, acceptance BDD; do not duplicate
- `docs/architecture/adr/0020-first-class-invitation-aggregate.md`, `0021-operational-event-safety-boundary.md` — token-boundary ownership and log-sanitizer decisions to cite, not relitigate
- Tests: `R2dbcPasswordResetTokenRepositoryTest` (atomic-consume reference), `LocalAuthHandlersTransactionPostgresIntegrationTest`, `R2dbcInvitationRepositoryTest`, `InvitationActivationCoordinatorTest`, `platform-admin.feature`, `SendInvitationEmailConsumerTest`

### Approaches
1. **Scoped hardening, keep transient event handoff** — fix metrics cardinality, fix discarded CAS result, keep `rawToken` in in-memory events only with proof it never persists (except the acknowledged `acceptUrl` delivery surface), add security + concurrency tests.
   - Pros: smallest blast radius; no contract breakage for notifications; matches ADR-0020's interim stance; fits "no general token infra redesign" out-of-scope.
   - Cons: leaves spec-vs-code contradiction (`InvitationIssued` must not carry raw token) unresolved; `acceptUrl` bearer persists in `notifications` rows; reviewer may reject as incomplete for #662.
   - Effort: Medium
2. **Token-safe handoff (sealed delivery)** — remove `rawToken` from `InvitationIssued`/`DirectInvitationResent`/shared events; issuance boundary renders the accept URL once and passes a sealed/opaque delivery reference (or encrypts to the consumer); redact `acceptUrl` from persisted notification payload (store template params + delivery key only).
   - Pros: satisfies spec requirement and issue's "raw only to recipient" domain rule end-to-end; eliminates bearer from event bus and notification store; strongest audit story.
   - Cons: changes the notifications contract (DALLAY-565 surface); needs key/seal management or a delivery-record design; resend rotation must re-seal; larger migration + BDD surface.
   - Effort: High
3. **Storage/verify upgrade (peppered HMAC + single-statement consume)** — replace BCrypt+SHA-256 pair with peppered HMAC-SHA-256 candidate+verifier (or keep BCrypt, drop separate candidate key via truncation scheme), and mirror the password-reset single-`UPDATE … WHERE status='ACTIVE' AND expires_at > now` conditional consume to make exactly-once structural rather than read-then-CAS.
   - Pros: removes BCrypt cost-DoS on acceptance; single-statement consume is simpler to prove under contention; kills the vacuous unique-BCrypt constraint question.
   - Cons: explicitly out of scope ("general token infra redesign"); pepper/secret rotation is new operational surface; migration of existing rows; highest risk for least issue-mandated gain.
   - Effort: High

### Recommendation
Propose Approach 1 as the base with a deliberate, scoped slice of Approach 2: keep generation, BCrypt+SHA-256 storage, coordinator validation, and version-CAS exactly as-is (all proven), but (a) fix `recordBulkInvite` to aggregate counts without per-value tags, (b) honor the discarded CAS boolean in the waitlist re-invite path, (c) decide explicitly — with DALLAY-565 owners — whether the persisted `notifications.acceptUrl` bearer is accepted delivery-surface debt (documented, like ADR-0020) or redacted in this change, and (d) back it with automated tests: hash-only persistence (lookup by raw returns nothing, mirroring the password-reset test), expiry/revoked/consumed rejection, replay-after-accept, concurrent-accept exactly-one-winner at the invitation layer, and audit/log/metric payload assertions proving no token or high-cardinality dimensions. Defer Approach 3 entirely; note BCrypt-cost rate-limiting as a proposal-level decision. Reconcile the `InvitationIssued`-without-raw-token spec line with reality in the proposal (either schedule the sealed handoff or amend the spec) — do not silently leave spec and code contradicting each other.

### Risks
- Spec-vs-code contradiction on `InvitationIssued.rawToken` will fail verification if the proposal does not explicitly resolve it (spec change or sealed handoff).
- Persisted `notifications` payload with token-bearing `acceptUrl` is the largest residual bearer store; any claim of "hash-only" must scope it precisely.
- `candidate_key` is an unsalted deterministic SHA-256 of a 256-bit secret — safe only because entropy is high; proposal must not weaken generator entropy while touching this area.
- `updateIfVersionMatches` CAS + `FOR UPDATE` + provision-before-CAS ordering needs a transaction-boundary proof for the concurrent-accept claim; membership reconcile placement must stay inside the atomic boundary.
- Bulk-invite metric tag fix changes dashboards; coordinate with observability owners.
- Dependency #660 (unresolved in this exploration — no issue-tracker access) may own issuance/acceptance flow pieces this change assumes; proposal must confirm the interface contract before apply.
- RFC 12/14/40/43/44 cited by the issue were not found in `docs/`; proposal must link or inline the applicable clauses instead of assuming them.

### Ready for Proposal
Yes — scope is mappable to a tight proposal: reuse generator/storage/coordinator/CAS, fix metrics + CAS-ignore, resolve the event-handoff contradiction, and add security/concurrency tests. The orchestrator should confirm (1) whether #662 may touch the notifications event/payload contract or must treat it as owned by DALLAY-565, (2) what #660 delivers and its interface, and (3) where the cited RFC clauses live.
