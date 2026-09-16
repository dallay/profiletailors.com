# Design: Bulk Waitlist Invitation Operations (#665)

## Technical Approach

Synchronous orchestrating `BulkInviteWaitlistEntriesHandler` over the existing `InviteWaitlistEntryHandler` per entry. Cap → up-front `WAITLIST_INVITE` check → sequential per-entry `runAtomically` calls → 200 envelope with per-entry `invited|skipped|failed` results. No new permission, no schema change, dual-write parity preserved.

## Architecture Decisions

| Option | Tradeoff | Decision |
|---|---|---|
| Reuse single handler per entry vs duplicate lifecycle rules | N round-trips, but zero invariant duplication | **Reuse**: bulk handler calls `InviteWaitlistEntryHandler.handle` per entry |
| Per-entry txn (`AtomicTransactionRunner`) vs one giant `@Transactional` | Giant txn = all-or-nothing + audit rollback | **Per-entry txn**; bulk controller method carries **no** `@Transactional` |
| Success audit inside entry txn, failure audit outside | Symmetric-outside needs single-handler refactor | **Asymmetric**: success audit commits atomically with state (existing path); `skipped`/`failed` audits published by bulk handler in `catch`, outside the rolled-back txn |
| `INVITED` → `skipped` vs revoke-and-report (single behavior) | Revoke churns tokens on retry | **Short-circuit**: bulk pre-reads status; `INVITED` returns `skipped/ALREADY_INVITED` without touching single handler |
| Sequential loop vs parallel | Parallel breaks R2DBC txn scoping, nondeterministic order | **Sequential**, request order preserved in `results[]` |
| `InvitationAlreadyActiveException` → `skipped` vs `failed` | PENDING + active legacy row = prior partial attempt | **`skipped/INVITATION_ALREADY_ACTIVE`** (retry-safe, `REJECTED`) |

## Data Flow

```
Controller ──→ BulkHandler ──→ per entry: runAtomically { SingleHandler }
     │                │                    │ success: state + audit commit together
     │                │                    └ failure: rollback → catch → audit outside txn
     │                └──→ auditPublisher (skipped/failed) + telemetry + summary
     └──→ 200 {results[], summary}
```

## File Changes

| File | Action | Description |
|---|---|---|
| `…/application/command/AdminCommands.kt` | Modify | Add `BulkInviteWaitlistEntriesCommand(operatorPrincipalId, operatorRoles, entryIds)` + `BulkEntryResult(entryId, outcome, invitationId?, code?)`, `BulkInviteSummary` |
| `…/application/handler/BulkInviteWaitlistEntriesHandler.kt` | Create | Cap/dedupe validation, permission fail-fast, per-entry loop + exception→outcome map |
| `…/infrastructure/http/AdminWaitlistController.kt` | Modify | `POST /invitations:bulk`, thin mapping, no `@Transactional` |
| `…/application/contracts/InvitationTelemetry.kt` + `…/infrastructure/observability/InvitationObservability.kt` | Modify | Add `recordBulkInvite(requested, invited, skipped, failed)` counter |
| `…/infrastructure/PlatformAdminBootstrapConfiguration.kt` | Modify | Wire bulk handler bean |
| `src/test/resources/features/platform-admin.feature` | Modify | Bulk BDD scenarios |
| `apps/web/admin/src/views/WaitlistView.vue` | Modify | Bulk selection + per-entry result display |

## Interfaces / Contracts

```kotlin
data class BulkInviteWaitlistEntriesCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<PlatformRole>,
    val entryIds: List<String>,
)
```

Route: `POST /api/admin/waitlist-entries/invitations:bulk`, `Accept: application/vnd.api.v1+json`. Validation (`IllegalArgumentException` → existing 400): empty list, >50 ids, blank ids; duplicates deduped preserving first-occurrence order. Permission checked once before any entry touched (`PlatformAccessDeniedException` → existing 403).

Request/response:

```json
POST .../invitations:bulk
{"entryIds": ["e1", "e2", "e3"]}
```
```json
{
  "results": [
    {"entryId": "e1", "outcome": "invited", "invitationId": "9f…"},
    {"entryId": "e2", "outcome": "skipped", "code": "ALREADY_INVITED"},
    {"entryId": "e3", "outcome": "failed", "code": "ENTRY_ALREADY_CONVERTED"}
  ],
  "summary": {"requested": 3, "invited": 1, "skipped": 1, "failed": 1}
}
```

Only IDs + stable codes cross the boundary — never raw tokens or emails (ADR-0020/0021).

## Error-to-Outcome Mapping

| Exception / condition | Outcome | `code` | Audit |
|---|---|---|---|
| Success | `invited` | — (`invitationId` set) | `WAITLIST_ENTRY_INVITED/SUCCEEDED` (inside txn) |
| `INVITED` pre-read | `skipped` | `ALREADY_INVITED` | `REJECTED` + code |
| `InvitationAlreadyActiveException` | `skipped` | `INVITATION_ALREADY_ACTIVE` | `REJECTED` + code |
| `WaitlistEntryNotFoundException` | `failed` | `ENTRY_NOT_FOUND` | `FAILED` + code |
| `WaitlistEntryAlreadyConvertedException` | `failed` | `ENTRY_ALREADY_CONVERTED` | `FAILED` + code |
| `WaitlistEntryNotInvitableException` | `failed` | `ENTRY_NOT_INVITABLE` | `FAILED` + code |
| `InvitationVersionConflictException`, `OptimisticLockException`, unique-violation | `failed` | `VERSION_CONFLICT` | `FAILED` + code |
| Unexpected | `failed` | `UNEXPECTED_ERROR` | `FAILED` + code |

## Retry / Idempotency / Concurrency

Retry contract: re-`POST` the same ID list; previously-`invited` entries now read `INVITED` → deterministic `skipped`, never duplicates or new tokens. Contention (bulk vs single on one `PENDING` entry): `updateIfVersionMatches` + unique index on active-`WAITLIST` `source_reference_id` make exactly one writer win; loser → `failed/VERSION_CONFLICT`, retry → `skipped`. Telemetry: existing per-entry `recordInvitationCreated` on success plus one `platform.waitlist.invitations.bulk` counter with outcome tags.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Cap/empty/dedupe validation; exception→outcome table | Handler unit tests with fakes |
| Application | Per-entry independence; failure audit outside rolled-back txn; `INVITED` short-circuit emits no `InvitationIssued` | Handler tests with fake ports, assert audit/event calls |
| API | 200 partial envelope + summary; 400 over-cap; 403 no permission before any entry touched | WebFlux slice with mocked handler |
| Integration | Mixed batch commits independently; audit rows per entry; unique-index contention → one invited | R2DBC/Testcontainers |
| BDD | 3×PENDING+INVITED+CONVERTED mixed batch; retry→skips; bulk-vs-single contention; permission denial | `platform-admin.feature` scenarios |

## Migration / Rollout

No migration required. No schema change. Rollback: delete bulk route + handler bean; single flow untouched.

Rollout notes: hard cap 50 bounds latency and token issuance per request; no new permission (`platform.waitlist.invite` reused, fail-fast 403). Legacy `WaitlistInvitation` dual-write is preserved entry-for-entry as parity debt and retires jointly with the single flow in a follow-up, never diverged by bulk. Retry contract is safe by construction (`INVITED` short-circuit, no duplicate tokens).

## Open Questions

- None blocking. Follow-up (not this change): joint retirement of legacy `WaitlistInvitation` dual-write.
