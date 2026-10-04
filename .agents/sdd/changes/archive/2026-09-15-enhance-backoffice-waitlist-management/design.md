# Design: Enhance Backoffice Waitlist Management

## Technical Approach

Close 8 operational gaps in the waitlist back-office slice by extending the existing
`AdminWaitlistController`, `CancelWaitlistEntryHandler`, and Vue views. Backend changes are
minimal — the heavy lifting is wiring already-present handlers to endpoints and enriching the
`WaitlistEntryDetail` query. Frontend changes mirror the invitations slice pattern (optimistic lock
on mutations, summary card, resend UX).

## Architecture Decisions

### Decision: Add `expectedVersion` to `CancelWaitlistEntryCommand`

**Choice**: Extend `CancelWaitlistEntryCommand` with a required `expectedVersion: Long` field.
**Alternatives considered**: Version-agnostic delete; conditional header `If-Match: :version`;
separate `CancelWaitlistEntryIfUnmodifiedCommand`. **Rationale**: Mirrors `RevokeInvitationHandler`
which already uses `expectedVersion` in `RevokeWaitlistInvitationCommand`. Spring MVC binding makes
the field natural in the request body. Avoids adding a new command class and keeps the existing
handler as the single execution path.

### Decision: Discover invitation ID for resend from entry detail

**Choice**: Frontend extracts `invitationHistory[0].id` from `AdminWaitlistEntryDetail` rather than
adding a dedicated `getActiveInvitation` endpoint. **Alternatives considered**: New query endpoint;
join in the `list` response; separate resend-in-list with a GET-first round trip. **Rationale**:
`AdminWaitlistEntryDetail` already surfaces `invitationHistory` (list of `AdminInvitationSummary`
with `id: UUID`). The active invitation is the first entry with status `ACTIVE`. No new endpoint
needed; no extra round trip from list view.

### Decision: Reuse `POST /api/admin/invitations/{invitationId}/resend`

**Choice**: Call the existing invitation resend endpoint from the entry detail view using the
invitation ID from the history. **Alternatives considered**: Add a separate
`POST /api/admin/waitlist-entries/{entryId}/resend` wrapper in `AdminWaitlistController`.
**Rationale**: `ResendWaitlistInvitationHandler` already exists and is wired in
`AdminInvitationController` under `/api/admin/invitations/`. Adding a second URL surface introduces
routing ambiguity and duplicated handler wiring. The frontend simply uses the correct invitation ID
from the entry detail.

## Data Flow

### Cancel with optimistic lock

```
UI (WaitlistView) ──POST /waitlist-entries/{id}/cancel──→ AdminWaitlistController
                                                              │
                                                   CancelWaitlistEntryHandler
                                                              │
                                                   WaitlistEntryAdmin.findById()
                                                              │
                                                   domain.cancel(expectedVersion)
                                                              │
                                                   WaitlistEntryRepository.save()
                                                              │
                                                   ← HTTP 200 (success) or 409 Conflict
```

### Resend invitation

```
WaitlistEntryView ──GET /waitlist-entries/{id}──→ AdminWaitlistController
                                                       │
                                            AdminWaitlistQuery.findById()
                                                       │
                                            returns invitationHistory[0].id  ← discovered
                                                       │
UI clicks Resend ──POST /invitations/{invitationId}/resend──→ AdminInvitationController
                                                                          │
                                                              ResendWaitlistInvitationHandler
                                                                          │
                                                              InvitationRepository.update() + save()
                                                                          │
                                                          InvitationResent event published
                                                                          │
                                                       ← AdminInvitationSummary (200)
```

## File Changes

| File                                                                             | Action | Description                                                                                                                                                                                                                               |
|----------------------------------------------------------------------------------|--------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `server/smp/src/main/kotlin/…/application/command/AdminCommands.kt`              | Modify | Add `expectedVersion: Long` to `CancelWaitlistEntryCommand`                                                                                                                                                                               |
| `server/smp/src/main/kotlin/…/application/handler/CancelWaitlistEntryHandler.kt` | Modify | Check `expectedVersion` against `entry.version`; throw `WaitlistEntryVersionConflictException` on mismatch                                                                                                                                |
| `server/smp/src/main/kotlin/…/infrastructure/http/AdminProblemDetailsHandler.kt` | Modify | Add handler for `WaitlistEntryVersionConflictException` → HTTP 409 `WAITLIST_ENTRY_VERSION_CONFLICT`                                                                                                                                      |
| `apps/web/admin/src/views/WaitlistView.vue`                                      | Modify | Summary card, `waitlistKey` filter, date-range filters, cancel sends `expectedVersion`, resend buttons                                                                                                                                    |
| `apps/web/admin/src/views/WaitlistEntryView.vue`                                 | Modify | Consent fields display, metadata summary, resend button, full `InvitationSummary` interface                                                                                                                                               |
| `apps/web/admin/src/i18n/index.ts`                                               | Modify | Add `waitlist.filters.waitlistKey`, `waitlist.filters.joinedFrom/To`, `waitlist.filters.invitedFrom/To`, `waitlist.consent.*`, `waitlist.metadata`, `waitlist.resendSuccess`, `waitlist.cancelConflict`, i18n key `waitlist.statusCounts` |
| `apps/web/admin/src/views/WaitlistView.spec.ts`                                  | Create | Vitest: renders summary card, filters fire API params, cancel sends `expectedVersion`, conflict shows error, resend succeeds                                                                                                              |
| `apps/web/admin/src/views/WaitlistEntryView.spec.ts`                             | Create | Vitest: renders consent fields, metadata summary, resend calls correct endpoint, invite/revoke unchanged                                                                                                                                  |
| `server/smp/src/test/kotlin/…/handler/CancelWaitlistEntryHandlerTest.kt`         | Modify | Add test for version mismatch → `WaitlistEntryVersionConflictException`                                                                                                                                                                   |
| `server/smp/src/test/resources/features/platform-admin.feature`                  | Modify | Add BDD scenarios: cancel with stale version → 409, resend invitation → 200                                                                                                                                                               |

## Interfaces / Contracts

### Backend — Modified Command

```kotlin
// AdminCommands.kt
data class CancelWaitlistEntryCommand(
    val operatorPrincipalId: UUID,
    val operatorRoles: Set<PlatformRole>,
    val waitlistEntryId: String,
    val reason: String,
    val expectedVersion: Long,   // NEW
)
```

### Backend — Error Contract

```kotlin
// AdminProblemDetailsHandler.kt — add:
@ExceptionHandler(WaitlistEntryVersionConflictException::class)
fun handleWaitlistVersionConflict(ex: WaitlistEntryVersionConflictException): ProblemDetail =
    problem(HttpStatus.CONFLICT, "WAITLIST_ENTRY_VERSION_CONFLICT", ex.message)
```

### Frontend — Extended WaitlistEntry

```typescript
// WaitlistView.vue — WaitlistEntry interface extended:
interface WaitlistEntry {
  id: string
  email: string
  normalizedEmail: string
  status: string
  joinedAt: string
  invitedAt: string | null
  waitlistKey: string
  source: string
  version: number        // NEW — needed for cancel expectedVersion
  activeInvitationId?: string  // NEW — if status === 'INVITED'
}

// WaitlistEntryView.vue — WaitlistEntryDetail extended:
interface WaitlistEntryDetail {
  email: string
  status: string
  joinedAt: string
  invitedAt: string | null
  cancelledAt: string | null
  source: string
  preferredLocale: string | null
  invitationHistory: InvitationSummary[]
  earlyAccessConsent: boolean    // NEW
  marketingConsent: boolean       // NEW
  consentVersion: string | null  // NEW
  metadataSummary: Record<string, string>  // NEW
  version: number               // NEW — for cancel-from-detail expectedVersion
}
```

### Frontend — Extended InvitationSummary

```typescript
// WaitlistEntryView.vue — InvitationSummary interface extended:
interface InvitationSummary {
  id: string           // was missing in existing interface; needed for resend
  status: string
  issuedAt: string
  expiresAt: string
  deliveryStatus: string
}
```

### Backend — New/Extended API

```
GET  /api/admin/waitlist-entries/summary        # countByStatus (add to AdminWaitlistController)
GET  /api/admin/waitlist-entries/{id}           # returns full WaitlistEntryDetail with consent/metadata/version
POST /api/admin/waitlist-entries/{id}/cancel    # body: { reason, expectedVersion }
POST /api/admin/invitations/{invitationId}/resend  # already exists; frontend discovers invitationId
```

## Testing Strategy

| Layer       | What to Test                                                       | Approach                                                                           |
|-------------|--------------------------------------------------------------------|------------------------------------------------------------------------------------|
| Unit        | `CancelWaitlistEntryHandler` with matching/stale `expectedVersion` | Existing test file extended with version mismatch case                             |
| Unit        | `WaitlistView.vue` filter → URL params                             | Vitest with `msw` to assert correct query params                                   |
| Unit        | `WaitlistView.vue` cancel → `expectedVersion` sent                 | Vitest mock `fetch`, assert body JSON includes `expectedVersion`                   |
| Unit        | `WaitlistView.vue` conflict 409 → error message                    | Vitest mock `fetch` returning 409, assert UI shows error                           |
| Unit        | `WaitlistView.vue` resend → calls `/invitations/{id}/resend`       | Vitest mock, assert URL and method                                                 |
| Unit        | `WaitlistEntryView.vue` consent fields rendered                    | Vitest mock entry with consent fields, assert DOM                                  |
| Unit        | `WaitlistEntryView.vue` resend → correct endpoint                  | Vitest mock, assert `POST /invitations/{invitationId}/resend`                      |
| Integration | Full cancel conflict flow                                          | `just backend-test-fast` — add test for `WaitlistEntryVersionConflictException`    |
| BDD         | Cancel conflict + resend succeed                                   | `just backend-bdd-fast` — add `@smoke @fast` scenarios in `platform-admin.feature` |

Commands:

- `just admin-test` — Vitest suite for Vue views
- `just admin-check` — TypeScript type-check
- `just admin-build` — build
- `just backend-test-fast` — Kotlin unit + integration
- `just backend-bdd-fast` — Cucumber BDD

## Migration / Rollback

No database migration required. The `expectedVersion` field is added to `CancelWaitlistEntryCommand`
and the handler enforces the check. Old frontend requests that omit `expectedVersion` will cause a
Spring binding failure (missing required field) — the frontend always sends it post-change, so this
is safe.

Rollback: revert `CancelWaitlistEntryCommand`, remove version check in handler, remove exception
handler, and revert frontend changes. No data migration needed.

## Open Questions

- [ ] Should the summary card use a dedicated `GET /api/admin/waitlist-entries/summary` endpoint or
  poll `GET /api/admin/dashboard/summary`? The dashboard summary already includes `countByStatus` —
  a separate endpoint avoids returning dashboard-specific fields but duplicates the query.
  Recommend: add endpoint to `AdminWaitlistController` scoped to entries only.
- [ ] Confirm: should `waitlistKey` filter be an exact match or partial (ILIKE)? The backend
  currently does exact match (`w.key = :waitlistKey`). Partial match would require a change to
  `R2dbcAdminWaitlistQuery`. Recommend: exact match for now (same as existing `waitlistId`
  behavior).
- [ ] The `metadataSummary` is `Map<String, String>` — should it be displayed as a key-value table
  or a JSON blob? Recommend: key-value definition list (`<dl>`) to match the existing entry detail
  layout.
