# Invitations Specification

## Purpose

`Invitation` is the canonical standalone authorization to register. It owns identity, source,
target, semantic lifecycle, and acceptance facts—not waitlist entries, provisioning, notifications,
or bearer-token handoff. DALLAY-568 owns admin commands; DALLAY-570 conversion; DALLAY-567
provisioning; DALLAY-565 notifications; DALLAY-566 concrete token generation, hashing, lookup,
enforcement, and handoff. DALLAY-564 MUST NOT invent those implementations, endpoints, or an
additional aggregate.

## Requirements

### Requirement: DDD markers and identity

`Invitation` MUST be `@AggregateRoot`. `InvitationId`, `InvitationStatus`, and `InvitationSource`
MUST be immutable `@ValueObject` types. `InvitationId` remains UUID-backed and persists as raw
PostgreSQL `uuid`; aggregate references MUST be identities only.

#### Scenario: Marker coverage

- GIVEN platform-admin production types
- WHEN DDD tests inspect invitation types
- THEN markers, immutability, and UUID identity MUST be verified

### Requirement: Construction and source invariants

Construction MUST reject blank workspace/issuer identities, non-normalized trimmed-lowercase target
email, blank opaque token material, and `expiresAt <= createdAt`. `DIRECT` MUST have no reference;
`WAITLIST` MUST have a nonblank logical waitlist-entry reference.

#### Scenario: Invalid invitation fails

- GIVEN an invalid field or source/reference combination
- WHEN an invitation is constructed
- THEN construction MUST fail before persistence

### Requirement: Semantic lifecycle

Statuses MUST be exactly `ACTIVE`, `ACCEPTED`, `EXPIRED`, and `REVOKED`. Only `ACTIVE` MAY transition
to another state; terminal states MUST reject mutation. Status MUST NOT contain delivery fields.

#### Scenario: Delivery is independent

- GIVEN an active invitation and notification failure
- WHEN Notifications records that failure
- THEN invitation status and semantic fields MUST remain unchanged

### Requirement: Explicit expiration

`expiresAt` is exclusive: `ACTIVE` is unusable at or after the boundary. Reading an expired active
row MUST NOT write persistence. Only explicit `expire(at)` MAY materialize `EXPIRED`, at or after
the boundary; scheduling and cleanup are outside scope.

#### Scenario: Expired read does not mutate

- GIVEN an active row where `now >= expiresAt`
- WHEN it is read without `expire(at)`
- THEN it remains stored as `ACTIVE` but MUST be unusable

### Requirement: Acceptance metadata

`ACCEPTED` MUST contain both `acceptedAt` and `acceptedPrincipalId`; every other status MUST contain
neither. `accept(at, principal)` MUST preserve all other invitation facts.

#### Scenario: Acceptance records one principal

- GIVEN an active, unexpired invitation
- WHEN principal `P` accepts at `at`
- THEN exactly `at` and `P` MUST be recorded

### Requirement: Canonical repository transitions

A framework-free `InvitationRepository` MUST provide aggregate reads/writes and conditional lifecycle
transitions. Adapters MUST map the `invitations` schema and report success only when the expected
current state changed; handlers MUST use the port.

#### Scenario: Stale transition is rejected

- GIVEN a transition expects `ACTIVE` but storage is `REVOKED`
- WHEN the conditional operation runs
- THEN no row MUST change and conflict/unavailable MUST be reported

### Requirement: One-time concurrent acceptance

The acceptance contract MUST atomically permit at most one `ACTIVE`→`ACCEPTED` transition per
invitation. Under contention exactly one caller MAY succeed; provisioning remains DALLAY-567.

#### Scenario: Concurrent clients contend

- GIVEN two clients accept one active invitation concurrently
- WHEN both transactions finish
- THEN exactly one succeeds and one accepted state persists

### Requirement: Schema protections

The schema MUST enforce UUID identity, required fields, source/reference consistency, normalized
email, `expires_at > created_at`, accepted-metadata consistency, unique opaque lookup/token material,
and at most one active invitation per workspace and normalized target email. It MUST NOT persist raw
tokens or add delivery columns.

#### Scenario: Impossible row is rejected

- GIVEN partial acceptance metadata or invalid source/reference
- WHEN PostgreSQL receives the write
- THEN the write MUST be rejected

### Requirement: Safe audit and observability

Lifecycle evidence MUST use low-cardinality invitation ID, status, outcome, timestamps, and correlation
data. Raw tokens, token-bearing URLs, and full target emails MUST NOT cross invitation, audit, log,
or metric boundaries; downstream owners publish their events.

#### Scenario: Evidence contains no bearer

- GIVEN a lifecycle transition succeeds
- WHEN audit or metrics evidence is emitted
- THEN it MUST identify the invitation without bearer or full-email values

### Requirement: Token ownership

Invitation MAY persist only non-reversible token material and opaque lookup data required by DALLAY-566.
It MUST NOT define token algorithms, raw-token handoff, URL construction, or delivery behavior.

#### Scenario: Notification failure stays external

- GIVEN DALLAY-565 cannot deliver
- WHEN its failure is recorded
- THEN no Invitation field or semantic status MUST change

### Requirement: Legacy compatibility

`WaitlistInvitation`, `waitlist_invitations`, legacy commands, queries, history, and delivery bridge
MUST remain usable until DALLAY-565/570 define migration. DALLAY-564 MUST NOT drop, rename,
backfill, or substitute those flows.

#### Scenario: Legacy flow remains separate

- GIVEN an existing legacy waitlist invitation
- WHEN its operator flow reads or updates it
- THEN it MUST use its own aggregate and delivery fields

### Requirement: Documentation and strict TDD

Implementation MUST document UUID, explicit expiry materialization, ownership, and compatibility in
affected architecture, data-model, operational, and OpenSpec documents. Strict TDD MUST add failing
domain/marker tests before production changes, then port, schema, and PostgreSQL contention tests.
This specification authorizes no production implementation.

#### Scenario: Contract evidence is reviewable

- GIVEN the change is reviewed
- WHEN artifacts and test history are inspected
- THEN boundaries and the red/green sequence MUST be verifiable

### Requirement: InvitationTarget models two distinct onboarding paths

Every `Invitation` has a `target: InvitationTarget` field:

```kotlin
enum class InvitationTarget {
    EXISTING_WORKSPACE   // invitee joins an existing workspace
    NEW_WORKSPACE        // invitee provisions a new workspace on acceptance
}
```

**Lifecycle-aware invariants enforced in aggregate init:**

| target | status | workspaceId |
|--------|--------|------------|
| `EXISTING_WORKSPACE` | any | `!= null` (always required) |
| `NEW_WORKSPACE` | `ACTIVE`, `EXPIRED`, `REVOKED` | `== null` |
| `NEW_WORKSPACE` | `ACCEPTED` | `!= null` (set by `accept()`) |

The aggregate init raises `IllegalStateException` when invariants are violated.

**Accept transition is single-method with workspace parameter:**

```kotlin
fun accept(at: Instant, principalId: String, resolvedWorkspaceId: String? = null): Invitation
```

For `NEW_WORKSPACE`, `resolvedWorkspaceId` is mandatory. For `EXISTING_WORKSPACE`,
it is unused and `workspaceId` is already set.

#### Scenario: Admin creates invitation from eligible waitlist entry

- GIVEN a waitlist entry with status PENDING and no active invitation
- WHEN admin with WAITLIST_INVITE permission executes InviteWaitlistEntryCommand
- THEN the handler creates Invitation(source=WAITLIST, sourceReferenceId=waitlistEntryId, target=NEW_WORKSPACE, workspaceId=null)
- AND persists it via InvitationRepository
- AND calls WaitlistEntry.invite(now) [PENDING → INVITED]
- AND publishes InvitationIssued (audit event — no raw token)

#### Scenario: User accepts a waitlist invitation (NEW_WORKSPACE)

- GIVEN an active Invitation with source=WAITLIST, target=NEW_WORKSPACE, workspaceId=null
- WHEN user with matching identity and email presents valid token
- THEN InvitationActivationCoordinator.activate() provisions workspace, converts waitlist entry, and accepts invitation
- AND returns InvitationActivationResult(invitation, membershipStatus)

#### Scenario: User accepts invitation to existing workspace (EXISTING_WORKSPACE)

- GIVEN an active Invitation with target=EXISTING_WORKSPACE, workspaceId=ws-789
- WHEN user with matching email presents valid token
- THEN InvitationActivationCoordinator.activate() reconciles membership and accepts invitation
- AND returns InvitationActivationResult(invitation, membershipStatus)

### Requirement: InvitationActivationCoordinator orchestrates all acceptance paths

Both acceptance entry points delegate to `InvitationActivationCoordinator`:

| Entry point | Triggered by |
|---|---|
| `AcceptInvitationHandler` | Authenticated user clicks email link |
| `InvitationRegistrationGatewayAdapter` | New user completes registration form |

Coordinator returns `InvitationActivationResult`:
```kotlin
data class InvitationActivationResult(
    val invitation: Invitation,
    val membershipStatus: WorkspaceMembershipStatus,
)
```

`ProvisionedWorkspace` MUST expose `membershipStatus`:
```kotlin
data class ProvisionedWorkspace(
    val workspaceId: String,
    val name: String,
    val membershipStatus: WorkspaceMembershipStatus,
)
```

Coordinator has no transaction of its own. Transaction is owned by the caller (`AtomicTransactionRunner`).

### Requirement: Waitlist entry reflects conversion on acceptance

`WaitlistEntry.convert()` MUST be called by `InvitationActivationCoordinator` when a `source=WAITLIST` invitation is accepted.

#### Scenario: INVITED entry transitions to CONVERTED when workspace is provisioned

- GIVEN a waitlist entry with status INVITED and an active Invitation with target=NEW_WORKSPACE
- WHEN InvitationActivationCoordinator activates the invitation for NEW_WORKSPACE
- THEN WorkspaceProvisioningService.provisionDefaultWorkspace() is called
- AND WaitlistEntry.convert(now) [INVITED → CONVERTED]
- AND Invitation.accept(now, principalId, provisionedWorkspaceId) [ACTIVE → ACCEPTED]

### Requirement: WAITLIST source enforces sourceReferenceId

`Invitation` with `source = InvitationSource.WAITLIST` MUST have non-blank `sourceReferenceId`.
Init block enforces: `require(source != WAITLIST || !sourceReferenceId.isNullOrBlank())`.

### Requirement: No raw token in InvitationIssued event

`InvitationIssued` published by `InviteWaitlistEntryHandler` MUST NOT carry the raw token.
Token handoff for notification delivery follows DALLAY-565/566 contract:
`InvitationNotificationRequested(invitationId, commandId, kind)` — no raw token.

### Requirement: No SUPERSEDED status

Canonical `Invitation` status is NOT modified. `SUPERSEDED` is not a valid status.
PostgreSQL CHECK constraint enforces: `status IN ('ACTIVE', 'ACCEPTED', 'EXPIRED', 'REVOKED')`.

Resend follows DALLAY-565 contract: same `InvitationId`, new delivery command/notification record.
DALLAY-570 does NOT create a new `Invitation` on re-invite.

### Requirement: WaitlistInvitation is legacy-only

`WaitlistInvitation` and `WaitlistInvitationRepository` are **legacy compatibility models only**.
New waitlist invitation flows MUST NOT create or update `WaitlistInvitation` rows.
Existing records created before this change remain readable via the legacy repository.
