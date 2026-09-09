# Exploration: DALLAY-562 Administrative Audit Event Infrastructure

## Status: COMPLETE

## Context

DALLAY-562 is about building audit event infrastructure for Back Office administrative mutations. This exploration was triggered because two overlapping audit models exist in the codebase:
- `platformadmin/AdminAuditEvent` — LIVE, used by 6 handlers
- `administrative/AdministrativeAuditEvent` — STALE, orphaned table, zero handlers emitting to it

## Findings

### Two audit models found

#### LIVE: `platformadmin` context

**Domain model** (`platformadmin/domain/AdminAuditEvent.kt`):
```
AdminAuditEvent(
  eventId: UUID,
  occurredAt: Instant,
  operatorPrincipalId: UUID,
  operatorPlatformRoles: Set<PlatformRole>,   ← rich operator context
  action: AdminAuditAction,                  ← typed enum (8 values)
  targetType: String,
  targetId: String,
  result: AdminAuditResult,                  ← SUCCEEDED / REJECTED / FAILED
  reason: String? = null,
  correlationId: String? = null,
  requestId: String? = null,
  sourceIpHash: String? = null,
  userAgentSummary: String? = null,
  metadata: Map<String, String> = emptyMap()
)
```

**Ports** (`platformadmin/application/contracts/`):
- `AdministrativeAuditPublisher` — publish-only interface (6 handlers use it)
- `AdminAuditQuery` — query/list interface for the audit UI

**Repository** (`platformadmin/infrastructure/persistence/R2dbcAdminAuditRepository.kt`):
- Implements both `AdministrativeAuditPublisher` and `AdminAuditQuery`
- Writes to `platform_admin_audit_events` table
- Rich dynamic query builder with pagination, filtering by operatorPrincipalId/action/targetType/targetId/result/correlationId/occurredFrom/occurredTo

**Callers** (codegraph blast radius):
- 18 callers of `AdminAuditEvent`
- 14 callers of `AdministrativeAuditPublisher`
- Handlers already using it: `InviteWaitlistEntryHandler`, `CancelWaitlistEntryHandler`, `ResendWaitlistInvitationHandler`, and bootstrap wiring

**Tests**: PostgreSQL integration tests exist for `R2dbcAdminAuditRepository`

#### STALE: `administrative` context

**Domain model** (`administrative/domain/AdministrativeAuditEvent.kt`):
```
AdministrativeAuditEvent(
  id: UUID,
  actorId: UUID,
  actorType: String,           ← free string (not typed)
  action: String,               ← free string (not typed enum)
  targetId: String,
  targetType: String,
  correlationId: String?,
  metadata: Map<String, String>,
  occurredAt: Instant,
) {
  init {
    // redaction at construction time — good design
    require(metadata.keys.none { SENSITIVE_SUBSTRINGS.any { ss -> it.lowercase().contains(ss) } })
  }
}
```

Where `SENSITIVE_SUBSTRINGS = ["password", "token", "secret", "credential", "key", "invitationtoken", "resettoken", "refreshtoken", "accesstoken"]`

**Ports** (`administrative/application/`):
- `AdministrativeAuditEventRepository` — combined save + query port (not separated by concern)

**Repository** (`administrative/infrastructure/persistence/R2dbcAdministrativeAuditEventRepository.kt`):
- Implements `AdministrativeAuditEventRepository`
- Writes to `administrative_audit_events` table
- Has `findByActor`, `findByTarget`, `findByCorrelationId` query methods

**Callers**: ZERO. No handlers emit `AdministrativeAuditEvent`. The `AuditEventPublisher` class exists but is never wired.

**Table**: `administrative_audit_events` — created by migration 006, orphaned (no events ever written to it).

**Redaction**: Already implemented in the domain model `init` block — a `redact()` function and `SENSITIVE_SUBSTRINGS` denylist exist here but are dead code since nothing emits to this context.

### Comparison

| Dimension | `platformadmin` (LIVE) | `administrative` (STALE) |
|-----------|------------------------|---------------------------|
| Action field | Typed `AdminAuditAction` enum (8 values) | Free `String` |
| Operator context | Has `operatorPlatformRoles: Set<PlatformRole>` | No |
| Result field | `AdminAuditResult` enum | No |
| Source IP tracking | `sourceIpHash`, `userAgentSummary` | No |
| Redaction | None (metadata stored raw) | Yes (`init` block enforcement) |
| Port separation | Publish + Query (SRP ✓) | Combined repository |
| Handler adoption | 6 handlers, production-active | 0 handlers, orphaned |
| Query API | Rich dynamic filtering + pagination | Basic findByActor/Target/CorrelationId |
| Tests | PostgreSQL integration tests | None |

## Recommendation

**The `platformadmin` context is the live production system.** The `administrative` context was an parallel effort that was started but never wired to handlers.

The right path forward is:

1. **Keep `platformadmin`** as the production seam — it has the rich operator context, typed actions, source tracking, and handler adoption that `administrative` lacks.

2. **Add redaction** to `platformadmin` — the one meaningful capability that `administrative` attempted but never activated. This means adding a `redact()` utility to sanitize `metadata` before storing, modeled after the `administrative` `SENSITIVE_SUBSTRINGS` denylist approach.

3. **Remove the stale `administrative` context** — `AdministrativeAuditEvent`, `AuditEventPublisher`, `R2dbcAdministrativeAuditEventRepository`, and the orphaned `administrative_audit_events` table should be deleted as part of this change. They are dead code that creates confusion.

4. **Extend `AdminAuditAction` enum** if new Back Office actions need to be recorded — not free strings like `administrative` uses.

## Revised Proposal

See `proposal.md` (this directory) — updated to reflect the `platformadmin`-first approach.
