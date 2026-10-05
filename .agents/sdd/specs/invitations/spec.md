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

Statuses MUST be exactly `ACTIVE`, `ACCEPTED`, `EXPIRED`, and `REVOKED`. Only `ACTIVE` MAY
transition
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

A framework-free `InvitationRepository` MUST provide aggregate reads/writes and conditional
lifecycle
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
email, `expires_at > created_at`, accepted-metadata consistency, unique opaque lookup/token
material,
and at most one active invitation per workspace and normalized target email. It MUST NOT persist raw
tokens or add delivery columns.

#### Scenario: Impossible row is rejected

- GIVEN partial acceptance metadata or invalid source/reference
- WHEN PostgreSQL receives the write
- THEN the write MUST be rejected

### Requirement: Safe audit and observability

Lifecycle evidence MUST use low-cardinality invitation ID, status, outcome, timestamps, and
correlation
data. Raw tokens, token-bearing URLs, and full target emails MUST NOT cross invitation, audit, log,
or metric boundaries; downstream owners publish their events.

#### Scenario: Evidence contains no bearer

- GIVEN a lifecycle transition succeeds
- WHEN audit or metrics evidence is emitted
- THEN it MUST identify the invitation without bearer or full-email values

### Requirement: Token ownership

Invitation MAY persist only non-reversible token material and opaque lookup data required by
DALLAY-566.
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

| target               | status                         | workspaceId                   |
|----------------------|--------------------------------|-------------------------------|
| `EXISTING_WORKSPACE` | any                            | `!= null` (always required)   |
| `NEW_WORKSPACE`      | `ACTIVE`, `EXPIRED`, `REVOKED` | `== null`                     |
| `NEW_WORKSPACE`      | `ACCEPTED`                     | `!= null` (set by `accept()`) |

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
- THEN the handler creates Invitation (source=WAITLIST, sourceReferenceId=waitlistEntryId,
  target=NEW_WORKSPACE, workspaceId=null)
- AND persists it via InvitationRepository
- AND calls WaitlistEntry.invite (now) [PENDING → INVITED]
- AND publishes InvitationIssued (audit event — no raw token)

#### Scenario: User accepts a waitlist invitation (NEW_WORKSPACE)

- GIVEN an active Invitation with source=WAITLIST, target=NEW_WORKSPACE, workspaceId=null
- WHEN user with matching identity and email presents valid token
- THEN InvitationActivationCoordinator.activate () provisions workspace, converts waitlist entry,
  and accepts invitation
- AND returns InvitationActivationResult (invitation, membershipStatus)

#### Scenario: User accepts invitation to existing workspace (EXISTING_WORKSPACE)

- GIVEN an active Invitation with target=EXISTING_WORKSPACE, workspaceId=ws-789
- WHEN user with matching email presents valid token
- THEN InvitationActivationCoordinator.activate () reconciles membership and accepts invitation
- AND returns InvitationActivationResult (invitation, membershipStatus)

### Requirement: InvitationActivationCoordinator orchestrates all acceptance paths

Both acceptance entry points delegate to `InvitationActivationCoordinator`:

| Entry point                            | Triggered by                         |
|----------------------------------------|--------------------------------------|
| `AcceptInvitationHandler`              | Authenticated user clicks email link |
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

Coordinator has no transaction of its own. Transaction is owned by the caller
(`AtomicTransactionRunner`).

### Requirement: Waitlist entry reflects conversion on acceptance

`WaitlistEntry.convert()` MUST be called by `InvitationActivationCoordinator` when a
`source=WAITLIST` invitation is accepted.

#### Scenario: INVITED entry transitions to CONVERTED when workspace is provisioned

- GIVEN a waitlist entry with status INVITED and an active Invitation with target=NEW_WORKSPACE
- WHEN InvitationActivationCoordinator activates the invitation for NEW_WORKSPACE
- THEN WorkspaceProvisioningService.provisionDefaultWorkspace () is called
- AND WaitlistEntry.convert (now) [INVITED → CONVERTED]
- AND Invitation.accept (now, principalId, provisionedWorkspaceId) [ACTIVE → ACCEPTED]

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

### Requirement: Direct invitation transactions are atomic

`CreateInvitationHandler` and `ResendInvitationHandler` MUST own an `AtomicTransactionRunner`
boundary. Inside one reactive R2DBC transaction each handler MUST validate the operator, resolve the
target, mutate the invitation, write the administrative audit event, and register the domain event
through `InvitationEventPublisher`. Any failure before commit MUST roll back invitation, audit, and
event registration together. `AdminInvitationController` direct create and direct resend MUST NOT be
transactional. Telemetry recording MUST happen only after the transaction succeeds.

#### Scenario: Committed create persists invitation and audit with registered event

- GIVEN a direct invitation create with a resolvable target
- WHEN the handler transaction commits
- THEN one `ACTIVE` invitation exists
- AND one `INVITATION_CREATED` audit event exists
- AND one `InvitationIssued` event is registered for after-commit delivery

#### Scenario: Publisher failure leaves no invitation or audit

- GIVEN a direct create where event publication fails inside the transaction
- WHEN the handler propagates that failure
- THEN no invitation row exists for the requested email
- AND no `INVITATION_CREATED` audit event exists
- AND no notification exists and the provider is not called

### Requirement: Target-aware invitation context with 404 workspace semantics

For `EXISTING_WORKSPACE` the handler MUST resolve the human label through the narrow tenancy
workspace-name port (`workspaces.name` where `ACTIVE`). A null lookup MUST raise the platform-admin
workspace-not-found error, map to HTTP `404 Not Found` with code `WORKSPACE_NOT_FOUND`, abort before
commit, and create no invitation, audit, event, notification, or provider call. The email MUST
receive
the resolved name, never the workspace ID or blank text. For `NEW_WORKSPACE` the handler MUST keep
`workspaceId` absent and use exactly: “You’ve been invited to create a new Profile Tailors
workspace.”

#### Scenario: Unknown workspace returns 404 with no writes

- GIVEN a direct create for `EXISTING_WORKSPACE` with an unknown workspace ID
- WHEN the handler resolves the target
- THEN HTTP `404` with `WORKSPACE_NOT_FOUND` is returned
- AND no invitation, audit, event, notification, or provider call is created

#### Scenario: New-workspace create uses the canonical copy

- GIVEN a direct create for `NEW_WORKSPACE`
- WHEN the handler resolves the target
- THEN no workspace lookup is performed
- AND the event carries `NEW_WORKSPACE` with the exact canonical workspace copy

### Requirement: Delivery identity originates in handlers

Initial direct create MUST publish `InvitationIssued` with `deliveryId = null`, which the consumer
renders as `invitation:{invitationId}:initial`. Each intentional direct resend MUST mint a new
random
`deliveryId` and publish `DirectInvitationResent` with that identity, which the consumer renders as
`invitation:{invitationId}:resend:{deliveryId}`. Two intentional resends of the same invitation MUST
carry distinct delivery identities.

#### Scenario: Initial create carries no delivery identity

- GIVEN a direct create commits
- WHEN its `InvitationIssued` event is inspected
- THEN `deliveryId` is null
- AND the delivery key is `invitation:{invitationId}:initial`

#### Scenario: Two resends carry distinct delivery identities

- GIVEN one active direct invitation
- WHEN it is resent twice
- THEN both `DirectInvitationResent` events carry non-null delivery identities
- AND the two delivery identities differ

### Requirement: Bulk invitation envelope (DALLAY-665)

Bulk requests MUST accept at most 50 entry IDs and respond HTTP 200 with `results` (per ID:
`entryId`, `outcome` of `invited|skipped|failed`, `invitationId` when invited, `code` otherwise)
plus `summary` (`requested`, `invited`, `skipped`, `failed`). Partial success MUST be reported per
entry, never hidden. Results MUST carry IDs and codes only — never raw tokens or emails.

#### Scenario: Mixed batch reports partial success

- GIVEN 3 PENDING + 1 INVITED + 1 CONVERTED entries
- WHEN an authorized admin bulk-invites all 5
- THEN the response is 200 with 3 `invited`, 1 `skipped`, 1 `failed` and a matching summary

#### Scenario: Batch cap enforced before any work

- GIVEN a request with 51 entry IDs
- WHEN the bulk endpoint is called
- THEN the request MUST be rejected before any entry is touched

### Requirement: Bulk reuses single-entry issuance (DALLAY-665)

Each entry MUST reuse the single-entry WAITLIST issuance path; bulk MUST NOT define a separate
lifecycle. One `InvitationIssued` event MUST be published per `invited` entry only, with no raw
token. Bulk persistence MUST match single-entry behavior entry-for-entry (dual-write parity kept as
legacy debt).

#### Scenario: Success issues one event per entry

- GIVEN 2 PENDING entries bulk-invited successfully
- WHEN issuance completes
- THEN exactly 2 `InvitationIssued` events exist, one per invitation, with no raw token

### Requirement: Bulk observability (DALLAY-665)

The system MUST record per-entry counters plus one bulk counter with batch size and per-outcome
counts, all low-cardinality.

### Requirement: Validate invitation before mutation

Registration MUST validate token, lifecycle, expiry, and normalized target email before mutation. It
MUST return Problem Details: invalid `400 INVITATION_INVALID`; expired `410
INVITATION_EXPIRED`; revoked `410 INVITATION_REVOKED`; consumed or replayed `409
INVITATION_ALREADY_CONSUMED` or `INVITATION_REPLAYED`; mismatch `403 INVITATION_EMAIL_MISMATCH`;
and client workspace override `400 INVITATION_WORKSPACE_OVERRIDE_NOT_ALLOWED`. Details MUST omit
tokens, full emails, and workspace values.

#### Scenario: Expired invite

- GIVEN an invitation at or beyond its exclusive `expiresAt` boundary
- WHEN its matching token is submitted
- THEN HTTP `410 INVITATION_EXPIRED` is returned and no mutation remains

#### Scenario: Revoked invite

- GIVEN an invitation whose lifecycle is `REVOKED`
- WHEN its matching token is submitted
- THEN HTTP `410 INVITATION_REVOKED` is returned and no mutation remains

#### Scenario: Email mismatch

- GIVEN an active invitation targeted to normalized email A
- WHEN its token is submitted with normalized email B
- THEN HTTP `403 INVITATION_EMAIL_MISMATCH` is returned without mutation or sensitive details

### Requirement: Invitation target determines workspace

`EXISTING_WORKSPACE` MUST use its stored workspace. `NEW_WORKSPACE` MUST provision exactly one
workspace for the principal. Client workspace, invitation ID, or fallback identity
MUST NOT determine authorization or tenancy.

#### Scenario: Existing or new target

- GIVEN a valid invitation targeting existing workspace W or a new workspace
- WHEN a new identity accepts
- THEN membership uses W, or exactly one workspace is provisioned and linked

#### Scenario: Workspace override

- GIVEN a valid invitation and a client workspace ID
- WHEN registration is processed
- THEN `400 INVITATION_WORKSPACE_OVERRIDE_NOT_ALLOWED` is returned before acceptance dispatch

### Requirement: Existing identity is authenticated and non-duplicating

An identity that already has an accepted invitation to the same workspace MUST NOT accept another
invitation to that workspace. The handler MUST return `409 INVITATION_ALREADY_ACCEPTED` with
code `INVITATION_ALREADY_ACCEPTED`.

#### Scenario: Duplicate acceptance returns 409

- GIVEN a principal who has already accepted an invitation to workspace W
- WHEN the same principal submits a new invitation to workspace W
- THEN HTTP `409 INVITATION_ALREADY_ACCEPTED` is returned

### Requirement: Acceptance mutations are atomic

All write operations (invitation acceptance, workspace provisioning, membership creation, audit, and
event emission) MUST be atomic. Failure at any step MUST rollback all changes and return an error
without partial state.

#### Scenario: Atomic acceptance commits all or nothing

- GIVEN an active, unexpired invitation
- WHEN acceptance is processed
- THEN either all operations succeed or all are rolled back

### Requirement: Verification follows existing policy

Email verification MUST follow the existing registration verification policy with no deviation.

#### Scenario: Verification is unchanged

- GIVEN a successful invitation acceptance
- WHEN the registration completes
- THEN email verification follows the existing verification flow

### Requirement: Evidence is redacted and aggregate

HTTP responses, logs, metrics, and audit records MUST NOT contain raw invitation tokens, full
email addresses, or workspace identifiers beyond the minimum required for the use case.

#### Scenario: Evidence contains no bearer

- GIVEN a registration with invitation
- WHEN the response is logged or recorded
- THEN no raw token, full email, or workspace ID appears in evidence

### Requirement: One-time concurrent acceptance

At most one `ACTIVE`→`ACCEPTED` transition MAY succeed per invitation. Concurrent attempts MUST
result in exactly one success and `409 INVITATION_ALREADY_CONSUMED` for the remainder.

#### Scenario: Concurrent clients contend

- GIVEN two concurrent acceptance attempts for the same invitation
- WHEN both are processed simultaneously
- THEN exactly one succeeds with `200` and the other fails with `409`

### Requirement: Token-presence authorization and split acceptance path

Authorization MUST check for token presence and redirect unauthenticated requests to the
registration flow. Authenticated requests MUST bypass invitation validation and proceed to
dashboard.

#### Scenario: Unauthenticated request routes to registration

- GIVEN a request with no invitation token
- WHEN the invitation check runs
- THEN HTTP `302` redirects to `/register`

#### Scenario: Authenticated request bypasses invitation

- GIVEN a request with a valid session and an invitation token
- WHEN the invitation check runs
- THEN the token is ignored and the request proceeds to dashboard

#### Scenario: Bulk counters recorded

- GIVEN a batch of 5 yielding 3 invited, 1 skipped, 1 failed
- WHEN the batch completes
- THEN per-entry counters and one bulk counter (size 5, matching outcome counts) MUST be recorded


## Implementation audit: additional requirements

Verdicts are against concrete current implementation and test assertions. `Invitation` means the canonical aggregate; `WaitlistInvitation` is a separate legacy model and does not evidence canonical behavior. No tests, checks, or builds were run for this audit.

| Exact requirement/scenario title | Current code (`path:symbol`) | Test path + concrete assertion | Verdict |
|---|---|---|---|
| Safe audit and observability / Evidence contains no bearer | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/audit/InvitationAcceptedAuditEventListener.kt:InvitationAcceptedAuditEventListener`; `.../domain/InvitationIssued.kt:InvitationIssued` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/audit/InvitationAcceptedAuditEventListenerTest.kt`: `fact.toString().contains("raw-token") shouldBe false`; likewise `invitee@example.com` false. Fields also assert action, target, actor and workspace. | partially matches: only accepted-audit fact redaction explicitly asserted; issuance/telemetry redaction not verified |
| Token ownership / Notification failure stays external | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/contracts/TokenHasher.kt:TokenHasher`; `.../application/handler/CreateInvitationHandler.kt:CreateInvitationHandler`; `.../application/InvitationActivationCoordinator.kt:candidateKey` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/InvitationActivationCoordinatorTest.kt`: verifies failure when injected hasher is not `InvitationTokenCandidateKey`. `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/CreateInvitationHandlerTest.kt`: publisher failure yields `rollbackCount == 1`; telemetry `recordInvitationCreated()` is not called. | partially matches: ownership boundary/error and publish-failure transaction tested; notification failure/handoff not asserted here |
| Legacy compatibility / Legacy flow remains separate | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/WaitlistInvitation.kt:WaitlistInvitation`; `.../infrastructure/persistence/R2dbcWaitlistInvitationRepository.kt:R2dbcWaitlistInvitationRepository`; canonical counterpart `.../domain/Invitation.kt:Invitation` | No assertion inspected proving pre-existing `WaitlistInvitation` records remain readable while new invitation commands avoid creating/updating them. | pending evidence |
| InvitationTarget models two distinct onboarding paths / Admin creates invitation from eligible waitlist entry | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/InviteWaitlistEntryHandler.kt:InviteWaitlistEntryHandler`; `.../domain/Invitation.kt:InvitationTarget` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/InviteWaitlistEntryHandlerTest.kt`: exact assertions for PENDING→INVITED, canonical `Invitation` target/source/reference and published event were not retrieved. | pending exact assertion |
| InvitationTarget models two distinct onboarding paths / User accepts a waitlist invitation (NEW_WORKSPACE) | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/InvitationActivationCoordinator.kt:completeLocked` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/InvitationActivationCoordinatorTest.kt`: `assertEquals(InvitationStatus.ACCEPTED, result.invitation.status)`, `assertEquals("ws-new", result.invitation.workspaceId)`, and active membership assertion in NEW_WORKSPACE case. | matches acceptance target/workspace outcome; source-specific issuance evidence remains pending |
| InvitationTarget models two distinct onboarding paths / User accepts invitation to existing workspace (EXISTING_WORKSPACE) | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/InvitationActivationCoordinator.kt:completeLocked`; `.../domain/Invitation.kt:Invitation.accept` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/InvitationActivationCoordinatorTest.kt`: asserts ACCEPTED status, workspace `ws-existing`, and ACTIVE membership. | matches |
| InvitationActivationCoordinator orchestrates all acceptance paths | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/InvitationActivationCoordinator.kt:activateForRegistration`, `completeLocked`; `.../application/AcceptInvitation.kt:AcceptInvitationHandler`; `.../infrastructure/InvitationRegistrationGatewayAdapter.kt:InvitationRegistrationGatewayAdapter` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/AcceptInvitationHandlerTest.kt`: accepted handler enters one transaction (`invocationCount == 1`); `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/InvitationRegistrationGatewayAdapterTest.kt`: returned `workspaceId shouldBe "workspace-a"`; `InvitationActivationCoordinatorTest.kt` asserts both targets' accepted workspace and membership. | partially matches: both outcomes and entry seams have evidence; cross-path shared coordinator delegation itself is not explicitly asserted |
| Waitlist entry reflects conversion on acceptance / INVITED entry transitions to CONVERTED when workspace is provisioned | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/InvitationActivationCoordinator.kt:convertWaitlistEntryIfNeeded`; `shared/lead-capture/waitlist/src/main/kotlin/com/profiletailors/leadcapture/waitlist/domain/WaitlistEntry.kt:convert` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/InvitationActivationCoordinatorTest.kt`: acceptance test asserts `result.invitation.status == ACCEPTED` and `entry.status == WaitlistEntryStatus.CONVERTED`. | matches |
| WAITLIST source enforces sourceReferenceId | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/Invitation.kt:Invitation` | No concrete test assertion inspected for blank/missing WAITLIST reference rejection. | pending evidence |
| No raw token in InvitationIssued event | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/InvitationIssued.kt:InvitationIssued`; issuance handlers in `.../application/handler/CreateInvitationHandler.kt:CreateInvitationHandler` and `.../InviteWaitlistEntryHandler.kt:InviteWaitlistEntryHandler` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/CreateInvitationHandlerTest.kt`: issuance event assertion expects `eventSlot.captured.rawToken` non-empty. This is a delivery event, not proof that `InvitationIssued` audit/domain event is token-free. | contradicts if interpreted as no raw token in all events; partial if requirement is limited to audit `InvitationIssued` |
| No SUPERSEDED status | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/Invitation.kt:InvitationStatus`; `server/smp/src/main/resources/db/migration` invitation status constraint | Exact current migration and assertion proving allowed status set were not opened during this audit. | pending evidence |
| WaitlistInvitation is legacy-only | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/WaitlistInvitation.kt:WaitlistInvitation`; `.../application/handler/InviteWaitlistEntryHandler.kt:InviteWaitlistEntryHandler` | No exact test assertion inspected for non-use of legacy repository on canonical issuance and continued legacy reads. | pending evidence |
| Direct invitation transactions are atomic / Publisher failure leaves no invitation or audit | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/CreateInvitationHandler.kt:CreateInvitationHandler` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/CreateInvitationHandlerTest.kt`: publisher failure asserts transaction `rollbackCount == 1`, and `verify(exactly = 0) { telemetry.recordInvitationCreated() }`. No persisted-row/audit rollback assertion in this unit test. | partially matches |
| Direct invitation transactions are atomic / Committed create persists invitation and audit with registered event | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/CreateInvitationHandler.kt:CreateInvitationHandler` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/CreateInvitationHandlerTest.kt`: valid create asserts result status ACTIVE and event payload target/email/token. It does not assert persisted audit plus after-commit registration in this assertion. | partially matches |
| Direct invitation transactions are atomic / Resend publisher failure rolls back | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/ResendInvitationHandler.kt:ResendInvitationHandler` | No concrete resend failure/rollback assertion inspected. | pending evidence |
| Target-aware invitation context with 404 workspace semantics / Unknown workspace returns 404 with no writes | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/CreateInvitationHandler.kt:CreateInvitationHandler`; `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/AdminInvitationController.kt:AdminInvitationController` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/CreateInvitationHandlerTest.kt`: absent workspace is rejected; missing workspace name asserts exception message `Workspace not found: missing-workspace`. No inspected assertion maps this to HTTP 404 and proves zero writes. | partially matches |
| Target-aware invitation context with 404 workspace semantics / New-workspace create uses the canonical copy | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/CreateInvitationHandler.kt:CreateInvitationHandler` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/CreateInvitationHandlerTest.kt`: asserts event target is `InvitationTarget.NEW_WORKSPACE` and workspace name equals the expected new-workspace copy. | matches |
| Acceptance is atomic / rejects when coordinator throws | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/AcceptInvitation.kt:AcceptInvitationHandler` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/AcceptInvitationHandlerTest.kt`: asserts transaction invocation count 1 and rollback count 1 when coordinator throws. | matches handler transaction/rollback seam; datastore side effects not demonstrated |
| Token validation and duplicate identity | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/InvitationActivationCoordinator.kt:validateToken`, `validateContext`, `candidateKey` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/InvitationActivationCoordinatorTest.kt`: tests assert null candidate key lookup failure, token mismatch failure, non-ACTIVE status rejection, missing identity, email mismatch, non-USER principal, and EXPIRED/REVOKED/ALREADY_CONSUMED failure codes. No duplicate-identity collision assertion was inspected. | partially matches; duplicate-identity exact row pending |
| Bulk invitation envelope (DALLAY-665) / Batch cap enforced before any work | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/BulkInviteWaitlistEntriesHandler.kt:BulkInviteWaitlistEntriesHandler` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/BulkInviteHandlerTest.kt`: test `rejects batch over fifty entries before any work`; exact assertion body was not opened. | pending exact assertion |
| Bulk invitation envelope (DALLAY-665) / Mixed batch reports partial success | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/BulkInviteWaitlistEntriesHandler.kt:BulkInviteWaitlistEntriesHandler` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/BulkInviteHandlerTest.kt`: asserts outcomes exactly INVITED, SKIPPED, FAILED and summary counts requested=3, invited=1, skipped=1, failed=1. | matches |
| Bulk reuses single-entry issuance (DALLAY-665) / Success issues one event per entry | `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/BulkInviteWaitlistEntriesHandler.kt:BulkInviteWaitlistEntriesHandler`; `.../InviteWaitlistEntryHandler.kt:InviteWaitlistEntryHandler` | `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/handler/BulkInviteHandlerTest.kt`: successful/active retry assertions establish outcome and no additional single-handler call for retry; exact successful event cardinality assertion not inspected. | pending exact assertion |
| Frontend invitation acceptance | `apps/web/app/src/modules/invitation/infrastructure/accept-invitation.store.ts:accept`; `apps/web/app/src/modules/invitation/presentation/AcceptInvitationView.vue:AcceptInvitationView` | `apps/web/app/src/modules/invitation/index.spec.ts`: only asserts `AcceptInvitationView` is defined/exported; no acceptance interaction assertion. | no implementational test evidence |

### Exact pending evidence

- Legacy compatibility/readability and canonical no-legacy-write assertions for `WaitlistInvitation`.
- InvitationTarget waitlist issuance exact assertions for `Invitation`, source/reference, waitlist INVITED mutation, and event.
- Coordinator delegation across authenticated and registration-driven acceptance paths.
- WAITLIST source reference rejection and current schema migration CHECK assertion for status values.
- Resend rollback assertions, persistent row/audit rollback evidence, and direct-create HTTP 404 plus no-write assertion.
- Duplicate identity/collision assertion and its externally observable error contract.
- Bulk >50 zero-work assertion and exact successful event count/payload assertion.
- Frontend acceptance interaction assertions; the current barrel test is export-only.
