# Design: Governance Consent HTTP API

## Technical Approach

Complete the draft as a hexagonal HTTP adapter over governance application services. Controllers remain transport-only and dispatch through `Mediator`; workspace identity is resolved in application handlers with `ResourceContextProvider.require()`. `PrincipalContextProvider` is not added to consent code: `WorkspaceAuthorizationService` already resolves the principal when query handlers call `WorkspaceAuthorizationDecider`. Replace status inference with `RecordConsentOutcome(created, record)`, materialize repository `Flow`s with `toList()`, and separate mutable current state from immutable legal evidence.

## Architecture Decisions

| Decision | Alternatives | Rationale |
|---|---|---|
| Context-derived HTTP commands | Controller parameters; direct principal provider | Existing handlers use `ResourceContextProvider`; authorization already owns principal resolution. HTTP commands omit workspace IDs, while the trusted waitlist adapter retains explicit-workspace `RecordConsentCommand`, preserving waitlist → governance ownership direction. |
| Mediator-only controller composition | Inject handlers directly | Existing tenancy/governance controllers dispatch queries/commands through `Mediator`; it preserves CQRS registration and pipeline behavior. |
| `RecordConsentOutcome(created: Boolean, record)` | Infer from `ConsentStatus`; nullable outcomes | ACTIVE does not distinguish a new insert from replay. `created` deterministically maps to 201/200 and supports waitlist callers that ignore the outcome. |
| Current projection plus append-only event ledger | Existing UPSERT; reuse IDs in one table | `R2dbcConsentRepository` currently updates the same row, so history loses the ACTIVE state. `consent_records` remains the current projection; immutable `consent_record_events` stores RECORDED/WITHDRAWN snapshots. |
| Database-enforced idempotency | `existsActive` then `save` | The current check-then-insert races. A partial unique index on the active natural key plus `INSERT ... ON CONFLICT ... DO NOTHING` makes one writer win; repository returns the inserted record or reads the winner. |
| Liquibase permission migration | In-code registry | Authorization is database-backed through `roles`, `permissions`, and `role_permissions`. A new idempotent changelog seeds `workspace:consent:read`, WORKSPACE_OWNER/WORKSPACE_ADMIN assignments, and no WORKSPACE_MEMBER assignment, following authorization 008/009 patterns. |

## Data Flow

    HTTP → ConsentController → Mediator → workspace application handler
                                      → ResourceContextProvider.require()
                                      → authorization decider (queries)
                                      → ConsentRepository
                                           ├─ consent_records (current projection)
                                           └─ consent_record_events (immutable history)

Record and withdrawal repository operations execute projection/event writes in one reactive transaction. Withdrawal locks the active projection row, updates it to WITHDRAWN, and appends a WITHDRAWN event; history reads only events ordered by event timestamp/id. Re-consent inserts a new logical record and RECORDED event.

## File Changes

| File | Action | Description |
|---|---|---|
| `governance/application/ConsentCommands.kt` | Modify | Add context-derived HTTP commands and `RecordConsentOutcome`. |
| `governance/application/RecordConsentHandler.kt` | Modify | Return explicit outcome; use atomic repository operation. |
| `governance/application/WithdrawConsentHandler.kt` | Modify | Resolve HTTP workspace and invoke atomic withdrawal; keep trusted explicit-workspace path for waitlist. |
| `governance/application/Get*Consent*Handler.kt` | Modify | Authorize, resolve workspace, validate filters, and call `Flow.toList()`. |
| `governance/domain/ConsentRepository.kt` | Modify | Define atomic record/withdraw and immutable-history contracts. |
| `governance/infrastructure/R2dbcConsentRepository.kt` | Modify | Replace UPSERT, add transactional projection/event writes and conflict-safe insert. |
| `governance/infrastructure/http/ConsentController.kt` | Modify | Mediator-only composition, `ResponseEntity` 201/200 mapping, typed parsing/OpenAPI. |
| `governance/infrastructure/http/GovernanceProblemDetailsHandler.kt` | Modify | Map invalid enum/locale and missing record to RFC 7807 responses. |
| `db/changelog/governance/005-create-consent-record-events.yaml` | Create | Ledger, backfill, active unique index. |
| `db/changelog/authorization/010-seed-consent-permission.yaml` | Create | Permission and OWNER/ADMIN role links. |
| `db/changelog/db.changelog-master.yaml` | Modify | Include migrations. |
| Focused governance/authorization tests and `features/consent-api.feature` | Create/Modify | Strict RED→GREEN coverage. |

## Interfaces / Contracts

```kotlin
data class RecordConsentOutcome(val created: Boolean, val record: ConsentRecord)
```

Invalid `SubjectKind`/`ConsentType` and locale values (`^[a-z]{2}(?:-[A-Z]{2})?$`, with ISO membership checks) become 400 Problem Details naming the field. Missing authentication/context remains 401 through existing security handlers; authorization denial remains 403.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | context resolution, authorization, `toList()`, outcome semantics | Failing handler tests first with fakes/captures. |
| HTTP | 201/200, validation, 401/403/404 Problem Details | `WebTestClient` controller/security slices. |
| Postgres | concurrent duplicate POST, withdrawal ledger, re-consent/history, role permission | Existing Testcontainers base; concurrent coroutines and direct row assertions. |
| BDD | authenticated full lifecycle | Extend existing Cucumber glue/feature conventions. |

## Migration / Rollout

Liquibase backfills one RECORDED event per existing row before enforcing ledger constraints. Rollback removes the HTTP adapter and new permission links; ledger data must be retained for legal evidence. DALLAY-496 bulk withdrawal is excluded.

## Open Questions

None.
