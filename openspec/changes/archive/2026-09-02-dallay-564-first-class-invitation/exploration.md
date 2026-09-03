## Exploration: DALLAY-564 — Model invitation as a first-class domain capability

### Current State

#### Formal status versus implemented source

Linear reports DALLAY-564 as **Backlog** and it still blocks DALLAY-565, DALLAY-566, DALLAY-567, and
DALLAY-568. The branch is at `main` commit `724f54df`; the only pre-existing worktree change is the
requested `.agents/skill-registry.md` modification. The repository already contains a substantial
invitation acceptance slice from the private-beta work (`5f484548`, `9d746033`), but that source is
not evidence that the standalone capability is complete or that the Linear issue is closed.

#### Domain model and lifecycle

`platformadmin.domain.Invitation` is a framework-free data class with:

- `InvitationSource.DIRECT` or `WAITLIST`;
- nullable `sourceReferenceId`, required only for `WAITLIST`;
- mandatory `workspaceId`, normalized target email, `issuedBy`, `createdAt`, and `expiresAt`;
- `tokenHash`, `InvitationStatus` (`ACTIVE`, `ACCEPTED`, `EXPIRED`, `REVOKED`);
- optional `acceptedAt` and `acceptedPrincipalId`.

Construction validates nonblank tenant/issuer/token values, lowercase-trimmed email, expiration after
creation, source/reference consistency, and acceptance metadata. `isActive(now)` treats an active row
at or after `expiresAt` as unusable. `accept(at, principalId)` records the accepting principal and
changes only semantic invitation state; it rejects expired, revoked, or already accepted invitations.

The model is incomplete against the issue and the DDD ADRs. It is not marked `@AggregateRoot`, its
`InvitationId` is not marked `@ValueObject`, and `InvitationStatus`/`InvitationSource` are not included
in `PlatformAdminMarkerCoverageTest`. There are no domain `revoke` or explicit `expire` transitions.
Expiration is computed during validation but is not materialized as `EXPIRED`; non-accepted statuses do
not reject stray acceptance metadata; and there is no aggregate-level version or event contract. The
model contains no delivery fields, which is the correct direction for DALLAY-564.

#### Persistence and schema

Liquibase already includes `platform-admin/004-create-invitations.yaml` and the master changelog
includes it after workspace and principal prerequisites. `invitations` persists the first-class fields,
plus a unique `candidate_key`, unique `token_hash`, workspace/issuer/accepted-principal foreign keys,
and an index on `(candidate_key, status)`. IDs are raw PostgreSQL UUIDs. `source_reference_id` is a
logical reference with no waitlist foreign key.

There are no database checks for source/reference consistency, normalized email, `expires_at >
created_at`, or accepted metadata. There is no active-email uniqueness constraint, no version column,
no token-hash index, and no expiration cleanup/materialization mechanism. The table is therefore a
usable storage base, not a complete persistence contract for creation, revocation, expiration, or
concurrent lifecycle operations.

`R2dbcInvitationAcceptanceRepository` only supports `findByCandidateKeyForUpdate` and a conditional
`markAccepted`. The `SELECT ... FOR UPDATE` and `UPDATE ... WHERE status = 'ACTIVE'` provide a useful
acceptance primitive when called inside `AtomicTransactionRunner`, but there is no general
`InvitationRepository` for the canonical aggregate and no persistence port for the remaining lifecycle
transitions.

#### Application commands and ports

The existing first-class acceptance path has:

- `AcceptInvitationCommand` and `AcceptInvitationHandler`;
- `InvitationAcceptanceRepository`, `TokenHasher`, and `InvitationTokenCandidateKey` ports;
- identity lookup, tenancy membership reconciliation, and `AtomicTransactionRunner` dependencies;
- `InvitationRegistrationGateway`, used by `RegisterUserHandler` for invite-only registration.

The handler validates the candidate key, hash, active/unexpired state, authenticated user identity,
normalized email, and user principal type, then reconciles membership from the persisted invitation
workspace and conditionally accepts the invitation in one transaction. The registration adapter
duplicates most of this validation and mutation logic rather than reusing one canonical acceptance
use case.

No first-class `CreateInvitationCommand`, `RevokeInvitationCommand`, `ExpireInvitationCommand`, or
canonical write/read repository exists. The current `InviteWaitlistEntryCommand`, resend command, and
revoke command operate on the legacy `WaitlistInvitation` aggregate only. Those operational commands
belong to downstream issues, not to the DALLAY-564 model exploration.

#### HTTP and API exposure

`POST /api/invitations/accept` exists and returns only `workspaceId` and `membershipStatus`; it requires
an authenticated principal and accepts `{ "token": "..." }`. Registration also accepts an optional
`invitationToken` and uses the first-class `invitations` table through the registration gateway. These
are acceptance integrations already present in source, principally relevant to DALLAY-567 and the
DALLAY-556 journey.

The admin API is not first-class yet. `POST /api/admin/waitlist-entries/{entryId}/invitations`, resend,
revoke, `R2dbcAdminInvitationQuery`, `AdminInvitationSummary`, and waitlist detail history all use
`WaitlistInvitation`. `AdminInvitationSummary` requires `waitlistEntryId` and exposes
`deliveryStatus`/`deliveryAttemptCount`, so it cannot represent a direct invitation and is not a
canonical Invitation read model. There is no `POST /api/admin/invitations` direct-create endpoint.
Admin controllers inject handlers directly and existing invitation mappings do not declare the usual
versioned endpoint metadata, both of which conflict with the repository's API/CQRS guidance but are
outside the DALLAY-564 domain-only acceptance criteria.

#### Token and security ownership

`InvitationTokenGenerator` uses `SecureRandom.getInstanceStrong()` and 32 bytes encoded as unpadded
base64url. `BCryptTokenHasher` stores a BCrypt hash and derives a SHA-256 candidate key for lookup.
The first-class schema therefore avoids storing the raw token, and the acceptance path checks the
presented token against the stored hash.

Concrete token ownership is not separated yet: the generator and hasher live in platformadmin and the
current issuance code is only the waitlist handler. The old notification events
`InvitationCreated`/`InvitationResent` carry `rawToken` and a token-bearing `acceptUrl`; the
`Notification.payload` currently persists that URL as JSONB. This conflicts with the stricter
DALLAY-566 and DALLAY-565 boundary that raw or directly recoverable bearer values must not cross a
durable event, persistence, audit, log, or metric boundary. DALLAY-564 should define the canonical
non-secret invitation persistence/state contract, while DALLAY-566 owns generation, hashing/candidate
lookup, expiration enforcement, recipient binding, raw-token handling, and concurrency-safe
consumption mechanics. DALLAY-564 must not invent a second token subsystem.

#### Audit and observability

The legacy waitlist handlers publish `WAITLIST_ENTRY_INVITED`, `INVITATION_RESENT`, and
`INVITATION_REVOKED` admin audit events. First-class acceptance does not publish an admin audit event,
and `AdminAuditAction` has no `INVITATION_CREATED` or `INVITATION_ACCEPTED` action. The audit model
supports correlation identifiers but the repository does not persist its `metadata` map. No
first-class invitation lifecycle metric exists; platformadmin telemetry currently measures waitlist
queries, while dashboard invitation counts are hardcoded to zero. General observability documentation
does list `invitationId` as a pivot and forbids raw tokens/full emails, but the invitation acceptance
path has no dedicated low-cardinality outcome instrumentation.

#### Concurrency and acceptance behavior

The first-class acceptance handler uses the repository row lock and conditional active-state update,
and membership persistence has `UNIQUE(workspace_id, principal_id)`. This is a sound starting point
for one successful acceptance when all mutations share the explicit R2DBC transaction. Existing tests
cover conditional second updates, membership reconciliation, invalid identity, replayed semantic
state, and registration rollback for an invalid token.

There is no two-client concurrent acceptance integration test, no true race test through the registration
path, and no test proving exactly one account/membership/acceptance under contention. Expiration remains
an in-read check rather than a persisted state transition. The separate registration adapter and
direct acceptance handler also create two paths whose transaction and error behavior can drift.

#### Waitlist separation

The first-class model correctly allows a direct invitation with no waitlist reference and a waitlist
origin with an optional source reference. However, all current operator issuance, resend, revoke,
admin queries, delivery-state updates, and waitlist history still use the older `WaitlistInvitation`
aggregate and `waitlist_invitations` table. That model requires a waitlist entry, has a `SUPERSEDED`
state, and embeds delivery status, attempt timestamps, and counts. The repository therefore has two
invitation representations in one bounded context, and the current notification outcome bridge writes
back into the legacy invitation row.

The waitlist entry remains a separate lead-capture aggregate with its own `PENDING`, `INVITED`,
`CONVERTED`, and `CANCELLED` lifecycle. DALLAY-570 owns converting an eligible entry into the canonical
Invitation and tracking the entry-side conversion state. DALLAY-564 must not absorb waitlist entry
mutation or bulk conversion behavior.

#### Test coverage and gaps

Existing first-class tests cover basic construction, source/reference invariants, normalized email,
workspace presence, expiration boundary, accepted metadata, replay rejection, acceptance handler
identity matching, conditional persistence, and two registration endpoint cases. Legacy waitlist tests
cover its own lifecycle, optimistic version conflict, delivery fields, audit, and BDD operations.

Important gaps are:

- no aggregate/value-object marker coverage for `Invitation`, `InvitationId`, or its status/source;
- no explicit domain tests for revoke/expire transitions or invalid metadata on non-accepted states;
- no canonical repository save/read/update contract or schema constraint tests;
- no active normalized-email duplicate race test for direct invitations;
- no concurrent acceptance test proving exactly one success;
- no first-class acceptance audit or low-cardinality metric test;
- no test proving delivery failure leaves a first-class Invitation unchanged;
- no token-free durable event/notification-payload test for the current raw-token event path;
- no first-class direct-create/revoke API tests, which are assigned to DALLAY-568;
- no dedicated invitation Cucumber feature; the mixed `platform-admin.feature` scenarios mostly cover
  legacy waitlist operations plus acceptance;
- no test that an invitation is transitioned or cleaned up as `EXPIRED`.

#### Explicit Linear/code inconsistencies

1. Linear says DALLAY-564 is a standalone capability, but admin issuance and reads still target
   `WaitlistInvitation`; only acceptance currently uses `invitations`.
2. Linear says delivery state is outside Invitation; the first-class model follows that rule, but the
   active operational path and `AdminInvitationSummary` still make delivery state invitation-owned.
3. Linear lists `EXPIRED` as a semantic lifecycle state; code only computes expiration in `isActive` and
   never materializes the status.
4. Linear requires future invitation mutations to be auditable; first-class acceptance has no audit
   publication or accepted action, while only legacy waitlist mutations are audited.
5. Linear requires raw-token exclusion from persistence; the current notification event and persisted
   token-bearing accept URL violate the stronger token-safe boundary that DALLAY-566/565 now define.
6. Linear favors explicit later commands, but no canonical create/revoke commands exist and the only
   admin create route is waitlist-specific. DALLAY-568 owns those commands, so DALLAY-564 must not
   duplicate them merely to close the model gap.
7. `docs/infrastructure/private-beta-correlation-matrix.md` describes `waitlist_invitations`,
   `last_used_at`, and HMAC-SHA256 storage, while the current first-class schema is `invitations` with
   `accepted_at`, BCrypt `token_hash`, and a SHA-256 candidate key. `docs/architecture/c4/04-code.md`
   also names `platform_invitations`, not the migration's `invitations` table.
8. ADR-0015/0016/0017 require marker-driven DDD coverage, but the first-class Invitation types are
   unmarked and are omitted from `PlatformAdminMarkerCoverageTest`; ADR-0016's bootstrap list still
   names only `WaitlistInvitation`.

### Affected Areas

- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/Invitation.kt` — existing
  first-class model; lifecycle and DDD marker gaps.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/InvitationId.kt` — existing
  raw UUID identity without value-object marker or canonical identifier decision.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/InvitationTokenGenerator.kt`
  and `platformadmin/infrastructure/BCryptTokenHasher.kt` — current token mechanics that must be
  separated from DALLAY-564's semantic model.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/AcceptInvitation.kt` —
  existing acceptance command, transaction use, and narrow acceptance port.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/InvitationRegistrationGatewayAdapter.kt`
  and `identity/application/LocalAuthHandlers.kt` — duplicated registration acceptance path.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcInvitationAcceptanceRepository.kt`
  — first-class row-lock and conditional-consume adapter, but not a complete aggregate repository.
- `server/smp/src/main/resources/db/changelog/platform-admin/004-create-invitations.yaml` — existing
  first-class schema and missing constraints/index/version/expiry support.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/{application,infrastructure}/**`
  — legacy waitlist command, query, HTTP, and delivery-state ownership that downstream work must
  reconcile without duplicating the canonical model.
- `server/smp/src/main/kotlin/com/profiletailors/smp/notifications/infrastructure/email/SendInvitationEmailConsumer.kt`
  and `shared/notifications/src/main/kotlin/com/profiletailors/notifications/domain/{InvitationEmail.kt, event/*}`
  — current raw-token event and token-bearing notification payload boundary; DALLAY-565/566 concern.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/AdminAuditEvent.kt` and
  `R2dbcAdminAuditRepository.kt` — existing audit seam and missing first-class acceptance coverage.
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/{domain,application,infrastructure,integration}/**`
  — current first-class and legacy tests; concurrency, lifecycle, audit, and security gaps.
- `server/smp/src/test/resources/features/platform-admin.feature` — mixed acceptance/legacy feature;
  no dedicated canonical invitation feature.
- `docs/architecture/adr/{0001,0002,0004,0005,0008,0015,0016,0017,0019}-*.md`,
  `docs/architecture/transaction-policy.md`, and `docs/architecture/data-model/README.md` — module,
  layer, CQRS, identity, DDD, transaction, schema, and cross-context ownership constraints.
- `openspec/specs/{lead-capture-waitlist,registration,email-verification,email-notifications,iam}/spec.md`
  and `openspec/changes/private-beta-launch-readiness/**` — existing waitlist, registration,
  verification, email, acceptance, and evidence contracts.

### Approaches

1. **Canonicalize the existing Invitation with a compatibility boundary** — Complete the existing
   `Invitation` model, mark it under the DDD contracts, make its semantic lifecycle and conditional
   state transitions explicit, and provide the canonical repository/application contract needed by
   DALLAY-567/566/568. Keep legacy `waitlist_invitations` readable until DALLAY-570 and DALLAY-565
   define their migrations; do not move notification delivery into the aggregate.
   - Pros: preserves the already-landed table and acceptance work; smallest reversible change; gives
     downstream issues one stable source of truth; avoids a destructive waitlist migration.
   - Cons: temporarily retains two invitation tables and requires explicit legacy/new-flow rules;
     token ownership and expiration enforcement still cross the DALLAY-566 boundary.
   - Effort: Medium

2. **Replace WaitlistInvitation wholesale with Invitation in DALLAY-564** — Migrate all waitlist
   issuance, resend, revoke, admin reads, delivery state, and acceptance paths to one table/model now.
   - Pros: removes duplicate invitation semantics immediately and produces a visually clean bounded
     context.
   - Cons: exceeds the Linear scope; overlaps DALLAY-565, DALLAY-566, DALLAY-567, and DALLAY-570;
     risks destructive schema/API changes and legacy operational-data loss; would force delivery and
     waitlist decisions before their owning issues.
   - Effort: High

3. **Treat the current first-class acceptance slice as complete** — Close DALLAY-564 around the
   existing data class, table, and acceptance handler and let downstream issues build around it.
   - Pros: no immediate production change.
   - Cons: leaves the aggregate unguarded, expiration/audit/command/persistence gaps unresolved, and
     forces DALLAY-565/566 to build against two competing models and an unsafe token boundary.
   - Effort: Low now, High rework later

### Recommendation

Use Approach 1. DALLAY-564 should own the canonical Invitation semantic contract: aggregate identity,
direct/waitlist source relation, workspace and normalized target binding, issuer/timestamps, semantic
statuses, acceptance metadata, domain invariants, and the persistence/application port that gives
downstream flows a conditional, concurrency-safe state transition. It should add focused domain,
repository/schema, and race-oriented contract tests and bring the model under ADR-0015/0016/0017.

DALLAY-564 should not own direct admin creation/revocation (DALLAY-568), waitlist conversion or entry
state (DALLAY-570), registration/account provisioning (DALLAY-567), email scheduling/delivery/read
composition (DALLAY-565), or the concrete secure token lifecycle (DALLAY-566).

DALLAY-565 must consume the canonical Invitation ID and keep notification attempts, status, timestamps,
failure reasons, and delivery summaries in Notifications. It must not add delivery fields to Invitation,
target `WaitlistInvitation`, create a second invitation table, or persist raw/token-bearing values.

DALLAY-566 must own secure token generation, URL-safe encoding, hash/candidate lookup, raw-token
ephemeral handling, recipient binding, expiration enforcement, and exactly-once concurrent consumption,
using DALLAY-564's state-transition contract. It must not create a second Invitation aggregate or put
delivery state in Invitation. The concrete ephemeral token handoff must remain a hard gate for the
notification implementation, as already recorded in the DALLAY-565 artifacts.

The proposal should explicitly decide whether the existing raw UUID `InvitationId` is retained as a
documented infrastructure exception or converted to the repository's prefixed public-ID convention,
and whether expiration is materialized by a command/scheduler or remains a computed validity projection.
Neither decision should be hidden inside DALLAY-565 or DALLAY-566.

### Risks

- Continuing to use both `Invitation` and `WaitlistInvitation` without a migration/compatibility rule
  will produce divergent status, resend, audit, and admin-read semantics.
- The current notification event and JSONB payload carry a recoverable bearer URL; downstream delivery
  work must not treat the existing event shape as an approved security contract.
- Row locking and conditional update look correct for one acceptance path, but the absence of a real
  concurrent integration test leaves the exactly-once claim unproven.
- Controller-level `@Transactional` and direct handler injection remain existing transaction/CQRS
  risks for legacy waitlist operations; DALLAY-564 should use the explicit transaction port for its
  canonical state transitions and avoid inheriting those patterns.
- The existing `invitations` schema has no active-email uniqueness, lifecycle checks, or version field;
  duplicate direct creation and state-race guarantees must be assigned explicitly to DALLAY-568 and
  the DALLAY-564 persistence contract rather than assumed.
- Current architecture and operational documents are stale around table names, expiry fields, and hash
  algorithms; implementation without documentation reconciliation will mislead operators and future
  phases.

### Ready for Proposal

Yes. The current source is sufficient to write a scoped proposal, with Approach 1 as the baseline and
the token-handoff details intentionally deferred to DALLAY-566. The proposal must state that the
existing acceptance slice is partial implementation evidence, not a closed DALLAY-564 capability, and
must preserve the requested `.agents/skill-registry.md` and unrelated worktree state.
