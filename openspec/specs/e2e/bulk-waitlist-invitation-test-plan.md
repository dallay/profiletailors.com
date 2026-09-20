# E2E Test Plan: Bulk Waitlist Invitation

> Explored live in the browser and against the API on 2026-09-16 (admin SPA on
> `http://localhost:5174`, SMP backend on `http://localhost:7638`, main at
> `980ad046`). Every behavior below was exercised, not inferred.

## Scope

Covers the Back Office bulk waitlist invitation flow from issue #665 end to end:
admin login → waitlist list/search/filter → bulk selection → bulk submission →
per-entry results → summary refresh → audit trail. Includes the negative,
authorization, validation, and i18n paths observed during exploration.

Out of scope: campaign segmentation, waitlist scoring, generic bulk mutation
framework (all explicitly out of scope in #665); single-entry invite flow
(covered by existing BDD in `platform-admin.feature` and `WaitlistView.spec.ts`
unit coverage); backend-only BDD/integration suites (already green).

## Test Infrastructure

- **Framework**: Playwright. The admin SPA (`apps/web/admin/`) has no E2E
  harness today — create `apps/web/admin/e2e/` following the established
  `apps/web/app/e2e/` pattern (fixtures, page objects, `*-test.ts` lane
  composition, per-lane Playwright configs, CI job with report artifact).
- **App URL**: `http://localhost:5174` (vite `dev:app`, `/api` proxied to the
  backend, but note below).
- **Backend URL**: `http://localhost:7638` (dev profile, `just backend-run`).
- **Browsers**: Chromium (primary).
- **Critical wiring fact (verified)**: the admin SPA calls the backend
  **directly** at `VITE_API_BASE_URL` (default `http://localhost:7638`), not
  through the vite `/api` proxy (`resolveApiBaseUrl` in `src/lib/api.ts`).
  Browser E2E therefore requires the backend CORS allowlist to include the
  dev origin, otherwise every request fails preflight (`Failed to fetch`,
  HTTP 403 on `OPTIONS`). For this exploration the backend was restarted with
  `SMP_CORS_ALLOWED_ORIGINS` extended with `http://localhost:5174`; the
  committed `.env` only lists production/portless origins.
- **Backend prerequisites (verified)**: dev boot needs `SMP_LOCAL_JWT_DEV_FALLBACK`
  (32+ bytes, dev only) and `PUBLISHING_CREDENTIALS_ENCRYPTION_KEY` (base64
  AES key) in the launching shell — root `.env` values are not auto-loaded
  into `bootRun`. Without them the context fails (`jwtEncoder`,
  `CredentialEncryptionService`).
- **Auth**: form login at `/login` → `POST /api/auth/login` → Bearer token.
  Bulk invite requires `PLATFORM_OWNER`/`PLATFORM_OPERATOR` (`WAITLIST_INVITE`).
  A `SUPPORT_AGENT` gets HTTP 403 `PLATFORM_ACCESS_DENIED` before any entry
  is touched (verified).

### Seed Recipe (verified working)

```sql
-- Operator (note the second row: invitations.issued_by FKs to principals(id)
-- in user-<uuid> form; without it every invite fails with VERSION_CONFLICT
-- surfacing an fk_invitations_issued_by violation)
INSERT INTO principals (id, principal_type, subject, provider, display_identity)
VALUES ('<uuid>', 'USER', 'local:<email>', NULL, '<name>') ON CONFLICT DO NOTHING;
INSERT INTO principals (id, principal_type, subject, provider, display_identity)
VALUES ('user-<uuid>', 'USER', 'local:<email>', NULL, '<name>') ON CONFLICT DO NOTHING;
INSERT INTO user_identities (principal_id, email, username, email_status)
VALUES ('<uuid>', '<email>', '<user>', 'VERIFIED') ON CONFLICT DO NOTHING;
-- local_password_credentials: BCrypt hash of a known password
INSERT INTO local_password_credentials (principal_id, password_hash)
VALUES ('<uuid>', '<bcrypt-hash>') ON CONFLICT DO NOTHING;
INSERT INTO platform_role_assignments (id, principal_id, role, assigned_at, assigned_by, version)
VALUES (gen_random_uuid(), '<uuid>'::uuid, 'PLATFORM_OWNER', NOW(), '<uuid>'::uuid, 0)
ON CONFLICT DO NOTHING;
-- Waitlist + entries. Timestamps must be coherent with status:
-- INVITED rows need invited_at, CONVERTED rows need converted_at, otherwise
-- domain mapping throws on load (see Finding F1).
INSERT INTO waitlists (id, key, name, context, status)
VALUES ('<wl-id>', '<key>', '<name>', 'e2e', 'ACTIVE') ON CONFLICT DO NOTHING;
INSERT INTO waitlist_entries
  (id, waitlist_id, email_original, normalized_email, source, locale,
   consent_early_access, consent_marketing, consent_version, status,
   joined_at, invited_at, converted_at, version)
VALUES (...);
```

### Suggested Data Matrix

| Entry   | Status    | Purpose                                  |
|---------|-----------|------------------------------------------|
| 3× PENDING | PENDING | Happy-path bulk + select-all coverage   |
| 1× INVITED | INVITED | `skipped` / `ALREADY_INVITED` path      |
| 1× CONVERTED | CONVERTED | `failed` / `ENTRY_ALREADY_CONVERTED` path |
| 1× operator | PLATFORM_OWNER | Authorized bulk runs               |
| 1× support | SUPPORT_AGENT | 403 denial path                      |

## Architecture Overview

```
┌──────────────────────┐  direct http://localhost:7638 ┌───────────────────┐
│ Admin SPA (5174)     │ ────────────────────────────→ │ SMP backend       │
│ WaitlistView.vue     │  POST /api/admin/             │ AdminWaitlist     │
│  bulkInviteSelected  │  waitlist-entries/            │ Controller        │
│  selectedIds /       │  invitations:bulk             │  → BulkInvite…    │
│  bulkResults /       │  Accept: vnd.api.v1+json      │  Handler          │
│  bulkSummary         │                               │  → R2DBC/Postgres │
└──────────────────────┘                               └───────────────────┘
```

### Verified API Contract

| Request | Response |
|---|---|
| `POST …/invitations:bulk` `{"entryIds":["a","b"]}` + Bearer (owner) | 200 `{"results":[{"entryId","outcome":"invited\|skipped\|failed","invitationId?","code?"}],"summary":{"requested","invited","skipped","failed"}}` |
| Same, `SUPPORT_AGENT` token | 403 `PLATFORM_ACCESS_DENIED`, zero side effects |
| Same, no token | 401 |
| `{"entryIds":[]}` | 400 `At least one waitlist entry id is required` |
| 51 ids | 400 `Bulk invite supports at most 50 entries` |
| Unknown/converted ids | 200 with per-entry `failed` + stable code (never a batch abort) |

Observed codes: `ALREADY_INVITED` (skipped), `ENTRY_ALREADY_CONVERTED`
(failed). Successful rows create `invitations` with `source=WAITLIST` and one
`platform_admin_audit_events` row per entry (`SUCCEEDED`/`REJECTED`/`FAILED`).

### Observed UI Map (`/waitlist`, `WaitlistView.vue`)

- Summary chips: `Pending/Invited/Converted/Cancelled` counts (refreshed after
  bulk success — regression-cover this).
- Filters: email search, waitlist key, status combobox, joined/invited date
  pickers, Previous/Next pagination (fixed page size 25).
- Selection: `bulk-select-all` checkbox + per-row `bulk-select` checkboxes;
  button `INVITE SELECTED (n)`; selection **accumulates across pages**, so
  >50 is reachable and the client guard matters.
- Results: `bulk-results` panel — heading `Bulk invite results`, summary line
  `"{invited} invited · {skipped} skipped · {failed} failed"`, per-entry rows
  `"<id> — <outcome> (<CODE>)"`. Observed live:
  `2 invited · 1 skipped · 1 failed` with `ALREADY_INVITED` /
  `ENTRY_ALREADY_CONVERTED` rows.
- Errors: `role="alert"` area shows backend codes or the generic error on
  transport failure; over-limit selection shows the localized `bulkTooMany`
  message without sending any request.
- i18n: EN/ES keys (`bulkInvite`, `bulkSelect(All)`, `bulkResults`,
  `bulkSummary`, `bulkTooMany`).

## 1. Authorization

### 1.1 Unauthenticated bulk attempt is rejected

```
Given an operator with PLATFORM_OWNER exists
When POST …/invitations:bulk is sent without credentials
Then the response is 401
And no invitation or audit row is created
```

### 1.2 Support agent is denied before any entry is touched

```
Given an authenticated SUPPORT_AGENT without WAITLIST_INVITE
When POST …/invitations:bulk is sent with valid entry ids
Then the response is 403 PLATFORM_ACCESS_DENIED
And the waitlist entries keep their previous statuses
And no invitation or audit row is created
```

### 1.3 UI hides bulk actions without permission

```
Given a SUPPORT_AGENT session in the browser
When the waitlist view loads
Then no bulk-select checkboxes and no bulk-invite button are rendered
```

## 2. List, Search, Filter

### 2.1 Paginated list renders seeded entries

```
Given 7 seeded entries across statuses
When the operator opens /waitlist
Then all rows render with email, status, joined/invited dates
And Previous/Next pagination is present
```

### 2.2 Email search narrows rows

```
Given the seed matrix above
When the operator searches "gus@example.com"
Then only the matching row is shown
```

### 2.3 Status filter narrows rows

```
Given the seed matrix above
When the operator filters status PENDING
Then only PENDING rows are shown
```

## 3. Selection

### 3.1 Individual selection updates the count

```
Given the waitlist view with PENDING rows
When the operator checks two row checkboxes
Then the bulk button reads INVITE SELECTED (2)
```

### 3.2 Select-all covers the visible page

```
Given the waitlist view
When the operator checks select-all
Then every visible row checkbox is checked
And the button count matches the visible rows
```

### 3.3 Selection accumulates across pages

```
Given more than one page of entries
When the operator selects rows, changes page, and selects more rows
Then the button count reflects the union of both pages
```

## 4. Bulk Invite — Happy Path

### 4.1 All-PENDING batch invites every entry

```
Given 3 PENDING entries selected
When the operator clicks INVITE SELECTED
Then the response is 200 with 3 invited outcomes and invitationIds
And the results panel lists each invited id
And the summary line reads 3 invited · 0 skipped · 0 failed
And the summary chips update (PENDING down, INVITED up)
And 3 invitations exist with source WAITLIST
And 3 SUCCEEDED audit events exist, one per entry
```

## 5. Bulk Invite — Mixed Batch (Partial Success)

### 5.1 Mixed statuses report per-entry outcomes

```
Given 2 PENDING + 1 INVITED + 1 CONVERTED entries selected
When the operator clicks INVITE SELECTED
Then the response is 200 with 2 invited, 1 skipped (ALREADY_INVITED),
  1 failed (ENTRY_ALREADY_CONVERTED) and a matching summary
And the results panel shows each id with its outcome and code
And no raw tokens appear anywhere in the response or UI
```

### 5.2 Retry of a partial batch is safe

```
Given the batch above completed
When the operator re-submits the same ids
Then previously invited entries report skipped, never duplicates
And no second invitation row is created for them
```

## 6. Validation

### 6.1 Empty selection sends nothing

```
Given no rows selected
Then the bulk button is disabled
And no request is sent
```

### 6.2 Over-limit selection is blocked client-side with a localized message

```
Given 51 entries selected (accumulated across pages)
When the operator clicks INVITE SELECTED
Then no request is sent
And the alert shows the bulkTooMany message with the 50 limit
```

### 6.3 Over-limit request is rejected server-side

```
Given 51 ids in the request body with an owner token
When POST …/invitations:bulk is sent
Then the response is 400 Bulk invite supports at most 50 entries
And no entry is touched
```

### 6.4 Empty id list is rejected server-side

```
Given {"entryIds":[]} with an owner token
When POST …/invitations:bulk is sent
Then the response is 400 At least one waitlist entry id is required
```

## 7. Error Handling

### 7.1 Transport failure shows the generic error

```
Given rows selected and the backend unreachable
When the operator clicks INVITE SELECTED
Then the alert shows the generic error message
And the button returns to idle (not stuck loading)
```

### 7.2 Single corrupt row must not abort the batch (follow-up, see F1)

```
Given a batch containing one entry whose row violates a domain invariant
When the bulk invite runs
Then the corrupt entry reports failed with a stable code
And the remaining entries still report their own outcomes
```

## 8. Audit and Observability

### 8.1 Every outcome leaves exactly one audit row

```
Given a mixed batch completes
Then platform_admin_audit_events holds one row per requested id with
  SUCCEEDED / REJECTED+code / FAILED+code matching the reported outcomes
And metadata carries ids and codes only (no tokens, no emails)
```

## 9. i18n

### 9.1 Spanish locale renders bulk copy

```
Given the UI in Spanish
When the waitlist view with selection and results is shown
Then bulkInvite, bulkResults, bulkSummary, bulkTooMany render the ES copy
And layouts hold for the longer strings (no fixed-width clipping)
```

## Exploration Findings and Follow-ups

- **F1 (robustness, needs a decision, not fixed here)**: `BulkInviteWaitlistEntriesHandler.inviteOne`
  reads `findById` *outside* its per-entry try/catch. A row that fails domain
  mapping on load (observed: CONVERTED row with NULL `converted_at` →
  `Converted entry must have convertedAt`) escapes per-entry handling and
  aborts the whole batch with HTTP 400 — after earlier entries were already
  invited (verified live: 2 invited, then batch 400). Production writes
  should never produce such rows, but a single bad row can currently break
  the partial-success guarantee. Candidate fix: move the pre-check inside
  the guarded region and map load failures to `failed`/`UNEXPECTED_ERROR`.
- **Environment prerequisites for browser E2E** (all verified): backend
  needs `SMP_LOCAL_JWT_DEV_FALLBACK` + `PUBLISHING_CREDENTIALS_ENCRYPTION_KEY`
  in the launching shell; browser origin needs CORS allowlisting (the SPA
  calls the backend directly, not via the vite proxy); operator seeding must
  include the `user-<uuid>` principal row or invites die on
  `fk_invitations_issued_by` (surfaced as `VERSION_CONFLICT`); seeded rows
  need status-coherent timestamps.
- **Traceability to #665**: criteria 1–4 are covered by sections 4–6 and 8
  (bulk invite of eligible entries; ineligible/duplicate cases reported
  without blocking others; per-entry success/failure; auditability).

## Suggested Lane Split (mirrors repo precedent)

- **Mocked lane** (fast, CI-default): Playwright route interception for
  `/api/admin/waitlist-entries*` + login, covering sections 1.3, 2–3, 4.1,
  5.1–5.2, 6.1–6.2, 7.1, 9 — no backend required.
- **Real-backend lane** (nightly or on-demand): live SMP + Postgres with the
  seed recipe above, covering 1.1–1.2, 6.3–6.4, 7.2, 8 end to end.
