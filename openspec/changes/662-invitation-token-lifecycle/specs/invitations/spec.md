# Delta for invitations

## ADDED Requirements

### Requirement: Token lifecycle acceptance evidence

The system MUST prove hash-only, expiring, single-use lifecycle with automated tests: raw-token lookup returns nothing; expired, revoked, or consumed attempts are rejected with safe codes; replay after accept is rejected; concurrent accept yields exactly one winner; audit, log, and metric payloads contain no bearer.

#### Scenario: Hash-only persistence

- GIVEN an issued invitation with only hash material stored
- WHEN storage is queried by the raw token value
- THEN no row MUST be returned

#### Scenario: Rejected token states

- GIVEN an expired, revoked, consumed, or already-accepted invitation
- WHEN acceptance (or replay) is attempted
- THEN it MUST be rejected with a safe code (EXPIRED / REVOKED / ALREADY_CONSUMED / REPLAYED)

#### Scenario: Concurrent accept has exactly one winner

- GIVEN one ACTIVE unexpired invitation
- WHEN N callers accept concurrently
- THEN exactly one MUST succeed and the rest MUST be rejected

#### Scenario: Evidence carries no bearer

- GIVEN any token lifecycle transition
- WHEN audit, log, and metric payloads are asserted
- THEN none MUST contain raw tokens, token URLs, or full emails

### Requirement: CAS-honoring waitlist re-invite

`InviteWaitlistEntryHandler` MUST honor the `updateIfVersionMatches` boolean and MUST use `revoke()` instead of hand-building REVOKED copies.

#### Scenario: CAS conflict surfaces

- GIVEN a concurrent re-invite changes the version
- WHEN the conditional update reports false
- THEN the handler MUST surface conflict, MUST NOT silently diverge

### Requirement: Accept-attempt throttle decision

The design MUST decide accept-attempt throttling given ~100ms BCrypt cost per attempt: rate-limit or justify absence.

#### Scenario: Throttle decision recorded

- GIVEN the BCrypt amplification surface on accept
- WHEN the design is reviewed
- THEN a throttle rule or justified no-throttle decision MUST be present

## MODIFIED Requirements

### Requirement: Bulk observability (DALLAY-665)

The system MUST record per-outcome aggregate counters plus one bulk counter with batch size and matching outcome counts. Tags MUST be low-cardinality outcome names only; per-value numeric tags are FORBIDDEN.
(Previously: required bulk counters without forbidding per-value tags.)

#### Scenario: Aggregate counters only

- GIVEN any bulk batch (e.g. 5 yielding 3 invited, 1 skipped, 1 failed)
- WHEN the batch completes
- THEN aggregate outcome counters plus one bulk counter (size 5, matching counts) MUST be recorded
- AND no tag value MUST embed a per-batch count or identifier

### Requirement: No raw token in InvitationIssued event

`InvitationIssued` and `DirectInvitationResent` MUST NOT carry raw token as canonical behavior: the issuance boundary renders the accept URL once from the transient value and passes a sealed delivery reference; persisted state holds hash material only. Until DALLAY-566 replaces the handoff AND DALLAY-565 owners sign off in design.md, the in-memory path MAY remain as scoped interim debt tracked by DALLAY-566, adding no new bearer surfaces.
(Previously: forbade raw token with no interim clause, contradicting live code.)

#### Scenario: Sealed handoff or scoped debt, never silent

- GIVEN the token-safe replacement is absent
- WHEN invitation delivery occurs
- THEN either no event carries raw token, or the interim handoff is recorded as owner-signed debt with no new bearer surface
