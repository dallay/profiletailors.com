## Exploration: Back Office slice 1 — Users management (hardened/expanded)

### Current State

The users back-office area has a functional but skeletal foundation: `UsersView.vue` (paged list with email search), `UserDetailView.vue` (detail + workspace memberships), `AdminUserController`, `R2dbcAdminUserQuery`, `ListAdminUsersQuery`, and a sparse i18n skeleton. Previous slices (shell, invitations, waitlist) are archived and establish the completeness bar.

### UsersView.vue — What it does

| Feature | Status |
|---------|--------|
| Paged list (page/size=25, sort=createdAt desc) | ✅ |
| Email substring search (debounced 300ms, exact lowercase match) | ✅ |
| Previous/Next pagination | ✅ |
| Loading and error states | ✅ |
| Click-through to user detail | ✅ |
| AbortController for in-flight requests | ✅ |
| Status filter dropdown (no UI wired) | ❌ |
| principalType filter dropdown | ❌ |
| Date-range filters (createdFrom/createdTo — backend supports, no UI) | ❌ |
| Summary count card (breakdown by principalType) | ❌ |
| Row-level actions (deactivate, role management) | ❌ |
| `authenticationMethods` column in list | ❌ |
| `workspaceCount` column in list | ❌ |
| `platformRoles` column in list | ❌ |
| `lastAuthenticatedAt` column in list | ❌ |
| Vitest spec | ❌ |

### UserDetailView.vue — What it does

| Feature | Status |
|---------|--------|
| User fields: email, displayIdentity, principalType, createdAt, lastAuthenticatedAt, platformRoles | ✅ |
| Workspace memberships table (name, status, roles, joinedAt) | ✅ |
| Back navigation to list | ✅ |
| Field component pattern for label/value pairs | ✅ |
| `authenticationMethods` list display | ❌ (present in model, not rendered) |
| Row actions (deactivate, manage roles, impersonate) | ❌ |
| Version field (optimistic lock) | ❌ |
| Consent fields display (consentReceipt, marketingConsent) | ❌ |
| User metadata summary | ❌ |
| Audit trail linkage (who created/modified) | ❌ |
| Copy principalId to clipboard | ❌ |
| Workspace detail drill-down | ❌ |
| Vitest spec | ❌ |

### Backend API Surface — What exists

| Endpoint | Path | Filters supported | Status |
|----------|------|-----------------|--------|
| List users | `GET /api/admin/users` | status, email, createdFrom, createdTo, sort, direction | ✅ |
| Get user detail | `GET /api/admin/users/{principalId}` | — | ✅ |
| Get user workspaces | `GET /api/admin/users/{principalId}/workspaces` | — | ✅ |
| Mutation command (deactivate) | `PATCH /api/admin/users/{principalId}` | — | ❌ |
| Role management | `PATCH /api/admin/users/{principalId}/roles` | — | ❌ |
| Version field in responses | — | — | ❌ |
| Consent fields in user model | — | — | ❌ |
| BDD scenarios for users | — | — | ❌ |

### i18n — What exists (EN)

| Key | Status |
|-----|--------|
| `users.title` | ✅ |
| `users.displayName`, `users.principalType`, `users.lastAuthenticated`, `users.platformRoles`, `users.workspaces`, `users.workspaceMemberships` | ✅ |
| `users.filters.*` (search, status, principalType, dateRange) | ❌ |
| `users.actions.*` (deactivate, reactivate, manageRoles) | ❌ |
| `users.confirmDeactivate`, `users.confirmReactivate` | ❌ |
| `users.status.*` (active, inactive, suspended) | ❌ |
| `users.errors.*` (notFound, versionConflict) | ❌ |
| ES translations (`users.*`) | ❌ |

### Vitest specs

| Spec | Status | Notes |
|------|--------|-------|
| `UsersView.spec.ts` | ❌ | Missing entirely |
| `UserDetailView.spec.ts` | ❌ | Missing entirely |
| `DirectInvitationsView.spec.ts` | ✅ | Reference pattern |
| `WaitlistView.spec.ts` | ✅ | Reference pattern |
| `WaitlistEntryView.spec.ts` | ✅ | Reference pattern |

### Gap Analysis — High / Medium / Low

| Gap | Severity | Rationale |
|-----|----------|-----------|
| **No row actions** (deactivate/reactivate/role management) | **HIGH** | Core operational need; operators cannot manage users today |
| **No optimistic lock** on user mutations | **HIGH** | Race condition risk on concurrent edits |
| **No Vitest specs** for list and detail views | **HIGH** | Required coverage per repository policy |
| **No status filter UI** (status and principalType dropdowns missing) | **HIGH** | Backend supports status filtering; operators can't use it |
| **No BDD scenarios** for user list/detail | **HIGH** | Required for externally observable backend behavior |
| **`authenticationMethods` not rendered** in detail | **MEDIUM** | Data fetched but ignored in template |
| **`workspaceCount`, `platformRoles`, `lastAuthenticatedAt` absent** from list columns | **MEDIUM** | List columns lack useful context today |
| **No summary count card** (breakdown by principalType/status) | **MEDIUM** | Present in invitations; useful for operators |
| **No date-range filter UI** (createdFrom/createdTo) | **MEDIUM** | Backend supports; UI missing |
| **`consentReceipt` fields not displayed** in detail | **MEDIUM** | GDPR visibility useful for operators |
| **No ES i18n translations** for users namespace | **MEDIUM** | Repository requires EN+ES parity |
| **No principalType filter** in list | **LOW** | Minor UX improvement |
| **No version field** in detail view | **LOW** | Requires backend model change first |
| **No audit trail linkage** in detail | **LOW** | Requires audit context to be wired |

### Reference Slice Completeness Bar

The archived waitlist and invitations slices establish the target bar:

| Capability | Invitations | Waitlist | Users target |
|------------|-----------|----------|-------------|
| Paged list | ✅ | ✅ | ✅ |
| Email search | ✅ | ✅ | ✅ |
| Status filter | ✅ | ✅ | ✅ (add UI) |
| principalType filter | N/A | N/A | ✅ (add) |
| Date-range filter | ❌ | ✅ | ✅ (add UI) |
| Summary count card | ✅ | ❌ | ✅ (add) |
| Row actions | ✅ (resend, revoke) | ✅ (invite, cancel) | ✅ (deactivate, roles) |
| Optimistic lock (version field) | ✅ | ✅ | ✅ (add to model + UI) |
| Detail view | ✅ | ✅ | ✅ |
| Consent/metadata fields | ❌ | ✅ | ✅ (add) |
| BDD scenarios | ✅ | ✅ | ✅ (add) |
| Vitest spec | ✅ | ✅ | ✅ (add) |
| i18n EN+ES | ✅ | ✅ | ✅ (complete ES) |

### Risks

- **Mutation commands don't exist** — adding row actions requires backend `PATCH /api/admin/users/{id}` and `PATCH /api/admin/users/{id}/roles` endpoints with optimistic locking. These must be specced and implemented before frontend actions land.
- **User model lacks version field** — optimistic lock requires `version` on the user entity and returned in list/detail responses. Needs schema/data layer change.
- **Consent fields on user principal** — unclear if user principal model exposes consentReceipt; needs domain verification.
- **Permission gating** — `platform.users.manage` or equivalent permission must be added to the platform permission enum before row actions can gate on it.
- **No migration path for `authenticationMethods`** — the field exists in the model but is populated from `user_identities` table; needs verification.

### Ready for Proposal

Yes — the foundation exists but is operationally incomplete. The primary story is: wire the missing UI filters, add row actions with optimistic locking, and cover with tests and BDD. The blocker is the mutation API — it must land in parallel or before the frontend row actions.
