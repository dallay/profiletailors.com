# Design: Expand Backoffice Invitations Management

## Technical Approach

Mirror the waitlist collection-read slice (`ListAdminWaitlistEntriesQuery` /
`R2dbcAdminWaitlistQuery.list` / `AdminWaitlistController.listEntries` returning `PagedResult`) onto
the `invitations` table filtered `source='DIRECT'`. New direct-shaped summary type plus
`ListAdminDirectInvitationsQuery`; `GET /api/admin/invitations/direct` guarded `INVITATIONS_READ`.
Frontend adds a list section to `DirectInvitationsView.vue` following `WaitlistView.vue`
fetch/filter/paginate composition, with row resend/revoke reusing existing direct endpoints.

## Architecture Decisions

| Option                                                                       | Tradeoff                                                                                                                                                   | Decision                                                                                                                                                      |
|------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| New `AdminDirectInvitationSummary` vs reuse `AdminInvitationSummary`         | Reuse is free but wrong shape (`waitlistEntryId`, delivery fields; no email/target/workspaceId)                                                            | New type: `invitationId, email, target, workspaceId, status, expiresAt, version`                                                                              |
| `DatabaseClient` SQL in `R2dbcAdminInvitationQuery` vs new repository method | Repository keeps SQL in one place; query class currently wraps `WaitlistInvitationRepository` (`waitlist_invitations` table — wrong table for DIRECT rows) | Inject `DatabaseClient` into `R2dbcAdminInvitationQuery`; list reads `invitations` directly, `findById` untouched                                             |
| Fixed `created_at DESC` vs configurable sort param                           | Configurable mirrors waitlist but spec fixes `issuedAt desc`; allowlist with one key adds dead surface                                                     | Fixed `ORDER BY created_at DESC` (`created_at` is the direct `issuedAt`); no sort params                                                                      |
| Email filter `LIKE` on `invited_email_normalized` vs exact match             | Exact mirrors waitlist; spec requires normalized substring case-insensitive                                                                                | `invited_email_normalized LIKE '%' \|\| :email \|\| '%'` with trimmed-lowercased input                                                                        |
| Frontend state inline in view vs shared store/composable                     | Store is cleaner; view currently holds all create-form state locally and no invitations store exists                                                       | Inline in `DirectInvitationsView.vue` mirroring `WaitlistView.vue` (`result/loading/error/search/statusFilter/page`, `watch` reset page 0, `onMounted` fetch) |

## Data Flow

```
DirectInvitationsView ──GET /direct?page&size&status&email──→ AdminInvitationController
        │                         (INVITATIONS_READ guard, ADMIN_PAGE_MAX_SIZE check)
        │                    ListAdminDirectInvitationsQuery ──→ AdminInvitationQuery.list
        │                                                              │
        │                                              R2dbcAdminInvitationQuery (DatabaseClient)
        │                                              WHERE source='DIRECT' [+status][+email LIKE]
        │                                              ORDER BY created_at DESC LIMIT/OFFSET + COUNT(*)
        │                         ←── PagedResult<AdminDirectInvitationSummary> (no token/tokenHash) ──┘
Row resend/revoke ──POST /{id}/direct-resend | POST /{id}/direct-revoke {expectedVersion}──→ existing handlers ──→ list refresh
```

## File Changes

| File                                                                    | Action | Description                                                                                                                                                              |
|-------------------------------------------------------------------------|--------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `platformadmin/application/model/AdminDirectInvitationSummary.kt`       | Create | Direct-shaped row type (no token fields)                                                                                                                                 |
| `platformadmin/application/query/AdminQueries.kt`                       | Modify | Add `ListAdminDirectInvitationsQuery(page, size, status, email)`                                                                                                         |
| `platformadmin/application/contracts/AdminInvitationQuery.kt`           | Modify | Add `list(query): PagedResult<AdminDirectInvitationSummary>`; keep `findById`                                                                                            |
| `platformadmin/infrastructure/persistence/R2dbcAdminInvitationQuery.kt` | Modify | `DatabaseClient` list impl (`source='DIRECT'`, status equality, email LIKE, fixed sort, `validatePagination`, `PagedResult.of`)                                          |
| `platformadmin/infrastructure/http/AdminInvitationController.kt`        | Modify | `GET /direct` collection: 401 unauthenticated, `INVITATIONS_READ` or 403, 400 over max size, 200 page                                                                    |
| `apps/web/admin/src/views/DirectInvitationsView.vue`                    | Modify | List section: filters, table (email/target/status/expiry), pagination, loading/empty/error, row resend/revoke with `expectedVersion`, zero list requests when `!canRead` |
| `apps/web/admin/src/i18n/index.ts` + `types.ts`                         | Modify | `directInvitations.list.*` keys EN+ES (headers, filters, pagination, empty/loading/error)                                                                                |
| `R2dbcAdminInvitationQueryTest.kt`, `AdminInvitationControllerTest.kt`  | Modify | List coverage incl authz, pagination, filters, no-token assertion                                                                                                        |
| `src/test/resources/features/invitations-direct.feature` + glue         | Create | List/filter/pagination/401/403/empty scenarios (`@smoke @fast`)                                                                                                          |
| `apps/web/admin/src/views/DirectInvitationsView.spec.ts`                | Modify | List render, filter re-query, empty/loading/error, row actions                                                                                                           |

```kotlin
data class AdminDirectInvitationSummary(
    val invitationId: UUID, val email: String, val target: String,
    val workspaceId: String?, val status: String,
    val expiresAt: Instant, val version: Long,
)
data class ListAdminDirectInvitationsQuery(
    val page: Int = 0, val size: Int = 25,
    val status: String? = null, val email: String? = null,
)
```

## Interfaces / Contracts

`GET /api/admin/invitations/direct?page=0&size=25&status=ACTIVE&email=ops@` →
`200 PagedResult<AdminDirectInvitationSummary>` ordered `created_at DESC`. `status` validated
against `InvitationStatus` names; unknown → 400. `email` trimmed + lowercased, substring `LIKE`.
Response never contains `token`, `tokenHash`, or `candidateKey`. Auth: missing principal → 401;
without `INVITATIONS_READ` → 403 via `PlatformAccessDeniedException`. Row actions unchanged:
existing `direct-resend` / `direct-revoke {expectedVersion}` endpoints.

## Testing Strategy

| Layer       | What to Test                                                                                                                         | Approach                                                                           |
|-------------|--------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| Unit        | Status validation, email normalization, `PagedResult.of` edge (empty page)                                                           | JUnit, no Spring context                                                           |
| Integration | `R2dbcAdminInvitationQuery.list` filters/pagination/ordering on seeded DIRECT + WAITLIST rows (WAITLIST excluded)                    | Testcontainers PostgreSQL, `just backend-test-fast`                                |
| HTTP        | `GET /direct` 200 shape + no-token key assertion, 401, 403, 400 oversize, pagination totals                                          | `WebTestClient`, `just backend-test-fast`                                          |
| BDD         | List, pagination, combined filters, 401/403, empty page                                                                              | `invitations-direct.feature`, `just backend-bdd-fast`                              |
| Frontend    | Table render, filter re-query reset page 0, empty/loading/error, row resend/revoke refresh, ES labels, zero requests when `!canRead` | Vitest `DirectInvitationsView.spec.ts`, `admin-test` / `admin-check` / admin build |

## Migration / Rollout

No migration required. Reads existing `invitations` table; additive endpoint + view section only.
Rollback: revert commits — view falls back to create-only, no data change to undo.

## Open Questions

None blocking. Deferred by proposal scope: detail route, per-invitation audit linkage.
