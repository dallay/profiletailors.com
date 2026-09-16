# Delta for invitations

## ADDED Requirements

### Requirement: Bulk invitation envelope

Bulk requests MUST accept at most 50 entry IDs and respond HTTP 200 with `results` (per ID: `entryId`, `outcome` of `invited|skipped|failed`, `invitationId` when invited, `code` otherwise) plus `summary` (`requested`, `invited`, `skipped`, `failed`). Partial success MUST be reported per entry, never hidden. Results MUST carry IDs and codes only — never raw tokens or emails.

#### Scenario: Mixed batch reports partial success

- GIVEN 3 PENDING + 1 INVITED + 1 CONVERTED entries
- WHEN an authorized admin bulk-invites all 5
- THEN the response is 200 with 3 `invited`, 1 `skipped`, 1 `failed` and a matching summary

#### Scenario: Batch cap enforced before any work

- GIVEN a request with 51 entry IDs
- WHEN the bulk endpoint is called
- THEN the request MUST be rejected before any entry is touched

### Requirement: Bulk reuses single-entry issuance

Each entry MUST reuse the single-entry WAITLIST issuance path; bulk MUST NOT define a separate lifecycle. One `InvitationIssued` event MUST be published per `invited` entry only, with no raw token. Bulk persistence MUST match single-entry behavior entry-for-entry (dual-write parity kept as legacy debt).

#### Scenario: Success issues one event per entry

- GIVEN 2 PENDING entries bulk-invited successfully
- WHEN issuance completes
- THEN exactly 2 `InvitationIssued` events exist, one per invitation, with no raw token

### Requirement: Bulk observability

The system MUST record per-entry counters plus one bulk counter with batch size and per-outcome counts, all low-cardinality.

#### Scenario: Bulk counters recorded

- GIVEN a batch of 5 yielding 3 invited, 1 skipped, 1 failed
- WHEN the batch completes
- THEN per-entry counters and one bulk counter (size 5, matching outcome counts) MUST be recorded
